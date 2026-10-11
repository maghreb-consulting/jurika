import '@testing-library/jest-dom/vitest';
import { describe, expect, it, vi } from 'vitest';
import { render, renderHook } from '@testing-library/react';
import { DynamicField } from '../modificationResolutions';
import { useSeanceForm, emptyAssocie } from '../SeanceForm';
import { emptyLiquidateur } from '../LiquidateurPicker';

/**
 * Lot L4a (E2) : aucune valeur pre-remplie silencieuse. Une liste ne choisit plus sa
 * premiere option a la place de l'employe ; qualite du president, auteur et mode de
 * convocation, nationalite, remuneration du liquidateur ne partent pas sans confirmation.
 */
describe('Aucune valeur pré-remplie silencieuse', () => {
  it('une liste de résolution vide reste vide (plus de première option d’office)', () => {
    const onChange = vi.fn();
    render(
      <DynamicField
        spec={{ key: 'cessionnaireTiers', label: 'Cessionnaire tiers ?', kind: 'select',
          options: [{ value: 'oui', label: 'Oui' }, { value: 'non', label: 'Non' }] }}
        value=""
        onChange={onChange}
      />,
    );
    expect(onChange).not.toHaveBeenCalled();
  });

  it('la séance ne transmet ni qualité, ni auteur, ni mode non confirmés', () => {
    const { result } = renderHook(() =>
      useSeanceForm({ formeJuridique: 'SARL', societe: { denomination: 'PARACOSME' } }),
    );
    const p = result.current.buildPayload() as {
      seance: Record<string, string>; convocation: Record<string, string>;
    };
    expect(p.seance.presidentQualite).toBe('');
    expect(p.convocation.auteur).toBe('');
    expect(p.convocation.mode).toBe('');
    expect(result.current.presidentQualitePropose).toBe('gérant');
  });

  it('ni nationalité d’associé ni rémunération de liquidateur supposées', () => {
    expect(emptyAssocie().nationalite ?? '').toBe('');
    expect(emptyLiquidateur().remuneration).toBe('');
  });
});
