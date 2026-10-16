import '@testing-library/jest-dom/vitest';
import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { useState } from 'react';

vi.mock('../../identity/IdentityExtractor', () => ({ IdentityExtractor: () => null }));

import { LiquidateurPicker } from '../LiquidateurPicker';
import { buildLiquidateurOptions, emptyLiquidateur, type LiquidateurState } from '../liquidateurOptions';

/** Lot L4d (E5) : l'écran du liquidateur offre la personne morale (tiers ou associé). */
function Hote() {
  const [v, setV] = useState<LiquidateurState>(emptyLiquidateur());
  return <LiquidateurPicker value={v} onChange={setV} options={[]} />;
}

/**
 * Lot L6b (E15, parcours « dissolution ») : choisi parmi les gérants, le liquidateur reprend
 * sa nationalité et sa date de naissance connues du dossier (elles étaient redemandées
 * vides) et sa civilité abrégée (« M. », jamais le code « M »).
 */
function HoteDossier() {
  const [v, setV] = useState<LiquidateurState>(emptyLiquidateur());
  const options = buildLiquidateurOptions({
    gerants: [{ civilite: 'M', prenom: 'Mehdi', nom: 'Alaoui', cinNumero: 'BE123456', adresse: '8 rue des Essais',
      nationalite: 'marocaine', dateNaissance: '1980-03-15' }],
    associes: [],
  });
  return <LiquidateurPicker value={v} onChange={setV} options={options} />;
}

describe('Liquidateur choisi dans le dossier', () => {
  it('reprend nationalité, date de naissance et civilité abrégée', () => {
    render(<HoteDossier />);
    fireEvent.change(screen.getByTestId('liquidateur-select'), { target: { value: 'gerant:mehdi alaoui' } });
    expect(screen.getByTestId('liquidateur-nationalite')).toHaveValue('marocaine');
    expect(screen.getByLabelText('Date de naissance')).toHaveValue('1980-03-15');
    expect(screen.getByLabelText('Civilité')).toHaveValue('M.');
  });
});

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
