package com.nanzhufeng.ai.data

import androidx.room.RoomDatabase
import java.io.File
import java.lang.ref.WeakReference

/** A location change closes every area's handle for that root, including background owners. */
object ConversationDatabaseHandles {
    private val handles = mutableMapOf<String, MutableList<WeakReference<RoomDatabase>>>()
    @Synchronized fun register(directory: File, database: RoomDatabase) {
        val group = handles.getOrPut(directory.canonicalPath) { mutableListOf() }
        group.removeAll { it.get() == null }
        group += WeakReference(database)
    }
    @Synchronized fun closeForMove(directory: File) {
        handles.remove(directory.canonicalPath)?.mapNotNull { it.get() }?.forEach { it.close() }
    }
}
