import '@testing-library/jest-dom/vitest';
import { describe, expect, it, vi } from 'vitest';
import { act, render, screen } from '@testing-library/react';
import {
  RES_SPECS,
  ResolutionFieldsEditor,
  flattenResolution,
  newResolution,
} from '../modificationResolutions';

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
      <ResolutionFieldsEditor spec={RES_SPECS.affectation_resultat} values={{}} rows={{}}
        forme="SARL_AU" onField={() => {}} onRows={() => {}} />,
    );
    expect(screen.getByRole('alert')).toHaveTextContent('parcours d’approbation des comptes');
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
