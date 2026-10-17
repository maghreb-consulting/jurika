import { useCallback, useEffect, useState } from 'react';
import { api } from '../../lib/api';

/**
 * Lot L6b (E10) : mentions legales de l'editeur, lues dans les Parametres de l'editeur
 * (saisis par le super-admin) par la route publique et anonyme
 * GET /api/v1/public/editeur/mentions-legales (billing, lot L6b E2).
 *
 * Douze cles, chacune nulle tant qu'elle n'est pas renseignee : une valeur nulle s'affiche
 * "non renseigne", jamais remplacee par une valeur supposee. Une reponse qui n'a pas cette
 * forme est une ERREUR (motif 9 : aucun repli silencieux).
 */
export const CHEMIN_MENTIONS_LEGALES = '/public/editeur/mentions-legales';

export const CLES_MENTIONS = [
  'raisonSociale',
  'formeJuridique',
  'capitalSocial',
  'siegeSocial',
  'ville',
  'rcNumero',
  'rcTribunal',
  'ice',
  'identifiantFiscal',
  'taxeProfessionnelle',
  'representantNom',
  'representantQualite',
] as const;

export type CleMention = (typeof CLES_MENTIONS)[number];
export type MentionsLegales = Record<CleMention, string | null>;

/** Libelles affiches, dans l'ordre de la page des mentions legales. */
export const LIBELLES_MENTIONS: Record<CleMention, string> = {
  raisonSociale: 'Raison sociale',
  formeJuridique: 'Forme juridique',
  capitalSocial: 'Capital social',
  siegeSocial: 'Siège social',
  ville: 'Ville',
  rcNumero: 'Numéro du registre du commerce',
  rcTribunal: 'Tribunal du registre du commerce',
  ice: 'Identifiant commun de l’entreprise (ICE)',
  identifiantFiscal: 'Identifiant fiscal',
  taxeProfessionnelle: 'Taxe professionnelle',
  representantNom: 'Représentant légal',
  representantQualite: 'Qualité du représentant légal',
};

export const NON_RENSEIGNE = 'non renseigné';

export const MESSAGE_ERREUR_MENTIONS =
  'Les mentions légales de l’éditeur n’ont pas pu être chargées. Vérifiez votre connexion, puis réessayez.';

/** Valide la reponse du serveur : les douze cles, chacune texte ou nulle. */
export function lireMentions(corps: unknown): MentionsLegales {
  if (typeof corps !== 'object' || corps === null || Array.isArray(corps)) {
    throw new Error('Réponse des mentions légales illisible');
  }
  const source = corps as Record<string, unknown>;
  const resultat = {} as MentionsLegales;
  for (const cle of CLES_MENTIONS) {
    const v = source[cle];
    if (v === null || v === undefined) {
      resultat[cle] = null;
    } else if (typeof v === 'string') {
      resultat[cle] = v.trim() === '' ? null : v.trim();
    } else {
      throw new Error(`Mention légale illisible : ${cle}`);
    }
  }
  return resultat;
}

/** Valeur affichee : la mention, ou "non renseigne". */
export function valeurMention(m: MentionsLegales, cle: CleMention): string {
  return m[cle] ?? NON_RENSEIGNE;
}

// Une seule lecture par visite (le pied de page et la page des mentions la partagent) ;
// un echec n'est pas garde : "Reessayer" relance la lecture.
let enCours: Promise<MentionsLegales> | null = null;

export function chargerMentionsLegales(): Promise<MentionsLegales> {
  if (!enCours) {
    enCours = api
      .get<unknown>(CHEMIN_MENTIONS_LEGALES)
      .then((r) => lireMentions(r.data))
      .catch((e: unknown) => {
        enCours = null;
        throw e;
      });
  }
  return enCours;
}

/** Pour les tests : oublie la lecture precedente. */
export function oublierMentionsLegales(): void {
  enCours = null;
}

export type EtatMentions =
  | { etat: 'chargement' }
  | { etat: 'erreur' }
  | { etat: 'pret'; mentions: MentionsLegales };

export function useMentionsLegales(): { resultat: EtatMentions; recharger: () => void } {
  const [resultat, setResultat] = useState<EtatMentions>({ etat: 'chargement' });
  const [essai, setEssai] = useState(0);

  useEffect(() => {
    let actif = true;
    chargerMentionsLegales().then(
      (mentions) => {
        if (actif) setResultat({ etat: 'pret', mentions });
      },
      () => {
        if (actif) setResultat({ etat: 'erreur' });
      },
    );
    return () => {
      actif = false;
    };
  }, [essai]);

  const recharger = useCallback(() => {
    setResultat({ etat: 'chargement' });
    setEssai((n) => n + 1);
  }, []);

  return { resultat, recharger };
}
