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

export type AudioProLoadOptions = {
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
