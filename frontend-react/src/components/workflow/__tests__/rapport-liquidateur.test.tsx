import '@testing-library/jest-dom/vitest';
import { describe, expect, it } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { useState } from 'react';

import { RapportLiquidateurPanel } from '../RapportLiquidateurPanel';
import {
  calculer,
  centimes,
  formatDh,
  manquesRapport,
  rapportPayload,
  type ContexteRapport,
  type RapportLiquidateur,
} from '../../../lib/rapportLiquidateur';

/**
 * Lot L5 (E4) : operations de liquidation du rapport final du liquidateur (modele du corpus).
 * Totaux et resultat calcules (miroir du serveur) ; rien n'est suppose : ce qui manque ou ne
 * concorde pas avec le PV de cloture est nomme.
 */
const complet = (): RapportLiquidateur => ({
  periodeDebut: '2026-05-15', periodeFin: '2026-06-10',
  dissolutionOrgane: 'l’assemblée générale extraordinaire des associés', dissolutionDateDecision: '2026-05-15',
  dissolutionMotifLibelle: 'la cessation de l’activité', prolongee: 'non',
  realisations: [
    { designation: 'Fonds de commerce', mode: 'cession', acquereur: 'ATLAS', prix: '250 000', date: '2026-05-29', valeurInventaire: '220000', observations: 'néant' },
    { designation: 'Créances clients', mode: 'recouvrement', acquereur: 'clients', prix: '50000', date: '2026-06-05', valeurInventaire: '55000', observations: 'néant' },
  ],
  apurements: [
    { creancier: 'BANQUE', nature: 'emprunt', montantAdmis: '150000', montantRegle: '150000', dateReglement: '2026-06-02', modeReglement: 'virement', observations: 'néant' },
  ],
  creancesIrrecouvrablesExistent: 'non', passifsContestesExistent: 'non', cessionsRestreintesExistent: 'non',
  frais: [{ nature: 'Honoraires', beneficiaire: 'CABINET', montant: '10000', date: '2026-06-08', statut: 'réglé' }],
  fraisRembourses: '0', personnelExiste: 'non', contratsExistent: 'non',
  diligences: [{ nature: 'Déclaration de cessation', organisme: 'DGI', referenceTexte: 'article 150 du CGI', dateLimite: '2026-06-29', dateAccomplissement: '2026-06-01', etat: 'accomplie', observations: 'néant' }],
  tvaAssujettie: 'non', attestationsEtat: 'demandées', dateSignature: '2026-06-12',
});

const ctx: ContexteRapport = { capital: 100000, remuneration: 0, totalActif: '300000', totalPassif: '150000', fraisPv: '10000' };

describe('Calcul du rapport du liquidateur', () => {
  it('compte en centimes, sans erreur d’arrondi', () => {
    expect(centimes('12 500,50')).toBe(1250050);
    expect(centimes('0.1')).toBe(10);
    expect(centimes('-5')).toBeNull();
    expect(centimes('1,234')).toBeNull();
    expect(formatDh(1250050)).toBe('12 500,50');
    expect(formatDh(30000000)).toBe('300 000');
  });

  it('calcule totaux, trésorerie et boni depuis les lignes saisies', () => {
    const k = calculer(complet(), ctx);
    expect(k).toMatchObject({ actif: 30000000, passif: 15000000, frais: 1000000, passifRestant: 0, tresorerie: 14000000 });
    expect(k.resultat).toEqual({ sens: 'boni', montant: 4000000 });
    expect(manquesRapport(complet(), ctx, false)).toEqual([]);
  });

  it('mali quand le disponible ne couvre pas le capital', () => {
    const r = complet();
    r.realisations![0].prix = '150000';
    expect(calculer(r, { ...ctx, totalActif: '200000' }).resultat).toEqual({ sens: 'mali', montant: 6000000 });
  });

  it('nomme ce qui ne concorde pas avec le procès-verbal de clôture', () => {
    const m = manquesRapport(complet(), { ...ctx, totalActif: '310000', fraisPv: '12000' }, false);
    expect(m.join('\n')).toContain('Total des réalisations de l’actif (300 000 DH) différent du total de l’actif de l’étape 1 (310 000 DH)');
    expect(m.join('\n')).toContain('Total des frais et honoraires (10 000 DH)');
    // Remuneration du liquidateur : comprise dans le total des frais.
    expect(manquesRapport(complet(), { ...ctx, fraisPv: null, remuneration: '20000' }, false).join('\n'))
      .toContain('La rémunération du liquidateur doit figurer au tableau des frais');
  });

  it('ne suppose ni l’existence d’un tableau ni une ligne incomplète', () => {
    const r = complet();
    delete r.creancesIrrecouvrablesExistent;
    r.passifsContestesExistent = 'oui';
    r.diligences = [];
    r.realisations![1].observations = '';
    const m = manquesRapport(r, ctx, false).join('\n');
    expect(m).toContain('Créances irrécouvrables : répondez par oui ou par non.');
    expect(m).toContain('Passifs contestés : ajoutez au moins une ligne');
    expect(m).toContain('Ajoutez au moins une diligence');
    expect(m).toContain('Réalisation de l’actif, ligne 2 : renseignez « Observations »');
  });

  it('passif connu restant et solde nul : nommés', () => {
    const r = complet();
    r.apurements![0].montantAdmis = '160000';
    expect(manquesRapport(r, ctx, false).join('\n')).toContain('Il reste 10 000 DH de passif admis non réglé');
    const n = complet();
    n.realisations![0].prix = '210000';
    expect(manquesRapport(n, { ...ctx, totalActif: '260000' }, false).join('\n')).toContain('Résultat nul');
  });

  it('SARL AU : l’organe de la dissolution n’est pas demandé', () => {
    const r = complet();
    delete r.dissolutionOrgane;
    expect(manquesRapport(r, ctx, true)).toEqual([]);
    expect(manquesRapport(r, ctx, false).join('\n')).toContain('organe');
  });

  it('charge utile : lignes vides retirées, annexes en liste de désignations', () => {
    const r = { ...complet(), frais: [...complet().frais!, { nature: '', beneficiaire: '', montant: '', date: '', statut: '' }],
      annexes: [{ designation: ' Compte définitif ' }, { designation: '' }], retenueObservations: '  ' };
    const p = rapportPayload(r);
    expect(p.frais).toHaveLength(1);
    expect(p.annexes).toEqual(['Compte définitif']);
    expect(p).not.toHaveProperty('retenueObservations');
  });
});

function Hote({ initial, isAU = false }: { initial: RapportLiquidateur; isAU?: boolean }) {
  const [v, setV] = useState<RapportLiquidateur>(initial);
  return <RapportLiquidateurPanel isAU={isAU} valeurs={v} onChange={(p) => setV((x) => ({ ...x, ...p }))} contexte={ctx} />;
}

describe('Écran du rapport du liquidateur', () => {
  it('affiche les totaux calculés et le résultat, puis dit que le rapport est complet', () => {
    render(<Hote initial={complet()} />);
    expect(screen.getByTestId('rapport-total-actif')).toHaveTextContent('300 000 DH');
    expect(screen.getByTestId('rapport-resultat')).toHaveTextContent('boni de 40 000 DH');
    expect(screen.getByTestId('rapport-complet')).toBeInTheDocument();
  });

  it('un tableau facultatif s’ouvre sur « oui » ; les lignes s’ajoutent et se retirent', () => {
    render(<Hote initial={{}} />);
    expect(screen.queryByTestId('rapport-tableau-personnel')).toBeNull();
    fireEvent.change(screen.getByTestId('rapport-personnelExiste'), { target: { value: 'oui' } });
    expect(screen.getByTestId('rapport-tableau-personnel')).toBeInTheDocument();
    expect(screen.getByTestId('rapport-salariesDixOuPlus')).toBeInTheDocument();
    fireEvent.click(screen.getByTestId('rapport-personnel-ajouter'));
    fireEvent.change(screen.getByTestId('rapport-personnel-0-nom'), { target: { value: 'M. Karim Saidi' } });
    expect(screen.getByTestId('rapport-personnel-0-nom')).toHaveValue('M. Karim Saidi');
    fireEvent.click(screen.getByRole('button', { name: 'Retirer la ligne 1' }));
    expect(screen.queryByTestId('rapport-personnel-0-nom')).toBeNull();
    expect(screen.getByTestId('rapport-manques')).toHaveTextContent('Personnel : ajoutez au moins une ligne');
  });

  it('porte son aide (ce qu’il faut faire, ce qui vient ensuite) et l’organe en SARL seulement', () => {
    const { unmount } = render(<Hote initial={{}} />);
    expect(screen.getByRole('note', { name: 'Ce que le rapport du liquidateur demande' }))
      .toHaveTextContent('générez le rapport avec le procès-verbal');
    expect(screen.getByTestId('rapport-dissolutionOrgane')).toBeInTheDocument();
    unmount();
    render(<Hote initial={{}} isAU />);
    expect(screen.queryByTestId('rapport-dissolutionOrgane')).toBeNull();
  });
});
