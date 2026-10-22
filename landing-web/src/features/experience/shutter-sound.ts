let context: AudioContext | null = null;

/** 녹음 파일 없이 짧은 잡음 두 번으로 "찰-칵" 소리를 낸다. */
export function playShutterSound() {
  const AudioContextClass = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
  if (!AudioContextClass) return;
  context ??= new AudioContextClass();
  const audio = context;

  const burst = (delay: number, duration: number, frequency: number, gain: number) => {
    const buffer = audio.createBuffer(1, Math.floor(audio.sampleRate * duration), audio.sampleRate);
    const data = buffer.getChannelData(0);
    for (let i = 0; i < data.length; i++) data[i] = (Math.random() * 2 - 1) * (1 - i / data.length) ** 3;
    const source = audio.createBufferSource();
    const filter = audio.createBiquadFilter();
    const volume = audio.createGain();
    source.buffer = buffer;
    filter.type = "bandpass";
    filter.frequency.value = frequency;
    volume.gain.value = gain;
    source.connect(filter).connect(volume).connect(audio.destination);
    source.start(audio.currentTime + delay);
  };

  burst(0, 0.05, 2400, 0.8);
  burst(0.09, 0.07, 1300, 1);
}
