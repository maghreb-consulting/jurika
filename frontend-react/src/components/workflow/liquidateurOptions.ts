/**
 * Liquidateur (lot L6) : etat, options et fonctions pures, separes du selecteur
 * (LiquidateurPicker.tsx) pour que ce dernier n'exporte que des composants
 * (rafraichissement a chaud de Vite). Voir l'en-tete de LiquidateurPicker.tsx.
 */
import type { DossierPartie, DossierParties } from '../../services/workflow.service';

/** Origine du liquidateur : partie prenante du dossier (BD) ou tiers (EXTERNE). */
export type LiquidateurSource = 'BD' | 'EXTERNE';

export interface LiquidateurState {
  source: LiquidateurSource;
  /** Clé de l'option BD retenue ('' si externe). */
  partieKey: string;
  civilite: string;
  prenom: string;
  nom: string;
  cin: string;
  /** L3 (D6) : type de piece, "CIN" propose a l'ecran puis confirme. */
  pieceType?: string;
  adresse: string;
  remuneration: string;
  /**
   * Lot L4d (E5, décision du directeur du 11 octobre 2026) : le liquidateur est une
   * personne physique ou une personne morale. Absent = personne physique.
   */
  forme?: 'physique' | 'morale';
  denomination?: string;
  formeJuridique?: string;
  capital?: string;
  siegeSocial?: string;
  rcVille?: string;
  rcNumero?: string;
  ice?: string;
  representantNom?: string;
  representantQualite?: string;
  /**
   * Lot L4d (E11d) : donnees du PV de dissolution du corpus. Nationalite et date de
   * naissance (personne physique), ancien gerant (oui / non), remuneration (oui / non,
   * montant, modalites) : saisies, jamais supposees.
   */
  nationalite?: string;
  dateNaissance?: string;
  ancienGerant?: '' | 'oui' | 'non';
  remunerationApplicable?: '' | 'oui' | 'non';
  remunerationMontant?: string;
  remunerationModalites?: string;
  /** Lot L4d (E11d) : siege de la liquidation au siege social (oui) ou a une autre adresse (non). */
  siegeIdentique?: string;
}

export interface LiquidateurOption {
  key: string;
  label: string;
  role: 'gerant' | 'associe';
  civilite: string;
  prenom: string;
  nom: string;
  cin: string;
  adresse: string;
  /** Lot L4d (E5) : associé personne morale (désigné par sa dénomination). */
  morale?: boolean;
  denomination?: string;
  formeJuridique?: string;
  siegeSocial?: string;
  rcNumero?: string;
  rcVille?: string;
  ice?: string;
  representantNom?: string;
}

export const EXTERNE_KEY = '__externe__';

export function emptyLiquidateur(): LiquidateurState {
  return {
    source: 'BD',
    partieKey: '',
    civilite: 'M.',
    prenom: '',
    nom: '',
    cin: '',
    adresse: '',
    remuneration: '', // L4a (E2) : proposee a l'ecran, a confirmer
  };
}

function str(v: unknown): string {
  return v == null ? '' : String(v).trim();
}

/** Lot L4d (E5) : une partie personne morale peut être liquidateur (désignée par sa dénomination). */
function isPhysique(p: DossierPartie): boolean {
  return str(p.typePersonne).toUpperCase() !== 'MORALE';
}

/**
 * Construit les options « liquidateur » depuis les parties prenantes BD : gérants
 * d'abord (ils sont les candidats naturels), puis associés. Dédoublonnage sur le nom
 * complet (un gérant associé n'apparaît qu'une fois, avec le rôle gérant).
 */
export function buildLiquidateurOptions(
  parties: DossierParties | null | undefined,
): LiquidateurOption[] {
  const out: LiquidateurOption[] = [];
  const seen = new Set<string>();
  const push = (p: DossierPartie, role: 'gerant' | 'associe') => {
    if (!isPhysique(p)) {
      const denomination = str(p.denomination);
      if (!denomination) return;
      const cle = denomination.toLowerCase();
      if (seen.has(cle)) return;
      seen.add(cle);
      out.push({
        key: `${role}:${cle}`,
        label: `${denomination} — ${role === 'gerant' ? 'gérant' : 'associé'} (personne morale)`,
        role, civilite: '', prenom: '', nom: '', cin: '',
        adresse: str(p.siegeSocial) || str(p.adresse),
        morale: true,
        denomination,
        formeJuridique: str(p.formeJuridique),
        siegeSocial: str(p.siegeSocial) || str(p.adresse),
        rcNumero: str(p.rcNumero),
        rcVille: str(p.rcVille),
        ice: str(p.ice),
        representantNom: str(p.representantNom),
      });
      return;
    }
    const prenom = str(p.prenom);
    const nom = str(p.nom);
    const full = [prenom, nom].filter(Boolean).join(' ').trim();
    if (!full) return;
    const dedupe = full.toLowerCase();
    if (seen.has(dedupe)) return;
    seen.add(dedupe);
    out.push({
      key: `${role}:${dedupe}`,
      label: `${full} — ${role === 'gerant' ? 'gérant' : 'associé'}`,
      role,
      civilite: str(p.civilite) || 'M.',
      prenom,
      nom,
      // Fix D4 (2026-08-16) — la CIN du liquidateur n'était pas reprise de la base
      // et devait être re-saisie, alors que le liquidateur est presque toujours un
      // gérant ou un associé déjà connu. En cause : on ne lisait QUE la clé `cin`,
      // or la fiche structurée la range selon son origine sous `cinNumero`
      // (dirigeants du wizard de création) ou `pieceNumero` (voie directeur).
      cin: str(p.cin) || str(p.cinNumero) || str(p.pieceNumero),
      adresse: str(p.adresse) || str(p.domicile),
    });
  };
  (parties?.gerants ?? []).forEach((g) => push(g, 'gerant'));
  (parties?.associes ?? []).forEach((a) => push(a, 'associe'));
  return out;
}
