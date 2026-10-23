package com.example.plcsim

class SimResult(
    val labels: Map<Int, String>,
    val live: Set<Int>,
    val dir: Map<Int, Int>,
    val short: Boolean,
    val coil: Set<String>,
    val done: Set<String>,
    val tripped: Set<String>
) {
    companion object {
        val EMPTY = SimResult(emptyMap(), emptySet(), emptyMap(), false, emptySet(), emptySet(), emptySet())
    }
}

private class Solved(
    val labels: Map<Int, String>,
    val short: Boolean,
    val coilsOn: Set<String>,
    val timersOn: Set<String>,
    val live: Set<Int>,
    val dir: Map<Int, Int>
)

private val SRC_LABELS = listOf("L1", "L2", "L3", "N")

private fun isForward(a: String, b: String, c: String): Boolean =
    (a == "L1" && b == "L2" && c == "L3") ||
        (a == "L2" && b == "L3" && c == "L1") ||
        (a == "L3" && b == "L1" && c == "L2")

private fun solve(
    comps: List<Comp>,
    wires: List<Wire>,
    idx: Map<Int, Int>,
    n: Int,
    coil: Set<String>,
    done: Set<String>,
    tripped: Set<String>
): Solved {
    val parent = IntArray(n) { it }
    fun find(a: Int): Int {
        var x = a
        while (parent[x] != x) {
            parent[x] = parent[parent[x]]
            x = parent[x]
        }
        return x
    }
    fun union(a: Int, b: Int) {
        val ra = find(a)
        val rb = find(b)
        if (ra != rb) parent[ra] = rb
    }

    val atPoint = HashMap<Pair<Int, Int>, Int>()
    for (c in comps) {
        for (t in c.type.terms.indices) {
            val g = idx[c.id * 100 + t]!!
            val k = termKey(c, t)
            val prev = atPoint[k]
            if (prev == null) atPoint[k] = g else union(prev, g)
        }
    }
    for (w in wires) {
        val a = idx[w.a * 100 + w.ta]
        val b = idx[w.b * 100 + w.tb]
        if (a != null && b != null) union(a, b)
    }
    for (c in comps) {
        if (c.type.pairs.isEmpty()) continue
        if (!isClosed(c, coil, done, tripped)) continue
        for ((i, j) in c.type.pairs) union(idx[c.id * 100 + i]!!, idx[c.id * 100 + j]!!)
    }

    val rootLabels = HashMap<Int, MutableSet<String>>()
    for (c in comps) {
        if (c.type != CType.SRC) continue
        for (t in 0..3) {
            val g = find(idx[c.id * 100 + t]!!)
            rootLabels.getOrPut(g) { HashSet() }.add(SRC_LABELS[t])
        }
    }
    val short = rootLabels.values.any { it.size > 1 }
    if (short) return Solved(emptyMap(), true, emptySet(), emptySet(), emptySet(), emptyMap())

    val labels = HashMap<Int, String>()
    for (c in comps) {
        for (t in c.type.terms.indices) {
            val l = rootLabels[find(idx[c.id * 100 + t]!!)]?.firstOrNull()
            if (l != null) labels[c.id * 100 + t] = l
        }
    }

    val coilsOn = HashSet<String>()
    val timersOn = HashSet<String>()
    val live = HashSet<Int>()
    val dir = HashMap<Int, Int>()
    for (c in comps) {
        when (c.type) {
            CType.COIL, CType.TCOIL, CType.LAMP -> {
                val a = labels[c.id * 100]
                val b = labels[c.id * 100 + 1]
                if (a != null && b != null && a != b) {
                    live.add(c.id)
                    if (c.type == CType.COIL) coilsOn.add(c.tag)
                    if (c.type == CType.TCOIL) timersOn.add(c.tag)
                }
            }
            CType.MOTOR -> {
                val a = labels[c.id * 100]
                val b = labels[c.id * 100 + 1]
                val d = labels[c.id * 100 + 2]
                if (a != null && b != null && d != null &&
                    a != "N" && b != "N" && d != "N" &&
                    a != b && b != d && a != d
                ) {
                    live.add(c.id)
                    dir[c.id] = if (isForward(a, b, d)) 1 else -1
                }
            }
            else -> {}
        }
    }
    return Solved(labels, false, coilsOn, timersOn, live, dir)
}

class Sim {
    private val coil = HashSet<String>()
    private val done = HashSet<String>()
    private val elapsed = HashMap<String, Float>()
    val tripped = HashSet<String>()

    fun reset() {
        coil.clear()
        done.clear()
        elapsed.clear()
        tripped.clear()
    }

    fun step(comps: List<Comp>, wires: List<Wire>, dt: Float): SimResult {
        val idx = HashMap<Int, Int>()
        var n = 0
        for (c in comps) for (t in c.type.terms.indices) {
            idx[c.id * 100 + t] = n
            n += 1
        }
        var s = solve(comps, wires, idx, n, coil, done, tripped)
        var guard = 0
        while (s.coilsOn != coil && guard < 12) {
            coil.clear()
            coil.addAll(s.coilsOn)
            s = solve(comps, wires, idx, n, coil, done, tripped)
            guard += 1
        }
        for (c in comps) {
            if (c.type != CType.TCOIL) continue
            if (c.tag in s.timersOn) {
                val e = (elapsed[c.tag] ?: 0f) + dt
                elapsed[c.tag] = e
                if (e >= c.delay) done.add(c.tag)
            } else {
                elapsed[c.tag] = 0f
                done.remove(c.tag)
            }
        }
        return SimResult(s.labels, s.live, s.dir, s.short, HashSet(coil), HashSet(done), HashSet(tripped))
    }
}
