package com.kenkern.engine.assets.image

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * FORMATO .KIMG — imagem leve com compressão de GPU (F2.9 / ETAPA C).
 *
 * Header (24 bytes):
 *   magic   "KIMG" (4 bytes)
 *   versão  u16 (= 1)
 *   formato u16 (ver [KimgFormato])
 *   largura u32, altura u32 (nível 0)
 *   mips    u16 (número de níveis de mipmap)
 *   flags   u16 (bit0 sRGB, bit1 alfa, bit2 normal map)
 *   reservado u32 (0)
 * Tabela de mipmaps: mips × (offset u32, tamanho u32) — offsets absolutos
 * desde o início do arquivo. Depois: dados por mip. Bytes BIG-ENDIAN
 * (convenção dos blocos ETC2, que são lidos MSB-first pelo hardware).
 *
 * Sem metadados desnecessários (decisão da ETAPA C). O leitor valida TODOS os
 * limites: magic/versão/formato desconhecido, mip fora do arquivo, tamanho
 * menor que o esperado para o formato — tudo vira erro CLARO (nunca estoura
 * buffer, nunca silêncio — regra #17).
 */
object KimgFormat {

    const val MAGIC = "KIMG"
    const val VERSAO: Short = 1
    const val HEADER_SIZE = 24
    const val MIP_ENTRY_SIZE = 8

    // flags
    const val FLAG_SRGB: Short = 1
    const val FLAG_ALFA: Short = 2
    const val FLAG_NORMAL_MAP: Short = 4
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

    fun read(bytes: ByteArray): KimgImagem {
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
        if (versao != KimgFormat.VERSAO) {
            throw KimgException("versão .KIMG $versao não suportada (esperado ${KimgFormat.VERSAO})")
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
        buf.int // reservado

        val mips = ArrayList<KimgMip>(mipCount)
        for (i in 0 until mipCount) {
            if (buf.remaining() < KimgFormat.MIP_ENTRY_SIZE) {
                throw KimgException(".KIMG truncado: tabela de mipmaps incompleta (mip $i)")
            }
            val offset = buf.int
            val tamanho = buf.int
            if (offset < 0 || tamanho < 0 || offset + tamanho > bytes.size) {
                throw KimgException(
                    "mip $i fora do arquivo (offset $offset + $tamanho > ${bytes.size}) — arquivo truncado ou corrompido"
                )
            }
            val mw = maxOf(1, largura shr i)
            val mh = maxOf(1, altura shr i)
            val esperado = tamanhoBloco(formato, mw, mh)
            if (tamanho != esperado) {
                throw KimgException(
                    "mip $i tem $tamanho bytes, esperado $esperado para $formato ${mw}×$mh"
                )
            }
            mips.add(KimgMip(mw, mh, offset, bytes.copyOfRange(offset, offset + tamanho)))
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
}

/**
 * ESCRITOR do .KIMG: header + tabela de mips + dados. Os dados de cada mip
 * DEVEM ter exatamente [KimgReader.tamanhoBloco] bytes (validado aqui —
 * tamanho errado nunca sai para o disco).
 */
object KimgWriter {

    fun write(imagem: KimgImagem): ByteArray {
        if (imagem.mips.isEmpty()) throw KimgException(".KIMG sem mipmaps")
        var tamanhoTotal = KimgFormat.HEADER_SIZE + imagem.mips.size * KimgFormat.MIP_ENTRY_SIZE
        for (mip in imagem.mips) {
            val esperado = KimgReader.tamanhoBloco(imagem.formato, mip.largura, mip.altura)
            if (mip.dados.size != esperado) {
                throw KimgException(
                    "mip ${mip.largura}×${mip.altura} tem ${mip.dados.size} bytes, esperado $esperado"
                )
            }
            tamanhoTotal += mip.dados.size
        }
        val out = ByteBuffer.allocate(tamanhoTotal).order(ByteOrder.BIG_ENDIAN)
        out.put(KimgFormat.MAGIC.toByteArray(Charsets.US_ASCII))
        out.putShort(KimgFormat.VERSAO)
        out.putShort(imagem.formato.id)
        out.putInt(imagem.largura)
        out.putInt(imagem.altura)
        out.putShort(imagem.mips.size.toShort())
        var flags: Short = 0
        if (imagem.srgb) flags = (flags.toInt() or KimgFormat.FLAG_SRGB.toInt()).toShort()
        if (imagem.temAlfa) flags = (flags.toInt() or KimgFormat.FLAG_ALFA.toInt()).toShort()
        if (imagem.normalMap) flags = (flags.toInt() or KimgFormat.FLAG_NORMAL_MAP.toInt()).toShort()
        out.putShort(flags)
        out.putInt(0) // reservado

        var offset = KimgFormat.HEADER_SIZE + imagem.mips.size * KimgFormat.MIP_ENTRY_SIZE
        for (mip in imagem.mips) {
            out.putInt(offset)
            out.putInt(mip.dados.size)
            offset += mip.dados.size
        }
        for (mip in imagem.mips) out.put(mip.dados)
        return out.array()
    }
}
