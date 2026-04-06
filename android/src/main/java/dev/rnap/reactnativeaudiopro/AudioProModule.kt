package dev.rnap.reactnativeaudiopro

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.LifecycleEventListener
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AudioProModule(private val reactContext: ReactApplicationContext) :
	ReactContextBaseJavaModule(reactContext), LifecycleEventListener {

	companion object {
		const val NAME = "AudioPro"

		const val EVENT_NAME = "AudioProEvent"

		const val STATE_IDLE = "IDLE"
		const val STATE_PLAYING = "PLAYING"
		const val STATE_PAUSED = "PAUSED"
		const val STATE_STOPPED = "STOPPED"
		const val STATE_LOADING = "LOADING"
		const val STATE_ERROR = "ERROR"

		const val EVENT_TYPE_STATE_CHANGED = "STATE_CHANGED"
		const val EVENT_TYPE_TRACK_ENDED = "TRACK_ENDED"
		const val EVENT_TYPE_PLAYBACK_ERROR = "PLAYBACK_ERROR"
		const val EVENT_TYPE_PROGRESS = "PROGRESS"
		const val EVENT_TYPE_SEEK_COMPLETE = "SEEK_COMPLETE"
		const val EVENT_TYPE_REMOTE_NEXT = "REMOTE_NEXT"
		const val EVENT_TYPE_REMOTE_PREV = "REMOTE_PREV"
		const val EVENT_TYPE_PLAYBACK_SPEED_CHANGED = "PLAYBACK_SPEED_CHANGED"
		const val EVENT_TYPE_DIAGNOSTIC = "DIAGNOSTIC"

		// Trigger sources for seek events
		const val TRIGGER_SOURCE_USER = "USER"
		const val TRIGGER_SOURCE_SYSTEM = "SYSTEM"
	}

	init {
		AudioProController.setReactContext(reactContext)
		AudioProAmbientController.setReactContext(reactContext)
		reactContext.addLifecycleEventListener(this)
	}

	@ReactMethod
	fun play(track: ReadableMap, options: ReadableMap) {
		CoroutineScope(Dispatchers.Main).launch {
			AudioProController.play(track, options)
		}
	}

	@ReactMethod
	fun pause() {
		AudioProController.pause()
	}

	@ReactMethod
	fun resume() {
		AudioProController.resume()
	}

	@ReactMethod
	fun stop() {
		AudioProController.stop()
	}

	@ReactMethod
	fun seekTo(position: Double) {
		AudioProController.seekTo(position.toLong())
	}

	@ReactMethod
	fun seekForward(amount: Double) {
		AudioProController.seekForward(amount.toLong())
	}

	@ReactMethod
	fun seekBack(amount: Double) {
		AudioProController.seekBack(amount.toLong())
	}

	@ReactMethod
	fun setPlaybackSpeed(speed: Double) {
		AudioProController.setPlaybackSpeed(speed.toFloat())
	}

	@ReactMethod
	fun setVolume(volume: Double) {
		AudioProController.setVolume(volume.toFloat())
	}

	@ReactMethod
	fun clear() {
		AudioProController.clear()
	}

	@ReactMethod
	fun configure(options: ReadableMap) {
		CoroutineScope(Dispatchers.Main).launch {
			AudioProController.configure(options)
		}
	}

	@ReactMethod
	fun ambientPlay(options: ReadableMap) {
		AudioProAmbientController.ambientPlay(options)
	}

	@ReactMethod
	fun ambientStop() {
		AudioProAmbientController.ambientStop()
	}

	@ReactMethod
	fun ambientSetVolume(volume: Double) {
		AudioProAmbientController.ambientSetVolume(volume.toFloat())
	}

	@ReactMethod
	fun ambientPause() {
		AudioProAmbientController.ambientPause()
	}

	@ReactMethod
	fun ambientResume() {
		AudioProAmbientController.ambientResume()
	}

	@ReactMethod
	fun ambientSeekTo(positionMs: Double) {
		AudioProAmbientController.ambientSeekTo(positionMs.toLong())
	}

    // Keep: Required for RN built in Event Emitter Calls.
    @ReactMethod fun addListener(eventName: String) {}

    @ReactMethod fun removeListeners(count: Int) {}

	override fun getName(): String {
		return NAME
	}

	override fun onHostDestroy() {
		val hasActiveCatalyst = try { reactContext.hasActiveCatalystInstance() } catch (_: Exception) { false }
		Log.d("[react-native-audio-pro]", "onHostDestroy called, hasActiveCatalyst=$hasActiveCatalyst")
		try {
			AudioProPlaybackService.emitDiagnosticWithEnvelope("LIFECYCLE", mapOf(
				"callback" to "onHostDestroy",
				"hasActiveCatalystInstance" to hasActiveCatalyst
			))
		} catch (_: Exception) {}
		// Do NOT call clear() here — the host activity being destroyed does not mean
		// playback should stop. The foreground service owns playback lifetime.
		// Ambient audio can be stopped since it's UI-tied (only if catalyst is gone).
		if (!hasActiveCatalyst) {
			AudioProAmbientController.ambientStop()
		}
	}

	override fun onCatalystInstanceDestroy() {
		Log.d("AudioProModule", "React Native bridge is being destroyed")
		try {
			AudioProPlaybackService.emitDiagnosticWithEnvelope("LIFECYCLE", mapOf(
				"callback" to "onCatalystInstanceDestroy"
			))
		} catch (_: Exception) {}
		// Do NOT call clear() here — the bridge being destroyed does not mean
		// playback should stop. The foreground service owns playback lifetime.
		AudioProAmbientController.ambientStop()

		// Release the controller-side MediaBrowser (doesn't affect the service/playback).
		// This detaches listeners that would fire into a null context.
		// A fresh browser is created on next play().
		AudioProController.release()

		// Detach context references but leave the service running
		AudioProController.setReactContext(null)
		AudioProAmbientController.setReactContext(null)

		try {
			reactContext.removeLifecycleEventListener(this)
		} catch (e: Exception) {
			Log.e("AudioProModule", "Error removing lifecycle listener", e)
		}
		super.onCatalystInstanceDestroy()
	}

	override fun onHostResume() {}

	override fun onHostPause() {}
}
