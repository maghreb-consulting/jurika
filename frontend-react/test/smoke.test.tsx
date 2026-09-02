import React from 'react';
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

// Sprint 14 D1 -- smoke test pour valider le setup Vitest + RTL + jsdom.
// Tests composants reels viendront en Sprint 14 bis (D2/D3/D4 -- 30+ tests).
describe('Vitest smoke', () => {
  it('Vitest + jsdom + RTL fonctionnent', () => {
    render(<div data-testid="hello">Hello JURIKA Sprint 14</div>);
    expect(screen.getByTestId('hello')).toHaveTextContent('Hello JURIKA Sprint 14');
  });

  it('matchers @testing-library/jest-dom disponibles', () => {
    render(
      <button type="button" disabled>
        OK
      </button>,
    );
    expect(screen.getByRole('button')).toBeDisabled();
  });
});
