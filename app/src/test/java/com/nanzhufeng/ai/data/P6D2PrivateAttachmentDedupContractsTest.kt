package com.nanzhufeng.ai.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.RoomPrivateAttachmentRepository
import com.nanzhufeng.ai.domain.AttachmentId
import com.nanzhufeng.ai.domain.AttachmentReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** P6-D2: dedup is a Room fact and remains readable after the repository is rebuilt. */
@RunWith(RobolectricTestRunner::class)
class P6D2PrivateAttachmentDedupContractsTest {
    private lateinit var database: NanfengAiDatabase

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            NanfengAiDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After fun tearDown() = database.close()

    @Test fun `same SHA 256 uses the original private asset and survives repository rebuild`() {
        val first = AttachmentReference(
            reference = "attachments/v1/${"a".repeat(64)}.png",
            mimeType = "image/png",
            displayName = "first.png",
            id = AttachmentId("first"),
            byteCount = 3,
            sha256 = "a".repeat(64),
        )
        val duplicate = first.copy(
            reference = "attachments/v1/${"a".repeat(64)}-duplicate.png",
            displayName = "duplicate.png",
            id = AttachmentId("duplicate"),
        )

        val firstSaved = RoomPrivateAttachmentRepository(database).save(first)
        val duplicateSaved = RoomPrivateAttachmentRepository(database).save(duplicate)
        val restored = RoomPrivateAttachmentRepository(database).findBySha256(requireNotNull(first.sha256))

        assertEquals(firstSaved, duplicateSaved)
        assertNotNull(restored)
        assertEquals(firstSaved, restored)
    }
}
