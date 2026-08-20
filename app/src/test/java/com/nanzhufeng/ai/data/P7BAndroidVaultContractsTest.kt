package com.nanzhufeng.ai.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class P7BAndroidVaultContractsTest {
    @Test fun `Room eighteen persists only secret free vault references`() {
        val context = ApplicationProvider.getApplicationContext<Context>(); context.deleteDatabase("p7b-vault.db")
        val database = Room.databaseBuilder(context, NanfengAiDatabase::class.java, "p7b-vault.db").build()
        try {
            val columns = database.openHelper.writableDatabase.query("PRAGMA table_info(sync_account_metadata)").use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) } }
            assertFalse(columns.any { it.contains("token", true) || it.contains("recovery", true) || it.contains("dataKey", true) || it.contains("ciphertext", true) })
        } finally { database.close(); context.deleteDatabase("p7b-vault.db") }
    }
}
