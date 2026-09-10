package com.nanzhufeng.ai.ui

import android.content.Context
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.RoomMemoryRepository
import com.nanzhufeng.ai.domain.*
import java.time.Clock
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.PAUSED)
class MemorySummaryEditorFlowTest {
    private fun awaitState(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("ViewModel did not reach the expected observable state", condition())
    }

    @Test fun filteredSummaryCanBeEditedInFullCancelledAndEntirelyReplaced() {
        val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NanfengAiDatabase::class.java)
            .allowMainThreadQueries().build()
        val store = ViewModelStore()
        try {
            val repository = RoomMemoryRepository(database)
            val manage = ManageMemoryUseCase(MemoryDomain(Clock.systemUTC()), repository)
            for (id in listOf("alpha", "beta")) {
                manage.execute(MemoryIntent(MemoryIntentId.new(), MemoryIntentAction.CREATE, MemoryId(id),
                    id, "$id original body", MemoryScope(MemoryScopeKind.GLOBAL)))
            }
            val model = MemoryViewModel(manage)
            store.put("editor", model)
            awaitState { model.state.memories.size == 2 }
            model.querySummary("alpha")
            awaitState { model.state.memories.size == 1 }
            model.editSummary()
            awaitState { model.state.summaryEditor?.loading == false }
            assertTrue(model.state.summaryEditor!!.text.contains("beta original body"))
            model.updateSummaryEditor("discard this")
            model.dismissSummaryEditor()
            assertEquals(2, repository.list(MemoryScopeKind.GLOBAL, MemoryStatus.ACTIVE, "").size)
            model.editSummary()
            awaitState { model.state.summaryEditor?.loading == false }
            model.updateSummaryEditor("Completely replaced full text")
            model.saveSummaryEditor()
            awaitState { model.state.summaryEditor == null && model.state.memories.singleOrNull()?.memory?.body == "Completely replaced full text" }
            assertEquals("", model.state.search)
            assertEquals("Completely replaced full text", RoomMemoryRepository(database).list(MemoryScopeKind.GLOBAL, MemoryStatus.ACTIVE, "").single().memory.body)
            model.editSummary()
            awaitState { model.state.summaryEditor?.loading == false }
            assertEquals("Completely replaced full text", model.state.summaryEditor!!.text)
            model.updateSummaryEditor("密码: fixture-only-secret")
            model.saveSummaryEditor()
            awaitState { model.state.summaryEditor?.error != null }
            assertEquals("密码: fixture-only-secret", model.state.summaryEditor!!.text)
            assertEquals("Completely replaced full text", repository.list(MemoryScopeKind.GLOBAL, MemoryStatus.ACTIVE, "").single().memory.body)
        } finally { store.clear(); database.close() }
    }
}
