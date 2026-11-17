package com.example.engine

import com.example.model.ProjectDocument

class UndoRedoManager(private val maxHistory: Int = 80) {

    data class HistoryEntry(
        val label: String,
        val document: ProjectDocument
    )

    private val undoStack = ArrayDeque<HistoryEntry>()
    private val redoStack = ArrayDeque<HistoryEntry>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val undoLabel: String? get() = undoStack.lastOrNull()?.label
    val redoLabel: String? get() = redoStack.lastOrNull()?.label

    fun record(label: String, previousState: ProjectDocument) {
        if (undoStack.lastOrNull()?.document == previousState) return
        undoStack.addLast(HistoryEntry(label, previousState))
        while (undoStack.size > maxHistory) {
            undoStack.removeFirst()
        }
        redoStack.clear()
    }

    fun undo(currentState: ProjectDocument): ProjectDocument? {
        if (undoStack.isEmpty()) return null
        val prev = undoStack.removeLast()
        redoStack.addLast(HistoryEntry(prev.label, currentState))
        return prev.document
    }

    fun redo(currentState: ProjectDocument): ProjectDocument? {
        if (redoStack.isEmpty()) return null
        val next = redoStack.removeLast()
        undoStack.addLast(HistoryEntry(next.label, currentState))
        return next.document
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
