/// <reference types="vitest" />
import '@testing-library/jest-dom/vitest';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

/**
 * Lot 5 (2026-09-07) — l'étape 7 à l'écran.
 *
 * Deux règles s'y vérifient, sur ce que l'employé voit :
 *  1. un champ complémentaire n'apparaît que si le document qui le consomme est
 *     retenu (règle posée par l'utilisateur) ;
 *  2. régénérer un document modifié à la main avertit NOMMÉMENT, y compris pour
 *     les trois documents ajoutés par ce lot.
 */

const TEMPLATES = [
  { code: 'STATUTS_SARL_DIRECTEUR', documentKind: 'Statuts', file: 'a.docx', origin: 'd', deprecated: false, placeholderStyle: 's' },
  { code: 'ANNONCE_LEGALE_DIRECTEUR', documentKind: 'Annonce', file: 'b.docx', origin: 'd', deprecated: false, placeholderStyle: 's' },
  { code: 'DEMANDE_TAXE_PROFESSIONNELLE', documentKind: 'TP', file: 'c.docx', origin: 'd', deprecated: false, placeholderStyle: 's' },
  { code: 'DECLARATION_EXISTENCE', documentKind: 'DE', file: 'd.docx', origin: 'd', deprecated: false, placeholderStyle: 's' },
  { code: 'DECLARATION_IMMATRICULATION_RC', documentKind: 'RC', file: 'e.docx', origin: 'd', deprecated: false, placeholderStyle: 's' },
];

vi.mock('../../../../services/workflowDocumentService', () => ({
  listTemplatesForWorkflow: vi.fn(async () => TEMPLATES),
  generateDocument: vi.fn(async () => ({ blob: new Blob(['x']), filename: 'x.docx' })),
}));

vi.mock('../../../../services/dataroom.service', () => ({
  dataroomService: {
    listBrouillons: vi.fn(async () => []),
    getJuridique: vi.fn(async () => ({ documentsEnVigueur: [], historique: [] })),
    fetchDocumentBlob: vi.fn(async () => new Blob(['x'])),
    saveBrouillon: vi.fn(async () => ({ id: 'doc-1' })),
    validerBrouillon: vi.fn(async () => undefined),
  },
}));

vi.mock('../../../../components/document/DocumentEditor', () => ({
  DocumentEditor: () => null,
}));
vi.mock('../../../../components/document/CollaboraEditor', () => ({
  CollaboraEditor: () => null,
}));

import { Step7Generation } from '../Step7Generation';

/** Dossier complet : toutes les étapes 1 à 6 validées (aucun préflight bloquant). */
const data = {
  step1: { denomination: { denomination: 'PARACOSME', formeJuridique: 'SARL' } },
  step2: { siege: { adresse: '101 bd Zerktouni', commune: 'Casablanca', villeGreffe: 'Casablanca' } },
  step3: { capital: { capitalSocialMad: 100000, nombreParts: 1000, valeurNominale: 100, depotFondsBloque: 'non' } },
  step4: { activite: { description: 'le conseil', activites: ['le conseil'] } },
  step5: {
    dirigeants: [{
      prenom: 'Yassine', nom: 'BENANI', isStatutaire: false,
      // Mandat PROPRE au dirigeant, tel que l'étape 5 le persiste.
      dureeMandatType: 'determinee', dureeAnnees: 3,
    }],
    gerance: { dureeMandat: '3 année(s)', dureeGerance: '3 année(s)', remunerationMode: 'gratuit' },
  },
  step6: { associes: [{ prenom: 'Yassine', nom: 'BENANI', nombreParts: 1000 }] },
};

function rendre(existing: Record<string, unknown> = {}) {
  return render(
    <Step7Generation
      existing={existing}
      data={data}
      saving={false}
      onSubmit={vi.fn(async () => undefined)}
    />,
  );
}

describe('Étape 7 — les champs suivent les documents retenus', () => {
  beforeEach(() => vi.clearAllMocks());

  it('affiche les champs de la déclaration d’existence tant qu’elle est retenue', async () => {
    rendre();
    // Les documents obligatoires sont retenus d'office : les champs sont là.
    expect(await screen.findByLabelText(/Régime de détermination du résultat/i))
      .toBeInTheDocument();
    expect(screen.getByLabelText(/Enseigne commerciale/i)).toBeInTheDocument();
  });

  it('les champs disparaissent quand leur document est écarté', async () => {
    rendre({ documentsEcartes: ['DECLARATION_EXISTENCE'] });
    await screen.findByText(/Documents a generer/i);
    expect(screen.queryByLabelText(/Régime de détermination du résultat/i)).toBeNull();
    // Ceux du modèle 2, lui toujours retenu, restent affichés.
    expect(screen.getByLabelText(/Enseigne commerciale/i)).toBeInTheDocument();
  });

  it('ne demande le téléphone qu’une seule fois, bien qu’il serve deux imprimés', async () => {
    rendre();
    await screen.findByText(/Documents a generer/i);
    expect(screen.getAllByLabelText(/Téléphone de la société/i)).toHaveLength(1);
  });

  it('rappelle à l’écran ce qui est repris sans ressaisie', async () => {
    rendre();
    expect(await screen.findByText(/Repris automatiquement/i)).toBeInTheDocument();
    expect(
      screen.getByText(/date ET lieu de naissance, qualité, déclarant/i),
    ).toBeInTheDocument();
  });
});

describe('Étape 7 — régénérer un formulaire modifié à la main', () => {
  beforeEach(() => vi.clearAllMocks());

  it('avertit NOMMÉMENT avant de remplacer les retouches manuelles', async () => {
    rendre({
      documents: {
        DECLARATION_IMMATRICULATION_RC: {
          generated: true,
          validated: false,
          version: 2,
          documentId: 'doc-rc',
          editeManuellementAt: '2026-09-05T10:30:00Z',
        },
      },
    });

    // La carte du document régénérable est là.
    const boutons = await screen.findAllByRole('button', { name: /Regenerer/i });
    fireEvent.click(boutons[boutons.length - 1]);

    const dialogue = await waitFor(() =>
      screen.getByTestId('confirmation-regeneration'),
    );
    // L'avertissement NOMME le document concerné, et dit quand il a été retouché.
    expect(dialogue).toHaveTextContent(/Déclaration d'immatriculation au RC/i);
    expect(dialogue).toHaveTextContent(/modifié à la main/i);
    expect(dialogue).toHaveTextContent(/5 septembre/i);
  });
});

describe('Étape 7 — la durée du mandat arrive jusqu’au générateur', () => {
  beforeEach(() => vi.clearAllMocks());

  it('transmet la durée saisie à l’étape 5, au lieu de la laisser tomber', async () => {
    const { generateDocument } = await import('../../../../services/workflowDocumentService');
    rendre();
    // L'acte de nomination est proposé : le gérant n'est pas statutaire.
    const boutons = await screen.findAllByRole('button', { name: /^Generer$/i });
    fireEvent.click(boutons[0]);

    await waitFor(() => expect(generateDocument).toHaveBeenCalled());
    // generateDocument(workflowCode, templateCode, payload)
    const [, , payload] = (generateDocument as unknown as {
      mock: { calls: unknown[][] };
    }).mock.calls[0] as [string, string, Record<string, never>];

    // Le mandat PROPRE au dirigeant — c'est lui que le moteur scope par gérant.
    const gerants = (payload as unknown as {
      gerants: { dureeMandat?: string }[];
    }).gerants;
    expect(gerants[0].dureeMandat).toBe('3 année(s)');

    // Et le repli au niveau société, pour les brouillons antérieurs.
    const societe = (payload as unknown as {
      societe: { dureeGerance?: string };
    }).societe;
    expect(societe.dureeGerance).toBe('3 année(s)');
  });

  it('ne renvoie JAMAIS la durée de la société comme durée de mandat', async () => {
    const { generateDocument } = await import('../../../../services/workflowDocumentService');
    rendre();
    const boutons = await screen.findAllByRole('button', { name: /^Generer$/i });
    fireEvent.click(boutons[0]);
    await waitFor(() => expect(generateDocument).toHaveBeenCalled());
    // generateDocument(workflowCode, templateCode, payload)
    const [, , payload] = (generateDocument as unknown as {
      mock: { calls: unknown[][] };
    }).mock.calls[0] as [string, string, Record<string, never>];
    const societe = (payload as unknown as {
      societe: { dureeGerance?: string; dureeAnnees?: number };
    }).societe;
    // 99 est la durée de la SOCIÉTÉ : elle ne doit pas tenir lieu de mandat.
    expect(societe.dureeAnnees).toBe(99);
    expect(societe.dureeGerance).not.toBe('99 années');
    expect(societe.dureeGerance).not.toBe(99);
  });
});
