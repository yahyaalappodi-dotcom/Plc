package com.example.plcsim

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val INPUTS = listOf("X0", "X1", "X2", "X3")
private val OUTPUTS = listOf("Y0", "Y1", "Y2", "Y3")
private val MEMORY = listOf("M0", "M1", "M2", "M3")
private val ALL_NAMES = INPUTS + OUTPUTS + MEMORY
private val COIL_NAMES = OUTPUTS + MEMORY

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { PlcApp() }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlcApp() {
    val rungs = remember {
        mutableStateListOf(
            Rung(listOf(Contact("X0", ContactKind.NO), Contact("X1", ContactKind.NC)), "Y0")
        )
    }
    val inputs = remember { mutableStateMapOf<String, Boolean>() }
    var running by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Int?>(null) }

    val state: Map<String, Boolean> =
        if (running) LadderEngine.scan(rungs.toList(), inputs.toMap()) else emptyMap()

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("PLC Sim - Ladder", style = MaterialTheme.typography.titleLarge)
            Button(onClick = { running = !running }) {
                Text(if (running) "إيقاف" else "تشغيل المحاكاة")
            }
        }

        Text(
            "اضغط على أي سطر لتعديله",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(rungs) { i, rung ->
                Card(Modifier.fillMaxWidth().clickable { editing = i }) {
                    Column(Modifier.padding(8.dp)) {
                        Text("سطر ${i + 1}", style = MaterialTheme.typography.labelMedium)
                        RungView(rung, state, running)
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = {
                        rungs.add(Rung(listOf(Contact("X0", ContactKind.NO)), "Y0"))
                        editing = rungs.lastIndex
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("+ إضافة سطر") }
            }
        }

        Text("المدخلات", style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            INPUTS.forEach { name ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(name)
                    Switch(
                        checked = inputs[name] ?: false,
                        onCheckedChange = { inputs[name] = it }
                    )
                }
            }
        }

        Text("المخرجات", style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OUTPUTS.forEach { name ->
                val on = state[name] == true
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(name)
                    Box(
                        Modifier
                            .padding(top = 4.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (on) Color(0xFF43A047) else Color(0xFFBDBDBD))
                    )
                }
            }
        }
    }

    val idx = editing
    if (idx != null && idx in rungs.indices) {
        RungEditor(
            rung = rungs[idx],
            onChange = { rungs[idx] = it },
            onDelete = { rungs.removeAt(idx); editing = null },
            onClose = { editing = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RungEditor(
    rung: Rung,
    onChange: (Rung) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit
) {
    var pickName by remember { mutableStateOf("X0") }
    var pickKind by remember { mutableStateOf(ContactKind.NO) }

    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("تم") } },
        dismissButton = { TextButton(onClick = onDelete) { Text("حذف السطر") } },
        title = { Text("تعديل السطر") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ملامس جديد:", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ALL_NAMES.forEach { n ->
                        FilterChip(
                            selected = pickName == n,
                            onClick = { pickName = n },
                            label = { Text(n) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = pickKind == ContactKind.NO,
                        onClick = { pickKind = ContactKind.NO },
                        label = { Text("NO (مفتوح)") }
                    )
                    FilterChip(
                        selected = pickKind == ContactKind.NC,
                        onClick = { pickKind = ContactKind.NC },
                        label = { Text("NC (مغلق)") }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        onChange(rung.copy(contacts = rung.contacts + Contact(pickName, pickKind)))
                    }) { Text("إضافة") }
                    OutlinedButton(
                        enabled = rung.contacts.isNotEmpty(),
                        onClick = { onChange(rung.copy(contacts = rung.contacts.dropLast(1))) }
                    ) { Text("حذف آخر ملامس") }
                }

                Text("الملف (المخرج):", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    COIL_NAMES.forEach { n ->
                        FilterChip(
                            selected = rung.coil == n,
                            onClick = { onChange(rung.copy(coil = n)) },
                            label = { Text(n) }
                        )
                    }
                }
            }
        }
    )
}

@Composable
fun RungView(rung: Rung, state: Map<String, Boolean>, running: Boolean) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 12.sp, color = Color.DarkGray)
    val flow = if (running) LadderEngine.powerFlow(rung, state) else null
    val onColor = Color(0xFF2E7D32)
    val offColor = Color(0xFF757575)
    val stroke = 4f

    Canvas(Modifier.fillMaxWidth().height(90.dp)) {
        val railL = 16f
        val railR = size.width - 16f
        val midY = size.height / 2f
        val n = rung.contacts.size + 1
        val slot = (railR - railL) / n

        drawLine(Color.Black, Offset(railL, 6f), Offset(railL, size.height - 6f), stroke * 1.5f)
        drawLine(Color.Black, Offset(railR, 6f), Offset(railR, size.height - 6f), stroke * 1.5f)

        fun wire(x1: Float, x2: Float, powered: Boolean) {
            drawLine(if (powered) onColor else offColor, Offset(x1, midY), Offset(x2, midY), stroke)
        }

        rung.contacts.forEachIndexed { i, c ->
            val x0 = railL + slot * i
            val x1 = x0 + slot
            val cx = (x0 + x1) / 2f
            val inPower = flow?.get(i) ?: false
            val outPower = flow?.get(i + 1) ?: false
            val symColor = if (outPower && flow != null && inPower) onColor else offColor

            wire(x0, cx - 14f, inPower)
            wire(cx + 14f, x1, outPower)
            drawLine(symColor, Offset(cx - 14f, midY - 16f), Offset(cx - 14f, midY + 16f), stroke)
            drawLine(symColor, Offset(cx + 14f, midY - 16f), Offset(cx + 14f, midY + 16f), stroke)
            if (c.kind == ContactKind.NC) {
                drawLine(symColor, Offset(cx - 18f, midY + 16f), Offset(cx + 18f, midY - 16f), stroke)
            }
            val t = measurer.measure(c.name, labelStyle)
            drawText(t, topLeft = Offset(cx - t.size.width / 2f, midY - 36f))
        }

        val ci = rung.contacts.size
        val x0 = railL + slot * ci
        val x1 = x0 + slot
        val cx = (x0 + x1) / 2f
        val coilOn = flow?.last() == true
        wire(x0, cx - 16f, coilOn)
        wire(cx + 16f, railR, coilOn)
        if (coilOn) {
            drawCircle(Color(0xFFA5D6A7), radius = 16f, center = Offset(cx, midY))
        }
        drawCircle(
            if (coilOn) onColor else offColor,
            radius = 16f,
            center = Offset(cx, midY),
            style = Stroke(stroke)
        )
        val t = measurer.measure(rung.coil, labelStyle)
        drawText(t, topLeft = Offset(cx - t.size.width / 2f, midY - 40f))
    }
}
