/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./src/**/*.{html,ts}",
  ],
  darkMode: 'class',
  theme: {
    extend: {
      // Design System Colors
      colors: {
        primary: {
          main: '#5B6FE8',
          light: '#7B8FFF',
          dark: '#3B4FC8',
          DEFAULT: '#5B6FE8',
        },
        secondary: {
          main: '#F59E0B',
          light: '#FCD34D',
          dark: '#D97706',
          DEFAULT: '#F59E0B',
        },
        neutral: {
          darkest: '#1A1D2E',
          dark: '#252837',
          medium: '#3E4455',
          light: '#6B7280',
          lighter: '#9CA3AF',
          lightest: '#F3F4F6',
          white: '#FFFFFF',
        },
        success: {
          bg: '#D1FAE5',
          text: '#065F46',
          icon: '#10B981',
        },
        danger: {
          bg: '#FEE2E2',
          text: '#991B1B',
          icon: '#EF4444',
        },
        warning: {
          bg: '#FEF3C7',
          text: '#92400E',
          icon: '#F59E0B',
        },
        info: {
          bg: '#DBEAFE',
          text: '#1E40AF',
          icon: '#3B82F6',
        },
      },
      // Design System Typography
      fontFamily: {
        primary: ['Inter', '-apple-system', 'BlinkMacSystemFont', 'Segoe UI', 'Roboto', 'sans-serif'],
        mono: ['Roboto Mono', 'Courier New', 'monospace'],
      },
      fontSize: {
        'h1': ['32px', { lineHeight: '1.2', letterSpacing: '-0.02em', fontWeight: '700' }],
        'h2': ['24px', { lineHeight: '1.3', letterSpacing: '-0.01em', fontWeight: '600' }],
        'h3': ['20px', { lineHeight: '1.4', fontWeight: '600' }],
        'h4': ['16px', { lineHeight: '1.5', fontWeight: '600' }],
        'body': ['14px', { lineHeight: '1.5', fontWeight: '400' }],
        'body-sm': ['12px', { lineHeight: '1.5', fontWeight: '400' }],
        'caption': ['11px', { lineHeight: '1.4', fontWeight: '400' }],
      },
      // Design System Spacing
      spacing: {
        'xs': '4px',
        'sm': '8px',
        'md': '12px',
        'lg': '16px',
        'xl': '24px',
        '2xl': '32px',
        '3xl': '48px',
        '4xl': '64px',
        'card': '24px',
        'section': '32px',
        'element': '16px',
      },
      // Design System Layout
      maxWidth: {
        'content': '1440px',
      },
      width: {
        'sidebar': '260px',
      },
      // Design System Shadows
      boxShadow: {
        'card': '0 1px 3px rgba(0, 0, 0, 0.1)',
        'card-hover': '0 4px 12px rgba(0, 0, 0, 0.15)',
        'focus': '0 0 0 3px rgba(91, 111, 232, 0.1)',
      },
      // Design System Border Radius
      borderRadius: {
        'card': '12px',
        'button': '8px',
        'input': '8px',
        'badge': '4px',
        'badge-status': '12px',
      },
      // Animation
      animation: {
        'fade-slide-up': 'fadeSlideUp 0.3s ease-out',
        'button-press': 'buttonPress 0.1s ease',
      },
      keyframes: {
        fadeSlideUp: {
          '0%': { opacity: '0', transform: 'translateY(8px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        buttonPress: {
          '0%, 100%': { transform: 'scale(1)' },
          '50%': { transform: 'scale(0.98)' },
        },
      },
    },
  },
  plugins: [],
}
