package com.jszutkoski.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SheetTest {
    private fun activity() = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()

    @Test
    fun closingOneActivitysSheetsLeavesTheOthers() {
        val first = activity()
        val second = activity()
        val mine = Sheet(first, "Mine").apply { show() }
        val theirs = Sheet(second, "Theirs").apply { show() }
        Sheet.dismissAll(first)
        idle()
        assertFalse(mine.dialog.isShowing)
        assertTrue(theirs.dialog.isShowing)
        Sheet.dismissAll(second)
    }

    @Test
    fun anOptionClosesTheSheetAndRuns() {
        val host = activity()
        var ran = false
        var closed = false
        val sheet = Sheet(host, "Menu").apply {
            option("Do it") { ran = true }
            onDismiss = { closed = true }
            show()
        }
        sheet.column.textViews().single { it.text == "Do it" }.performClick()
        idle()
        assertTrue(ran)
        assertTrue(closed)
        assertFalse(sheet.dialog.isShowing)
    }
}
