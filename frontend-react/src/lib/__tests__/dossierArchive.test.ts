import { describe, expect, it } from 'vitest';
import {
  ARCHIVED_DOSSIER_STATUS,
  archivedStatutLabel,
  dataroomReadOnlyMessage,
  dataroomReadOnlyReason,
  isDossierArchived,
} from '../dossierArchive';

/**
 * Lot DIVERS §A (2026-08-13) — le front doit refléter EXACTEMENT la règle
 * backend `DossierArchiveGuard.ARCHIVED_STATUS`. Si l'un des deux dérive, un
 * bouton reste affiché alors que le serveur renverra 400.
 */
describe('dossierArchive', () => {
  it('couvre les 4 statuts archivants du backend', () => {
    expect([...ARCHIVED_DOSSIER_STATUS].sort()).toEqual([
      'DISSOUTE',
      'EN_LIQUIDATION',
      'LIQUIDEE',
      'RADIE',
    ]);
  });

  it('marque en lecture seule les societes non vivantes', () => {
    for (const statut of ARCHIVED_DOSSIER_STATUS) {
      expect(isDossierArchived(statut)).toBe(true);
    }
  });

  it('laisse ecrivables les societes vivantes', () => {
    expect(isDossierArchived('ACTIVE')).toBe(false);
    expect(isDossierArchived('EN_CONSTITUTION')).toBe(false);
  });

  it('traite statut absent comme ecrivable (pas de faux verrou)', () => {
    expect(isDossierArchived(null)).toBe(false);
    expect(isDossierArchived(undefined)).toBe(false);
    expect(isDossierArchived('')).toBe(false);
  });

  it('libelle chaque statut archivant', () => {
    expect(archivedStatutLabel('DISSOUTE')).toBe('Societe dissoute');
    expect(archivedStatutLabel('EN_LIQUIDATION')).toBe('Societe en liquidation');
    expect(archivedStatutLabel('LIQUIDEE')).toBe('Societe liquidee');
    expect(archivedStatutLabel('RADIE')).toBe('Societe radiee');
    expect(archivedStatutLabel('INCONNU')).toBe('Societe archivee');
  });

  it('produit le bandeau demande par la spec', () => {
    expect(dataroomReadOnlyMessage('DISSOUTE')).toBe(
      'Societe dissoute — Data Room en lecture seule',
    );
  });

  it('explique ce qui reste possible dans la raison', () => {
    const reason = dataroomReadOnlyReason('DISSOUTE');
    expect(reason).toContain('Consultation et telechargement restent possibles');
    expect(reason).toContain('desactives');
  });
});
