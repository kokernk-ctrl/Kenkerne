package com.kenkern.engine.assets.image

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * CODEC ETC2 em Kotlin puro (ETAPA C — F2.9): encoder que roda NO APARELHO e
 * decoder usado no fallback RGBA8, nos testes de ida e volta da JVM e na
 * checagem "etc2" do Diagnóstico (upload + leitura de pixel em GL offscreen).
 *
 * CONVENÇÃO ÚNICA de bits (conferida contra o ETCPACK/etcdec da Ericsson):
 * cada bloco de 64 bits é partido em DUAS palavras de 32 bits, onde
 * palavra1 = bits 63..32 do bloco e palavra2 = bits 31..0. Todas as funções
 * aqui leem/escrevem posições como BIT DO BLOCO (63..0) e convertem
 * internamente: campo em palavra1 usa (pos − 32); campo em palavra2 usa pos.
 *
 * Layout RGB (bloco 64 bits):
 *  - diff = bit 33, flip = bit 32, cw1 = bits 39..37, cw2 = bits 36..34;
 *  - INDIVIDUAL (diff=0): R1(4)@63..60 R2(4)@59..56 G1(4)@55..52 G2(4)@51..48
 *    B1(4)@47..44 B2(4)@43..40;
 *  - DIFERENCIAL (diff=1): R1'(5)@63..59 dR2(3)@58..56 G1'(5)@55..51
 *    dG2(3)@50..48 B1'(5)@47..43 dB2(3)@42..40;
 *  - T/H/PLANAR: empacotamento "twotimer" (stuffs abaixo), escolhidos pelo
 *    decoder via overflow 5 bits de R/G/B (base1+delta fora de [0,31]);
 *  - ÍNDICES de textel (bits 31..0, todos os modos): texel (x,y) usa MSB no
 *    bit (y+x*4)+16 e LSB no bit (y+x*4); modificador = unscramble(valor).
 *
 * Decisão de qualidade (DECISIONS #21): encoder escolhe o modo de MENOR erro
 * por bloco; PSNR medido por textura e registrado no relatório. CANCELAMENTO
 * checado a cada linha de blocos.
 *
 * B2 (v0.1.17):
 *  - [Qualidade.RAPIDA] por padrão na importação: individual + diferencial +
 *    planar por bloco (T/H só em [Qualidade.ALTA] — "Alta qualidade" no diálogo);
 *  - BLOCOS paralelizados em N threads (availableProcessors − 1) — faixas de
 *    LINHAS de blocos independentes, saída por faixas disjuntas; fallback
 *    sequencial automático quando o pool não existe (1 thread/falha);
 *  - cancelamento checado A CADA LINHA de blocos em cada worker (responde em
 *    menos de 1 s em qualquer textura realista).
 */
object Etc2 {

    /**
     * Modo do encoder (B2b): RAPIDA = individual + diferencial + planar
     * (padrão da importação); ALTA = acrescenta os modos T e H por bloco
     * (busca mais cara, PSNR maior em blocos complexos).
     */
    enum class Qualidade {
        RAPIDA, ALTA
    }

    /** Threads do encoder (availableProcessors − 1, mínimo 1, teto 8). */
    val threadsEncoder: Int =
        (Runtime.getRuntime().availableProcessors() - 1).coerceIn(1, 8)

    /**
     * Pool fixo do encoder (daemon, prioridade abaixo da UI). Null = 1 thread
     * ou falha ao criar (fallback sequencial AUTOMÁTICO, com registro).
     */
    private val pool: java.util.concurrent.ExecutorService? =
        if (threadsEncoder > 1) {
            runCatching {
                java.util.concurrent.Executors.newFixedThreadPool(threadsEncoder) { r ->
                    Thread(r, "kenkern-etc2").apply {
                        isDaemon = true
                        priority = Thread.NORM_PRIORITY - 1
                    }
                }
            }.onFailure {
                com.kenkern.engine.diag.KenkernLog.w(
                    "Kenkern/Etc2", "pool do encoder não criou — blocos codificam em 1 thread", it
                )
            }.getOrNull()
        } else null

    /** True quando o encoder roda com mais de 1 thread (para o Diagnóstico). */
    fun paralelo(): Boolean = pool != null

    /**
     * SEAM DE TESTE (B4, v0.1.17): força o caminho KOTLIN mesmo com o núcleo
     * nativo carregado — os testes do encoder Kotlin rodam com a biblioteca
     * do HOST carregada (mesmo padrão do ImportModelCommand.testeFalhaNoMove).\n     * Nunca setado em produção.
     */
    @Volatile
    internal var testeForcarKotlin = false

    // ------------------------------------------------------------- tabelas

    /** Modificadores: cw c (3 bits) → linha c*4 .. c*4+3 do array. */
    private val MODS = intArrayOf(
        -8, -2, 2, 8,
        -17, -5, 5, 17,
        -29, -9, 9, 29,
        -42, -13, 13, 42,
        -60, -18, 18, 60,
        -80, -24, 24, 80,
        -106, -33, 33, 106,
        -183, -47, 47, 183
    )

    private val TABELA_TH = intArrayOf(3, 6, 11, 16, 23, 32, 41, 64)

    /** unscramble do decoder: valor armazenado → índice do modificador. */
    private val UNSCRAMBLE = intArrayOf(2, 3, 1, 0)

    /** inverso: índice do modificador → valor armazenado. */
    private val UNSCRAMBLE_INV = intArrayOf(3, 2, 0, 1)

    private val ALPHA_BASE = arrayOf(
        intArrayOf(-15, -9, -6, -3), intArrayOf(-13, -10, -7, -3),
        intArrayOf(-13, -8, -5, -2), intArrayOf(-13, -6, -4, -2),
        intArrayOf(-12, -8, -6, -3), intArrayOf(-11, -9, -7, -3),
        intArrayOf(-11, -8, -7, -4), intArrayOf(-11, -8, -5, -3),
        intArrayOf(-10, -8, -6, -2), intArrayOf(-10, -8, -5, -2),
        intArrayOf(-10, -8, -4, -2), intArrayOf(-10, -7, -5, -2),
        intArrayOf(-10, -7, -4, -3), intArrayOf(-10, -3, -2, -1),
        intArrayOf(-9, -8, -6, -4), intArrayOf(-9, -7, -5, -3)
    )

    /** alphaTable[256][8] (chave = table<<4 | mul), como no ETCPACK. */
    private val ALPHA_TABELA = buildAlphaTable()

    private fun buildAlphaTable(): IntArray {
        val tab = IntArray(256 * 8)
        for (i in 16 until 32) {
            for (j in 0 until 8) {
                val buf = ALPHA_BASE[i - 16][3 - j % 4]
                tab[i * 8 + j] = if (j < 4) buf else -buf - 1
            }
        }
        for (i in 0 until 256) {
            val mul = i / 16
            val old = 16 + i % 16
            for (j in 0 until 8) {
                tab[i * 8 + j] = tab[old * 8 + j] * mul
            }
        }
        return tab
    }

    // ------------------------------------------------------------- leitura de campos

    /** Campo de PALAVRA1 (bits 63..32 do bloco): [pos] é a posição NO BLOCO. */
    private fun c1(w1: Long, pos: Int, bits: Int): Int =
        ((w1 ushr (pos - 32 - bits + 1)) and ((1L shl bits) - 1)).toInt()

    /** Campo de PALAVRA2 (bits 31..0 do bloco). */
    private fun c2(w2: Long, pos: Int, bits: Int): Int =
        ((w2 ushr (pos - bits + 1)) and ((1L shl bits) - 1)).toInt()

    private fun s1(w1: Long, pos: Int, bits: Int, v: Int): Long {
        val shift = pos - 32 - bits + 1
        val mask = ((1L shl bits) - 1) shl shift
        return (w1 and mask.inv()) or ((v.toLong() shl shift) and mask)
    }

    private fun s2(w2: Long, pos: Int, bits: Int, v: Int): Long {
        val shift = pos - bits + 1
        val mask = ((1L shl bits) - 1) shl shift
        return (w2 and mask.inv()) or ((v.toLong() shl shift) and mask)
    }

    /** Campo de 3 bits com sinal (deltas). */
    private fun c3s(w1: Long, pos: Int): Int {
        val v = c1(w1, pos, 3)
        return if (v >= 4) v - 8 else v
    }

    private fun bit2(w2: Long, pos: Int): Int = ((w2 ushr pos) and 1L).toInt()

    // ------------------------------------------------------------- encoder API

    /** Bloco 4×4 (16 texels; r/g/b/a por texel). */
    class BlocoEntrada(val r: IntArray, val g: IntArray, val b: IntArray, val a: IntArray)

    /** Codifica UM bloco RGB 4×4 → 8 bytes (escolhe o modo de menor erro). */
    fun codificarBlocoRgb(bloco: BlocoEntrada, qualidade: Qualidade = Qualidade.ALTA): ByteArray {
        val saida = ByteArray(8)

        var melhorErro = Long.MAX_VALUE
        var melhorW1 = 0L
        var melhorW2 = 0L

        // ---- individual + diferencial (metades por coluna e por linha)
        for (flip in 0..1) {
            val metas = metades(flip)
            val idxA = metas[0]
            val idxB = metas[1]
            val mA = media(idxA, bloco)
            val mB = media(idxB, bloco)

            // bases candidatas por metade: média + centro min/max (halves com
            // contraste se beneficiam; custo ×2 na busca de cw)
            val candA = basesCandidatas(idxA, bloco, 16)
            val candB = basesCandidatas(idxB, bloco, 16)

            // INDIVIDUAL (444): candidatos são cores 8-bit → quantiza para 4 bits
            for (c1i in candA.indices) {
                for (c2i in candB.indices) {
                    val b1 = quantizar444(candA[c1i])
                    val b2 = quantizar444(candB[c2i])
                    for (cw1 in 0..7) {
                        for (cw2 in 0..7) {
                            val e = erroPar(idxA, idxB, bloco, b1, cw1, b2, cw2, escala444 = true)
                            if (e < melhorErro) {
                                melhorErro = e
                                melhorW1 = montarIndividual(b1, b2, cw1, cw2, flip)
                                melhorW2 = indicesFlip(flip, bloco, b1, cw1, b2, cw2, escala444 = true)
                            }
                        }
                    }
                }
            }

            // DIFERENCIAL (555 + 333): base 1 = candidato, base 2 = base1 + delta
            // (a base 2 É derivada — por isso só q1 varia entre os candidatos)
            for (c1i in candA.indices) {
                val qa = quantizar555(candA[c1i])
                val qb = quantizar555(candB[c1i % candB.size])
                val delta = intArrayOf(
                    (qb[0] - qa[0]).coerceIn(-4, 3),
                    (qb[1] - qa[1]).coerceIn(-4, 3),
                    (qb[2] - qa[2]).coerceIn(-4, 3)
                )
                val q2 = intArrayOf(qa[0] + delta[0], qa[1] + delta[1], qa[2] + delta[2])
                for (cw1 in 0..7) {
                    for (cw2 in 0..7) {
                        val e = erroPar(idxA, idxB, bloco, qa, cw1, q2, cw2, escala444 = false)
                        if (e < melhorErro) {
                            melhorErro = e
                            melhorW1 = montarDiferencial(qa, delta, cw1, cw2, flip)
                            melhorW2 = indicesFlip(flip, bloco, qa, cw1, q2, cw2, escala444 = false)
                        }
                    }
                }
            }
        }

        // ---- PLANAR (mínimos quadrados): fica no modo RÁPIDO (barato e ajuda
        // blocos suaves — é um único candidato de mínimos quadrados)
        val planar = tentarPlanar(bloco)
        if (planar != null && planar.erro < melhorErro) {
            melhorErro = planar.erro
            melhorW1 = planar.w1
            melhorW2 = planar.w2
        }

        if (qualidade == Qualidade.ALTA) {
            // ---- T (59 bits) e H (58 bits): SÓ em "Alta qualidade" (B2b) —
            // a busca T/H é a parte cara do encoder por bloco.
            val t = tentarT(bloco)
            if (t != null && t.erro < melhorErro) {
                melhorErro = t.erro
                melhorW1 = t.w1
                melhorW2 = t.w2
            }

            // ---- H (58 bits)
            val h = tentarH(bloco)
            if (h != null && h.erro < melhorErro) {
                melhorErro = h.erro
                melhorW1 = h.w1
                melhorW2 = h.w2
            }
        }

        for (i in 0 until 4) saida[i] = ((melhorW1 ushr (24 - 8 * i)) and 0xFF).toByte()
        for (i in 0 until 4) saida[4 + i] = ((melhorW2 ushr (24 - 8 * i)) and 0xFF).toByte()
        return saida
    }

    /** Codifica UM bloco de alfa 4×4 → 8 bytes (EAC alpha). */
    fun codificarBlocoAlfa(bloco: BlocoEntrada): ByteArray {
        val saida = ByteArray(8)
        var aMin = 255
        var aMax = 0
        for (i in 0 until 16) {
            aMin = min(aMin, bloco.a[i])
            aMax = max(aMax, bloco.a[i])
        }
        var melhorErro = Long.MAX_VALUE
        var melhorByte0 = 0
        var melhorByte1 = 0
        val melhorIdx = IntArray(16)

        for (t in 0 until 16) {
            val baseRow = 16 + t
            val offs1 = IntArray(8) { ALPHA_TABELA[baseRow * 8 + it] }
            val oMin = offs1.min()
            val oMax = offs1.max()
            val span = oMax - oMin
            if (span <= 0) continue
            val mul = ((aMax - aMin + span - 1) / span).coerceIn(1, 15)
            val chave = ((t shl 4) or mul) * 8
            val offs = IntArray(8) { ALPHA_TABELA[chave + it] }
            val base = (aMin - offs.min()).coerceIn(0, 255)
            var erro = 0L
            val idx = IntArray(16)
            for (i in 0 until 16) {
                val alvo = bloco.a[i]
                var mErr = Int.MAX_VALUE
                var mIdx = 0
                for (k in 0 until 8) {
                    val e = abs(base + offs[k] - alvo)
                    if (e < mErr) { mErr = e; mIdx = k }
                }
                idx[i] = mIdx
                erro += mErr.toLong() * mErr.toLong()
            }
            if (erro < melhorErro) {
                melhorErro = erro
                melhorByte0 = base
                melhorByte1 = (t shl 4) or mul
                System.arraycopy(idx, 0, melhorIdx, 0, 16)
            }
        }

        saida[0] = melhorByte0.toByte()
        saida[1] = melhorByte1.toByte()
        // 48 bits de índices de 3 bits, MSB-first, ordem x-major (x externo, y interno)
        var bitPos = 0
        for (x in 0 until 4) {
            for (y in 0 until 4) {
                val v = melhorIdx[x * 4 + y] and 7
                for (k in 2 downTo 0) {
                    if ((v ushr k) and 1 == 1) {
                        val byteI = 2 + (bitPos shr 3)
                        val bitI = 7 - (bitPos and 7)
                        saida[byteI] = (saida[byteI].toInt() or (1 shl bitI)).toByte()
                    }
                    bitPos++
                }
            }
        }
        return saida
    }

    /**
     * Codifica RGBA8888 → blocos ETC2 RGBA8 (16 bytes/bloco).
     *
     * B2 (v0.1.17): LINHAS de blocos paralelizadas em até [threadsEncoder]
     * threads (availableProcessors − 1) com fallback sequencial automático;
     * cancelamento checado a cada linha (responde < 1 s); qualidade RÁPIDA
     * por padrão (individual + diferencial + planar; T/H só em ALTA).
     *
     * B4 (v0.1.17): ENCODER NATIVO (etcpak) PRIMEIRO; falha = fallback Kotlin
     * com o motivo no Console (Etc2Nativo.codificar registra).
     *
     * CORREÇÃO DE ORDEM (v0.1.17): o laço antigo era bx-externo/by-interno
     * (blocos em COLUNA-major) enquanto o decoder e o glCompressedTexImage2D
     * esperam LINHA-major — texturas com alfa > 4 px saíam com os blocos
     * TRANSPOSTOS na GPU. Agora codifica (by*blocosX + bx), igual ao decoder.
     */
    fun codificarRgba(
        rgba: ByteArray,
        largura: Int,
        altura: Int,
        cancelado: () -> Boolean = { false },
        qualidade: Qualidade = Qualidade.RAPIDA
    ): ByteArray {
        // B4: etcpak nativo (multithread em C++) quando disponível. Cancelamento:
        // checado ANTES e DEPOIS da chamada nativa (a chamada em si é uma
        // codificação completa — em 1024² leva milissegundos, dentro da meta
        // de resposta < 1 s).
        if (!testeForcarKotlin) {
            if (cancelado()) throw KimgException("compressão ETC2 cancelada pelo usuário")
            val nativo = com.kenkern.engine.nativecore.Etc2Nativo.codificar(
                rgba, largura, altura, temAlfa = true, qualidadeRapida = qualidade == Qualidade.RAPIDA
            )
            if (cancelado()) throw KimgException("compressão ETC2 cancelada pelo usuário")
            if (nativo != null) return nativo
        }
        val blocosX = blocosDe(largura)
        val blocosY = blocosDe(altura)
        val saida = ByteArray(blocosX * blocosY * 16)
        codificarLinhasEmParalelo(blocosY) { byIni, byFim ->
            codificarFaixaRgba(rgba, largura, altura, blocosX, byIni, byFim, saida, cancelado, qualidade)
        }
        return saida
    }

    /** Uma faixa de LINHAS de blocos RGBA (bytes disjuntos de [saida] — sem trava). */
    private fun codificarFaixaRgba(
        rgba: ByteArray, largura: Int, altura: Int, blocosX: Int,
        byIni: Int, byFim: Int, saida: ByteArray,
        cancelado: () -> Boolean, qualidade: Qualidade
    ) {
        val bloco = BuffBloco()
        for (by in byIni until byFim) {
            if (cancelado()) throw KimgException("compressão ETC2 cancelada pelo usuário")
            for (bx in 0 until blocosX) {
                preencherBloco(bloco, rgba, largura, altura, bx, by)
                val bi = (by * blocosX + bx) * 16
                System.arraycopy(codificarBlocoAlfa(bloco.entrada()), 0, saida, bi, 8)
                System.arraycopy(codificarBlocoRgb(bloco.entrada(), qualidade), 0, saida, bi + 8, 8)
            }
        }
    }

    /**
     * Codifica RGBA8888 (4 bytes/texel; alfa ignorado) → blocos ETC2 RGB8
     * (8 bytes/bloco, LINHA-MAJOR — padrão do hardware). Paralelo em faixas
     * de linhas (B2c), cancelamento por linha (B2d), qualidade por parâmetro (B2b).
     * B4: etcpak nativo primeiro; falha = fallback Kotlin com motivo no Console.
     */
    fun codificarRgb(
        rgb: ByteArray,
        largura: Int,
        altura: Int,
        cancelado: () -> Boolean = { false },
        qualidade: Qualidade = Qualidade.RAPIDA
    ): ByteArray {
        // B4: etcpak nativo primeiro (com cancelamento checado antes/depois).
        if (!testeForcarKotlin) {
            if (cancelado()) throw KimgException("compressão ETC2 cancelada pelo usuário")
            val nativo = com.kenkern.engine.nativecore.Etc2Nativo.codificar(
                rgb, largura, altura, temAlfa = false, qualidadeRapida = qualidade == Qualidade.RAPIDA
            )
            if (cancelado()) throw KimgException("compressão ETC2 cancelada pelo usuário")
            if (nativo != null) return nativo
        }
        val blocosX = blocosDe(largura)
        val blocosY = blocosDe(altura)
        val saida = ByteArray(blocosX * blocosY * 8)
        codificarLinhasEmParalelo(blocosY) { byIni, byFim ->
            codificarFaixaRgb(rgb, largura, altura, blocosX, byIni, byFim, saida, cancelado, qualidade)
        }
        return saida
    }

    /** Uma faixa de LINHAS de blocos RGB (bytes disjuntos de [saida] — sem trava). */
    private fun codificarFaixaRgb(
        rgb: ByteArray, largura: Int, altura: Int, blocosX: Int,
        byIni: Int, byFim: Int, saida: ByteArray,
        cancelado: () -> Boolean, qualidade: Qualidade
    ) {
        val bloco = BuffBloco()
        for (by in byIni until byFim) {
            if (cancelado()) throw KimgException("compressão ETC2 cancelada pelo usuário")
            for (bx in 0 until blocosX) {
                preencherBlocoRgb(bloco, rgb, largura, altura, bx, by)
                val bi = (by * blocosX + bx) * 8
                System.arraycopy(codificarBlocoRgb(bloco.entrada(), qualidade), 0, saida, bi, 8)
            }
        }
    }

    /**
     * Divide as [linhasTotais] linhas de blocos em até [threadsEncoder] faixas
     * BALANCEADAS e roda [tarefa] em paralelo no pool (B2c). Cada faixa escreve
     * bytes DISJUNTOS — nenhuma trava no caminho quente. Fallback sequencial
     * AUTOMÁTICO: pool ausente, 1 thread ou textura pequena (< 8 linhas).
     * A PRIMEIRA exceção de worker vence (cancelamento/erro claro); os demais
     * workers saem no próximo cheque de linha.
     */
    private fun codificarLinhasEmParalelo(
        linhasTotais: Int,
        tarefa: (byIni: Int, byFim: Int) -> Unit
    ) {
        val executor = pool
        val minimoPorFaixa = 4
        if (executor == null || linhasTotais < minimoPorFaixa * 2) {
            tarefa(0, linhasTotais)
            return
        }
        val faixas = threadsEncoder.coerceAtMost(linhasTotais / minimoPorFaixa)
        val porFaixa = (linhasTotais + faixas - 1) / faixas
        val futuros = ArrayList<java.util.concurrent.Future<*>>(faixas)
        var inicio = 0
        while (inicio < linhasTotais) {
            val fim = (inicio + porFaixa).coerceAtMost(linhasTotais)
            val f0 = inicio
            val f1 = fim
            futuros.add(executor.submit { tarefa(f0, f1) })
            inicio = fim
        }
        try {
            for (f in futuros) f.get()
        } catch (e: java.util.concurrent.ExecutionException) {
            val causa = e.cause ?: e
            if (causa is RuntimeException) throw causa
            if (causa is Error) throw causa
            throw KimgException("encoder ETC2 falhou em paralelo: ${causa.message ?: causa.javaClass.simpleName}")
        }
    }

    // ------------------------------------------------------------- decoder API

    /** Decodifica blocos ETC2 RGB8 → RGBA8888 (alfa 255). */
    fun decodificarRgb(blocos: ByteArray, largura: Int, altura: Int): ByteArray {
        val saida = ByteArray(largura * altura * 4)
        val blocosX = blocosDe(largura)
        val blocosY = blocosDe(altura)
        val px = IntArray(16 * 4)
        for (bx in 0 until blocosX) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            for (by in 0 until blocosY) {
                val base = (by * blocosX + bx) * 8
                if (base + 8 > blocos.size) {
                    throw KimgException("blocos ETC2 truncados (esperado ${blocosX * blocosY * 8}, tem ${blocos.size})")
                }
                decodificarBlocoRgb(palavra(blocos, base), palavra(blocos, base + 4), px)
                copiarBloco(px, saida, largura, altura, bx, by, 255)
            }
        }
        return saida
    }

    /** Decodifica blocos ETC2 RGBA8 (EAC alfa + RGB) → RGBA8888. */
    fun decodificarRgba(blocos: ByteArray, largura: Int, altura: Int): ByteArray {
        val saida = ByteArray(largura * altura * 4)
        val blocosX = blocosDe(largura)
        val blocosY = blocosDe(altura)
        val px = IntArray(16 * 4)
        for (bx in 0 until blocosX) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            for (by in 0 until blocosY) {
                val base = (by * blocosX + bx) * 16
                if (base + 16 > blocos.size) {
                    throw KimgException("blocos ETC2 RGBA truncados (esperado ${blocosX * blocosY * 16}, tem ${blocos.size})")
                }
                val alfas = decodificarBlocoAlfa(blocos, base)
                decodificarBlocoRgb(palavra(blocos, base + 8), palavra(blocos, base + 12), px)
                copiarBlocoAlfa(px, alfas, saida, largura, altura, bx, by)
            }
        }
        return saida
    }

    /** Decodifica UM bloco RGB (escolha de modo pelo hardware: overflow 5 bits). */
    fun decodificarBlocoRgb(w1: Long, w2: Long, saida: IntArray) {
        if (c1(w1, 33, 1) == 0) {
            decodificarIndDif(w1, w2, saida)
            return
        }
        val r1 = c1(w1, 63, 5)
        val g1 = c1(w1, 55, 5)
        val b1 = c1(w1, 47, 5)
        val r = r1 + c3s(w1, 58)
        val g = g1 + c3s(w1, 50)
        val b = b1 + c3s(w1, 42)
        when {
            r < 0 || r > 31 -> decodificarT(w1, w2, saida)
            g < 0 || g > 31 -> decodificarH(w1, w2, saida)
            b < 0 || b > 31 -> decodificarPlanar(w1, w2, saida)
            else -> decodificarIndDif(w1, w2, saida)
        }
    }

    // ------------------------------------------------------------- decoder modos

    private fun decodificarIndDif(w1: Long, w2: Long, saida: IntArray) {
        val diff = c1(w1, 33, 1)
        val flip = c1(w1, 32, 1)
        val cw1 = c1(w1, 39, 3)
        val cw2 = c1(w1, 36, 3)
        val c1Arr = IntArray(3)
        val c2Arr = IntArray(3)
        if (diff == 0) {
            c1Arr[0] = c1(w1, 63, 4); c2Arr[0] = c1(w1, 59, 4)
            c1Arr[1] = c1(w1, 55, 4); c2Arr[1] = c1(w1, 51, 4)
            c1Arr[2] = c1(w1, 47, 4); c2Arr[2] = c1(w1, 43, 4)
            for (c in 0 until 3) {
                c1Arr[c] = (c1Arr[c] shl 4) or c1Arr[c]
                c2Arr[c] = (c2Arr[c] shl 4) or c2Arr[c]
            }
        } else {
            val r1 = c1(w1, 63, 5); val g1 = c1(w1, 55, 5); val b1 = c1(w1, 47, 5)
            val r2 = (r1 + c3s(w1, 58)).coerceIn(0, 31)
            val g2 = (g1 + c3s(w1, 50)).coerceIn(0, 31)
            val b2 = (b1 + c3s(w1, 42)).coerceIn(0, 31)
            c1Arr[0] = expandir5(r1); c1Arr[1] = expandir5(g1); c1Arr[2] = expandir5(b1)
            c2Arr[0] = expandir5(r2); c2Arr[1] = expandir5(g2); c2Arr[2] = expandir5(b2)
        }
        pintarIndDif(saida, w2, c1Arr, cw1 * 4, c2Arr, cw2 * 4, flip)
    }

    private fun pintarIndDif(
        saida: IntArray, w2: Long,
        c1: IntArray, linha1: Int, c2: IntArray, linha2: Int, flip: Int
    ) {
        for (x in 0 until 4) {
            for (y in 0 until 4) {
                val pos = y + x * 4
                val idx = UNSCRAMBLE[(bit2(w2, pos + 16) shl 1) or bit2(w2, pos)]
                val esquerda = if (flip == 0) x < 2 else y < 2
                val cor = if (esquerda) c1 else c2
                val m = MODS[(if (esquerda) linha1 else linha2) + idx]
                val i = (x * 4 + y) * 4
                saida[i] = clamp8(cor[0] + m)
                saida[i + 1] = clamp8(cor[1] + m)
                saida[i + 2] = clamp8(cor[2] + m)
            }
        }
    }

    private fun decodificarT(w1: Long, w2: Long, saida: IntArray) {
        // unstuff59 (ETCPACK) em aritmética de 32 bits: word59_32 bit k = word1_32 bit k+1
        val w1i = (w1 and 0xFFFFFFFFL).toInt()
        var w59 = (w1i ushr 1) or (w1i and 1)
        val r0a = (w1i ushr 27) and 3
        w59 = (w59 and (3 shl 25).inv()) or (r0a shl 25)
        val r0 = (w59 ushr 23) and 15
        val g0 = (w59 ushr 19) and 15
        val b0 = (w59 ushr 15) and 15
        val r1 = (w59 ushr 11) and 15
        val g1 = (w59 ushr 7) and 15
        val b1 = (w59 ushr 3) and 15
        val d = w59 and 7
        val c0 = intArrayOf(expandir4(r0), expandir4(g0), expandir4(b0))
        val c1 = intArrayOf(expandir4(r1), expandir4(g1), expandir4(b1))
        val dist = TABELA_TH[d]
        val pinturas = Array(4) { IntArray(3) }
        pinturas[3][0] = clamp8(c1[0] - dist); pinturas[3][1] = clamp8(c1[1] - dist); pinturas[3][2] = clamp8(c1[2] - dist)
        pinturas[0][0] = c0[0]; pinturas[0][1] = c0[1]; pinturas[0][2] = c0[2]
        pinturas[1][0] = clamp8(c1[0] + dist); pinturas[1][1] = clamp8(c1[1] + dist); pinturas[1][2] = clamp8(c1[2] + dist)
        pinturas[2][0] = c1[0]; pinturas[2][1] = c1[1]; pinturas[2][2] = c1[2]
        pintarIndices(saida, w2, pinturas)
    }

    private fun decodificarH(w1: Long, w2: Long, saida: IntArray) {
        // unstuff58 (ETCPACK) → word58_32
        val part0 = ((w1 ushr 24) and 0x7F).toInt()
        val part1 = ((w1 ushr 19) and 3L).toInt()
        val part2 = ((w1 ushr 2) and 0xFFFFL).toInt()
        val part3 = (w1 and 1L).toInt()
        val w58 = ((part0.toLong() shl 19) or (part1.toLong() shl 17) or (part2.toLong() shl 1) or part3.toLong())
        val r0 = ((w58 ushr 25) and 15L).toInt()
        val g0 = ((w58 ushr 21) and 15L).toInt()
        val b0 = ((w58 ushr 17) and 15L).toInt()
        val r1 = ((w58 ushr 13) and 15L).toInt()
        val g1 = ((w58 ushr 9) and 15L).toInt()
        val b1 = ((w58 ushr 5) and 15L).toInt()
        val dHi = ((w58 ushr 1) and 3L).toInt()
        val col0 = (r0 shl 8) or (g0 shl 4) or b0
        val col1 = (r1 shl 8) or (g1 shl 4) or b1
        val d = (dHi shl 1) or (if (col0 >= col1) 1 else 0)
        val c0 = intArrayOf(expandir4(r0), expandir4(g0), expandir4(b0))
        val c1 = intArrayOf(expandir4(r1), expandir4(g1), expandir4(b1))
        val dist = TABELA_TH[d]
        val pinturas = Array(4) { IntArray(3) }
        pinturas[3][0] = clamp8(c1[0] - dist); pinturas[3][1] = clamp8(c1[1] - dist); pinturas[3][2] = clamp8(c1[2] - dist)
        pinturas[0][0] = clamp8(c0[0] + dist); pinturas[0][1] = clamp8(c0[1] + dist); pinturas[0][2] = clamp8(c0[2] + dist)
        pinturas[1][0] = clamp8(c0[0] - dist); pinturas[1][1] = clamp8(c0[1] - dist); pinturas[1][2] = clamp8(c0[2] - dist)
        pinturas[2][0] = clamp8(c1[0] + dist); pinturas[2][1] = clamp8(c1[1] + dist); pinturas[2][2] = clamp8(c1[2] + dist)
        pintarIndices(saida, w2, pinturas)
    }

    private fun pintarIndices(saida: IntArray, w2: Long, pinturas: Array<IntArray>) {
        for (x in 0 until 4) {
            for (y in 0 until 4) {
                val pos = y + x * 4
                val p = UNSCRAMBLE[(bit2(w2, pos + 16) shl 1) or bit2(w2, pos)]
                val i = (x * 4 + y) * 4
                saida[i] = pinturas[p][0]
                saida[i + 1] = pinturas[p][1]
                saida[i + 2] = pinturas[p][2]
            }
        }
    }

    private fun decodificarPlanar(w1: Long, w2: Long, saida: IntArray) {
        // unstuff57 (ETCPACK) → campos diretos
        val ro = ((w1 ushr 25) and 63L).toInt()
        val go1 = ((w1 ushr 24) and 1L).toInt()
        val go2 = ((w1 ushr 17) and 63L).toInt()
        val bo1 = ((w1 ushr 16) and 1L).toInt()
        val bo2 = ((w1 ushr 11) and 3L).toInt()
        val bo3 = ((w1 ushr 7) and 7L).toInt()
        val rh1 = ((w1 ushr 2) and 31L).toInt()
        val rh2 = (w1 and 1L).toInt()
        val gh = ((w2 ushr 25) and 127L).toInt()
        val bh = ((w2 ushr 19) and 63L).toInt()
        val rv = ((w2 ushr 13) and 63L).toInt()
        val gv = ((w2 ushr 6) and 127L).toInt()
        val bv = (w2 and 63L).toInt()

        val co = intArrayOf(expandir6(ro), expandir7((go1 shl 6) or go2), expandir6((bo1 shl 5) or (bo2 shl 3) or bo3))
        val ch = intArrayOf(expandir6((rh1 shl 1) or rh2), expandir7(gh), expandir6(bh))
        val cv = intArrayOf(expandir6(rv), expandir7(gv), expandir6(bv))
        for (x in 0 until 4) {
            for (y in 0 until 4) {
                val i = (x * 4 + y) * 4
                for (c in 0 until 3) {
                    val v = (x * (ch[c] - co[c]) + y * (cv[c] - co[c]) + 4 * co[c] + 2) shr 2
                    saida[i + c] = v.coerceIn(0, 255)
                }
            }
        }
    }

    /** Decodifica o bloco EAC alfa (8 bytes a partir de [base]). */
    private fun decodificarBlocoAlfa(blocos: ByteArray, base: Int): IntArray {
        val alfaBase = blocos[base].toInt() and 0xFF
        val chave = (blocos[base + 1].toInt() and 0xFF) * 8
        val alfas = IntArray(16)
        var bitPos = 0
        for (x in 0 until 4) {
            for (y in 0 until 4) {
                var idx = 0
                for (k in 0 until 3) {
                    val p = bitPos + k
                    val byteI = base + 2 + (p shr 3)
                    val bitI = 7 - (p and 7)
                    idx = (idx shl 1) or ((blocos[byteI].toInt() shr bitI) and 1)
                }
                bitPos += 3
                alfas[x * 4 + y] = (alfaBase + ALPHA_TABELA[chave + idx]).coerceIn(0, 255)
            }
        }
        return alfas
    }

    // ------------------------------------------------------------- encoder modos

    private class BuffBloco {
        val r = IntArray(16); val g = IntArray(16); val b = IntArray(16); val a = IntArray(16)
        fun entrada() = BlocoEntrada(r, g, b, a)
    }

    private fun preencherBloco(b: BuffBloco, rgba: ByteArray, largura: Int, altura: Int, bx: Int, by: Int) {
        for (x in 0 until 4) {
            for (y in 0 until 4) {
                val sx = min(bx * 4 + x, largura - 1)
                val sy = min(by * 4 + y, altura - 1)
                val p = (sy * largura + sx) * 4
                val i = x * 4 + y
                b.r[i] = rgba[p].toInt() and 0xFF
                b.g[i] = rgba[p + 1].toInt() and 0xFF
                b.b[i] = rgba[p + 2].toInt() and 0xFF
                b.a[i] = rgba[p + 3].toInt() and 0xFF
            }
        }
    }

    /** Entrada RGBA8888 (4 bytes/texel; alfa IGNORADO — o formato escolhido já decidiu). */
    private fun preencherBlocoRgb(b: BuffBloco, rgba: ByteArray, largura: Int, altura: Int, bx: Int, by: Int) {
        for (x in 0 until 4) {
            for (y in 0 until 4) {
                val sx = min(bx * 4 + x, largura - 1)
                val sy = min(by * 4 + y, altura - 1)
                val p = (sy * largura + sx) * 4
                val i = x * 4 + y
                b.r[i] = rgba[p].toInt() and 0xFF
                b.g[i] = rgba[p + 1].toInt() and 0xFF
                b.b[i] = rgba[p + 2].toInt() and 0xFF
                b.a[i] = 255
            }
        }
    }

    /** índices dos 8 texels de cada metade (flip 0 = colunas, 1 = linhas). */
    private fun metades(flip: Int): Array<IntArray> {
        val a = IntArray(8)
        val b = IntArray(8)
        var ai = 0
        var bi = 0
        for (x in 0 until 4) {
            for (y in 0 until 4) {
                val i = x * 4 + y
                val esquerda = if (flip == 0) x < 2 else y < 2
                if (esquerda) { a[ai++] = i } else { b[bi++] = i }
            }
        }
        return arrayOf(a, b)
    }

    private fun media(indices: IntArray, b: BlocoEntrada): IntArray {
        var r = 0; var g = 0; var bl = 0
        for (i in indices) { r += b.r[i]; g += b.g[i]; bl += b.b[i] }
        return intArrayOf(r / 8, g / 8, bl / 8)
    }

    /** Média + centro min/max da metade (2 bases candidatas — qualidade ×2 sem custo alto). */
    private fun basesCandidatas(indices: IntArray, b: BlocoEntrada, canais: Int): List<IntArray> {
        var mn = intArrayOf(255, 255, 255)
        var mx = intArrayOf(0, 0, 0)
        for (i in indices) {
            for (c in 0 until 3) {
                val v = when (c) { 0 -> b.r[i]; 1 -> b.g[i]; else -> b.b[i] }
                mn[c] = min(mn[c], v); mx[c] = max(mx[c], v)
            }
        }
        val media = media(indices, b)
        val centro = intArrayOf(
            (mn[0] + mx[0]) / 2, (mn[1] + mx[1]) / 2, (mn[2] + mx[2]) / 2
        )
        return if (media.toList() == centro.toList()) listOf(media) else listOf(media, centro)
    }

    /** Quantiza 8-bit → código 4-bit com ARREDONDAMENTO (truncar colapsava médias baixas para 0). */
    private fun quantizar444(cor: IntArray): IntArray = intArrayOf(
        clamp8((cor[0] * 15 + 127) / 255), clamp8((cor[1] * 15 + 127) / 255), clamp8((cor[2] * 15 + 127) / 255)
    )

    /** Quantiza 8-bit → código 5-bit com arredondamento. */
    private fun quantizar555(cor: IntArray): IntArray = intArrayOf(
        clamp5((cor[0] * 31 + 127) / 255), clamp5((cor[1] * 31 + 127) / 255), clamp5((cor[2] * 31 + 127) / 255)
    )

    private fun expandir4(v: Int): Int = (v shl 4) or v
    private fun expandir5(v: Int): Int = (v shl 3) or (v shr 2)
    private fun expandir6(v: Int): Int = (v shl 2) or (v shr 4)
    private fun expandir7(v: Int): Int = (v shl 1) or (v shr 6)

    private fun erroPar(
        idxA: IntArray, idxB: IntArray, b: BlocoEntrada,
        q1: IntArray, cw1: Int, q2: IntArray, cw2: Int, escala444: Boolean
    ): Long {
        val b1 = if (escala444) escalar444(q1) else escalar555(q1)
        val b2 = if (escala444) escalar444(q2) else escalar555(q2)
        return erroMetade(idxA, b, b1, cw1) + erroMetade(idxB, b, b2, cw2)
    }

    private fun escalar444(q: IntArray): IntArray = intArrayOf((q[0] shl 4) or q[0], (q[1] shl 4) or q[1], (q[2] shl 4) or q[2])
    private fun escalar555(q: IntArray): IntArray = intArrayOf(expandir5(q[0]), expandir5(q[1]), expandir5(q[2]))

    private fun erroMetade(indices: IntArray, b: BlocoEntrada, base: IntArray, cw: Int): Long {
        var e = 0L
        val linha = cw * 4
        for (i in indices) {
            val tr = b.r[i] - base[0]; val tg = b.g[i] - base[1]; val tb = b.b[i] - base[2]
            var melhor = Int.MAX_VALUE
            for (k in 0 until 4) {
                val m = MODS[linha + k]
                val d = abs(tr - m) + abs(tg - m) + abs(tb - m)
                if (d < melhor) melhor = d
            }
            e += melhor.toLong()
        }
        return e
    }

    /** Palavra de índices: por texel, modificador mais próximo; bits na ordem do hardware. */
    private fun indicesFlip(
        flip: Int, b: BlocoEntrada,
        q1: IntArray, cw1: Int, q2: IntArray, cw2: Int, escala444: Boolean
    ): Long {
        val b1 = if (escala444) escalar444(q1) else escalar555(q1)
        val b2 = if (escala444) escalar444(q2) else escalar555(q2)
        var w = 0L
        val linha1 = cw1 * 4
        val linha2 = cw2 * 4
        for (x in 0 until 4) {
            for (y in 0 until 4) {
                val i = x * 4 + y
                val esquerda = if (flip == 0) x < 2 else y < 2
                val linha = if (esquerda) linha1 else linha2
                val base = if (esquerda) b1 else b2
                val tr = b.r[i] - base[0]; val tg = b.g[i] - base[1]; val tb = b.b[i] - base[2]
                var melhor = Int.MAX_VALUE
                var mIdx = 0
                for (k in 0 until 4) {
                    val m = MODS[linha + k]
                    val d = abs(tr - m) + abs(tg - m) + abs(tb - m)
                    if (d < melhor) { melhor = d; mIdx = k }
                }
                val v = UNSCRAMBLE_INV[mIdx]
                val bitPos = y + x * 4
                if ((v shr 1) and 1 == 1) w = w or (1L shl (bitPos + 16))
                if (v and 1 == 1) w = w or (1L shl bitPos)
            }
        }
        return w
    }

    private fun montarIndividual(b1: IntArray, b2: IntArray, cw1: Int, cw2: Int, flip: Int): Long {
        var w = 0L
        w = s1(w, 63, 4, b1[0])
        w = s1(w, 59, 4, b2[0])
        w = s1(w, 55, 4, b1[1])
        w = s1(w, 51, 4, b2[1])
        w = s1(w, 47, 4, b1[2])
        w = s1(w, 43, 4, b2[2])
        w = s1(w, 39, 3, cw1)
        w = s1(w, 36, 3, cw2)
        w = s1(w, 33, 1, 0)
        w = s1(w, 32, 1, flip)
        return w
    }

    private fun montarDiferencial(q1: IntArray, delta: IntArray, cw1: Int, cw2: Int, flip: Int): Long {
        var w = 0L
        w = s1(w, 63, 5, q1[0])
        w = s1(w, 58, 3, delta[0] and 7)
        w = s1(w, 55, 5, q1[1])
        w = s1(w, 50, 3, delta[1] and 7)
        w = s1(w, 47, 5, q1[2])
        w = s1(w, 42, 3, delta[2] and 7)
        w = s1(w, 39, 3, cw1)
        w = s1(w, 36, 3, cw2)
        w = s1(w, 33, 1, 1)
        w = s1(w, 32, 1, flip)
        return w
    }

    // ---- planar

    private class Resultado(val w1: Long, val w2: Long, val erro: Long)

    private fun tentarPlanar(b: BlocoEntrada): Resultado? {
        val planos = IntArray(9)
        for (c in 0 until 3) {
            val v = when (c) { 0 -> b.r; 1 -> b.g; else -> b.b }
            var sv = 0; var svx = 0; var svy = 0
            for (x in 0 until 4) {
                for (y in 0 until 4) {
                    val i = x * 4 + y
                    sv += v[i]; svx += v[i] * x; svy += v[i] * y
                }
            }
            val m = arrayOf(
                doubleArrayOf(16.0, 24.0, 24.0),
                doubleArrayOf(24.0, 56.0, 144.0),
                doubleArrayOf(24.0, 144.0, 56.0)
            )
            val rhs = doubleArrayOf(sv.toDouble(), svx.toDouble(), svy.toDouble())
            resolver3x3(m, rhs)
            planos[c * 3] = rhs[0].roundToInt()
            planos[c * 3 + 1] = rhs[1].roundToInt()
            planos[c * 3 + 2] = rhs[2].roundToInt()
        }
        // layout "nice" de 57 bits (antes do stuff): RO(6)@63..58, G(7)@57..51, B(6)@50..45,
        // RH(6)@44..39, GH(7)@38..32 | BH(6)@31..26, RV(6)@25..20, GV(7)@19..13, BV(6)@12..7
        // Os campos do planar são CÓDIGOS de 6/7/6 bits (expandidos pelo decoder
        // para 8 bits) — os valores do plano 8-bit devem ser QUANTIZADOS para o
        // código (q = v*max/255), NUNCA clampeados (clamp quebrava gradientes).
        val maxO = intArrayOf(63, 127, 63) // 6/7/6 bits
        val co = IntArray(3); val ch = IntArray(3); val cv = IntArray(3)
        for (c in 0 until 3) {
            val a = planos[c * 3]; val bx = planos[c * 3 + 1]; val by = planos[c * 3 + 2]
            val co8 = a.coerceIn(0, 255)
            val ch8 = (a + 3 * bx).coerceIn(0, 255)
            val cv8 = (a + 3 * by).coerceIn(0, 255)
            co[c] = (co8 * maxO[c] + 127) / 255
            ch[c] = (ch8 * maxO[c] + 127) / 255
            cv[c] = (cv8 * maxO[c] + 127) / 255
        }
        var w57a = 0L
        w57a = s1(w57a, 63, 6, co[0])
        w57a = s1(w57a, 57, 1, (co[1] shr 6) and 1)
        w57a = s1(w57a, 56, 6, co[1] and 63)
        w57a = s1(w57a, 50, 1, (co[2] shr 5) and 1)
        w57a = s1(w57a, 49, 2, (co[2] shr 3) and 3)
        w57a = s1(w57a, 47, 3, co[2] and 7)
        w57a = s1(w57a, 44, 5, (ch[0] shr 1) and 31)
        w57a = s1(w57a, 39, 1, ch[0] and 1)
        w57a = s1(w57a, 38, 7, ch[1])
        var w57b = 0L
        w57b = s2(w57b, 31, 6, ch[2])
        w57b = s2(w57b, 25, 6, cv[0])
        w57b = s2(w57b, 19, 7, cv[1])
        w57b = s2(w57b, 12, 6, cv[2])
        val (w1, w2) = stuff57(w57a, w57b)
        val decodificado = IntArray(16 * 4)
        decodificarPlanar(w1, w2, decodificado)
        var e = 0L
        for (i in 0 until 16) {
            for (c in 0 until 3) {
                val origem = when (c) { 0 -> b.r[i]; 1 -> b.g[i]; else -> b.b[i] }
                val d = origem - decodificado[i * 4 + c]
                e += (d * d).toLong()
            }
        }
        return Resultado(w1, w2, e)
    }

    /** stuff57bits OFICIAL (ETCPACK): red/green não transbordam, blue transborda, df=1. */
    private fun stuff57(w57a: Long, w57b: Long): Pair<Long, Long> {
        val ro = c1(w57a, 63, 6)
        val go1 = c1(w57a, 57, 1)
        val go2 = c1(w57a, 56, 6)
        val bo1 = c1(w57a, 50, 1)
        val bo2 = c1(w57a, 49, 2)
        val bo3 = c1(w57a, 47, 3)
        val rh1 = c1(w57a, 44, 5)
        val rh2 = c1(w57a, 39, 1)
        val gh = c1(w57a, 38, 7)
        val bh = c2(w57b, 31, 6)
        val rv = c2(w57b, 25, 6)
        val gv = c2(w57b, 19, 7)
        val bv = c2(w57b, 12, 6)

        var w1 = 0L
        var w2 = 0L
        w1 = s1(w1, 62, 6, ro)
        w1 = s1(w1, 56, 1, go1)
        w1 = s1(w1, 54, 6, go2)
        w1 = s1(w1, 48, 1, bo1)
        w1 = s1(w1, 44, 2, bo2)
        w1 = s1(w1, 41, 3, bo3)
        w1 = s1(w1, 38, 5, rh1)
        w1 = s1(w1, 32, 1, rh2)
        w2 = s2(w2, 31, 7, gh)
        w2 = s2(w2, 24, 6, bh)
        w2 = s2(w2, 18, 6, rv)
        w2 = s2(w2, 12, 7, gv)
        w2 = s2(w2, 5, 6, bv)

        // vermelho NÃO transborda: bit63 = !bit62
        val bitR = c1(w1, 62, 1)
        w1 = s1(w1, 63, 1, if (bitR == 1) 0 else 1)
        // verde NÃO transborda: bit55 = !bit54
        val bitG = c1(w1, 54, 1)
        w1 = s1(w1, 55, 1, if (bitG == 1) 0 else 1)
        // azul TRANSBORDA (padrões abcd 0111/1010/1011/1101/1110/1111 → bits 47..45=111, bit42=0)
        val a = c1(w1, 44, 1); val b = c1(w1, 43, 1)
        val c = c1(w1, 41, 1); val d = c1(w1, 40, 1)
        val na = if (a == 1) 0 else 1
        val nc = if (c == 1) 0 else 1
        val bit = (a and c) or (na and b and c and d) or (a and b and nc and d)
        w1 = s1(w1, 47, 3, if (bit == 1) 7 else 0)
        w1 = s1(w1, 42, 1, if (bit == 1) 0 else 1)
        // diffbit
        w1 = s1(w1, 33, 1, 1)
        return w1 to w2
    }

    private fun tentarT(b: BlocoEntrada): Resultado? {
        var imax = 0; var imin = 0; var lmax = Int.MIN_VALUE; var lmin = Int.MAX_VALUE
        for (i in 0 until 16) {
            val l = b.r[i] * 2 + b.g[i] * 4 + b.b[i]
            if (l > lmax) { lmax = l; imax = i }
            if (l < lmin) { lmin = l; imin = i }
        }
        // cores 444 quantizadas
        val c0 = intArrayOf((b.r[imax] * 15 + 127) / 255, (b.g[imax] * 15 + 127) / 255, (b.b[imax] * 15 + 127) / 255)
        val c1 = intArrayOf((b.r[imin] * 15 + 127) / 255, (b.g[imin] * 15 + 127) / 255, (b.b[imin] * 15 + 127) / 255)

        var melhorErro = Long.MAX_VALUE
        var melhorW1 = 0L
        var melhorW2 = 0L
        for (d in 0 until 8) {
            val dist = TABELA_TH[d]
            val pinturas = Array(4) { IntArray(3) }
            pinturas[0][0] = expandir4(c0[0]); pinturas[0][1] = expandir4(c0[1]); pinturas[0][2] = expandir4(c0[2])
            pinturas[1][0] = clamp8(expandir4(c1[0]) + dist); pinturas[1][1] = clamp8(expandir4(c1[1]) + dist); pinturas[1][2] = clamp8(expandir4(c1[2]) + dist)
            pinturas[2][0] = expandir4(c1[0]); pinturas[2][1] = expandir4(c1[1]); pinturas[2][2] = expandir4(c1[2])
            pinturas[3][0] = clamp8(expandir4(c1[0]) - dist); pinturas[3][1] = clamp8(expandir4(c1[1]) - dist); pinturas[3][2] = clamp8(expandir4(c1[2]) - dist)
            var e = 0L
            var w2 = 0L
            for (x in 0 until 4) {
                for (y in 0 until 4) {
                    val i = x * 4 + y
                    var mErr = Int.MAX_VALUE
                    var mIdx = 0
                    for (p in 0 until 4) {
                        val dR = b.r[i] - pinturas[p][0]; val dG = b.g[i] - pinturas[p][1]; val dB = b.b[i] - pinturas[p][2]
                        val dd = dR * dR + dG * dG + dB * dB
                        if (dd < mErr) { mErr = dd; mIdx = p }
                    }
                    e += mErr.toLong()
                    val v = UNSCRAMBLE_INV[mIdx]
                    val bitPos = y + x * 4
                    if ((v shr 1) and 1 == 1) w2 = w2 or (1L shl (bitPos + 16))
                    if (v and 1 == 1) w2 = w2 or (1L shl bitPos)
                }
            }
            if (e < melhorErro) {
                melhorErro = e
                // word59 (layout "nice"): R0(4)@58..55 G0(4)@54..51 B0(4)@50..47
                // R1(4)@46..43 G1(4)@42..39 B1(4)@38..35 dist(3)@34..32
                var w59 = 0L
                w59 = s1(w59, 58, 4, c0[0])
                w59 = s1(w59, 54, 4, c0[1])
                w59 = s1(w59, 50, 4, c0[2])
                w59 = s1(w59, 46, 4, c1[0])
                w59 = s1(w59, 42, 4, c1[1])
                w59 = s1(w59, 38, 4, c1[2])
                w59 = s1(w59, 34, 3, d)
                melhorW1 = stuff59(w59)
                melhorW2 = w2
            }
        }
        return Resultado(melhorW1, melhorW2, melhorErro)
    }

    /** stuff59bits OFICIAL (ETCPACK): vermelho TRANSBORDA, df=1. */
    private fun stuff59(w59: Long): Long {
        val r0a = c1(w59, 58, 2)
        var w1 = w59 shl 1 // word59 bit p → word1 bit p+1 (p ≥ 33); bit 33 = word59 bit 32
        // db (LSB da distância): word1 bit 32 = word59 bit 32
        w1 = (w1 and (1L shl 32).inv()) or (w59 and (1L shl 32))
        // R0a (top 2 bits de R0): word1 bits 60..59
        w1 = s1(w1, 60, 2, r0a)
        // vermelho TRANSBORDA (padrões abcd → bits 63..61=111, bit58=0 quando necessário)
        val a = c1(w1, 60, 1); val b = c1(w1, 59, 1)
        val c = c1(w1, 57, 1); val d = c1(w1, 56, 1)
        val na = if (a == 1) 0 else 1
        val nc = if (c == 1) 0 else 1
        val bit = (a and c) or (na and b and c and d) or (a and b and nc and d)
        w1 = s1(w1, 63, 3, if (bit == 1) 7 else 0)
        w1 = s1(w1, 58, 1, if (bit == 1) 0 else 1)
        // diffbit
        w1 = s1(w1, 33, 1, 1)
        return w1
    }

    private fun tentarH(b: BlocoEntrada): Resultado? {
        var melhorErro = Long.MAX_VALUE
        var melhorW1 = 0L
        var melhorW2 = 0L
        for (flip in 0..1) {
            val metas = metades(flip)
            val mA = media(metas[0], b)
            val mB = media(metas[1], b)
            val c0 = intArrayOf((mA[0] * 15 + 127) / 255, (mA[1] * 15 + 127) / 255, (mA[2] * 15 + 127) / 255)
            val c1 = intArrayOf((mB[0] * 15 + 127) / 255, (mB[1] * 15 + 127) / 255, (mB[2] * 15 + 127) / 255)
            val col0 = (c0[0] shl 8) or (c0[1] shl 4) or c0[2]
            val col1 = (c1[0] shl 8) or (c1[1] shl 4) or c1[2]
            for (dHi in 0 until 4) {
                // o decoder RECOMPUTA o LSB da distância de col0>=col1 — tentamos os dois
                val dist = TABELA_TH[dHi shl 1]
                val dist2 = TABELA_TH[(dHi shl 1) or 1]
                for (distCand in intArrayOf(dist, dist2)) {
                    val pinturas = Array(4) { IntArray(3) }
                    pinturas[3][0] = clamp8(expandir4(c1[0]) - distCand); pinturas[3][1] = clamp8(expandir4(c1[1]) - distCand); pinturas[3][2] = clamp8(expandir4(c1[2]) - distCand)
                    pinturas[0][0] = clamp8(expandir4(c0[0]) + distCand); pinturas[0][1] = clamp8(expandir4(c0[1]) + distCand); pinturas[0][2] = clamp8(expandir4(c0[2]) + distCand)
                    pinturas[1][0] = clamp8(expandir4(c0[0]) - distCand); pinturas[1][1] = clamp8(expandir4(c0[1]) - distCand); pinturas[1][2] = clamp8(expandir4(c0[2]) - distCand)
                    pinturas[2][0] = clamp8(expandir4(c1[0]) + distCand); pinturas[2][1] = clamp8(expandir4(c1[1]) + distCand); pinturas[2][2] = clamp8(expandir4(c1[2]) + distCand)
                    var e = 0L
                    var w2 = 0L
                    for (x in 0 until 4) {
                        for (y in 0 until 4) {
                            val i = x * 4 + y
                            var mErr = Int.MAX_VALUE
                            var mIdx = 0
                            for (p in 0 until 4) {
                                val dR = b.r[i] - pinturas[p][0]; val dG = b.g[i] - pinturas[p][1]; val dB = b.b[i] - pinturas[p][2]
                                val dd = dR * dR + dG * dG + dB * dB
                                if (dd < mErr) { mErr = dd; mIdx = p }
                            }
                            e += mErr.toLong()
                            val v = UNSCRAMBLE_INV[mIdx]
                            val bitPos = y + x * 4
                            if ((v shr 1) and 1 == 1) w2 = w2 or (1L shl (bitPos + 16))
                            if (v and 1 == 1) w2 = w2 or (1L shl bitPos)
                        }
                    }
                    if (e < melhorErro) {
                        melhorErro = e
                        // word58 (layout "nice"): R0(4)@57..54 G0(4)@53..50 B0(4)@49..46
                        // R1(4)@45..42 G1(4)@41..38 B1(4)@37..34 dist(2)@33..32
                        var w58 = 0L
                        w58 = s1(w58, 57, 4, c0[0])
                        w58 = s1(w58, 53, 4, c0[1])
                        w58 = s1(w58, 49, 4, c0[2])
                        w58 = s1(w58, 45, 4, c1[0])
                        w58 = s1(w58, 41, 4, c1[1])
                        w58 = s1(w58, 37, 4, c1[2])
                        w58 = s1(w58, 33, 2, dHi)
                        melhorW1 = stuff58(w58)
                        melhorW2 = w2
                    }
                }
            }
        }
        return Resultado(melhorW1, melhorW2, melhorErro)
    }

    /** stuff58bits OFICIAL (ETCPACK): vermelho NÃO transborda, verde TRANSBORDA, df=1. */
    private fun stuff58(w58: Long): Long {
        val part0 = c1(w58, 57, 7)
        val part1 = c1(w58, 50, 2)
        val part2 = c1(w58, 48, 16)
        val part3 = c1(w58, 32, 1)
        var w1 = 0L
        w1 = s1(w1, 62, 7, part0)
        w1 = s1(w1, 52, 2, part1)
        w1 = s1(w1, 49, 16, part2)
        w1 = s1(w1, 32, 1, part3)
        // vermelho NÃO transborda: bit63 = !bit62
        val bitR = c1(w1, 62, 1)
        w1 = s1(w1, 63, 1, if (bitR == 1) 0 else 1)
        // verde TRANSBORDA (mesmo padrão de bits)
        val a = c1(w1, 52, 1); val b = c1(w1, 51, 1)
        val c = c1(w1, 49, 1); val d = c1(w1, 48, 1)
        val na = if (a == 1) 0 else 1
        val nc = if (c == 1) 0 else 1
        val bit = (a and c) or (na and b and c and d) or (a and b and nc and d)
        w1 = s1(w1, 55, 3, if (bit == 1) 7 else 0)
        w1 = s1(w1, 50, 1, if (bit == 1) 0 else 1)
        // diffbit
        w1 = s1(w1, 33, 1, 1)
        return w1
    }

    // ------------------------------------------------------------- utilitários

    private fun blocosDe(total: Int): Int = (total + 3) / 4

    private fun clamp8(v: Int): Int = v.coerceIn(0, 255)
    private fun clamp5(v: Int): Int = v.coerceIn(0, 31)

    private fun palavra(blocos: ByteArray, base: Int): Long {
        var p = 0L
        for (i in 0 until 4) p = (p shl 8) or (blocos[base + i].toLong() and 0xFF)
        return p
    }

    private fun copiarBloco(px: IntArray, saida: ByteArray, largura: Int, altura: Int, bx: Int, by: Int, alfa: Int) {
        for (x in 0 until 4) {
            for (y in 0 until 4) {
                val dx = bx * 4 + x
                val dy = by * 4 + y
                if (dx < largura && dy < altura) {
                    val s = (x * 4 + y) * 4
                    val d = (dy * largura + dx) * 4
                    saida[d] = px[s].toByte()
                    saida[d + 1] = px[s + 1].toByte()
                    saida[d + 2] = px[s + 2].toByte()
                    saida[d + 3] = alfa.toByte()
                }
            }
        }
    }

    private fun copiarBlocoAlfa(px: IntArray, alfas: IntArray, saida: ByteArray, largura: Int, altura: Int, bx: Int, by: Int) {
        for (x in 0 until 4) {
            for (y in 0 until 4) {
                val dx = bx * 4 + x
                val dy = by * 4 + y
                if (dx < largura && dy < altura) {
                    val s = (x * 4 + y) * 4
                    val d = (dy * largura + dx) * 4
                    saida[d] = px[s].toByte()
                    saida[d + 1] = px[s + 1].toByte()
                    saida[d + 2] = px[s + 2].toByte()
                    saida[d + 3] = alfas[x * 4 + y].toByte()
                }
            }
        }
    }

    /** Resolve 3×3 (Gauss; rhs recebe a solução). */
    private fun resolver3x3(m: Array<DoubleArray>, rhs: DoubleArray) {
        for (col in 0 until 3) {
            var p = col
            for (r in col + 1 until 3) if (abs(m[r][col]) > abs(m[p][col])) p = r
            val tmpM = m[col]; m[col] = m[p]; m[p] = tmpM
            val tmpR = rhs[col]; rhs[col] = rhs[p]; rhs[p] = tmpR
            val div = m[col][col]
            if (abs(div) < 1e-9) continue
            for (r in col + 1 until 3) {
                val f = m[r][col] / div
                for (c2 in col until 3) m[r][c2] -= f * m[col][c2]
                rhs[r] -= f * rhs[col]
            }
        }
        for (r in 2 downTo 0) {
            var s = rhs[r]
            for (c2 in r + 1 until 3) s -= m[r][c2] * rhs[c2]
            rhs[r] = if (abs(m[r][r]) < 1e-9) 0.0 else s / m[r][r]
        }
    }

    /** PSNR (dB) entre duas imagens RGBA8888 do mesmo tamanho (canais RGB). */
    fun psnr(origem: ByteArray, reconstruida: ByteArray): Double {
        require(origem.size == reconstruida.size) { "imagens de tamanhos diferentes no PSNR" }
        var sse = 0L
        var n = 0
        var i = 0
        while (i + 2 < origem.size) {
            for (c in 0 until 3) {
                val d = (origem[i + c].toInt() and 0xFF) - (reconstruida[i + c].toInt() and 0xFF)
                sse += (d * d).toLong()
                n++
            }
            i += 4
        }
        if (n == 0 || sse == 0L) return 99.0
        val mse = sse.toDouble() / n
        return 10.0 * kotlin.math.log10(255.0 * 255.0 / mse)
    }
}
