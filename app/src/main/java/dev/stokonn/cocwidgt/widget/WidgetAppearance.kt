package dev.stokonn.cocwidgt.widget

import android.content.SharedPreferences

/**
 * Background transparency of the widget surface, 0–100 %.
 *
 * 0 % is the default: the fully opaque warm dark brown surface of the visual reference.
 * 100 % makes only the root background fully transparent so the launcher wallpaper shows
 * through. The value affects the root background alone — the header, the category chips,
 * the green progress rows, all text and all icons keep their own fully opaque colours, so
 * contrast never degrades and the warm brown identity stays intact at the default.
 *
 * Pure and unit-tested: no Android types, no Compose types.
 */
object WidgetAppearance {
    const val MIN_TRANSPARENCY = 0
    const val MAX_TRANSPARENCY = 100

    /** Opaque warm dark brown surface of the reference design (ARGB base colour). */
    private const val BASE_RGB = 0x00281D17
    private const val OPAQUE_ALPHA = 0xFF

    const val DEFAULT_TRANSPARENCY = MIN_TRANSPARENCY

    fun clamp(percent: Int): Int = percent.coerceIn(MIN_TRANSPARENCY, MAX_TRANSPARENCY)

    /**
     * ARGB colour of the widget root background for the given transparency:
     * 0 % → `0xFF281D17` (opaque warm brown), 100 % → `0x00281D17` (fully transparent),
     * intermediate values scale the alpha channel only — the RGB identity never changes.
     */
    fun backgroundArgb(transparency: Int): Int {
        val opaqueFraction = MAX_TRANSPARENCY - clamp(transparency)
        val alpha = (opaqueFraction * OPAQUE_ALPHA + MAX_TRANSPARENCY / 2) / MAX_TRANSPARENCY
        return (alpha shl 24) or BASE_RGB
    }
}

/**
 * Pure codec for the widget appearance settings stored in the `cocwidgt_widget_settings`
 * preferences: a global default transparency plus an optional per-widget override keyed by
 * [AppWidgetId][androidx.glance.appwidget.AppWidgetId]. Values written by hand or by an
 * older release are clamped on read, so a corrupt entry can never crash a render.
 */
object WidgetSettingsCodec {
    const val PREFS = "cocwidgt_widget_settings"

    /** Global default applied to every widget without its own override. */
    const val GLOBAL_TRANSPARENCY_KEY = "background_transparency"
    private const val WIDGET_KEY_PREFIX = "background_transparency_"

    fun widgetKey(appWidgetId: Int): String = "$WIDGET_KEY_PREFIX$appWidgetId"

    /**
     * Transparency for one widget: its own override first, then the global default, then
     * the opaque default. [appWidgetId] null (or an invalid id) reads the global default.
     */
    fun transparencyFor(values: Map<String, Any?>, appWidgetId: Int?): Int {
        val override = appWidgetId?.takeIf { it >= 0 }?.let { values[widgetKey(it)] }
        val raw = (override as? Number) ?: (values[GLOBAL_TRANSPARENCY_KEY] as? Number)
            ?: return WidgetAppearance.DEFAULT_TRANSPARENCY
        return WidgetAppearance.clamp(raw.toInt())
    }

    /** The global default as stored (clamped). */
    fun globalDefault(values: Map<String, Any?>): Int = transparencyFor(values, appWidgetId = null)

    /**
     * The preference mutations for setting the transparency of [appWidgetId]:
     * null writes the global default **and clears every per-widget override** (so "all
     * widgets" really applies to all of them); a concrete id only writes that widget's
     * override. Keys map to the value to store; [null] values mark keys to remove.
     */
    fun applyTransparency(values: Map<String, Any?>, appWidgetId: Int?, value: Int): Map<String, Any?> {
        val clamped = WidgetAppearance.clamp(value)
        return if (appWidgetId == null) {
            buildMap {
                putAll(values.filterKeys { !it.startsWith(WIDGET_KEY_PREFIX) })
                put(GLOBAL_TRANSPARENCY_KEY, clamped)
                // Mark existing overrides for removal.
                values.keys.filter { it.startsWith(WIDGET_KEY_PREFIX) }.forEach { put(it, null) }
            }
        } else {
            values + (widgetKey(appWidgetId) to clamped)
        }
    }
}

/**
 * Thin [SharedPreferences] wrapper around [WidgetSettingsCodec] for the widget renderer and
 * the settings screen. Only the pure codec is unit-tested; this class just moves values
 * between preferences and the codec.
 */
class WidgetSettingsStore(private val prefs: SharedPreferences) {
    fun transparency(appWidgetId: Int?): Int =
        WidgetSettingsCodec.transparencyFor(prefs.all, appWidgetId)

    fun setTransparency(appWidgetId: Int?, value: Int) {
        val mutations = WidgetSettingsCodec.applyTransparency(prefs.all, appWidgetId, value)
        val editor = prefs.edit()
        mutations.forEach { (key, newValue) ->
            if (newValue == null) editor.remove(key)
            else editor.putInt(key, (newValue as Number).toInt())
        }
        editor.apply()
    }
}
