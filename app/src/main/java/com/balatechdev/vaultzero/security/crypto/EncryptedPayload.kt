package com.balatechdev.vaultzero.security.crypto

import java.nio.ByteBuffer

/**
 * EncryptedPayload
 *
 * Represents an authenticated ciphertext produced by AES-256-GCM.
 * Conforms to NIST SP 800-38D:
 * - 96-bit (12-byte) unique nonce (IV)
 * - Variable-length ciphertext
 * - 128-bit (16-byte) GCM authentication tag appended at the end
 */
class EncryptedPayload(
    val nonce: ByteArray,
    val ciphertextAndTag: ByteArray
) {
    init {
        require(nonce.size == NONCE_LENGTH_BYTES) {
            "Nonce must be exactly $NONCE_LENGTH_BYTES bytes (96 bits), got ${nonce.size}"
        }
        require(ciphertextAndTag.size >= TAG_LENGTH_BYTES) {
            "Ciphertext and tag must be at least $TAG_LENGTH_BYTES bytes (128 bits tag), got ${ciphertextAndTag.size}"
        }
    }

    /**
     * Pure ciphertext without the 16-byte authentication tag.
     */
    val ciphertext: ByteArray
        get() = ciphertextAndTag.copyOfRange(0, ciphertextAndTag.size - TAG_LENGTH_BYTES)

    /**
     * The 16-byte GCM authentication tag.
     */
    val tag: ByteArray
        get() = ciphertextAndTag.copyOfRange(ciphertextAndTag.size - TAG_LENGTH_BYTES, ciphertextAndTag.size)

    /**
     * Serializes this payload into a single byte array:
     * [12 bytes Nonce] || [Ciphertext] || [16 bytes Tag]
     */
    fun toBytes(): ByteArray {
        val totalSize = NONCE_LENGTH_BYTES + ciphertextAndTag.size
        val buffer = ByteBuffer.allocate(totalSize)
        buffer.put(nonce)
        buffer.put(ciphertextAndTag)
        return buffer.array()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EncryptedPayload
        if (!nonce.contentEquals(other.nonce)) return false
        if (!ciphertextAndTag.contentEquals(other.ciphertextAndTag)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = nonce.contentHashCode()
        result = 31 * result + ciphertextAndTag.contentHashCode()
        return result
    }

    companion object {
        const val NONCE_LENGTH_BYTES = 12 // 96 bits
        const val TAG_LENGTH_BYTES = 16   // 128 bits

        /**
         * Parses a serialized byte array [12-byte nonce || ciphertext || 16-byte tag].
         */
        fun fromBytes(bytes: ByteArray): EncryptedPayload {
            if (bytes.size < NONCE_LENGTH_BYTES + TAG_LENGTH_BYTES) {
                throw MalformedPayloadException(
                    "Payload must be at least ${NONCE_LENGTH_BYTES + TAG_LENGTH_BYTES} bytes, got ${bytes.size}"
                )
            }
            val nonce = bytes.copyOfRange(0, NONCE_LENGTH_BYTES)
            val ciphertextAndTag = bytes.copyOfRange(NONCE_LENGTH_BYTES, bytes.size)
            return EncryptedPayload(nonce, ciphertextAndTag)
        }

        /**
         * Reconstructs an [EncryptedPayload] from separate components.
         */
        fun fromComponents(nonce: ByteArray, ciphertext: ByteArray, tag: ByteArray): EncryptedPayload {
            require(tag.size == TAG_LENGTH_BYTES) { "Tag must be $TAG_LENGTH_BYTES bytes" }
            val combined = ByteArray(ciphertext.size + tag.size)
            System.arraycopy(ciphertext, 0, combined, 0, ciphertext.size)
            System.arraycopy(tag, 0, combined, ciphertext.size, tag.size)
            return EncryptedPayload(nonce, combined)
        }
    }
}
