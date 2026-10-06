package app.notificationbridge.config

import app.notificationbridge.model.BridgeSettings
import app.notificationbridge.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigFileTest {

    private val settings = BridgeSettings(
        selectedAddress = "aa:bb:cc:dd:ee:ff",
        selectedName = "Alcatel 3080A",
        bridgeEnabled = true,
        ignoreSilent = false,
        ignoreOngoing = true,
        ignoreUpdates = false,
        notifyOnCalls = true,
        themeMode = ThemeMode.DARK,
        dumbphoneMode = true,
        maxTextChars = 1500,
        batchingEnabled = true,
        batchingCooldownSeconds = 30,
        allowedPackages = setOf("com.whatsapp", "com.google.android.gm")
    )

    private val themeSource = """
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

    private fun export(
        s: BridgeSettings = settings,
        receiver: ReceiverExport = ReceiverExport.NAME_ONLY,
        language: String = "fr"
    ) = ConfigFile.export(s, language, receiver, "0.13.1")

    @Test
    fun `round trips every exported setting`() {
        val c = ConfigFile.parse(export(receiver = ReceiverExport.NAME_AND_ADDRESS))
        assertEquals(setOf("com.whatsapp", "com.google.android.gm"), c.allowedPackages)
        assertFalse(c.ignoreSilent)
        assertTrue(c.ignoreOngoing)
        assertFalse(c.ignoreUpdates)
        assertTrue(c.notifyOnCalls)
        assertTrue(c.batchingEnabled)
        assertEquals(30, c.batchingCooldownSeconds)
        assertEquals(1500, c.maxTextChars)
        assertEquals("Alcatel 3080A", c.receiverName)
        assertEquals("AA:BB:CC:DD:EE:FF", c.receiverAddress)
        assertEquals(ThemeMode.DARK, c.themeMode)
        assertEquals("fr", c.language)
        assertTrue(c.dumbphoneMode)
        assertNull(c.customThemeSource)
    }

    @Test
    fun `the default export leaves out the bluetooth address`() {
        val text = export()
        assertFalse(text.contains("AA:BB:CC:DD:EE:FF", ignoreCase = true))
        assertTrue(text.contains("receiver.name: Alcatel 3080A"))
        val c = ConfigFile.parse(text)
        assertEquals("Alcatel 3080A", c.receiverName)
        assertNull(c.receiverAddress)
    }

    @Test
    fun `receiver can be left out entirely`() {
        val text = export(receiver = ReceiverExport.NONE)
        assertFalse(text.contains("Alcatel"))
        assertFalse(text.contains("receiver.address"))
        val c = ConfigFile.parse(text)
        assertNull(c.receiverName)
        assertNull(c.receiverAddress)
    }

    @Test
    fun `warns in the file when the address is included`() {
        assertTrue(export(receiver = ReceiverExport.NAME_AND_ADDRESS).contains("Do not post it publicly"))
        assertFalse(export().contains("Do not post it publicly"))
    }

    @Test
    fun `no receiver selected still exports and imports`() {
        val none = settings.copy(selectedAddress = null, selectedName = null)
        for (mode in ReceiverExport.values()) {
            val c = ConfigFile.parse(export(none, mode))
            assertNull(c.receiverName)
            assertNull(c.receiverAddress)
        }
    }

    @Test
    fun `custom theme is carried along and re-validated`() {
        val c = ConfigFile.parse(export(settings.copy(customThemeSource = themeSource)))
        val theme = c.customThemeSource
        assertNotNull(theme)
        assertTrue(theme!!.contains("name: Tokyo Night"))
        assertTrue(theme.contains("dark.error: #F7768E"))
        // The exported text is itself a valid config again.
        val again = ConfigFile.parse(export(settings.copy(customThemeSource = c.customThemeSource)))
        assertEquals(c.customThemeSource, again.customThemeSource)
    }

    @Test
    fun `an invalid stored theme is omitted instead of producing an unimportable file`() {
        val c = ConfigFile.parse(export(settings.copy(customThemeSource = "garbage")))
        assertNull(c.customThemeSource)
    }

    @Test
    fun `an incomplete custom theme in a file rejects the whole file`() {
        val text = export(settings.copy(customThemeSource = themeSource))
            .lines().filterNot { it.startsWith("custom-theme.dark.error") }.joinToString("\n")
        assertThrows(IllegalArgumentException::class.java) { ConfigFile.parse(text) }
    }

    @Test
    fun `invalid package names are not exported`() {
        val bad = settings.copy(allowedPackages = setOf("com.ok.app", "not a package", "x"))
        assertEquals(setOf("com.ok.app"), ConfigFile.parse(export(bad)).allowedPackages)
    }

    @Test
    fun `an empty allowed list is valid`() {
        assertTrue(ConfigFile.parse(export(settings.copy(allowedPackages = emptySet()))).allowedPackages.isEmpty())
    }

    @Test
    fun `an unknown language is exported as system`() {
        assertEquals("system", ConfigFile.parse(export(language = "xx")).language)
        assertEquals("system", ConfigFile.languageValue(null))
        assertEquals("system", ConfigFile.languageValue("ja"))
        assertEquals("de", ConfigFile.languageValue("de"))
    }

    @Test
    fun `the bridge switch is never part of the file`() {
        val on = export(settings.copy(bridgeEnabled = true))
        val off = export(settings.copy(bridgeEnabled = false))
        assertEquals(off, on)
        assertFalse(on.contains("bridge"))
    }

    @Test
    fun `comments blank lines and a byte order mark are ignored`() {
        val text = "﻿# hello\n\n" + export() + "\n# trailing\n"
        assertEquals(30, ConfigFile.parse(text).batchingCooldownSeconds)
    }

    @Test
    fun `keys are case insensitive and values are trimmed`() {
        val text = export().replace("batching.interval-seconds: 30", "Batching.Interval-Seconds:   45  ")
        assertEquals(45, ConfigFile.parse(text).batchingCooldownSeconds)
    }

    private fun rejects(edit: (String) -> String) {
        assertThrows(IllegalArgumentException::class.java) { ConfigFile.parse(edit(export(receiver = ReceiverExport.NAME_AND_ADDRESS))) }
    }

    @Test
    fun `rejects the whole file for any malformed or unknown content`() {
        rejects { it.replace("version: 1", "version: 2") }
        rejects { it.replace("version: 1\n", "") }
        rejects { it + "mystery: 1\n" }
        rejects { it + "filter.ignore-silent: true\n" }
        rejects { it.replace("filter.ignore-silent: false", "filter.ignore-silent: yes") }
        rejects { it.replace("batching.interval-seconds: 30", "batching.interval-seconds: 4") }
        rejects { it.replace("batching.interval-seconds: 30", "batching.interval-seconds: 61") }
        rejects { it.replace("batching.interval-seconds: 30", "batching.interval-seconds: soon") }
        rejects { it.replace("max-text-chars: 1500", "max-text-chars: 99") }
        rejects { it.replace("max-text-chars: 1500", "max-text-chars: 5001") }
        rejects { it.replace("theme.mode: dark", "theme.mode: purple") }
        rejects { it.replace("language: fr", "language: klingon") }
        rejects { it.replace("receiver.address: AA:BB:CC:DD:EE:FF", "receiver.address: not-an-address") }
        rejects { it.replace("receiver.name: Alcatel 3080A\n", "") }
        rejects { it.replace("app: com.whatsapp", "app: com.whatsapp\napp: com.whatsapp") }
        rejects { it.replace("app: com.whatsapp", "app: ../etc/passwd") }
        rejects { it.replace("dumbphone-mode: true\n", "") }
        rejects { it + "no separator here\n" }
    }

    @Test
    fun `rejects oversized files`() {
        assertThrows(IllegalArgumentException::class.java) {
            ConfigFile.parse(export() + "#" + "x".repeat(ConfigFile.MAX_FILE_CHARS))
        }
    }

    @Test
    fun `error messages name the problem`() {
        val e = assertThrows(IllegalArgumentException::class.java) {
            ConfigFile.parse(export().replace("max-text-chars: 1500", "max-text-chars: 99"))
        }
        assertTrue(e.message!!.contains("max-text-chars"))
    }

    @Test
    fun `receiver names with colons and odd spacing survive`() {
        val c = ConfigFile.parse(export(settings.copy(selectedName = "  Mi: tel\n  fono ")))
        assertEquals("Mi: tel fono", c.receiverName)
    }
}
