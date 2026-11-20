package com.balatechdev.vaultzero.data.database

import com.balatechdev.vaultzero.data.model.DocumentEntity
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * DocumentDao
 *
 * Data Access Object for encrypted document metadata.
 */
interface DocumentDao {
    fun insert(entity: DocumentEntity): Long
    fun update(entity: DocumentEntity): Int
    fun deleteById(id: String): Int
    fun getById(id: String): DocumentEntity?
    fun getAll(): List<DocumentEntity>
    fun search(query: String, categoryId: String? = null): List<DocumentEntity>
    fun getFavorites(): List<DocumentEntity>
    fun getCount(): Int
    fun clear(): Int
}

/**
 * EncryptedDocumentDatabase
 *
 * Interface for document database lifecycle.
 */
interface EncryptedDocumentDatabase : AutoCloseable {
    val documentDao: DocumentDao
    fun isOpen(): Boolean
}

/**
 * InMemoryEncryptedDocumentDatabase
 *
 * Thread-safe encrypted document database implementation.
 */
class InMemoryEncryptedDocumentDatabase : EncryptedDocumentDatabase {

    private val isOpen = AtomicBoolean(true)
    private val storage = ConcurrentHashMap<String, DocumentEntity>()

    override val documentDao: DocumentDao = object : DocumentDao {
        override fun insert(entity: DocumentEntity): Long {
            check(isOpen.get()) { "Database has been locked and closed" }
            storage[entity.id] = entity
            return 1L
        }

        override fun update(entity: DocumentEntity): Int {
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

        override fun getById(id: String): DocumentEntity? {
            check(isOpen.get()) { "Database has been locked and closed" }
            return storage[id]
        }

        override fun getAll(): List<DocumentEntity> {
            check(isOpen.get()) { "Database has been locked and closed" }
            return storage.values.sortedByDescending { it.createdDate }
        }

        override fun search(query: String, categoryId: String?): List<DocumentEntity> {
            check(isOpen.get()) { "Database has been locked and closed" }
            return storage.values.filter { item ->
                val matchesCategory = categoryId == null || categoryId == "cat_all" || item.categoryId == categoryId
                val matchesQuery = query.isBlank() ||
                        item.title.contains(query, ignoreCase = true) ||
                        item.fileName.contains(query, ignoreCase = true) ||
                        item.notes.contains(query, ignoreCase = true)
                matchesCategory && matchesQuery
            }.sortedByDescending { it.createdDate }
        }

        override fun getFavorites(): List<DocumentEntity> {
            check(isOpen.get()) { "Database has been locked and closed" }
            return storage.values.filter { it.isFavorite }.sortedByDescending { it.createdDate }
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
