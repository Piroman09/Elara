export interface AudioEngineCallbacks {
  onTimeUpdate?: (currentTime: number, duration: number) => void;
  onEnded?: () => void;
  onError?: (err: any) => void;
  onPlay?: () => void;
  onPause?: () => void;
  onLoadedMetadata?: (duration: number) => void;
}

class AudioEngine {
  private audio: HTMLAudioElement;
  private audioContext: AudioContext | null = null;
  private sourceNode: MediaElementAudioSourceNode | null = null;
  private analyserNode: AnalyserNode | null = null;
  private gainNode: GainNode | null = null;
  private pannerNode: StereoPannerNode | null = null;
  private eqFilters: BiquadFilterNode[] = [];
  private reverbNode: ConvolverNode | null = null;
  private reverbGainNode: GainNode | null = null;
  private dryGainNode: GainNode | null = null;
  private callbacks: AudioEngineCallbacks = {};
  private isInitialized = false;

  // 8D Audio Rotation State
  private is8DActive = false;
  private orbitalSpeed = 0.5; // rotations per second
  private pannerAngle = 0;
  private pannerAnimationId: number | null = null;

  // Procedural Ambient Sound Nodes
  private ambientGains: {
    rain: GainNode | null;
    fire: GainNode | null;
    space: GainNode | null;
    vinyl: GainNode | null;
    cafe: GainNode | null;
  } = { rain: null, fire: null, space: null, vinyl: null, cafe: null };

  private ambientSources: any[] = [];

  constructor() {
    this.audio = new Audio();
    this.audio.crossOrigin = 'anonymous';
    this.audio.preload = 'metadata';

    this.setupAudioListeners();
  }

  private setupAudioListeners() {
    this.audio.addEventListener('timeupdate', () => {
      this.callbacks.onTimeUpdate?.(this.audio.currentTime, this.audio.duration || 0);
    });

    this.audio.addEventListener('ended', () => {
      this.callbacks.onEnded?.();
    });

    this.audio.addEventListener('play', () => {
      this.callbacks.onPlay?.();
    });

    this.audio.addEventListener('pause', () => {
      this.callbacks.onPause?.();
    });

    this.audio.addEventListener('loadedmetadata', () => {
      this.callbacks.onLoadedMetadata?.(this.audio.duration || 0);
    });

    this.audio.addEventListener('error', (e) => {
      console.warn('Audio element error or CORS restriction:', e);
      this.callbacks.onError?.(e);
    });
  }

  public initWebAudio(): boolean {
    if (this.isInitialized && this.audioContext) {
      if (this.audioContext.state === 'suspended') {
        this.audioContext.resume();
      }
      return true;
    }

    try {
      const AudioCtx = window.AudioContext || (window as any).webkitAudioContext;
      if (!AudioCtx) return false;

      this.audioContext = new AudioCtx();
      this.analyserNode = this.audioContext.createAnalyser();
      this.analyserNode.fftSize = 256;
      this.analyserNode.smoothingTimeConstant = 0.8;

      this.gainNode = this.audioContext.createGain();

      // Setup 8D Stereo Panner Node if supported
      if (this.audioContext.createStereoPanner) {
        this.pannerNode = this.audioContext.createStereoPanner();
      }

      // Setup Reverb Convolver + Dry/Wet Mix
      this.reverbNode = this.audioContext.createConvolver();
      this.reverbGainNode = this.audioContext.createGain();
      this.dryGainNode = this.audioContext.createGain();

      this.reverbGainNode.gain.value = 0; // default dry
      this.dryGainNode.gain.value = 1.0;
      this.generateReverbImpulse(1.5, 2.0); // synthetic impulse response

      // Setup 5-Band Equalizer: 60Hz, 230Hz, 910Hz, 3600Hz, 14000Hz
      const frequencies = [60, 230, 910, 3600, 14000];
      const types: BiquadFilterType[] = ['lowshelf', 'peaking', 'peaking', 'peaking', 'highshelf'];

      this.eqFilters = frequencies.map((freq, i) => {
        const filter = this.audioContext!.createBiquadFilter();
        filter.type = types[i];
        filter.frequency.value = freq;
        filter.gain.value = 0;
        if (filter.type === 'peaking') {
          filter.Q.value = 1.0;
        }
        return filter;
      });

      // Chain audio element -> source -> filter chain -> analyser
      this.sourceNode = this.audioContext.createMediaElementSource(this.audio);

      let lastNode: AudioNode = this.sourceNode;
      for (const filter of this.eqFilters) {
        lastNode.connect(filter);
        lastNode = filter;
      }

      lastNode.connect(this.analyserNode);

      // Connect to Dry/Wet reverb path
      this.analyserNode.connect(this.dryGainNode);
      this.analyserNode.connect(this.reverbNode);
      this.reverbNode.connect(this.reverbGainNode);

      // Re-converge
      const mixer = this.audioContext.createGain();
      this.dryGainNode.connect(mixer);
      this.reverbGainNode.connect(mixer);

      if (this.pannerNode) {
        mixer.connect(this.pannerNode);
        this.pannerNode.connect(this.gainNode);
      } else {
        mixer.connect(this.gainNode);
      }

      this.gainNode.connect(this.audioContext.destination);

      // Initialize Ambient Synthesizers
      this.setupAmbientSoundscapeSynthesizers();

      this.isInitialized = true;
      return true;
    } catch (err) {
      console.warn('Web Audio initialization note:', err);
      return false;
    }
  }

  // Generates smooth synthetic room reverb impulse response
  private generateReverbImpulse(duration: number, decay: number) {
    if (!this.audioContext || !this.reverbNode) return;
    const rate = this.audioContext.sampleRate;
    const length = Math.floor(rate * duration);
    const impulse = this.audioContext.createBuffer(2, length, rate);
    const left = impulse.getChannelData(0);
    const right = impulse.getChannelData(1);

    for (let i = 0; i < length; i++) {
      const n = i / length;
      const factor = Math.exp(-n * decay);
      left[i] = (Math.random() * 2 - 1) * factor;
      right[i] = (Math.random() * 2 - 1) * factor;
    }

    this.reverbNode.buffer = impulse;
  }

  public setRoomReverb(type: 'studio' | 'cathedral' | 'cyber-bunker' | 'vinyl-lounge') {
    if (!this.reverbGainNode || !this.dryGainNode) return;

    switch (type) {
      case 'studio':
        this.reverbGainNode.gain.setTargetAtTime(0.0, this.audioContext?.currentTime || 0, 0.05);
        this.dryGainNode.gain.setTargetAtTime(1.0, this.audioContext?.currentTime || 0, 0.05);
        break;
      case 'cathedral':
        this.generateReverbImpulse(3.5, 1.8);
        this.reverbGainNode.gain.setTargetAtTime(0.55, this.audioContext?.currentTime || 0, 0.05);
        this.dryGainNode.gain.setTargetAtTime(0.8, this.audioContext?.currentTime || 0, 0.05);
        break;
      case 'cyber-bunker':
        this.generateReverbImpulse(1.8, 3.2);
        this.reverbGainNode.gain.setTargetAtTime(0.4, this.audioContext?.currentTime || 0, 0.05);
        this.dryGainNode.gain.setTargetAtTime(0.85, this.audioContext?.currentTime || 0, 0.05);
        break;
      case 'vinyl-lounge':
        this.generateReverbImpulse(1.2, 4.0);
        this.reverbGainNode.gain.setTargetAtTime(0.25, this.audioContext?.currentTime || 0, 0.05);
        this.dryGainNode.gain.setTargetAtTime(0.9, this.audioContext?.currentTime || 0, 0.05);
        break;
    }
  }

  // 8D Orbital Audio Engine
  public set8DSpatialAudio(enabled: boolean, speed = 0.5) {
    this.is8DActive = enabled;
    this.orbitalSpeed = speed;

    if (!enabled) {
      if (this.pannerAnimationId) {
        cancelAnimationFrame(this.pannerAnimationId);
        this.pannerAnimationId = null;
      }
      if (this.pannerNode) {
        this.pannerNode.pan.setTargetAtTime(0, this.audioContext?.currentTime || 0, 0.1);
      }
      return;
    }

    if (!this.pannerAnimationId) {
      this.run8DOrbitalLoop();
    }
  }

  private run8DOrbitalLoop() {
    const loop = () => {
      if (this.is8DActive && this.pannerNode && this.audioContext) {
        this.pannerAngle += this.orbitalSpeed * 0.03;
        // Smooth sine pan oscillation (-0.95 to +0.95)
        const panValue = Math.sin(this.pannerAngle) * 0.95;
        this.pannerNode.pan.setValueAtTime(panValue, this.audioContext.currentTime);
        this.pannerAnimationId = requestAnimationFrame(loop);
      } else {
        this.pannerAnimationId = null;
      }
    };
    loop();
  }

  // Procedural Web Audio Ambient Soundscapes (Rain, Fire, Cosmic Drone, Vinyl, Cafe)
  private setupAmbientSoundscapeSynthesizers() {
    if (!this.audioContext) return;
    const ctx = this.audioContext;

    // Buffer generator helper
    const createNoiseBuffer = (seconds = 5) => {
      const buffer = ctx.createBuffer(1, ctx.sampleRate * seconds, ctx.sampleRate);
      const data = buffer.getChannelData(0);
      for (let i = 0; i < data.length; i++) {
        data[i] = Math.random() * 2 - 1;
      }
      return buffer;
    };

    const noiseBuffer = createNoiseBuffer();

    // 1. Rain Synthesizer (Filtered Pink Noise with modulation)
    const rainGain = ctx.createGain();
    rainGain.gain.value = 0;
    const rainFilter = ctx.createBiquadFilter();
    rainFilter.type = 'lowpass';
    rainFilter.frequency.value = 1100;

    const rainSrc = ctx.createBufferSource();
    rainSrc.buffer = noiseBuffer;
    rainSrc.loop = true;
    rainSrc.connect(rainFilter);
    rainFilter.connect(rainGain);
    rainGain.connect(ctx.destination);
    rainSrc.start(0);

    // 2. Fireplace Crackle (Lowpass Noise + Random Click Modulation)
    const fireGain = ctx.createGain();
    fireGain.gain.value = 0;
    const fireFilter = ctx.createBiquadFilter();
    fireFilter.type = 'bandpass';
    fireFilter.frequency.value = 500;
    fireFilter.Q.value = 2.0;

    const fireSrc = ctx.createBufferSource();
    fireSrc.buffer = noiseBuffer;
    fireSrc.loop = true;
    fireSrc.connect(fireFilter);
    fireFilter.connect(fireGain);
    fireGain.connect(ctx.destination);
    fireSrc.start(0);

    // 3. Space Cosmic Drone (Low Frequency Sine Oscillators)
    const spaceGain = ctx.createGain();
    spaceGain.gain.value = 0;

    const osc1 = ctx.createOscillator();
    osc1.type = 'sine';
    osc1.frequency.value = 65.41; // C2

    const osc2 = ctx.createOscillator();
    osc2.type = 'triangle';
    osc2.frequency.value = 98.00; // G2

    const droneFilter = ctx.createBiquadFilter();
    droneFilter.type = 'lowpass';
    droneFilter.frequency.value = 350;

    osc1.connect(droneFilter);
    osc2.connect(droneFilter);
    droneFilter.connect(spaceGain);
    spaceGain.connect(ctx.destination);
    osc1.start(0);
    osc2.start(0);

    // 4. Vinyl Dust & Hiss (Highpass filtered noise)
    const vinylGain = ctx.createGain();
    vinylGain.gain.value = 0;
    const vinylFilter = ctx.createBiquadFilter();
    vinylFilter.type = 'highpass';
    vinylFilter.frequency.value = 3200;

    const vinylSrc = ctx.createBufferSource();
    vinylSrc.buffer = noiseBuffer;
    vinylSrc.loop = true;
    vinylSrc.connect(vinylFilter);
    vinylFilter.connect(vinylGain);
    vinylGain.connect(ctx.destination);
    vinylSrc.start(0);

    // 5. Tokyo Cafe (Warm diffuse noise band)
    const cafeGain = ctx.createGain();
    cafeGain.gain.value = 0;
    const cafeFilter = ctx.createBiquadFilter();
    cafeFilter.type = 'bandpass';
    cafeFilter.frequency.value = 850;
    cafeFilter.Q.value = 1.2;

    const cafeSrc = ctx.createBufferSource();
    cafeSrc.buffer = noiseBuffer;
    cafeSrc.loop = true;
    cafeSrc.connect(cafeFilter);
    cafeFilter.connect(cafeGain);
    cafeGain.connect(ctx.destination);
    cafeSrc.start(0);

    this.ambientGains = {
      rain: rainGain,
      fire: fireGain,
      space: spaceGain,
      vinyl: vinylGain,
      cafe: cafeGain
    };

    this.ambientSources.push(rainSrc, fireSrc, osc1, osc2, vinylSrc, cafeSrc);
  }

  public setAmbientVolume(type: 'rain' | 'fire' | 'space' | 'vinyl' | 'cafe', volume: number) {
    this.initWebAudio();
    const gainNode = this.ambientGains[type];
    if (gainNode && this.audioContext) {
      gainNode.gain.setTargetAtTime(Math.max(0, Math.min(1, volume * 0.35)), this.audioContext.currentTime, 0.05);
    }
  }

  public setCallbacks(callbacks: AudioEngineCallbacks) {
    this.callbacks = { ...this.callbacks, ...callbacks };
  }

  public async loadTrack(url: string, autoplay = true) {
    this.initWebAudio();
    if (this.audioContext && this.audioContext.state === 'suspended') {
      await this.audioContext.resume();
    }

    this.audio.src = url;
    this.audio.load();

    if (autoplay) {
      try {
        await this.audio.play();
      } catch (err) {
        console.warn('Autoplay note:', err);
      }
    }
  }

  public async play() {
    this.initWebAudio();
    if (this.audioContext && this.audioContext.state === 'suspended') {
      await this.audioContext.resume();
    }
    return this.audio.play();
  }

  public pause() {
    this.audio.pause();
  }

  public seek(seconds: number) {
    if (!isNaN(seconds) && isFinite(seconds)) {
      this.audio.currentTime = Math.max(0, Math.min(seconds, this.audio.duration || 0));
    }
  }

  public setVolume(volume: number) {
    const clamped = Math.max(0, Math.min(1, volume));
    this.audio.volume = clamped;
    if (this.gainNode) {
      this.gainNode.gain.value = clamped;
    }
  }

  public setEqualizerGains(gains: number[]) {
    this.eqFilters.forEach((filter, index) => {
      if (gains[index] !== undefined) {
        filter.gain.setTargetAtTime(gains[index], this.audioContext?.currentTime || 0, 0.05);
      }
    });
  }

  public getFrequencyData(outputArray: any): void {
    if (this.analyserNode) {
      this.analyserNode.getByteFrequencyData(outputArray);
    } else {
      if (!this.audio.paused && this.audio.currentTime > 0) {
        const time = Date.now() * 0.005;
        for (let i = 0; i < outputArray.length; i++) {
          const val = Math.sin(time + i * 0.2) * 50 + Math.cos(time * 0.5 + i * 0.1) * 30 + 100;
          outputArray[i] = Math.max(10, Math.min(240, Math.floor(val)));
        }
      } else {
        outputArray.fill(0);
      }
    }
  }

  public getTimeDomainData(outputArray: any): void {
    if (this.analyserNode) {
      this.analyserNode.getByteTimeDomainData(outputArray);
    } else {
      if (!this.audio.paused) {
        const time = Date.now() * 0.008;
        for (let i = 0; i < outputArray.length; i++) {
          outputArray[i] = 128 + Math.floor(Math.sin(time + i * 0.15) * 40);
        }
      } else {
        outputArray.fill(128);
      }
    }
  }

  public getCurrentTime(): number {
    return this.audio.currentTime || 0;
  }

  public getDuration(): number {
    return this.audio.duration || 0;
  }

  public isPaused(): boolean {
    return this.audio.paused;
  }
}

export const audioEngine = new AudioEngine();
