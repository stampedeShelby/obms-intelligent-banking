/**
 * OBMS design tokens.
 *
 * The palette is a deep navy with teal accents, as specified: the navy carries
 * authority without the saturation that makes long sessions tiring, and teal
 * is used only for emphasis so that it stays meaningful. Risk colours are
 * separated from brand colours deliberately - a risk band must never be
 * confused with a decorative accent.
 */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        navy: {
          50: '#f3f6fb',
          100: '#e3eaf5',
          200: '#c7d5ea',
          300: '#9bb4d8',
          400: '#688dc1',
          500: '#466fa9',
          600: '#35578c',
          700: '#2c4672',
          800: '#283c5f',
          900: '#0e234c',
          950: '#081734',
        },
        teal: {
          50: '#effefb',
          100: '#c7fff4',
          200: '#90ffea',
          300: '#51f7dd',
          400: '#1de4ca',
          500: '#04c8b1',
          600: '#00a192',
          700: '#058076',
          800: '#0a655f',
          900: '#0d544f',
        },
        risk: {
          low: '#0f9d58',
          medium: '#e8a33d',
          high: '#e8602c',
          critical: '#c62828',
        },
      },
      fontFamily: {
        sans: ['Inter', 'ui-sans-serif', 'system-ui', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'ui-monospace', 'monospace'],
      },
      boxShadow: {
        card: '0 1px 2px rgba(8, 23, 52, 0.04), 0 4px 16px rgba(8, 23, 52, 0.06)',
        lifted: '0 2px 4px rgba(8, 23, 52, 0.06), 0 12px 32px rgba(8, 23, 52, 0.1)',
      },
      borderRadius: {
        xl: '0.875rem',
        '2xl': '1.25rem',
      },
      keyframes: {
        shimmer: {
          '100%': { transform: 'translateX(100%)' },
        },
        'fade-in': {
          from: { opacity: '0', transform: 'translateY(4px)' },
          to: { opacity: '1', transform: 'translateY(0)' },
        },
      },
      animation: {
        shimmer: 'shimmer 1.6s infinite',
        'fade-in': 'fade-in 0.2s ease-out',
      },
    },
  },
  plugins: [],
};
