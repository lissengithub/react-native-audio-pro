"use strict";

/**
 * Default seek interval in milliseconds (30 seconds)
 */
export const DEFAULT_SEEK_MS = 30000;

/**
 * Content type for audio playback
 */
export let AudioProContentType = /*#__PURE__*/function (AudioProContentType) {
  /** Music content type */
  AudioProContentType["MUSIC"] = "MUSIC";
  /** Speech content type */
  AudioProContentType["SPEECH"] = "SPEECH";
  return AudioProContentType;
}({});

/**
 * Possible states of the audio player
 */
export let AudioProState = /*#__PURE__*/function (AudioProState) {
  /** Initial state, no track loaded */
  AudioProState["IDLE"] = "IDLE";
  /** Track is loaded but not playing */
  AudioProState["STOPPED"] = "STOPPED";
  /** Track is being loaded */
  AudioProState["LOADING"] = "LOADING";
  /** Track is currently playing */
  AudioProState["PLAYING"] = "PLAYING";
  /** Track is paused */
  AudioProState["PAUSED"] = "PAUSED";
  /** An error has occurred */
  AudioProState["ERROR"] = "ERROR";
  return AudioProState;
}({});

/**
 * Types of events that can be emitted by the audio player
 */
export let AudioProEventType = /*#__PURE__*/function (AudioProEventType) {
  /** Player state has changed */
  AudioProEventType["STATE_CHANGED"] = "STATE_CHANGED";
  /** Playback progress update */
  AudioProEventType["PROGRESS"] = "PROGRESS";
  /** Track has ended */
  AudioProEventType["TRACK_ENDED"] = "TRACK_ENDED";
  /** Seek operation has completed */
  AudioProEventType["SEEK_COMPLETE"] = "SEEK_COMPLETE";
  /** Playback speed has changed */
  AudioProEventType["PLAYBACK_SPEED_CHANGED"] = "PLAYBACK_SPEED_CHANGED";
  /** Remote next button pressed */
  AudioProEventType["REMOTE_NEXT"] = "REMOTE_NEXT";
  /** Remote previous button pressed */
  AudioProEventType["REMOTE_PREV"] = "REMOTE_PREV";
  /** Playback error has occurred */
  AudioProEventType["PLAYBACK_ERROR"] = "PLAYBACK_ERROR";
  /** Native diagnostic event */
  AudioProEventType["DIAGNOSTIC"] = "DIAGNOSTIC";
  return AudioProEventType;
}({});

/**
 * Sources for seek-complete events.
 */
export let AudioProTriggerSource = /*#__PURE__*/function (AudioProTriggerSource) {
  /** Seek initiated by user or app code */
  AudioProTriggerSource["USER"] = "USER";
  /** Seek initiated by system or remote controls */
  AudioProTriggerSource["SYSTEM"] = "SYSTEM";
  return AudioProTriggerSource;
}({});

/**
 * Types of events that can be emitted by the ambient audio player
 */
export let AudioProAmbientEventType = /*#__PURE__*/function (AudioProAmbientEventType) {
  /** Ambient track has ended */
  AudioProAmbientEventType["AMBIENT_TRACK_ENDED"] = "AMBIENT_TRACK_ENDED";
  /** Ambient audio error has occurred */
  AudioProAmbientEventType["AMBIENT_ERROR"] = "AMBIENT_ERROR";
  return AudioProAmbientEventType;
}({});

/**
 * Error codes for classifying playback errors
 */
export let AudioProErrorCode = /*#__PURE__*/function (AudioProErrorCode) {
  /** Network connection failed */
  AudioProErrorCode[AudioProErrorCode["NETWORK_DISCONNECTED"] = 1001] = "NETWORK_DISCONNECTED";
  /** Network connection timed out */
  AudioProErrorCode[AudioProErrorCode["NETWORK_TIMEOUT"] = 1002] = "NETWORK_TIMEOUT";
  /** HTTP server error (5xx) or retryable HTTP codes (408, 429) */
  AudioProErrorCode[AudioProErrorCode["HTTP_SERVER_ERROR"] = 1003] = "HTTP_SERVER_ERROR";
  /** HTTP client error (4xx, non-retryable) */
  AudioProErrorCode[AudioProErrorCode["HTTP_CLIENT_ERROR"] = 1004] = "HTTP_CLIENT_ERROR";
  /** Unspecified I/O error */
  AudioProErrorCode[AudioProErrorCode["IO_UNSPECIFIED"] = 1005] = "IO_UNSPECIFIED";
  return AudioProErrorCode;
}({});

/**
 * Check if an error code represents a transient (retryable) error
 */
export function isTransientErrorCode(code) {
  if (!code) return false;
  return [AudioProErrorCode.NETWORK_DISCONNECTED, AudioProErrorCode.NETWORK_TIMEOUT, AudioProErrorCode.HTTP_SERVER_ERROR, AudioProErrorCode.IO_UNSPECIFIED].includes(code);
}

/**
 * Default skip interval in milliseconds (30 seconds)
 */
export const DEFAULT_SKIP_INTERVAL_MS = 30000;

/**
 * Default configuration options for the audio player
 */
export const DEFAULT_CONFIG = {
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
  retryBackoffMs: 1000
};
//# sourceMappingURL=values.js.map