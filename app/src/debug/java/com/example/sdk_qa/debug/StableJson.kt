package com.example.sdk_qa.debug

import org.json.JSONArray
import org.json.JSONObject

/**
 * Serializador JSON determinista (SOLO debug) — compartido por [SessionExporter] y [LayoutExporter].
 *
 * Ordena las claves de cada objeto alfabéticamente para que el diff entre versiones del SDK sea
 * limpio (una línea por campo, sin reordenamientos espurios). Los arrays conservan su orden, que
 * es significativo (timeline de eventos, lista de anchors).
 */
object StableJson {

    /** Pretty-print con claves de objeto ordenadas alfabéticamente. */
    fun pretty(value: Any?, indent: Int = 0): String {
        val pad = "  ".repeat(indent)
        val padIn = "  ".repeat(indent + 1)
        return when (value) {
            is JSONObject -> {
                val keys = value.keys().asSequence().toSortedSet()
                if (keys.isEmpty()) "{}" else buildString {
                    append("{\n")
                    keys.forEachIndexed { i, k ->
                        append(padIn).append(JSONObject.quote(k)).append(": ")
                        append(pretty(value.get(k), indent + 1))
                        append(if (i < keys.size - 1) ",\n" else "\n")
                    }
                    append(pad).append("}")
                }
            }
            is JSONArray -> {
                if (value.length() == 0) "[]" else buildString {
                    append("[\n")
                    for (i in 0 until value.length()) {
                        append(padIn).append(pretty(value.get(i), indent + 1))
                        append(if (i < value.length() - 1) ",\n" else "\n")
                    }
                    append(pad).append("]")
                }
            }
            is String -> JSONObject.quote(value)
            JSONObject.NULL, null -> "null"
            else -> value.toString() // números y booleanos
        }
    }

    /** Sanea un string para usarlo en un nombre de archivo (scenario/version). */
    fun sanitizeForFilename(s: String): String =
        s.replace(Regex("[^A-Za-z0-9._-]"), "-").trim('-').ifEmpty { "x" }
}
