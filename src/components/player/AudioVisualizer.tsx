import React, { useEffect, useRef } from 'react';
import { useMusicStore } from '../../store/useMusicStore';
import { audioEngine } from '../../services/audioEngine';
import { AURA_THEMES } from '../../data/mockTracks';

interface AudioVisualizerProps {
  className?: string;
  height?: number;
}

export const AudioVisualizer: React.FC<AudioVisualizerProps> = ({ className = '', height = 80 }) => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const visualizerMode = useMusicStore(state => state.visualizerMode);
  const isPlaying = useMusicStore(state => state.isPlaying);
  const currentTrack = useMusicStore(state => state.currentTrack);
  const currentTheme = useMusicStore(state => state.currentTheme);

  const themeConfig = AURA_THEMES.find(t => t.id === currentTheme) || AURA_THEMES[0];
  const accentColor = currentTrack?.accentColor || themeConfig.accent;

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas || visualizerMode === 'off') return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let animationFrameId: number;
    const freqData = new Uint8Array(64);
    const timeData = new Uint8Array(128);

    const particles = Array.from({ length: 45 }, () => ({
      x: Math.random() * 300,
      y: Math.random() * 80,
      radius: Math.random() * 2.5 + 1,
      speedX: (Math.random() - 0.5) * 0.9,
      speedY: (Math.random() - 0.5) * 0.9,
      baseAlpha: Math.random() * 0.5 + 0.3
    }));

    let vinylAngle = 0;
    let haloPulse = 0;

    const render = () => {
      animationFrameId = requestAnimationFrame(render);

      const width = canvas.offsetWidth;
      const h = canvas.offsetHeight;
      if (canvas.width !== width || canvas.height !== h) {
        canvas.width = width;
        canvas.height = h;
      }

      ctx.clearRect(0, 0, width, h);

      if (visualizerMode === 'halo') {
        audioEngine.getFrequencyData(freqData);
        const bass = (freqData[0] + freqData[1] + freqData[2]) / 765;
        haloPulse = isPlaying ? bass : 0.05;

        const centerX = width / 2;
        const centerY = h / 2;
        const baseRadius = Math.min(centerX, centerY) * 0.6;
        const haloRadius = baseRadius * (1 + haloPulse * 0.4);

        // Halo outer ring
        ctx.save();
        ctx.beginPath();
        ctx.arc(centerX, centerY, haloRadius, 0, Math.PI * 2);
        ctx.strokeStyle = accentColor;
        ctx.lineWidth = 3 + haloPulse * 4;
        ctx.shadowBlur = 18 + haloPulse * 20;
        ctx.shadowColor = accentColor;
        ctx.stroke();

        // Inner glowing core
        ctx.beginPath();
        ctx.arc(centerX, centerY, baseRadius * 0.8, 0, Math.PI * 2);
        ctx.fillStyle = `${accentColor}33`;
        ctx.fill();
        ctx.restore();
      } else if (visualizerMode === 'bars') {
        audioEngine.getFrequencyData(freqData);
        const barCount = 32;
        const barWidth = Math.max(3, Math.floor(width / (barCount * 1.6)));
        const gap = Math.max(2, Math.floor((width - (barCount * barWidth)) / barCount));

        for (let i = 0; i < barCount; i++) {
          const rawValue = freqData[i] || 0;
          const percent = isPlaying ? Math.min(1, rawValue / 255) : 0.08;
          const barHeight = Math.max(4, percent * (h - 8));

          const x = i * (barWidth + gap) + gap / 2;
          const y = h - barHeight;

          const grad = ctx.createLinearGradient(0, y, 0, h);
          grad.addColorStop(0, '#00ff87');
          grad.addColorStop(0.6, accentColor);
          grad.addColorStop(1, '#06b6d4');

          ctx.fillStyle = grad;
          ctx.beginPath();
          ctx.roundRect(x, y, barWidth, barHeight, [3, 3, 0, 0]);
          ctx.fill();

          ctx.fillStyle = '#ffffff';
          ctx.fillRect(x, y - 2, barWidth, 2);
        }
      } else if (visualizerMode === 'wave') {
        audioEngine.getTimeDomainData(timeData);
        ctx.lineWidth = 2.5;
        ctx.strokeStyle = accentColor;
        ctx.shadowBlur = 10;
        ctx.shadowColor = accentColor;

        ctx.beginPath();
        const sliceWidth = width / (timeData.length - 1);
        let x = 0;

        for (let i = 0; i < timeData.length; i++) {
          const v = isPlaying ? timeData[i] / 128.0 : 1.0;
          const y = (v * h) / 2;

          if (i === 0) {
            ctx.moveTo(x, y);
          } else {
            ctx.lineTo(x, y);
          }
          x += sliceWidth;
        }

        ctx.stroke();
        ctx.shadowBlur = 0;
      } else if (visualizerMode === 'particles') {
        audioEngine.getFrequencyData(freqData);
        const bassIntensity = (freqData[0] + freqData[1] + freqData[2]) / 765;

        particles.forEach(p => {
          p.x += p.speedX * (isPlaying ? 1 + bassIntensity * 2 : 0.4);
          p.y += p.speedY * (isPlaying ? 1 + bassIntensity * 2 : 0.4);

          if (p.x < 0) p.x = width;
          if (p.x > width) p.x = 0;
          if (p.y < 0) p.y = h;
          if (p.y > h) p.y = 0;

          const r = p.radius * (1 + (isPlaying ? bassIntensity * 1.5 : 0));
          ctx.beginPath();
          ctx.arc(p.x, p.y, r, 0, Math.PI * 2);
          ctx.fillStyle = accentColor;
          ctx.globalAlpha = p.baseAlpha + (isPlaying ? bassIntensity * 0.4 : 0);
          ctx.shadowBlur = 8;
          ctx.shadowColor = accentColor;
          ctx.fill();
        });
        ctx.globalAlpha = 1.0;
        ctx.shadowBlur = 0;
      } else if (visualizerMode === 'vinyl') {
        if (isPlaying) {
          vinylAngle += 0.03;
        }
        const centerX = width / 2;
        const centerY = h / 2;
        const radius = Math.min(centerX, centerY) - 4;

        ctx.save();
        ctx.translate(centerX, centerY);
        ctx.rotate(vinylAngle);

        ctx.beginPath();
        ctx.arc(0, 0, radius, 0, Math.PI * 2);
        ctx.fillStyle = '#0a0a0e';
        ctx.fill();
        ctx.strokeStyle = '#27272a';
        ctx.lineWidth = 2;
        ctx.stroke();

        for (let r = radius * 0.4; r < radius * 0.9; r += 5) {
          ctx.beginPath();
          ctx.arc(0, 0, r, 0, Math.PI * 2);
          ctx.strokeStyle = 'rgba(255, 255, 255, 0.04)';
          ctx.lineWidth = 1;
          ctx.stroke();
        }

        ctx.beginPath();
        ctx.arc(0, 0, radius * 0.35, 0, Math.PI * 2);
        ctx.fillStyle = accentColor;
        ctx.fill();

        ctx.beginPath();
        ctx.arc(0, 0, 4, 0, Math.PI * 2);
        ctx.fillStyle = '#ffffff';
        ctx.fill();

        ctx.restore();
      }
    };

    render();

    return () => {
      cancelAnimationFrame(animationFrameId);
    };
  }, [visualizerMode, isPlaying, accentColor, currentTheme]);

  if (visualizerMode === 'off') return null;

  return (
    <div className={`relative overflow-hidden ${className}`}>
      <canvas 
        ref={canvasRef} 
        className="w-full h-full block" 
        style={{ height }}
      />
    </div>
  );
};
