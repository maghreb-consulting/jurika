/// <reference types="vitest" />
import '@testing-library/jest-dom/vitest';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';

/**
 * Lot B — LE PANNEAU DE COCHAGE, À L'ÉCRAN.
 *
 * Trois choses s'y vérifient, et toutes portent sur ce que l'employé fait, pas
 * sur l'absence d'erreur :
 *
 *  1. **annuler un cochage exige un motif** (énoncé 9) — le bouton n'agit pas
 *     seul, il ouvre un tiroir, et le tiroir refuse un motif vide ;
 *  2. **le journal se lit** : les deux horodatages y sont, celui du cochage et
 *     celui de l'annulation ;
 *  3. **la visibilité client se règle AU DÉPÔT** (énoncé 10), et part du
 *     référentiel, pas d'un choix de l'employé sur le type.
 */

const decocher = vi.fn(async () => vueDeBase());
const cocher = vi.fn(async () => vueDeBase());
const uploadJuridique = vi.fn(async () => ({ id: 'doc-1' }));

vi.mock('../../../services/demarche.service', () => ({
  demarcheService: {
    vue: vi.fn(async () => vueDeBase()),
    cocher: (...args: unknown[]) => cocher(...(args as [])),
    decocher: (...args: unknown[]) => decocher(...(args as [])),
    marquerNonApplicable: vi.fn(async () => vueDeBase()),
    appliquerConditionGerance: vi.fn(async () => vueDeBase()),
    recapitulatif: vi.fn(async () => ({})),
  },
}));

vi.mock('../../../services/dataroom.service', () => ({
  dataroomService: {
    uploadJuridique: (...args: unknown[]) => uploadJuridique(...(args as [])),
  },
}));

import { DemarchesPanel } from '../DemarchesPanel';
import type { LigneDemarche, VueDemarches } from '../../../types/demarche';

const COCHE_LE = '2026-09-10T09:15:00Z';
const ANNULE_LE = '2026-09-11T14:03:00Z';

function ligne(partial: Partial<LigneDemarche>): LigneDemarche {
  return {
    demarcheId: 'd-' + (partial.ordre ?? 1),
    ordre: partial.ordre ?? 1,
    phaseCode: 'S3',
    phaseLibelle: 'Déroulement de la démarche',
    libelle: 'Enregistrement des statuts — dépôt',
    statutTicket: 'DEROULEMENT_DEMARCHE',
    acteur: null,
    organisme: null,
    obligatoire: true,
    conditionApplication: 'Tous dossiers',
    piecesEntrantes: null,
    documentProduit: null,
    justificatifsTexte: 'Récépissé de dépôt à l’enregistrement',
    modeleJurika: null,
    delai: 'Dans les 30 jours de l’acte',
    coutIndicatif: null,
    variablesAlimentees: null,
    delaiCalculable: false,
    justificatifsAttendus: [
      { alternativeGroupe: 1, documentType: 'RECEPISSE_DEPOT', libelle: 'Récépissé de dépôt' },
    ],
    etat: 'A_FAIRE',
    motif: null,
    cocheAt: null,
    documentsDeposes: [],
    actionnableMaintenant: true,
    horsSequence: false,
    formaliteCode: null,
    formaliteVolet: null,
    depotOrdre: null,
    deposeLe: null,
    journal: [],
    delaiDepartManquant: null,
    ...partial,
  };
}

function vueDeBase(): VueDemarches {
  return {
    workflowType: 'CREATION',
    statutCourant: 'DEROULEMENT_DEMARCHE',
    phases: [
      {
        code: 'S3',
        libelle: 'Déroulement de la démarche',
        traitees: 1,
        total: 2,
        demarches: [
          ligne({
            ordre: 19,
            formaliteCode: 'ENREGISTREMENT_STATUTS',
            formaliteVolet: 'DEPOT',
            etat: 'COCHEE',
            cocheAt: COCHE_LE,
            journal: [
              {
                id: 'e1', ticketDemarcheId: 'td-19', type: 'COCHAGE', motif: null,
                acteurId: 'u-1', survenuLe: COCHE_LE, justificatifs: 1,
              },
              {
                id: 'e2', ticketDemarcheId: 'td-19', type: 'ANNULATION',
                motif: 'Récépissé illisible, redépôt demandé',
                acteurId: 'u-1', survenuLe: ANNULE_LE, justificatifs: 0,
              },
            ],
          }),
          ligne({
            ordre: 20,
            libelle: 'Enregistrement des statuts — retrait',
            formaliteCode: 'ENREGISTREMENT_STATUTS',
            formaliteVolet: 'RETRAIT',
            depotOrdre: 19,
            deposeLe: COCHE_LE,
            justificatifsAttendus: [
              { alternativeGroupe: 1, documentType: 'STATUTS', libelle: 'Statuts enregistrés' },
            ],
          }),
          // Lot B — la taxe professionnelle : son délai est calculable, mais il
          // part d'une DATE SAISIE qui n'est pas encore là.
          ligne({
            ordre: 23,
            libelle: 'Demande d’inscription à la taxe professionnelle — dépôt',
            delai: 'Dans les 30 jours du début d’activité',
            delaiCalculable: true,
            delaiDepartManquant: 'DATE_DEBUT_ACTIVITE',
            formaliteCode: 'TAXE_PROFESSIONNELLE',
            formaliteVolet: 'DEPOT',
          }),
        ],
      },
    ],
    avancement: {
      statutCourant: 'DEROULEMENT_DEMARCHE',
      position: 3,
      totalStatuts: 4,
      traitees: 1,
      applicables: 2,
      prochainOrdre: 20,
      prochainLibelle: 'Enregistrement des statuts — retrait',
      pointsAttention: [],
    },
  };
}

function rendre() {
  return render(<DemarchesPanel ticketId="t-1" dossierId="dos-1" canAct />);
}

describe('Annuler un cochage exige un motif — énoncé 9', () => {
  beforeEach(() => vi.clearAllMocks());

  it('n’annule rien au clic : le tiroir s’ouvre et rappelle que la date est conservée', async () => {
    rendre();
    fireEvent.click(await screen.findByRole('button', { name: /Annuler le cochage/i }));

    expect(decocher).not.toHaveBeenCalled();
    expect(await screen.findByText(/horodatage du cochage est/i)).toBeInTheDocument();
    expect(screen.getByText(/garde les deux dates/i)).toBeInTheDocument();
  });

  it('refuse un motif vide, sans appeler le serveur', async () => {
    rendre();
    fireEvent.click(await screen.findByRole('button', { name: /Annuler le cochage/i }));

    // Deux boutons portent ce nom : celui de la ligne et celui du tiroir. Le
    // dernier rendu est celui du tiroir.
    const boutons = await screen.findAllByRole('button', { name: /^Annuler le cochage$/i });
    fireEvent.click(boutons[boutons.length - 1]);

    expect(await screen.findByText(/Motif obligatoire/i)).toBeInTheDocument();
    expect(decocher).not.toHaveBeenCalled();
  });

  it('transmet le motif au serveur quand il est saisi', async () => {
    rendre();
    fireEvent.click(await screen.findByRole('button', { name: /Annuler le cochage/i }));

    fireEvent.change(await screen.findByLabelText(/^Motif$/i), {
      target: { value: 'Récépissé illisible' },
    });
    const boutons = screen.getAllByRole('button', { name: /^Annuler le cochage$/i });
    fireEvent.click(boutons[boutons.length - 1]);

    await waitFor(() =>
      expect(decocher).toHaveBeenCalledWith('t-1', 19, 'Récépissé illisible'),
    );
  });
});

describe('Le journal se lit — les deux horodatages y sont', () => {
  beforeEach(() => vi.clearAllMocks());

  it('annonce le nombre d’événements et déplie les deux dates', async () => {
    rendre();

    const resume = await screen.findByRole('button', { name: /2 événements/i });
    fireEvent.click(resume);

    // « Cochage annulé » apparaît deux fois : dans le résumé replié et dans le
    // détail. C'est voulu — le résumé dit le dernier geste sans qu'on déplie.
    expect(await screen.findAllByText(/Cochage annulé/)).not.toHaveLength(0);
    expect(screen.getByText(/^Cochée$/)).toBeInTheDocument();
    expect(screen.getByText(/Récépissé illisible, redépôt demandé/)).toBeInTheDocument();
  });
});

describe('La ligne de retrait dit depuis quand elle attend — énoncé 12', () => {
  beforeEach(() => vi.clearAllMocks());

  it('affiche la date du dépôt, et la ligne d’où elle vient', async () => {
    rendre();

    const retrait = await screen.findByTestId('demarche-20');
    expect(retrait).toHaveTextContent(/Déposé le/i);
    expect(retrait).toHaveTextContent(/ligne 19/i);
    expect(retrait).toHaveTextContent(/en attente du retrait/i);
  });
});

describe('La visibilité client se règle au dépôt — énoncé 10', () => {
  beforeEach(() => vi.clearAllMocks());

  it('propose la case, cochée par défaut, sur une démarche qui attend une pièce', async () => {
    rendre();

    const retrait = await screen.findByTestId('demarche-20');
    const visible = retrait.querySelector(
      'input[type="checkbox"]',
    ) as HTMLInputElement;
    expect(visible).toBeChecked();
  });

  it('transmet la visibilité au dépôt, avec le type issu du référentiel', async () => {
    rendre();
    const retrait = await screen.findByTestId('demarche-20');

    // On décoche : cette pièce-là ne sera pas montrée au client.
    const visible = retrait.querySelector('input[type="checkbox"]') as HTMLInputElement;
    fireEvent.click(visible);

    const fichier = retrait.querySelector('input[type="file"]') as HTMLInputElement;
    fireEvent.change(fichier, {
      target: { files: [new File(['x'], 'statuts.pdf', { type: 'application/pdf' })] },
    });
    fireEvent.click(within(retrait).getByRole('button', { name: /^Cocher$/i }));

    await waitFor(() => expect(uploadJuridique).toHaveBeenCalled());
    const [, params] = uploadJuridique.mock.calls[0] as [string, Record<string, unknown>];
    expect(params.documentType)
      .toBe('STATUTS');   // le type vient du référentiel, jamais de l'employé
    expect(params.visibleClient).toBe(false);
  });
});

/**
 * Lot B — DEUX DÉLAIS LÉGAUX QUI CESSENT D'ÊTRE AVEUGLES.
 *
 * La taxe professionnelle et l'affiliation CNSS courent « dans les 30 jours du
 * début d'activité ». Ce point de départ n'était pas mécanisable : aucune ligne
 * cochable ne le porte, et le produit disait — à raison — qu'il ne savait pas
 * calculer. Le champ existe maintenant.
 *
 * Ce qui se vérifie ici est la DIFFÉRENCE entre les deux silences. « Je ne sais
 * pas calculer » et « j'attends une date » ne se disent pas de la même façon :
 * le second nomme le geste qui débloque l'alerte, et ce geste est une saisie.
 */
describe('Un délai qui attend une saisie le dit, et dit quoi', () => {
  beforeEach(() => vi.clearAllMocks());

  it('nomme la date attendue plutôt que de rester muet', async () => {
    rendre();
    const taxe = await screen.findByTestId('demarche-23');

    expect(within(taxe).getByText(/Dans les 30 jours du début d’activité/)).toBeInTheDocument();
    expect(within(taxe).getByText(/la date de début d’activité/)).toBeInTheDocument();
    expect(within(taxe).getByText(/n’est pas renseignée/)).toBeInTheDocument();
  });

  it('ne dit PAS « le guide ne permet pas de déterminer le point de départ »', async () => {
    rendre();
    const taxe = await screen.findByTestId('demarche-23');

    // Ce message-là reste vrai pour la ligne 15 — la signature du contrat de
    // siège —, mais il est devenu faux ici : le produit sait calculer.
    expect(within(taxe).queryByText(/le guide ne permet pas/i)).not.toBeInTheDocument();
  });
});
