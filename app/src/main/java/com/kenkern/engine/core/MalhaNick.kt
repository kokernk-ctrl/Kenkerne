package com.kenkern.engine.core

/**
 * NICK COM MALHA importada (ETAPA A): o Nick carrega a classe "Malha" criada
 * pelo importador — campos (todos serializados no .ck, nada novo no formato):
 *
 *     path      STRING  caminho RELATIVO do .KERN (ex.: "assets/models/carro.KERN")
 *     aabbMin*  FLOAT   AABB local (para picking/foco SEM carregar a malha)
 *     aabbMax*  FLOAT
 *     escalaFit FLOAT  escala sugerida ("Ajustar ao tamanho" ~2 m; Nick mantém editável)
 *
 * Leitura defensiva: campos ausentes/errados devolvem default (Nick antigo,
 * classe parcial) — a engine NUNCA crasha por classe malformada; falha de
 * carregamento da malha em si é tratada no AssetMeshLibrary com erro claro.
 */
object MalhaNick {

    const val CLASSE = "Malha"

    /** T0.3: classe de COLISÃO do Collider Body Nick (forma/centro/tamanho). */
    const val CLASSE_COLISOR = "Colisor"

    /** Chaves estáveis das formas do colisor (rótulos localizados na UI). */
    val FORMAS_COLISOR = listOf("box", "sphere", "capsule", "cylinder", "convex", "mesh")

    /** Caminho relativo do .KERN ou null se o Nick não tem malha importada. */
    fun path(nick: Nick): String? {
        val inst = nick.findClassInstance(CLASSE) ?: return null
        val p = (inst.fields["path"] as? NickValue.StringValue)?.value ?: return null
        return if (p.isBlank()) null else p
    }

    fun temMalha(nick: Nick): Boolean = path(nick) != null

    /** Meia-extensão LOCAL (half extents) da AABB da malha; cubo (0.5) sem malha. */
    fun meiaExtensaoLocal(nick: Nick, out: FloatArray) {
        val inst = nick.findClassInstance(CLASSE)
        if (inst == null) {
            out[0] = 0.5f; out[1] = 0.5f; out[2] = 0.5f
            return
        }
        val minX = f(inst, "aabbMinX", -0.5f); val minY = f(inst, "aabbMinY", -0.5f); val minZ = f(inst, "aabbMinZ", -0.5f)
        val maxX = f(inst, "aabbMaxX", 0.5f); val maxY = f(inst, "aabbMaxY", 0.5f); val maxZ = f(inst, "aabbMaxZ", 0.5f)
        out[0] = (maxX - minX) * 0.5f
        out[1] = (maxY - minY) * 0.5f
        out[2] = (maxZ - minZ) * 0.5f
    }

    /** Centro LOCAL da AABB da malha (zero para o cubo padrão). */
    fun centroLocal(nick: Nick, out: FloatArray) {
        val inst = nick.findClassInstance(CLASSE)
        if (inst == null) {
            out[0] = 0f; out[1] = 0f; out[2] = 0f
            return
        }
        val minX = f(inst, "aabbMinX", -0.5f); val minY = f(inst, "aabbMinY", -0.5f); val minZ = f(inst, "aabbMinZ", -0.5f)
        val maxX = f(inst, "aabbMaxX", 0.5f); val maxY = f(inst, "aabbMaxY", 0.5f); val maxZ = f(inst, "aabbMaxZ", 0.5f)
        out[0] = (minX + maxX) * 0.5f
        out[1] = (minY + maxY) * 0.5f
        out[2] = (minZ + maxZ) * 0.5f
    }

    /** Escala sugerida pela opção "Ajustar ao tamanho" (1.0 = sem ajuste). */
    fun escalaFit(nick: Nick): Float {
        val inst = nick.findClassInstance(CLASSE) ?: return 1f
        return f(inst, "escalaFit", 1f)
    }

    /**
     * REGRA "CENA = TELA" (Bug A, v0.1.17): o viewport desenha SOMENTE
     * Nicks que REPRESENTAM GEOMETRIA:
     *  - Nick com malha importada (classe Malha com path) → desenha a malha;
     *  - Nick FOLHA sem filhos → desenha o cubo padrão de 1 m (Fase 1).
     * CONTAINERS (Nick sem malha e COM filhos, ex.: a raiz "Mundo") NÃO são
     * desenhados nem pegáveis — hierarquia e tela ficam coerentes: todo objeto
     * visível no viewport aparece na Hierarquia e pode ser selecionado por toque.
     * Puro Kotlin (mesma função no render, no picking e nos testes JVM).
     */
    fun desenhavel(nick: Nick): Boolean =
        temMalha(nick) || nick.children.isEmpty()

    /** Total de Nicks da cena (N da StatusBar "N nicks · M desenhados"). */
    fun contarNicks(scene: Scene): Int {
        var total = 0
        val pilha = ArrayList<Nick>(16)
        for (r in scene.roots) pilha.add(r)
        var head = 0
        while (head < pilha.size) {
            val nick = pilha[head++]
            total++
            for (c in nick.children) pilha.add(c)
        }
        return total
    }

    /**
     * Nicks VISÍVEIS com malha (M esperado da StatusBar): ativos, visíveis e
     * desenhaveis. O "M desenhados" medido pelo renderer precisa ser IGUAL a
     * este número quando todas as malhas estão na GPU (checagem "cena = tela").
     */
    fun contarDesenhaveisVisiveis(scene: Scene): Int {
        var total = 0
        val pilha = ArrayList<Nick>(16)
        for (r in scene.roots) pilha.add(r)
        var head = 0
        while (head < pilha.size) {
            val nick = pilha[head++]
            if (nick.active && nick.visible && desenhavel(nick)) total++
            for (c in nick.children) pilha.add(c)
        }
        return total
    }

    private fun f(inst: NickClassInstance, campo: String, def: Float): Float =
        (inst.fields[campo] as? NickValue.FloatValue)?.value ?: def
}
