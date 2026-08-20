package com.nanzhufeng.ai.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.RoomP6KProfilePersonalizationSettingsOwner
import com.nanzhufeng.ai.data.local.RoomP6KZipImportTaskRepository
import com.nanzhufeng.ai.domain.P6KChatGptZipCandidateMapper
import com.nanzhufeng.ai.domain.P6KProfileCommitResult
import com.nanzhufeng.ai.domain.P6KZipImportTask
import com.nanzhufeng.ai.domain.P6KZipTaskId
import com.nanzhufeng.ai.domain.P6KZipTaskStatus
import com.nanzhufeng.ai.domain.ThirdPartyZipInventoryPolicy
import com.nanzhufeng.ai.domain.ThirdPartyZipInventoryResult
import com.nanzhufeng.ai.domain.ThirdPartyZipProvider
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class P6KProfilePersonalizationOwnerRoomContractsTest {
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_010_000), ZoneOffset.UTC)

    @Test fun `versioned fixture maps to one owner with replay conflict reopen and batch revoke`() {
        val context = ApplicationProvider.getApplicationContext<Context>(); val name = "p6k-profile-${UUID.randomUUID()}.db"; context.deleteDatabase(name)
        val fixture = zip(
            "conversations.json" to """[{"id":"fixture","title":"Fixture","create_time":1,"update_time":2,"mapping":{"m":{"parent":null,"message":{"author":{"role":"user"},"content":{"parts":["fixture"]}}}}}]""",
            "profile-personalization-v1.json" to """{"format":"nfai.third-party-profile-personalization","version":1,"personalization":{"displayName":"Fixture","language":"zh-CN","notificationsEnabled":true}}""",
        )
        try {
            val mapped = P6KChatGptZipCandidateMapper().map(ThirdPartyZipProvider.CHATGPT, fixture, (ThirdPartyZipInventoryPolicy.inspect(fixture) as ThirdPartyZipInventoryResult.InventoriedUnsupported).entries) as com.nanzhufeng.ai.domain.P6KChatGptZipMappingResult.Mapped
            val task = P6KZipImportTask(P6KZipTaskId.new(), ThirdPartyZipProvider.CHATGPT, "fixture.zip", fixture.length(), "fixture-package", P6KZipTaskStatus.AWAITING_CONFIRMATION, createdAt = clock.instant(), updatedAt = clock.instant(), profile = mapped.profile)
            val database = open(context, name); val tasks = RoomP6KZipImportTaskRepository(database); val owner = RoomP6KProfilePersonalizationSettingsOwner(database); tasks.save(task)
            val value = requireNotNull(mapped.profile.personalization)
            assertEquals(P6KProfileCommitResult.Committed, owner.commit(task, value, clock.instant()))
            assertEquals(P6KProfileCommitResult.Replayed, owner.commit(task, value, clock.instant()))
            assertEquals("OWNER_COMMITTED", tasks.find(task.id)!!.profile.status); assertEquals("Fixture", owner.read()!!.displayName)
            val conflicting = task.copy(id = P6KZipTaskId.new(), packageHash = "different-package", profile = mapped.profile.copy(personalization = null)); tasks.save(conflicting)
            assertEquals(P6KProfileCommitResult.Conflict, owner.commit(conflicting, value, clock.instant()))
            assertEquals("CONFLICT_PROFILE_OWNER", tasks.find(conflicting.id)!!.profile.status)
            database.close()
            val reopened = open(context, name); val reopenedOwner = RoomP6KProfilePersonalizationSettingsOwner(reopened)
            assertEquals("zh-CN", reopenedOwner.read()!!.language); assertTrue(reopenedOwner.revokeBatch(task, clock.instant())); assertNull(reopenedOwner.read()); reopened.close()
        } finally { fixture.delete(); context.deleteDatabase(name) }
    }

    private fun open(context: Context, name: String) = Room.databaseBuilder(context, NanfengAiDatabase::class.java, name).allowMainThreadQueries().build()
    private fun zip(vararg entries: Pair<String, String>): File = File.createTempFile("p6k-profile-", ".zip").also { file -> ZipOutputStream(file.outputStream()).use { zip -> entries.forEach { (name, text) -> zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry() } } }
}
