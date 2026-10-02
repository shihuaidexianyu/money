package com.shihuaidexianyu.money.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppShellMacrobenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartup() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.Full(),
        startupMode = StartupMode.COLD,
        iterations = 10,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
    }

    @Test
    fun openHistoryFirstFrame() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Full(),
        startupMode = StartupMode.WARM,
        iterations = 10,
        setupBlock = {
            seed(10_000)
            startActivityAndWait()
            selectTab("账户")
        },
    ) {
        selectTab("明细")
    }

    @Test
    fun accountsTenThousandRows() = measureAccounts(10_000)

    @Test
    fun accountsOneHundredThousandRows() = measureAccounts(100_000)

    @Test
    fun historyTenThousandRows() = measureHistory(10_000)

    @Test
    fun historyOneHundredThousandRows() = measureHistory(100_000)

    private fun measureAccounts(recordCount: Int) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Full(),
        startupMode = StartupMode.COLD,
        iterations = 10,
        setupBlock = {
            seed(recordCount)
            pressHome()
        },
    ) {
        startActivityAndWait()
        device.waitForIdle()
    }

    private fun measureHistory(recordCount: Int) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Full(),
        startupMode = StartupMode.WARM,
        iterations = 10,
        setupBlock = {
            seed(recordCount)
            startActivityAndWait()
            selectTab("账户")
        },
    ) {
        selectTab("明细")
    }

    @Test
    fun historyScrollTenThousandRows() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Full(),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = {
            seed(10_000)
            startActivityAndWait()
            selectTab("明细")
        },
    ) {
        val list = device.wait(Until.findObject(By.scrollable(true)), 5_000L)
            ?: error("History list was not visible")
        // Exercise real scrolling/prefetch; measuring idle repeated tab clicks hides jank.
        repeat(3) { list.fling(Direction.DOWN); device.waitForIdle() }
        repeat(3) { list.fling(Direction.UP); device.waitForIdle() }
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.selectTab(label: String) {
        check(device.wait(Until.hasObject(By.text(label)), 5_000L)) { "Destination $label was not visible" }
        // The title and the bottom tab can share text. Pick the tab, not the toolbar title.
        val tab = device.findObjects(By.text(label)).maxBy { it.visibleBounds.bottom }
        tab.click()
        device.waitForIdle()
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.seed(recordCount: Int) {
        val result = device.executeShellCommand(
            "am broadcast --include-stopped-packages -a $SEED_ACTION -p $TARGET_PACKAGE --ei record_count $recordCount",
        )
        check("result=-1" in result && "data=\"$recordCount\"" in result) {
            "Performance fixture was not loaded: $result"
        }
    }

    private companion object {
        const val TARGET_PACKAGE = "com.shihuaidexianyu.money"
        const val SEED_ACTION = "com.shihuaidexianyu.money.SEED_PERFORMANCE_FIXTURE"
    }
}
