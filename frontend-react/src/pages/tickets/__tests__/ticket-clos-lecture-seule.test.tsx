/// <reference types="vitest" />
import '@testing-library/jest-dom/vitest';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';

/**
 * Lot B — LE DÉTAIL D'UN TICKET CLOS N'OFFRE AUCUNE ACTION (énoncé 15).
 *
 * <p>Le détail est une vue de CONSULTATION. Le cochage se fait dans le workflow,
 * où l'employé a sous les yeux la condition d'application, les justificatifs
 * attendus et le document à déposer ; le même geste posé depuis un tiroir de
 * détail se fait sans ce contexte.
 *
 * <p>Ce test porte sur le PANNEAU, monté tel que le détail le monte —
 * {@code canAct = false} — plutôt que sur le tiroir entier : c'est là que la
 * règle vit, et c'est là qu'une régression la ferait tomber.
 */

vi.mock('../../../services/demarche.service', () => ({
  demarcheService: {
    vue: vi.fn(async () => vue()),
    cocher: vi.fn(),
    decocher: vi.fn(),
    marquerNonApplicable: vi.fn(),
    appliquerConditionGerance: vi.fn(),
    recapitulatif: vi.fn(async () => recapitulatif()),
  },
}));

vi.mock('../../../services/dataroom.service', () => ({
  dataroomService: { uploadJuridique: vi.fn() },
}));

import { DemarchesPanel } from '../DemarchesPanel';
import { RecapitulatifPanel } from '../RecapitulatifPanel';
import type { RecapitulatifCloture, VueDemarches } from '../../../types/demarche';

function vue(): VueDemarches {
  return {
    workflowType: 'CREATION',
    statutCourant: 'CLOTURE_DOSSIER',
    phases: [
      {
        code: 'S3',
        libelle: 'Déroulement de la démarche',
        traitees: 1,
        total: 1,
        demarches: [
          {
            demarcheId: 'd-19',
            ordre: 19,
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
            justificatifsTexte: 'Récépissé de dépôt',
            modeleJurika: null,
            delai: null,
            coutIndicatif: null,
            variablesAlimentees: null,
            delaiCalculable: false,
            justificatifsAttendus: [
              { alternativeGroupe: 1, documentType: 'RECEPISSE_DEPOT', libelle: 'Récépissé' },
            ],
            etat: 'COCHEE',
            motif: null,
            cocheAt: '2026-09-10T09:15:00Z',
            documentsDeposes: ['doc-1'],
            // Le ticket est clos : la démarche ne relève plus du statut courant.
            actionnableMaintenant: false,
            horsSequence: false,
            formaliteCode: 'ENREGISTREMENT_STATUTS',
            formaliteVolet: 'DEPOT',
            depotOrdre: null,
            deposeLe: null,
            journal: [
              {
                id: 'e1', ticketDemarcheId: 'td-19', type: 'COCHAGE', motif: null,
                acteurId: 'u-1', survenuLe: '2026-09-10T09:15:00Z', justificatifs: 1,
              },
            ],
          },
        ],
      },
    ],
    avancement: {
      statutCourant: 'CLOTURE_DOSSIER',
      position: 4,
      totalStatuts: 4,
      traitees: 1,
      applicables: 1,
      prochainOrdre: null,
      prochainLibelle: null,
      pointsAttention: [],
    },
  };
}

function recapitulatif(): RecapitulatifCloture {
  return {
    ticketId: 't-1',
    reference: 'T-2026-00841',
    statut: 'CLOTURE_DOSSIER',
    clotureEnvisageable: false,
    documents: [
      { id: 'doc-1', documentType: 'STATUTS', titre: 'Statuts PARACOSME', visibleClient: true },
      { id: 'doc-2', documentType: 'AUTRE', titre: 'Note interne', visibleClient: false },
    ],
    demarches: [
      {
        ordre: 19,
        libelle: 'Enregistrement des statuts — dépôt',
        etat: 'COCHEE',
        cocheAt: '2026-09-10T09:15:00Z',
        motif: null,
        journal: [],
      },
    ],
    justificatifs: [
      {
        ordreDemarche: 28,
        libelleDemarche: 'Immatriculation — retrait du modèle J',
        groupe: 1,
        alternatives: ['RC'],
        libelle: "Certificat d'immatriculation",
        archive: false,
        documentId: null,
      },
    ],
    manquants: ["Ligne 28 — Immatriculation — retrait du modèle J : Certificat d'immatriculation (RC)"],
    piecesSelonLeParcours: 'Certificat négatif ; contrat de bail enregistré ; …',
    identifiants: {
      rcNumero: 'RC 445221',
      identifiantFiscal: 'IF 40221188',
      ice: '001234567000089',
      taxeProfessionnelle: null,
      cnss: null,
    },
  };
}

/**
 * Sur un ticket clos, aucune démarche n'est actionnable : les phases s'affichent
 * donc repliées, et c'est voulu — la vue résumée, c'est le récapitulatif. On
 * déplie explicitement pour inspecter ce que la ligne offre, ou n'offre pas.
 */
async function deplier() {
  const phase = await screen.findByTestId('phase-S3');
  fireEvent.click(phase.querySelector('button') as HTMLButtonElement);
  return screen.findByTestId('demarche-19');
}

describe('Le détail d’un ticket clos n’offre aucune action — énoncé 15', () => {
  beforeEach(() => vi.clearAllMocks());

  it('n’affiche ni cochage, ni annulation, ni mise hors périmètre', async () => {
    render(<DemarchesPanel ticketId="t-1" dossierId="dos-1" canAct={false} />);
    await deplier();

    for (const action of [/^Cocher$/i, /Annuler le cochage/i, /Non applicable/i,
      /Remettre au périmètre/i]) {
      expect(screen.queryByRole('button', { name: action }), String(action)).toBeNull();
    }
  });

  it('n’offre aucun dépôt de pièce, ni réglage de visibilité', async () => {
    const { container } = render(
      <DemarchesPanel ticketId="t-1" dossierId="dos-1" canAct={false} />,
    );
    await deplier();

    expect(container.querySelector('input[type="file"]')).toBeNull();
    expect(container.querySelector('input[type="checkbox"]')).toBeNull();
  });

  it('montre en revanche TOUT : l’état, la date, et le journal', async () => {
    render(<DemarchesPanel ticketId="t-1" dossierId="dos-1" canAct={false} />);
    const ligne = await deplier();
    expect(ligne).toHaveTextContent(/Enregistrement des statuts/);
    // Le journal reste consultable : « quand avons-nous déposé ? » doit avoir une
    // réponse, précisément sur un ticket clos depuis des mois.
    expect(ligne).toHaveTextContent(/Cochée le/);
  });
});

describe('Le récapitulatif d’un ticket clos', () => {
  beforeEach(() => vi.clearAllMocks());

  it('liste les justificatifs manquants, nommément — énoncé 14', async () => {
    render(<RecapitulatifPanel ticketId="t-1" />);

    const manquants = await screen.findByTestId('justificatifs-manquants');
    expect(manquants).toHaveTextContent(/Ligne 28/);
    expect(manquants).toHaveTextContent(/Certificat d'immatriculation/);
  });

  it('montre les identifiants obtenus, et dit lesquels manquent', async () => {
    render(<RecapitulatifPanel ticketId="t-1" />);

    const panneau = await screen.findByTestId('recapitulatif-ticket');
    expect(panneau).toHaveTextContent(/RC 445221/);
    expect(panneau).toHaveTextContent(/Identifiants obtenus — 3 \/ 5/);
    expect(panneau).toHaveTextContent(/— non obtenu/);
  });

  it('signale les documents que le client ne voit pas', async () => {
    render(<RecapitulatifPanel ticketId="t-1" />);

    const panneau = await screen.findByTestId('recapitulatif-ticket');
    // Remettre un dossier dont des pièces restent masquées est une décision :
    // le récapitulatif ne la laisse pas passer en silence.
    expect(panneau).toHaveTextContent(/Note interne/);
    expect(panneau).toHaveTextContent(/masqué/);
  });

  it('n’offre aucune action — la clôture se décide ailleurs', async () => {
    render(<RecapitulatifPanel ticketId="t-1" />);
    await screen.findByTestId('recapitulatif-ticket');

    expect(screen.queryAllByRole('button')).toHaveLength(0);
  });
});
