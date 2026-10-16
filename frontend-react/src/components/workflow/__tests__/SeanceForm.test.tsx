import { describe, it, expect } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import {
  useSeanceForm,
  serializeAssocie,
  sanitizeFilename,
  emptyAssocie,
  nomAvecCivilite,
} from '../seanceFormEtat';

// Forme lue par ces tests (le payload est un Record<string, unknown>).
type Payload = {
  associeUnique: boolean;
  formeJuridique: string;
  societe: { denomination: string; villeGreffe: string };
  associes: unknown[];
  documentsJoints: unknown[];
  ordreDuJour: string[];
  convocation: Record<string, unknown>;
  seance: Record<string, unknown>;
};

describe('SeanceForm — contrat de séance partagé', () => {
  const societe = {
    denomination: 'PARACOSME',
    capitalChiffres: 100000,
    siegeSocial: '101 bd Zerktouni, Casablanca',
    nombreParts: 1000,
    rcNumero: '123456',
    villeGreffe: 'Casablanca',
  };

  it('produit le contrat de séance (SARL) attendu par le back', () => {
    const { result } = renderHook(() =>
      useSeanceForm({ formeJuridique: 'SARL', societe }),
    );
    const p = result.current.buildPayload() as Payload;
    expect(p.associeUnique).toBe(false);
    expect(p.formeJuridique).toBe('SARL');
    expect(p.societe.denomination).toBe('PARACOSME');
    expect(p.societe.villeGreffe).toBe('Casablanca');
    // Toujours une liste associes (jamais un objet unique) — le back lit associes[0] en AU.
    expect(Array.isArray(p.associes)).toBe(true);
    expect(Array.isArray(p.documentsJoints)).toBe(true);
    expect(Array.isArray(p.ordreDuJour)).toBe(true);
    expect(p.convocation).toHaveProperty('rang', 'première');
    expect(p.seance).toHaveProperty('type');
  });

  it('marque associeUnique=true pour une SARL AU', () => {
    const { result } = renderHook(() =>
      useSeanceForm({ formeJuridique: 'SARL_AU', societe }),
    );
    const p = result.current.buildPayload() as Payload;
    expect(p.associeUnique).toBe(true);
    expect(p.formeJuridique).toBe('SARL_AU');
  });

  it('reflète les points d’ordre du jour saisis, filtrés/trimés', () => {
    const { result } = renderHook(() =>
      useSeanceForm({ formeJuridique: 'SARL', societe }),
    );
    act(() => result.current.setOrdreDuJour(['  Approbation des comptes  ', '', 'Affectation']));
    const p = result.current.buildPayload() as Payload;
    expect(p.ordreDuJour).toEqual(['Approbation des comptes', 'Affectation']);
  });

  it('serializeAssocie ne conserve le mandataire que si représenté', () => {
    const present = serializeAssocie({ ...emptyAssocie(), nom: 'BENANI', mandataireNom: 'X', presence: 'présent' });
    expect(present.mandataireNom).toBe('');
    const repr = serializeAssocie({ ...emptyAssocie(), nom: 'IDRISSI', mandataireNom: 'M. BENANI', presence: 'représenté' });
    expect(repr.mandataireNom).toBe('M. BENANI');
  });

  it('sanitizeFilename retire les caractères interdits', () => {
    expect(sanitizeFilename('Convocation - A/B:C*?')).toBe('Convocation - A B C');
  });

  // L6 : les reprises des sources du dossier se font au rendu (plus dans des effets).
  // Ces tests gardent le comportement : la source arrivee apres le montage remplit un
  // champ vide, sans jamais ecraser une saisie.
  it('reprend le siège et la ville du greffe arrivés après le montage, sans écraser une saisie', () => {
    const { result, rerender } = renderHook(
      ({ s }) => useSeanceForm({ formeJuridique: 'SARL', societe: s }),
      { initialProps: { s: { denomination: 'PARACOSME' } as typeof societe | { denomination: string } } },
    );
    expect(result.current.lieu).toBe('');
    expect(result.current.lieuSignature).toBe('');
    rerender({ s: societe });
    expect(result.current.lieu).toBe('101 bd Zerktouni, Casablanca');
    expect(result.current.lieuSignature).toBe('Casablanca');
    act(() => result.current.setLieu('Salle du conseil'));
    rerender({ s: { ...societe, siegeSocial: 'Autre siège' } });
    expect(result.current.lieu).toBe('Salle du conseil');
  });

  it('propose le premier gérant comme président, et suit la date pilotée en amont', () => {
    // Liste stable, comme les pages la fournissent (memo) : une liste recreee a chaque
    // rendu relancerait la reprise des gerants sans fin.
    const gerants = [{ civilite: 'M.', prenom: 'Ali', nom: 'ALAOUI' }];
    const { result, rerender } = renderHook(
      ({ date }) => useSeanceForm({
        formeJuridique: 'SARL', societe, defaults: { date },
        initialGerants: gerants,
      }),
      { initialProps: { date: '2026-10-01' } },
    );
    expect(result.current.presidentNom).toBe('M. Ali ALAOUI');
    expect(result.current.date).toBe('2026-10-01');
    rerender({ date: '2026-10-20' });
    expect(result.current.date).toBe('2026-10-20');
  });

  it('ordre du jour verrouillé : suit la sélection, même recalculée à chaque rendu', () => {
    const { result, rerender } = renderHook(
      ({ odj }) => useSeanceForm({
        formeJuridique: 'SARL', societe, initialOrdreDuJour: [...odj], lockOrdreDuJour: true,
      }),
      { initialProps: { odj: ['Transfert du siège'] } },
    );
    expect(result.current.ordreDuJour).toEqual(['Transfert du siège']);
    rerender({ odj: ['Transfert du siège', 'Augmentation du capital'] });
    expect(result.current.ordreDuJour).toEqual(['Transfert du siège', 'Augmentation du capital']);
  });

  it('ordre du jour non verrouillé : une édition manuelle n’est plus écrasée', () => {
    const { result, rerender } = renderHook(
      ({ odj }) => useSeanceForm({ formeJuridique: 'SARL', societe, initialOrdreDuJour: odj }),
      { initialProps: { odj: ['Point A'] } },
    );
    rerender({ odj: ['Point B'] });
    expect(result.current.ordreDuJour).toEqual(['Point B']);
    act(() => result.current.setOrdreDuJour(['Point saisi']));
    rerender({ odj: ['Point C'] });
    expect(result.current.ordreDuJour).toEqual(['Point saisi']);
  });
});

/**
 * Lot L6b (E15, parcours « nomination du gerant ») : le nom propose (president, auteur de la
 * convocation) s'imprime avec la civilite abregee « M. », jamais le code « M » des etapes de
 * saisie (« sur convocation de M Mehdi Alaoui » dans le PV, constate a l'ecran). Rendu du
 * formulaire : pv-modification-corpus.test.tsx.
 */
describe('SeanceForm — civilité abrégée', () => {
  it('le code « M » devient « M. », « Mme » reste « Mme »', () => {
    expect(nomAvecCivilite({ civilite: 'M', prenom: 'Mehdi', nom: 'Alaoui' })).toBe('M. Mehdi Alaoui');
    expect(nomAvecCivilite({ civilite: 'M.', prenom: 'Mehdi', nom: 'Alaoui' })).toBe('M. Mehdi Alaoui');
    expect(nomAvecCivilite({ civilite: 'Mme', prenom: 'Sara', nom: 'Bennani' })).toBe('Mme Sara Bennani');
    expect(nomAvecCivilite({ civilite: '', prenom: 'Sara', nom: 'Bennani' })).toBe('Sara Bennani');
  });
});

/**
 * Lot L6b (E15, parcours « approbation des comptes ») : la convocation saisie a l'etape 1 du
 * PV AGO arrive au formulaire de seance de l'etape 2 (date ET heure), meme saisie APRES le
 * montage du formulaire (la page le monte des l'ouverture). Constate a l'ecran : la date et
 * l'heure de convocation de l'etape 2 etaient vides.
 */
describe('SeanceForm — convocation pilotée en amont', () => {
  const societe = { denomination: 'ATLAS NORD SARL' };
  it('date et heure de convocation suivent l’amont, même arrivées après le montage', () => {
    const { result, rerender } = renderHook(
      ({ d }: { d: { convDate?: string; convHeure?: string } }) =>
        useSeanceForm({ formeJuridique: 'SARL', societe, defaults: { type: 'ordinaire', date: '2026-06-05', ...d } }),
      { initialProps: { d: {} } },
    );
    expect(result.current.convDate).toBe('');
    rerender({ d: { convDate: '2026-05-20', convHeure: '10:00' } });
    expect(result.current.convDate).toBe('2026-05-20');
    expect(result.current.convHeure).toBe('10:00');
  });
});

