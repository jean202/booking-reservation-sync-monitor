/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      fontFamily: {
        display: ['Fraunces', 'Georgia', 'serif'],
        sans: ['"Hanken Grotesk"', 'system-ui', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'ui-monospace', 'monospace'],
      },
      colors: {
        ink: {
          900: '#13110d',
          800: '#1b1813',
          700: '#23201a',
          600: '#2e2a22',
          500: '#3a352b',
        },
        cream: {
          50: '#f7f1e3',
          100: '#efe7d4',
          200: '#ddd2b8',
          400: '#a89c80',
        },
        brass: {
          300: '#e7c98a',
          400: '#d6ad5c',
          500: '#c4953b',
          600: '#a87a28',
        },
        sage: '#7c8b6a',
        rust: '#c2603f',
      },
      boxShadow: {
        plate: '0 1px 0 0 rgba(247,241,227,0.04) inset, 0 18px 40px -24px rgba(0,0,0,0.8)',
        lamp: '0 0 60px -12px rgba(214,173,92,0.35)',
      },
      keyframes: {
        riseIn: {
          '0%': { opacity: '0', transform: 'translateY(14px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        glow: {
          '0%, 100%': { opacity: '0.55' },
          '50%': { opacity: '1' },
        },
      },
      animation: {
        riseIn: 'riseIn 0.6s cubic-bezier(0.22, 1, 0.36, 1) both',
        glow: 'glow 3.5s ease-in-out infinite',
      },
    },
  },
  plugins: [],
}
