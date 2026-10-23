package com.example.plcsim

import kotlin.math.roundToInt

private fun t2() = listOf(0.5f to 0f, 0.5f to 2f)
private fun t6() = listOf(0.5f to 0f, 1.5f to 0f, 2.5f to 0f, 0.5f to 2f, 1.5f to 2f, 2.5f to 2f)
private fun p1() = listOf(0 to 1)
private fun p3() = listOf(0 to 3, 1 to 4, 2 to 5)
private fun n6() = listOf("1", "3", "5", "2", "4", "6")

enum class CType(
    val title: String,
    val prefix: String,
    val w: Int,
    val h: Int,
    val terms: List<Pair<Float, Float>>,
    val names: List<String>,
    val pairs: List<Pair<Int, Int>> = emptyList(),
    val sym: String = "",
    val defOn: Boolean = false
) {
    SRC("مصدر 3 فاز", "G", 4, 1, listOf(0.5f to 1f, 1.5f to 1f, 2.5f to 1f, 3.5f to 1f), listOf("L1", "L2", "L3", "N")),
    BREAKER3("قاطع 3 قطب", "Q", 3, 2, t6(), n6(), p3(), "3P", true),
    BREAKER1("قاطع 1 قطب", "Q", 1, 2, t2(), listOf("1", "2"), p1(), "1P", true),
    FUSE("فيوز", "FU", 1, 2, t2(), listOf("1", "2"), p1(), "FU", true),
    PB_NO("زر تشغيل NO", "S", 1, 2, t2(), listOf("3", "4"), p1(), "NO"),
    PB_NC("زر إيقاف NC", "S", 1, 2, t2(), listOf("1", "2"), p1(), "NC"),
    ESTOP("طوارئ NC", "S", 1, 2, t2(), listOf("1", "2"), p1(), "E-STOP", true),
    SWITCH("مفتاح NO", "SA", 1, 2, t2(), listOf("1", "2"), p1(), "SW"),
    COIL("ملف كونتاكتور/ريلي", "K", 1, 2, t2(), listOf("A1", "A2")),
    TCOIL("ملف مؤقت", "KT", 1, 2, t2(), listOf("A1", "A2")),
    K_MAIN("ملامسات رئيسية 3P", "K", 3, 2, t6(), n6(), p3(), "3P"),
    K_NO("ملامس مساعد NO", "K", 1, 2, t2(), listOf("13", "14"), p1(), "NO"),
    K_NC("ملامس مساعد NC", "K", 1, 2, t2(), listOf("21", "22"), p1(), "NC"),
    T_NO("ملامس مؤقت NO", "KT", 1, 2, t2(), listOf("17", "18"), p1(), "T-NO"),
    T_NC("ملامس مؤقت NC", "KT", 1, 2, t2(), listOf("15", "16"), p1(), "T-NC"),
    OL_POWER("أوفرلود (قدرة)", "F", 3, 2, t6(), n6(), p3(), "OL"),
    OL_CTRL("أوفرلود (تحكم NC)", "F", 1, 2, t2(), listOf("95", "96"), p1(), "OL"),
    LAMP("لمبة", "H", 1, 2, t2(), listOf("X1", "X2")),
    MOTOR("موتور 3 فاز", "M", 3, 2, listOf(0.5f to 0f, 1.5f to 0f, 2.5f to 0f), listOf("U", "V", "W"))
}

data class Comp(
    val id: Int,
    val type: CType,
    val x: Int,
    val y: Int,
    val tag: String,
    val delay: Float = 3f,
    val on: Boolean = type.defOn,
    val pressed: Boolean = false
)

data class Wire(val a: Int, val ta: Int, val b: Int, val tb: Int, val bx: Float? = null)

fun isClosed(c: Comp, coil: Set<String>, done: Set<String>, tripped: Set<String>): Boolean =
    when (c.type) {
        CType.PB_NO -> c.pressed
        CType.PB_NC -> !c.pressed
        CType.ESTOP, CType.SWITCH, CType.BREAKER3, CType.BREAKER1 -> c.on
        CType.FUSE -> true
        CType.K_NO, CType.K_MAIN -> c.tag in coil
        CType.K_NC -> c.tag !in coil
        CType.T_NO -> c.tag in done
        CType.T_NC -> c.tag !in done
        CType.OL_POWER, CType.OL_CTRL -> c.tag !in tripped
        else -> false
    }

fun termPt(c: Comp, i: Int): Pair<Float, Float> =
    (c.x + c.type.terms[i].first) to (c.y + c.type.terms[i].second)

fun termKey(c: Comp, i: Int): Pair<Int, Int> {
    val p = termPt(c, i)
    return (p.first * 2f).roundToInt() to (p.second * 2f).roundToInt()
}

fun defaultTag(t: CType, comps: List<Comp>): String = when (t) {
    CType.K_NO, CType.K_NC, CType.K_MAIN -> comps.lastOrNull { it.type == CType.COIL }?.tag ?: "K1"
    CType.T_NO, CType.T_NC -> comps.lastOrNull { it.type == CType.TCOIL }?.tag ?: "KT1"
    CType.OL_CTRL -> comps.lastOrNull { it.type == CType.OL_POWER }?.tag ?: "F1"
    CType.COIL, CType.TCOIL, CType.OL_POWER -> t.prefix + (comps.count { it.type == t } + 1)
    else -> t.prefix + (comps.count { it.type.prefix == t.prefix } + 1)
}

fun exampleDol(): Pair<List<Comp>, List<Wire>> {
    var id = 0
    fun comp(t: CType, x: Int, y: Int, tag: String): Comp {
        id += 1
        return Comp(id, t, x, y, tag)
    }
    val src = comp(CType.SRC, 0, 0, "G1")
    val q1 = comp(CType.BREAKER3, 0, 3, "Q1")
    val k1m = comp(CType.K_MAIN, 0, 5, "K1")
    val f1 = comp(CType.OL_POWER, 0, 7, "F1")
    val m1 = comp(CType.MOTOR, 0, 9, "M1")
    val fu = comp(CType.FUSE, 5, 3, "FU1")
    val s0 = comp(CType.PB_NC, 5, 5, "S0")
    val s1 = comp(CType.PB_NO, 5, 7, "S1")
    val k1a = comp(CType.K_NO, 7, 7, "K1")
    val f1c = comp(CType.OL_CTRL, 5, 9, "F1")
    val k1c = comp(CType.COIL, 5, 11, "K1")
    val h1 = comp(CType.LAMP, 8, 11, "H1")
    val ws = mutableListOf<Wire>()
    fun w(a: Comp, ta: Int, b: Comp, tb: Int, bx: Float? = null) {
        ws.add(Wire(a.id, ta, b.id, tb, bx))
    }
    for (i in 0..2) w(src, i, q1, i)
    w(src, 0, fu, 0)
    w(s0, 1, k1a, 0)
    w(k1a, 1, f1c, 0)
    w(f1c, 1, h1, 0)
    w(h1, 1, k1c, 1)
    w(k1c, 1, src, 3, 11f)
    return listOf(src, q1, k1m, f1, m1, fu, s0, s1, k1a, f1c, k1c, h1) to ws
}
