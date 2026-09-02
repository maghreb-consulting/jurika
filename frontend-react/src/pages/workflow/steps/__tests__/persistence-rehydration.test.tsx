/// <reference types="vitest" />
import '@testing-library/jest-dom/vitest';
import { describe, it, expect, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { Step1Denomination } from '../Step1Denomination';
import { Step5Dirigeants } from '../Step5Dirigeants';

/**
 * Persistance « bulletproof » 2026-08 — Preuve « 0 champ perdu » au retour sur
 * une étape. On rend chaque composant avec la forme `existing` telle que le
 * backend la persiste (executeStep) ET telle que l'autosave la persiste (draft),
 * et on vérifie que chaque champ (y compris upload CN, gérance, signataires) est
 * bien ré-hydraté.
 */

describe('Step1 — ré-hydratation « 0 champ perdu »', () => {
  it('forme VALIDÉE (denomination nichée) : champs + CN « déjà importé » restitués', () => {
    // Forme produite par executeStep : `denomination` est un OBJET.
    const existing = {
      denomination: {
        denomination: 'PARACOSME',
        ice: '000 000 000 000 000',
        cnNumero: 'CN-2026-001',
        cnDate: '2026-01-10',
        activiteCn: 'conseil',
        beneficiaire: 'M. BENANI',
        formeJuridique: 'SARL',
      },
      formeJuridique: 'SARL',
      cnArchivedDocumentId: 'doc-abcd1234ef',
      cnFileName: 'Certificat Négatif — PARACOSME',
    };
    render(
      <Step1Denomination existing={existing} saving={false} onSubmit={vi.fn()} />,
    );

    // Champs texte ré-hydratés.
    expect(screen.getByDisplayValue('PARACOSME')).toBeInTheDocument();
    expect(screen.getByDisplayValue('CN-2026-001')).toBeInTheDocument();
    expect(screen.getByDisplayValue('M. BENANI')).toBeInTheDocument();

    // Le CN archivé s'affiche comme « déjà importé » (plus d'extracteur vide).
    expect(screen.getByText(/Certificat Negatif deja importe/i)).toBeInTheDocument();
    expect(screen.getByText(/Certificat Négatif — PARACOSME/)).toBeInTheDocument();
    // Bouton « Remplacer » disponible sans re-forcer la saisie.
    expect(screen.getByRole('button', { name: /Remplacer/i })).toBeInTheDocument();
  });

  it('forme BROUILLON (denomination à plat = chaîne) : champs restitués (pas de reset)', () => {
    // Forme produite par l'autosave : `denomination` est une CHAÎNE + champs à plat.
    const existing = {
      denomination: 'PARACOSME',
      ice: '000 000 000 000 000',
      cnNumero: 'CN-2026-001',
      cnDate: '2026-01-10',
      activiteCn: 'conseil',
      beneficiaire: 'M. BENANI',
      formeJuridique: 'SARL',
      cnArchivedDocumentId: 'doc-abcd1234ef',
      cnFileName: 'Certificat Négatif — PARACOSME',
    };
    render(
      <Step1Denomination existing={existing} saving={false} onSubmit={vi.fn()} />,
    );
    // Le bug historique réinitialisait ces champs (denomination = chaîne).
    expect(screen.getByDisplayValue('PARACOSME')).toBeInTheDocument();
    expect(screen.getByDisplayValue('CN-2026-001')).toBeInTheDocument();
    expect(screen.getByText(/Certificat Negatif deja importe/i)).toBeInTheDocument();
  });

  it('« Remplacer » rouvre l’extracteur (ré-upload possible sans perdre l’état)', () => {
    const existing = {
      denomination: { denomination: 'PARACOSME', formeJuridique: 'SARL' },
      cnArchivedDocumentId: 'doc-abcd1234ef',
      cnFileName: 'Certificat Négatif — PARACOSME',
    };
    render(
      <Step1Denomination existing={existing} saving={false} onSubmit={vi.fn()} />,
    );
    fireEvent.click(screen.getByRole('button', { name: /Remplacer/i }));
    // Après « Remplacer », on peut annuler pour conserver le CN déjà importé.
    expect(
      screen.getByRole('button', { name: /Annuler le remplacement/i }),
    ).toBeInTheDocument();
  });
});

describe('Step5 — ré-hydratation gouvernance + signataires « 0 champ perdu »', () => {
  const validatedExisting = () => ({
    dirigeants: [
      {
        id: 'd1',
        typePersonne: 'PHYSIQUE',
        civilite: 'M',
        nom: 'Alaoui',
        prenom: 'Ali',
        cinNumero: 'BK12345',
        nationalite: 'Marocaine',
        adresse: '123 rue de Paris',
        dateNaissance: '1990-01-01',
        fonction: 'GERANT',
        isStatutaire: true,
        isAssociate: false,
        // cinUploaded=true -> l'IdentityExtractor n'est pas rendu (évite le réseau).
        cinUploaded: true,
        cinFileName: 'Archive Data Room abcd1234',
        extracting: false,
      },
    ],
    gerance: {
      dureeMandatType: 'illimitee',
      dureeMandat: 'illimitée',
      // Forme VALIDÉE : la rémunération est stockée en PHRASE + clé interne.
      remunerationMode: 'non rémunéré',
      remunerationModeKey: 'non_remunere',
      gerantModeDesignation: 'statutaire',
      dureeGerance: '50 années',
      limitationPouvoirs: 'Au-delà de 200 000 DH, accord des associés.',
      modeSignature: 'séparée',
      modeSignatureAdmin: 'identique',
    },
    cacNomme: true,
    cacNom: 'Cabinet Fiduciaire XYZ',
    signataires: [{ id: 's1', nom: 'M. Ali Alaoui', qualite: 'Gérant' }],
  });

  it('signataires + CAC + durée/limitation de gérance restitués', () => {
    render(
      <Step5Dirigeants
        existing={validatedExisting()}
        formeJuridique="SARL"
        saving={false}
        onSubmit={vi.fn()}
      />,
    );
    // Signataire (art. 15) — la clé qui disparaissait côté backend.
    expect(screen.getByDisplayValue('M. Ali Alaoui')).toBeInTheDocument();
    // CAC.
    expect(screen.getByDisplayValue('Cabinet Fiduciaire XYZ')).toBeInTheDocument();
    /*
     * 2026-08-18 — ATTENTE ADAPTÉE À LA GÉRANCE PAR DIRIGEANT.
     *
     * La gérance ne se saisit plus globalement : chaque dirigeant porte son mandat.
     * Un brouillon antérieur range pourtant tout dans le bag `gerance`, d'où la
     * reprise qui le reverse dans chaque fiche. Ce test vérifie donc que RIEN n'est
     * perdu à la réouverture — c'était déjà son objet (« 0 champ perdu »), la forme
     * seule a changé.
     *
     * « 50 années » (texte libre de l'ancien modèle) redevient un mandat structuré :
     * durée déterminée + 50 ans. C'est précisément la dé-duplication recherchée, le
     * texte libre étant ce qui écrasait le choix de l'utilisateur.
     */
    expect(screen.getByDisplayValue('Duree determinee')).toBeInTheDocument();
    expect(screen.getByDisplayValue('50')).toBeInTheDocument();
    expect(
      screen.getByDisplayValue('Au-delà de 200 000 DH, accord des associés.'),
    ).toBeInTheDocument();
  });

  it('le mode de rémunération (PHRASE persistée) ré-hydrate le <select> et débloque la validation', () => {
    render(
      <Step5Dirigeants
        existing={validatedExisting()}
        formeJuridique="SARL"
        saving={false}
        onSubmit={vi.fn()}
      />,
    );
    // Le <select> rémunération est bien positionné sur « Fonctions non rémunérées »
    // (preuve que la phrase « non rémunéré » a été ré-inversée vers la clé interne).
    // Le <select> du dirigeant est positionné sur « Fonctions non remunerees »
    // (preuve que la phrase « non rémunéré » du bag hérité a été ré-inversée).
    expect(
      screen.getByDisplayValue('Fonctions non remunerees'),
    ).toBeInTheDocument();
    // Gouvernance complète + dirigeant valide => « Valider et continuer » activé
    // (si la rémunération s'était réinitialisée, le bouton resterait désactivé).
    expect(
      screen.getByRole('button', { name: /Valider et continuer/i }),
    ).toBeEnabled();
  });
});
