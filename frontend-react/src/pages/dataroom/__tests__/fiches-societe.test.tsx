import '@testing-library/jest-dom/vitest';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';

/**
 * Lot L5 (E7) puis L5b (E3) : fiche client et fiche dossier a l'ecran (cahier des charges
 * 10.2, 10.3) -- deux panneaux distincts, chacun son PDF (RG-FIC-05, 07, 08 ; plus de Word).
 * Les valeurs affichees sont celles rendues par le serveur (provenance, versions de TP,
 * operations) ; le telechargement dit si la fiche a ete conservee.
 */

const svc = {
  ficheClient: vi.fn(),
  ficheDossier: vi.fn(),
  genererFichePdf: vi.fn(),
};
vi.mock('../../../services/dataroom.service', () => ({
  dataroomService: {
    ficheClient: (...a: unknown[]) => svc.ficheClient(...a),
    ficheDossier: (...a: unknown[]) => svc.ficheDossier(...a),
    genererFichePdf: (...a: unknown[]) => svc.genererFichePdf(...a),
  },
}));

import { FicheSocieteDrawer } from '../components/FichesSocieteDrawer';
import type { FicheDossierVue } from '../../../types/dataroom';

const vue = (modifiable: boolean): FicheDossierVue => ({
  fiche: {
    dossierId: 'd1',
    cabinet: 'Maghreb Consulting',
    denomination: 'ACME SARL',
    modifiable,
    identifiants: [
      { code: 'DENOMINATION', libelle: 'Dénomination', valeur: 'ACME SARL', provenance: 'Reprise à l’import du dossier', origine: 'OUVERTURE' },
      { code: 'RC', libelle: 'Registre du commerce', valeur: '45678 (tribunal : Casablanca)', provenance: 'Obtenu en fin de parcours (ticket T-0001)', origine: 'FIN_PARCOURS' },
      { code: 'TP', libelle: 'Taxe professionnelle (patente) en vigueur', valeur: 'TP-2222', provenance: 'Saisie manuelle par Salma Alaoui, le 05/03/2026', origine: 'SAISIE' },
      { code: 'CNSS', libelle: 'CNSS', valeur: null, provenance: 'Donnée manquante', origine: null },
    ],
    versionsTp: [
      { numero: 'TP-1111', dateEffet: null, enVigueur: false, saisieLe: '2026-01-10T10:00:00Z', saisiePar: 'Salma Alaoui' },
      { numero: 'TP-2222', dateEffet: '2026-03-01', enVigueur: true, saisieLe: '2026-03-05T10:00:00Z', saisiePar: 'Salma Alaoui' },
    ],
    modifications: [
      { champ: 'taxe_professionnelle', libelle: 'Taxe professionnelle (patente)', ancienneValeur: 'TP-1111', nouvelleValeur: 'TP-2222', provenance: 'Saisie manuelle par Salma Alaoui, le 05/03/2026', auteur: 'Salma Alaoui', date: '2026-03-05T10:00:00Z' },
    ],
  },
  operations: [
    { ticketId: 't1', type: 'CREATION', typeLabel: 'Création de société', sousTypeLabel: null, reference: 'T-0001', dateOuverture: '2026-01-10T10:00:00Z', dateFin: '2026-03-05T10:00:00Z', natureFin: 'CLOTURE', statut: 'CLOTURE_DOSSIER', statutLabel: 'Clôturé', responsable: 'Salma Alaoui' },
    { ticketId: 't3', type: 'PV_AGO', typeLabel: 'Assemblée générale ordinaire', sousTypeLabel: null, reference: 'T-0003', dateOuverture: '2026-06-20T10:00:00Z', dateFin: '2026-07-01T10:00:00Z', natureFin: 'ANNULATION', statut: 'ANNULE', statutLabel: 'Annulé', responsable: 'Non renseigné' },
  ],
  derniereCloture: '2026-03-05T10:00:00Z',
});

beforeEach(() => {
  window.localStorage.clear();
  Object.values(svc).forEach((f) => f.mockReset());
});

const ouvrir = (type: 'client' | 'dossier') =>
  render(<FicheSocieteDrawer open type={type} dossierId="d1" raisonSociale="ACME SARL" onClose={() => {}} onModifier={() => {}} />);

describe('Fiche client', () => {
  it('identifiants, provenance, versions de TP, trace, manquants nommes ; aucune operation', async () => {
    svc.ficheClient.mockResolvedValue(vue(true).fiche);
    ouvrir('client');

    const table = await screen.findByTestId('fiche-identifiants-table');
    expect(svc.ficheClient).toHaveBeenCalledWith('d1');
    expect(svc.ficheDossier).not.toHaveBeenCalled();
    expect(within(table).getByText('Obtenu en fin de parcours (ticket T-0001)')).toBeInTheDocument();
    expect(within(table).getByText('Saisie manuelle par Salma Alaoui, le 05/03/2026')).toBeInTheDocument();
    expect(within(table).getByText('Non renseigné')).toBeInTheDocument();
    expect(screen.getByTestId('fiche-manquants')).toHaveTextContent('CNSS');
    expect(screen.getByText('Non renseignée')).toBeInTheDocument();
    expect(screen.getByText('En vigueur')).toBeInTheDocument();
    expect(screen.getByTestId('fiche-trace')).toHaveTextContent('TP-1111');
    // Document distinct : ni onglets, ni operations du dossier.
    expect(screen.queryByRole('tab')).not.toBeInTheDocument();
    expect(screen.queryByTestId('fiche-operations-table')).not.toBeInTheDocument();
    expect(screen.getByRole('note', { name: 'À quoi sert la fiche client ?' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Modifier les identifiants/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Télécharger la fiche client (PDF)' })).toBeInTheDocument();
    expect(screen.queryByText(/Word/)).not.toBeInTheDocument();
  });

  it('telechargement PDF : dit si la fiche est conservee et sa version', async () => {
    svc.ficheClient.mockResolvedValue(vue(true).fiche);
    svc.genererFichePdf.mockResolvedValue({ conservee: true, version: 3 });
    ouvrir('client');
    await screen.findByTestId('fiche-identifiants-table');
    fireEvent.click(screen.getByTestId('fiche-generer'));
    await waitFor(() => expect(svc.genererFichePdf).toHaveBeenCalledWith('d1', 'fiche-client'));
    expect(await screen.findByRole('status')).toHaveTextContent(
      'La fiche client (PDF) a été téléchargée et conservée dans la Data Room du dossier (version 3)',
    );
  });
});

describe('Fiche dossier', () => {
  it('toutes les operations, annulees comprises, et la derniere cloture ; pas de trace', async () => {
    svc.ficheDossier.mockResolvedValue(vue(false));
    ouvrir('dossier');
    const ops = await screen.findByTestId('fiche-operations-table');
    expect(svc.ficheClient).not.toHaveBeenCalled();
    expect(ops).toHaveTextContent('T-0001');
    expect(ops).toHaveTextContent('T-0003');
    expect(ops).toHaveTextContent(/Annulé le/);
    expect(screen.getByText(/À jour de la dernière clôture/)).toBeInTheDocument();
    expect(screen.getByTestId('fiche-identifiants-table')).toBeInTheDocument();
    expect(screen.queryByTestId('fiche-trace')).not.toBeInTheDocument();
    expect(screen.getByRole('note', { name: 'À quoi sert la fiche dossier ?' })).toBeInTheDocument();
    // Superviseur (non modifiable) : pas de bouton de modification.
    expect(screen.queryByRole('button', { name: /Modifier les identifiants/ })).not.toBeInTheDocument();
  });

  it('telechargement non conserve (superviseur) : le message le dit', async () => {
    svc.ficheDossier.mockResolvedValue(vue(false));
    svc.genererFichePdf.mockResolvedValue({ conservee: false, version: null });
    ouvrir('dossier');
    await screen.findByTestId('fiche-operations-table');
    fireEvent.click(screen.getByRole('button', { name: 'Télécharger la fiche dossier (PDF)' }));
    await waitFor(() => expect(svc.genererFichePdf).toHaveBeenCalledWith('d1', 'fiche-dossier'));
    expect(await screen.findByRole('status')).toHaveTextContent('n’est pas conservée');
  });

  it('un refus du serveur est affiche', async () => {
    svc.ficheDossier.mockRejectedValue(new Error('Seul l’employé responsable du dossier consulte ses fiches.'));
    ouvrir('dossier');
    expect(await screen.findByRole('alert')).toBeInTheDocument();
  });
});
