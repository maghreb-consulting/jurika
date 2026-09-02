// Sprint 14 D1 -- setup global Vitest.
import '@testing-library/jest-dom/vitest';
import { afterEach } from 'vitest';
import { cleanup } from '@testing-library/react';

// Cleanup React Testing Library après chaque test
afterEach(() => {
  cleanup();
});
