package com.mouajmd.IceBox

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppItem(
    val name: String,
    val packageName: String,
    val isFrozen: Boolean,
    val icon: ImageBitmap
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestMaxRefreshRate()
        enableEdgeToEdge()
        setContent { FrostTheme { FrostVaultScreen() } }
    }

    private fun requestMaxRefreshRate() {
        val currentDisplay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay
        } ?: return
        val maxMode = currentDisplay.supportedModes.maxByOrNull { it.refreshRate } ?: return
        window.attributes = window.attributes.apply {
            preferredDisplayModeId = maxMode.modeId
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        RootShell.close()
    }
}

@Composable
fun FrostTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrostVaultScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("frost_vault_prefs", Context.MODE_PRIVATE) }

    var allApps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var boxPackages by remember {
        mutableStateOf(prefs.getStringSet("box_packages", emptySet()) ?: emptySet())
    }
    var showPickerSheet by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var isFreezingAll by remember { mutableStateOf(false) }

    fun saveBoxPackages(newSet: Set<String>) {
        boxPackages = newSet
        prefs.edit().putStringSet("box_packages", newSet).apply()
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 && it.packageName != context.packageName }
                .map { info ->
                    AppItem(
                        name = info.loadLabel(pm).toString(),
                        packageName = info.packageName,
                        isFrozen = !info.enabled,
                        icon = info.loadIcon(pm).toBitmap(96, 96).asImageBitmap()
                    )
                }
                .sortedBy { it.name.lowercase() }
            withContext(Dispatchers.Main) { allApps = installed }
        }
    }

    val boxApps = remember(allApps, boxPackages) {
        allApps.filter { it.packageName in boxPackages }
            .sortedWith(compareBy({ it.isFrozen }, { it.name.lowercase() }))
    }

    val unfrozenCount = remember(boxApps) { boxApps.count { !it.isFrozen } }

    fun launchApp(packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            context.startActivity(intent)
        } else {
            Toast.makeText(context, "该应用无启动入口", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(46.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AcUnit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(11.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "极简冰箱",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (boxApps.isEmpty()) "点击右上角 + 添加要冻结的应用"
                            else "共 ${boxApps.size} 个 · ${boxApps.size - unfrozenCount} 个已冻结",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalIconButton(
                        onClick = { showAboutDialog = true },
                        modifier = Modifier.size(46.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Rounded.Info, contentDescription = "关于")
                    }

                    FilledTonalIconButton(
                        onClick = { showPickerSheet = true },
                        modifier = Modifier.size(46.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "添加应用")
                    }
                }
            }
        },
        floatingActionButton = {
            if (boxApps.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (isFreezingAll) return@ExtendedFloatingActionButton
                        val targets = boxApps.filter { !it.isFrozen }.map { it.packageName }
                        if (targets.isEmpty()) {
                            Toast.makeText(context, "列表内应用已全部冻结", Toast.LENGTH_SHORT).show()
                            return@ExtendedFloatingActionButton
                        }
                        isFreezingAll = true
                        scope.launch(Dispatchers.IO) {
                            val ok = RootShell.freezeBatch(targets)
                            withContext(Dispatchers.Main) {
                                isFreezingAll = false
                                if (ok) {
                                    val targetSet = targets.toSet()
                                    allApps = allApps.map {
                                        if (it.packageName in targetSet) it.copy(isFrozen = true) else it
                                    }
                                    Toast.makeText(context, "已冻结 ${targets.size} 个应用", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Root 权限执行失败", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    shape = RoundedCornerShape(22.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Rounded.AcUnit, contentDescription = null) },
                    text = {
                        Text(
                            text = if (unfrozenCount > 0) "一键冻结 ($unfrozenCount)" else "已全部冻结",
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
            }
        }
    ) { innerPadding ->
        if (boxApps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.AcUnit,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outlineVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "冰箱列表为空",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { showPickerSheet = true },
                        shape = CircleShape
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("选择应用加入冰箱")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 4.dp,
                    bottom = innerPadding.calculateBottomPadding() + 92.dp,
                    start = 16.dp,
                    end = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = boxApps,
                    key = { it.packageName },
                    contentType = { "box_app" }
                ) { app ->
                    BoxAppCard(
                        app = app,
                        onOpenApp = {
                            if (!app.isFrozen) {
                                launchApp(app.packageName)
                            } else {
                                scope.launch(Dispatchers.IO) {
                                    val ok = RootShell.setAppEnabled(app.packageName, enabled = true)
                                    withContext(Dispatchers.Main) {
                                        if (ok) {
                                            allApps = allApps.map {
                                                if (it.packageName == app.packageName) it.copy(isFrozen = false) else it
                                            }
                                            launchApp(app.packageName)
                                        } else {
                                            Toast.makeText(context, "解冻失败，请检查 Root 权限", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        },
                        onToggleFreeze = {
                            scope.launch(Dispatchers.IO) {
                                val targetFreeze = !app.isFrozen
                                val ok = RootShell.setAppEnabled(app.packageName, enabled = !targetFreeze)
                                withContext(Dispatchers.Main) {
                                    if (ok) {
                                        allApps = allApps.map {
                                            if (it.packageName == app.packageName) it.copy(isFrozen = targetFreeze) else it
                                        }
                                    } else {
                                        Toast.makeText(context, "Root 执行失败", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        onRemoveFromBox = {
                            saveBoxPackages(boxPackages - app.packageName)
                            Toast.makeText(context, "已将 ${app.name} 移出列表", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }

        if (showPickerSheet) {
            AppPickerBottomSheet(
                allApps = allApps,
                selectedPackages = boxPackages,
                onTogglePackage = { pkg ->
                    val updated = if (pkg in boxPackages) boxPackages - pkg else boxPackages + pkg
                    saveBoxPackages(updated)
                },
                onDismiss = { showPickerSheet = false }
            )
        }

        if (showAboutDialog) {
            AboutDialog(onDismiss = { showAboutDialog = false })
        }
    }
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val repoUrl = "https://github.com/MOUAJMD/IceBox_for_Android"

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        icon = {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.AcUnit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(14.dp)
                )
            }
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "极简冰箱",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = "v2.0 · Material 3 Expressive",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "基于 Root Shell (pm enable / pm disable) 实现的轻量级 Android 应用冻结工具，支持列表管理、一键批量冻结与点击解冻直达。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(repoUrl))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "未找到可用浏览器", Toast.LENGTH_SHORT).show()
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "GitHub 项目地址",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "MOUAJMD/IceBox_for_Android",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.OpenInNew,
                            contentDescription = "打开链接",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BoxAppCard(
    app: AppItem,
    onOpenApp: () -> Unit,
    onToggleFreeze: () -> Unit,
    onRemoveFromBox: () -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = if (app.isFrozen) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "cardColor"
    )

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = containerColor,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .combinedClickable(
                onClick = onOpenApp,
                onLongClick = onRemoveFromBox
            )
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Image(
                bitmap = app.icon,
                contentDescription = app.name,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (app.isFrozen) "已冻结 · 点击解冻并打开" else "运行中 · 点击直接打开",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (app.isFrozen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            FilledTonalIconButton(
                onClick = onToggleFreeze,
                shape = CircleShape,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (app.isFrozen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = if (app.isFrozen) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(
                    imageVector = if (app.isFrozen) Icons.Rounded.PlayArrow else Icons.Rounded.AcUnit,
                    contentDescription = if (app.isFrozen) "解冻" else "冻结",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerBottomSheet(
    allApps: List<AppItem>,
    selectedPackages: Set<String>,
    onTogglePackage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(allApps, query) {
        if (query.isBlank()) allApps
        else allApps.filter { it.name.contains(query, true) || it.packageName.contains(query, true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "管理冰箱列表 (${selectedPackages.size})",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            )

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索应用名称或包名...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(
                    items = filtered,
                    key = { it.packageName },
                    contentType = { "picker_app" }
                ) { app ->
                    val checked = app.packageName in selectedPackages
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (checked) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onTogglePackage(app.packageName) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Image(
                                bitmap = app.icon,
                                contentDescription = app.name,
                                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = app.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (checked) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "已添加",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
