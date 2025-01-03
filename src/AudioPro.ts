import { AudioProEvent, type AudioProLoadOptions } from './types';

const eventListeners = new Map<AudioProEvent, Set<Function>>();

export const load = async (options: AudioProLoadOptions): Promise<void> => {
  console.log('Loading audio with options:', options);
};

export const play = async (): Promise<void> => {
  console.log('Playing audio');
};

export const pause = async (): Promise<void> => {
  console.log('Pausing audio');
};

export const stop = async (): Promise<void> => {
  console.log('Stopping and releasing resources');
};

export const addEventListener = <E extends AudioProEvent>(
  event: E,
  callback: (payload: any) => void
): { event: E; callback: Function } => {
  if (!eventListeners.has(event)) {
    eventListeners.set(event, new Set());
  }

  const listeners = eventListeners.get(event)!;
  listeners.add(callback);

  console.log(`Added listener for event: ${event}`);
  return { event, callback };
};

export const removeEventListener = ({
  event,
  callback,
}: {
  event: AudioProEvent;
  callback: Function;
}): void => {
  const listeners = eventListeners.get(event);
  if (listeners) {
    listeners.delete(callback);
    console.log(`Removed listener for event: ${event}`);
  }
};
