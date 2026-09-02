import { describe, expect, it } from 'vitest';
import {
  LIQUIDATION_DELAI_JOURS,
  joursCalendairesEntre,
  liquidationDelaiError,
} from '../liquidationDelai';

/**
 * RG-LI03 — règle des 15 jours entre la dissolution et la clôture de la liquidation.
 * Miroir exact du backend `WorkflowSteps.liquidationDelaiError`.
 *
 * Ces tests verrouillent les quatre défauts corrigés par le lot « Liquidation 4 étapes » :
 * seuil (15 et non 16), calcul en jours calendaires, comparaison dissolution → clôture
 * (et non « aujourd'hui »), caractère bloquant.
 */
describe('liquidationDelai — règle des 15 jours', () => {
  it('impose un seuil de 15 jours (et non 16)', () => {
    expect(LIQUIDATION_DELAI_JOURS).toBe(15);
  });

  it('REJETTE une clôture à J+14 après la dissolution', () => {
    const err = liquidationDelaiError('2026-05-01', '2026-05-15');
    expect(err).not.toBeNull();
    expect(err).toContain('14 jour(s)');
    expect(err).toContain('15');
  });

  it('ACCEPTE une clôture à J+15 pile', () => {
    expect(liquidationDelaiError('2026-05-01', '2026-05-16')).toBeNull();
  });

  it('ACCEPTE une clôture au-delà de J+15', () => {
    expect(liquidationDelaiError('2026-05-01', '2026-06-30')).toBeNull();
    expect(liquidationDelaiError('2026-05-01', '2027-01-01')).toBeNull();
  });

  it('REJETTE une clôture antérieure à la dissolution', () => {
    expect(liquidationDelaiError('2026-05-15', '2026-05-01')).toContain('preceder');
  });

  it('ne contrôle rien si la date de dissolution est inconnue (dossier ancien)', () => {
    expect(liquidationDelaiError('', '2026-05-16')).toBeNull();
    expect(liquidationDelaiError('2026-05-01', '')).toBeNull();
  });

  it('ignore les dates non ISO plutôt que de bloquer à tort', () => {
    expect(liquidationDelaiError('01/05/2026', '2026-05-16')).toBeNull();
  });

  it('compare la DISSOLUTION à la CLÔTURE saisie, jamais « aujourd’hui »', () => {
    // Deux dates entièrement dans le passé : un calcul basé sur Date.now() les aurait
    // toutes deux jugées « largement au-delà de 15 jours », masquant la violation.
    expect(liquidationDelaiError('2020-01-01', '2020-01-10')).not.toBeNull(); // 9 j
    // Et deux dates dans le futur restent contrôlées de la même façon.
    expect(liquidationDelaiError('2099-01-01', '2099-01-10')).not.toBeNull(); // 9 j
    expect(liquidationDelaiError('2099-01-01', '2099-01-16')).toBeNull(); // 15 j
  });

  it('calcule en jours CALENDAIRES, insensibles au fuseau et au changement d’heure', () => {
    // Passage à l'heure d'été (nuit du 2026-03-29 en Europe/Casablanca) : la durée réelle
    // n'est pas un multiple de 24 h. Un calcul en millisecondes dérivait d'un jour ici.
    expect(liquidationDelaiError('2026-03-20', '2026-04-04')).toBeNull(); // 15 j
    expect(liquidationDelaiError('2026-03-20', '2026-04-03')).not.toBeNull(); // 14 j
    // Passage à l'heure d'hiver (2026-10-25) — même exigence, dans l'autre sens.
    expect(liquidationDelaiError('2026-10-15', '2026-10-30')).toBeNull(); // 15 j
    expect(liquidationDelaiError('2026-10-15', '2026-10-29')).not.toBeNull(); // 14 j
  });

  it('reste exact sur les fins de mois et les années bissextiles', () => {
    expect(liquidationDelaiError('2026-12-20', '2027-01-04')).toBeNull(); // 15 j
    expect(liquidationDelaiError('2028-02-20', '2028-03-06')).toBeNull(); // 15 j (bissextile)
    expect(liquidationDelaiError('2028-02-20', '2028-03-05')).not.toBeNull(); // 14 j
  });
});

describe('liquidationDelai — joursCalendairesEntre', () => {
  it('compte des jours calendaires exacts', () => {
    expect(joursCalendairesEntre('2026-05-01', '2026-05-16')).toBe(15);
    expect(joursCalendairesEntre('2026-05-01', '2026-05-01')).toBe(0);
    expect(joursCalendairesEntre('2026-05-16', '2026-05-01')).toBe(-15);
  });

  it('reste exact autour des changements d’heure (heures extrêmes incluses)', () => {
    // Bornes encadrant les deux bascules DST de 2026.
    expect(joursCalendairesEntre('2026-03-29', '2026-03-30')).toBe(1);
    expect(joursCalendairesEntre('2026-10-25', '2026-10-26')).toBe(1);
    expect(joursCalendairesEntre('2026-03-20', '2026-04-04')).toBe(15);
  });

  it('renvoie null sur une date absente ou invalide', () => {
    expect(joursCalendairesEntre('', '2026-05-16')).toBeNull();
    expect(joursCalendairesEntre('2026-05-01', '')).toBeNull();
    expect(joursCalendairesEntre('01/05/2026', '2026-05-16')).toBeNull();
  });
});
