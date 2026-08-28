package com.nanzhufeng.ai.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.P6KZipAssetOccurrenceEntity
import com.nanzhufeng.ai.data.local.P6KZipAssetOccurrenceReceiptEntity
import com.nanzhufeng.ai.data.local.RoomPrivateAttachmentRepository
import com.nanzhufeng.ai.domain.AttachmentId
import com.nanzhufeng.ai.domain.AttachmentReference
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class P6KZipSharedAssetCleanupContractsTest {
    private lateinit var database: NanfengAiDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            NanfengAiDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `private bytes remain while any ZIP occurrence receipt references the attachment`() {
        val attachment = AttachmentReference(
            reference = "attachments/v1/${"a".repeat(64)}.png",
            mimeType = "image/png",
            displayName = "shared.png",
            id = AttachmentId("shared"),
            byteCount = 3,
            sha256 = "a".repeat(64),
        )
        val repository = RoomPrivateAttachmentRepository(database)
        repository.save(attachment)
        val zipDao = database.p6kZipImportTaskDao()
        zipDao.insertAssetOccurrence(P6KZipAssetOccurrenceEntity("task", "shared.dat", "source-conversation", "source-message"))
        zipDao.insertAssetOccurrenceReceipt(
            P6KZipAssetOccurrenceReceiptEntity(
                "task", "shared.dat", "source-conversation", "source-message",
                "conversation", "message", attachment.id.value, 1L,
            ),
        )

        assertNull(repository.removeIfUnreferenced(attachment.id))
        assertNotNull(repository.findById(attachment.id))

        zipDao.deleteAssetOccurrenceReceiptsForTask("task")
        zipDao.deleteAssetOccurrencesForTask("task")
        assertNotNull(repository.removeIfUnreferenced(attachment.id))
        assertNull(repository.findById(attachment.id))
    }
}
