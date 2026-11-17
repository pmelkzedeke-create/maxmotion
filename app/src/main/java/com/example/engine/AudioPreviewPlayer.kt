package com.example.engine

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import com.example.model.AssetItem
import com.example.model.AssetType
import com.example.model.CompositionNode
import com.example.model.LayerType

/**
 * Synchronizes real Android MediaPlayer playback with timeline playheadMs, in/out trim,
 * startOffset, volume, and fade-in/fade-out envelopes.
 */
class AudioPreviewPlayer(private val context: Context) {

    private data class ActivePlayer(
        val layerId: String,
        val uri: String,
        val player: MediaPlayer
    )

    private val players = HashMap<String, ActivePlayer>()

    fun syncPlayback(
        isPlaying: Boolean,
        playheadMs: Long,
        comp: CompositionNode,
        assets: List<AssetItem>
    ) {
        val audioLayers = comp.layers.filter {
            it.visible && (it.type == LayerType.AUDIO || it.type == LayerType.VIDEO) && it.assetId != null
        }
        val activeIds = audioLayers.map { it.id }.toSet()

        // Release removed layers
        val toRemove = players.keys.filter { it !in activeIds }
        toRemove.forEach { id ->
            runCatching { players.remove(id)?.player?.release() }
        }

        if (!isPlaying) {
            players.values.forEach { ap ->
                runCatching { if (ap.player.isPlaying) ap.player.pause() }
            }
            return
        }

        for (layer in audioLayers) {
            val asset = assets.firstOrNull { it.id == layer.assetId } ?: continue
            if (asset.type != AssetType.AUDIO && asset.type != AssetType.VIDEO) continue

            val inWindow = playheadMs in layer.inMs..layer.outMs
            var active = players[layer.id]
            if (active == null || active.uri != asset.uri) {
                active?.player?.let { runCatching { it.release() } }
                val mp = runCatching {
                    MediaPlayer().apply {
                        setDataSource(context, Uri.parse(asset.uri))
                        prepare()
                    }
                }.getOrNull() ?: continue
                active = ActivePlayer(layer.id, asset.uri, mp)
                players[layer.id] = active
            }

            val mp = active.player
            if (!inWindow) {
                runCatching { if (mp.isPlaying) mp.pause() }
                continue
            }

            val localMs = (playheadMs - layer.inMs + layer.startOffsetMs).toInt().coerceAtLeast(0)
            val baseVol = layer.audioSpec?.volume?.baseValue ?: if (layer.type == LayerType.VIDEO) 0.8f else 1.0f
            val fadeIn = (layer.audioSpec?.fadeInMs ?: 0L).coerceAtLeast(1L)
            val fadeOut = (layer.audioSpec?.fadeOutMs ?: 0L).coerceAtLeast(1L)
            val inEnv = ((playheadMs - layer.inMs).toFloat() / fadeIn.toFloat()).coerceIn(0f, 1f)
            val outEnv = ((layer.outMs - playheadMs).toFloat() / fadeOut.toFloat()).coerceIn(0f, 1f)
            val vol = (baseVol * minOf(inEnv, outEnv)).coerceIn(0f, 1f)

            runCatching {
                mp.setVolume(vol, vol)
                if (!mp.isPlaying) {
                    if (localMs < mp.duration) mp.seekTo(localMs)
                    mp.start()
                } else if (kotlin.math.abs(mp.currentPosition - localMs) > 220) {
                    if (localMs < mp.duration) mp.seekTo(localMs)
                }
            }
        }
    }

    fun releaseAll() {
        players.values.forEach { runCatching { it.player.release() } }
        players.clear()
    }
}
