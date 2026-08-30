package de.lmaa.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveLayoutPolicyTest {
    @Test
    fun `wide tablet uses two panes`() {
        assertTrue(AdaptiveLayoutPolicy.useTwoPane(widthDp = 1280f, fontScale = 1f))
    }

    @Test
    fun `portrait tablet stays single pane`() {
        assertFalse(AdaptiveLayoutPolicy.useTwoPane(widthDp = 800f, fontScale = 1f))
    }

    @Test
    fun `large font falls back to single pane before content becomes cramped`() {
        assertFalse(AdaptiveLayoutPolicy.useTwoPane(widthDp = 1280f, fontScale = 1.5f))
    }
}
