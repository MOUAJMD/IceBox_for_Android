package com.mouajmd.icebox.helper

import android.app.AndroidAppHelper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Handler
import android.os.HandlerThread
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class MainHook : IXposedHookLoadPackage {

    companion object {
        private const val CONFIG_PATH = "/data/system/icebox_list.conf"
        private const val FREEZE_DELAY_MS = 2 * 60 * 1000L // 2 分钟
        private const val FOREGROUND_RETRY_MS = 60 * 1000L // 若仍在前台则顺延 1 分钟

        @Volatile
        private var lastModified = -1L

        @Volatile
        private var cachedPackages: Set<String> = emptySet()

        private val workerThread = HandlerThread("IceBoxHelperWorker").apply { start() }
        private val workerHandler = Handler(workerThread.looper)
        private val pendingTasks = ConcurrentHashMap<String, Runnable>()
        private val recentUnfreezeTime = ConcurrentHashMap<String, Long>()
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "android") return

        try {
            val activityStarterClass = XposedHelpers.findClass(
                "com.android.server.wm.ActivityStarter",
                lpparam.classLoader
            ) ?: return

            XposedBridge.hookAllMethods(activityStarterClass, "execute", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val targetPkg = extractTargetPackage(param) ?: return
                    if (targetPkg !in getManagedPackages()) return
                    handleTargetLaunch(targetPkg)
                }
            })

            XposedBridge.log("IceBox-Helper: 已成功挂载 ActivityStarter 拦截器")
        } catch (t: Throwable) {
            XposedBridge.log("IceBox-Helper: 挂载失败 -> ${t.message}")
        }
    }

    private fun extractTargetPackage(param: XC_MethodHook.MethodHookParam): String? {
        param.args?.forEach { arg ->
            when (arg) {
                is Intent -> {
                    val pkg = arg.`package` ?: arg.component?.packageName
                    if (!pkg.isNullOrEmpty()) return pkg
                }
                is ActivityInfo -> {
                    if (!arg.packageName.isNullOrEmpty()) return arg.packageName
                }
            }
        }
        return try {
            val instance = param.thisObject ?: return null
            val reqField = instance.javaClass.getDeclaredField("mRequest").apply { isAccessible = true }
            val req = reqField.get(instance) ?: return null
            val intentField = req.javaClass.getDeclaredField("intent").apply { isAccessible = true }
            val intent = intentField.get(req) as? Intent
            intent?.`package` ?: intent?.component?.packageName
        } catch (_: Throwable) {
            null
        }
    }

    private fun getManagedPackages(): Set<String> {
        return try {
            val file = File(CONFIG_PATH)
            if (!file.exists()) return emptySet()
            val mod = file.lastModified()
            if (mod != lastModified) {
                cachedPackages = file.readLines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toSet()
                lastModified = mod
            }
            cachedPackages
        } catch (_: Throwable) {
            cachedPackages
        }
    }

    private fun handleTargetLaunch(pkg: String) {
        val now = System.currentTimeMillis()
        val lastTime = recentUnfreezeTime[pkg] ?: 0L
        if (now - lastTime < 1500L) {
            scheduleRefreeze(pkg, FREEZE_DELAY_MS)
            return
        }

        if (isAppFrozen(pkg)) {
            recentUnfreezeTime[pkg] = now
            setPackageFrozen(pkg, freeze = false)
            XposedBridge.log("IceBox-Helper: 检测到调用，已自动解冻 $pkg (2分钟后回冻)")
            scheduleRefreeze(pkg, FREEZE_DELAY_MS)
        } else if (pendingTasks.containsKey(pkg)) {
            scheduleRefreeze(pkg, FREEZE_DELAY_MS)
        }
    }

    private fun isAppFrozen(pkg: String): Boolean {
        val token = Binder.clearCallingIdentity()
        return try {
            val context = AndroidAppHelper.currentApplication() ?: return false
            val state = context.packageManager.getApplicationEnabledSetting(pkg)
            state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
        } catch (_: Throwable) {
            false
        } finally {
            Binder.restoreCallingIdentity(token)
        }
    }

    private fun setPackageFrozen(pkg: String, freeze: Boolean) {
        val token = Binder.clearCallingIdentity()
        try {
            val context = AndroidAppHelper.currentApplication()
            if (context != null) {
                val newState = if (freeze) {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                }
                context.packageManager.setApplicationEnabledSetting(pkg, newState, 0)
                return
            }
        } catch (t: Throwable) {
            XposedBridge.log("IceBox-Helper: 系统接口切换状态失败，回退 Shell -> ${t.message}")
        } finally {
            Binder.restoreCallingIdentity(token)
        }
        runShell(if (freeze) "pm disable $pkg" else "pm enable $pkg")
    }

    private fun scheduleRefreeze(pkg: String, delayMs: Long) {
        pendingTasks[pkg]?.let { workerHandler.removeCallbacks(it) }

        val task = Runnable {
            if (pkg !in getManagedPackages()) {
                pendingTasks.remove(pkg)
                return@Runnable
            }
            if (isAppInForeground(pkg)) {
                XposedBridge.log("IceBox-Helper: $pkg 仍在屏幕前台，顺延 1 分钟后再检测回冻")
                scheduleRefreeze(pkg, FOREGROUND_RETRY_MS)
            } else {
                setPackageFrozen(pkg, freeze = true)
                pendingTasks.remove(pkg)
                XposedBridge.log("IceBox-Helper: 已自动回冻 $pkg")
            }
        }

        pendingTasks[pkg] = task
        workerHandler.postDelayed(task, delayMs)
    }

    private fun isAppInForeground(pkg: String): Boolean {
        return try {
            val output = runShell("dumpsys window displays")
            output.contains("mCurrentFocus=Window{") && output.contains(" $pkg/")
        } catch (_: Throwable) {
            false
        }
    }

    private fun runShell(cmd: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            val result = process.inputStream.bufferedReader().readText()
            process.waitFor()
            result
        } catch (t: Throwable) {
            XposedBridge.log("IceBox-Helper: 命令执行异常 ($cmd) -> ${t.message}")
            ""
        }
    }
}
