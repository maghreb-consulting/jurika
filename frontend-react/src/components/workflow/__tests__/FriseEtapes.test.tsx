import '@testing-library/jest-dom/vitest';
import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { FriseEtapes } from '../FriseEtapes';
import { etatEtape } from '../etatsEtapes';

/**
 * Lot L6b (E5) : frise des etapes d'un parcours. L'etat de chaque etape se lit sans la
 * couleur (nom accessible), seules les etapes atteintes sont cliquables, et l'etape
 * affichee porte aria-current="step".
 */
const ETAPES = [
  { numero: 1, libelle: 'Dénomination' },
  { numero: 2, libelle: 'Siège social' },
  { numero: 3, libelle: 'Capital' },
  { numero: 4, libelle: 'Activité' },
];

describe('FriseEtapes', () => {
  it('nomme chaque etape avec son etat et marque l etape affichee', () => {
    render(<FriseEtapes etapes={ETAPES} plusAvancee={3} affichee={2} onNavigate={vi.fn()} />);
    expect(screen.getByRole('button', { name: 'Étape 1 : Dénomination, validée' })).toBeEnabled();
    const affichee = screen.getByRole('button', { name: 'Étape 2 : Siège social, affichée' });
    expect(affichee).toHaveAttribute('aria-current', 'step');
    expect(affichee).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Étape 3 : Capital, en cours' })).toBeEnabled();
    expect(screen.getByRole('button', { name: 'Étape 4 : Activité, verrouillée' })).toBeDisabled();
    expect(screen.getByText('Étape 2 sur 4')).toBeInTheDocument();
    expect(screen.getByTestId('frise-compteur')).toHaveTextContent('2/4 étapes validées');
    expect(screen.getByRole('progressbar', { name: 'Étapes validées' })).toHaveAttribute('aria-valuenow', '2');
  });

  it('ne navigue que vers une etape atteinte', () => {
    const naviguer = vi.fn();
    render(<FriseEtapes etapes={ETAPES} plusAvancee={3} affichee={3} onNavigate={naviguer} />);
    fireEvent.click(screen.getByRole('button', { name: /Étape 1 :/ }));
    fireEvent.click(screen.getByRole('button', { name: /Étape 4 :/ }));
    expect(naviguer).toHaveBeenCalledTimes(1);
    expect(naviguer).toHaveBeenCalledWith(1);
  });

  it('sans navigation, aucune pastille n est cliquable', () => {
    render(<FriseEtapes etapes={ETAPES} plusAvancee={3} />);
    for (const b of screen.getAllByRole('button')) expect(b).toBeDisabled();
  });

  it('signale l etape affichee bloquee', () => {
    render(<FriseEtapes etapes={ETAPES} plusAvancee={2} bloquee />);
    expect(screen.getByRole('button', { name: 'Étape 2 : Siège social, bloquée' })).toHaveAttribute('aria-current', 'step');
  });

  it('etatEtape suit la regle : validee avant la plus avancee, verrouillee apres', () => {
    expect(etatEtape(1, 3, 3)).toBe('validee');
    expect(etatEtape(3, 3, 3)).toBe('affichee');
    expect(etatEtape(3, 3, 1)).toBe('atteinte');
    expect(etatEtape(4, 3, 3)).toBe('verrouillee');
    expect(etatEtape(3, 3, 3, true)).toBe('bloquee');
  });
});
