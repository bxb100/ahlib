package cn.ahlib.reservation.ui

import cn.ahlib.reservation.data.OpacBook
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReservationNavigationTest {
    @Test
    fun primary_navigation_excludes_scanner_and_keeps_catalog_visible() {
        assertEquals(
            listOf(
                AuthenticatedTab.ROOMS,
                AuthenticatedTab.OPAC,
                AuthenticatedTab.RESERVATIONS,
                AuthenticatedTab.PROFILE,
            ),
            AUTHENTICATED_NAVIGATION_TABS,
        )
        assertFalse(AuthenticatedTab.SCANNER in AUTHENTICATED_NAVIGATION_TABS)
    }

    @Test
    fun opac_fab_transformation_requires_more_than_one_viewport() {
        val state = ReservationUiState(
            selectedTab = AuthenticatedTab.OPAC,
            opacSearch = OpacSearchUiState(
                books = listOf(OpacBook(id = "book-id", title = "Book")),
            ),
        )

        assertFalse(state.shouldTransformOpacFab(hasScrolledBeyondViewport = false))
        assertTrue(state.shouldTransformOpacFab(hasScrolledBeyondViewport = true))
        assertFalse(
            state.copy(selectedTab = AuthenticatedTab.ROOMS)
                .shouldTransformOpacFab(hasScrolledBeyondViewport = true),
        )
        assertFalse(
            state.copy(
                opacSearch = state.opacSearch.copy(books = emptyList()),
            ).shouldTransformOpacFab(hasScrolledBeyondViewport = true),
        )
    }

    @Test
    fun scroll_tracker_publishes_only_strict_viewport_threshold_changes() {
        val tracker = OpacScrollThresholdTracker()

        assertNull(tracker.updateViewportHeight(1_000))
        assertNull(
            tracker.onScrollConsumed(
                consumedY = -999f,
                canScrollBackward = true,
            ),
        )
        assertNull(
            tracker.onScrollConsumed(
                consumedY = -1f,
                canScrollBackward = true,
            ),
        )
        assertEquals(1_000.0, tracker.scrollDistancePx, 0.0)
        assertFalse(tracker.isBeyondViewport)

        assertEquals(
            true,
            tracker.onScrollConsumed(
                consumedY = -0.5f,
                canScrollBackward = true,
            ),
        )
        assertNull(
            tracker.onScrollConsumed(
                consumedY = -120f,
                canScrollBackward = true,
            ),
        )
        assertEquals(
            false,
            tracker.onScrollConsumed(
                consumedY = 120.5f,
                canScrollBackward = true,
            ),
        )
        assertEquals(1_000.0, tracker.scrollDistancePx, 0.0)
    }

    @Test
    fun scroll_tracker_rechecks_the_current_viewport_after_resize() {
        val tracker = OpacScrollThresholdTracker(
            initialScrollDistancePx = 800.25,
            initialViewportHeightPx = 1_000,
        )

        assertFalse(tracker.isBeyondViewport)
        assertEquals(true, tracker.updateViewportHeight(800))
        assertNull(tracker.updateViewportHeight(800))
        assertEquals(false, tracker.updateViewportHeight(1_200))
    }

    @Test
    fun scroll_tracker_resets_at_the_top_and_for_a_new_search() {
        val tracker = OpacScrollThresholdTracker(
            initialScrollDistancePx = 1_000.25,
            initialViewportHeightPx = 1_000,
        )

        assertTrue(tracker.isBeyondViewport)
        assertEquals(
            false,
            tracker.onScrollConsumed(
                consumedY = 0f,
                canScrollBackward = false,
            ),
        )
        assertEquals(0.0, tracker.scrollDistancePx, 0.0)
        assertNull(tracker.reset())
    }

    @Test
    fun restored_scroll_tracker_preserves_fractional_pixels_and_threshold() {
        val restored = OpacScrollThresholdTracker(
            initialScrollDistancePx = 1_000.25,
            initialViewportHeightPx = 1_000,
        )

        assertEquals(1_000.25, restored.scrollDistancePx, 0.0)
        assertEquals(1_000, restored.viewportHeightPx)
        assertTrue(restored.isBeyondViewport)
    }

    @Test
    fun opac_scroll_to_top_animation_is_limited_to_one_visible_viewport() {
        assertEquals(
            4,
            calculateOpacScrollToTopAnimationStartIndex(
                firstVisibleItemIndex = 40,
                visibleItemCount = 4,
            ),
        )
        assertNull(
            calculateOpacScrollToTopAnimationStartIndex(
                firstVisibleItemIndex = 4,
                visibleItemCount = 4,
            ),
        )
        assertNull(
            calculateOpacScrollToTopAnimationStartIndex(
                firstVisibleItemIndex = 0,
                visibleItemCount = 0,
            ),
        )
        assertEquals(
            1,
            calculateOpacScrollToTopAnimationStartIndex(
                firstVisibleItemIndex = 2,
                visibleItemCount = 0,
            ),
        )
    }

    @Test
    fun opac_scroll_to_top_controller_ignores_overlapping_requests() = runTest {
        val controller = OpacScrollToTopController()
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        var laterRequestCount = 0

        assertTrue(
            controller.launch(this) {
                firstStarted.complete(Unit)
                releaseFirst.await()
            },
        )
        runCurrent()
        assertTrue(firstStarted.isCompleted)
        assertFalse(
            controller.launch(this) {
                laterRequestCount += 1
            },
        )

        releaseFirst.complete(Unit)
        advanceUntilIdle()
        assertEquals(0, laterRequestCount)
        assertTrue(
            controller.launch(this) {
                laterRequestCount += 1
            },
        )
        advanceUntilIdle()
        assertEquals(1, laterRequestCount)
    }

    @Test
    fun cancelling_scroll_to_top_does_not_clear_a_replacement_request() = runTest {
        val controller = OpacScrollToTopController()
        val firstStarted = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val releaseSecond = CompletableDeferred<Unit>()
        var thirdRequestCount = 0

        assertTrue(
            controller.launch(this) {
                firstStarted.complete(Unit)
                awaitCancellation()
            },
        )
        runCurrent()
        assertTrue(firstStarted.isCompleted)

        controller.cancel()
        assertTrue(
            controller.launch(this) {
                secondStarted.complete(Unit)
                releaseSecond.await()
            },
        )
        runCurrent()
        assertTrue(secondStarted.isCompleted)
        assertFalse(
            controller.launch(this) {
                thirdRequestCount += 1
            },
        )

        releaseSecond.complete(Unit)
        advanceUntilIdle()
        assertTrue(
            controller.launch(this) {
                thirdRequestCount += 1
            },
        )
        advanceUntilIdle()
        assertEquals(1, thirdRequestCount)
    }
}
