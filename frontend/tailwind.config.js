/**
 * Tailwind config — Zeptex / "Synthetic Intelligence Interface"
 * All tokens extracted verbatim from the Stitch design (project: AI Project Architect).
 * Do not edit values without user approval — they mirror the design system 1:1.
 */
/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      colors: {
        // Surfaces
        surface: '#0b1326',
        'surface-dim': '#0b1326',
        'surface-bright': '#31394d',
        'surface-container-lowest': '#060e20',
        'surface-container-low': '#131b2e',
        'surface-container': '#171f33',
        'surface-container-high': '#222a3d',
        'surface-container-highest': '#2d3449',
        'surface-variant': '#2d3449',
        'surface-tint': '#7bd0ff',
        'inverse-surface': '#dae2fd',
        'inverse-on-surface': '#283044',
        'on-surface': '#dae2fd',
        'on-surface-variant': '#bdc8d1',

        // Background
        background: '#0b1326',
        'on-background': '#dae2fd',

        // Primary (Electric Blue)
        primary: '#8ed5ff',
        'on-primary': '#00354a',
        'primary-container': '#38bdf8',
        'on-primary-container': '#004965',
        'inverse-primary': '#00668a',
        'primary-fixed': '#c4e7ff',
        'primary-fixed-dim': '#7bd0ff',
        'on-primary-fixed': '#001e2c',
        'on-primary-fixed-variant': '#004c69',

        // Secondary (Emerald)
        secondary: '#4edea3',
        'on-secondary': '#003824',
        'secondary-container': '#00a572',
        'on-secondary-container': '#00311f',
        'secondary-fixed': '#6ffbbe',
        'secondary-fixed-dim': '#4edea3',
        'on-secondary-fixed': '#002113',
        'on-secondary-fixed-variant': '#005236',

        // Tertiary (Amber)
        tertiary: '#ffc174',
        'on-tertiary': '#472a00',
        'tertiary-container': '#f59e0b',
        'on-tertiary-container': '#613b00',
        'tertiary-fixed': '#ffddb8',
        'tertiary-fixed-dim': '#ffb95f',
        'on-tertiary-fixed': '#2a1700',
        'on-tertiary-fixed-variant': '#653e00',

        // Error
        error: '#ffb4ab',
        'on-error': '#690005',
        'error-container': '#93000a',
        'on-error-container': '#ffdad6',

        // Outline
        outline: '#87929a',
        'outline-variant': '#3e484f',

        // Overrides — seed/override fields + prose-only hexes from the design.
        // Namespaced so they never conflict with the authoritative namedColors above.
        // Usage: bg-overrides-canvas, text-overrides-text-high, etc.
        overrides: {
          neutral: '#0f172a', // overrideNeutralColor
          primary: '#38bdf8', // overridePrimaryColor (== customColor seed)
          secondary: '#10b981', // overrideSecondaryColor
          tertiary: '#f59e0b', // overrideTertiaryColor
          canvas: '#0f172a', // prose: Level 0 base canvas
          container: '#1e293b', // prose: Level 1 primary containers
          popover: '#334155', // prose: Level 2 popover/tooltip fill
          'text-high': '#f8fafc', // prose: high-contrast primary text
          'text-muted': '#94a3b8', // prose: slate-400 secondary metadata
        },
      },

      fontFamily: {
        sans: ['Geist', 'sans-serif'],
        geist: ['Geist', 'sans-serif'],
        mono: ['JetBrains Mono', 'monospace'],
        jetbrains: ['JetBrains Mono', 'monospace'],
      },

      // Typography scale — [fontSize, { lineHeight, letterSpacing, fontWeight }]
      fontSize: {
        'display-lg': ['48px', { lineHeight: '56px', letterSpacing: '-0.02em', fontWeight: '700' }],
        'headline-lg': ['32px', { lineHeight: '40px', letterSpacing: '-0.01em', fontWeight: '600' }],
        'headline-lg-mobile': ['24px', { lineHeight: '32px', fontWeight: '600' }],
        'headline-md': ['24px', { lineHeight: '32px', fontWeight: '600' }],
        'body-lg': ['16px', { lineHeight: '24px', fontWeight: '400' }],
        'body-md': ['14px', { lineHeight: '20px', fontWeight: '400' }],
        'label-md': ['12px', { lineHeight: '16px', letterSpacing: '0.02em', fontWeight: '500' }],
        'label-sm': ['10px', { lineHeight: '14px', letterSpacing: '0.04em', fontWeight: '500' }],
      },

      borderRadius: {
        sm: '0.125rem',
        DEFAULT: '0.25rem',
        md: '0.375rem',
        lg: '0.5rem',
        xl: '0.75rem',
        full: '9999px',
      },

      // Base-4 spacing system from the design
      spacing: {
        unit: '4px',
        gutter: '16px',
        'margin-mobile': '16px',
        'margin-desktop': '32px',
      },

      maxWidth: {
        container: '1440px',
      },
    },
  },
  plugins: [],
}
