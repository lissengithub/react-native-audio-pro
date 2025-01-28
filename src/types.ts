// src/types.ts

export enum AudioProEvent {
  BUFFERING = 'BUFFERING',
  PLAYING = 'PLAYING',
  PAUSED = 'PAUSED',
  FINISHED = 'FINISHED',
  ERROR = 'ERROR',
  REMOTE_SEEK = 'REMOTE_SEEK',
  REMOTE_SKIP_NEXT = 'REMOTE_SKIP_NEXT',
  REMOTE_SKIP_PREVIOUS = 'REMOTE_SKIP_PREVIOUS',
  PROGRESS = 'PROGRESS',
}

export type AudioProMediaFile = {
  url: string;
  title: string;
  artist: string;
  album?: string;
  artwork: string;
};

export enum AudioProErrorCode {
  NETWORK_ERROR = 'NETWORK_ERROR',
  DECODE_ERROR = 'DECODE_ERROR',
  UNSUPPORTED_FORMAT = 'UNSUPPORTED_FORMAT',
  UNKNOWN_ERROR = 'UNKNOWN_ERROR',
}

export type EventPayloads = {
  [AudioProEvent.BUFFERING]: { progress: number };
  [AudioProEvent.PLAYING]: { currentTime: number; duration: number };
  [AudioProEvent.PAUSED]: { currentTime: number };
  [AudioProEvent.FINISHED]: {};
  [AudioProEvent.ERROR]: { code: AudioProErrorCode; message: string };
  [AudioProEvent.REMOTE_SEEK]: { position: number };
  [AudioProEvent.REMOTE_SKIP_NEXT]: {};
  [AudioProEvent.REMOTE_SKIP_PREVIOUS]: {};
  [AudioProEvent.PROGRESS]: { currentTime: number; duration: number };
};
