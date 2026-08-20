package org.abap2ui5.mobileshell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * QR onboarding payloads (Phase 1). A scanned code is untrusted input from
 * whatever the camera happened to see, so every shape that is not a usable
 * endpoint must come back as null rather than as a half-parsed payload.
 */
class OnboardingTest {

    @Test
    fun `plain https url is taken as endpoint`() {
        val parsed = Onboarding.parseQrPayload("https://host/sap/bc/z2ui5?sap-client=100")
        assertEquals("https://host/sap/bc/z2ui5?sap-client=100", parsed?.url)
        assertNull(parsed?.msHost)
        assertNull(parsed?.msAppId)
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        assertEquals("https://host/z2ui5", Onboarding.parseQrPayload("  https://host/z2ui5 \n")?.url)
    }

    @Test
    fun `json payload carries mobile services coordinates`() {
        val parsed = Onboarding.parseQrPayload(
            """{"url":"https://host/z2ui5","msHost":"x.hana.ondemand.com","msAppId":"com.example.app"}""")
        assertEquals("https://host/z2ui5", parsed?.url)
        assertEquals("x.hana.ondemand.com", parsed?.msHost)
        assertEquals("com.example.app", parsed?.msAppId)
    }

    @Test
    fun `json payload without mobile services keys is valid`() {
        val parsed = Onboarding.parseQrPayload("""{"url":"https://host/z2ui5"}""")
        assertEquals("https://host/z2ui5", parsed?.url)
        assertNull(parsed?.msHost)
        assertNull(parsed?.msAppId)
    }

    @Test
    fun `blank mobile services values are dropped`() {
        val parsed = Onboarding.parseQrPayload(
            """{"url":"https://host/z2ui5","msHost":"  ","msAppId":""}""")
        assertNull(parsed?.msHost)
        assertNull(parsed?.msAppId)
    }

    @Test
    fun `json without url is rejected`() {
        assertNull(Onboarding.parseQrPayload("""{"msHost":"x.hana.ondemand.com"}"""))
    }

    @Test
    fun `malformed json is rejected`() {
        assertNull(Onboarding.parseQrPayload("""{"url":"https://host/z2ui5" """))
    }

    @Test
    fun `arbitrary qr content is rejected`() {
        assertNull(Onboarding.parseQrPayload("WIFI:S=guest;T=WPA;P=secret;;"))
        assertNull(Onboarding.parseQrPayload(""))
    }
}
