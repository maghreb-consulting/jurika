import '@testing-library/jest-dom/vitest';
import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { EnTeteDepliant } from '../Depliant';
import { Select } from '../Select';

/** Lot L6b (E5, W2) : ajouts a la bibliotheque pour les etapes des parcours. */
describe('EnTeteDepliant', () => {
  it('annonce son etat (aria-expanded) et rend la main a l appelant au clic', () => {
    const onClick = vi.fn();
    const { rerender } = render(
      <EnTeteDepliant ouvert={false} onClick={onClick} data-testid="entete">
        Incident de séance
      </EnTeteDepliant>,
    );
    const bouton = screen.getByRole('button', { name: 'Incident de séance' });
    expect(bouton).toHaveAttribute('aria-expanded', 'false');
    expect(bouton).toHaveAttribute('type', 'button');
    fireEvent.click(bouton);
    expect(onClick).toHaveBeenCalledTimes(1);
    rerender(
      <EnTeteDepliant ouvert onClick={onClick} data-testid="entete">
        Incident de séance
      </EnTeteDepliant>,
    );
    expect(screen.getByTestId('entete')).toHaveAttribute('aria-expanded', 'true');
  });
});

describe('Select : options passees en enfants', () => {
  it('rend les <option> enfants apres `options`, et la valeur choisie remonte', () => {
    const onChange = vi.fn();
    render(
      <Select label="Nature" value="" onChange={(e) => onChange(e.target.value)} options={[{ value: '', label: 'Choisir…' }]}>
        <option value="CONSTITUTION">Constitution</option>
        <option value="MODIFICATION">Modification</option>
      </Select>,
    );
    const liste = screen.getByLabelText('Nature') as HTMLSelectElement;
    expect(Array.from(liste.options).map((o) => o.value)).toEqual(['', 'CONSTITUTION', 'MODIFICATION']);
    fireEvent.change(liste, { target: { value: 'MODIFICATION' } });
    expect(onChange).toHaveBeenCalledWith('MODIFICATION');
  });

  it('sans `options` : seuls les enfants', () => {
    render(
      <Select aria-label="Civilité" defaultValue="Mme">
        <option value="M.">M.</option>
        <option value="Mme">Mme</option>
      </Select>,
    );
    expect((screen.getByLabelText('Civilité') as HTMLSelectElement).value).toBe('Mme');
  });
});
