# react-native-audio-pro

A lightweight audio playback library for React Native. Laser-focused on delivering reliable, high-quality audio playback for remote streams on iOS and Android. No complicated features—just simple, effective audio playback.

---

## Features 🎧

- **🔗 Stream Remote Audio**: Play audio files from URLs with quick buffering.
- **🕶️ Background Playback**: Keep audio running when the app is minimized.
- **🔒 Lock Screen Controls**: Support play, pause, seek, and skip directly from the lock screen or notification center.
- **📡 Modern Native Components**: Utilizes the latest native APIs for iOS (15+) and Android (API 33+).
- **🛠️ Simple Integration**: Minimal API with TypeScript support for clean and easy development.
- **📊 Event Handling**: Subscribe to key playback events (e.g., play, pause, skip, seek).
---

## Requirements

- **React Native**: 0.71+ (modern version support).
- **Android**: API 33+ (latest audio/notification APIs).
- **iOS**: 15.1+ (React Native platform baseline).

---

## Installation

```bash
npm install react-native-audio-pro
```

---

## Platform-Specific Setup

### Android

1. **Gradle Configuration**:

   Ensure that your `build.gradle` is set to use SDK version 33 or higher.

   ```gradle
   // File: android/build.gradle
   buildscript {
       ext {
           minSdkVersion = 33
           compileSdkVersion = 33
           targetSdkVersion = 33
           // ...
       }
       // ...
   }
   ```

2. **Permissions**:

   Add the following permissions to your `AndroidManifest.xml`:

   ```xml
   <!-- File: android/app/src/main/AndroidManifest.xml -->
   <manifest xmlns:android="http://schemas.android.com/apk/res/android"
       package="com.yourapp">
       <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
       <uses-permission android:name="android.permission.WAKE_LOCK" />
       <!-- ... -->
   </manifest>
   ```

3. **ProGuard Rules**:

   If using ProGuard add the following rules:

   ```
   # File: android/app/proguard-rules.pro
   -keep class com.google.android.exoplayer2.** { *; }
   -dontwarn com.google.android.exoplayer2.**
   ```

### iOS

1. **Enable Background Modes**:

  - Configure your project to enable background audio playback:
    1. Open Xcode and select your project.
    2. Navigate to **Signing & Capabilities**.
    3. Add **Background Modes**.
    4. Check the option for **Audio, AirPlay, and Picture in Picture**.

2. **iOS Deployment Target**:

  - Set the minimum iOS version to at least iOS 15.0:
    1. Go to your project's settings in Xcode.
    2. Navigate to the **Deployment Info** section.
    3. Set the **iOS Deployment Target** to **iOS 15.0** or higher.

---

## Usage

See the `./example/` app!

### Importing the Module

```typescript
import AudioPro, { EventNames } from 'react-native-audio-pro';
```

### Playing Audio

```typescript jsx
const App = () => {

  const handlePlay = async () => {
      await AudioPro.play('https://example.com/audio.mp3');
  };

  return (
    <View>
      <Button title="Play Audio" onPress={handlePlay} />
    </View>
  );
};
```

### Handling Events

```typescript jsx
const App = () => {

  useEffect(() => {
    const onPlay = () => {
      console.log('Playback started');
    };

    const onPause = () => {
      console.log('Playback paused');
    };

    AudioPro.addEventListener(EventNames.ON_PLAY, onPlay);
    AudioPro.addEventListener(EventNames.ON_PAUSE, onPause);

    return () => {
      AudioPro.removeEventListener(EventNames.ON_PLAY, onPlay);
      AudioPro.removeEventListener(EventNames.ON_PAUSE, onPause);
    };
  }, []);

  // ... rest of the component
};
```

### Seeking

```typescript jsx
const App = () => {

  const handleSeekTo = async () => {
    await AudioPro.seekTo(60);
  };

  const handleSeekBy = async () => {
    await AudioPro.seekBy(15);
  };

  return (
    <View>
      <Button title="Seek to 1:00" onPress={handleSeekTo} />
      <Button title="Seek Forward 15s" onPress={handleSeekBy} />
    </View>
  );
};
```

---

## API Reference

### Methods

| Method | Description | Parameters |
|--------|-------------|------------|
| `play(url: string, headers?: Headers): Promise<void>` | Starts streaming the audio from the provided URL. | **url**: The URL of the audio file (must be HTTPS). **headers**: Optional HTTP headers for authenticated streams. |
| `pause(): Promise<void>` | Pauses the audio playback. | None |
| `resume(): Promise<void>` | Resumes the audio playback. | None |
| `stop(): Promise<void>` | Stops the audio playback and releases resources. | None |
| `seekTo(seconds: number): Promise<void>` | Seeks to a specific time in the audio track. | **seconds**: The position in seconds to seek to. |
| `seekBy(seconds: number): Promise<void>` | Seeks forward or backward by a specific amount of time. | **seconds**: The number of seconds to seek by (negative values seek backward). |
| `addEventListener(eventName: EventNames, listener: (...args: any[]) => void): void` | Adds an event listener for playback events. | **eventName**: The event to listen for (use `EventNames` enum). **listener**: The callback function to handle the event. |
| `removeEventListener(eventName: EventNames, listener: (...args: any[]) => void): void` | Removes a previously added event listener. | **eventName**: The event to stop listening for. **listener**: The previously added callback function. |
---

### Events

Use the `EventNames` enum to subscribe to the following events:

- `ON_PLAY`: Emitted when playback starts or resumes.
- `ON_PAUSE`: Emitted when playback is paused.
- `ON_BUFFERING`: Emitted when buffering starts or ends.
- `ON_LOADING`: Emitted when the player is loading the track.
- `ON_ERROR`: Emitted when an error occurs.
- `ON_FINISHED`: Emitted when the track finishes playing.
- `ON_DURATION_RECEIVED`: Emitted when the track duration is available.
- `ON_SEEK`: Emitted when a seek operation completes.
- `ON_SKIP_TO_NEXT`: Emitted when the user requests to skip to the next track.
- `ON_SKIP_TO_PREVIOUS`: Emitted when the user requests to skip to the previous track.

---

## Troubleshooting

- **Playback Doesn't Start**: Check that the audio URL is correct and uses HTTPS. Also, ensure that the required permissions and capabilities are set up.
- **Events Not Emitting**: Verify that event listeners are properly added and that the event names match the `EventNames` enum.
- **App Crashes on Android**: Ensure that the `minSdkVersion` is set to 33 or higher and that all dependencies are correctly installed.

---

## Contributing

See the [contributing guide](CONTRIBUTING.md) to learn how to contribute to the repository and the development workflow.

---

Made with [create-react-native-library](https://github.com/callstack/react-native-builder-bob)
