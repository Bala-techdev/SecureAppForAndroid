package com.balatechdev.vaultzero.security.crypto

import java.security.GeneralSecurityException

/**
 * Base exception for all cryptographic operations in VaultZero.
 */
open class CryptoException(message: String, cause: Throwable? = null) : GeneralSecurityException(message, cause)

/**
 * Thrown when an AEAD authentication tag fails validation, indicating wrong key,
 * tampered ciphertext, wrong nonce, or corrupted associated data.
 */
class CryptoAuthException(
    message: String = "Cryptographic authentication failed: tag mismatch, corrupted ciphertext, or invalid key",
    cause: Throwable? = null
) : CryptoException(message, cause)

/**
 * Thrown when cryptographic key parameters or formats are invalid.
 */
class InvalidKeyException(
    message: String,
    cause: Throwable? = null
) : CryptoException(message, cause)

/**
 * Thrown when payload byte representation is truncated or malformed.
 */
class MalformedPayloadException(
    message: String,
    cause: Throwable? = null
) : CryptoException(message, cause)
