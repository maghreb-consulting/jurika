import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { useState } from 'react';
import { RapportGestionFields } from '../RapportGestionFields';
import {
  rapportGestionDateErreur,
  rapportGestionDepuis,
  rapportGestionManquants,
  rapportGestionPayload,
  rapportGestionVide,
  type RapportGestionSaisie,
} from '../rapportGestion';

/**
 * Lot L4d (E11e) : rapport de gestion rendu par les modeles du corpus. Les champs saisis
 * a l'ecran, sans texte par defaut ; ce qui manque est dit ; les donnees conditionnelles
 * ne sont demandees que si le modele les imprime.
 */
function complet(): RapportGestionSaisie {
  return {
    ...rapportGestionVide(),
    exerciceDebut: '2025-01-01',
    activiteExpose: 'Développement auprès de nouveaux clients.',
    faitsMarquants: [{ date: '2025-03-01', libelle: 'ouverture d’un bureau à Rabat' }],
    indicateurs: [{ libelle: 'Chiffre d’affaires', montantExercice: '1200000', montantExercicePrecedent: '950000' }],
    evolutionCommentaire: 'Hausse due aux nouveaux contrats.',
    capitauxPropres: '420000',
    evenementsPostCloture: 'non',
    perspectives: 'Poursuite de la croissance.',
    rechercheDeveloppement: 'non',
    reserveLegaleDoteeIntegralement: 'non',
    lieuSignature: 'Casablanca',
    date: '2026-05-25',
  };
}

function Hote({ initial, resultatType = 'bénéfice', dateAgo = '2026-06-15' }: { initial: RapportGestionSaisie; resultatType?: string; dateAgo?: string }) {
  const [v, setV] = useState(initial);
  return (
    <RapportGestionFields valeur={v} onChange={setV} resultatType={resultatType} dateAgo={dateAgo}
      propositionDebut="2025-01-01" propositionLieu="Casablanca" />
  );
}

describe('Rapport de gestion : saisie (L4d, E11e)', () => {
  it('aucun texte par défaut : tout est vide et nommé comme manquant', () => {
    const vide = rapportGestionVide();
    expect(vide.activiteExpose).toBe('');
    expect(vide.perspectives).toBe('');
    expect(vide.evenementsPostCloture).toBe('');
    const m = rapportGestionManquants(vide, { resultatType: 'bénéfice' });
    expect(m).toContain('activité de l’exercice');
    expect(m).toContain('au moins un fait marquant');
    expect(m).toContain('au moins un indicateur');
    expect(m).toContain('lieu de signature du rapport');
    expect(m).toContain('réserve légale au cinquième du capital (oui ou non)');
    expect(m).not.toContain('pertes cumulées après affectation');
  });

  it('les données conditionnelles suivent le sens du résultat et les réponses oui / non', () => {
    expect(rapportGestionManquants(complet(), { resultatType: 'bénéfice' })).toEqual([]);
    const perte = { ...complet(), reserveLegaleDoteeIntegralement: '' as const };
    expect(rapportGestionManquants(perte, { resultatType: 'perte' })).toEqual(['pertes cumulées après affectation']);
    const ev = { ...complet(), evenementsPostCloture: 'oui' as const };
    expect(rapportGestionManquants(ev, { resultatType: 'bénéfice' }))
      .toEqual(['date, nature et incidence de chaque événement postérieur à la clôture']);
    const rd = { ...complet(), rechercheDeveloppement: 'oui' as const };
    expect(rapportGestionManquants(rd, { resultatType: 'bénéfice' }))
      .toEqual(['exposé des travaux de recherche et de développement']);
  });

  it('la charge utile écarte les lignes vides et les branches non imprimées', () => {
    const r = { ...complet(), faitsMarquants: [...complet().faitsMarquants, { date: '', libelle: '' }], pertesCumulees: '5000' };
    const p = rapportGestionPayload(r, 'bénéfice');
    expect(p.faitsMarquants).toHaveLength(1);
    expect(p.pertesCumulees).toBe('');
    expect(p.reserveLegaleDoteeIntegralement).toBe('non');
    expect(p.evenementsPostClotureListe).toEqual([]);
    expect(rapportGestionPayload({ ...r, reserveLegaleDoteeIntegralement: 'oui' }, 'perte').reserveLegaleDoteeIntegralement).toBe('');
  });

  it('un brouillon enregistré est repris', () => {
    const r = rapportGestionDepuis({ activiteExpose: 'Texte saisi', faitsMarquants: [] });
    expect(r.activiteExpose).toBe('Texte saisi');
    expect(r.faitsMarquants).toHaveLength(1);
    expect(rapportGestionDepuis(null)).toEqual(rapportGestionVide());
  });

  it('le rapport est daté au plus tard du jour de l’assemblée', () => {
    expect(rapportGestionDateErreur({ ...complet(), date: '2026-06-20' }, '2026-06-15')).toMatch(/au plus tard/);
    expect(rapportGestionDateErreur(complet(), '2026-06-15')).toBeUndefined();
  });

  it('l’écran dit quoi faire, ce qui manque et ce qui vient ensuite', () => {
    render(<Hote initial={rapportGestionVide()} />);
    const aide = screen.getByRole('note', { name: 'Rapport de gestion de la gérance' });
    expect(aide).toHaveTextContent('repris du dossier');
    expect(aide).toHaveTextContent('Il se génère à l’étape 3');
    expect(screen.getByTestId('pvago-rapport-manquants')).toHaveTextContent('il reste à saisir');
    expect(screen.getByTestId('pvago-rapport-manquants')).toHaveTextContent('activité de l’exercice');
  });

  it('saisie complète : le rapport est annoncé comme générable', () => {
    render(<Hote initial={complet()} />);
    expect(screen.getByTestId('pvago-rapport-manquants')).toHaveTextContent('Le rapport de gestion est complet');
  });

  it('bénéfice : réserve légale demandée ; perte : pertes cumulées demandées', () => {
    const { unmount } = render(<Hote initial={complet()} resultatType="bénéfice" />);
    expect(screen.getByTestId('pvago-rg-reserve-legale')).toBeInTheDocument();
    expect(screen.queryByTestId('pvago-rg-pertes-cumulees')).toBeNull();
    unmount();
    render(<Hote initial={complet()} resultatType="perte" />);
    expect(screen.queryByTestId('pvago-rg-reserve-legale')).toBeNull();
    expect(screen.getByTestId('pvago-rg-pertes-cumulees')).toBeInTheDocument();
  });

  it('« oui » aux événements ouvre leur liste, que la saisie complète', () => {
    render(<Hote initial={complet()} />);
    expect(screen.queryByLabelText('Événement 1')).toBeNull();
    fireEvent.change(screen.getByTestId('pvago-rg-evenements'), { target: { value: 'oui' } });
    expect(screen.getByTestId('pvago-rapport-manquants')).toHaveTextContent('incidence de chaque événement');
    fireEvent.change(screen.getByLabelText('Date de l’événement 1'), { target: { value: '2026-02-10' } });
    fireEvent.change(screen.getByLabelText('Événement 1'), { target: { value: 'perte d’un client' } });
    fireEvent.change(screen.getByLabelText('Incidence sur la société'), { target: { value: 'baisse du chiffre d’affaires' } });
    expect(screen.getByTestId('pvago-rapport-manquants')).toHaveTextContent('Le rapport de gestion est complet');
  });

  it('le lieu proposé n’est repris qu’une fois confirmé', () => {
    const onChange = vi.fn();
    render(<RapportGestionFields valeur={{ ...complet(), lieuSignature: '' }} onChange={onChange} resultatType="bénéfice"
      propositionDebut="2025-01-01" propositionLieu="Casablanca" />);
    expect(screen.getByTestId('pvago-rapport-manquants')).toHaveTextContent('lieu de signature du rapport');
    const lieu = screen.getAllByRole('button', { name: 'Confirmer' });
    fireEvent.click(lieu[lieu.length - 1]);
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ lieuSignature: 'Casablanca' }));
  });
});
