package com.nanzhufeng.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class P7BAccountSyncContractsTest {
    private class Store : P7BMetadataStore {
        val accounts = linkedMapOf<String, P7BAccountMetadata>(); val receipts = linkedMapOf<String, P7BIntentReceipt>()
        override fun account(accountRef: String) = accounts[accountRef]
        override fun receipt(intentId: String) = receipts[intentId]
        override fun transaction(block: () -> P7BIntentReceipt) = block()
        override fun save(metadata: P7BAccountMetadata, receipt: P7BIntentReceipt) { accounts[metadata.accountRef] = metadata; receipts[receipt.intentId] = receipt }
    }
    private class Vault : P7BKeyVault {
        val keys = linkedMapOf<String, ByteArray>(); var creates = 0
        override fun createAccountKey(accountRef: String): P7BKeyVault.Created { creates++; keys[accountRef] = ByteArray(32) { creates.toByte() }; return P7BKeyVault.Created("alias-$accountRef", "ref-$accountRef", "hash-$accountRef") }
        override fun <T> withUnsealedDataKey(accountRef: String, block: (ByteArray) -> T): T { val key = keys.getValue(accountRef).copyOf(); try { return block(key) } finally { key.fill(0) } }
        override fun isUsable(metadata: P7BAccountMetadata) = metadata.accountRef in keys
    }
    @Test fun `direct login never creates or reads recovery keys and survives sign out`() {
        val store = Store()
        val deniedVault = object : P7BKeyVault {
            override fun createAccountKey(accountRef: String): P7BKeyVault.Created = error("must not create a key")
            override fun <T> withUnsealedDataKey(accountRef: String, block: (ByteArray) -> T): T = error("must not open a key")
            override fun isUsable(metadata: P7BAccountMetadata): Boolean = error("must not inspect a key")
        }
        val machine = P7BAccountStateMachine(store, deniedVault)
        val first = machine.authenticateDirect("direct", null, "direct-fixture-account")
        assertEquals(P7BSyncState.DIRECTION_REQUIRED, first.state)
        assertEquals(first, machine.authenticateDirect("direct", null, "direct-fixture-account"))
        val ready = machine.chooseDirection("direction", first.resultingRevision, first.accountRef, P7BDirectionFact.LOCAL_PRESENT_EMPTY_REMOTE)
        assertEquals(P7BSyncState.READY, ready.state)
        val out = machine.signOutKeepLocal("out", ready.resultingRevision, first.accountRef)
        assertEquals(P7BSyncState.DIRECTION_REQUIRED, machine.authenticateDirect("again", out.resultingRevision, "direct-fixture-account").state)
    }

    @Test fun `direct login repairs only obsolete key failures and preserves real conflicts`() {
        val store = Store(); val machine = P7BAccountStateMachine(store, Vault())
        val ref = P7BAccountStateMachine.accountRef("direct-legacy-account")
        val old = P7BAccountMetadata(ref, P7BSyncState.FAILED, 7, "old-alias", "old-ref", "old-hash", null, "KEY_MATERIAL_UNAVAILABLE")
        store.accounts[ref] = old
        assertEquals(P7BSyncState.DIRECTION_REQUIRED, machine.authenticateDirect("repair", 7, "direct-legacy-account").state)
        assertEquals("old-ref", store.accounts[ref]?.wrappedKeyRef)
        store.accounts[ref] = old.copy(state = P7BSyncState.CONFLICT, lastError = "REMOTE_CHANGED")
        assertEquals(P7BSyncState.CONFLICT, machine.authenticateDirect("conflict", 7, "direct-legacy-account").state)
    }

    private fun verified(value: String) = VerifiedAccountHandle.fromVerifiedAuthentication(value)

    @Test fun `new verified account is idempotent gated and isolates keys`() {
        val store = Store(); val vault = Vault(); val machine = P7BAccountStateMachine(store, vault)
        val first = machine.authenticate("intent-auth-a", null, verified("verified-account-a")); val replay = machine.authenticate("intent-auth-a", null, verified("verified-account-a"))
        assertEquals(first, replay); assertEquals(1, vault.creates); assertEquals(P7BSyncState.AUTHENTICATED_NEEDS_RECOVERY_CONFIRMATION, first.state)
        val account = first.accountRef; assertTrue(runCatching { machine.withReadyDataKey(account) { it.size } }.isFailure)
        val confirmed = machine.confirmRecoverySaved("intent-confirm-a", 1, account); assertEquals(P7BSyncState.DIRECTION_REQUIRED, confirmed.state)
        val ready = machine.chooseDirection("intent-direction-a", 2, account, P7BDirectionFact.LOCAL_PRESENT_EMPTY_REMOTE); assertEquals(P7BSyncState.READY, ready.state)
        assertEquals(32, machine.withReadyDataKey(account) { it.size })
        val other = machine.authenticate("intent-auth-b", null, verified("verified-account-b")); assertFalse(other.accountRef == account); assertEquals(2, vault.creates)
    }

    @Test fun `direction conflict logout and invalid key all fail closed without scheduling`() {
        val store = Store(); val vault = Vault(); val machine = P7BAccountStateMachine(store, vault); val account = machine.authenticate("auth", null, verified("verified-account-c")).accountRef
        machine.confirmRecoverySaved("confirm", 1, account); val conflict = machine.chooseDirection("direction", 2, account, P7BDirectionFact.LOCAL_PRESENT_REMOTE_PRESENT)
        assertEquals(P7BSyncState.CONFLICT, conflict.state); assertTrue(runCatching { machine.withReadyDataKey(account) { } }.isFailure)
        val signedOut = machine.signOutKeepLocal("logout", 3, account); assertEquals(P7BSyncState.SIGNED_OUT_KEEP_LOCAL, signedOut.state)
        assertEquals(P7BSyncState.SIGNED_OUT_KEEP_LOCAL, machine.restoreAtColdStart(account)?.state)
        vault.keys.remove(account); assertEquals(P7BSyncState.FAILED, machine.authenticate("re-auth-missing-key", 4, verified("verified-account-c")).state)
    }

    @Test fun `matching local receipt and remote head may resume manual sync after login`() {
        val store = Store(); val vault = Vault(); val machine = P7BAccountStateMachine(store, vault)
        val account = machine.authenticate("auth-matched", null, verified("verified-account-matched")).accountRef
        machine.confirmRecoverySaved("confirm-matched", 1, account)
        val ready = machine.chooseDirection("direction-matched", 2, account, P7BDirectionFact.LOCAL_PRESENT_REMOTE_MATCHED)
        assertEquals(P7BSyncState.READY, ready.state)
    }
}
