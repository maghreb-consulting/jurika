/// <reference types="vitest" />
import '@testing-library/jest-dom/vitest';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';

/**
 * L'étape 7 à l'écran, sur le corpus du 9 septembre.
 *
 * Trois règles s'y vérifient, sur ce que l'employé voit :
 *  1. **Statuts et Annonce légale sont cochés par défaut**, les huit autres
 *     documents décochés — décision du cabinet (énoncé 4) ;
 *  2. un champ n'apparaît que si le document qui le consomme est retenu, et
 *     disparaît quand on le décoche (énoncé 5) ;
 *  3. régénérer un document modifié à la main avertit NOMMÉMENT.
 */

/** Le manifeste expose les dix documents du statut 2, variantes comprises. */
const TEMPLATES = [
  'STATUTS_SARL',
  'ANNONCE_LEGALE_CONSTITUTION',
  'CONTRAT_BAIL',
  'CONTRAT_DOMICILIATION',
  'ETAT_ACTES_SOCIETE_EN_FORMATION',
  'ACTE_NOMINATION_GERANT',
  'ATTESTATION_SOUSCRIPTION_LIBERATION',
  'POUVOIR_FORMALITES_CREATION',
  'DEMANDE_TAXE_PROFESSIONNELLE',
  'DECLARATION_IMMATRICULATION_RC',
  'DECLARATION_EXISTENCE',
].map((code) => ({
  code,
  documentKind: code,
  file: `${code}.docx`,
  origin: 'cabinet',
  deprecated: false,
  placeholderStyle: 'uppercase_dollar',
}));

vi.mock('../../../../services/workflowDocumentService', async () => {
  const vrai = await vi.importActual<typeof import('../../../../services/workflowDocumentService')>(
    '../../../../services/workflowDocumentService',
  );
  return {
    ...vrai,
    listTemplatesForWorkflow: vi.fn(async () => TEMPLATES),
    generateDocument: vi.fn(async () => ({ blob: new Blob(['x']), filename: 'x.docx', donneesAObtenir: [] })),
    emplacementClausesLibres: vi.fn(async () => ({ possible: false, motif: 'En attente du cabinet.' })),
  };
});
vi.mock('../../../../services/workflow.service', () => ({
  workflowService: { save: vi.fn(async () => ({})), donneesAttendues: vi.fn(async () => []), clausesLibres: vi.fn(async () => []) },
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
  step2: {
    siege: {
      adresse: '101 bd Zerktouni',
      commune: 'Casablanca',
      villeGreffe: 'Casablanca',
      justificatifType: 'BAIL',
    },
  },
  step3: { capital: { capitalSocialMad: 100000, nombreParts: 1000, valeurNominale: 100, depotFondsBloque: 'non' } },
  step4: { activite: { description: 'le conseil', activites: ['le conseil'] } },
  step5: {
    dirigeants: [{
      prenom: 'Yassine', nom: 'BENANI', isStatutaire: false,
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

/** La case d'une ligne du parcours, par son numéro. */
function caseDuDocument(ligne: number): HTMLInputElement {
  return screen
    .getByTestId(`choix-document-${ligne}`)
    .querySelector('input[type="checkbox"]') as HTMLInputElement;
}

describe('Étape 7 — ce qui est coché par défaut (énoncé 4)', () => {
  beforeEach(() => vi.clearAllMocks());

  it('coche Statuts et Annonce légale, et laisse les huit autres décochés', async () => {
    rendre();
    await screen.findByTestId('choix-document-3');

    expect(caseDuDocument(3), 'Statuts').toBeChecked();
    expect(caseDuDocument(11), 'Annonce légale').toBeChecked();

    for (const ligne of [2, 4, 5, 6, 7, 8, 9, 10]) {
      expect(caseDuDocument(ligne), `ligne ${ligne}`).not.toBeChecked();
    }
  });

  it('affiche, sous chaque document, la condition du parcours', async () => {
    rendre();
    await screen.findByTestId('choix-document-5');

    // « Si la gérance n'est pas désignée dans les statuts » : l'employé décide en
    // connaissance de cause, le système ne décide pas à sa place.
    expect(screen.getByTestId('choix-document-5')).toHaveTextContent(
      /gérance n'est pas désignée dans les statuts/i,
    );
    expect(screen.getByTestId('choix-document-4')).toHaveTextContent(
      /engagements ont été pris avant l'immatriculation/i,
    );
  });
});

describe('Étape 7 — les champs suivent les documents retenus (énoncé 5)', () => {
  beforeEach(() => vi.clearAllMocks());

  it('n’affiche AUCUN champ tant que seuls les deux documents par défaut sont retenus', async () => {
    rendre();
    await screen.findByTestId('choix-document-3');

    // Statuts et Annonce légale ne réclament aucune des 160 : tout ce qu'ils
    // consomment est déjà résolu. Le parcours par défaut est donc muet.
    expect(screen.queryByText(/Complements demandes par les documents retenus/i)).toBeNull();
    expect(screen.queryByLabelText(/Taxe professionnelle commune/i)).toBeNull();
  });

  it('fait apparaître les champs quand on retient la demande de taxe professionnelle', async () => {
    rendre();
    await screen.findByTestId('choix-document-8');

    fireEvent.click(caseDuDocument(8));

    expect(await screen.findByLabelText(/Taxe professionnelle commune/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/Effectif prévisionnel/i)).toBeInTheDocument();
  });

  it('fait disparaître les champs quand on décoche le document', async () => {
    rendre();
    await screen.findByTestId('choix-document-8');

    fireEvent.click(caseDuDocument(8));
    await screen.findByLabelText(/Taxe professionnelle commune/i);

    fireEvent.click(caseDuDocument(8));
    await waitFor(() =>
      expect(screen.queryByLabelText(/Taxe professionnelle commune/i)).toBeNull(),
    );
  });

  it('ne demande qu’UNE fois un champ que deux documents partagent', async () => {
    rendre();
    await screen.findByTestId('choix-document-8');

    // Le numéro de pièce du déclarant sert les trois imprimés administratifs.
    fireEvent.click(caseDuDocument(8));
    fireEvent.click(caseDuDocument(9));
    fireEvent.click(caseDuDocument(10));

    await screen.findByLabelText(/Déclarant pièce numéro/i);
    expect(screen.getAllByLabelText(/Déclarant pièce numéro/i)).toHaveLength(1);
  });

  it('rappelle à l’écran ce qui est repris sans ressaisie', async () => {
    rendre();
    await screen.findByTestId('choix-document-8');
    fireEvent.click(caseDuDocument(8));

    expect(await screen.findByText(/Repris automatiquement/i)).toBeInTheDocument();
    expect(
      screen.getByText(/date ET lieu de naissance, qualité, déclarant/i),
    ).toBeInTheDocument();
  });

  it('dit pourquoi aucun champ n’est marqué obligatoire', async () => {
    rendre();
    await screen.findByTestId('choix-document-8');
    fireEvent.click(caseDuDocument(8));

    // Le jugement « phrase ou case » se fait sur le DOCUMENT RENDU, côté serveur.
    // L'écran doit le dire, sinon l'employé croit que tout est facultatif.
    //
    // Le texte est coupé par un <strong> : on interroge donc le contenu du
    // paragraphe entier plutôt qu'un nœud de texte isolé.
    const explication = await screen.findByText((_, element) =>
      element?.tagName === 'P'
      && /milieu d[’']une phrase fait refuser la génération/i.test(element.textContent ?? ''),
    );
    expect(explication).toHaveTextContent(/case d[’']imprimé administratif laissée blanche/i);
  });
});

describe('Étape 7 — les boucles', () => {
  beforeEach(() => vi.clearAllMocks());

  it('ne propose pas de boucle tant qu’aucun document ne la porte', async () => {
    rendre();
    await screen.findByTestId('choix-document-2');
    expect(screen.queryByTestId('boucle-BAIL_LOCAUX')).toBeNull();
  });

  it('propose la boucle des locaux quand le contrat de bail est retenu', async () => {
    rendre();
    await screen.findByTestId('choix-document-2');

    fireEvent.click(caseDuDocument(2));

    const boucle = await screen.findByTestId('boucle-BAIL_LOCAUX');
    // Vide au départ : le document sortira sans la section, ce qui est correct
    // si le dossier n'en comporte pas.
    expect(boucle).toHaveTextContent(/Aucune occurrence/i);

    fireEvent.click(within(boucle).getByRole('button', { name: /Ajouter/i }));
    expect(await screen.findByText(/n° 1/i)).toBeInTheDocument();
  });
});

describe('Étape 7 — régénérer un document modifié à la main', () => {
  beforeEach(() => vi.clearAllMocks());

  it('avertit NOMMÉMENT avant de remplacer les retouches manuelles', async () => {
    rendre({
      lignesRetenues: [3, 9, 11],
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

    const boutons = await screen.findAllByRole('button', { name: /Regenerer/i });
    fireEvent.click(boutons[boutons.length - 1]);

    const dialogue = await waitFor(() => screen.getByTestId('confirmation-regeneration'));
    expect(dialogue).toHaveTextContent(/Déclaration d'immatriculation au RC/i);
    expect(dialogue).toHaveTextContent(/modifié à la main/i);
    expect(dialogue).toHaveTextContent(/5 septembre/i);
  });
});

describe('Étape 7 — la charge utile est construite par le serveur (lot L3, P2)', () => {
  beforeEach(() => vi.clearAllMocks());

  function rendreAvecTicket() {
    return render(
      <Step7Generation existing={{}} data={data} saving={false} onSubmit={vi.fn(async () => undefined)} ticketId="t-1" />,
    );
  }

  it('n’envoie que le ticket : plus de date du jour ni de « Casablanca » inventés', async () => {
    const { generateDocument } = await import('../../../../services/workflowDocumentService');
    rendreAvecTicket();
    const boutons = await screen.findAllByRole('button', { name: /^Generer$/i });
    fireEvent.click(boutons[0]);
    await waitFor(() => expect(generateDocument).toHaveBeenCalled());
    const [workflow, , payload] = (generateDocument as unknown as { mock: { calls: unknown[][] } }).mock.calls[0];
    expect(workflow).toBe('CREATION_SARL');
    expect(payload).toEqual({ ticketId: 't-1' });
  });

  it('enregistre l’étape avant de générer : la saisie arrive au magasin, signature comprise', async () => {
    const { workflowService } = await import('../../../../services/workflow.service');
    const { generateDocument } = await import('../../../../services/workflowDocumentService');
    rendreAvecTicket();
    await screen.findByTestId('choix-document-8');
    fireEvent.click(caseDuDocument(8));
    fireEvent.change(await screen.findByLabelText(/Taxe professionnelle commune/i), {
      target: { value: 'Casablanca-Anfa' },
    });
    fireEvent.change(screen.getByLabelText('Lieu de signature (ville)'), { target: { value: 'Rabat' } });
    fireEvent.change(screen.getByLabelText('Date de signature'), { target: { value: '2026-10-01' } });
    const boutons = await screen.findAllByRole('button', { name: /^Generer$/i });
    fireEvent.click(boutons[0]);
    await waitFor(() => expect(generateDocument).toHaveBeenCalled());
    const save = workflowService.save as unknown as { mock: { calls: unknown[][] } };
    const [ticket, etape, donnees] = save.mock.calls[0] as [string, number, { step7: { complements: Record<string, string> } }];
    expect([ticket, etape]).toEqual(['t-1', 7]);
    expect(donnees.step7.complements).toMatchObject({
      tpCommune: 'Casablanca-Anfa', lieuSignature: 'Rabat', dateSignature: '2026-10-01',
    });
  });

  it('sans ticket, ne génère rien et le dit', async () => {
    const { generateDocument } = await import('../../../../services/workflowDocumentService');
    rendre();
    const boutons = await screen.findAllByRole('button', { name: /^Generer$/i });
    fireEvent.click(boutons[0]);
    expect(await screen.findByText(/rattaché à aucun ticket/)).toBeInTheDocument();
    expect(generateDocument).not.toHaveBeenCalled();
  });
});
