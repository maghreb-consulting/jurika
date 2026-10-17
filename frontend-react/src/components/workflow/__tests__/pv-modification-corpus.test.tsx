import '@testing-library/jest-dom/vitest';
import { describe, expect, it, vi } from 'vitest';
import { act, render, screen } from '@testing-library/react';
import { ResolutionFieldsEditor } from '../modificationResolutions';
import { RES_SPECS, flattenResolution, newResolution } from '../modificationResolutionsSpecs';

vi.mock('../../../services/workflowDocumentService', async (orig) => ({
  ...(await orig<typeof import('../../../services/workflowDocumentService')>()),
  listTemplatesForWorkflow: vi.fn().mockResolvedValue([]),
}));

import { ModificationOperationForm } from '../ModificationOperationForm';

/**
 * Lot L4d (E11c) : le PV de modification est rendu par les modeles du corpus du cabinet
 * (PV_AG_SARL, PV_DECISIONS_ASSOCIE_UNIQUE_SARL_AU). L'ecran demande les donnees que ces
 * modeles impriment, selon la forme, avec leur aide ; il annonce les decisions que le modele
 * ne sait pas encore rediger.
 */
describe('PV de modification (modèles du corpus) — saisie des résolutions', () => {
  const cles = (type: string, forme: 'SARL' | 'SARL_AU') =>
    RES_SPECS[type].fields.filter((f) => !f.seulement || f.seulement === forme).map((f) => f.key);

  it('les champs propres à une forme ne sont demandés que pour elle', () => {
    expect(cles('augmentation_capital_numeraire', 'SARL')).toEqual(
      expect.arrayContaining(['dpsSuppression', 'articlesModifies']),
    );
    expect(cles('augmentation_capital_numeraire', 'SARL')).not.toContain('souscriptionTiers');
    expect(cles('augmentation_capital_numeraire', 'SARL_AU')).toContain('souscriptionTiers');
    expect(cles('augmentation_capital_numeraire', 'SARL_AU')).not.toContain('dpsSuppression');
    expect(cles('transfert_siege', 'SARL_AU')).not.toContain('articlesModifies');
    expect(cles('revocation_gerant', 'SARL_AU')).toEqual(
      expect.arrayContaining(['gerantSortantDateEffet', 'gerantSortantMotif']),
    );
    expect(cles('commissaire_comptes', 'SARL_AU')).toEqual(
      expect.arrayContaining(['commissaireComptesAdresse', 'cacCessationDateEffet', 'cacCessationMotif']),
    );
    expect(cles('cession_parts_pluripersonnelle', 'SARL_AU')).toEqual(
      expect.arrayContaining(['cessionDate', 'cessionPartsDe', 'cessionPartsA']),
    );
  });

  it('les données nouvelles du modèle sont demandées et transmises', () => {
    expect(cles('modification_denomination', 'SARL')).toEqual(
      expect.arrayContaining(['enseigneModifiee', 'certificatNegatifNumero', 'certificatNegatifDate']),
    );
    expect(cles('transfert_siege', 'SARL')).toContain('villeGreffeArrivee');
    const r = newResolution('modification_denomination', 'd1');
    r.values = { nouvelleDenomination: 'ATLAS', enseigneModifiee: 'oui', certificatNegatifNumero: '2026/1' };
    expect(flattenResolution(r)).toMatchObject({ enseigneModifiee: 'oui', certificatNegatifNumero: '2026/1' });
  });

  it('plus de durée de gérance « durée illimitée » proposée (« pour une durée de durée illimitée »)', () => {
    for (const type of ['nomination_gerant', 'renouvellement_gerant']) {
      const duree = RES_SPECS[type].fields.find((f) => f.key === 'dureeGerance');
      expect(duree?.proposition).toBeUndefined();
      expect(duree?.aide).toMatch(/pour une durée de/);
    }
  });

  it('les numérotations et valeurs nominales calculées ne sont plus saisies', () => {
    const augmentation = RES_SPECS.augmentation_capital_numeraire.fields.map((f) => f.key);
    expect(augmentation).not.toContain('augcapPartsDe');
    expect(augmentation).not.toContain('valeurNominalePart');
    expect(RES_SPECS.reduction_capital.fields.map((f) => f.key)).not.toContain('valeurNominalePart');
  });

  it('une décision que le modèle ne sait pas rédiger est annoncée avant la saisie', () => {
    const { rerender } = render(
      <ResolutionFieldsEditor spec={RES_SPECS.augmentation_capital_incorporation} values={{}} rows={{}}
        forme="SARL" onField={() => {}} onRows={() => {}} />,
    );
    expect(screen.getByRole('alert')).toHaveTextContent('la génération du procès-verbal sera refusée');
    rerender(
      <ResolutionFieldsEditor spec={RES_SPECS.affectation_resultat} values={{}} rows={{}}
        forme="SARL" onField={() => {}} onRows={() => {}} />,
    );
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    rerender(
      <ResolutionFieldsEditor spec={RES_SPECS.acompte_dividendes} values={{}} rows={{}}
        forme="SARL_AU" onField={() => {}} onRows={() => {}} />,
    );
    expect(screen.getByRole('alert')).toHaveTextContent('ne prévoit pas d’acompte sur dividendes');
  });

  it('chaque champ doté d’une aide l’affiche dans une infobulle', () => {
    render(
      <ResolutionFieldsEditor spec={RES_SPECS.transfert_siege} values={{}} rows={{}}
        forme="SARL" onField={() => {}} onRows={() => {}} />,
    );
    expect(screen.getByRole('button', { name: /Aide : Ville du greffe du nouveau siège/ })).toBeInTheDocument();
  });

  it('les conventions approuvées ne sont demandées que pour l’associé unique', () => {
    const { rerender } = render(
      <ResolutionFieldsEditor spec={RES_SPECS.conventions_reglementees} values={{}} rows={{}}
        forme="SARL_AU" onField={() => {}} onRows={() => {}} />,
    );
    expect(screen.getByText('Conventions approuvées')).toBeInTheDocument();
    rerender(
      <ResolutionFieldsEditor spec={RES_SPECS.conventions_reglementees} values={{}} rows={{}}
        forme="SARL" onField={() => {}} onRows={() => {}} />,
    );
    expect(screen.queryByText('Conventions approuvées')).not.toBeInTheDocument();
  });
});

/**
 * Lot L5 (E5) : l'affectation du résultat et les distributions de l'associé unique ne sont plus
 * refusées faute d'écran : le modèle des décisions de l'associé unique (réserve légale, retenue
 * à la source, postes de réserves) est saisi ici ; les montants dérivés sont calculés.
 */
describe('PV de modification SARL AU — affectation du résultat et distributions', () => {
  const cles = (type: string, forme: 'SARL' | 'SARL_AU') =>
    RES_SPECS[type].fields.filter((f) => !f.seulement || f.seulement === forme).map((f) => f.key);

  it('les décisions ne sont plus annoncées comme refusées pour l’associé unique', () => {
    for (const type of ['affectation_resultat', 'distribution_dividendes', 'distribution_reserves']) {
      expect(RES_SPECS[type].nonCouvert?.SARL_AU).toBeUndefined();
    }
    expect(RES_SPECS.acompte_dividendes.nonCouvert?.SARL_AU).toMatch(/acompte/);
  });

  it('affectation : sens du résultat, réserve légale et capitaux propres demandés à l’associé unique seulement', () => {
    const au = cles('affectation_resultat', 'SARL_AU');
    expect(au).toEqual(expect.arrayContaining(['exerciceClosDate', 'resultatSens', 'resultatMontant',
      'reserveLegaleApplicable', 'reserveLegaleDotationTaux', 'reserveLegalePlafond',
      'reserveLegaleDotationMontant', 'capitauxPropresSousQuart']));
    expect(cles('affectation_resultat', 'SARL')).not.toContain('reserveLegaleApplicable');
    // Aucune quotité de réserve légale proposée : le corpus n'en fixe aucune.
    const taux = RES_SPECS.affectation_resultat.fields.find((f) => f.key === 'reserveLegaleDotationTaux');
    expect(taux?.proposition).toBeUndefined();
  });

  it('dividendes : données du modèle demandées, montant par part calculé (non saisi) pour l’associé unique', () => {
    const au = cles('distribution_dividendes', 'SARL_AU');
    expect(au).toEqual(expect.arrayContaining(['exerciceClosDate', 'approbationComptesDate', 'beneficeDistribuable',
      'dividendeTotal', 'dateMisePaiement', 'prolongationOrdonnance', 'prolongationOrdonnanceDate',
      'prolongationDateNouvelle', 'retenueSourceTaux', 'distributionExerciceOrigine', 'retenueSourceExoneration']));
    expect(au).not.toContain('dividendeParPart');
    expect(cles('distribution_dividendes', 'SARL')).toContain('dividendeParPart');
    expect(cles('distribution_dividendes', 'SARL')).not.toContain('retenueSourceTaux');
  });

  it('réserves : postes prélevés en liste pour l’associé unique, poste unique pour l’assemblée', () => {
    expect(cles('distribution_reserves', 'SARL_AU')).not.toContain('prelevementPoste');
    expect(cles('distribution_reserves', 'SARL')).toContain('prelevementPoste');
    const { rerender } = render(
      <ResolutionFieldsEditor spec={RES_SPECS.distribution_reserves} values={{}} rows={{}}
        forme="SARL_AU" onField={() => {}} onRows={() => {}} />,
    );
    expect(screen.getByText('Postes de réserves prélevés')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Aide : Postes de réserves prélevés/ })).toBeInTheDocument();
    rerender(
      <ResolutionFieldsEditor spec={RES_SPECS.distribution_reserves} values={{}} rows={{}}
        forme="SARL" onField={() => {}} onRows={() => {}} />,
    );
    expect(screen.queryByText('Postes de réserves prélevés')).not.toBeInTheDocument();
  });

  it('les saisies sont transmises au procès-verbal', () => {
    const r = newResolution('distribution_reserves', 'd9');
    r.values = { dateMisePaiement: '2026-07-31', retenueSourceTaux: '10', retenueSourceExoneration: 'non',
      distributionExerciceOrigine: '2024' };
    r.rows = { reservesPrelevees: [{ libelle: 'Réserves facultatives', prelevement: '30000', soldeAvant: '80000' }, {}] };
    expect(flattenResolution(r)).toMatchObject({
      retenueSourceTaux: '10', retenueSourceExoneration: 'non', distributionExerciceOrigine: '2024',
      reservesPrelevees: [{ libelle: 'Réserves facultatives', prelevement: '30000', soldeAvant: '80000' }],
    });
    const a = newResolution('affectation_resultat', 'd8');
    a.values = { resultatSens: 'bénéfice', reserveLegaleApplicable: 'oui', reserveLegaleDotationTaux: '5 %' };
    expect(flattenResolution(a)).toMatchObject({ resultatSens: 'bénéfice', reserveLegaleApplicable: 'oui',
      reserveLegaleDotationTaux: '5 %' });
  });

  it('une aide dit ce qui est calculé et ce qui vient ensuite', () => {
    const { rerender } = render(
      <ResolutionFieldsEditor spec={RES_SPECS.distribution_dividendes} values={{}} rows={{}}
        forme="SARL_AU" onField={() => {}} onRows={() => {}} />,
    );
    expect(screen.getByRole('note', { name: 'Distribution de dividendes : que renseigner ?' }))
      .toHaveTextContent('calculés à la génération');
    expect(screen.getByRole('button', { name: /Aide : Taux de la retenue à la source/ })).toBeInTheDocument();
    rerender(
      <ResolutionFieldsEditor spec={RES_SPECS.distribution_dividendes} values={{}} rows={{}}
        forme="SARL" onField={() => {}} onRows={() => {}} />,
    );
    expect(screen.queryByRole('note', { name: 'Distribution de dividendes : que renseigner ?' })).not.toBeInTheDocument();
  });
});

describe('PV de modification — options du procès-verbal', () => {
  it('nombre d’originaux proposé (usage du cabinet) et registre de l’associé unique demandé', async () => {
    await act(async () => {
      render(
        <ModificationOperationForm formeJuridique="SARL_AU" societe={{ denomination: 'PARACOSME AU' }}
          resolutions={[]} />,
      );
    });
    expect(screen.getByText('Nombre d’exemplaires originaux')).toBeInTheDocument();
    expect(screen.getByDisplayValue('quatre')).toBeInTheDocument();
    expect(screen.getByLabelText('N° du registre des décisions')).toBeInTheDocument();
    expect(screen.getByLabelText('Feuillet du registre')).toBeInTheDocument();
    expect(screen.getByRole('note', { name: 'Que renseigner ici ?' })).toHaveTextContent('registre des décisions');
  });

  it('en SARL, pas de registre de l’associé unique', async () => {
    await act(async () => {
      render(
        <ModificationOperationForm formeJuridique="SARL" societe={{ denomination: 'PARACOSME' }} resolutions={[]} />,
      );
    });
    expect(screen.getByText('Nombre d’exemplaires originaux')).toBeInTheDocument();
    expect(screen.queryByLabelText('N° du registre des décisions')).not.toBeInTheDocument();
  });
});

/**
 * Lot L6b (E15, parcours de bout en bout " nomination du gerant ") : la date et l'heure de
 * convocation saisies a l'etape 1 du parcours de modification arrivent au proces-verbal
 * (etape 3) sans etre redemandees. Defaut trouve a l'ecran : le formulaire du PV les
 * reaffichait vides (les panneaux de convocation et de presence les recevaient deja).
 */
const GERANTS = [{ civilite: 'M', prenom: 'Mehdi', nom: 'Alaoui' }];

describe('PV de modification — convocation saisie en amont', () => {
  it('la convocation de l’étape 1 est reprise par le procès-verbal, sans nouveau champ', async () => {
    const onSnapshot = vi.fn();
    await act(async () => {
      render(
        <ModificationOperationForm formeJuridique="SARL" societe={{ denomination: 'ATLAS IMPORT SARL' }}
          resolutions={[]} defaultDate="2026-11-05" defaultType="ordinaire"
          defaultConvocation={{ date: '2026-10-20', heure: '10:00' }} onSnapshot={onSnapshot} />,
      );
    });
    expect(screen.queryByLabelText('Date de convocation')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Heure de convocation')).not.toBeInTheDocument();
    expect(onSnapshot).toHaveBeenLastCalledWith(expect.objectContaining({ convDate: '2026-10-20', convHeure: '10:00' }));
  });

  it('président et auteur de la convocation proposés avec la civilité abrégée « M. »', async () => {
    await act(async () => {
      render(
        <ModificationOperationForm formeJuridique="SARL" societe={{ denomination: 'ATLAS IMPORT SARL' }}
          resolutions={[]} defaultDate="2026-11-05" defaultType="ordinaire" initialGerants={GERANTS} />,
      );
    });
    expect(screen.getByLabelText('Président — Nom')).toHaveValue('M. Mehdi Alaoui');
    // President et auteur de la convocation (valeur proposee) : jamais le code " M " seul.
    expect(screen.getAllByDisplayValue('M. Mehdi Alaoui')).toHaveLength(2);
    expect(screen.queryAllByDisplayValue('M Mehdi Alaoui')).toHaveLength(0);
  });

  it('sans convocation en amont, le procès-verbal la demande', async () => {
    await act(async () => {
      render(
        <ModificationOperationForm formeJuridique="SARL" societe={{ denomination: 'ATLAS IMPORT SARL' }}
          resolutions={[]} defaultDate="2026-11-05" defaultType="ordinaire" />,
      );
    });
    expect(screen.getByLabelText('Date de convocation')).toBeInTheDocument();
  });
});
