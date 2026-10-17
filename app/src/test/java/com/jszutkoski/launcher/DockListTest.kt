package com.jszutkoski.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

/** The bottom icons as a list: what each change does to it, and to how many are shown. */
class DockListTest {
    private val camera = Prefs.DOCK_CAMERA

    @Test
    fun theDefaultsFillInWhatIsMissingWithoutRepeats() {
        assertEquals(
            listOf("a", "b", camera, "c"),
            DockList.fill(listOf("a", "b", "a"), listOf("b", camera, "c"), max = 6),
        )
        assertEquals(listOf("a", "b"), DockList.fill(listOf("a"), listOf("b", "c"), max = 2))
    }

    @Test
    fun anAppAlreadyThereSwapsPlacesInsteadOfShowingTwice() {
        assertEquals(listOf("c", "b", "a"), DockList.replace(listOf("a", "b", "c"), 0, "c"))
        assertEquals(listOf("x", "b", "c"), DockList.replace(listOf("a", "b", "c"), 0, "x"))
    }

    @Test
    fun aNewIconGoesAfterTheShownOnesAndTheRestKeepTheirPlace() {
        // Three shown, "d" and "e" kept for later.
        val list = listOf("a", "b", "c", "d", "e")
        assertEquals(listOf("a", "b", "c", "x", "d", "e") to 4, DockList.add(list, 3, "x"))
        // A kept one is brought forward rather than repeated.
        assertEquals(listOf("a", "b", "c", "e", "d") to 4, DockList.add(list, 3, "e"))
        // One already shown changes nothing.
        assertEquals(list to 3, DockList.add(list, 3, "b"))
    }

    @Test
    fun removingOneShowsOneFewerWithoutBringingAKeptOneForward() {
        val (list, count) = DockList.remove(listOf("a", "b", "c", "d"), 3, 1)
        assertEquals(listOf("a", "c", "d"), list)
        assertEquals(2, count)
        assertEquals(listOf("a", "c"), list.take(count))
    }

    @Test
    fun movingSwapsWithTheNeighbourAndStopsAtTheEnds() {
        assertEquals(listOf("b", "a", "c"), DockList.move(listOf("a", "b", "c"), 1, -1))
        assertEquals(listOf("a", "c", "b"), DockList.move(listOf("a", "b", "c"), 1, 1))
        assertEquals(listOf("a", "b", "c"), DockList.move(listOf("a", "b", "c"), 2, 1))
    }
}
