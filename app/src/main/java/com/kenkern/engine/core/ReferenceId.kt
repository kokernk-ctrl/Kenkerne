package com.kenkern.engine.core

import java.util.UUID

/**
 * Identidade estável da KENKERN. Um ReferenceId sobrevive a renomear e mover:
 * é a chave usada pela serialização .ck, pelo sistema de referências e pelo Linkerf.
 */
@JvmInline
value class ReferenceId(val value: String) {

    fun isValid(): Boolean = value.isNotBlank() && value != INVALID.value

    companion object {
        val INVALID = ReferenceId("invalid")

        fun generate(): ReferenceId = ReferenceId(UUID.randomUUID().toString())

        fun of(value: String): ReferenceId = if (value.isBlank()) INVALID else ReferenceId(value)
    }
}
