package com.balatechdev.vaultzero.security.auth

import java.util.concurrent.atomic.AtomicReference

/**
 * VaultKeyStorage
 *
 * Storage abstraction for persisting the wrapped MasterKeyEnvelope.
 * Never stores the master password or plaintext master key.
 */
interface VaultKeyStorage {
    fun saveEnvelope(envelope: MasterKeyEnvelope)
    fun getEnvelope(): MasterKeyEnvelope?
    fun hasMasterPassword(): Boolean
    fun clear()
}

/**
 * InMemoryVaultKeyStorage
 *
 * Thread-safe memory storage for envelopes (used in testing and runtime state).
 */
class InMemoryVaultKeyStorage(
    initialEnvelope: MasterKeyEnvelope? = null
) : VaultKeyStorage {

    private val storedEnvelope = AtomicReference<MasterKeyEnvelope?>(initialEnvelope)

    override fun saveEnvelope(envelope: MasterKeyEnvelope) {
        storedEnvelope.set(envelope)
    }

    override fun getEnvelope(): MasterKeyEnvelope? {
        return storedEnvelope.get()
    }

    override fun hasMasterPassword(): Boolean {
        return storedEnvelope.get() != null
    }

    override fun clear() {
        storedEnvelope.set(null)
    }
}
