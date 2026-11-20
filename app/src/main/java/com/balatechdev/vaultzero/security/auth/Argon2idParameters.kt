package com.balatechdev.vaultzero.security.auth

/**
 * Argon2idParameters
 *
 * Parameters for the memory-hard Argon2id key derivation function.
 * Default settings match the VaultZero SECURITY_DESIGN.md specification:
 * 64 MiB RAM, 3 iterations, 4 parallelism lanes, 32-byte salt, 32-byte key output.
 */
data class Argon2idParameters(
    val memoryKiB: Int = 65536,    // 64 MiB
    val iterations: Int = 3,       // 3 passes
    val parallelism: Int = 4,      // 4 lanes
    val saltLengthBytes: Int = 32, // 256-bit salt
    val outputLengthBytes: Int = 32 // 256-bit derived key
) {
    companion object {
        /**
         * Production recommended parameters (64MB, t=3, p=4)
         */
        val Default = Argon2idParameters()

        /**
         * Fast parameters for unit tests to avoid slowing down CI/test runs
         */
        val Test = Argon2idParameters(
            memoryKiB = 1024,      // 1 MiB
            iterations = 2,
            parallelism = 1,
            saltLengthBytes = 32,
            outputLengthBytes = 32
        )
    }
}
