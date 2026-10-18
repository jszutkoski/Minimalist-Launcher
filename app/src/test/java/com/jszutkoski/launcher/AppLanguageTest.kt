package com.jszutkoski.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test
    fun eachTapMovesToTheNextLanguageAndBackToThePhones() {
        assertEquals(AppLanguage.PORTUGUESE, AppLanguage.after(null))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.after(AppLanguage.PORTUGUESE))
        assertEquals(null, AppLanguage.after(AppLanguage.ENGLISH))
    }
}
