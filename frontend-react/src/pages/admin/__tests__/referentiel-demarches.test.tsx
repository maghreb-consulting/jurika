import '@testing-library/jest-dom/vitest';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

/**
 * Lot L4b (E2, RG-DEM-01) : le super-administrateur importe le referentiel des demarches
 * du corpus ; une version refusee est annoncee avec son motif, le journal garde chaque import.
 */
const get = vi.fn();
const post = vi.fn();
vi.mock('../../../lib/api', () => ({
  api: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) },
  extractError: (e: unknown) => ({ message: String(e) }),
}));

import { ReferentielDemarchesPage } from '../ReferentielDemarchesPage';

const imp = (statut: 'APPLIQUE' | 'REFUSE', motif: string | null = null) => ({
  id: statut, corpus_version: 'CORPUS_2026-10-03', statut, lignes: statut === 'APPLIQUE' ? 7006 : 0,
  decisions: 301, renvois: 24, motif, importe_le: '2026-10-11T10:00:00Z',
  rapport: JSON.stringify({ total: 7006, conditionsPhrases: ['M l. 1 : phrase'], delaisTextuels: [], erreurs: [],
    ecartsCreation: ['n° 47 : modèle : en service « », corpus « — (procédure interne) »'] }),
});

beforeEach(() => {
  get.mockReset();
  post.mockReset();
});

describe('Référentiel des démarches', () => {
  it('importe le corpus et annonce la version appliquée', async () => {
    get.mockResolvedValueOnce({ data: { versionActive: null, imports: [] } });
    post.mockResolvedValueOnce({ data: { id: 'x', version: 'CORPUS_2026-10-03', statut: 'APPLIQUE', lignes: 7006, decisions: 301, renvois: 24, motif: null } });
    get.mockResolvedValueOnce({ data: { versionActive: 'CORPUS_2026-10-03', imports: [imp('APPLIQUE')] } });
    render(<ReferentielDemarchesPage />, { wrapper: MemoryRouter });
    expect(await screen.findByText(/n’ont pas encore de démarches du corpus/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /Importer le référentiel/ }));
    expect(await screen.findByTestId('resultat-import')).toHaveTextContent('7006 démarches, 301 décisions, 24 renvois');
    expect(await screen.findByTestId('version-active')).toHaveTextContent('CORPUS_2026-10-03');
    expect(post).toHaveBeenCalledWith('/tickets/referentiel-demarches/import');
  });

  it('montre le motif d’un refus et le rapport d’un import', async () => {
    get.mockResolvedValueOnce({ data: { versionActive: 'CORPUS_2026-10-03', imports: [imp('REFUSE', 'La version est déjà appliquée.')] } });
    render(<ReferentielDemarchesPage />, { wrapper: MemoryRouter });
    expect(await screen.findByText('La version est déjà appliquée.')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Voir le rapport' }));
    expect(screen.getByText(/1 conditions rédigées en/)).toBeInTheDocument();
    expect(screen.getByTestId('ecarts-creation')).toHaveTextContent('1 écart(s)');
  });

  // Lot L4d (E3) : l'avancement des tickets en cours suit la nouvelle version (V37).
  it('dit combien d’états des tickets en cours ont suivi la nouvelle version', async () => {
    const applique = imp('APPLIQUE');
    applique.rapport = JSON.stringify({ total: 7006, erreurs: [], etatsReportes: 12, etatsNonReportes: 1 });
    get.mockResolvedValueOnce({ data: { versionActive: 'CORPUS_2026-10-11', imports: [applique] } });
    render(<ReferentielDemarchesPage />, { wrapper: MemoryRouter });
    fireEvent.click(await screen.findByRole('button', { name: 'Voir le rapport' }));
    expect(screen.getByTestId('etats-reportes')).toHaveTextContent('12 état(s) reporté(s)');
    expect(screen.getByTestId('etats-reportes')).toHaveTextContent('1 sans ligne correspondante');
  });
});
