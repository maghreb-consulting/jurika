import { expect } from '@playwright/test';
import { API_URL } from './env';

/**
 * Recette R1 : messages (SMS, courriels) envoyes a une identite de test, lus par la route de test
 * d'auth-service (GET /api/v1/test/messages?adresse=...@jurika.test ; MessagesDeTestController,
 * absente en production). Le parcours de l'essai y lit le code SMS, le lien de confirmation et le
 * courriel de bienvenue (code du cabinet, identifiant, mot de passe provisoire), qu'il ne pourrait
 * lire autrement (journal d'auth, fournisseur de courriel reel). Rien n'est ecrit sur disque.
 */
export interface CourrielDeTest {
  adresse: string;
  sujet: string;
  gabarit: string;
  variables: Record<string, string>;
  envoyeLe: string;
}

export interface SmsDeTest {
  telephone: string;
  texte: string;
  envoyeLe: string;
}

interface Boite {
  courriels: CourrielDeTest[];
  sms: SmsDeTest[];
}

async function lire(adresse: string): Promise<Boite> {
  const r = await fetch(`${API_URL}/test/messages?adresse=${encodeURIComponent(adresse)}`);
  if (r.status === 404) {
    throw new Error('Route des messages de test absente (404) : la pile doit transmettre '
      + 'JURIKA_TEST_SEED_ENABLED=true a auth-service et a la passerelle (profil hors prod).');
  }
  if (!r.ok) throw new Error(`GET /test/messages -> ${r.status}`);
  return (await r.json()) as Boite;
}

/** Dernier courriel du gabarit donne envoye a l'adresse (attendu jusqu'a 30 s). */
export async function dernierCourriel(adresse: string, gabarit: string): Promise<CourrielDeTest> {
  let trouve: CourrielDeTest | undefined;
  await expect.poll(async () => {
    trouve = (await lire(adresse)).courriels.filter((c) => c.gabarit === gabarit).at(-1);
    return trouve ? 'recu' : 'attendu';
  }, { message: `courriel "${gabarit}" a ${adresse}`, timeout: 30_000 }).toBe('recu');
  return trouve!;
}

/** Code a six chiffres du dernier SMS envoye au telephone d'une demande faite avec l'adresse. */
export async function dernierCodeSms(adresse: string): Promise<string> {
  let code: string | undefined;
  await expect.poll(async () => {
    code = (await lire(adresse)).sms.at(-1)?.texte.match(/\b(\d{6})\b/)?.[1];
    return code ? 'recu' : 'attendu';
  }, { message: `SMS de la demande de ${adresse}`, timeout: 30_000 }).toBe('recu');
  return code!;
}
