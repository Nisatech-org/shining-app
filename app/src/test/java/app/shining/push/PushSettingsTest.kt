package app.shining.push

import app.shining.testing.FakeKeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushSettingsTest {

    private val store = FakeKeyValueStore()
    private val settings = PushSettings(store)

    @Test
    fun `every vendor is on by default, new ones too`() {
        assertTrue(settings.isVendorEnabled("self"))
        assertTrue(settings.isVendorEnabled("some-new-vendor"))
    }

    @Test
    fun `a vendor turned off stays off, and back on clears the setting`() {
        settings.setVendorEnabled("kaleh", false)
        settings.setVendorEnabled("kalana", false)
        assertFalse(PushSettings(store).isVendorEnabled("kaleh"))
        assertTrue(settings.isVendorEnabled("self"))

        settings.setVendorEnabled("kaleh", true)
        settings.setVendorEnabled("kalana", true)
        assertTrue(settings.isVendorEnabled("kaleh"))
        assertEquals(emptyMap<String, String>(), store.values)
    }

    @Test
    fun `admin alerts are off until turned on`() {
        assertFalse(settings.adminAlerts)
        settings.adminAlerts = true
        assertTrue(PushSettings(store).adminAlerts)
        settings.adminAlerts = false
        assertFalse(settings.adminAlerts)
    }
}
