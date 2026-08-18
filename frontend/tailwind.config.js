/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        // Claim status badges pair a colour with a shape/label in the markup —
        // colour alone would fail NFR-014 (Phase 8).
        status: {
          paid: '#15803d',
          flagged: '#b45309',
          denied: '#b91c1c',
          reversed: '#6d28d9',
        },
      },
      fontFamily: {
        // Money and journal amounts are tabular — digits must align in a column.
        mono: ['ui-monospace', 'SFMono-Regular', 'Menlo', 'Consolas', 'monospace'],
      },
    },
  },
  plugins: [],
};
