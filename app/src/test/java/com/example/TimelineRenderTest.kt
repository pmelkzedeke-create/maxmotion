package com.example

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import com.example.engine.SceneEvaluator
import com.example.model.DemoProjectFactory
import com.example.render.LayerRasterizer
import com.example.ui.components.TimelinePanel
import com.example.ui.theme.MaxMotionTheme
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-420dpi")
class TimelineRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `test drawText behavior when topLeft exceeds canvas width`() {
        composeRule.setContent {
            val tm = rememberTextMeasurer()
            Box(modifier = Modifier.size(200.dp, 30.dp)) {
                Canvas(modifier = Modifier.size(200.dp, 30.dp)) {
                    drawText(
                        textMeasurer = tm,
                        text = "8s",
                        style = TextStyle(fontSize = 9.sp),
                        topLeft = Offset(size.width + 10f, 5f)
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `timeline panel renders layers expanded subtracks and center playhead cleanly`() {
        val demo = DemoProjectFactory.createDemoProject()
        composeRule.setContent {
            MaxMotionTheme {
                var playhead by remember { mutableLongStateOf(600L) }
                var expanded by remember { mutableStateOf(setOf("layer_prism_vector")) }
                var selectedKfs by remember { mutableStateOf(emptySet<String>()) }
                TimelinePanel(
                    composition = demo.activeComposition,
                    assets = demo.assets,
                    playheadMs = playhead,
                    isPlaying = false,
                    selectedLayerIds = setOf("layer_prism_vector"),
                    expandedLayerIds = expanded,
                    selectedKeyframeIds = selectedKfs,
                    pickWhipSourceId = null,
                    timelineZoom = 1f,
                    magneticSnapping = true,
                    onTogglePlay = {},
                    onSeek = { playhead = it },
                    onZoomChange = {},
                    onToggleMagnetic = {},
                    onSelectLayer = { _, _ -> },
                    onToggleExpandLayer = { id ->
                        expanded = if (id in expanded) expanded - id else expanded + id
                    },
                    onToggleSelectKeyframe = { id ->
                        selectedKfs = if (id in selectedKfs) selectedKfs - id else selectedKfs + id
                    },
                    onShiftSelectedKeyframes = {},
                    onDeleteSelectedKeyframes = {},
                    onApplyCurveToSelectedKeyframes = {},
                    onClearSelectedKeyframes = { selectedKfs = emptySet() },
                    onToggleVisibility = {},
                    onToggleLock = {},
                    onToggleSolo = {},
                    onToggle3D = {},
                    onStartPickWhip = {},
                    onMoveZOrder = { _, _ -> },
                    onUpdateBounds = { _, _, _ -> },
                    onEnterPreComp = {},
                    modifier = Modifier.fillMaxWidth().height(420.dp)
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("playhead_needle_handle").assertIsDisplayed()
        composeRule.onNodeWithTag("subtrack_kf_k_prism_s0").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("multi_keyframe_toolbar").assertIsDisplayed()
    }

    @Test
    fun `rasterizer renders all demo layers cleanly`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val demo = DemoProjectFactory.createDemoProject()
        val scene = SceneEvaluator.evaluateScene(demo, demo.activeComposition, 600L)
        val rasterizer = LayerRasterizer(ctx)
        scene.layers.forEach { ev ->
            if (ev.layer.type == com.example.model.LayerType.VECTOR || ev.layer.type == com.example.model.LayerType.TEXT) {
                val bmp = rasterizer.rasterizeLayerBitmap(ev, demo.assets, demo.activeComposition.width, demo.activeComposition.height, scene.audioAmplitude)
                assertNotNull("Layer ${ev.layer.name} failed to rasterize", bmp)
            }
        }
    }

    @Test
    fun `timeline snap engine snaps clip edges keyframes and switches ruler to frame mode at high zoom`() {
        val demo = DemoProjectFactory.createDemoProject()
        val comp = demo.activeComposition.copy(beatMarkersMs = listOf(1500L))
        val targetLayer = comp.layers.first()

        val snapMove = com.example.ui.components.timeline.TimelineSnapEngine.snapClipMove(
            proposedInMs = 1485L,
            clipDurationMs = 1000L,
            composition = comp,
            layerId = targetLayer.id,
            playheadMs = 600L,
            pxPerSec = 192f,
            enabled = true
        )
        org.junit.Assert.assertTrue(snapMove.didSnap)
        org.junit.Assert.assertEquals(1500L, snapMove.snappedMs)
        org.junit.Assert.assertEquals(1500L, snapMove.snapGuideMs)

        val noSnap = com.example.ui.components.timeline.TimelineSnapEngine.snapClipMove(
            proposedInMs = 1485L,
            clipDurationMs = 1000L,
            composition = comp,
            layerId = targetLayer.id,
            playheadMs = 600L,
            pxPerSec = 192f,
            enabled = false
        )
        org.junit.Assert.assertFalse(noSnap.didSnap)
        org.junit.Assert.assertEquals(1485L, noSnap.snappedMs)

        val tickFrame = com.example.ui.components.timeline.TimelineSnapEngine.computeRulerTickStep(pxPerSec = 600f, fps = 60)
        org.junit.Assert.assertTrue(tickFrame.showFrames)
    }

    @Test
    fun `timeline layer header flags quick strip and batch keyframe duplication work end to end`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = com.example.ui.MaxMotionViewModel(app)
        vm.openDemoProject()

        val targetId = "layer_prism_vector"
        vm.selectLayer(targetId)
        vm.seekTo(1200L)
        vm.toggleBeatMarkerAtPlayhead()
        org.junit.Assert.assertTrue(vm.document.value.activeComposition.beatMarkersMs.any { kotlin.math.abs(it - 1200L) <= 45L })

        vm.setTimelineZoom(10.5f)
        org.junit.Assert.assertEquals(10.5f, vm.timelineZoom.value, 0.01f)

        vm.toggleLayerSolo(targetId)
        org.junit.Assert.assertTrue(vm.document.value.activeComposition.layers.first { it.id == targetId }.solo)

        vm.toggleLayer3D(targetId)
        org.junit.Assert.assertFalse(vm.document.value.activeComposition.layers.first { it.id == targetId }.is3D)
        vm.toggleLayer3D(targetId)
        org.junit.Assert.assertTrue(vm.document.value.activeComposition.layers.first { it.id == targetId }.is3D)

        val layersBefore = vm.document.value.activeComposition.layers.size
        vm.freezeFrameAtPlayhead()
        org.junit.Assert.assertTrue(vm.document.value.activeComposition.layers.size > layersBefore)

        vm.toggleKeyframeSelection("k_prism_s0")
        vm.duplicateSelectedKeyframes(120L)
        org.junit.Assert.assertEquals(1, vm.selectedKeyframeIds.value.size)

        val frozenLayerId = vm.selectedLayerIds.value.first()
        composeRule.setContent {
            MaxMotionTheme {
                TimelinePanel(
                    composition = vm.document.value.activeComposition,
                    assets = vm.document.value.assets,
                    playheadMs = vm.playheadMs.value,
                    isPlaying = false,
                    selectedLayerIds = vm.selectedLayerIds.value,
                    expandedLayerIds = setOf(targetId),
                    selectedKeyframeIds = vm.selectedKeyframeIds.value,
                    pickWhipSourceId = null,
                    timelineZoom = vm.timelineZoom.value,
                    magneticSnapping = true,
                    onTogglePlay = {},
                    onSeek = { vm.seekTo(it) },
                    onZoomChange = { vm.setTimelineZoom(it) },
                    onToggleMagnetic = {},
                    onSelectLayer = { id, m -> vm.selectLayer(id, m) },
                    onToggleExpandLayer = {},
                    onToggleSelectKeyframe = { vm.toggleKeyframeSelection(it) },
                    onShiftSelectedKeyframes = { vm.shiftSelectedKeyframes(it) },
                    onDeleteSelectedKeyframes = { vm.deleteSelectedKeyframes() },
                    onApplyCurveToSelectedKeyframes = {},
                    onClearSelectedKeyframes = { vm.clearKeyframeSelection() },
                    onToggleVisibility = { vm.toggleLayerVisibility(it) },
                    onToggleLock = { vm.toggleLayerLock(it) },
                    onToggleSolo = { vm.toggleLayerSolo(it) },
                    onToggle3D = { vm.toggleLayer3D(it) },
                    onStartPickWhip = { vm.startPickWhip(it) },
                    onUpdateBounds = { id, i, o -> vm.updateLayerBounds(id, i, o) },
                    onEnterPreComp = {},
                    modifier = Modifier.fillMaxWidth().height(460.dp)
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("timeline_quick_split_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("timeline_beat_toggle_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("timeline_freeze_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("timeline_layers_list").performScrollToNode(hasTestTag("layer_row_$frozenLayerId"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("solo_layer_$frozenLayerId").assertIsDisplayed()
        composeRule.onNodeWithTag("lock_layer_$frozenLayerId").assertIsDisplayed()
        composeRule.onNodeWithTag("3d_layer_$frozenLayerId").assertIsDisplayed()
        composeRule.onNodeWithTag("pickwhip_layer_$frozenLayerId").assertIsDisplayed()
        composeRule.onNodeWithTag("drag_reorder_$frozenLayerId").assertIsDisplayed()
    }
}
