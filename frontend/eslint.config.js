import js from '@eslint/js';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import globals from 'globals';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  { ignores: ['dist', 'node_modules', 'coverage', 'playwright-report'] },
  {
    extends: [js.configs.recommended, ...tseslint.configs.recommendedTypeChecked],
    files: ['**/*.{ts,tsx}'],
    languageOptions: {
      ecmaVersion: 2020,
      globals: globals.browser,
      parserOptions: {
        project: ['./tsconfig.app.json', './tsconfig.node.json'],
        tsconfigRootDir: import.meta.dirname,
      },
    },
    plugins: {
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],

      // -----------------------------------------------------------------------
      // PLAN.md Phase 0 — ban `any`.
      //
      // The API types in PRD §5 are the contract; `any` at the boundary means a
      // response shape change compiles clean and fails at runtime instead.
      // -----------------------------------------------------------------------
      '@typescript-eslint/no-explicit-any': 'error',
      '@typescript-eslint/no-unsafe-assignment': 'error',
      '@typescript-eslint/no-unsafe-member-access': 'error',
      '@typescript-eslint/no-unsafe-call': 'error',
      '@typescript-eslint/no-unsafe-return': 'error',
      '@typescript-eslint/no-unsafe-argument': 'error',

      // -----------------------------------------------------------------------
      // PLAN.md Phase 0 — ban BigDecimal-unsafe number parsing.
      //
      // Money crosses the wire as a decimal *string* precisely so it never
      // touches an IEEE-754 double. `Number("0.1") + Number("0.2")` is
      // 0.30000000000000004, and a claim that fails the server's to-the-cent
      // line-sum check for that reason is a bug the client created.
      //
      // Parse with decimal.js (`new Decimal(...)`), render with
      // Intl.NumberFormat over the Decimal's string form. See
      // src/lib/formatMoney.ts (Phase 8).
      // -----------------------------------------------------------------------
      'no-restricted-globals': [
        'error',
        {
          name: 'parseFloat',
          message: 'Money is a decimal string — parse it with decimal.js (new Decimal(value)).',
        },
        {
          name: 'parseInt',
          message: 'Money is a decimal string — parse it with decimal.js (new Decimal(value)).',
        },
      ],
      'no-restricted-syntax': [
        'error',
        {
          selector: "CallExpression[callee.name='Number']",
          message: 'Number() loses decimal precision — use decimal.js (new Decimal(value)).',
        },
        {
          selector: "CallExpression[callee.object.name='Math'][callee.property.name='round']",
          message: 'Math.round() on money is a float operation — use Decimal#toDecimalPlaces(2).',
        },
        {
          selector: "UnaryExpression[operator='+'][argument.type!='Literal']",
          message: 'Unary + coerces to a float — use decimal.js for money, Number.parseInt elsewhere with an explicit radix.',
        },
      ],
    },
  },
  // The lint rules above are typed-linting rules; the config files themselves
  // are not in either tsconfig's `include`, so they parse without type info.
  {
    files: ['*.config.js', 'eslint.config.js'],
    extends: [tseslint.configs.disableTypeChecked],
    languageOptions: { globals: globals.node },
  },
);
