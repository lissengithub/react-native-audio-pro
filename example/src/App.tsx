import { useEffect } from 'react';
import { View, Button, StyleSheet, Image, SafeAreaView } from 'react-native';
import {
  load,
  play,
  pause,
  stop,
  addEventListener,
  removeEventListener,
  AudioProEvent,
  type AudioProLoadOptions,
} from 'react-native-audio-pro';

const App = () => {
  useEffect(() => {
    const onBuffering = addEventListener(AudioProEvent.BUFFERING, () =>
      console.log('Buffering...')
    );
    const onPlaying = addEventListener(AudioProEvent.PLAYING, (payload) =>
      console.log('Playing:', payload)
    );
    const onPaused = addEventListener(AudioProEvent.PAUSED, (payload) =>
      console.log('Paused:', payload)
    );
    const onFinished = addEventListener(AudioProEvent.FINISHED, () =>
      console.log('Finished playback')
    );
    const onError = addEventListener(AudioProEvent.ERROR, (error) =>
      console.log('Error:', error)
    );

    return () => {
      removeEventListener(onBuffering);
      removeEventListener(onPlaying);
      removeEventListener(onPaused);
      removeEventListener(onFinished);
      removeEventListener(onError);
    };
  }, []);

  const exampleLoadOptions: AudioProLoadOptions = {
    url: 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3',
    title: 'SoundHelix Song 1',
    artist: 'SoundHelix',
    artwork: 'https://via.placeholder.com/150',
  };

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.container}>
        <Image
          source={{ uri: exampleLoadOptions.artwork }}
          style={styles.artwork}
        />
        <Button title="Load Audio" onPress={() => load(exampleLoadOptions)} />
        <Button title="Play Audio" onPress={play} />
        <Button title="Pause Audio" onPress={pause} />
        <Button title="Stop Audio" onPress={stop} />
      </View>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  safe: {
    flex: 1,
  },
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 16,
  },
  artwork: {
    width: 150,
    height: 150,
    marginBottom: 16,
  },
});

export default App;
