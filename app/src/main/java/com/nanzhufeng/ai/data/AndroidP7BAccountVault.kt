package com.nanzhufeng.ai.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.SyncAccountMetadataEntity
import com.nanzhufeng.ai.data.local.SyncIntentEntity
import com.nanzhufeng.ai.domain.P7BAccountMetadata
import com.nanzhufeng.ai.domain.P7BIntentReceipt
import com.nanzhufeng.ai.domain.P7BKeyVault
import com.nanzhufeng.ai.domain.P7BMetadataStore
import com.nanzhufeng.ai.domain.P7BSyncState
import com.nanzhufeng.ai.domain.P7BDirectionFact
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

/** Android-only P7-B vault: the data key is encrypted in app-private storage and never enters Room. */
class AndroidP7BAccountVault(private val context: Context) : P7BKeyVault {
    private val random = SecureRandom()
    override fun createAccountKey(accountRef: String): P7BKeyVault.Created {
        val alias = "com.nanzhufeng.ai.p7b.$accountRef"; val target = blob(accountRef)
        require(!target.exists()) { "existing key blob requires persisted metadata" }
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        require(!store.containsAlias(alias)) { "existing keystore alias requires persisted metadata" }
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        }.generateKey()
        val plain = ByteArray(32).also(random::nextBytes)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, store.getKey(alias, null)) }
            val encrypted = cipher.doFinal(plain); val bytes = byteArrayOf(cipher.iv.size.toByte()) + cipher.iv + encrypted
            target.parentFile?.mkdirs(); val part = File(target.parentFile, "${target.name}.part")
            part.writeBytes(bytes); require(part.renameTo(target)) { "vault blob atomic move failed" }
            return P7BKeyVault.Created(alias, "p7b-vault/v1/${target.name}", sha256(bytes))
        } catch (error: Throwable) { store.deleteEntry(alias); target.delete(); throw error } finally { plain.fill(0) }
    }
    override fun <T> withUnsealedDataKey(accountRef: String, block: (ByteArray) -> T): T {
        val bytes = blob(accountRef).readBytes(); val alias = "com.nanzhufeng.ai.p7b.$accountRef"; val ivLength = bytes.firstOrNull()?.toInt() ?: error("key blob unavailable")
        require(ivLength == 12 && bytes.size > 1 + ivLength + 16) { "key blob invalid" }
        val key = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getKey(alias, null) ?: error("keystore key unavailable")
        val plain = Cipher.getInstance("AES/GCM/NoPadding").run { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(1, 1 + ivLength))); doFinal(bytes.copyOfRange(1 + ivLength, bytes.size)) }
        try { require(plain.size == 32); return block(plain) } finally { plain.fill(0) }
    }
    override fun isUsable(metadata: P7BAccountMetadata): Boolean = runCatching {
        val alias = metadata.keyAliasRef ?: return false; val ref = metadata.wrappedKeyRef ?: return false
        require(alias == "com.nanzhufeng.ai.p7b.${metadata.accountRef}" && ref == "p7b-vault/v1/${metadata.accountRef}.wrapped")
        val bytes = blob(metadata.accountRef).readBytes(); require(sha256(bytes) == metadata.wrappedKeySha256)
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.containsAlias(alias)
    }.getOrDefault(false)
    private fun blob(accountRef: String) = File(context.filesDir, "p7b-vault/v1/$accountRef.wrapped")
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

class RoomP7BMetadataStore(private val database: NanfengAiDatabase, private val now: () -> Long = { System.currentTimeMillis() }) : P7BMetadataStore {
    private val dao get() = database.syncAccountMetadataDao()
    override fun account(accountRef: String) = dao.find(accountRef)?.toDomain()
    override fun receipt(intentId: String) = dao.receipt(intentId)?.toDomain()
    override fun transaction(block: () -> P7BIntentReceipt): P7BIntentReceipt = database.runInTransaction<P7BIntentReceipt> { block() }
    override fun save(metadata: P7BAccountMetadata, receipt: P7BIntentReceipt) {
        dao.save(SyncAccountMetadataEntity(metadata.accountRef, metadata.state.name, metadata.revision, metadata.keyAliasRef, metadata.wrappedKeyRef, metadata.wrappedKeySha256, metadata.directionFact?.name, metadata.lastError, now()))
        dao.saveReceipt(SyncIntentEntity(receipt.intentId, receipt.accountRef, receipt.expectedRevision, receipt.resultingRevision, receipt.state.name, now()))
    }
    private fun SyncAccountMetadataEntity.toDomain() = P7BAccountMetadata(accountRef, P7BSyncState.valueOf(state), revision, keyAliasRef, wrappedKeyRef, wrappedKeySha256, directionFact?.let(P7BDirectionFact::valueOf), lastError)
    private fun SyncIntentEntity.toDomain() = P7BIntentReceipt(intentId, accountRef, expectedRevision, resultingRevision, P7BSyncState.valueOf(resultingState))
}
