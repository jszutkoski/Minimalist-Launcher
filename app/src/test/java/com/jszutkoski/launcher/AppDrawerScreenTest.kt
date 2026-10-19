package com.jszutkoski.launcher

import android.telephony.TelephonyManager
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ListView
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AppDrawerScreenTest {
    private val launched = mutableListOf<String>()
    private val results = mutableListOf<SearchResult>()
    private val callbacks = object : AppDrawerView.Callbacks {
        override fun onLaunch(app: App, source: View) {
            launched += app.label
        }

        override fun onAppLongPress(app: App) = Unit

        override fun onResult(result: SearchResult, source: View) {
            results += result
        }

        override fun onWhatsApp(contact: Contact) = Unit
        override fun onCloseDrag(progress: Float) = Unit
        override fun onCloseRelease(close: Boolean) = Unit
    }
    private val locale = Locale.getDefault()
    private lateinit var drawer: AppDrawerView

    private val search get() = drawer.views().filterIsInstance<EditText>().single()
    private val list get() = drawer.views().filterIsInstance<ListView>().single()
    private val items get() = (0 until list.adapter.count).map { list.adapter.getItem(it) as SearchResult }

    @Before
    fun setUp() = newDrawer()

    /** The drawer reads the phone's country when it is made, so tests that set one make it again. */
    private fun newDrawer() {
        drawer = AppDrawerView(RuntimeEnvironment.getApplication(), callbacks)
        drawer.setApps(listOf("Agenda", "aiqfome", "Instagram", "Spotify", "Telegram").map { testApp(it) })
    }

    private fun phoneIn(country: String, language: String) {
        Locale.setDefault(Locale.forLanguageTag(language))
        val telephony = RuntimeEnvironment.getApplication().getSystemService(TelephonyManager::class.java)
        shadowOf(telephony).setNetworkCountryIso(country)
        newDrawer()
    }

    /** The answer row as it is drawn: "= 1.800". */
    private fun answerText() =
        list.adapter.getView(0, null, list).textViews().first().text.toString()

    @After
    fun tearDown() = Locale.setDefault(locale)

    private fun type(text: String) = search.setText(text)

    @Test
    fun twoLettersThatStartTheOnlyMatchOpenIt() {
        type("ai")
        assertEquals(listOf("aiqfome"), launched)
    }

    @Test
    fun aTimerComesFirstAndOpensNothingOnItsOwn() {
        type("timer 10 min")
        val first = items.first() as SearchResult.Clock
        assertEquals(ClockCommand.Timer(600), first.command)
        assertEquals("Timer for 10 min", list.adapter.getView(0, null, list).textViews().first().text.toString())
        assertTrue(launched.isEmpty())
    }

    @Test
    fun anAlarmIsOfferedForATimeOfDay() {
        type("alarm 7:30")
        assertEquals(ClockCommand.Alarm(7, 30), (items.first() as SearchResult.Clock).command)
    }

    @Test
    fun aLetterInsideANameOpensNothing() {
        type("q")
        assertTrue(launched.isEmpty())
    }

    @Test
    fun twoLettersThatAlsoFindASettingStillOpenTheApp() {
        // "in" finds Wi-Fi ("internet") too; it used to stop Instagram from opening.
        type("in")
        assertEquals(listOf("Instagram"), launched)
    }

    @Test
    fun deletingLettersNeverOpens() {
        drawer.autoLaunch = false
        type("spot")
        drawer.autoLaunch = true
        type("sp")
        assertTrue(launched.isEmpty())
    }

    @Test
    fun anAppsShortNameWaitsForWhatToSearchInIt() {
        // "sp" is Spotify's short name: "sp lofi" must stay typeable.
        type("sp")
        assertTrue(launched.isEmpty())
        type("sp lofi")
        assertTrue(launched.isEmpty())
        assertTrue(items.first() is SearchResult.InApp)
    }

    @Test
    fun goWithAnEmptySearchOpensNothing() {
        search.onEditorAction(EditorInfo.IME_ACTION_GO)
        assertTrue(launched.isEmpty())
        assertTrue(results.isEmpty())
    }

    @Test
    fun goOpensTheTopMatch() {
        drawer.autoLaunch = false
        type("tele")
        search.onEditorAction(EditorInfo.IME_ACTION_GO)
        assertEquals(listOf("Telegram"), launched)
    }

    @Test
    fun aPhoneInEnglishInBrazilReadsTheDotAsThousands() {
        phoneIn("br", "en-US")
        type("1.500 + 300")
        assertEquals(1800.0, (items.first() as SearchResult.Answer).value, 0.0)
        assertEquals("= 1.800", answerText())
    }

    @Test
    fun aPhoneInPortugueseInBrazilToo() {
        phoneIn("br", "pt-BR")
        type("2,5 + 1.000")
        assertEquals(1002.5, (items.first() as SearchResult.Answer).value, 0.0)
        assertEquals("= 1.002,5", answerText())
    }

    @Test
    fun inTheUsTheDotIsADecimal() {
        phoneIn("us", "en-US")
        type("1.5 + 1,000")
        assertEquals(1001.5, (items.first() as SearchResult.Answer).value, 0.0)
        assertEquals("= 1,001.5", answerText())
    }

    @Test
    fun aPhoneNumberIsNotASum() {
        type("11 98765-4321")
        assertTrue(items.none { it is SearchResult.Answer })
    }

    @Test
    fun theWebIsAlwaysLast() {
        drawer.autoLaunch = false
        type("spo")
        assertTrue(items.last() is SearchResult.Web)
        assertTrue(items.first() is SearchResult.AppHit)
    }

    @Test
    fun searchOnlyListsNothingUntilTypedOrAsked() {
        drawer.searchOnly = true
        assertTrue(items.isEmpty())
        val showAll = drawer.textViews().single { it.text == drawer.context.getString(R.string.show_all_apps) }
        assertEquals(View.VISIBLE, showAll.visibility)
        showAll.performClick()
        assertEquals(5, items.size)
        drawer.reset()
        assertTrue(items.isEmpty())
    }

    @Test
    fun resetClearsTheSearch() {
        drawer.autoLaunch = false
        type("spo")
        drawer.reset()
        assertEquals("", search.text.toString())
        assertEquals(5, items.size)
    }
}
