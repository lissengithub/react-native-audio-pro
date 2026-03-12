import type { AudioProConfigureOptions } from './types';

/**
 * Default seek interval in milliseconds (30 seconds)
 */
export const DEFAULT_SEEK_MS = 30000;

/**
 * Content type for audio playback
 */
export enum AudioProContentType {
	/** Music content type */
	MUSIC = 'MUSIC',
	/** Speech content type */
	SPEECH = 'SPEECH',
}

/**
 * Possible states of the audio player
 */
export enum AudioProState {
	/** Initial state, no track loaded */
	IDLE = 'IDLE',
	/** Track is loaded but not playing */
	STOPPED = 'STOPPED',
	/** Track is being loaded */
	LOADING = 'LOADING',
	/** Track is currently playing */
	PLAYING = 'PLAYING',
	/** Track is paused */
	PAUSED = 'PAUSED',
	/** An error has occurred */
	ERROR = 'ERROR',
}

/**
 * Types of events that can be emitted by the audio player
 */
export enum AudioProEventType {
	/** Player state has changed */
	STATE_CHANGED = 'STATE_CHANGED',
	/** Playback progress update */
	PROGRESS = 'PROGRESS',
	/** Track has ended */
	TRACK_ENDED = 'TRACK_ENDED',
	/** Seek operation has completed */
	SEEK_COMPLETE = 'SEEK_COMPLETE',
	/** Playback speed has changed */
	PLAYBACK_SPEED_CHANGED = 'PLAYBACK_SPEED_CHANGED',
	/** Remote next button pressed */
	REMOTE_NEXT = 'REMOTE_NEXT',
	/** Remote previous button pressed */
	REMOTE_PREV = 'REMOTE_PREV',
	/** Playback error has occurred */
	PLAYBACK_ERROR = 'PLAYBACK_ERROR',
	/** Native diagnostic event */
	DIAGNOSTIC = 'DIAGNOSTIC',
}

/**
 * Sources for seek-complete events.
 */
export enum AudioProTriggerSource {
	/** Seek initiated by user or app code */
	USER = 'USER',
	/** Seek initiated by system or remote controls */
	SYSTEM = 'SYSTEM',
}

/**
 * Types of events that can be emitted by the ambient audio player
 */
export enum AudioProAmbientEventType {
	/** Ambient track has ended */
	AMBIENT_TRACK_ENDED = 'AMBIENT_TRACK_ENDED',
	/** Ambient audio error has occurred */
	AMBIENT_ERROR = 'AMBIENT_ERROR',
}

/**
 * Error codes for classifying playback errors
 */
export enum AudioProErrorCode {
	/** Network connection failed */
	NETWORK_DISCONNECTED = 1001,
	/** Network connection timed out */
	NETWORK_TIMEOUT = 1002,
	/** HTTP server error (5xx) or retryable HTTP codes (408, 429) */
	HTTP_SERVER_ERROR = 1003,
	/** HTTP client error (4xx, non-retryable) */
	HTTP_CLIENT_ERROR = 1004,
	/** Unspecified I/O error */
	IO_UNSPECIFIED = 1005,
}

/**
 * Check if an error code represents a transient (retryable) error
 */
export function isTransientErrorCode(code?: number): boolean {
	if (!code) return false;
	return [
		AudioProErrorCode.NETWORK_DISCONNECTED,
		AudioProErrorCode.NETWORK_TIMEOUT,
		AudioProErrorCode.HTTP_SERVER_ERROR,
		AudioProErrorCode.IO_UNSPECIFIED,
	].includes(code);
}

/**
 * Default skip interval in milliseconds (30 seconds)
 */
export const DEFAULT_SKIP_INTERVAL_MS = 30000;

/**
 * Default configuration options for the audio player
 */
export const DEFAULT_CONFIG: AudioProConfigureOptions = {
	/** Default content type */
	contentType: AudioProContentType.MUSIC,
	/** Whether debug logging is enabled */
	debug: false,
	/** Whether to include progress events in debug logs */
	debugIncludesProgress: false,
	/** Interval in milliseconds for progress events */
	progressIntervalMs: 1000,
	/** Whether to show next/previous controls */
	showNextPrevControls: true,
	/** Whether to show skip forward/back controls in notification */
	showSkipControls: false,
	/** Interval in milliseconds for skip forward/back actions */
	skipIntervalMs: DEFAULT_SKIP_INTERVAL_MS,
	/** Minimum buffer duration in milliseconds (Android only) */
	minBufferMs: 30_000,
	/** Maximum buffer duration in milliseconds (Android only) */
	maxBufferMs: 120_000,
	/** Buffer duration required to start playback in milliseconds (Android only) */
	bufferForPlaybackMs: 2_500,
	/** Buffer duration required after rebuffer to resume playback in milliseconds (Android only) */
	bufferForPlaybackAfterRebufferMs: 5_000,
	/** Maximum retry attempts for transient errors */
	maxRetries: 5,
	/** Base backoff delay in ms, multiplied by attempt number */
	retryBackoffMs: 1000,
};
