// src/NativeAudioPro.ts

import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';
import type { AudioProMediaFile } from './types';

export interface Spec extends TurboModule {
  load(mediaFile: AudioProMediaFile): Promise<void>;
  play(): Promise<void>;
  pause(): Promise<void>;
  stop(): Promise<void>;
  // Add other native methods here as needed
}

export default TurboModuleRegistry.getEnforcing<Spec>('AudioPro');
