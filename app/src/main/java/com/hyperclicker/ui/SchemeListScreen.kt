package com.hyperclicker.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperclicker.data.ClickScheme
import com.hyperclicker.data.MODE_HIGH_FREQ
import com.hyperclicker.data.REPEAT_COUNT
import com.hyperclicker.data.REPEAT_DURATION
import com.hyperclicker.util.PermissionUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchemeListScreen(
    vm: MainViewModel,
    onNew: () -> Unit,
    onEdit: (Long) -> Unit
) {
    val schemes by vm.schemes.collectAsState()
    val connected by vm.accessibilityConnected.collectAsState()
    val running by vm.running.collectAsState()
    val activeId by vm.activeSchemeId.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = { TopAppBar(title = { Text("连点器") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onNew) { Text("+", fontSize = 24.sp) }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            PermissionBanner(connected = connected, context = context)

            if (schemes.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无方案，点击右下角 + 新建", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            "先「选择」方案 → 切到目标界面用悬浮球「录点」→ 回列表「开始」",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    items(schemes, key = { it.id }) { scheme ->
                        SchemeCard(
                            scheme = scheme,
                            isSelected = activeId == scheme.id,
                            isRunning = running && activeId == scheme.id,
                            onSelect = { vm.select(scheme) },
                            onStart = { vm.start(scheme) },
                            onEdit = { onEdit(scheme.id) },
                            onDelete = { vm.delete(scheme) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionBanner(connected: Boolean, context: Context) {
    val overlay = PermissionUtils.isOverlayGranted(context)
    val exactAlarm = PermissionUtils.canScheduleExactAlarms(context)
    if (connected && overlay && exactAlarm) return

    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("使用前需完成以下设置", style = MaterialTheme.typography.titleSmall)
            if (!connected) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("1. 开启无障碍服务", Modifier.weight(1f))
                    Button(onClick = { PermissionUtils.openAccessibilitySettings(context) }) { Text("去开启") }
                }
            }
            if (!overlay) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("2. 授予悬浮窗权限", Modifier.weight(1f))
                    Button(onClick = { PermissionUtils.openOverlaySettings(context) }) { Text("去授予") }
                }
            }
            if (!exactAlarm) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("3. 授予精确闹钟权限（低频点击）", Modifier.weight(1f))
                    Button(onClick = { PermissionUtils.openExactAlarmSettings(context) }) { Text("去授予") }
                }
            }
            Text(
                "4. HyperOS 还需：自启动 + 省电无限制（低频点击必需）",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFE65100)
            )
            TextButton(onClick = { PermissionUtils.openAutoStartSettings(context) }) { Text("打开自启动设置") }
        }
    }
}

@Composable
private fun SchemeCard(
    scheme: ClickScheme,
    isSelected: Boolean,
    isRunning: Boolean,
    onSelect: () -> Unit,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(scheme.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                when {
                    isRunning -> Text("运行中", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                    isSelected -> Text("已选择", color = Color(0xFF1565C0), fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(describeScheme(scheme), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSelect) { Text(if (isSelected) "已选择" else "选择") }
                Button(onClick = onStart) { Text(if (isRunning) "重新开始" else "开始") }
                OutlinedButton(onClick = onEdit) { Text("编辑") }
                TextButton(onClick = onDelete) { Text("删除", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

private fun describeScheme(s: ClickScheme): String {
    val mode = if (s.mode == MODE_HIGH_FREQ) "高频" else "低频"
    val interval = when {
        s.intervalMs >= 3_600_000 && s.intervalMs % 3_600_000 == 0L -> "${s.intervalMs / 3_600_000}小时"
        s.intervalMs >= 60_000 && s.intervalMs % 60_000 == 0L -> "${s.intervalMs / 60_000}分钟"
        s.intervalMs >= 1000 && s.intervalMs % 1000 == 0L -> "${s.intervalMs / 1000}秒"
        else -> "${s.intervalMs}毫秒"
    }
    val repeat = when (s.repeatType) {
        REPEAT_COUNT -> "执行${s.repeatValue}次"
        REPEAT_DURATION -> "持续${s.repeatValue}秒"
        else -> "无限循环"
    }
    return "$mode · 每$interval · $repeat · ${s.points().size}个点位"
}
