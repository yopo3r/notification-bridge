/**
 * Small, versioned text format for user supplied Material 3 color themes.
 * Unknown keys and incomplete files are rejected as a whole, so importing a future or damaged
 * file can never replace the currently active theme with a partial palette.
 */
package app.notificationbridge.ui.theme

import androidx.compose.ui.graphics.Color

data class CustomTheme(
    val name: String,
    val light: CustomThemePalette,
    val dark: CustomThemePalette
)

data class CustomThemePalette(
    val foreground: Color,
    val background: Color,
    val highlight: Color,
    val highlightForeground: Color,
    val secondary: Color,
    val surface: Color,
    val error: Color
)

object CustomThemeParser {
    private val paletteKeys = listOf(
        "foreground", "background", "highlight", "highlight-foreground", "secondary",
        "surface", "error"
    )
    private val validKeys = setOf("version", "name") +
        paletteKeys.flatMap { listOf("light.$it", "dark.$it") }.toSet()

    fun parse(source: String): CustomTheme {
        require(source.length <= MAX_FILE_CHARS) { "Theme file is too large (maximum 16 KB)" }
        val values = linkedMapOf<String, String>()
        source.lineSequence().forEachIndexed { index, rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith('#')) return@forEachIndexed
            val separator = line.indexOf(':')
            require(separator > 0) { "Line ${index + 1}: expected key: value" }
            val key = line.substring(0, separator).trim().lowercase()
            val value = line.substring(separator + 1).trim()
            require(key in validKeys) { "Line ${index + 1}: unknown key '$key'" }
            require(values.put(key, value) == null) { "Line ${index + 1}: duplicate key '$key'" }
        }
        require(values["version"] == "1") { "Missing or unsupported theme version" }
        val name = values["name"]?.takeIf { it.isNotBlank() && it.length <= 40 }
            ?: throw IllegalArgumentException("Theme name is missing or too long")

        fun palette(mode: String): CustomThemePalette {
            fun color(key: String): Color {
                val raw = values["$mode.$key"]
                    ?: throw IllegalArgumentException("Missing $mode.$key")
                require(HEX_COLOR.matches(raw)) {
                    "Invalid color for $mode.$key: use #RRGGBB or #AARRGGBB"
                }
                val parsed = raw.substring(1).toLong(16)
                return Color(if (raw.length == 7) 0xFF000000L or parsed else parsed)
            }
            return CustomThemePalette(
                foreground = color("foreground"),
                background = color("background"),
                highlight = color("highlight"),
                highlightForeground = color("highlight-foreground"),
                secondary = color("secondary"),
                surface = color("surface"),
                error = color("error")
            )
        }
        return CustomTheme(name, palette("light"), palette("dark"))
    }

    private const val MAX_FILE_CHARS = 16 * 1024
    private val HEX_COLOR = Regex("#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{8})")
}
