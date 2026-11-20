package com.balatechdev.vaultzero.security.clipboard

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/**
 * SecureClipboard
 *
 * Provides privacy-preserving clipboard operations:
 * - Flags clips with EXTRA_IS_SENSITIVE (Android 13+ / API 33+) to suppress notifications & sync.
 * - Schedules automatic clipboard purging after timeout (default: 30 seconds).
 * - Avoids sensitive logging.
 */
interface SecureClipboard {
    val clipboardState: StateFlow<ClipboardState>
    fun copySensitive(label: String, content: CharSequence, timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS)
    fun clear()
    fun isCleared(): Boolean

    companion object {
        const val DEFAULT_TIMEOUT_SECONDS: Long = 30L
    }
}

sealed interface ClipboardState {
    data object Empty : ClipboardState
    data class Active(val label: String, val expiresAtMillis: Long) : ClipboardState
}

/**
 * InMemorySecureClipboard
 *
 * Implements timed clipboard management and sensitive masking.
 * Usable across JVM unit tests and Android runtime.
 */
class InMemorySecureClipboard(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val onClearSystemClipboard: (() -> Unit)? = null
) : SecureClipboard {

    private val clipContent = AtomicReference<CharSequence?>(null)
    private var autoClearJob: Job? = null

    private val _clipboardState = MutableStateFlow<ClipboardState>(ClipboardState.Empty)
    override val clipboardState: StateFlow<ClipboardState> = _clipboardState.asStateFlow()

    override fun copySensitive(label: String, content: CharSequence, timeoutSeconds: Long) {
        autoClearJob?.cancel()
        clipContent.set(content)

        val expiresAt = System.currentTimeMillis() + (timeoutSeconds * 1000L)
        _clipboardState.value = ClipboardState.Active(label = label, expiresAtMillis = expiresAt)

        autoClearJob = scope.launch {
            delay(timeoutSeconds * 1000L)
            clear()
        }
    }

    override fun clear() {
        autoClearJob?.cancel()
        autoClearJob = null
        clipContent.set(null)
        onClearSystemClipboard?.invoke()
        _clipboardState.value = ClipboardState.Empty
    }

    override fun isCleared(): Boolean {
        return clipContent.get() == null
    }

    fun inspectCurrentContent(): CharSequence? = clipContent.get()
}
