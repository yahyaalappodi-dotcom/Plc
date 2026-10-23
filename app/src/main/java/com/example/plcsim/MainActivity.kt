package com.example.plcsim

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.hypot
import kotlin.math.roundToInt

private val INK = Color(0xFF263238)
private val GREEN = Color(0xFF2E7D32)
private val RED = Color(0xFFD32F2F)
private val IDLE = Color(0xFF78909C)
private val BODY = Color(0xFFECEFF1)
private val BODY_ON = Color(0xFFA5D6A7)

enum class Tool(val label: String) {
    MOVE("تحريك"),
    WIRE("توصيل"),
    DELETE("حذف"),
    RUN("تشغيل ▶")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { CircuitApp() }
            }
        }
    }
}

private fun labelColor(l: String): Color = when (l) {
    "L1" -> Color(0xFFE53935)
    "L2" -> Color(0xFFF9A825)
    "L3" -> Color(0xFF1E88E5)
    else -> Color(0xFF00897B)
}

private fun hitComp(c: Comp, p: Offset): Boolean =
    p.x >= c.x - 0.1f && p.x <= c.x + c.type.w + 0.1f &&
        p.y >= c.y - 0.1f && p.y <= c.y + c.type.h + 0.1f

private fun hitTerm(comps: List<Comp>, p: Offset): Pair<Int, Int>? {
    var best: Pair<Int, Int>? = null
    var bd = 0.5f
    for (c in comps) {
        for (i in c.type.terms.indices) {
            val q = termPt(c, i)
            val d = hypot(p.x - q.first, p.y - q.second)
            if (d < bd) {
                bd = d
                best = c.id to i
            }
        }
    }
    return best
}

private fun wirePts(ca: Comp, ta: Int, cb: Comp, tb: Int, bx: Float?): List<Offset> {
    val qa = termPt(ca, ta)
    val qb = termPt(cb, tb)
    val a = Offset(qa.first, qa.second)
    val b = Offset(qb.first, qb.second)
    return if (bx != null) {
        listOf(
            a, Offset(a.x, a.y + 0.5f), Offset(bx, a.y + 0.5f),
            Offset(bx, b.y + 0.7f), Offset(b.x, b.y + 0.7f), b
        )
    } else {
        val m = (a.y + b.y) / 2f
        listOf(a, Offset(a.x, m), Offset(b.x, m), b)
    }
}

private fun distSeg(p: Offset, a: Offset, b: Offset): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val l2 = dx * dx + dy * dy
    val t = if (l2 == 0f) 0f else (((p.x - a.x) * dx + (p.y - a.y) * dy) / l2).coerceIn(0f, 1f)
    return hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy))
}

private fun wireHit(comps: List<Comp>, w: Wire, p: Offset): Boolean {
    val ca = comps.firstOrNull { it.id == w.a } ?: return false
    val cb = comps.firstOrNull { it.id == w.b } ?: return false
    val pts = wirePts(ca, w.ta, cb, w.tb, w.bx)
    for (k in 0 until pts.size - 1) {
        if (distSeg(p, pts[k], pts[k + 1]) < 0.3f) return true
    }
    return false
}

private fun DrawScope.lbl(m: TextMeasurer, s: String, at: Offset, size: Float = 10f, color: Color = INK) {
    val r = m.measure(s, TextStyle(fontSize = size.sp, color = color))
    drawText(r, topLeft = at)
}

private fun DrawScope.lblC(m: TextMeasurer, s: String, at: Offset, size: Float = 10f, color: Color = INK) {
    val r = m.measure(s, TextStyle(fontSize = size.sp, color = color))
    drawText(r, topLeft = Offset(at.x - r.size.width / 2f, at.y - r.size.height / 2f))
}

private fun DrawScope.drawGrid(cell: Float, pan: Offset) {
    val sx = ((pan.x % cell) + cell) % cell
    val sy = ((pan.y % cell) + cell) % cell
    var gx = sx
    while (gx < size.width) {
        var gy = sy
        while (gy < size.height) {
            drawCircle(Color(0x33000000), 1.5f, Offset(gx, gy))
            gy += cell
        }
        gx += cell
    }
}

private fun DrawScope.drawWire(
    w: Wire, comps: List<Comp>, res: SimResult, running: Boolean, cell: Float, pan: Offset
) {
    val ca = comps.firstOrNull { it.id == w.a } ?: return
    val cb = comps.firstOrNull { it.id == w.b } ?: return
    val pts = wirePts(ca, w.ta, cb, w.tb, w.bx)
    val lab = res.labels[w.a * 100 + w.ta]
    val col = if (running && lab != null) labelColor(lab) else IDLE
    for (k in 0 until pts.size - 1) {
        val s = Offset(pan.x + pts[k].x * cell, pan.y + pts[k].y * cell)
        val e = Offset(pan.x + pts[k + 1].x * cell, pan.y + pts[k + 1].y * cell)
        drawLine(col, s, e, cell * 0.1f)
    }
}

private fun DrawScope.drawComp(
    c: Comp, res: SimResult, running: Boolean, cell: Float, pan: Offset, m: TextMeasurer
) {
    fun p(x: Float, y: Float) = Offset(pan.x + (c.x + x) * cell, pan.y + (c.y + y) * cell)
    val t = c.type
    val sw = cell * 0.09f
    val live = c.id in res.live

    if (t.pairs.isNotEmpty()) {
        val closed = isClosed(c, res.coil, res.done, res.tripped)
        val isOl = t == CType.OL_POWER || t == CType.OL_CTRL
        val col = when {
            isOl && !closed -> RED
            closed && running -> GREEN
            else -> INK
        }
        for (pr in t.pairs) {
            val x = t.terms[pr.first].first
            drawLine(INK, p(x, 0f), p(x, 0.6f), sw)
            drawLine(INK, p(x, 1.4f), p(x, 2f), sw)
            if (closed) {
                drawLine(col, p(x, 1.4f), p(x, 0.6f), sw)
            } else {
                drawLine(col, p(x, 1.4f), p(x - 0.4f, 0.75f), sw)
            }
            drawCircle(INK, sw * 1.3f, p(x, 0.6f))
            drawCircle(INK, sw * 1.3f, p(x, 1.4f))
        }
        if (t.pairs.size > 1) drawLine(IDLE, p(0.5f, 1f), p(2.5f, 1f), sw * 0.5f)
    } else {
        when (t) {
            CType.COIL, CType.TCOIL -> {
                drawLine(INK, p(0.5f, 0f), p(0.5f, 0.5f), sw)
                drawLine(INK, p(0.5f, 1.5f), p(0.5f, 2f), sw)
                drawRect(
                    color = if (live) BODY_ON else BODY,
                    topLeft = p(0.1f, 0.5f), size = Size(0.8f * cell, cell)
                )
                drawRect(
                    color = if (live) GREEN else INK,
                    topLeft = p(0.1f, 0.5f), size = Size(0.8f * cell, cell), style = Stroke(sw)
                )
            }
            CType.LAMP -> {
                drawLine(INK, p(0.5f, 0f), p(0.5f, 0.55f), sw)
                drawLine(INK, p(0.5f, 1.45f), p(0.5f, 2f), sw)
                val ctr = p(0.5f, 1f)
                val r = 0.42f * cell
                drawCircle(if (live) Color(0xFFFFEB3B) else BODY, r, ctr)
                drawCircle(INK, r, ctr, style = Stroke(sw))
                drawLine(INK, p(0.2f, 0.7f), p(0.8f, 1.3f), sw * 0.7f)
                drawLine(INK, p(0.8f, 0.7f), p(0.2f, 1.3f), sw * 0.7f)
            }
            CType.MOTOR -> {
                for (i in 0..2) drawLine(INK, p(t.terms[i].first, 0f), p(t.terms[i].first, 0.5f), sw)
                drawRect(
                    color = if (live) BODY_ON else BODY,
                    topLeft = p(0.1f, 0.5f), size = Size(2.8f * cell, 1.45f * cell)
                )
                drawRect(
                    color = if (live) GREEN else INK,
                    topLeft = p(0.1f, 0.5f), size = Size(2.8f * cell, 1.45f * cell), style = Stroke(sw)
                )
                val d = res.dir[c.id]
                val txt = if (running && live) (if (d == 1) "M  FWD" else "M  REV") else "M  3~"
                lblC(m, txt, p(1.5f, 1.2f), 12f)
            }
            CType.SRC -> {
                drawRect(color = Color(0xFFE3F2FD), topLeft = p(0f, 0f), size = Size(4f * cell, cell))
                drawRect(color = INK, topLeft = p(0f, 0f), size = Size(4f * cell, cell), style = Stroke(sw))
                lblC(m, "3~ 380/220V", p(2f, 0.22f), 10f)
            }
            else -> {}
        }
    }

    for (i in t.terms.indices) {
        val tx = t.terms[i].first
        val ty = t.terms[i].second
        val lab = res.labels[c.id * 100 + i]
        val dot = if (running && lab != null) labelColor(lab) else IDLE
        drawCircle(dot, cell * 0.13f, p(tx, ty))
        val nx = if (t == CType.SRC) tx - 0.17f else tx + 0.12f
        val ny = if (ty < 0.5f) ty + 0.05f else ty - 0.5f
        lbl(m, t.names[i], p(nx, ny), 8f, IDLE)
    }
    val txt = if (t.sym.isNotEmpty()) c.tag + "\n" + t.sym else c.tag
    lbl(m, txt, p(t.w + 0.08f, 0.4f), 10f)
}

@Composable
fun CircuitApp() {
    val comps = remember { mutableStateListOf<Comp>() }
    val wires = remember { mutableStateListOf<Wire>() }
    var tool by remember { mutableStateOf(Tool.MOVE) }
    var pan by remember { mutableStateOf(Offset(8f, 8f)) }
    var cell by remember { mutableStateOf(60f) }
    var wireStart by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var editId by remember { mutableStateOf<Int?>(null) }
    val sim = remember { Sim() }
    var result by remember { mutableStateOf(SimResult.EMPTY) }
    val measurer = rememberTextMeasurer()

    fun loadExample() {
        val ex = exampleDol()
        comps.clear()
        wires.clear()
        comps.addAll(ex.first)
        wires.addAll(ex.second)
        pan = Offset(8f, 8f)
    }

    fun addComp(t: CType) {
        val nid = (comps.maxOfOrNull { it.id } ?: 0) + 1
        val wx = (-pan.x / cell).roundToInt() + 1
        val wy = (-pan.y / cell).roundToInt() + 1
        comps.add(Comp(nid, t, wx, wy, defaultTag(t, comps.toList())))
    }

    fun setPressed(id: Int, v: Boolean) {
        val i = comps.indexOfFirst { it.id == id }
        if (i >= 0) comps[i] = comps[i].copy(pressed = v)
    }

    fun handleTap(o: Offset) {
        val p = Offset((o.x - pan.x) / cell, (o.y - pan.y) / cell)
        when (tool) {
            Tool.MOVE -> {
                val c = comps.lastOrNull { hitComp(it, p) }
                if (c != null) editId = c.id
            }
            Tool.WIRE -> {
                val tt = hitTerm(comps, p)
                val ws = wireStart
                if (tt == null) {
                    wireStart = null
                } else if (ws == null) {
                    wireStart = tt
                } else {
                    val dup = wires.any {
                        (it.a == ws.first && it.ta == ws.second && it.b == tt.first && it.tb == tt.second) ||
                            (it.a == tt.first && it.ta == tt.second && it.b == ws.first && it.tb == ws.second)
                    }
                    if (ws != tt && !dup) wires.add(Wire(ws.first, ws.second, tt.first, tt.second))
                    wireStart = null
                }
            }
            Tool.DELETE -> {
                val c = comps.lastOrNull { hitComp(it, p) }
                if (c != null) {
                    comps.removeAll { it.id == c.id }
                    wires.removeAll { it.a == c.id || it.b == c.id }
                } else {
                    val wi = wires.indexOfFirst { wireHit(comps, it, p) }
                    if (wi >= 0) wires.removeAt(wi)
                }
            }
            Tool.RUN -> {
                val c = comps.lastOrNull { hitComp(it, p) }
                if (c != null) {
                    when (c.type) {
                        CType.ESTOP, CType.SWITCH, CType.BREAKER3, CType.BREAKER1 -> {
                            val i = comps.indexOfFirst { it.id == c.id }
                            if (i >= 0) comps[i] = comps[i].copy(on = !comps[i].on)
                        }
                        CType.OL_POWER, CType.OL_CTRL -> {
                            if (!sim.tripped.remove(c.tag)) sim.tripped.add(c.tag)
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) { loadExample() }

    LaunchedEffect(tool == Tool.RUN) {
        if (tool == Tool.RUN) {
            sim.reset()
            while (true) {
                result = sim.step(comps.toList(), wires.toList(), 0.05f)
                delay(50)
            }
        } else {
            result = SimResult.EMPTY
        }
    }

    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Tool.values().forEach { t ->
                FilterChip(
                    selected = tool == t,
                    onClick = { tool = t; wireStart = null },
                    label = { Text(t.label) }
                )
            }
        }

        if (tool != Tool.RUN) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item { Button(onClick = { loadExample() }) { Text("مثال: تشغيل موتور") } }
                item {
                    OutlinedButton(onClick = { comps.clear(); wires.clear() }) { Text("مسح الكل") }
                }
                items(CType.values().toList()) { t ->
                    OutlinedButton(onClick = { addComp(t) }) { Text(t.title) }
                }
            }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { cell = it.width / 12f }
        ) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .pointerInput(tool) {
                        detectTapGestures(
                            onPress = { o ->
                                if (tool == Tool.RUN) {
                                    val p = Offset((o.x - pan.x) / cell, (o.y - pan.y) / cell)
                                    val c = comps.lastOrNull { hitComp(it, p) }
                                    if (c != null && (c.type == CType.PB_NO || c.type == CType.PB_NC)) {
                                        setPressed(c.id, true)
                                        tryAwaitRelease()
                                        setPressed(c.id, false)
                                    }
                                }
                            },
                            onTap = { o -> handleTap(o) }
                        )
                    }
                    .pointerInput(tool) {
                        var dragId: Int? = null
                        var sx = 0
                        var sy = 0
                        var acc = Offset.Zero
                        detectDragGestures(
                            onDragStart = { o ->
                                dragId = null
                                if (tool == Tool.MOVE) {
                                    val p = Offset((o.x - pan.x) / cell, (o.y - pan.y) / cell)
                                    val c = comps.lastOrNull { hitComp(it, p) }
                                    if (c != null) {
                                        dragId = c.id
                                        sx = c.x
                                        sy = c.y
                                        acc = Offset.Zero
                                    }
                                }
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                val id = dragId
                                if (id != null) {
                                    acc += amount
                                    val i = comps.indexOfFirst { it.id == id }
                                    if (i >= 0) {
                                        comps[i] = comps[i].copy(
                                            x = sx + (acc.x / cell).roundToInt(),
                                            y = sy + (acc.y / cell).roundToInt()
                                        )
                                    }
                                } else {
                                    pan += amount
                                }
                            },
                            onDragEnd = { dragId = null },
                            onDragCancel = { dragId = null }
                        )
                    }
            ) {
                val running = tool == Tool.RUN
                val res = result
                drawGrid(cell, pan)
                for (w in wires) drawWire(w, comps, res, running, cell, pan)
                for (c in comps) drawComp(c, res, running, cell, pan, measurer)
                val ws = wireStart
                if (ws != null) {
                    val cc = comps.firstOrNull { it.id == ws.first }
                    if (cc != null) {
                        val q = termPt(cc, ws.second)
                        drawCircle(
                            Color(0xFF6200EE), cell * 0.3f,
                            Offset(pan.x + q.first * cell, pan.y + q.second * cell),
                            style = Stroke(4f)
                        )
                    }
                }
            }
            if (result.short) {
                Text(
                    "⚠ ماس كهربائي! تم فصل الدائرة",
                    color = RED,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .background(Color(0xDDFFFFFF))
                        .padding(6.dp)
                )
            }
        }

        Text(
            when (tool) {
                Tool.MOVE -> "اسحب مكوّناً لتحريكه، اضغط عليه لتعديل اسمه، واسحب الفراغ لتحريك الشاشة. الأطراف المتلامسة متصلة تلقائياً."
                Tool.WIRE -> "اضغط طرفاً ثم طرفاً ثانياً لتوصيلهما بسلك."
                Tool.DELETE -> "اضغط على مكوّن أو سلك لحذفه."
                Tool.RUN -> "اضغط الأزرار والمفاتيح. اضغط الأوفرلود لتفعيل/إلغاء الفصل الحراري."
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp)
        )
    }

    val ec = comps.firstOrNull { it.id == editId }
    if (ec != null) {
        var tagText by remember(ec.id) { mutableStateOf(ec.tag) }
        var delayText by remember(ec.id) { mutableStateOf(ec.delay.toString()) }
        AlertDialog(
            onDismissRequest = { editId = null },
            confirmButton = {
                TextButton(onClick = {
                    val i = comps.indexOfFirst { it.id == ec.id }
                    if (i >= 0) {
                        comps[i] = comps[i].copy(
                            tag = tagText.trim(),
                            delay = delayText.toFloatOrNull() ?: ec.delay
                        )
                    }
                    editId = null
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { editId = null }) { Text("إلغاء") } },
            title = { Text(ec.type.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = tagText,
                        onValueChange = { tagText = it },
                        label = { Text("الاسم (Tag)") },
                        singleLine = true
                    )
                    if (ec.type == CType.TCOIL) {
                        OutlinedTextField(
                            value = delayText,
                            onValueChange = { delayText = it },
                            label = { Text("التأخير (ثانية)") },
                            singleLine = true
                        )
                    }
                    Text(
                        "الأجزاء التي لها نفس الاسم مرتبطة ببعض (مثلاً ملف K1 وملامساته K1).",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        )
    }
}
