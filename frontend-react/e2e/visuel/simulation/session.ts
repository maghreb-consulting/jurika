import type { Page } from '@playwright/test';
import { MAINTENANT, type Theme } from '../constantes';

/**
 * Lot L6b (E4) : session simulee, posee dans le stockage comme le front l'attend
 * (lib/api.ts tokenStorage, store/authStore persist 'jurika-auth', store/themeStore
 * 'jurika.theme'). Le jeton est un JWT non signe : le front ne fait que le decoder.
 */
export type Role = 'SUPER_ADMIN' | 'SUPERVISEUR' | 'EMPLOYE' | 'CLIENT';
export type Cabinet = 'demo' | 'essai' | 'plateforme';

export interface Profil {
  role: Role;
  cabinet: Cabinet;
  userId: string;
  workspaceId: string;
  workspaceCode: string;
  email: string;
  prenom: string;
  nom: string;
}

export const WS_DEMO = '11111111-1111-4111-8111-111111111111';
export const WS_ESSAI = '22222222-2222-4222-8222-222222222222';
export const WS_PLATEFORME = '00000000-0000-4000-8000-000000000000';

export const PROFILS: Record<string, Profil> = {
  'super-admin': {
    role: 'SUPER_ADMIN',
    cabinet: 'plateforme',
    userId: 'a0000000-0000-4000-8000-000000000001',
    workspaceId: WS_PLATEFORME,
    workspaceCode: 'JUR-PLATEFORME',
    email: 'admin@jurika.ma',
    prenom: 'Salma',
    nom: 'Bennani',
  },
  superviseur: {
    role: 'SUPERVISEUR',
    cabinet: 'demo',
    userId: 'b0000000-0000-4000-8000-000000000001',
    workspaceId: WS_DEMO,
    workspaceCode: 'JUR-DEMO1',
    email: 'k.alaoui@atlas-conseil.jurika.ma',
    prenom: 'Karim',
    nom: 'Alaoui',
  },
  employe: {
    role: 'EMPLOYE',
    cabinet: 'demo',
    userId: 'b0000000-0000-4000-8000-000000000002',
    workspaceId: WS_DEMO,
    workspaceCode: 'JUR-DEMO1',
    email: 'n.idrissi@atlas-conseil.jurika.ma',
    prenom: 'Nadia',
    nom: 'Idrissi',
  },
  client: {
    role: 'CLIENT',
    cabinet: 'demo',
    userId: 'c0000000-0000-4000-8000-000000000001',
    workspaceId: WS_DEMO,
    workspaceCode: 'JUR-DEMO1',
    email: 'y.tazi@atlas-conseil.jurika.ma',
    prenom: 'Youssef',
    nom: 'Tazi',
  },
  'superviseur-essai': {
    role: 'SUPERVISEUR',
    cabinet: 'essai',
    userId: 'd0000000-0000-4000-8000-000000000001',
    workspaceId: WS_ESSAI,
    workspaceCode: 'JUR-ESSAI7',
    email: 'm.chraibi@essai.jurika.ma',
    prenom: 'Meryem',
    nom: 'Chraibi',
  },
};

export type CleProfil = keyof typeof PROFILS;

function base64url(obj: unknown): string {
  return Buffer.from(JSON.stringify(obj)).toString('base64').replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_');
}

export function jeton(p: Profil): string {
  const iat = Math.floor(MAINTENANT.getTime() / 1000);
  return [
    base64url({ alg: 'none', typ: 'JWT' }),
    base64url({ sub: p.userId, uid: p.userId, wsid: p.workspaceId, role: p.role, email: p.email, iat, exp: iat + 8 * 3600 }),
    'simule',
  ].join('.');
}

/** Pose la session (ou rien pour une page publique) et le theme avant tout script de la page. */
export async function poserSession(page: Page, profil: Profil | null, theme: Theme): Promise<void> {
  const choix = theme === 'sombre' ? 'SOMBRE' : 'CLAIR';
  const donnees = profil
    ? {
        access: jeton(profil),
        user: {
          userId: profil.userId,
          workspaceId: profil.workspaceId,
          workspaceCode: profil.workspaceCode,
          email: profil.email,
          role: profil.role,
        },
      }
    : null;
  await page.addInitScript(
    ({ choix, donnees }) => {
      try {
        localStorage.setItem('jurika.theme', choix);
        if (donnees) {
          localStorage.setItem('jurika_access_token', donnees.access);
          localStorage.setItem('jurika_refresh_token', 'rafraichissement-simule');
          localStorage.setItem('jurika-auth', JSON.stringify({ state: { user: donnees.user, isAuthenticated: true }, version: 0 }));
        }
      } catch {
        // Stockage indisponible : le test le verra (ecran de connexion au lieu de l'ecran attendu).
      }
    },
    { choix, donnees },
  );
}
