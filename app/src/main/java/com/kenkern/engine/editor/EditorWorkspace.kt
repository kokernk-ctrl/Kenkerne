package com.kenkern.engine.editor

/** Each workspace keeps its own panel layout around the same editor viewport. */
enum class EditorWorkspace(val key: String) {
    MODEL("model"), ANIMATE("animate"), TEXTURE("texture");

    // Preserve the old editor's saved layouts in the default workspace.
    fun panelKey(base: String): String = if (this == MODEL) base else "$base@$key"

    val hierarchyByDefault: Boolean get() = this == MODEL
}
