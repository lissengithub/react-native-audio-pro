import { NativeModules, NativeEventEmitter } from 'react-native';
import { AudioProEvent, type AudioProMediaFile } from './types';

const { AudioPro } = NativeModules;
const eventEmitter = new NativeEventEmitter(AudioPro);

export const load = async (mediaFile: AudioProMediaFile): Promise<void> => {
  await AudioPro.load(mediaFile);
};

export const play = async (): Promise<void> => {
  await AudioPro.play();
};

export const pause = async (): Promise<void> => {
  await AudioPro.pause();
};

export const stop = async (): Promise<void> => {
  await AudioPro.stop();
};

export const addEventListener = <E extends AudioProEvent>(
  event: E,
  callback: (payload: any) => void
): { remove: () => void } => {
  const subscription = eventEmitter.addListener(event, callback);

  return {
    remove: () => subscription.remove(),
  };
};
