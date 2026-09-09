import path from 'node:path';

// Scaffold serves the static placeholder directly from disk — no dev server or docker needed.
export const INBOX_URL = `file://${path.join(__dirname, '..', 'index.html')}`;
// Full-stack runs (docker compose up) can target the served UI instead:
//   ETG_UI_URL=http://localhost:3000 npx playwright test
export const UI_URL = process.env.ETG_UI_URL ?? INBOX_URL;
