package com.kenkern.engine.assets.import

import java.util.Locale

/**
 * CRONÔMETRO DA IMPORTAÇÃO (B1, v0.1.17): mede CADA etapa da importação em ms
 * e monta a tabela com % do total que vai para o ImportReport e para o Console
 * (KenkernLog). REGRA B1: sem medição, nenhuma afirmação sobre onde está o
 * gargalo — então TODA etapa pesada é medida no ponto exato onde roda:
 *
 *   leitura do arquivo (EditorViewModel, cópia em streaming)
 *   parse glTF/FBX (ModelImporter → Gltf/FbxImporter)
 *   dedup + LODs (PosProcessamento)
 *   decodificação/redimensionamento/mipmaps/ETC2/gravação de CADA textura
 *   (KimgConversor → KimgPipeline → KimgWriter)
 *   gravação do .KERN (KernWriter) e criação do Nick (ImportModelCommand)
 *
 * Puro Kotlin (sem android.*): os mesmos números da JVM de teste e do aparelho.
 * A THREAD não importa para a medição (nanoTime monotônico por chamada).
 */
class CronometroImport {

    /** Uma etapa medida (nome → ms). */
    class Etapa(val nome: String, val ms: Long, val detalhe: String = "") {
        fun linha(totalMs: Long): String {
            val pct = if (totalMs > 0) (ms * 100.0 / totalMs) else 0.0
            return String.format(
                Locale.US, "  %-34s %6d ms (%5.1f%%)%s",
                nome, ms, pct, if (detalhe.isEmpty()) "" else "  · $detalhe"
            )
        }
    }

    private val inicioParede = System.nanoTime()
    private val etapas = ArrayList<Etapa>(32)

    /** Mede um bloco como UMA etapa (nome único; bloco não pode aninhar medir()). */
    inline fun <T> medir(nome: String, detalhe: String = "", bloco: () -> T): T {
        val t0 = System.nanoTime()
        try {
            return bloco()
        } finally {
            registrar(nome, (System.nanoTime() - t0) / 1_000_000L, detalhe)
        }
    }

    /** Registra uma etapa já medida em outro ponto (ms prontos). */
    @Synchronized
    fun registrar(nome: String, ms: Long, detalhe: String = "") {
        etapas.add(Etapa(nome, ms.coerceAtLeast(0), detalhe))
    }

    /** Soma das etapas medidas (o "total medido" da tabela). */
    @Synchronized
    fun somaEtapasMs(): Long = etapas.sumOf { it.ms }

    /** Tempo de parede desde a criação do cronômetro (a operação INTEIRA). */
    fun paredeMs(): Long = (System.nanoTime() - inicioParede) / 1_000_000L

    /** Cópia das etapas (para testes e para o relatório). */
    @Synchronized
    fun copiaEtapas(): List<Etapa> = ArrayList(etapas)

    /**
     * TABELA completa (B1): uma linha por etapa com ms e % do total MEDIDO +
     * linha de total (medido e de parede — a diferença é a cola não medida,
     * nunca escondida). Texto monoespaçado pronto para o Console/relatório.
     */
    @Synchronized
    fun tabela(): String = buildString {
        val total = somaEtapasMs()
        appendLine("Tempo por etapa (total medido ${total} ms · parede ${paredeMs()} ms):")
        for (e in etapas) appendLine(e.linha(total))
        val pct = if (total > 0) "100,0%" else "—"
        appendLine(String.format(Locale.US, "  %-34s %6d ms (%s)", "TOTAL medido", total, pct))
    }

    /** Etapa mais cara (gargalo apontado com número, sem achismo). */
    @Synchronized
    fun gargalo(): Etapa? = etapas.maxByOrNull { it.ms }
}
