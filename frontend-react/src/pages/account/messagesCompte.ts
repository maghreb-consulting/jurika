import axios from 'axios';
import { extractError } from '../../lib/api';

/**
 * Lot L5 (E10) : messages affiches pour les refus du serveur sur "Mon compte"
 * et la page de confirmation. Le serveur renvoie un code stable ; le texte
 * destine a l'utilisateur est redige ici.
 */
const MESSAGES: Record<string, string> = {
  ADRESSE_INVALIDE: 'L’adresse saisie n’est pas une adresse électronique valide.',
  ADRESSE_INCHANGEE: 'Cette adresse est déjà celle de votre compte.',
  IDENTIFIANT_INCHANGE: 'Cet identifiant est déjà le vôtre.',
  PRENOM_OBLIGATOIRE: 'Le prénom est obligatoire.',
  NOM_OBLIGATOIRE: 'Le nom est obligatoire.',
  LIEN_DEJA_UTILISE:
    'Ce lien a déjà été utilisé, ou une demande plus récente l’a remplacé. Utilisez le dernier lien reçu.',
  LIEN_EXPIRE:
    'Ce lien a expiré. Refaites la demande depuis l’écran « Mon compte » : un nouveau lien vous sera envoyé.',
  ENVOI_IMPOSSIBLE:
    'Le courriel n’a pas pu être envoyé : aucune modification n’a été enregistrée. Réessayez plus tard.',
};

export function messageCompte(err: unknown): string {
  const e = extractError(err);
  if (MESSAGES[e.code]) return MESSAGES[e.code];
  const statut = axios.isAxiosError(err) ? err.response?.status : undefined;
  if (statut === 409) return 'Cet identifiant est déjà utilisé par un autre compte du cabinet.';
  if (statut === 404) return 'Ce lien n’est pas valide. Vérifiez qu’il est complet, ou refaites la demande.';
  return e.message;
}

/** Date et heure lisibles d'un instant ISO-8601 (fuseau du navigateur). */
export function dateHeure(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString('fr-FR', { dateStyle: 'long', timeStyle: 'short' });
}
