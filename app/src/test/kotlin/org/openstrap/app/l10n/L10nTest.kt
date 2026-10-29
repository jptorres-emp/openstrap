package org.openstrap.app.l10n

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class L10nTest {
    @After fun english() = L10n.speak(false)

    @Test fun `the phone's language picks Portuguese only when it's Portuguese`() {
        assertTrue(L10n.speaksPortuguese(AppLanguage.System, Locale.forLanguageTag("pt-PT")))
        assertTrue(L10n.speaksPortuguese(AppLanguage.System, Locale.forLanguageTag("pt-BR")))
        assertFalse(L10n.speaksPortuguese(AppLanguage.System, Locale.UK))
        assertTrue(L10n.speaksPortuguese(AppLanguage.Portuguese, Locale.UK))
        assertFalse(L10n.speaksPortuguese(AppLanguage.English, Locale.forLanguageTag("pt-PT")))
    }

    @Test fun `a saved choice reads back, and anything else is the phone's`() {
        AppLanguage.entries.forEach { assertEquals(it, AppLanguage.of(it.key)) }
        assertEquals(AppLanguage.System, AppLanguage.of(null))
        assertEquals(AppLanguage.System, AppLanguage.of("klingon"))
    }

    @Test fun `tr speaks the language set`() {
        assertEquals("Today", tr("Today", "Hoje"))
        L10n.speak(true)
        assertEquals("Hoje", tr("Today", "Hoje"))
    }
}
