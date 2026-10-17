package com.jszutkoski.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

/** Which icons the bedside clock shows for the apps with notifications waiting. */
class GlyphsTest {
    private val glyphs = mapOf(
        "com.whatsapp" to R.drawable.ic_chat,
        "com.whatsapp.w4b" to R.drawable.ic_chat,
        "com.google.android.dialer" to R.drawable.ic_phone,
        "com.google.android.gm" to R.drawable.ic_mail,
        "com.microsoft.office.outlook" to R.drawable.ic_mail,
        "com.instagram.android" to R.drawable.ic_instagram,
        "com.nu.production" to R.drawable.ic_bank,
        "com.netflix.mediaclient" to R.drawable.ic_netflix,
    )

    private fun icons(vararg packages: String, max: Int = AppGlyphs.MAX_WAITING) =
        AppGlyphs.waiting(packages.toList(), glyphs, max).map { it.drawable to it.packages }

    @Test
    fun callsComeFirstThenMessagesWhateverTheOrderTheyArrived() {
        assertEquals(
            listOf(R.drawable.ic_phone, R.drawable.ic_chat, R.drawable.ic_instagram, R.drawable.ic_mail),
            icons("com.google.android.gm", "com.instagram.android", "com.whatsapp", "com.google.android.dialer").map { it.first },
        )
    }

    @Test
    fun appsOfOneKindShareOneIcon() {
        assertEquals(
            listOf(R.drawable.ic_mail to listOf("com.google.android.gm", "com.microsoft.office.outlook")),
            icons("com.google.android.gm", "com.microsoft.office.outlook"),
        )
    }

    @Test
    fun appsWithoutAnIconShareThreeDotsAtTheEnd() {
        assertEquals(
            listOf(R.drawable.ic_chat to listOf("com.whatsapp"), R.drawable.ic_more to listOf("org.example", "com.other")),
            icons("org.example", "com.whatsapp", "com.other"),
        )
        assertEquals(emptyList<Pair<Int, List<String>>>(), icons())
    }

    @Test
    fun pastTheMostTheRestJoinTheDots() {
        assertEquals(
            listOf(R.drawable.ic_phone to listOf("com.google.android.dialer"), R.drawable.ic_more to listOf("org.example", "com.nu.production")),
            icons("com.nu.production", "org.example", "com.google.android.dialer", max = 1),
        )
    }
}
