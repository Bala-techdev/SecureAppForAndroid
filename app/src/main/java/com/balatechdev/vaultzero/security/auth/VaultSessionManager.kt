package com.balatechdev.vaultzero.security.auth

import com.balatechdev.vaultzero.security.VaultLockState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicReference

data class ActiveVaultSession(
    val hierarchy: MasterKeyHierarchy,
    val createdAtMillis: Long = System.currentTimeMillis(),
    var lastActivityAtMillis: Long = System.currentTimeMillis()
) : AutoCloseable {

    val isAlive: Boolean
        get() = !hierarchy.isDestroyed

    override fun close() {
        hierarchy.close()
    }
}

/**
 * VaultSessionManager
 *
 * Manages the authenticated in-memory session lifetime.
 * Guarantees that authentication is backed by an active [MasterKeyHierarchy] in memory
 * and not a simple boolean flag.
 *
 * Handles inactivity tracking and automatic session expiration with memory zeroization.
 */
class VaultSessionManager(
    var timeoutDurationMillis: Long = DEFAULT_TIMEOUT_MILLIS
) {
    private val activeSession = AtomicReference<ActiveVaultSession?>(null)
    private val _lockState = MutableStateFlow<VaultLockState>(VaultLockState.Locked)
    val lockState: StateFlow<VaultLockState> = _lockState.asStateFlow()

    fun currentSession(currentTimeMillis: Long = System.currentTimeMillis()): ActiveVaultSession? {
        checkAndExpire(currentTimeMillis)
        return activeSession.get()
    }

    val currentSession: ActiveVaultSession?
        get() = currentSession(System.currentTimeMillis())

    fun isUnlocked(currentTimeMillis: Long = System.currentTimeMillis()): Boolean {
        checkAndExpire(currentTimeMillis)
        val session = activeSession.get()
        return session != null && session.isAlive
    }

    val isUnlocked: Boolean
        get() = isUnlocked(System.currentTimeMillis())

    /**
     * Starts an active session with the unlocked key hierarchy.
     */
    fun startSession(hierarchy: MasterKeyHierarchy, currentTimeMillis: Long = System.currentTimeMillis()): ActiveVaultSession {
        // Close any existing session and zeroize previous keys
        lock()

        val session = ActiveVaultSession(
            hierarchy = hierarchy,
            createdAtMillis = currentTimeMillis,
            lastActivityAtMillis = currentTimeMillis
        )
        activeSession.set(session)
        _lockState.value = VaultLockState.Unlocked
        return session
    }

    /**
     * Records user interaction to refresh the inactivity timer.
     */
    fun recordUserActivity(currentTimeMillis: Long = System.currentTimeMillis()) {
        val session = activeSession.get() ?: return
        if (isSessionExpired(currentTimeMillis)) {
            lock()
        } else {
            session.lastActivityAtMillis = currentTimeMillis
        }
    }

    /**
     * Checks if the active session has exceeded the inactivity timeout.
     */
    fun isSessionExpired(currentTimeMillis: Long = System.currentTimeMillis()): Boolean {
        val session = activeSession.get() ?: return false
        val idleTime = currentTimeMillis - session.lastActivityAtMillis
        return idleTime >= timeoutDurationMillis
    }

    /**
     * Checks expiration and immediately locks and zeroizes memory if expired.
     * Returns true if the session expired and was locked.
     */
    fun checkAndExpire(currentTimeMillis: Long = System.currentTimeMillis()): Boolean {
        val session = activeSession.get() ?: return false
        if (isSessionExpired(currentTimeMillis)) {
            lock()
            return true
        }
        return false
    }

    /**
     * Explicitly locks the vault and zeroizes the key hierarchy in memory.
     */
    fun lock() {
        val session = activeSession.getAndSet(null)
        session?.close()
        _lockState.value = VaultLockState.Locked
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS: Long = 5 * 60 * 1000L // 5 minutes
    }
}
