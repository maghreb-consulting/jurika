import { act, fireEvent, render, renderHook, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it } from 'vitest';
import { ConvocationPanel, FeuillePresencePanel } from '../SeanceDocPanels';
import { SeanceFormFields } from '../SeanceForm';
import {
  emptyAssocie,
  useSeanceForm,
  type SeanceDefaults,
  type SeanceSections,
} from '../seanceFormEtat';

/**
 * L4d (E11b) : convocation et feuille de presence rendues par les modeles du corpus.
 * Les donnees de ces modeles se saisissent a l'ecran, avec leur aide ; rien n'est suppose.
 */
/** Ce que la charge utile porte pour les modeles du corpus. */
type Charge = {
  documentsJoints: string[];
  convocation: Record<string, string>;
  comptes: Record<string, string>;
  exercice: Record<string, string>;
};

const societe = { denomination: 'PARACOSME', villeGreffe: 'Casablanca', nombreParts: 1000 };

const CONVOCATION: SeanceSections = {
  seance: true, convocation: true, convocationRang: true, ordreDuJour: true, documentsJoints: true,
  resolutions: true, presence: true, convocationCorpus: true,
};
const FEUILLE: SeanceSections = {
  seance: true, bureau: true, convocation: true, convocationRang: true, presence: true, voix: true,
  feuilleCorpus: true,
};

// Listes stables, comme celles des pages hotes (une nouvelle liste a chaque rendu relance
// la synchronisation des associes et des gerants).
const GERANTS = [{ civilite: 'M.', prenom: 'Karim', nom: 'BENALI' }];
const ASSOCIES = [{ ...emptyAssocie(), prenom: 'Sofia', nom: 'ALAOUI', presence: 'représenté' as const }];

function Formulaire({ sections, defaults }: { sections: SeanceSections; defaults?: SeanceDefaults }) {
  const form = useSeanceForm({
    formeJuridique: 'SARL',
    societe,
    defaults,
    initialGerants: GERANTS,
    initialAssocies: ASSOCIES,
  });
  return <SeanceFormFields form={form} sections={sections} />;
}

beforeEach(() => {
  window.localStorage.clear();
});

describe('SARL à associé unique : ni convocation ni feuille de présence', () => {
  it('le panneau de convocation explique l’absence de convocation (article 76)', () => {
    render(<ConvocationPanel formeJuridique="SARL_AU" societe={societe} />);
    expect(screen.queryByTestId('seance-doc-toggle-CONVOCATION_AG')).not.toBeInTheDocument();
    expect(screen.getByTestId('seance-doc-sans-objet-convocation')).toHaveTextContent(/article 76/);
  });

  it('le panneau de la feuille de présence aussi', () => {
    render(<FeuillePresencePanel formeJuridique="SARL_AU" societe={societe} />);
    expect(screen.queryByTestId('seance-doc-toggle-FEUILLE_PRESENCE_AG')).not.toBeInTheDocument();
    expect(screen.getByTestId('seance-doc-sans-objet-feuille')).toHaveTextContent(/registre/);
  });
});

describe('Charge utile des modèles du corpus', () => {
  it('rien n’est supposé : réponses oui / non vides, aucun document pré-rempli', () => {
    const { result } = renderHook(() => useSeanceForm({ formeJuridique: 'SARL', societe }));
    const p = result.current.buildPayload() as unknown as Charge;
    expect(p.documentsJoints).toEqual([]);
    expect(p.convocation.pouvoirJoint).toBe('');
    expect(p.convocation.auteurQualite).toBe('');
    expect(p.convocation.auteurType).toBe('');
    expect(p.comptes.cacDesigne).toBe('');
    expect(p.comptes.quitusGerantVote).toBe('');
    expect(p.exercice).toEqual({ dateDebut: '', dateCloture: '' });
  });

  it('la date de clôture saisie en amont est reprise ; les réponses saisies partent', () => {
    const { result } = renderHook(() =>
      useSeanceForm({ formeJuridique: 'SARL', societe, defaults: { exerciceClos: '2025-12-31' } }));
    act(() => {
      result.current.setConvCorpus({ pouvoirJoint: 'oui', clauseContraireTexte: 'texte' });
      result.current.setComptes({ cacDesigne: 'non', reserveLegale: 'non', reserveLegaleTaux: '5 %' });
    });
    const p = result.current.buildPayload() as unknown as Charge;
    expect(p.exercice.dateCloture).toBe('2025-12-31');
    expect(p.convocation.pouvoirJoint).toBe('oui');
    // Texte de clause et quotite de reserve : seulement si la reponse les appelle.
    expect(p.convocation.clauseContraireTexte).toBe('');
    expect(p.comptes.reserveLegaleTaux).toBe('');
    expect(p.comptes.cacDesigne).toBe('non');
  });
});

describe('Écran de la convocation', () => {
  it('assemblée ordinaire : comptes de l’exercice, auteur proposé (premier gérant), aides', () => {
    render(<Formulaire sections={CONVOCATION} defaults={{ type: 'ordinaire' }} />);
    expect(screen.getByRole('note', { name: 'Quelle lettre de convocation ?' })).toBeInTheDocument();
    expect(screen.getByRole('note', { name: 'Assemblée ordinaire : les comptes de l’exercice' })).toBeInTheDocument();
    expect(screen.getByLabelText(/Auteur de la convocation/, { selector: 'input' })).toHaveValue('M. Karim BENALI');
    expect(screen.getByLabelText(/Convoquée par/, { selector: 'select' })).toBeInTheDocument();
    expect(screen.getByLabelText(/Commissaire aux comptes en fonction/)).toBeInTheDocument();
    expect(screen.getByLabelText(/Formule de pouvoir jointe/, { selector: 'select' })).toBeInTheDocument();
    // Avant l'assemblee, la presence est sans objet.
    expect(screen.queryByLabelText(/Présence \*/)).not.toBeInTheDocument();
  });

  it('seconde convocation : la première assemblée se saisit, avec son aide', () => {
    render(<Formulaire sections={CONVOCATION} defaults={{ type: 'extraordinaire', convocationRang: 'deuxième' }} />);
    expect(screen.getByRole('note', { name: 'Seconde convocation : la première assemblée' })).toBeInTheDocument();
    expect(screen.getByLabelText(/Mode d’envoi de la première convocation/)).toBeInTheDocument();
    expect(screen.getByLabelText(/Pouvoirs de la première assemblée valables/)).toBeInTheDocument();
    // La presence est celle de la premiere assemblee.
    expect(screen.getByLabelText(/Présence \*/)).toBeInTheDocument();
    expect(screen.queryByText('Exercice et comptes (assemblée annuelle)')).not.toBeInTheDocument();
  });

  it('envoi par voie électronique : l’adresse de chaque associé est demandée', () => {
    render(<Formulaire sections={CONVOCATION} defaults={{ type: 'extraordinaire' }} />);
    expect(screen.queryByLabelText(/Adresse électronique/)).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText(/Mode de convocation/, { selector: 'select' }), { target: { value: 'voie électronique' } });
    expect(screen.getByLabelText(/Adresse électronique/)).toBeInTheDocument();
  });
});

describe('Écran de la feuille de présence', () => {
  it('associé représenté : la date du pouvoir est demandée', () => {
    render(<Formulaire sections={FEUILLE} defaults={{ type: 'extraordinaire' }} />);
    expect(screen.getByLabelText(/Date du pouvoir/)).toBeInTheDocument();
    expect(screen.queryByText('Exercice et comptes (assemblée annuelle)')).not.toBeInTheDocument();
  });

  it('assemblée ordinaire : clôture de l’exercice et commissaire aux comptes, sans les rubriques de la convocation', () => {
    render(<Formulaire sections={FEUILLE} defaults={{ type: 'ordinaire' }} />);
    expect(screen.getByLabelText(/Clôture de l’exercice/)).toBeInTheDocument();
    expect(screen.queryByLabelText(/Ouverture de l’exercice/)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/Distribution de dividendes/)).not.toBeInTheDocument();
  });
});
