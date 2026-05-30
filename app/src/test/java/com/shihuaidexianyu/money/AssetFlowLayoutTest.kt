package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.ui.stats.calculateAssetFlowLayout
import kotlin.test.assertTrue
import org.junit.Test

class AssetFlowLayoutTest {
    @Test
    fun `middle row nodes fit within narrow phone card width`() {
        val layout = calculateAssetFlowLayout(widthDp = 280f)

        val middleRowWidth = layout.middleNodeWidth * 3f + layout.middleGap * 2f

        assertTrue(middleRowWidth <= 280f)
        assertTrue(layout.bottomNodeWidth <= 280f)
        assertTrue(layout.nodeHeight in 48f..56f)
    }

    @Test
    fun `node widths scale with wider card width`() {
        val narrow = calculateAssetFlowLayout(widthDp = 280f)
        val wide = calculateAssetFlowLayout(widthDp = 520f)

        assertTrue(wide.middleNodeWidth > narrow.middleNodeWidth)
        assertTrue(wide.diagramHeight >= narrow.diagramHeight)
    }

    @Test
    fun `layout dimensions are density independent`() {
        val phoneCardWidthDp = 320f
        val density = 420f / 160f
        val layout = calculateAssetFlowLayout(widthDp = phoneCardWidthDp)

        assertTrue(layout.nodeHeight >= 48f)
        assertTrue(layout.diagramHeight >= 184f)
        assertTrue(layout.diagramHeight * density >= 480f)
    }
}
