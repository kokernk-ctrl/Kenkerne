package com.kenkern.engine.core

/**
 * Valores tipados dos campos de uma classe de Nick. Serializáveis no formato .ck.
 */
sealed class NickValue {
    abstract val typeName: String

    data class IntValue(var value: Int) : NickValue() {
        override val typeName: String get() = "INT"
    }

    data class FloatValue(var value: Float) : NickValue() {
        override val typeName: String get() = "FLOAT"
    }

    data class BoolValue(var value: Boolean) : NickValue() {
        override val typeName: String get() = "BOOL"
    }

    data class StringValue(var value: String) : NickValue() {
        override val typeName: String get() = "STRING"
    }

    data class Vec3Value(var x: Float, var y: Float, var z: Float) : NickValue() {
        override val typeName: String get() = "VEC3"
    }

    /** Representação textual (dentro de value="..." no .ck). */
    fun serializeValue(): String = when (this) {
        is IntValue -> value.toString()
        is FloatValue -> value.toString()
        is BoolValue -> if (value) "true" else "false"
        is StringValue -> value
        is Vec3Value -> "$x,$y,$z"
    }

    fun copyValue(): NickValue = when (this) {
        is IntValue -> IntValue(value)
        is FloatValue -> FloatValue(value)
        is BoolValue -> BoolValue(value)
        is StringValue -> StringValue(value)
        is Vec3Value -> Vec3Value(x, y, z)
    }

    companion object {
        fun parse(type: String, raw: String): NickValue? = when (type.uppercase()) {
            "INT" -> raw.toIntOrNull()?.let { IntValue(it) } ?: IntValue(0)
            "FLOAT" -> raw.toFloatOrNull()?.let { FloatValue(it) } ?: FloatValue(0f)
            "BOOL" -> BoolValue(raw.equals("true", ignoreCase = true))
            "STRING" -> StringValue(raw)
            "VEC3" -> {
                val parts = raw.split(',')
                Vec3Value(
                    parts.getOrNull(0)?.toFloatOrNull() ?: 0f,
                    parts.getOrNull(1)?.toFloatOrNull() ?: 0f,
                    parts.getOrNull(2)?.toFloatOrNull() ?: 0f
                )
            }
            else -> null
        }
    }
}

/** Definição de um campo de classe. */
data class NickFieldDefinition(
    val name: String,
    val type: String,
    val default: NickValue
)

/**
 * Definição de uma classe de Nick: agrupa dados (campos) e FUNÇÕES.
 * Classes nativas são registradas em [NickClassRegistry].
 *
 * T0.3 (adendo v4): [categoria] agrupa o botão "Adicionar classe" do Inspetor
 * por função (Movimento, Geometria, Física…) — chave estável em inglês,
 * rótulo localizado na UI.
 */
class NickClassDefinition(
    val name: String,
    val description: String,
    val fields: List<NickFieldDefinition>,
    val categoria: String = "geral"
) {
    fun createInstance(): NickClassInstance {
        val inst = NickClassInstance(name)
        for (f in fields) inst.fields[f.name] = f.default.copyValue()
        return inst
    }
}

/** Instância de uma classe aplicada a um Nick (dados por instância). */
class NickClassInstance(val typeName: String) {
    val fields = LinkedHashMap<String, NickValue>()

    fun getFloat(name: String, default: Float): Float =
        (fields[name] as? NickValue.FloatValue)?.value ?: default

    fun getInt(name: String, default: Int): Int =
        (fields[name] as? NickValue.IntValue)?.value ?: default

    fun getBool(name: String, default: Boolean): Boolean =
        (fields[name] as? NickValue.BoolValue)?.value ?: default

    fun getString(name: String, default: String): String =
        (fields[name] as? NickValue.StringValue)?.value ?: default

    fun deepCopyFields(): LinkedHashMap<String, NickValue> {
        val out = LinkedHashMap<String, NickValue>()
        for ((k, v) in fields) out[k] = v.copyValue()
        return out
    }
}

/** Registro global de classes conhecidas pela engine. */
object NickClassRegistry {
    private val definitions = LinkedHashMap<String, NickClassDefinition>()

    fun register(definition: NickClassDefinition) {
        definitions[definition.name] = definition
    }

    fun find(typeName: String): NickClassDefinition? = definitions[typeName]

    fun all(): List<NickClassDefinition> = definitions.values.toList()

    /** T0.3: classes agrupadas por CATEGORIA (ordem de registro preservada dentro de cada grupo). */
    fun porCategoria(): LinkedHashMap<String, List<NickClassDefinition>> {
        val out = LinkedHashMap<String, List<NickClassDefinition>>()
        for (def in definitions.values) {
            out.getOrPut(def.categoria) { ArrayList() }.let { lista ->
                (lista as ArrayList).add(def)
            }
        }
        return out
    }

    fun names(): List<String> = definitions.keys.toList()

    fun createInstance(typeName: String): NickClassInstance? = find(typeName)?.createInstance()

    fun clear() = definitions.clear()
}
