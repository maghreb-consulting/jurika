import '@testing-library/jest-dom/vitest';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';

/**
 * Lot L6b (E14) : une donnee attendue d'un organisme arrive APRES la fin du parcours.
 *
 * Constate sur la pile (creation complete) : la date du depot legal et le numero de RC
 * s'obtiennent pendant le deroulement de la demarche, donc apres la finalisation. Le parcours
 * termine s'ouvrait en lecture seule et l'etape 7 n'affichait plus les donnees attendues : la
 * date ne pouvait plus etre saisie et l'avis de constitution ne se regenerait jamais (regle des
 * variables : "l'acte se regenere quand elle arrive").
 *
 * Desormais, sur un parcours termine dont le ticket est en cours, l'etape 7 propose la saisie ;
 * quand tout est arrive, l'acte est regenere et depose en Data Room (nouvelle version).
 */
const TEMPLATES = ['STATUTS_SARL', 'ANNONCE_LEGALE_CONSTITUTION'].map((code) => ({
  code, documentKind: code, file: `${code}.docx`, origin: 'cabinet', deprecated: false, placeholderStyle: 'uppercase_dollar',
}));

const { generer, attendues, saisir, depot } = vi.hoisted(() => ({
  generer: vi.fn(async () => ({ blob: new Blob(['regenere']), filename: 'x.docx', donneesAObtenir: [] as string[] })),
  attendues: vi.fn(),
  saisir: vi.fn(),
  depot: vi.fn(async () => ({ id: 'doc-2' })),
}));

vi.mock('../../../../services/workflowDocumentService', async () => {
  const vrai = await vi.importActual<typeof import('../../../../services/workflowDocumentService')>(
    '../../../../services/workflowDocumentService',
  );
  return {
    ...vrai,
    listTemplatesForWorkflow: vi.fn(async () => TEMPLATES),
    generateDocument: generer,
    emplacementClausesLibres: vi.fn(async () => ({ possible: false, motif: 'En attente du cabinet.' })),
  };
});
vi.mock('../../../../services/workflow.service', () => ({
  workflowService: {
    save: vi.fn(async () => ({})), donneesAttendues: attendues, saisirDonneeAttendue: saisir,
    clausesLibres: vi.fn(async () => []), variables: vi.fn(async () => []),
  },
}));
vi.mock('../../../../services/dataroom.service', () => ({
  dataroomService: {
    listBrouillons: vi.fn(async () => []),
    getJuridique: vi.fn(async () => ({
      documentsEnVigueur: [
        { id: 'doc-s', documentType: 'STATUTS', version: 1 },
        { id: 'doc-1', documentType: 'ANNONCE_JAL', version: 1 },
      ],
      historique: [],
    })),
    fetchDocumentBlob: vi.fn(async () => new Blob(['v1'])),
    saveBrouillon: vi.fn(async () => ({ id: 'doc-b' })),
    validerBrouillon: vi.fn(async () => undefined),
    uploadJuridique: depot,
  },
}));
vi.mock('../../../../components/document/DocumentEditor', () => ({ DocumentEditor: () => null }));
vi.mock('../../../../components/document/CollaboraEditor', () => ({ CollaboraEditor: () => null }));

import { Step7Generation } from '../Step7Generation';

const DELAI = { timeout: 10000 };
const data = {
  step1: { denomination: { denomination: 'PARACOSME', formeJuridique: 'SARL' } },
  step2: { siege: { adresse: '101 bd Zerktouni', commune: 'Casablanca', villeGreffe: 'Casablanca', justificatifType: 'BAIL' } },
  step3: { capital: { capitalSocialMad: 100000, nombreParts: 1000, valeurNominale: 100, depotFondsBloque: 'non' } },
  step4: { activite: { description: 'le conseil', activites: ['le conseil'] } },
  step5: { dirigeants: [{ prenom: 'Yassine', nom: 'BENANI', isStatutaire: true }], gerance: { remunerationMode: 'gratuit' } },
  step6: { associes: [{ prenom: 'Yassine', nom: 'BENANI', nombreParts: 1000 }] },
};
const existing = {
  documents: {
    STATUTS_SARL: { generated: true, validated: true, documentId: 'doc-s', version: 1 },
    ANNONCE_LEGALE_CONSTITUTION: { generated: true, validated: true, documentId: 'doc-1', version: 1 },
  },
};
const ATTENDUE = {
  templateCode: 'ANNONCE_LEGALE_CONSTITUTION', workflowCode: 'CREATION_SARL', variable: 'DATE_DEPOT_LEGAL',
  libelle: 'Date du d\u00e9p\u00f4t l\u00e9gal au greffe', reclameeLe: '2026-10-16T05:17:54Z', recue: false,
};

function rendre(completer: boolean) {
  return render(
    <Step7Generation existing={existing} data={data} saving={false} onSubmit={vi.fn(async () => undefined)}
      ticketId="t-1" dossierId="dos-1" readOnly completerDonneesAttendues={completer} />,
  );
}

describe('Etape 7 en lecture seule : donnees attendues apres la fin du parcours', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    attendues.mockResolvedValue([ATTENDUE]);
    saisir.mockResolvedValue([{ ...ATTENDUE, recue: true }]);
  });

  it('propose la saisie de la date du depot legal sur un parcours termine dont le ticket est en cours', async () => {
    rendre(true);
    expect(await screen.findByLabelText('Date du d\u00e9p\u00f4t l\u00e9gal au greffe', undefined, DELAI)).toBeInTheDocument();
  }, 20000);

  it('regenere l\u2019acte et le depose en Data Room quand la donnee arrive', async () => {
    rendre(true);
    const champ = await screen.findByLabelText('Date du d\u00e9p\u00f4t l\u00e9gal au greffe', undefined, DELAI);
    fireEvent.change(champ, { target: { value: '2026-11-05' } });
    const ligne = champ.closest('li') as HTMLElement;
    fireEvent.click(within(ligne).getByRole('button', { name: 'Enregistrer' }));
    await waitFor(() => expect(saisir).toHaveBeenCalledWith('t-1', 'DATE_DEPOT_LEGAL', '2026-11-05'));
    await waitFor(() => expect(generer).toHaveBeenCalledWith('CREATION_SARL', 'ANNONCE_LEGALE_CONSTITUTION', expect.anything()), DELAI);
    await waitFor(() => expect(depot).toHaveBeenCalledWith('dos-1', expect.objectContaining({
      documentType: 'ANNONCE_JAL', ticketId: 't-1',
    })), DELAI);
  }, 30000);

  it('ticket clos (sans le drapeau) : rien a saisir, aucune generation', async () => {
    rendre(false);
    await screen.findByText(/Documents g\u00e9n\u00e9r\u00e9s/, undefined, DELAI);
    expect(screen.queryByLabelText('Date du d\u00e9p\u00f4t l\u00e9gal au greffe')).toBeNull();
    expect(attendues).not.toHaveBeenCalled();
    expect(generer).not.toHaveBeenCalled();
  });
});
