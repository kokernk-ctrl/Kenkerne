package com.kenkern.engine.core

/**
 * FUNÇÕES de uma classe: executadas pelo loop da engine quando em modo Play.
 * Implementações NÃO devem alocar por chamada (loop de update, zero alocações).
 */
interface NickFunction {
    val className: String

    fun onSetup(nick: Nick) {}

    fun onUpdate(nick: Nick, deltaTime: Float, engineTime: Float) {}

    fun onDestroy(nick: Nick) {}
}

/** Registro global das funções por nome de classe. */
object NickFunctionRegistry {
    private val functions = HashMap<String, NickFunction>()

    fun register(function: NickFunction) {
        functions[function.className] = function
    }

    fun find(className: String): NickFunction? = functions[className]

    fun clear() = functions.clear()
}

/** Classe nativa "Spin": gira o Nick continuamente. Campos: speed (FLOAT, graus/s), axis (STRING X|Y|Z). */
object SpinFunction : NickFunction {
    override val className: String = "Spin"

    /** Ângulo acumulado por Nick — FloatArray(1) evita boxing de Float a cada passo. */
    private val angles = java.util.WeakHashMap<Nick, FloatArray>()

    override fun onUpdate(nick: Nick, deltaTime: Float, engineTime: Float) {
        val inst = nick.findClassInstance(className) ?: return
        val speed = inst.getFloat("speed", 45f)
        val axis = inst.getString("axis", "Y")
        // Zero alocação por passo: slot pré-alocado; comparação sem uppercase() (String nova).
        val slot = angles.getOrPut(nick) { FloatArray(1) }
        val a = slot[0] + speed * deltaTime
        slot[0] = a
        val rad = Math.toRadians(a.toDouble()).toFloat()
        when {
            axis.equals("X", ignoreCase = true) -> nick.transform.rotation.setFromAxisAngle(1f, 0f, 0f, rad)
            axis.equals("Z", ignoreCase = true) -> nick.transform.rotation.setFromAxisAngle(0f, 0f, 1f, rad)
            else -> nick.transform.rotation.setFromAxisAngle(0f, 1f, 0f, rad)
        }
    }
}

/** Classe nativa "Bob": oscila o Nick verticalmente. Campos: amplitude (FLOAT), speed (FLOAT). */
object BobFunction : NickFunction {
    override val className: String = "Bob"
    private val baseY = java.util.WeakHashMap<Nick, Float>()

    override fun onSetup(nick: Nick) {
        baseY[nick] = nick.transform.position.y
    }

    override fun onUpdate(nick: Nick, deltaTime: Float, engineTime: Float) {
        val inst = nick.findClassInstance(className) ?: return
        val amp = inst.getFloat("amplitude", 0.5f)
        val speed = inst.getFloat("speed", 1.5f)
        val base = baseY.getOrPut(nick) { nick.transform.position.y }
        nick.transform.position.y = base + kotlin.math.sin(engineTime * speed) * amp
    }
}

/** Registro das classes nativas da Fase 1. */
object KenkernBuiltInClasses {
    fun registerAll() {
        NickClassRegistry.register(
            NickClassDefinition(
                "Spin",
                "Gira o Nick continuamente",
                listOf(
                    NickFieldDefinition("speed", "FLOAT", NickValue.FloatValue(45f)),
                    NickFieldDefinition("axis", "STRING", NickValue.StringValue("Y"))
                )
            )
        )
        NickClassRegistry.register(
            NickClassDefinition(
                "Bob",
                "Oscila o Nick verticalmente",
                listOf(
                    NickFieldDefinition("amplitude", "FLOAT", NickValue.FloatValue(0.5f)),
                    NickFieldDefinition("speed", "FLOAT", NickValue.FloatValue(1.5f))
                )
            )
        )
        NickFunctionRegistry.register(SpinFunction)
        NickFunctionRegistry.register(BobFunction)
    }
}
