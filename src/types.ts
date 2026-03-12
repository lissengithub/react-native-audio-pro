import {
	AudioProTriggerSource,
	AudioProAmbientEventType,
	AudioProContentType,
	AudioProEventType,
	AudioProState,
} from './values';

// ==============================
// TRACK
// ==============================

export type AudioProArtwork = string;

export type AudioProTrack = {
	id: string;
	url: string;
	title: string;
	artwork?: AudioProArtwork;
	album?: string;
	artist?: string;
	[key: string]: unknown; // custom properties
};

// ==============================
// CONFIGURE OPTIONS
// ==============================

export type AudioProConfigureOptions = {
	contentType?: AudioProContentType;
	debug?: boolean;
	debugIncludesProgress?: boolean;
	progressIntervalMs?: number;
	showNextPrevControls?: boolean;
	showSkipControls?: boolean;
	skipIntervalMs?: number;
	/**
	 * @deprecated use skipIntervalMs instead
	 */
	skipInterval?: number;
	/** Minimum buffer duration in milliseconds (default: 30000) */
	minBufferMs?: number;
	/** Maximum buffer duration in milliseconds (default: 120000) */
	maxBufferMs?: number;
	/** Buffer duration required to start playback in milliseconds (default: 2500) */
	bufferForPlaybackMs?: number;
	/** Buffer duration required after rebuffer to resume playback in milliseconds (default: 5000) */
	bufferForPlaybackAfterRebufferMs?: number;
	/** Maximum number of retry attempts for transient errors (default: 5) */
	maxRetries?: number;
	/** Base backoff delay in ms, multiplied by attempt number (default: 1000) */
	retryBackoffMs?: number;
};

// ==============================
// PLAY OPTIONS
// ==============================

export type AudioProHeaders = {
	audio?: Record<string, string>;
	artwork?: Record<string, string>;
};

export type AudioProPlayOptions = {
	autoPlay?: boolean;
	headers?: AudioProHeaders;
	startTimeMs?: number;
};

// ==============================
// EVENTS
// ==============================

export type AudioProEventCallback = (event: AudioProEvent) => void;

export interface AudioProEvent {
	type: AudioProEventType;
	track: AudioProTrack | null; // Required for all events except REMOTE_NEXT and REMOTE_PREV
	payload?: {
		state?: AudioProState;
		position?: number;
		duration?: number;
		bufferedPosition?: number;
		error?: string;
		errorCode?: number;
		speed?: number;
		tag?: string;
		data?: Record<string, unknown>;
	};
}

export interface AudioProStateChangedPayload {
	state: AudioProState;
	position: number;
	duration: number;
}

export interface AudioProTrackEndedPayload {
	position: number;
	duration: number;
}

export interface AudioProPlaybackErrorPayload {
	error: string;
	errorCode?: number;
}

export interface AudioProProgressPayload {
	position: number;
	duration: number;
	bufferedPosition?: number;
}

export interface AudioProSeekCompletePayload {
	position: number;
	duration: number;
	/** Indicates who initiated the seek: user or system */
	triggeredBy: AudioProTriggerSource;
}

export interface AudioProPlaybackSpeedChangedPayload {
	speed: number;
}

// ==============================
// AMBIENT AUDIO
// ==============================

export interface AmbientAudioPlayOptions {
	url: string;
	loop?: boolean;
}

export type AudioProAmbientEventCallback = (event: AudioProAmbientEvent) => void;

export interface AudioProAmbientEvent {
	type: AudioProAmbientEventType;
	payload?: {
		error?: string;
	};
}

export interface AudioProAmbientErrorPayload {
	error: string;
}
