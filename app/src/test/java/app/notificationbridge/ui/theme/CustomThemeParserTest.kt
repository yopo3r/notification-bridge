package app.notificationbridge.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CustomThemeParserTest {

    @Test
    fun parsesCompleteTokyoNightStyleTheme() {
        val theme = CustomThemeParser.parse(THEME_FILE)

        assertEquals("Tokyo Night", theme.name)
    }

    @Test
    fun rejectsUnknownKeysWithoutProducingPartialTheme() {
        val unsupported = THEME_FILE.replace("name: Tokyo Night", "name: Tokyo Night\naccent: #FF00FF")

        assertThrows(IllegalArgumentException::class.java) {
            CustomThemeParser.parse(unsupported)
        }
    }

    @Test
    fun rejectsIncompleteTheme() {
        val incomplete = THEME_FILE.replace("dark.error: #F7768E", "")

        assertThrows(IllegalArgumentException::class.java) {
            CustomThemeParser.parse(incomplete)
        }
    }

    private companion object {
        val THEME_FILE = """
            version: 1
            name: Tokyo Night
            light.foreground: #343B58
            light.background: #D5D6DB
            light.highlight: #2E7DE9
            light.highlight-foreground: #FFFFFF
            light.secondary: #9854F1
            light.surface: #E1E2E7
            light.error: #F52A65
            dark.foreground: #C0CAF5
            dark.background: #1A1B26
            dark.highlight: #7AA2F7
            dark.highlight-foreground: #1A1B26
            dark.secondary: #BB9AF7
            dark.surface: #24283B
            dark.error: #F7768E
        """.trimIndent()
    }
}
