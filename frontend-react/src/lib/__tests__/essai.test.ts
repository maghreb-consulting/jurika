import { describe, it, expect } from 'vitest';
import { messageEssai } from '../essai';
import type { EtatEssai } from '../../services/essai.service';

/** Lot L7 (E4) : textes du bandeau (RG-ESS-08 rappels J-3 et J-1, RG-ESS-10 lecture seule). */

function etat(partiel: Partial<EtatEssai>): EtatEssai {
  return {
    enEssai: true, statut: 'TRIAL_ACTIVE', debut: '2026-10-10T10:00:00Z', fin: '2026-10-17T10:00:00Z',
    joursRestants: 5, rappel: null, lectureSeule: false, lectureSeuleDepuis: null, suppressionLe: null,
    avertissementLe: null, prolongeLe: null, prolongeable: true, limites: null, ...partiel,
  };
}

describe('messageEssai', () => {
  it('hors essai : aucun bandeau', () => {
    expect(messageEssai(etat({ enEssai: false, statut: 'CONVERTED' }))).toBeNull();
  });

  it('essai en cours : jours restants et limites', () => {
    const m = messageEssai(etat({}))!;
    expect(m.titre).toBe('Essai gratuit : 5 jours restants (fin le 17 octobre 2026).');
    expect(m.detail).toContain('aucun téléchargement');
    expect(m.urgent).toBe(false);
  });

  it('rappel J-3 puis J-1', () => {
    expect(messageEssai(etat({ rappel: 'J-3', joursRestants: 3 }))!.titre).toBe('Plus que 3 jours d’essai gratuit (fin le 17 octobre 2026).');
    const j1 = messageEssai(etat({ rappel: 'J-1', joursRestants: 1 }))!;
    expect(j1.titre).toBe('Dernier jour d’essai gratuit : il se termine le 17 octobre 2026.');
    expect(j1.urgent).toBe(true);
  });

  it('lecture seule : date de suppression definitive', () => {
    const m = messageEssai(etat({
      statut: 'TRIAL_EXPIRED', lectureSeule: true, joursRestants: 0,
      lectureSeuleDepuis: '2026-10-17T10:00:00Z', suppressionLe: '2026-11-16T10:00:00Z',
    }))!;
    expect(m.titre).toContain('lecture seule');
    expect(m.detail).toContain('supprimées définitivement le 16 novembre 2026');
    expect(m.urgent).toBe(true);
  });
});
