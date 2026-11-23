package com.kenkern.engine.engine

import com.kenkern.engine.core.Scene
import com.kenkern.engine.core.serialization.CkFormat

/** Captura uma edição consistente e cria uma instância privada da thread GL. */
internal fun Scene.copyForPlay(): Scene? {
    val text = withEditorAccess { CkFormat.writeToString(this) }
    return CkFormat.parseToNewScene(text)
}
