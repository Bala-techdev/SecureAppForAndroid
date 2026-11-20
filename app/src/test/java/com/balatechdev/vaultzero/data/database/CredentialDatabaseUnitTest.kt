package com.balatechdev.vaultzero.data.database

import com.balatechdev.vaultzero.data.model.CredentialEntity
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.UUID

class CredentialDatabaseUnitTest {

    private lateinit var database: EncryptedCredentialDatabase
    private lateinit var dao: CredentialDao

    @Before
    fun setUp() {
        database = InMemoryEncryptedCredentialDatabase()
        dao = database.credentialDao
    }

    private fun createDummyEntity(
        id: String = UUID.randomUUID().toString(),
        title: String = "GitHub Account",
        username: String = "octocat@github.com",
        categoryId: String = "cat_logins",
        isFavorite: Boolean = false
    ): CredentialEntity {
        return CredentialEntity(
            id = id,
            title = title,
            username = username,
            encryptedPassword = byteArrayOf(0x01, 0x02, 0x03, 0x04),
            encryptedNotes = byteArrayOf(0x05, 0x06),
            encryptedTotpSecret = byteArrayOf(0x07, 0x08),
            website = "https://github.com",
            categoryId = categoryId,
            isFavorite = isFavorite,
            createdDate = 1000L,
            updatedDate = 1000L
        )
    }

    @Test
    fun testInsertAndGetById_success() {
        val entity = createDummyEntity()
        dao.insert(entity)

        val retrieved = dao.getById(entity.id)
        assertNotNull(retrieved)
        assertEquals(entity.id, retrieved?.id)
        assertEquals("GitHub Account", retrieved?.title)
        assertEquals("octocat@github.com", retrieved?.username)
        assertArrayEquals(entity.encryptedPassword, retrieved?.encryptedPassword)
    }

    @Test
    fun testUpdate_modifiesExistingRecord() {
        val entity = createDummyEntity()
        dao.insert(entity)

        val updated = entity.copy(title = "GitHub Corporate", isFavorite = true, updatedDate = 2000L)
        val rows = dao.update(updated)
        assertEquals(1, rows)

        val retrieved = dao.getById(entity.id)
        assertEquals("GitHub Corporate", retrieved?.title)
        assertTrue(retrieved?.isFavorite == true)
        assertEquals(2000L, retrieved?.updatedDate)
    }

    @Test
    fun testDeleteById_removesRecord() {
        val entity = createDummyEntity()
        dao.insert(entity)
        assertEquals(1, dao.getCount())

        val deletedRows = dao.deleteById(entity.id)
        assertEquals(1, deletedRows)
        assertEquals(0, dao.getCount())
        assertNull(dao.getById(entity.id))
    }

    @Test
    fun testSearch_filtersByQueryAndCategory() {
        val github = createDummyEntity(title = "GitHub", username = "developer@work.com", categoryId = "cat_logins")
        val bank = createDummyEntity(title = "Chase Bank", username = "customer@home.com", categoryId = "cat_financial")
        val proton = createDummyEntity(title = "Proton Mail", username = "developer@pm.me", categoryId = "cat_logins")

        dao.insert(github)
        dao.insert(bank)
        dao.insert(proton)

        // Query "developer" with no category filter -> returns github and proton
        val devResults = dao.search(query = "developer", categoryId = "cat_all")
        assertEquals(2, devResults.size)
        assertTrue(devResults.any { it.title == "GitHub" })
        assertTrue(devResults.any { it.title == "Proton Mail" })

        // Query "developer" within "cat_financial" -> returns 0
        val financeDev = dao.search(query = "developer", categoryId = "cat_financial")
        assertEquals(0, financeDev.size)

        // Query "bank" in "cat_financial" -> returns 1
        val bankResults = dao.search(query = "bank", categoryId = "cat_financial")
        assertEquals(1, bankResults.size)
        assertEquals("Chase Bank", bankResults.first().title)
    }

    @Test
    fun testGetFavorites_returnsOnlyFavoriteRecords() {
        val fav1 = createDummyEntity(title = "Fav 1", isFavorite = true)
        val fav2 = createDummyEntity(title = "Fav 2", isFavorite = true)
        val normal = createDummyEntity(title = "Normal", isFavorite = false)

        dao.insert(fav1)
        dao.insert(fav2)
        dao.insert(normal)

        val favorites = dao.getFavorites()
        assertEquals(2, favorites.size)
        assertTrue(favorites.all { it.isFavorite })
    }

    @Test
    fun testCloseDatabase_clearsStateAndRejectsSubsequentOperations() {
        dao.insert(createDummyEntity())
        assertEquals(1, dao.getCount())

        database.close()
        assertFalse(database.isOpen())

        try {
            dao.getAll()
            fail("Database must reject queries after close")
        } catch (expected: IllegalStateException) {
            // Expected
        }
    }

    @Test
    fun testSQLCipherSchema_validatesPragmasAndTableStructure() {
        assertTrue(SQLCipherSchema.PRAGMA_STATEMENTS.any { it.contains("cipher_page_size = 4096") })
        assertTrue(SQLCipherSchema.PRAGMA_STATEMENTS.any { it.contains("kdf_iter = 256000") })
        assertTrue(SQLCipherSchema.PRAGMA_STATEMENTS.any { it.contains("HMAC_SHA512") })
        assertTrue(SQLCipherSchema.CREATE_TABLE_SQL.contains("encrypted_password BLOB NOT NULL"))
    }
}
