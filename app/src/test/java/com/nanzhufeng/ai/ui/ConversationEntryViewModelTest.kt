package com.nanzhufeng.ai.ui

import android.content.Context
import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.app.AppContainer
import com.nanzhufeng.ai.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
class ConversationEntryViewModelTest {
    private val store = ViewModelStore()
    private val clock = Clock.fixed(Instant.parse("2026-09-19T00:00:00Z"), ZoneOffset.UTC)
    private fun model(container: AppContainer, fresh: Boolean = false, retained: ConversationId? = null, selection: P6GModelSelectionOwner = container.p6gModelSelection, background: com.nanzhufeng.ai.background.NormalChatBackgroundExecution = com.nanzhufeng.ai.background.NoopNormalChatBackgroundExecution, checkpoint: (ConversationId, Boolean) -> Boolean = { _, _ -> true }): ConversationFoundationViewModel =
        ViewModelProvider(store, ConversationFoundationViewModel.Factory(
                container.conversationRepository,
                container.createConversation,
                container.appendConversationMessage,
                container.editConversationUserMessage,
                container.saveConversationDraft,
                container.submitConversationDraft,
                container.messagePresentationRenderer,
                container.startLocalConversationRuntime,
                container.applyConversationRuntimeEvent,
                container.deterministicFixtureStreamingAdapter,
                container.conversationActionOrchestrator,
                container.switchConversationBranch,
                container.manageConversation,
                container.searchConversations,
                container.searchConversationAttachments,
                container.glmOcrTaskOwner,
                container.localSearchHistory,
                container.conversationReadMarkerStore,
                container.exportConversationPackage,
                container.gallerySelectionReader,
                container.documentSelectionReader,
                container.addConversationImageAttachment,
                container.removeConversationAttachment,
                container.deletePersistedConversationAttachment,
                container.conversationAttachmentPreviewProjection,
                container.pdfPreviewPositionStore,
                container.videoPreviewPositionStore,
                container.audioPreviewPositionStore,
                container.readConversationAttemptHistory,
                container.temporaryConversationDomain,
                container.addTemporaryConversationAttachment,
                container.clearTemporaryConversation,
                selection,
                container.conversationWebSearchOverrides,
                container.conversationStyleOverrides,
                container.loadAssistantExperienceSettings,
                container.invocationRepository,
                container.assistantResponseModelAttributions,
                container.cloudResponseModelUsages,
                container.contextSelectionAudits,
                container.normalChatOpenRouterExecutor,
                normalChatBackgroundExecution = background,
                startWithFreshChat = fresh,
                entryConversationId = retained,
                ownedSurface = ConversationSurface.CHAT,
                saveAppEntryCheckpoint = checkpoint,
        ))[ConversationFoundationViewModel::class.java]

    private fun ready(viewModel: ConversationFoundationViewModel) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (viewModel.state.isLoading || viewModel.state.isCreating) {
            check(System.nanoTime() < deadline) { "Entry did not settle" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
    }

    private fun fixture(run: (AppContainer, ConversationSnapshot) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val container = runBlocking(Dispatchers.IO) { AppContainer(context, clock) }
        val tree = ConversationTreeService(clock)
        val original = tree.append(tree.create("Pinned history"), AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Text("Historical text must not flash"))))
        val pinned = original.copy(conversation = original.conversation.copy(pinnedAt = clock.instant()))
        runBlocking(Dispatchers.IO) { container.conversationRepository.save(pinned) }
        try { run(container, pinned) } finally {
            store.clear()
            shadowOf(Looper.getMainLooper()).idle()
            com.nanzhufeng.ai.data.ConversationDatabaseHandles.closeForMove(context.getDatabasePath("nanfeng-ai.db").parentFile!!)
        }
    }

    @Test fun `failed foreground retry start keeps the original retry action available`() = fixture { container, pinned ->
        val failed=ConversationTreeService(clock).append(pinned,AppendMessageRequest(MessageRole.ASSISTANT,listOf(ContentBlock.Text("partial fixture")),deliveryState=MessageDeliveryState.FAILED))
        val user=pinned.conversation.currentLeafMessageId!!
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=androidx.room.Room.databaseBuilder(context,com.nanzhufeng.ai.data.local.NanfengAiDatabase::class.java,ConversationDataArea.databaseName(ConversationSurface.CHAT)).build()
        try {
            runBlocking(Dispatchers.IO) {
                container.conversationRepository.save(failed)
                com.nanzhufeng.ai.data.local.RoomNormalChatSendAttemptStore(db).create(NormalChatSendAttempt(NormalChatSendAttemptId("retry-start-fixture"),user,pinned.conversation.id,ProviderId.OPENROUTER,"anthropic/claude-opus-5.5","retry-start-fixture",NormalChatSendAttemptStatus.FAILED,clock.instant(),clock.instant(),safeErrorCode="NETWORK"))
            }
            val starts=java.util.concurrent.atomic.AtomicInteger()
            val background=object:com.nanzhufeng.ai.background.NormalChatBackgroundExecution {
                override fun begin(conversationId:ConversationId,operation:com.nanzhufeng.ai.background.NormalChatBackgroundOperation,authorization:NormalChatEgressAuthorization?):Boolean {starts.incrementAndGet();return false}
                override fun cancel(conversationId:ConversationId) {}
                override fun ownsExecution()=true
                override fun isRunning(conversationId:ConversationId)=false
                override fun runningConversationIds()=emptySet<ConversationId>()
            }
            val holdCheckpoint=AtomicBoolean(false)
            val entered=CountDownLatch(1);val release=CountDownLatch(1);val returned=CountDownLatch(1)
            val vm=model(container,retained=pinned.conversation.id,background=background,checkpoint={_,_->
                if (holdCheckpoint.get()) {entered.countDown();check(release.await(10,TimeUnit.SECONDS));returned.countDown();false} else true
            });ready(vm)
            assertTrue(vm.state.normalSendRecovery?.canRetry==true)
            vm.retryLatestNormalSend()
            val deadline=System.nanoTime()+10_000_000_000L
            while(vm.state.isSending) {check(System.nanoTime()<deadline);shadowOf(Looper.getMainLooper()).idle();Thread.sleep(5)}
            assertTrue("Recovery button must survive a failed service start",vm.state.normalSendRecovery?.canRetry==true)
            vm.retryLatestNormalSend()
            while(vm.state.isSending) {check(System.nanoTime()<deadline);shadowOf(Looper.getMainLooper()).idle();Thread.sleep(5)}
            assertEquals(2,starts.get())
            val other=ConversationTreeService(clock).create("Other conversation")
            runBlocking(Dispatchers.IO) {container.conversationRepository.save(other)}
            holdCheckpoint.set(true)
            vm.retryLatestNormalSend()
            val navDeadline=System.nanoTime()+10_000_000_000L
            try {
                while(entered.count>0L) {check(System.nanoTime()<navDeadline);shadowOf(Looper.getMainLooper()).idle();Thread.sleep(5)}
                vm.selectConversation(other.conversation.id);ready(vm)
                assertEquals(other.conversation.id,vm.state.selectedConversationId)
                release.countDown();assertTrue(returned.await(10,TimeUnit.SECONDS))
                repeat(50) {shadowOf(Looper.getMainLooper()).idle();Thread.sleep(5)}
                assertNull("Old retry failure must not replace another conversation recovery state",vm.state.normalSendRecovery)
                assertEquals(other.conversation.id,vm.state.selectedConversationId)
                assertEquals(2,starts.get())
            } finally {release.countDown()}

        } finally {db.close()}
    }

    @Test fun `initial refresh without explicit selection never displays pinned content`() = fixture { container, pinned ->
        val viewModel = model(container)
        ready(viewModel)
        assertTrue(viewModel.state.conversations.any { it.id == pinned.conversation.id })
        assertNull(viewModel.state.selectedConversationId)
        assertTrue(viewModel.state.messages.isEmpty())
    }

    @Test fun `hidden work navigation leaves current chat and retained work data untouched`() = fixture { container, pinned ->
        val viewModel = model(container, retained = pinned.conversation.id)
        ready(viewModel)
        val messagesBefore = viewModel.state.messages
        val workBefore = runBlocking(Dispatchers.IO) { container.conversationRepository.listActive(ConversationSurface.WORK) }
        viewModel.selectSurface(ConversationSurface.WORK)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(ConversationSurface.CHAT, viewModel.state.surface)
        assertEquals(pinned.conversation.id, viewModel.state.selectedConversationId)
        assertEquals(messagesBefore, viewModel.state.messages)
        assertEquals(workBefore, runBlocking(Dispatchers.IO) { container.conversationRepository.listActive(ConversationSurface.WORK) })
    }

    @Test fun `short return keeps exact selection and expired return clears it synchronously`() = fixture { container, pinned ->
        val viewModel = model(container, retained = pinned.conversation.id)
        ready(viewModel)
        assertEquals(pinned.conversation.id, viewModel.state.selectedConversationId)
        assertTrue(viewModel.state.messages.isNotEmpty())
        viewModel.onAppBackground(1_000)
        assertFalse(viewModel.onAppForeground(2_000))
        assertEquals(pinned.conversation.id, viewModel.state.selectedConversationId)
        viewModel.onAppBackground(3_000)
        assertTrue(viewModel.onAppForeground(3_000 + ConversationAppEntryPolicy.RETENTION_MILLIS))
        assertNull("Old conversation must disappear before the first resumed frame", viewModel.state.selectedConversationId)
        assertTrue(viewModel.state.messages.isEmpty())
        ready(viewModel)
        assertNotEquals(pinned.conversation.id, viewModel.state.selectedConversationId)
        assertNotNull(viewModel.state.draft)
        assertTrue(viewModel.state.messages.isEmpty())
    }
    @Test fun `late metadata from previous transcript cannot replace a newer selection`() = fixture { container, pinned ->
        val context = ApplicationProvider.getApplicationContext<Context>()
        val delegate = com.nanzhufeng.ai.data.AndroidP6GModelSelectionStore(context)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val returned = CountDownLatch(1)
        val first = AtomicBoolean(true)
        val delayed = object : P6GModelSelectionStore by delegate {
            override fun readGlobalDefault(): P6GGlobalDefault {
                if (first.getAndSet(false)) {
                    entered.countDown()
                    check(release.await(10, TimeUnit.SECONDS))
                    returned.countDown()
                }
                return delegate.readGlobalDefault()
            }
        }
        val next = ConversationTreeService(clock).create("Explicit next conversation")
        runBlocking(Dispatchers.IO) { container.conversationRepository.save(next) }
        val viewModel = model(container, retained = pinned.conversation.id,
            selection = P6GModelSelectionOwner(delayed, P6GModelRouter()))
        try {
            val deadline = System.nanoTime() + 10_000_000_000L
            while (entered.count != 0L) {
                check(System.nanoTime() < deadline) { "Metadata read was not reached" }
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(5)
            }
            viewModel.selectConversation(next.conversation.id)
            ready(viewModel)
            assertEquals(next.conversation.id, viewModel.state.selectedConversationId)
            release.countDown()
            assertTrue(returned.await(10, TimeUnit.SECONDS))
            repeat(100) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(5)
                assertEquals("Late old metadata must not replace the visible transcript", next.conversation.id, viewModel.state.selectedConversationId)
            }
            assertTrue(viewModel.state.messages.isEmpty())
        } finally { release.countDown() }
    }

}
