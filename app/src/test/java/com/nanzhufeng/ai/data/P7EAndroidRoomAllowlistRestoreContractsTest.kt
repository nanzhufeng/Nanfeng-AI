package com.nanzhufeng.ai.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.domain.NfaiSyncKnownAnswerMaterial
import com.nanzhufeng.ai.domain.NfaiSyncResult
import com.nanzhufeng.ai.domain.NfaiSyncV1Gateway
import com.nanzhufeng.ai.domain.P7ERestorePlanCoordinator
import com.nanzhufeng.ai.domain.P7ERestoreResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** The deprecated raw-table prototype must never bypass P7-E's semantic-record restore gate. */
@RunWith(RobolectricTestRunner::class)
class P7EAndroidRoomAllowlistRestoreContractsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After fun cleanup() {
        context.deleteDatabase("nanfeng-ai.db")
        File(context.filesDir, "p7e-allowlist-restore").deleteRecursively()
    }

    @Test fun `raw Room table snapshot is rejected before it can replace current business truth`() {
        context.deleteDatabase("nanfeng-ai.db")
        val room = Room.databaseBuilder(context, NanfengAiDatabase::class.java, "nanfeng-ai.db").allowMainThreadQueries().build()
        room.openHelper.writableDatabase.execSQL(
            "INSERT INTO projects(id,title,description,colorSemantic,iconSemantic,createdAtEpochMs,updatedAtEpochMs,archivedAtEpochMs,pinnedAtEpochMs,deletedAtEpochMs,schemaVersion) VALUES ('project-p7e','Before restore','',NULL,NULL,1,1,NULL,NULL,NULL,1)"
        )
        val owner = AndroidP7EAllowlistWorkspaceOwner(context, room)
        val snapshot = owner.snapshot("document-p7e", 1)
        assertEquals(setOf("project", "conversation", "knowledge", "memory", "relation", "safe_settings"), snapshot.records.map { it.kind }.toSet())
        val envelope = (NfaiSyncV1Gateway.sealKnownAnswer(
            snapshot,
            "fixture recovery only".toCharArray(),
            NfaiSyncKnownAnswerMaterial(ByteArray(32) { 7 }, ByteArray(16) { 2 }, ByteArray(12) { 3 }, ByteArray(12) { 4 }),
        ) as NfaiSyncResult.Sealed).canonicalEnvelope
        room.openHelper.writableDatabase.execSQL("UPDATE projects SET title='Changed locally' WHERE id='project-p7e'")
        val coordinator = P7ERestorePlanCoordinator(owner)
        val result = coordinator.plan(envelope, "fixture recovery only".toCharArray(), "com.nanzhufeng.ai", "document-p7e", 1, com.nanzhufeng.ai.domain.P7ERestoreMode.REPLACE_LOCAL_CONFIRMED)
        assertEquals(P7ERestoreResult.Rejected("SEMANTIC_RECORD_REJECTED"), result)
        SQLiteDatabase.openDatabase(context.getDatabasePath("nanfeng-ai.db").path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT title FROM projects WHERE id='project-p7e'", null).use { cursor ->
                assertTrue(cursor.moveToFirst()); assertEquals("Changed locally", cursor.getString(0))
            }
            db.rawQuery("PRAGMA integrity_check", null).use { cursor -> assertTrue(cursor.moveToFirst() && cursor.getString(0) == "ok") }
        }
    }
}
