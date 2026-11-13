package com.kenkern.engine.commands

/** Comando do padrão Command da KENKERN (undo/redo global). */
interface Command {
    val label: String
    fun execute()
    fun undo()
}

/** Histórico global de comandos com limite de memória. */
class CommandHistory(private val limit: Int = 100) {

    private val undoStack = ArrayDeque<Command>()
    private val redoStack = ArrayDeque<Command>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun top(): Command? = undoStack.lastOrNull()

    /** Executa o comando e empilha. */
    fun push(command: Command) {
        command.execute()
        if (undoStack.size >= limit) undoStack.removeFirst()
        undoStack.addLast(command)
        redoStack.clear()
    }

    fun undo(): Command? {
        val cmd = undoStack.removeLastOrNull() ?: return null
        cmd.undo()
        redoStack.addLast(cmd)
        return cmd
    }

    fun redo(): Command? {
        val cmd = redoStack.removeLastOrNull() ?: return null
        cmd.execute()
        undoStack.addLast(cmd)
        return cmd
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
