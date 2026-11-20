package com.balatechdev.vaultzero.data.database

import com.balatechdev.vaultzero.data.model.CredentialEntity
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * EncryptedCredentialDatabase
 *
 * Encapsulates the encrypted storage engine lifecycle.
 * Ensures the database connection and in-memory caches are closed/zeroized upon vault lock.
 */
interface EncryptedCredentialDatabase : AutoCloseable {
    val credentialDao: CredentialDao
    fun isOpen(): Boolean
}

/**
 * InMemoryEncryptedCredentialDatabase
 *
 * Thread-safe encrypted database implementation.
 * Stores raw [CredentialEntity] instances with encrypted byte blobs, ensuring no plaintext
 * passwords or notes are held in the database layer.
 */
class InMemoryEncryptedCredentialDatabase : EncryptedCredentialDatabase {

    private val isOpen = AtomicBoolean(true)
    private val storage = ConcurrentHashMap<String, CredentialEntity>()

    override val credentialDao: CredentialDao = object : CredentialDao {
        override fun insert(entity: CredentialEntity): Long {
            check(isOpen.get()) { "Database has been locked and closed" }
            storage[entity.id] = entity
            return 1L
        }

        override fun update(entity: CredentialEntity): Int {
            check(isOpen.get()) { "Database has been locked and closed" }
            return if (storage.containsKey(entity.id)) {
                storage[entity.id] = entity
                1
            } else {
                0
            }
        }

        override fun deleteById(id: String): Int {
            check(isOpen.get()) { "Database has been locked and closed" }
            return if (storage.remove(id) != null) 1 else 0
        }

        override fun getById(id: String): CredentialEntity? {
            check(isOpen.get()) { "Database has been locked and closed" }
            return storage[id]
        }

        override fun getAll(): List<CredentialEntity> {
            check(isOpen.get()) { "Database has been locked and closed" }
            return storage.values.sortedByDescending { it.updatedDate }
        }

        override fun search(query: String, categoryId: String?): List<CredentialEntity> {
            check(isOpen.get()) { "Database has been locked and closed" }
            return storage.values.filter { item ->
                val matchesCategory = categoryId == null || categoryId == "cat_all" || item.categoryId == categoryId
                val matchesQuery = query.isBlank() ||
                        item.title.contains(query, ignoreCase = true) ||
                        item.username.contains(query, ignoreCase = true) ||
                        item.website.contains(query, ignoreCase = true)
                matchesCategory && matchesQuery
            }.sortedByDescending { it.updatedDate }
        }

        override fun getFavorites(): List<CredentialEntity> {
            check(isOpen.get()) { "Database has been locked and closed" }
            return storage.values.filter { it.isFavorite }.sortedByDescending { it.updatedDate }
        }

        override fun getCount(): Int {
            check(isOpen.get()) { "Database has been locked and closed" }
            return storage.size
        }

        override fun clear(): Int {
            check(isOpen.get()) { "Database has been locked and closed" }
            val count = storage.size
            storage.clear()
            return count
        }
    }

    override fun isOpen(): Boolean = isOpen.get()

    override fun close() {
        if (isOpen.compareAndSet(true, false)) {
            storage.clear()
        }
    }
}
