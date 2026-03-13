package dev.rnap.reactnativeaudiopro

import android.Manifest
import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.os.bundleOf
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.HttpDataSource.HttpDataSourceException
import androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.common.ForwardingSimpleBasePlayer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ControllerInfo

@UnstableApi
class GuardedPlayer(player: Player) : ForwardingSimpleBasePlayer(player) {

	override fun getState(): State {
		val state = super.getState()
		// Advertise next/prev commands so Bluetooth and external controllers
		// can send them, even though the player only has a single media item.
		val commands = state.availableCommands.buildUpon()
			.add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
			.add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
			.add(Player.COMMAND_SEEK_TO_NEXT)
			.add(Player.COMMAND_SEEK_TO_PREVIOUS)
			.build()
		return state.buildUpon()
			.setAvailableCommands(commands)
			.build()
	}

	override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
		if (playWhenReady) {
			val callerPackage = AudioProPlaybackService.currentMediaSession
				?.controllerForCurrentRequest?.packageName
			val ownPackage = AudioProPlaybackService.currentMediaSession
				?.token?.packageName
			val source = when {
				callerPackage == null -> "UNKNOWN"
				callerPackage == ownPackage -> "APP"
				else -> "REMOTE"
			}
			val playerState = AudioProPlaybackService.stateToString(player.playbackState)

			if (player.playbackState == Player.STATE_ENDED) {
				AudioProController.emitTrackEndedOnce(
					"GuardedPlayer.handleSetPlayWhenReady(STATE_ENDED) from ${callerPackage ?: "unknown"}",
					player.duration
				)
				AudioProPlaybackService.emitDiagnosticWithEnvelope("PLAY_INTENT", mapOf(
					"source" to source,
					"action" to "BLOCKED",
					"blockReason" to "TRACK_ENDED",
					"playerState" to playerState,
					"callerPackage" to (callerPackage ?: "unknown")
				))
				return Futures.immediateVoidFuture()
			}

			AudioProPlaybackService.emitDiagnosticWithEnvelope("PLAY_INTENT", mapOf(
				"source" to source,
				"action" to "ALLOWED",
				"playerState" to playerState,
				"callerPackage" to (callerPackage ?: "unknown")
			))
		}
		return super.handleSetPlayWhenReady(playWhenReady)
	}

	override fun handleSeek(
		mediaItemIndex: Int,
		positionMs: Long,
		seekCommand: Int
	): ListenableFuture<*> {
		when (seekCommand) {
			Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_NEXT -> {
				AudioProController.emitNext("GuardedPlayer.handleSeek(NEXT)")
				return Futures.immediateVoidFuture()
			}
			Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, Player.COMMAND_SEEK_TO_PREVIOUS -> {
				AudioProController.emitPrev("GuardedPlayer.handleSeek(PREV)")
				return Futures.immediateVoidFuture()
			}
		}
		return super.handleSeek(mediaItemIndex, positionMs, seekCommand)
	}
}

open class AudioProPlaybackService : MediaLibraryService() {

	private lateinit var mediaLibrarySession: MediaLibrarySession
	private lateinit var player: ExoPlayer
	private var wasPlayingBeforeFocusLoss: Boolean = false
	private var audioDeviceCallback: AudioDeviceCallback? = null

	companion object {
		private const val NOTIFICATION_ID = 789
		private const val CHANNEL_ID = "audio_pro_notification_channel_id"
		var currentMediaSession: MediaLibrarySession? = null
		private var diagnosticSeq = 0

		fun stateToString(state: Int): String = when (state) {
			Player.STATE_IDLE -> "IDLE"
			Player.STATE_BUFFERING -> "BUFFERING"
			Player.STATE_READY -> "READY"
			Player.STATE_ENDED -> "ENDED"
			else -> "UNKNOWN($state)"
		}

		fun emitDiagnosticWithEnvelope(tag: String, data: Map<String, Any?>) {
			val trackId = AudioProController.activeTrack?.getString("id") ?: "unknown"
			val envelope = mutableMapOf<String, Any?>(
				"ts" to System.currentTimeMillis(),
				"seq" to diagnosticSeq++,
				"trackId" to trackId,
				"route" to getAudioRouteStatic()
			)
			// Merge event-specific data, sanitizing negative Long values for duration fields
			for ((key, value) in data) {
				if (value is Long && value < 0 && (key.contains("duration", ignoreCase = true) || key.contains("Duration") || key == "durationMs" || key == "positionMs")) {
					envelope[key] = 0L
				} else {
					envelope[key] = value
				}
			}
			AudioProController.emitDiagnostic(tag, envelope)
		}

		private var audioManagerRef: AudioManager? = null

		private fun getAudioRouteStatic(): Map<String, String> {
			val am = audioManagerRef ?: return mapOf("type" to "UNKNOWN", "name" to "Unknown")
			return getAudioRouteFromManager(am)
		}

		private fun getAudioRouteFromManager(am: AudioManager): Map<String, String> {
			val type: String
			val name: String
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
				val attrs = AudioAttributes.Builder()
					.setUsage(C.USAGE_MEDIA)
					.setContentType(AudioProController.settingAudioContentType)
					.build()
				val devices = am.getAudioDevicesForAttributes(attrs.audioAttributesV21.audioAttributes)
				val device = devices.firstOrNull()
				type = device?.let { mapDeviceType(it.type) } ?: "UNKNOWN"
				name = device?.productName?.toString() ?: "Unknown"
			} else {
				@Suppress("DEPRECATION")
				type = when {
					am.isBluetoothA2dpOn -> "BLUETOOTH_A2DP"
					am.isBluetoothScoOn -> "BLUETOOTH_HFP"
					am.isWiredHeadsetOn -> "WIRED_HEADSET"
					am.isSpeakerphoneOn -> "SPEAKER"
					else -> "SPEAKER"
				}
				name = type
			}
			return mapOf("type" to type, "name" to name)
		}

		private fun mapDeviceType(type: Int): String = when (type) {
			AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "BLUETOOTH_A2DP"
			AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "BLUETOOTH_HFP"
			AudioDeviceInfo.TYPE_WIRED_HEADSET -> "WIRED_HEADSET"
			AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "WIRED_HEADPHONES"
			AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "SPEAKER"
			AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "EARPIECE"
			AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> "USB"
			AudioDeviceInfo.TYPE_DOCK -> "LINE_OUT"
			else -> {
				if (Build.VERSION.SDK_INT >= 31 && type == AudioDeviceInfo.TYPE_BLE_HEADSET) "BLUETOOTH_LE"
				else "UNKNOWN"
			}
		}
	}

	/**
	 * Brings app to foreground when notification or session is tapped.
	 *
	 * This method is used by the notification and session to provide an intent that brings the app
	 * to the foreground. Typically, this intent will launch the main activity with appropriate flags
	 * to avoid creating multiple instances.
	 *
	 * If null is returned, [MediaSession.setSessionActivity] is not set by the service.
	 */
	fun getSessionActivityIntent(): PendingIntent? {
		val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
			action = android.content.Intent.ACTION_MAIN
			addCategory(android.content.Intent.CATEGORY_LAUNCHER)
			flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
				android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
		}
		return launchIntent?.let {
			PendingIntent.getActivity(
				this,
				0,
				it,
				PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
			)
		}
	}

	/**
	 * Creates the library session callback to implement the domain logic. Can be overridden to return
	 * an alternative callback, for example a subclass of [AudioProMediaLibrarySessionCallback].
	 *
	 * This method is called when the session is built by the [AudioProPlaybackService].
	 */
	@OptIn(UnstableApi::class)
	protected open fun createLibrarySessionCallback(): MediaLibrarySession.Callback {
		return AudioProMediaLibrarySessionCallback()
	}

	@OptIn(UnstableApi::class) // MediaSessionService.setListener
	override fun onCreate() {
		super.onCreate()
		setListener(MediaSessionServiceListener())
		initializeSessionAndPlayer()

		// Register AudioDeviceCallback for route change diagnostics
		val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
		audioManagerRef = am
		val callback = object : AudioDeviceCallback() {
			override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
				val outputDevices = addedDevices.filter { it.isSink }
				if (outputDevices.isNotEmpty()) {
					emitDiagnosticWithEnvelope("AUDIO_ROUTE_CHANGED", mapOf(
						"reason" to "NEW_DEVICE"
					))
				}
			}
			override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
				val outputDevices = removedDevices.filter { it.isSink }
				if (outputDevices.isNotEmpty()) {
					emitDiagnosticWithEnvelope("AUDIO_ROUTE_CHANGED", mapOf(
						"reason" to "DEVICE_REMOVED"
					))
				}
			}
		}
		audioDeviceCallback = callback
		am.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
	}

	override fun onGetSession(controllerInfo: ControllerInfo): MediaLibrarySession {
		return mediaLibrarySession
	}

	private val playbackListener = object : Player.Listener {
		override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
			if (!::player.isInitialized) return
			updateForegroundState(player)

			val reasonStr = when (reason) {
				Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST -> "USER_REQUEST"
				Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS -> "AUDIO_FOCUS_LOSS"
				Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> "BECOMING_NOISY"
				Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE -> "REMOTE"
				Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM -> "END_OF_MEDIA_ITEM"
				else -> "UNKNOWN($reason)"
			}

			val positionMs = player.currentPosition.let { if (it < 0) 0L else it }
			val durationMs = player.duration.let { if (it < 0 || it == C.TIME_UNSET) 0L else it }

			emitDiagnosticWithEnvelope("PLAYBACK_STATE_CHANGE", mapOf(
				"state" to Companion.stateToString(player.playbackState),
				"playWhenReady" to playWhenReady,
				"reason" to reasonStr,
				"positionMs" to positionMs,
				"durationMs" to durationMs
			))

			// INTERRUPTION tracking
			if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS && !playWhenReady) {
				wasPlayingBeforeFocusLoss = true
				val hasTrackEnded = player.playbackState == Player.STATE_ENDED
				emitDiagnosticWithEnvelope("INTERRUPTION", mapOf(
					"type" to "BEGAN",
					"wasPlaying" to true,
					"hasTrackEnded" to hasTrackEnded
				))
			} else if (wasPlayingBeforeFocusLoss && playWhenReady &&
				(reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST || reason == Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE)) {
				val hasTrackEnded = player.playbackState == Player.STATE_ENDED
				emitDiagnosticWithEnvelope("INTERRUPTION", mapOf(
					"type" to "ENDED",
					"wasPlaying" to true,
					"shouldResume" to true,
					"willResume" to true,
					"hasTrackEnded" to hasTrackEnded
				))
				wasPlayingBeforeFocusLoss = false
			}
		}

		override fun onPlaybackStateChanged(playbackState: Int) {
			if (!::player.isInitialized) return

			val positionMs = player.currentPosition.let { if (it < 0) 0L else it }
			val durationMs = player.duration.let { if (it < 0 || it == C.TIME_UNSET) 0L else it }

			if (playbackState == Player.STATE_ENDED) {
				// Emit TRACK_DID_END before dedup/emitTrackEndedOnce
				emitDiagnosticWithEnvelope("TRACK_DID_END", mapOf(
					"positionMs" to positionMs,
					"durationMs" to durationMs
				))

				player.playWhenReady = false
				AudioProController.emitTrackEndedOnce(
					"service.onPlaybackStateChanged(STATE_ENDED)",
					player.duration
				)
			}

			updateForegroundState(player)
			emitDiagnosticWithEnvelope("PLAYBACK_STATE_CHANGE", mapOf(
				"state" to Companion.stateToString(playbackState),
				"playWhenReady" to player.playWhenReady,
				"positionMs" to positionMs,
				"durationMs" to durationMs
			))
		}
	}

	// stateToString is now in the companion object

	/**
	 * Called when the task is removed from the recent tasks list
	 * This happens when the user swipes away the app from the recent apps list
	 */
	override fun onTaskRemoved(rootIntent: android.content.Intent?) {
		android.util.Log.d("AudioProPlaybackService", "Task removed, stopping service")

		// Force stop playback and release resources
		try {
			val hasSession = ::mediaLibrarySession.isInitialized
			if (hasSession) {
				mediaLibrarySession.player.stop()
			}
			if (::player.isInitialized) {
				player.removeListener(playbackListener)
				player.release()
			}
			if (hasSession) {
				mediaLibrarySession.release()
			}
		} catch (e: Exception) {
			android.util.Log.e("AudioProPlaybackService", "Error stopping playback", e)
		}

		// Remove notification and stop service
		removeNotificationAndStopService()

		super.onTaskRemoved(rootIntent)
	}

	// MediaSession.setSessionActivity
	// MediaSessionService.clearListener
	@OptIn(UnstableApi::class)
	override fun onDestroy() {
		android.util.Log.d("AudioProPlaybackService", "Service being destroyed")
		currentMediaSession = null

		// Unregister audio device callback
		audioDeviceCallback?.let { callback ->
			val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
			am.unregisterAudioDeviceCallback(callback)
			audioDeviceCallback = null
		}
		audioManagerRef = null

		// Make sure to release all resources
		try {
			val hasSession = ::mediaLibrarySession.isInitialized
			if (hasSession) {
				// Stop playback first
				mediaLibrarySession.player.stop()
			}
			if (::player.isInitialized) {
				player.removeListener(playbackListener)
				player.release()
			}
			if (hasSession) {
				// Release session after tearing down the player
				mediaLibrarySession.release()
			}
			clearListener()
		} catch (e: Exception) {
			android.util.Log.e("AudioProPlaybackService", "Error during service destruction", e)
		}

		// Remove notification
		removeNotificationAndStopService()

		super.onDestroy()
	}

	/**
	 * Helper method to remove notification and stop the service
	 * Centralizes the notification removal and service stopping logic
	 */
	private fun removeNotificationAndStopService() {
		try {
			// Remove notification directly
			val notificationManager =
				getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
			notificationManager.cancel(NOTIFICATION_ID)
			lastNotificationOngoing = null

			// Stop foreground service - handle API level differences
			stopForegroundAndRemove()

			// Stop the service
			stopSelf()
		} catch (e: Exception) {
			android.util.Log.e("AudioProPlaybackService", "Error stopping service", e)
		}
	}

	@OptIn(UnstableApi::class)
	private fun initializeSessionAndPlayer() {
		// Create a composite data source factory that can handle both HTTP and file URIs
		val dataSourceFactory = object : DataSource.Factory {
			override fun createDataSource(): DataSource {
				// Create HTTP data source factory with custom headers if available
				val httpDataSourceFactory = DefaultHttpDataSource.Factory()

				// Apply custom headers if they exist
				AudioProController.headersAudio?.let { headers ->
					if (headers.isNotEmpty()) {
						httpDataSourceFactory.setDefaultRequestProperties(headers)
						android.util.Log.d(
							"AudioProPlaybackService",
							"Applied custom headers: $headers"
						)
					}
				}

				// Create a DefaultDataSource that will handle both HTTP and file URIs
				// It will delegate to FileDataSource for file:// URIs and to HttpDataSource for http(s):// URIs
				return DefaultDataSource.Factory(applicationContext, httpDataSourceFactory)
					.createDataSource()
			}
		}

		val loadErrorPolicy = object : DefaultLoadErrorHandlingPolicy() {
			override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
				val cause = loadErrorInfo.exception
				if (cause is HttpDataSourceException) {
					val httpCode = (cause as? InvalidResponseCodeException)?.responseCode ?: 0
					if (httpCode in 400..499 && httpCode != 408 && httpCode != 429) return C.TIME_UNSET
				}
				if (loadErrorInfo.errorCount <= AudioProController.maxRetries) {
					return loadErrorInfo.errorCount * AudioProController.retryBackoffMs
				}
				return C.TIME_UNSET
			}
		}

		val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
			.setLoadErrorHandlingPolicy(loadErrorPolicy)

		val loadControl = DefaultLoadControl.Builder()
			.setBufferDurationsMs(
				AudioProController.bufferMinMs,
				AudioProController.bufferMaxMs,
				AudioProController.bufferForPlaybackMs,
				AudioProController.bufferForPlaybackAfterRebufferMs,
			)
			.build()

		player =
			ExoPlayer.Builder(this)
				.setMediaSourceFactory(mediaSourceFactory)
				.setLoadControl(loadControl)
				.setWakeMode(C.WAKE_MODE_NETWORK)
				.setAudioAttributes(
					AudioAttributes.Builder()
						.setUsage(C.USAGE_MEDIA)
						.setContentType(AudioProController.settingAudioContentType)
						.build(),
					/* handleAudioFocus = */ true
				)
				.build()
		player.setHandleAudioBecomingNoisy(true)
		player.repeatMode = Player.REPEAT_MODE_OFF
		player.addAnalyticsListener(EventLogger())
		player.addListener(playbackListener)

		val guardedPlayer = GuardedPlayer(player)

		mediaLibrarySession =
			MediaLibrarySession.Builder(this, guardedPlayer, createLibrarySessionCallback())
				.also { builder -> getSessionActivityIntent()?.let { builder.setSessionActivity(it) } }
				.build()
				.also { mediaLibrarySession ->
					// Reserve only one set of controls per session: next/prev or skip, not both.
					// If both are true, prefer next/prev and log a warning.
					val extras = mutableMapOf<String, Boolean>()
					val showNextPrev = AudioProController.settingShowNextPrevControls
					val showSkip = AudioProController.settingShowSkipControls
					if (showNextPrev && showSkip) {
						android.util.Log.w(
							"AudioProPlaybackService",
							"Both settingShowNextPrevControls and settingShowSkipControls are true; only next/prev controls will be enabled for this session."
						)
					}
					// Only one set of controls can be active at a time.
					if (showNextPrev) {
						// Reserve next/prev (seek) slots and advertise only next/prev commands.
						extras[MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_PREV] = true
						extras[MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_NEXT] = true
					} else if (showSkip) {
						// Reserve skip/seek slots and advertise only fast forward/back commands.
						extras[MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_PREV] = true
						extras[MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_NEXT] = true
					}
					// If neither, explicitly clear all session extras to remove notification control slots.
					// This ensures that when neither next/prev nor skip controls are enabled,
					// no control slots are reserved and only play/pause is advertised.
					if (extras.isNotEmpty()) {
						mediaLibrarySession.setSessionExtras(bundleOf(*extras.entries.map { it.key to it.value }
							.toTypedArray()))
					} else {
						// Explicitly clear extras.
						mediaLibrarySession.setSessionExtras(bundleOf())
					}
				}
		currentMediaSession = mediaLibrarySession

		updateForegroundState(player)
	}

	@OptIn(UnstableApi::class) // MediaSessionService.Listener
	private inner class MediaSessionServiceListener : Listener {

		/**
		 * This method is only required to be implemented on Android 12 or above when an attempt is made
		 * by a media controller to resume playback when the {@link MediaSessionService} is in the
		 * background.
		 */
		@RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
		override fun onForegroundServiceStartNotAllowedException() {
			if (
				Build.VERSION.SDK_INT >= 33 &&
				checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
				PackageManager.PERMISSION_GRANTED
			) {
				// Notification permission is required but not granted
				return
			}
			showBackgroundNotification()
		}
	}

	private fun ensureNotificationChannel(notificationManagerCompat: NotificationManagerCompat) {
		val channel =
			NotificationChannel(
				CHANNEL_ID,
				"audio_pro_notification_channel",
				NotificationManager.IMPORTANCE_DEFAULT,
			)
		notificationManagerCompat.createNotificationChannel(channel)
	}

	private fun updateForegroundState(currentPlayer: Player) {
		if (currentPlayer.currentMediaItem == null) {
			if (isForegroundRunning) {
				detachForeground()
			}
			if (lastNotificationOngoing != null) {
				NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
				lastNotificationOngoing = null
			}
			return
		}

		// Stay foreground while a media item is loaded. Only detach when
		// media item is cleared (above) or on real teardown (onDestroy, etc).
		// This prevents notification flicker on play/pause and track transitions.
		if (!isForegroundRunning) {
			promoteToForeground(currentPlayer)
		}
	}

	private fun promoteToForeground(currentPlayer: Player) {
		val notificationManagerCompat = NotificationManagerCompat.from(this)
		ensureNotificationChannel(notificationManagerCompat)
		val notification = buildPlaybackNotification(currentPlayer, ongoing = true)
		try {
			startForeground(NOTIFICATION_ID, notification)
			isForegroundRunning = true
			lastNotificationOngoing = true
		} catch (exception: ForegroundServiceStartNotAllowedException) {
			android.util.Log.w(
				"AudioProPlaybackService",
				"Foreground start blocked; posting fallback notification",
				exception
			)
			postNotification(currentPlayer, ongoing = false)
		} catch (exception: Exception) {
			android.util.Log.e(
				"AudioProPlaybackService",
				"Unable to promote to foreground; posting fallback notification",
				exception
			)
			postNotification(currentPlayer, ongoing = false)
		}
	}

	private fun showBackgroundNotification() {
		val currentPlayer = if (::player.isInitialized) player else null
		postNotification(currentPlayer, ongoing = false)
	}

	private fun postNotification(currentPlayer: Player?, ongoing: Boolean) {
		val notificationManagerCompat = NotificationManagerCompat.from(this)
		ensureNotificationChannel(notificationManagerCompat)
		notificationManagerCompat.notify(
			NOTIFICATION_ID,
			buildPlaybackNotification(currentPlayer, ongoing)
		)
		lastNotificationOngoing = ongoing
	}

	private fun detachForeground() {
		if (!isForegroundRunning) {
			return
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			stopForeground(STOP_FOREGROUND_DETACH)
		} else {
			@Suppress("DEPRECATION")
			stopForeground(false)
		}
		isForegroundRunning = false
	}

	private fun stopForegroundAndRemove() {
		if (!isForegroundRunning) {
			return
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			stopForeground(STOP_FOREGROUND_REMOVE)
		} else {
			@Suppress("DEPRECATION")
			stopForeground(true)
		}
		isForegroundRunning = false
		lastNotificationOngoing = null
	}

	private fun buildPlaybackNotification(
		currentPlayer: Player?,
		ongoing: Boolean,
	): android.app.Notification {
		val metadata = currentPlayer?.mediaMetadata
		val title =
			metadata?.title?.toString()?.takeIf { it.isNotBlank() } ?: "Audio"
		val artist = metadata?.artist?.toString()
		val contentText = when {
			!artist.isNullOrBlank() -> artist
			currentPlayer == null -> if (ongoing) "Playing..." else "Playback ready"
			currentPlayer.playbackState == Player.STATE_BUFFERING -> "Buffering..."
			currentPlayer.playbackState == Player.STATE_READY && currentPlayer.playWhenReady -> "Playing..."
			currentPlayer.playbackState == Player.STATE_ENDED -> "Playback ended"
			else -> "Paused"
		}

		return NotificationCompat.Builder(this, CHANNEL_ID)
			.setSmallIcon(
				if (ongoing) android.R.drawable.ic_media_play else android.R.drawable.ic_dialog_info
			)
			.setContentTitle(title)
			.setContentText(contentText)
			.setOnlyAlertOnce(true)
			.setPriority(
				if (ongoing) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_DEFAULT
			)
			.setOngoing(ongoing)
			.setAutoCancel(!ongoing)
			.also { builder -> getSessionActivityIntent()?.let { builder.setContentIntent(it) } }
			.build()
	}

	private var isForegroundRunning: Boolean = false
	private var lastNotificationOngoing: Boolean? = null
}
