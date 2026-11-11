package dev.stokonn.cocwidgt.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the configurable background transparency (0–100 %) of the widget
 * surface: endpoints, clamping, per-widget overrides, "global clears overrides" and corrupt
 * preference values that must never crash a render.
 */
class WidgetAppearanceTest {

    // ---- background colour ----------------------------------------------------------

    @Test
    fun `zero transparency is the opaque warm brown of the reference design`() {
        assertEquals(0xFF281D17.toInt(), WidgetAppearance.backgroundArgb(0))
    }

    @Test
    fun `hundred transparency is a fully transparent root background`() {
        assertEquals(0x00281D17, WidgetAppearance.backgroundArgb(100))
    }

    @Test
    fun `half transparency halves the alpha only and keeps the brown rgb identity`() {
        val half = WidgetAppearance.backgroundArgb(50)
        assertEquals(128, half ushr 24)
        assertEquals(0x00281D17, half and 0x00FFFFFF)
    }

    @Test
    fun `transparency outside the range is clamped not rejected`() {
        assertEquals(WidgetAppearance.backgroundArgb(0), WidgetAppearance.backgroundArgb(-40))
        assertEquals(WidgetAppearance.backgroundArgb(100), WidgetAppearance.backgroundArgb(999))
        assertEquals(0, WidgetAppearance.clamp(-1))
        assertEquals(100, WidgetAppearance.clamp(101))
    }

    @Test
    fun `transparency never touches the rgb components used for text and bars`() {
        for (t in listOf(0, 1, 25, 50, 75, 100)) {
            val argb = WidgetAppearance.backgroundArgb(t)
            assertEquals(0x28, (argb shr 16) and 0xFF)
            assertEquals(0x1D, (argb shr 8) and 0xFF)
            assertEquals(0x17, argb and 0xFF)
        }
    }

    // ---- per-widget settings codec --------------------------------------------------

    @Test
    fun `read defaults to the opaque default when nothing is stored`() {
        assertEquals(WidgetAppearance.DEFAULT_TRANSPARENCY, WidgetSettingsCodec.transparencyFor(emptyMap(), 7))
        assertEquals(WidgetAppearance.DEFAULT_TRANSPARENCY, WidgetSettingsCodec.transparencyFor(emptyMap(), null))
    }

    @Test
    fun `global setting applies to every widget without its own override`() {
        val values = mapOf<String, Any?>(WidgetSettingsCodec.GLOBAL_TRANSPARENCY_KEY to 60)
        assertEquals(60, WidgetSettingsCodec.transparencyFor(values, 1))
        assertEquals(60, WidgetSettingsCodec.transparencyFor(values, 999))
        assertEquals(60, WidgetSettingsCodec.globalDefault(values))
    }

    @Test
    fun `per widget override wins over the global default`() {
        val values = mapOf<String, Any?>(
            WidgetSettingsCodec.GLOBAL_TRANSPARENCY_KEY to 60,
            WidgetSettingsCodec.widgetKey(7) to 20,
        )
        assertEquals(20, WidgetSettingsCodec.transparencyFor(values, 7))
        assertEquals(60, WidgetSettingsCodec.transparencyFor(values, 8))
        assertEquals(60, WidgetSettingsCodec.transparencyFor(values, null))
    }

    @Test
    fun `setting the global default clears every per widget override`() {
        val values = mapOf<String, Any?>(
            WidgetSettingsCodec.GLOBAL_TRANSPARENCY_KEY to 10,
            WidgetSettingsCodec.widgetKey(7) to 20,
            WidgetSettingsCodec.widgetKey(8) to 30,
            "unrelated_key" to "kept",
        )
        val updated = WidgetSettingsCodec.applyTransparency(values, appWidgetId = null, value = 45)

        assertEquals(45, WidgetSettingsCodec.transparencyFor(updated, 7))
        assertEquals(45, WidgetSettingsCodec.transparencyFor(updated, 8))
        assertEquals("kept", updated["unrelated_key"])
        // Overrides are marked for removal, not silently left behind.
        assertEquals(null, updated[WidgetSettingsCodec.widgetKey(7)])
        assertEquals(null, updated[WidgetSettingsCodec.widgetKey(8)])
    }

    @Test
    fun `setting one widget only writes that widget's override`() {
        val values = mapOf<String, Any?>(WidgetSettingsCodec.GLOBAL_TRANSPARENCY_KEY to 10)
        val updated = WidgetSettingsCodec.applyTransparency(values, appWidgetId = 7, value = 80)

        assertEquals(80, WidgetSettingsCodec.transparencyFor(updated, 7))
        assertEquals(10, WidgetSettingsCodec.transparencyFor(updated, 8))
        assertEquals(10, WidgetSettingsCodec.globalDefault(updated))
    }

    @Test
    fun `stored transparency values are clamped on read`() {
        val values = mapOf<String, Any?>(WidgetSettingsCodec.GLOBAL_TRANSPARENCY_KEY to 4000)
        assertEquals(100, WidgetSettingsCodec.transparencyFor(values, 1))

        val negative = mapOf<String, Any?>(WidgetSettingsCodec.widgetKey(3) to -12)
        assertEquals(0, WidgetSettingsCodec.transparencyFor(negative, 3))
    }

    @Test
    fun `corrupt and wrong typed values fall back instead of crashing`() {
        // Not a number at all (e.g. a hand-edited preference or a leftover string).
        assertEquals(
            WidgetAppearance.DEFAULT_TRANSPARENCY,
            WidgetSettingsCodec.transparencyFor(mapOf(WidgetSettingsCodec.GLOBAL_TRANSPARENCY_KEY to "yes"), 1),
        )
        // Wrong type for the override must not throw; the global default still applies.
        val values = mapOf<String, Any?>(
            WidgetSettingsCodec.GLOBAL_TRANSPARENCY_KEY to 30,
            WidgetSettingsCodec.widgetKey(7) to "opaque",
        )
        assertEquals(30, WidgetSettingsCodec.transparencyFor(values, 7))
        // Unknown widget ids and invalid (negative) ids read the global default.
        assertEquals(30, WidgetSettingsCodec.transparencyFor(values, -5))
    }

    @Test
    fun `apply clamps the written value`() {
        val updated = WidgetSettingsCodec.applyTransparency(emptyMap(), appWidgetId = 7, value = 5000)
        assertEquals(100, WidgetSettingsCodec.transparencyFor(updated, 7))

        val clampedDown = WidgetSettingsCodec.applyTransparency(emptyMap(), appWidgetId = 7, value = -3)
        assertEquals(0, WidgetSettingsCodec.transparencyFor(clampedDown, 7))
    }

    @Test
    fun `codec keeps only what it understands when reading legacy maps`() {
        // An empty/legacy map yields the opaque default rather than a crash.
        assertEquals(WidgetAppearance.DEFAULT_TRANSPARENCY, WidgetSettingsCodec.globalDefault(emptyMap()))
        assertFalse(WidgetSettingsCodec.applyTransparency(emptyMap(), null, 0).containsKey("nope"))
        assertTrue(
            WidgetSettingsCodec.applyTransparency(emptyMap(), null, 0)
                .containsKey(WidgetSettingsCodec.GLOBAL_TRANSPARENCY_KEY),
        )
    }
}
