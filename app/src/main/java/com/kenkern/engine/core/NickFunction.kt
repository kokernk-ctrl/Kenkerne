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

/** Registro das classes nativas da Fase 1 (+ Colisor da T0.2). */
object KenkernBuiltInClasses {
    fun registerAll() {
        NickClassRegistry.register(
            NickClassDefinition(
                "Spin",
                "Gira o Nick continuamente",
                listOf(
                    NickFieldDefinition("speed", "FLOAT", NickValue.FloatValue(45f)),
                    NickFieldDefinition("axis", "STRING", NickValue.StringValue("Y"))
                ),
                categoria = "movimento"
            )
        )
        NickClassRegistry.register(
            NickClassDefinition(
                "Bob",
                "Oscila o Nick verticalmente",
                listOf(
                    NickFieldDefinition("amplitude", "FLOAT", NickValue.FloatValue(0.5f)),
                    NickFieldDefinition("speed", "FLOAT", NickValue.FloatValue(1.5f))
                ),
                categoria = "movimento"
            )
        )
        // T0.2 (adendo v4): classe COLISOR do Collider Body Nick — a forma é
        // CHAVE ESTÁVEL em inglês (box/sphere/capsule/cylinder/convex/mesh;
        // rótulos localizados no seletor do Inspetor na T0.3); centro/tamanho
        // em espaço LOCAL (o ajuste à AABB da malha entra na T0.3).
        NickClassRegistry.register(
            NickClassDefinition(
                "Colisor",
                "Volume de colisão do Nick",
                listOf(
                    NickFieldDefinition("forma", "STRING", NickValue.StringValue("box")),
                    NickFieldDefinition("centro", "VEC3", NickValue.Vec3Value(0f, 0f, 0f)),
                    NickFieldDefinition("tamanho", "VEC3", NickValue.Vec3Value(1f, 1f, 1f))
                ),
                categoria = "fisica"
            )
        )
        // T0.3: classe MALHA registrada também no catálogo (categoria Geometria)
        // — o importador cria a instância direto (criarClasseMalha), e o botão
        // "Adicionar classe" do Inspetor agora a lista com os campos padrão.
        NickClassRegistry.register(
            NickClassDefinition(
                "Malha",
                "Malha .KERN importada (caminho + AABB)",
                listOf(
                    NickFieldDefinition("path", "STRING", NickValue.StringValue("")),
                    NickFieldDefinition("aabbMinX", "FLOAT", NickValue.FloatValue(-0.5f)),
                    NickFieldDefinition("aabbMinY", "FLOAT", NickValue.FloatValue(-0.5f)),
                    NickFieldDefinition("aabbMinZ", "FLOAT", NickValue.FloatValue(-0.5f)),
                    NickFieldDefinition("aabbMaxX", "FLOAT", NickValue.FloatValue(0.5f)),
                    NickFieldDefinition("aabbMaxY", "FLOAT", NickValue.FloatValue(0.5f)),
                    NickFieldDefinition("aabbMaxZ", "FLOAT", NickValue.FloatValue(0.5f)),
                    NickFieldDefinition("escalaFit", "FLOAT", NickValue.FloatValue(1f))
                ),
                categoria = "geometria"
            )
        )
        // H2 (v0.1.20): classe LUZ dos Nicks de luz do LUME — cor/intensidade/
        // alcance (pontual e spot)/ângulo interno e externo (spot)/ligado. Os
        // 3 tipos light.* ganham a instância padrão; Nicks antigos sem a classe
        // usam os defaults de LuzNick (nada quebra, nada crasha).
        NickClassRegistry.register(
            NickClassDefinition(
                "Luz",
                "Luz do LUME (cor, intensidade, alcance, ângulos, ligado)",
                listOf(
                    NickFieldDefinition("cor", "VEC3", NickValue.Vec3Value(1f, 1f, 1f)),
                    NickFieldDefinition("intensidade", "FLOAT", NickValue.FloatValue(1f)),
                    NickFieldDefinition("alcance", "FLOAT", NickValue.FloatValue(8f)),
                    NickFieldDefinition("anguloInterno", "FLOAT", NickValue.FloatValue(25f)),
                    NickFieldDefinition("anguloExterno", "FLOAT", NickValue.FloatValue(40f)),
                    NickFieldDefinition("ligado", "BOOL", NickValue.BoolValue(true))
                ),
                categoria = "iluminacao"
            )
        )
        // H3 (v0.1.20): classe LENTE dos Nicks de câmera — fov/near/far/
        // ortográfica+tamanho, "Câmera principal" (SÓ UMA por cena) e os
        // campos do Follow Camera (alvo, distância, altura). Os 2 tipos
        // camera.* ganham a instância padrão; Nick antigo usa os defaults.
        NickClassRegistry.register(
            NickClassDefinition(
                "Lente",
                "Câmera do Nick (fov, near, far, orto, principal, alvo)",
                listOf(
                    NickFieldDefinition("fov", "FLOAT", NickValue.FloatValue(60f)),
                    NickFieldDefinition("near", "FLOAT", NickValue.FloatValue(0.1f)),
                    NickFieldDefinition("far", "FLOAT", NickValue.FloatValue(500f)),
                    NickFieldDefinition("ortografica", "BOOL", NickValue.BoolValue(false)),
                    NickFieldDefinition("tamanhoOrto", "FLOAT", NickValue.FloatValue(5f)),
                    NickFieldDefinition("principal", "BOOL", NickValue.BoolValue(false)),
                    NickFieldDefinition("alvo", "STRING", NickValue.StringValue("")),
                    NickFieldDefinition("distancia", "FLOAT", NickValue.FloatValue(6f)),
                    NickFieldDefinition("altura", "FLOAT", NickValue.FloatValue(3f))
                ),
                categoria = "camera"
            )
        )
        // M1.1 (v0.1.21): classe SCRIPT do Nick — campo `fonte` = código Lua;
        // os DEMAIS campos são VARIÁVEIS PÚBLICAS (params.<nome> no script e
        // editáveis no Inspetor). Erros desativam o script; o jogo continua.
        NickClassRegistry.register(
            NickClassDefinition(
                "Script",
                "Script Lua do Nick (LUA_API mínima: nick/scene/engine/params)",
                listOf(
                    NickFieldDefinition("fonte", "STRING", NickValue.StringValue(""))
                ),
                categoria = "logica"
            )
        )
        NickFunctionRegistry.register(SpinFunction)
        NickFunctionRegistry.register(BobFunction)
    }
}
