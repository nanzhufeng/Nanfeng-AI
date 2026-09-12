package com.nanzhufeng.ai.data

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidDataStorageLocationContractsTest {
    @Test fun `mobile data location uses only application internal or device storage roots`() {
        val source = File("src/main/java/com/nanzhufeng/ai/data/AndroidDataStorageLocation.kt").readText()
        assertTrue(source.contains("app.filesDir"))
        assertTrue(source.contains("app.getExternalFilesDir(EXTERNAL_DIRECTORY)"))
        assertFalse(source.contains("/Users/"))
        assertFalse(source.contains("p6b-workspace"))
    }

    @Test fun `path move validates the database before committing the new bootstrap pointer`() {
        val source = File("src/main/java/com/nanzhufeng/ai/data/AndroidDataStorageLocation.kt").readText()
        assertTrue(source.indexOf("verifyDatabase(File(stagingRoot") < source.indexOf("putString(KEY_LOCATION"))
        assertTrue(source.contains("PRAGMA integrity_check"))
        assertTrue(source.contains("copyFilesTree(sourceRoot, sourceDatabaseDirectory"))
        assertTrue(source.contains("copyDatabaseFiles(sourceDatabaseDirectory"))
    }

    @Test fun `movable storage context is limited to Room rather than application bootstrap`() {
        val source = File("src/main/java/com/nanzhufeng/ai/app/AppContainer.kt").readText()
        assertTrue(source.contains("private val context: Context = baseContext.applicationContext"))
        assertTrue(source.contains("private val databaseContext: Context = dataStorageLocationOwner.storageContext()"))
        assertTrue(source.contains("PDFBoxResourceLoader.init(context.applicationContext)"))
        assertTrue(source.contains("Room.databaseBuilder(\n        databaseContext,"))
        assertFalse(source.contains("private val context: Context = dataStorageLocationOwner.storageContext()"))
    }
}
