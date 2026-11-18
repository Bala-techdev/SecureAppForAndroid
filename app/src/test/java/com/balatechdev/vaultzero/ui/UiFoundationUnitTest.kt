package com.balatechdev.vaultzero.ui

import com.balatechdev.vaultzero.data.model.DefaultCategories
import com.balatechdev.vaultzero.data.model.PasswordStrength
import com.balatechdev.vaultzero.data.model.SampleData
import com.balatechdev.vaultzero.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UiFoundationUnitTest {

    @Test
    fun testSampleData_credentialsArePopulatedAndValid() {
        val credentials = SampleData.sampleCredentials
        assertTrue("Credentials list should not be empty", credentials.isNotEmpty())

        val first = credentials.first()
        assertEquals("Proton Mail", first.title)
        assertTrue(first.username.contains("@"))
        assertEquals(PasswordStrength.STRONG, first.passwordStrength)
        assertTrue(first.isFavorite)
    }

    @Test
    fun testSampleData_documentsArePopulatedAndValid() {
        val documents = SampleData.sampleDocuments
        assertTrue("Documents list should not be empty", documents.isNotEmpty())

        val passport = documents.first()
        assertEquals("Passport Scan (High-Res)", passport.title)
        assertEquals("PDF", passport.fileExtension)
        assertTrue(passport.isFavorite)
    }

    @Test
    fun testDefaultCategories_coverage() {
        val categories = DefaultCategories
        assertEquals(5, categories.size)
        assertTrue(categories.any { it.id == "cat_all" })
        assertTrue(categories.any { it.id == "cat_logins" })
        assertTrue(categories.any { it.id == "cat_financial" })
        assertTrue(categories.any { it.id == "cat_identity" })
        assertTrue(categories.any { it.id == "cat_notes" })
    }

    @Test
    fun testScreenRoutes_dynamicRouteGeneration() {
        val editRoute = Screen.EditCredential.createRoute("cred_99")
        assertEquals("edit_credential/cred_99", editRoute)

        val detailsRoute = Screen.CredentialDetails.createRoute("cred_123")
        assertEquals("credential_details/cred_123", detailsRoute)

        val previewRoute = Screen.DocumentPreview.createRoute("doc_456")
        assertEquals("document_preview/doc_456", previewRoute)
    }

    @Test
    fun testSecurityAuditStatus_optimalDefaults() {
        val status = SampleData.securityAudit
        assertTrue("Security health score should be high", status.healthScore >= 90)
        assertTrue(status.isOfflineEnforced)
        assertTrue(status.isBiometricsActive)
        assertTrue(status.isScreenshotProtected)
    }
}
