package com.hyperclicker.ui

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hyperclicker.data.ClickPoint
import com.hyperclicker.data.ClickScheme
import com.hyperclicker.data.MODE_HIGH_FREQ
import com.hyperclicker.data.MODE_LOW_FREQ
import com.hyperclicker.data.REPEAT_COUNT
import com.hyperclicker.data.REPEAT_DURATION
import com.hyperclicker.data.REPEAT_INFINITE
import kotlinx.coroutines.launch

private data class Preset(val label: String, val value: Long, val unit: Int)

/** 间隔数值+单位拆分：高频 unit 0=毫秒/1=秒；低频 unit 0=分钟/1=小时 */
private fun splitInterval(ms: Long, mode: Int): Pair<Long, Int> = if (mode == MODE_HIGH_FREQ) {
    if (ms >= 1000 && ms % 1000 == 0L) (ms / 1000) to 1 else ms to 0
} else {
    if (ms >= 3_600_000 && ms % 3_600_000 == 0L) (ms / 3_600_000) to 1 else (ms / 60_000) to 0
}

private fun presetsFor(mode: Int): List<Preset> = if (mode == MODE_HIGH_FREQ) {
    listOf(
        Preset("10ms", 10, 0), Preset("50ms", 50, 0), Preset("100ms", 100, 0),
        Preset("200ms", 200, 0), Preset("500ms", 500, 0),
        Preset("1s", 1, 1), Preset("2s", 2, 1), Preset("5s", 5, 1)
    )
} else {
    listOf(
        Preset("1分钟", 1, 0), Preset("5分钟", 5, 0), Preset("10分钟", 10, 0),
        Preset("15分钟", 15, 0), Preset("30分钟", 30, 0),
        Preset("1小时", 1, 1), Preset("2小时", 2, 1), Preset("6小时", 6, 1)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchemeEditScreen(
    vm: MainViewModel,
    schemeId: Long?,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var name by remember { mutableStateOf("") }
    var mode by remember { mutableIntStateOf(MODE_HIGH_FREQ) }
    var intervalValue by remember { mutableStateOf("100") }
    var intervalUnit by remember { mutableIntStateOf(0) }
    var repeatType by remember { mutableIntStateOf(REPEAT_INFINITE) }
    var repeatValue by remember { mutableStateOf("10") }
    var autoStart by remember { mutableStateOf(false) }
    var points by remember { mutableStateOf<List<ClickPoint>>(emptyList()) }
    var loaded by remember { mutableStateOf(schemeId == null) }

    LaunchedEffect(schemeId) {
        if (schemeId != null) {
            val s = vm.getScheme(schemeId)
            if (s != null) {
                name = s.name
                mode = s.mode
                val (v, u) = splitInterval(s.intervalMs, s.mode)
                intervalValue = v.toString()
                intervalUnit = u
                repeatType = s.repeatType
                repeatValue = s.repeatValue.toString()
                autoStart = s.autoStartOnBoot
                points = s.points()
            }
        }
        loaded = true
    }

    if (!loaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (schemeId == null) "新建方案" else "编辑方案") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("方案名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Text("点击模式", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = mode == MODE_HIGH_FREQ,
                    onClick = { mode = MODE_HIGH_FREQ; intervalUnit = 0 },
                    label = { Text("高频点击") }
                )
                FilterChip(
                    selected = mode == MODE_LOW_FREQ,
                    onClick = { mode = MODE_LOW_FREQ; intervalUnit = 0 },
                    label = { Text("低频点击") }
                )
            }

            Text("点击间隔", style = MaterialTheme.typography.titleSmall)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = intervalValue,
                    onValueChange = { intervalValue = it.filter { c -> c.isDigit() } },
                    modifier = Modifier.weight(1f),
                    label = { Text("数值") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                val units = if (mode == MODE_HIGH_FREQ) listOf("毫秒", "秒") else listOf("分钟", "小时")
                units.forEachIndexed { i, label ->
                    FilterChip(
                        selected = intervalUnit == i,
                        onClick = { intervalUnit = i },
                        label = { Text(label) }
                    )
                }
            }

            Text("快捷预设", style = MaterialTheme.typography.titleSmall)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presetsFor(mode).forEach { preset ->
                    AssistChip(
                        onClick = {
                            intervalValue = preset.value.toString()
                            intervalUnit = preset.unit
                        },
                        label = { Text(preset.label) }
                    )
                }
            }

            Text("执行方式", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = repeatType == REPEAT_INFINITE,
                    onClick = { repeatType = REPEAT_INFINITE },
                    label = { Text("无限") }
                )
                FilterChip(
                    selected = repeatType == REPEAT_COUNT,
                    onClick = { repeatType = REPEAT_COUNT },
                    label = { Text("次数") }
                )
                FilterChip(
                    selected = repeatType == REPEAT_DURATION,
                    onClick = { repeatType = REPEAT_DURATION },
                    label = { Text("时长(秒)") }
                )
            }
            if (repeatType != REPEAT_INFINITE) {
                OutlinedTextField(
                    value = repeatValue,
                    onValueChange = { repeatValue = it.filter { c -> c.isDigit() } },
                    label = { Text(if (repeatType == REPEAT_COUNT) "次数" else "持续秒数") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("开机自启（低频任务恢复）", modifier = Modifier.weight(1f))
                Switch(checked = autoStart, onCheckedChange = { autoStart = it })
            }

            // ---------------- 点位管理 ----------------
            Text("点位管理（${points.size} 个）", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val id = schemeId ?: return@launch
                            vm.getScheme(id)?.let { points = it.points() }
                        }
                    },
                    enabled = schemeId != null
                ) { Text("刷新") }
                TextButton(onClick = { points = emptyList() }, enabled = points.isNotEmpty()) { Text("清空") }
            }
            Text(
                "先回列表「选择」方案，切到目标界面用悬浮球「录点」（长按屏幕=长按点位）；录完回此处「刷新」即可编辑顺序 / 长按 / 间隔。",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )

            points.forEachIndexed { i, p ->
                PointRow(
                    index = i,
                    total = points.size,
                    point = p,
                    onPressMsChange = { v ->
                        points = points.mapIndexed { j, q -> if (j == i) q.copy(pressMs = v) else q }
                    },
                    onGapMsChange = { v ->
                        points = points.mapIndexed { j, q -> if (j == i) q.copy(gapMs = v) else q }
                    },
                    onMoveUp = {
                        if (i > 0) {
                            val l = points.toMutableList()
                            val t = l[i]; l[i] = l[i - 1]; l[i - 1] = t
                            points = l
                        }
                    },
                    onMoveDown = {
                        if (i < points.size - 1) {
                            val l = points.toMutableList()
                            val t = l[i]; l[i] = l[i + 1]; l[i + 1] = t
                            points = l
                        }
                    },
                    onDelete = { points = points.filterIndexed { j, _ -> j != i } }
                )
            }

            if (points.isEmpty()) {
                Text("暂无点位", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }

            Button(
                onClick = {
                    val v = intervalValue.toLongOrNull()
                    if (v == null || v <= 0) {
                        Toast.makeText(context, "间隔必须大于 0", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val ms = if (mode == MODE_HIGH_FREQ) {
                        v * (if (intervalUnit == 1) 1000L else 1L)
                    } else {
                        v * (if (intervalUnit == 1) 3_600_000L else 60_000L)
                    }
                    val rv = if (repeatType == REPEAT_INFINITE) 0 else (repeatValue.toIntOrNull() ?: 0)
                    if (repeatType != REPEAT_INFINITE && rv <= 0) {
                        Toast.makeText(context, "请填写有效的次数/时长", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val scheme = ClickScheme(
                        id = schemeId ?: 0L,
                        name = name.ifBlank { "未命名方案" },
                        mode = mode,
                        intervalMs = ms,
                        repeatType = repeatType,
                        repeatValue = rv,
                        autoStartOnBoot = autoStart,
                        enabled = true,
                        pointsJson = ClickScheme.toJson(points)
                    )
                    scope.launch {
                        vm.save(scheme)
                        Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("保存") }
        }
    }
}

@Composable
private fun PointRow(
    index: Int,
    total: Int,
    point: ClickPoint,
    onPressMsChange: (Long) -> Unit,
    onGapMsChange: (Long) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "点位 ${index + 1} · (${(point.x * 100).toInt()}%, ${(point.y * 100).toInt()}%)",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                IconButton(onClick = onDelete) { Text("✕", color = MaterialTheme.colorScheme.error) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = point.pressMs.toString(),
                    onValueChange = { t -> onPressMsChange(t.filter { it.isDigit() }.toLongOrNull() ?: 0L) },
                    label = { Text("长按(ms)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = point.gapMs.toString(),
                    onValueChange = { t -> onGapMsChange(t.filter { it.isDigit() }.toLongOrNull() ?: 0L) },
                    label = { Text("间隔(ms)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onMoveUp, enabled = index > 0) { Text("↑ 上移") }
                TextButton(onClick = onMoveDown, enabled = index < total - 1) { Text("↓ 下移") }
            }
        }
    }
}
