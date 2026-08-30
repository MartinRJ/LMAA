package de.lmaa.app

internal object AdaptiveLayoutPolicy {
    private const val TWO_PANE_EFFECTIVE_WIDTH_DP = 900f

    fun useTwoPane(widthDp: Float, fontScale: Float): Boolean {
        val safeFontScale = fontScale.coerceAtLeast(1f)
        return widthDp / safeFontScale >= TWO_PANE_EFFECTIVE_WIDTH_DP
    }
}
