import { API_URL, SEED_URL } from './env';
import { codeTotp, fenetreTotp } from './totp';

/**
 * Lot L6b (E13) : comptes des parcours de bout en bout.
 *
 * Chaque execution cree SES cabinets par la route de seed de test de dataroom
 * (POST /api/v1/test/seed/workspace?role=..., TestSeedController, actif hors prod avec
 * jurika.test.seed.enabled=true) et les supprime a la fin (DELETE /api/v1/test/seed/cleanup/{id},
 * cascade sur les utilisateurs et le dossier). Jamais les comptes du cabinet de demonstration.
 *
 * Le seed cree : un cabinet, un utilisateur du role demande (identifiant demo-<code>@jurika.test),
 * un utilisateur CLIENT (demo-client-<code>@jurika.test), un dossier SARL dont ce client est le
 * client et l'utilisateur principal le responsable. Mot de passe : celui du seed (renvoye par la
 * route ; jamais journalise ici).
 *
 * Double authentification : selon l'etat du compte, la connexion rend une session directe, exige
 * la configuration (setup-2fa puis confirm, code TOTP calcule en Node) ou exige un code (verify-2fa).
 * Le secret TOTP reste en memoire, dans l'objet Cabinet ; jamais ecrit nulle part.
 */
export type RoleSeed = 'EMPLOYE' | 'SUPERVISEUR' | 'SUPER_ADMIN' | 'CLIENT';

export interface Cabinet {
  workspaceId: string;
  workspaceCode: string;
  principal: { email: string; role: RoleSeed; userId: string };
  client: { email: string; userId: string };
  dossierId: string;
  /** Mot de passe du seed (memoire seulement). */
  readonly motDePasse: string;
  /** Secrets TOTP par identifiant, poses a la configuration (memoire seulement). */
  readonly secrets: Map<string, string>;
}

export interface Session {
  accessToken: string;
  refreshToken: string;
  userId: string;
  workspaceId: string;
  role: string;
  email: string;
}

/** Erreur d'appel nommee (methode, chemin, statut, code) ; jamais de jeton ni de mot de passe. */
export class ErreurApi extends Error {
  readonly statut: number;
  readonly corps: unknown;
  constructor(methode: string, chemin: string, statut: number, corps: unknown) {
    const code = corps && typeof corps === 'object' ? (corps as { code?: string; message?: string }) : null;
    super(`${methode} ${chemin} -> ${statut}${code ? ` ${code.code ?? ''} ${code.message ?? ''}` : ''}`.trim());
    this.statut = statut;
    this.corps = corps;
  }
}

async function appel<T>(methode: string, url: string, chemin: string, corps?: unknown, jeton?: string): Promise<T> {
  const r = await fetch(url + chemin, {
    method: methode,
    headers: { 'Content-Type': 'application/json', ...(jeton ? { Authorization: `Bearer ${jeton}` } : {}) },
    body: corps === undefined ? undefined : JSON.stringify(corps),
  });
  const texte = await r.text();
  let json: unknown = null;
  try { json = texte ? JSON.parse(texte) : null; } catch { json = texte.slice(0, 300); }
  if (!r.ok) throw new ErreurApi(methode, chemin, r.status, json);
  return json as T;
}

export const api = {
  get: <T>(chemin: string, jeton: string) => appel<T>('GET', API_URL, chemin, undefined, jeton),
  post: <T>(chemin: string, corps: unknown, jeton?: string) => appel<T>('POST', API_URL, chemin, corps, jeton),
  put: <T>(chemin: string, corps: unknown, jeton: string) => appel<T>('PUT', API_URL, chemin, corps, jeton),
};

interface ReponseSeed {
  workspaceId: string; workspaceCode: string; adminEmail: string; employeUserId: string;
  clientUserId: string; clientEmail: string; password: string; dossierId: string;
}

export async function creerCabinet(role: Exclude<RoleSeed, 'CLIENT'>): Promise<Cabinet> {
  let s: ReponseSeed;
  try {
    s = await appel<ReponseSeed>('POST', SEED_URL, `/test/seed/workspace?role=${role}`);
  } catch (e) {
    if (e instanceof ErreurApi && e.statut === 404) {
      throw new Error('Route de seed de test absente (404) : la pile doit transmettre '
        + 'JURIKA_TEST_SEED_ENABLED=true au conteneur dataroom (profil hors prod).');
    }
    throw e;
  }
  return {
    workspaceId: s.workspaceId,
    workspaceCode: s.workspaceCode,
    principal: { email: s.adminEmail, role, userId: s.employeUserId },
    client: { email: s.clientEmail, userId: s.clientUserId },
    dossierId: s.dossierId,
    motDePasse: s.password,
    secrets: new Map(),
  };
}

export async function supprimerCabinet(c: Cabinet): Promise<void> {
  await appel('DELETE', SEED_URL, `/test/seed/cleanup/${c.workspaceId}`);
}

let derniereFenetre = -1;
/** Attend une fenetre TOTP neuve : l'anti-rejeu refuse un second code de la meme fenetre (par compte). */
async function codeNeuf(secret: string): Promise<string> {
  if (fenetreTotp() === derniereFenetre) {
    await new Promise((r) => setTimeout(r, 30_000 - (Date.now() % 30_000) + 1_000));
  }
  derniereFenetre = fenetreTotp();
  return codeTotp(secret);
}

interface ReponseLogin {
  requires2fa?: boolean; requires2faSetup?: boolean; mustChangePassword?: boolean;
  accessToken?: string; refreshToken?: string; userId: string; workspaceId: string;
}
interface Jetons { accessToken: string; refreshToken: string; userId: string; workspaceId: string }

/** Connexion d'un compte du cabinet, double authentification comprise. */
export async function connecter(c: Cabinet, qui: 'principal' | 'client'): Promise<Session> {
  const email = qui === 'principal' ? c.principal.email : c.client.email;
  const l = await api.post<ReponseLogin>('/auth/login', { workspaceCode: c.workspaceCode, email, password: c.motDePasse });
  let j: Jetons;
  if (l.requires2fa) {
    const secret = c.secrets.get(email);
    if (!secret) throw new Error(`Connexion de ${email} : un code TOTP est exige, mais aucun secret n'a ete configure par ce parcours.`);
    j = await api.post<Jetons>('/auth/verify-2fa', { userId: l.userId, workspaceId: l.workspaceId, code: await codeNeuf(secret) });
  } else if (l.requires2faSetup && l.accessToken) {
    const s = await api.post<{ secret: string }>('/auth/setup-2fa', {}, l.accessToken);
    c.secrets.set(email, s.secret);
    j = await api.post<Jetons>('/auth/setup-2fa/confirm', { code: await codeNeuf(s.secret) }, l.accessToken);
  } else if (l.accessToken && l.refreshToken) {
    j = { accessToken: l.accessToken, refreshToken: l.refreshToken, userId: l.userId, workspaceId: l.workspaceId };
  } else {
    throw new Error(`Connexion de ${email} : reponse inattendue (${Object.keys(l).join(', ')}).`);
  }
  if (l.mustChangePassword) throw new Error(`Connexion de ${email} : changement de mot de passe exige (seed inattendu).`);
  const role = qui === 'principal' ? c.principal.role : 'CLIENT';
  return { ...j, role, email };
}
