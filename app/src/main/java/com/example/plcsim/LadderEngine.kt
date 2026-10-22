package com.example.plcsim

enum class ContactKind { NO, NC }

data class Contact(val name: String, val kind: ContactKind)

data class Rung(val contacts: List<Contact>, val coil: String)

object LadderEngine {

    private fun passes(c: Contact, state: Map<String, Boolean>): Boolean {
        val v = state[c.name] ?: false
        return if (c.kind == ContactKind.NO) v else !v
    }

    fun powerFlow(rung: Rung, state: Map<String, Boolean>): List<Boolean> {
        val flow = ArrayList<Boolean>(rung.contacts.size + 1)
        var p = true
        flow.add(p)
        for (c in rung.contacts) {
            p = p && passes(c, state)
            flow.add(p)
        }
        return flow
    }

    fun scan(rungs: List<Rung>, inputs: Map<String, Boolean>): Map<String, Boolean> {
        val state = HashMap<String, Boolean>(inputs)
        for (r in rungs) {
            state[r.coil] = powerFlow(r, state).last()
        }
        return state
    }
}
