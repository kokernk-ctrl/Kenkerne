package com.kenkern.engine.render

import org.junit.Assert.assertEquals
import org.junit.Test

class GlesBackendMipmapPolicyTest {

    @Test
    fun `cadeia completa usa os mips que vieram no KIMG`() {
        assertEquals(
            GlesBackend.MipmapPolicy.USE_PROVIDED,
            GlesBackend.mipmapPolicy(
                width = 8,
                height = 4,
                uploadedLevels = 4,
                formatoRgba8 = false,
                fallbackRgba8 = false
            )
        )
    }

    @Test
    fun `RGBA8 RAW com mip unico gera cadeia antes do filtro mipmapped`() {
        assertEquals(
            4,
            GlesBackend.mipCountCompleto(8, 4)
        )
        assertEquals(
            GlesBackend.MipmapPolicy.GENERATE,
            GlesBackend.mipmapPolicy(
                width = 8,
                height = 4,
                uploadedLevels = 1,
                formatoRgba8 = true,
                fallbackRgba8 = false
            )
        )
    }

    @Test
    fun `fallback ETC2 considera apenas o nivel base realmente enviado`() {
        // O KIMG pode ter a cadeia completa, mas o fallback decodifica só o mip 0.
        assertEquals(
            GlesBackend.MipmapPolicy.GENERATE,
            GlesBackend.mipmapPolicy(
                width = 8,
                height = 8,
                uploadedLevels = 1,
                formatoRgba8 = false,
                fallbackRgba8 = true
            )
        )
    }

    @Test
    fun `ETC2 parcial sem fallback desativa filtragem mipmapped`() {
        assertEquals(
            GlesBackend.MipmapPolicy.LINEAR,
            GlesBackend.mipmapPolicy(
                width = 8,
                height = 8,
                uploadedLevels = 2,
                formatoRgba8 = false,
                fallbackRgba8 = false
            )
        )
    }

    @Test
    fun `textura um por um e completa com o nivel base`() {
        assertEquals(1, GlesBackend.mipCountCompleto(1, 1))
        assertEquals(
            GlesBackend.MipmapPolicy.USE_PROVIDED,
            GlesBackend.mipmapPolicy(
                width = 1,
                height = 1,
                uploadedLevels = 1,
                formatoRgba8 = true,
                fallbackRgba8 = false
            )
        )
    }
}
