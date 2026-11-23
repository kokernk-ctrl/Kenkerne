package com.kenkern.engine.assets.image

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * FORMATO .KIMG — imagem leve com compressão de GPU (F2.9 / ETAPA C).
 *
 * Header (24 bytes):
 *   magic   "KIMG" (4 bytes)
 *   versão  u16 (= 1; 2 quando os dados de mip estão com Deflate em disco)
 *   formato u16 (ver [KimgFormato])
 *   largura u32, altura u32 (nível 0)
 *   mips    u16 (número de níveis de mipmap)
 *   flags   u16 (bit0 sRGB, bit1 alfa, bit2 normal map, bit3 DEFLATE em disco)
 *   reservado u32 (0)
 * Tabela de mipmaps: mips × (offset u32, tamanho u32) — offsets absolutos
 * desde o início do arquivo. Depois: dados por mip. Bytes BIG-ENDIAN
 * (convenção dos blocos ETC2, que são lidos MSB-first pelo hardware).
 *
 * VERSÃO 2 (auditoria v0.1.15 — V2.4): camada OPCIONAL de compressão EM DISCO
 * (Deflate do JDK, nível configurável). O bit3 do header indica que CADA mip
 * na tabela está DEFLATE-COMPRIMIDO: o campo "tamanho" da tabela passa a ser
 * o tamanho COMPRIMIDO e o leitor infla de volta validando o tamanho esperado
 * do formato (inflação errada = erro CLARO). O escritor SÓ grava versão 2 se
 * o total comprimido ficar MENOR que o cru (decisão por arquivo).
 * ETC2 já é compressão de GPU — o ganho em disco é pequeno; adotado por
 * padrão SÓ porque mediu menor em disco com carga aceitável (DECISIONS #19).
 *
 * Sem metadados desnecessários (decisão da ETAPA C). O leitor valida TODOS os
 * limites: magic/versão/formato desconhecido, mip fora do arquivo, tamanho
 * menor que o esperado para o formato — tudo vira erro CLARO (nunca estoura
 * buffer, nunca silêncio — regra #17).
 */
object KimgFormat {

    const val MAGIC = "KIMG"
    const val VERSAO: Short = 1

    /** Versão 2 = mips com Deflate em disco (flag bit3); o leitor aceita 1 e 2. */
    const val VERSAO_DEFLATE: Short = 2
    const val HEADER_SIZE = 24
    const val MIP_ENTRY_SIZE = 8

    // flags
    const val FLAG_SRGB: Short = 1
    const val FLAG_ALFA: Short = 2
    const val FLAG_NORMAL_MAP: Short = 4

    /** bit3: os mips estão Deflate-comprimidos EM DISCO (não muda o formato de GPU). */
    const val FLAG_DEFLATE: Short = 8
}

/** Formatos de compressão suportados pelo .KIMG (decisões já tomadas da ETAPA C). */
enum class KimgFormato(val id: Short, val rotulo: String) {
    ETC2_RGB(0, "ETC2 RGB8"),
    ETC2_RGBA(1, "ETC2 RGBA8 (EAC)"),
    ASTC_4x4(2, "ASTC 4x4"),
    ASTC_6x6(3, "ASTC 6x6"),
    ASTC_8x8(4, "ASTC 8x8"),
    RGBA8_RAW(5, "RGBA8 sem compressão");

    companion object {
        fun de(id: Short): KimgFormato? = entries.firstOrNull { it.id == id }
    }
}

/** Um nível de mipmap do .KIMG. */
class KimgMip(
    val largura: Int,
    val altura: Int,
    val offset: Int,
    val dados: ByteArray
)

/** Imagem .KIMG decodificada (estrutura, não pixels). */
class KimgImagem(
    val formato: KimgFormato,
    val largura: Int,
    val altura: Int,
    val srgb: Boolean,
    val temAlfa: Boolean,
    val normalMap: Boolean,
    val mips: List<KimgMip>
)

/** Erro claro de .KIMG (regra #17). */
class KimgException(motivo: String) : Exception(motivo)

/**
 * LEITOR do .KIMG: valida magic, versão, formato, limites de cada mip contra o
 * tamanho real do arquivo — truncado/corrompido = erro claro com o motivo.
 */
object KimgReader {

    fun read(bytes: ByteArray, maxDecodedBytes: Int = Int.MAX_VALUE): KimgImagem {
        if (bytes.size < KimgFormat.HEADER_SIZE) {
            throw KimgException(".KIMG truncado: ${bytes.size} bytes (mínimo ${KimgFormat.HEADER_SIZE})")
        }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val magic = ByteArray(4)
        buf.get(magic)
        if (String(magic, Charsets.US_ASCII) != KimgFormat.MAGIC) {
            throw KimgException("magic .KIMG inválido ('${String(magic, Charsets.US_ASCII)}')")
        }
        val versao = buf.short
        if (versao != KimgFormat.VERSAO && versao != KimgFormat.VERSAO_DEFLATE) {
            throw KimgException("versão .KIMG $versao não suportada (esperado ${KimgFormat.VERSAO} ou ${KimgFormat.VERSAO_DEFLATE})")
        }
        val formatoId = buf.short
        val formato = KimgFormato.de(formatoId)
            ?: throw KimgException("formato .KIMG $formatoId desconhecido")
        val largura = buf.int
        val altura = buf.int
        if (largura <= 0 || altura <= 0 || largura > 16384 || altura > 16384) {
            throw KimgException("dimensões .KIMG implausíveis: ${largura}×$altura")
        }
        val mipCount = buf.short.toInt() and 0xFFFF
        if (mipCount < 1 || mipCount > 16) {
            throw KimgException("número de mipmaps implausível: $mipCount")
        }
        val flags = buf.short
        val emDiscoDeflate = (flags.toInt() and KimgFormat.FLAG_DEFLATE.toInt()) != 0
        if (emDiscoDeflate && versao < KimgFormat.VERSAO_DEFLATE) {
            throw KimgException(".KIMG versão $versao não pode carregar a flag DEFLATE (só versão ${KimgFormat.VERSAO_DEFLATE})")
        }
        buf.int // reservado
        val inicioDados = KimgFormat.HEADER_SIZE.toLong() +
            mipCount.toLong() * KimgFormat.MIP_ENTRY_SIZE
        if (inicioDados > bytes.size) {
            throw KimgException(
                ".KIMG truncado: tabela de mipmaps precisa de $inicioDados bytes, arquivo tem ${bytes.size}"
            )
        }

        val mips = ArrayList<KimgMip>(mipCount)
        var fimMipAnterior = inicioDados
        var totalDecoded = 0L
        for (i in 0 until mipCount) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            if (buf.remaining() < KimgFormat.MIP_ENTRY_SIZE) {
                throw KimgException(".KIMG truncado: tabela de mipmaps incompleta (mip $i)")
            }
            val offset = buf.int
            val tamanho = buf.int
            val fim = offset.toLong() + tamanho.toLong()
            if (offset.toLong() < fimMipAnterior || tamanho < 0 || fim > bytes.size) {
                throw KimgException(
                    "mip $i fora da área de dados .KIMG (fora do arquivo ou sobreposto) (offset $offset + $tamanho; próximo offset mínimo $fimMipAnterior, arquivo tem ${bytes.size})"
                )
            }
            fimMipAnterior = fim
            val mw = maxOf(1, largura shr i)
            val mh = maxOf(1, altura shr i)
            val esperado = tamanhoBloco(formato, mw, mh)
            totalDecoded += esperado
            if (totalDecoded > maxDecodedBytes) {
                throw KimgException("mips .KIMG excedem o limite de carga ($maxDecodedBytes bytes decodificados)")
            }
            val dadosMip: ByteArray = if (emDiscoDeflate) {
                // V2: "tamanho" da tabela é o tamanho COMPRIMIDO — infla e
                // valida contra o esperado do formato (erro claro em qualquer
                // divergência; nunca estoura memória: limite = tamanho esperado).
                val inflado = inflar(bytes, offset, tamanho, esperado)
                if (inflado.size != esperado) {
                    throw KimgException(
                        "mip $i inflou para ${inflado.size} bytes, esperado $esperado para $formato ${mw}×$mh"
                    )
                }
                inflado
            } else {
                if (tamanho != esperado) {
                    throw KimgException(
                        "mip $i tem $tamanho bytes, esperado $esperado para $formato ${mw}×$mh"
                    )
                }
                bytes.copyOfRange(offset, fim.toInt())
            }
            mips.add(KimgMip(mw, mh, offset, dadosMip))
        }
        return KimgImagem(
            formato, largura, altura,
            (flags.toInt() and KimgFormat.FLAG_SRGB.toInt()) != 0,
            (flags.toInt() and KimgFormat.FLAG_ALFA.toInt()) != 0,
            (flags.toInt() and KimgFormat.FLAG_NORMAL_MAP.toInt()) != 0,
            mips
        )
    }

    /** Tamanho exato do bloco comprimido para (largura × altura) do formato. */
    fun tamanhoBloco(formato: KimgFormato, largura: Int, altura: Int): Int = when (formato) {
        KimgFormato.ETC2_RGB ->
            blocosDe(largura, altura) * 8
        KimgFormato.ETC2_RGBA ->
            blocosDe(largura, altura) * 16
        KimgFormato.ASTC_4x4 ->
            blocosDim(largura, 4) * blocosDim(altura, 4) * 16
        KimgFormato.ASTC_6x6 ->
            blocosDim(largura, 6) * blocosDim(altura, 6) * 16
        KimgFormato.ASTC_8x8 ->
            blocosDim(largura, 8) * blocosDim(altura, 8) * 16
        KimgFormato.RGBA8_RAW ->
            largura * altura * 4
    }

    /** Número de blocos 4×4 (com padding na borda). */
    fun blocosDe(largura: Int, altura: Int): Int =
        blocosDim(largura, 4) * blocosDim(altura, 4)

    private fun blocosDim(total: Int, lado: Int): Int = (total + lado - 1) / lado

    /**
     * Infla [tamanho] bytes de [bytes] a partir de [offset], com limite
     * HARD de [limiteBytes] (proteção de memória: um arquivo malicioso ou
     * corrompido nunca infla além do formato permite). Erro CLARO em
     * qualquer divergência — nunca silêncio. Usado pelo LEITOR (arquivo do
     * disco) e pelo ESCRITOR (validação do que vai para o disco).
     */
    fun inflar(bytes: ByteArray, offset: Int, tamanho: Int, limiteBytes: Int): ByteArray {
        val fim = offset.toLong() + tamanho.toLong()
        if (offset < 0 || tamanho < 0 || fim > bytes.size) {
            throw KimgException(
                "região Deflate inválida (offset $offset + $tamanho; arquivo tem ${bytes.size} bytes)"
            )
        }
        if (limiteBytes < 0 || limiteBytes == Int.MAX_VALUE) {
            throw KimgException("limite Deflate inválido: $limiteBytes")
        }
        val inflater = java.util.zip.Inflater()
        try {
            inflater.setInput(bytes, offset, tamanho)
            val saida = ByteArray(limiteBytes + 1) // +1: detecta estouro do limite
            var total = 0
            while (!inflater.finished()) {
                val n = inflater.inflate(saida, total, saida.size - total)
                total += n
                if (total > limiteBytes) {
                    throw KimgException("dados Deflate estouram o limite do formato ($limiteBytes bytes)")
                }
                if (inflater.finished()) break
                if (n == 0) {
                    when {
                        inflater.needsDictionary() ->
                            throw KimgException("dados Deflate exigem um dicionário não suportado")
                        inflater.needsInput() ->
                            throw KimgException("dados Deflate truncados (precisa de mais bytes na entrada)")
                        else ->
                            throw KimgException("dados Deflate inválidos (descompressão sem progresso)")
                    }
                }
            }
            if (inflater.remaining != 0) {
                throw KimgException("dados Deflate têm ${inflater.remaining} bytes excedentes")
            }
            return saida.copyOf(total)
        } catch (e: java.util.zip.DataFormatException) {
            throw KimgException("dados Deflate corrompidos: ${e.message ?: "formato inválido"}")
        } finally {
            inflater.end()
        }
    }
}

/**
 * ESCRITOR do .KIMG: header + tabela de mips + dados. Os dados de cada mip
 * DEVEM ter exatamente [KimgReader.tamanhoBloco] bytes (validado aqui —
 * tamanho errado nunca sai para o disco).
 *
 * Compressão EM DISCO (V2.4): [comprimirEmDisco] = true tenta Deflate em cada
 * mip ([nivelDeflate] configurável, padrão do JDK). A decisão é POR ARQUIVO:
 * só grava versão 2 com FLAG_DEFLATE se o total comprimido ficar MENOR que o
 * cru — senão sai versão 1 (byte-idêntica ao formato antigo). O round-trip
 * de cada mip é VALIDADO aqui (infla de volta antes de sair do disco —
 * erro de compressão nunca vira arquivo quebrado).
 */
object KimgWriter {

    fun write(imagem: KimgImagem): ByteArray = write(imagem, comprimirEmDisco = false)

    fun write(
        imagem: KimgImagem,
        comprimirEmDisco: Boolean,
        nivelDeflate: Int = java.util.zip.Deflater.DEFAULT_COMPRESSION
    ): ByteArray {
        if (imagem.mips.isEmpty()) throw KimgException(".KIMG sem mipmaps")
        for (mip in imagem.mips) {
            val esperado = KimgReader.tamanhoBloco(imagem.formato, mip.largura, mip.altura)
            if (mip.dados.size != esperado) {
                throw KimgException(
                    "mip ${mip.largura}×${mip.altura} tem ${mip.dados.size} bytes, esperado $esperado"
                )
            }
        }

        if (!comprimirEmDisco) {
            return montar(imagem, imagem.mips, KimgFormat.VERSAO, false)
        }

        // Tenta Deflate por mip; só adota se o TOTAL ficar menor que o cru.
        val comprimidos = ArrayList<ByteArray>(imagem.mips.size)
        var totalCru = 0
        var totalComprimido = 0
        for (mip in imagem.mips) {
            val deflater = java.util.zip.Deflater(nivelDeflate) // stream zlib — igual ao Inflater do leitor
            try {
                deflater.setInput(mip.dados)
                deflater.finish()
                val out = java.io.ByteArrayOutputStream(mip.dados.size / 2)
                val buf = ByteArray(16 * 1024)
                while (!deflater.finished()) {
                    val n = deflater.deflate(buf)
                    out.write(buf, 0, n)
                }
                val comprimido = out.toByteArray()
                // VALIDAÇÃO de escrita: o que sai para o disco infla de volta
                // para o MESMO conteúdo (round-trip no próprio writer).
                val esperadoMip = KimgReader.tamanhoBloco(imagem.formato, mip.largura, mip.altura)
                val inflado = KimgReader.inflar(comprimido, 0, comprimido.size, esperadoMip)
                if (inflado.size != esperadoMip || !inflado.contentEquals(mip.dados)) {
                    throw KimgException(
                        "validação interna do Deflate falhou no mip ${mip.largura}×${mip.altura}"
                    )
                }
                comprimidos.add(comprimido)
                totalCru += esperadoMip
                totalComprimido += comprimido.size
            } finally {
                deflater.end()
            }
        }
        if (totalComprimido >= totalCru) {
            // Deflate não ajudou (dados já entropicamente comprimidos) — versão 1.
            return montar(imagem, imagem.mips, KimgFormat.VERSAO, false)
        }
        return montarComprimido(imagem, comprimidos)
    }

    /** Monta o arquivo NA FORMA CRUA (versão 1 ou 2 sem deflate; byte-idêntica ao formato antigo). */
    private fun montar(imagem: KimgImagem, mips: List<KimgMip>, versao: Short, comFlagDeflate: Boolean): ByteArray {
        var tamanhoTotal = KimgFormat.HEADER_SIZE + mips.size * KimgFormat.MIP_ENTRY_SIZE
        for (mip in mips) tamanhoTotal += mip.dados.size
        val out = ByteBuffer.allocate(tamanhoTotal).order(ByteOrder.BIG_ENDIAN)
        escreverHeader(out, imagem, mips.size, versao, comFlagDeflate)
        var offset = KimgFormat.HEADER_SIZE + mips.size * KimgFormat.MIP_ENTRY_SIZE
        for (mip in mips) {
            out.putInt(offset)
            out.putInt(mip.dados.size)
            offset += mip.dados.size
        }
        for (mip in mips) out.put(mip.dados)
        return out.array()
    }

    /** Monta o arquivo V2 com a tabela de mips contendo tamanhos COMPRIMIDOS. */
    private fun montarComprimido(imagem: KimgImagem, comprimidos: List<ByteArray>): ByteArray {
        var tamanhoTotal = KimgFormat.HEADER_SIZE + comprimidos.size * KimgFormat.MIP_ENTRY_SIZE
        for (c in comprimidos) tamanhoTotal += c.size
        val out = ByteBuffer.allocate(tamanhoTotal).order(ByteOrder.BIG_ENDIAN)
        escreverHeader(out, imagem, comprimidos.size, KimgFormat.VERSAO_DEFLATE, true)
        var offset = KimgFormat.HEADER_SIZE + comprimidos.size * KimgFormat.MIP_ENTRY_SIZE
        for (i in comprimidos.indices) {
            out.putInt(offset)
            out.putInt(comprimidos[i].size)
            offset += comprimidos[i].size
        }
        for (c in comprimidos) out.put(c)
        return out.array()
    }

    private fun escreverHeader(
        out: ByteBuffer, imagem: KimgImagem, mipCount: Int, versao: Short, comFlagDeflate: Boolean
    ) {
        out.put(KimgFormat.MAGIC.toByteArray(Charsets.US_ASCII))
        out.putShort(versao)
        out.putShort(imagem.formato.id)
        out.putInt(imagem.largura)
        out.putInt(imagem.altura)
        out.putShort(mipCount.toShort())
        var flags: Short = 0
        if (imagem.srgb) flags = (flags.toInt() or KimgFormat.FLAG_SRGB.toInt()).toShort()
        if (imagem.temAlfa) flags = (flags.toInt() or KimgFormat.FLAG_ALFA.toInt()).toShort()
        if (imagem.normalMap) flags = (flags.toInt() or KimgFormat.FLAG_NORMAL_MAP.toInt()).toShort()
        if (comFlagDeflate) flags = (flags.toInt() or KimgFormat.FLAG_DEFLATE.toInt()).toShort()
        out.putShort(flags)
        out.putInt(0) // reservado
    }
}
