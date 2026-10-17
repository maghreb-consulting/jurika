import { describe, expect, it } from 'vitest';
import type { Role } from '../../../types/auth';
import { entreeActive, filParDefaut, menuDuRole } from '../navigation';

/** Lot L6b (E3) : menu lateral par role (regles RG-U07 a RG-U10, lot M, demande L6b). */
function chemins(role: Role): string[] {
  return menuDuRole(role).flatMap((g) => g.entrees.map((e) => e.to));
}

describe('Menu par role', () => {
  it('EMPLOYE : tickets et Data Rooms dans le premier groupe (un clic), pas d administration', () => {
    const premier = menuDuRole('EMPLOYE')[0].entrees.map((e) => e.libelle);
    expect(premier).toContain('Mes tickets');
    expect(premier).toContain('Mes Data Rooms');
    const tous = chemins('EMPLOYE');
    expect(tous).toEqual(expect.arrayContaining(['/tickets', '/data-rooms', '/chat', '/calendar', '/delais-greffe']));
    expect(tous.some((c) => c.startsWith('/admin'))).toBe(false);
    expect(tous).not.toContain('/tracabilite');
    expect(tous).not.toContain('/app/team');
  });

  it('SUPERVISEUR : le tableau de bord en premier, puis le pilotage du cabinet', () => {
    expect(menuDuRole('SUPERVISEUR')[0].entrees[0].to).toBe('/dashboard');
    const tous = chemins('SUPERVISEUR');
    expect(tous).toEqual(
      expect.arrayContaining(['/tickets', '/dossiers-a-verifier', '/data-rooms', '/app/team', '/tracabilite', '/chat']),
    );
    expect(tous.some((c) => c.startsWith('/admin'))).toBe(false);
  });

  it('CLIENT : espace simple, sans messagerie, ni tickets, ni facturation', () => {
    const tous = chemins('CLIENT');
    expect(tous).toEqual(['/dashboard', '/data-rooms', '/mes-demandes', '/mes-requetes']);
    expect(menuDuRole('CLIENT').flatMap((g) => g.entrees.map((e) => e.libelle))).toContain('Mon espace');
  });

  it('SUPER_ADMIN (RG-U07) : ni tickets, ni dossiers, ni documents, ni messagerie ; la page /design', () => {
    const tous = chemins('SUPER_ADMIN');
    for (const interdit of ['/tickets', '/data-rooms', '/chat', '/calendar', '/mes-demandes']) {
      expect(tous).not.toContain(interdit);
    }
    expect(tous).toEqual(expect.arrayContaining(['/admin/workspaces', '/admin/users', '/admin/audit', '/design']));
  });

  it('aucune entree en double dans un meme menu', () => {
    for (const role of ['SUPER_ADMIN', 'SUPERVISEUR', 'EMPLOYE', 'CLIENT'] as Role[]) {
      const c = chemins(role);
      expect(new Set(c).size, role).toBe(c.length);
    }
  });
});

describe('Entree active et fil d Ariane par defaut', () => {
  it('une page de detail active l entree de sa liste (prefixe le plus long)', () => {
    expect(entreeActive('EMPLOYE', '/tickets/123')?.libelle).toBe('Mes tickets');
    expect(entreeActive('SUPER_ADMIN', '/admin/workspaces/abc')?.libelle).toBe('Cabinets');
    expect(entreeActive('EMPLOYE', '/inconnu')).toBeUndefined();
  });

  it('Accueil > entree > titre ; le titre n est pas repete quand il est l entree', () => {
    expect(filParDefaut('EMPLOYE', '/tickets/123', 'Ticket 123')).toEqual([
      { libelle: 'Accueil', to: '/dashboard' },
      { libelle: 'Mes tickets', to: '/tickets' },
      { libelle: 'Ticket 123' },
    ]);
    expect(filParDefaut('EMPLOYE', '/tickets', 'Mes tickets')).toEqual([
      { libelle: 'Accueil', to: '/dashboard' },
      { libelle: 'Mes tickets' },
    ]);
    expect(filParDefaut('EMPLOYE', '/dashboard', 'Tableau de bord')).toEqual([{ libelle: 'Accueil' }]);
  });
});
