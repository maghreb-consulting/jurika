import '@testing-library/jest-dom/vitest';
import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { useState } from 'react';

vi.mock('../../identity/IdentityExtractor', () => ({ IdentityExtractor: () => null }));

import { LiquidateurPicker } from '../LiquidateurPicker';
import { emptyLiquidateur, type LiquidateurState } from '../liquidateurOptions';
import { MentionsPvFields } from '../MentionsPvFields';
import { MandatLiquidateurFields, PrecisionsMotifFields } from '../MandatLiquidateurFields';
import {
  champsMandatLiquidateur,
  cloturePayload,
  manqueMandatLiquidateur,
  manquesDetailPv,
  mandatPv,
  mentionsPayload,
  type MentionsPv,
} from '../../../lib/liquidationPv';

/**
 * Lot L4d (E11d) : les PV de dissolution et de cloture du corpus demandent des donnees que
 * l'ecran ne recueillait pas. Rien n'est suppose : chaque donnee manquante est nommee.
 */
describe('Données du PV de dissolution (étape 1)', () => {
  it('nomme les précisions du motif et le mandat du liquidateur', () => {
    const e = manquesDetailPv('situation_nette_art86', {});
    expect(Object.keys(e)).toEqual(expect.arrayContaining([
      'situationNetteMontant', 'approbationComptesDate', 'art86MotifsEcartement', 'dureeMandat', 'redditionPeriodicite',
    ]));
    expect(manquesDetailPv('volonte_associes', { dureeMandat: 'trois ans', redditionPeriodicite: 'chaque semestre' }))
      .toEqual({});
    expect(manquesDetailPv('realisation_objet', { dureeMandat: 'x', redditionPeriodicite: 'y' }))
      .toHaveProperty('objetEvenement');
  });

  it('transmet les pouvoirs particuliers ligne par ligne', () => {
    expect(mandatPv({ dureeMandat: ' trois ans ', pouvoirsComplementaires: 'vendre le fonds\n\n céder le bail ' }))
      .toMatchObject({ dureeMandat: 'trois ans', pouvoirsComplementaires: ['vendre le fonds', 'céder le bail'] });
  });

  it('la rémunération et l’ancien gérant ne sont jamais supposés', () => {
    const l: LiquidateurState = { ...emptyLiquidateur(), nom: 'Tazi', nationalite: 'marocaine', dateNaissance: '1975-03-12' };
    expect(manqueMandatLiquidateur(l)).toContain('a été gérant');
    expect(manqueMandatLiquidateur({ ...l, ancienGerant: 'non' })).toContain('rémunéré');
    expect(manqueMandatLiquidateur({ ...l, ancienGerant: 'non', remunerationApplicable: 'oui' })).toContain('montant');
    expect(manqueMandatLiquidateur({ ...l, ancienGerant: 'non', remunerationApplicable: 'non' })).toBeNull();
    // Choisi parmi les gerants : la qualite d'ancien gerant se deduit.
    const gerant = { ...l, source: 'BD' as const, partieKey: 'gerant:karim tazi', remunerationApplicable: 'non' as const };
    expect(manqueMandatLiquidateur(gerant)).toBeNull();
    expect(champsMandatLiquidateur(gerant)).toMatchObject({ ancienGerant: 'oui', remunerationApplicable: 'non' });
  });
});

function HoteLiquidateur() {
  const [v, setV] = useState<LiquidateurState>(emptyLiquidateur());
  return <LiquidateurPicker value={v} onChange={setV} options={[]} />;
}

describe('Écran du liquidateur', () => {
  it('demande la nationalité, la date de naissance et la rémunération (montant si rémunéré)', () => {
    render(<HoteLiquidateur />);
    fireEvent.change(screen.getByTestId('liquidateur-select'), { target: { value: '__externe__' } });
    expect(screen.getByTestId('liquidateur-nationalite')).toBeInTheDocument();
    expect(screen.getByTestId('liquidateur-date-naissance')).toBeInTheDocument();
    expect(screen.getByTestId('liquidateur-ancien-gerant')).toBeInTheDocument();
    // Plus de " exercees a titre gratuit " propose : un choix explicite.
    expect(screen.queryByDisplayValue('exercées à titre gratuit')).toBeNull();
    expect(screen.queryByTestId('liquidateur-remuneration-montant')).toBeNull();
    fireEvent.change(screen.getByTestId('liquidateur-remuneration-applicable'), { target: { value: 'oui' } });
    expect(screen.getByTestId('liquidateur-remuneration-montant')).toBeInTheDocument();
    expect(screen.getByTestId('liquidateur-remuneration-modalites')).toBeInTheDocument();
  });

  it('une personne morale n’a ni nationalité ni date de naissance, mais une rémunération', () => {
    render(<HoteLiquidateur />);
    fireEvent.change(screen.getByTestId('liquidateur-select'), { target: { value: '__externe__' } });
    fireEvent.change(screen.getByTestId('liquidateur-forme'), { target: { value: 'morale' } });
    expect(screen.queryByTestId('liquidateur-nationalite')).toBeNull();
    expect(screen.getByTestId('liquidateur-remuneration-applicable')).toBeInTheDocument();
  });
});

describe('Mandat du liquidateur et précisions du motif', () => {
  it('affiche l’aide et les champs du mandat', () => {
    render(<MandatLiquidateurFields valeurs={{}} onChange={() => {}} />);
    expect(screen.getByRole('note', { name: 'Ce que le procès-verbal fixe pour le liquidateur' })).toBeInTheDocument();
    expect(screen.getByTestId('dissolution-duree-mandat')).toBeInTheDocument();
    expect(screen.getByTestId('dissolution-reddition')).toBeInTheDocument();
  });

  it('demande les précisions propres au motif', () => {
    const { rerender } = render(<PrecisionsMotifFields nature="situation_nette_art86" valeurs={{}} onChange={() => {}} />);
    expect(screen.getByTestId('dissolution-situation-nette')).toBeInTheDocument();
    rerender(<PrecisionsMotifFields nature="extinction_objet" valeurs={{}} onChange={() => {}} />);
    expect(screen.getByTestId('dissolution-objet-evenement')).toBeInTheDocument();
    rerender(<PrecisionsMotifFields nature="volonte_associes" valeurs={{}} onChange={() => {}} />);
    expect(screen.queryByTestId('dissolution-objet-evenement')).toBeNull();
  });
});

function HoteMentions({ mode, quitusAvis }: { mode: 'dissolution' | 'liquidation'; quitusAvis?: string }) {
  const [v, setV] = useState<MentionsPv>({});
  return (
    <MentionsPvFields mode={mode} isAU={false} valeurs={v} onChange={(p) => setV((m) => ({ ...m, ...p }))}
      referenceDossier="TCK-2026-0042" villeEnregistrement="Casablanca" quitusAvis={quitusAvis} />
  );
}

describe('Mentions du procès-verbal (étape 2)', () => {
  it('dissolution : commissaire aux comptes, quitus de la gérance, effectif, journal ; aide affichée', () => {
    render(<HoteMentions mode="dissolution" />);
    expect(screen.getByRole('note', { name: 'Ce que le procès-verbal du cabinet demande encore' })).toBeInTheDocument();
    expect(screen.getByTestId('mentions-quitusGerance')).toBeInTheDocument();
    expect(screen.getByTestId('mentions-salariesDixOuPlus')).toBeInTheDocument();
    expect(screen.getByTestId('mentions-annonceJournal')).toBeInTheDocument();
    expect(screen.queryByTestId('mentions-cacNom')).toBeNull();
    fireEvent.change(screen.getByTestId('mentions-cacExiste'), { target: { value: 'oui' } });
    expect(screen.getByTestId('mentions-cacNom')).toBeInTheDocument();
    expect(screen.getByTestId('mentions-cacMandatLiquidation')).toBeInTheDocument();
    // La reference du ticket est proposee, a confirmer.
    expect(screen.getByDisplayValue('TCK-2026-0042')).toBeInTheDocument();
  });

  it('clôture : compte définitif, quitus à choisir dans l’avis, retenue à la source', () => {
    render(<HoteMentions mode="liquidation" />);
    expect(screen.getByTestId('mentions-frais')).toBeInTheDocument();
    expect(screen.getByText(/Choisissez le sens du quitus dans l’avis de clôture/)).toBeInTheDocument();
    fireEvent.change(screen.getByTestId('mentions-retenueApplicable'), { target: { value: 'oui' } });
    expect(screen.getByTestId('mentions-retenueTaux')).toBeInTheDocument();
    fireEvent.change(screen.getByTestId('mentions-compteDefinitifApprobation'), { target: { value: 'refuse' } });
    expect(screen.getByTestId('mentions-refusMotifs')).toBeInTheDocument();
    expect(screen.queryByTestId('mentions-retenueApplicable')).toBeNull();
  });

  it('clôture : quitus sous réserve, les réserves sont demandées', () => {
    render(<HoteMentions mode="liquidation" quitusAvis="sous_reserve" />);
    expect(screen.getByTestId('mentions-quitusReserveTexte')).toBeInTheDocument();
  });

  it('charge utile : mentions et clôture séparées, ordonnance et pièces structurées', () => {
    const m: MentionsPv = {
      dossierReference: 'D-1', cacExiste: 'non', frais: '10000', convocationAuteur: 'mandataire',
      ordonnanceNumero: '12/2026', piecesRadiation: 'attestation fiscale\nattestation CNSS', vide: ' ',
    };
    expect(mentionsPayload(m)).toEqual({ dossierReference: 'D-1', cacExiste: 'non' });
    expect(cloturePayload(m)).toMatchObject({
      frais: '10000', convocationAuteur: 'mandataire',
      ordonnance: { numero: '12/2026' },
      piecesRadiation: ['attestation fiscale', 'attestation CNSS'],
    });
  });
});
