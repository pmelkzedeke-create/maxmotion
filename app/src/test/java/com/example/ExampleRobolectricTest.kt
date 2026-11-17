package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.ProjectRepository
import com.example.model.AspectRatioPreset
import com.example.model.DemoProjectFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-420dpi")
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("MaxMotion", appName)
    }

    @Test
    fun `repository saves and reloads demo and blank projects with moshi and room`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.getInstance(context)
        val repo = ProjectRepository(context, db.maxMotionDao())
        repo.ensureSeeded()

        val loadedDemo = repo.loadProject("demo_neon_kinetic")
        assertNotNull(loadedDemo)
        assertEquals("Intro Cinética Neon", loadedDemo?.name)

        val blank = DemoProjectFactory.createBlankProject("Teste Crash", AspectRatioPreset.PORTRAIT_9_16, 60, 8000L)
        repo.saveProject(blank)
        val reloaded = repo.loadProject(blank.id)
        assertNotNull(reloaded)
        assertEquals("Teste Crash", reloaded?.name)
    }

    @Test
    fun `clicking Demo Neon navigates into EditorScreen without crashing`() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("open_demo_btn").assertIsDisplayed().performClick()
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodesWithTag("play_pause_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("play_pause_button").assertIsDisplayed()
        composeRule.onNodeWithTag("timeline_timecode").assertIsDisplayed()
        composeRule.onNodeWithTag("canvas_viewport").assertIsDisplayed()

        // Open and close TRANSFORM inspector + deselect layer to verify DockActionItem pill collapse
        composeRule.onNodeWithTag("dock_transform").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("inspector_sheet_transform").assertIsDisplayed()
        composeRule.onNodeWithTag("dock_transform").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dock_deselect").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dock_add_text").assertIsDisplayed()

        // Navigate back to Hub
        composeRule.onNodeWithTag("editor_back_btn").performClick()
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodesWithTag("create_project_btn").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `creating new project navigates into EditorScreen without crashing`() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("create_project_btn").assertIsDisplayed().performClick()
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodesWithTag("confirm_create_project_btn").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("confirm_create_project_btn").assertIsDisplayed().performClick()
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodesWithTag("play_pause_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("play_pause_button").assertIsDisplayed()
        composeRule.onNodeWithTag("canvas_viewport").assertIsDisplayed()
    }

    @Test
    fun `opening saved project card navigates into EditorScreen without crashing`() {
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodesWithTag("project_card_demo_neon_kinetic").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("project_card_demo_neon_kinetic").performClick()
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodesWithTag("play_pause_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("play_pause_button").assertIsDisplayed()
        composeRule.onNodeWithTag("playhead_needle_handle").assertIsDisplayed()
    }

    @Test
    fun `timeline panel first frame draw with zero viewport width does not throw`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("open_demo_btn").performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("play_pause_button").assertIsDisplayed()
    }

    @Test
    fun `expanding subtracks playback and seeking works smoothly`() {
        composeRule.onNodeWithTag("open_demo_btn").performClick()
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodesWithTag("play_pause_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("expand_layer_layer_prism_vector").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("subtrack_kf_k_prism_s0").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("multi_keyframe_toolbar").assertIsDisplayed()
        composeRule.onNodeWithTag("play_pause_button").performClick()
        composeRule.mainClock.advanceTimeBy(180)
        composeRule.onNodeWithTag("play_pause_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("rewind_to_start_btn").performClick()
        composeRule.waitForIdle()
    }
}
