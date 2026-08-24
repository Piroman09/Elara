/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        background: '#09090b',
        surface: '#121216',
        'surface-elevated': '#18181f',
        'surface-card': 'rgba(255, 255, 255, 0.04)',
        'surface-card-hover': 'rgba(255, 255, 255, 0.08)',
        spotify: {
          green: '#1ed760',
          hover: '#1fdf64',
          dark: '#121212',
          light: '#282828',
          accent: '#1db954',
          neon: '#00ff87'
        },
        aura: {
          purple: '#8b5cf6',
          pink: '#ec4899',
          cyan: '#06b6d4',
          emerald: '#10b981',
          amber: '#f59e0b',
          rose: '#f43f5e',
          violet: '#7c3aed'
        }
      },
      fontFamily: {
        sans: ['Plus Jakarta Sans', 'Inter', 'system-ui', 'sans-serif'],
      },
      animation: {
        'pulse-slow': 'pulse 4s cubic-bezier(0.4, 0, 0.6, 1) infinite',
        'spin-slow': 'spin 12s linear infinite',
        'bounce-subtle': 'bounceSubtle 2s infinite',
        'glow-pulse': 'glowPulse 3s ease-in-out infinite',
        'soundwave': 'soundwave 1.2s ease-in-out infinite alternate',
      },
      keyframes: {
        bounceSubtle: {
          '0%, 100%': { transform: 'translateY(0)' },
          '50%': { transform: 'translateY(-4px)' },
        },
        glowPulse: {
          '0%, 100%': { opacity: '0.4', filter: 'blur(30px)' },
          '50%': { opacity: '0.7', filter: 'blur(45px)' },
        },
        soundwave: {
          '0%': { height: '15%' },
          '100%': { height: '100%' },
        }
      },
      backdropBlur: {
        xs: '2px',
        '2xl': '40px',
        '3xl': '60px',
      }
    },
  },
  plugins: [],
}
