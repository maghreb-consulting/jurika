import '@testing-library/jest-dom/vitest';
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

/**
 * Recette R1 (point 3), RG-ESS-07 : pendant l'essai, le bandeau de toutes les pages du cabinet
 * affiche le forfait "Essai gratuit (fonctions du forfait Professionnel)" (avant : dans une
 * infobulle seulement).
 */
vi.mock('../../../store/authStore', () => ({
  useAuthStore: (sel: (s: { user: { role: string } }) => unknown) => sel({ user: { role: 'SUPERVISEUR' } }),
}));
const service = vi.hoisted(() => ({ etat: vi.fn() }));
vi.mock('../../../services/essai.service', () => ({ essaiService: service }));

import { BandeauEssai } from '../BandeauEssai';

describe('BandeauEssai', () => {
  it('pendant l essai : le forfait de l essai est affiche', async () => {
    service.etat.mockResolvedValue({
      enEssai: true, statut: 'TRIAL_ACTIVE', debut: '2026-10-17T10:00:00Z', fin: '2026-10-24T10:00:00Z',
      joursRestants: 7, rappel: null, lectureSeule: false, lectureSeuleDepuis: null, suppressionLe: null,
      avertissementLe: null, prolongeLe: null, prolongeable: true, limites: null,
    });
    render(<MemoryRouter><BandeauEssai /></MemoryRouter>);
    expect(await screen.findByTestId('bandeau-essai-forfait'))
      .toHaveTextContent('Forfait : Essai gratuit (fonctions du forfait Professionnel)');
  });
});
