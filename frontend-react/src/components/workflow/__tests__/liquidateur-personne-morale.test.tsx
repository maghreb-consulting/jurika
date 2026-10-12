import '@testing-library/jest-dom/vitest';
import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { useState } from 'react';

vi.mock('../../identity/IdentityExtractor', () => ({ IdentityExtractor: () => null }));

import { LiquidateurPicker, emptyLiquidateur, type LiquidateurState } from '../LiquidateurPicker';

/** Lot L4d (E5) : l'écran du liquidateur offre la personne morale (tiers ou associé). */
function Hote() {
  const [v, setV] = useState<LiquidateurState>(emptyLiquidateur());
  return <LiquidateurPicker value={v} onChange={setV} options={[]} />;
}

describe('Liquidateur : personne physique ou morale', () => {
  it('un tiers personne morale se saisit par sa dénomination et son représentant', () => {
    render(<Hote />);
    fireEvent.change(screen.getByTestId('liquidateur-select'), { target: { value: '__externe__' } });
    fireEvent.change(screen.getByTestId('liquidateur-forme'), { target: { value: 'morale' } });
    expect(screen.getByTestId('liquidateur-morale')).toBeInTheDocument();
    expect(screen.getByTestId('liquidateur-denomination')).toBeInTheDocument();
    expect(screen.getByTestId('liquidateur-representant-nom')).toBeInTheDocument();
    // Les champs de la personne physique disparaissent (pas de CIN pour une société).
    expect(screen.queryByTestId('liquidateur-cin')).toBeNull();
    expect(screen.queryByTestId('liquidateur-nom')).toBeNull();
  });
});
