package com.balatechdev.vaultzero.foundation

import com.balatechdev.vaultzero.data.model.VaultItemType
import com.balatechdev.vaultzero.data.repository.VaultStatusRepositoryImpl
import com.balatechdev.vaultzero.security.MemoryZeroizer
import com.balatechdev.vaultzero.security.VaultLockState
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class FoundationUnitTest {

    @Test
    fun testMemoryZeroizer_zeroizesByteArray() {
        val sensitiveData = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)
        MemoryZeroizer.zeroize(sensitiveData)
        val expected = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00)
        assertArrayEquals(expected, sensitiveData)
    }

    @Test
    fun testMemoryZeroizer_zeroizesCharArray() {
        val masterPassword = charArrayOf('S', 'e', 'c', 'u', 'r', 'e', 'P', '@', 's', 's')
        MemoryZeroizer.zeroize(masterPassword)
        val expected = charArrayOf('\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000')
        assertArrayEquals(expected, masterPassword)
    }

    @Test
    fun testMemoryZeroizer_zeroizesByteBuffer() {
        val buffer = ByteBuffer.allocate(4)
        buffer.put(byteArrayOf(10, 20, 30, 40))
        buffer.flip()

        MemoryZeroizer.zeroize(buffer)

        buffer.flip()
        while (buffer.hasRemaining()) {
            assertEquals(0.toByte(), buffer.get())
        }
    }

    @Test
    fun testVaultStatusRepository_initialStateAndTransitions() {
        val repo = VaultStatusRepositoryImpl()
        assertEquals(VaultLockState.Locked, repo.lockState.value)

        repo.unlockVault()
        assertEquals(VaultLockState.Unlocked, repo.lockState.value)

        repo.lockVault()
        assertEquals(VaultLockState.Locked, repo.lockState.value)
    }

    @Test
    fun testVaultItemType_enumCompleteness() {
        val types = VaultItemType.values().map { it.name }
        assertTrue(types.contains("PASSWORD"))
        assertTrue(types.contains("TOTP"))
        assertTrue(types.contains("DOCUMENT"))
        assertTrue(types.contains("NOTE"))
    }
}
