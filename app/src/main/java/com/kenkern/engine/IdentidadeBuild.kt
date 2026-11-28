package com.kenkern.engine

/**
 * F8 (v0.1.31) — identidade da build, FONTE ÚNICA: versão, versionCode, hash
 * curto do commit e data da build (os dois últimos injetados no
 * `BuildConfig` pelo build.gradle.kts na hora da build). Aparece no Hub, no
 * cabeçalho da tela Diagnóstico e no cabeçalho do "Copiar relatório".
 *
 * O teste `F8IdentidadeBuildTest` REPROVA uma build sem hash: build sem
 * identidade não pode ser distribuída (o usuário não consegue dizer qual
 * commit está rodando).
 */
object IdentidadeBuild {

    val versao: String get() = BuildConfig.VERSION_NAME
    val versionCode: Int get() = BuildConfig.VERSION_CODE
    val hashCurto: String get() = BuildConfig.BUILD_HASH
    val dataBuild: String get() = BuildConfig.BUILD_DATE

    /** true só com hash presente e hex de 7-40 caracteres (formato do `git rev-parse --short`). */
    fun hashValido(hash: String = hashCurto): Boolean =
        Regex("[0-9a-f]{7,40}").matches(hash)

    /**
     * Linha única de identidade. Sem hash a linha diz "(sem hash)" — forma
     * DEGRADADA que o teste F8 reprova em build real (e que nunca deve
     * chegar ao aparelho do usuário).
     */
    fun linha(
        versao: String = this.versao,
        versionCode: Int = this.versionCode,
        hash: String = this.hashCurto,
        data: String = this.dataBuild
    ): String =
        "Kenkern v$versao · vc$versionCode · ${if (hashValido(hash)) hash else "(sem hash)"} · $data"
}
