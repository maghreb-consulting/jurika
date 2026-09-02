/**
 * Sous-formulaire de séance d'AG — COMMUN et RÉUTILISABLE (Phase A, 2026-08-08).
 *
 * Capture le nécessaire d'une assemblée : type/date/heure/lieu, convocation
 * (mode, date, auteur, rang, 2e convocation), bureau (président/secrétaire),
 * présences par associé (présent/représenté/absent + mandataire + voix), ordre
 * du jour, documents joints, résolutions. La liste des associés et l'identité
 * société sont pré-remplies depuis le dossier par la page hôte.
 *
 * Réutilisé par : ConvocationPanel (début de workflow), FeuillePresencePanel
 * (après l'AG) et IncidentSeancePanel (PV incident). Chaque panneau choisit les
 * sections visibles via `sections` et alimente le même contrat de séance
 * consommé côté back par SeancePvVarsBuilder (double nommage).
 */
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Plus, Trash2 } from 'lucide-react';
import { TextField } from '../ui/TextField';
import { Select } from '../ui/Select';

// --------------------------------------------------------------------------
// Types publics
// --------------------------------------------------------------------------
export type FormeJuridique = 'SARL' | 'SARL_AU';

export interface SeanceSocieteInput {
  denomination?: string | null;
  capitalChiffres?: number | string | null;
  siegeSocial?: string | null;
  nombreParts?: number | string | null;
  rcNumero?: string | null;
  villeGreffe?: string | null;
}

export interface SeanceDefaults {
  type?: 'ordinaire' | 'extraordinaire' | 'mixte';
  date?: string;
  heure?: string;
  lieu?: string;
  presidentNom?: string;
  /**
   * Fix D1/ETR (2026-08-16) — qualité du président de séance.
   *
   * Le défaut « Gérant » est juste pour une AG d'associés marocains, mais FAUX pour
   * la décision de l'organe compétent d'une société mère étrangère : ce n'est pas
   * une gérance qui préside un conseil. Chaque appelant peut donc l'imposer.
   */
  presidentQualite?: string;
  convocationRang?: 'première' | 'deuxième';
  /** Date de convocation pilotée en amont (bloc PV) — évite un champ dupliqué. */
  convDate?: string;
  /**
   * Heure de convocation pilotée en amont, comme `convDate` (2026-08-14).
   *
   * Elle manquait : les pages d'étape 1 la saisissaient (« Heure de convocation »)
   * mais le formulaire de séance ne la portait pas, si bien qu'elle n'atteignait
   * jamais le document — et l'employé voyait deux champs d'heure sans comprendre
   * lequel comptait.
   */
  convHeure?: string;
}

export interface AssocieInput {
  typePersonne: 'PHYSIQUE' | 'MORALE';
  civilite: string;
  prenom: string;
  nom: string;
  denomination: string;
  adresse: string;
  nombreParts: string;
  nombreVoix: string;
  presence: 'présent' | 'représenté' | 'absent';
  mandataireNom: string;
  /**
   * Fix M6 (2026-08-16) — n° de pièce d'identité (CIN).
   *
   * La comparution SARL AU des modèles directeur écrit « titulaire de la
   * $ASSOCIE_PIECE_TYPE n° $ASSOCIE_PIECE_NUMERO » ; le formulaire de séance ne
   * portait PAS ce champ, si bien que les PV d'associé unique sortaient avec
   * « CIN n° , » alors que la CIN est en base depuis la création.
   */
  pieceType?: string;
  pieceNumero?: string;
  /** L'associé exerce aussi la gérance (désignation depuis la liste des associés). */
  estGerant?: boolean;
}

export interface ResolutionInput {
  intitule: string;
  texte: string;
  voixPour: string;
  voixContre: string;
  abstentions: string;
  resultat: string;
}

export interface GerantInput {
  civilite: string;
  prenom: string;
  nom: string;
}

/** Sections affichables du formulaire — chaque panneau active ce dont il a besoin. */
export interface SeanceSections {
  seance?: boolean;
  bureau?: boolean;
  convocation?: boolean;
  convocationRang?: boolean;
  irregularite?: boolean;
  secondeAssemblee?: boolean;
  ordreDuJour?: boolean;
  documentsJoints?: boolean;
  resolutions?: boolean;
  presence?: boolean;
  voix?: boolean;
}

export function emptyAssocie(): AssocieInput {
  return {
    typePersonne: 'PHYSIQUE',
    civilite: 'M.',
    prenom: '',
    nom: '',
    denomination: '',
    adresse: '',
    nombreParts: '',
    nombreVoix: '',
    presence: 'présent',
    mandataireNom: '',
    pieceType: 'CIN',
    pieceNumero: '',
    estGerant: false,
  };
}

export function emptyResolution(): ResolutionInput {
  return { intitule: '', texte: '', voixPour: '', voixContre: '', abstentions: '', resultat: 'adoptée' };
}

export function emptyGerant(): GerantInput {
  return { civilite: 'M.', prenom: '', nom: '' };
}

/** Comparaison de listes courtes par sérialisation (suivi des valeurs auto-appliquées). */
function sameList(a: unknown, b: unknown): boolean {
  return JSON.stringify(a) === JSON.stringify(b);
}

// --------------------------------------------------------------------------
// Hook d'état + construction du payload
// --------------------------------------------------------------------------
export interface UseSeanceFormOptions {
  formeJuridique: FormeJuridique;
  societe: SeanceSocieteInput;
  defaults?: SeanceDefaults;
  initialAssocies?: AssocieInput[];
  /** Gérants pré-remplis depuis la BD (fiche société) — évite la re-saisie. */
  initialGerants?: GerantInput[];
  /** Ordre du jour pré-rempli (ex. libellés des modifications choisies) — évite la re-saisie. */
  initialOrdreDuJour?: string[];
  /**
   * L'ordre du jour est ENTIÈREMENT DÉRIVÉ de `initialOrdreDuJour` (modifications
   * choisies) : synchronisation inconditionnelle, aucune saisie manuelle possible.
   */
  lockOrdreDuJour?: boolean;
  /**
   * Instantané persisté de l'état complet du formulaire (présence, bureau, ordre du
   * jour, convocation…). Restauré au remontage du panneau pour NE PAS perdre les
   * saisies manuelles en changeant d'étape. Priorité sur les valeurs BD/defaults.
   */
  initialSnapshot?: Record<string, unknown> | null;
}

export interface SeanceFormApi {
  isAU: boolean;
  societe: SeanceSocieteInput;
  // séance
  type: NonNullable<SeanceDefaults['type']>;
  setType: (v: NonNullable<SeanceDefaults['type']>) => void;
  date: string; setDate: (v: string) => void;
  heure: string; setHeure: (v: string) => void;
  lieu: string; setLieu: (v: string) => void;
  heureCloture: string; setHeureCloture: (v: string) => void;
  presidentNom: string; setPresidentNom: (v: string) => void;
  presidentQualite: string; setPresidentQualite: (v: string) => void;
  secretairePresent: 'oui' | 'non'; setSecretairePresent: (v: 'oui' | 'non') => void;
  secretaireNom: string; setSecretaireNom: (v: string) => void;
  // convocation
  convAuteur: string; setConvAuteur: (v: string) => void;
  convHeure: string; setConvHeure: (v: string) => void;
  convDate: string; setConvDate: (v: string) => void;
  convMode: string; setConvMode: (v: string) => void;
  convRang: 'première' | 'deuxième'; setConvRang: (v: 'première' | 'deuxième') => void;
  dateAgPremiere: string; setDateAgPremiere: (v: string) => void;
  lieuSignature: string; setLieuSignature: (v: string) => void;
  irregulariteNature: string; setIrregulariteNature: (v: string) => void;
  // 2e assemblée
  secondeDate: string; setSecondeDate: (v: string) => void;
  secondeHeure: string; setSecondeHeure: (v: string) => void;
  secondeLieu: string; setSecondeLieu: (v: string) => void;
  suiteAssemblee: 'renvoi' | 'régularisation'; setSuiteAssemblee: (v: 'renvoi' | 'régularisation') => void;
  // listes
  ordreDuJour: string[]; setOrdreDuJour: React.Dispatch<React.SetStateAction<string[]>>;
  documentsJoints: string[]; setDocumentsJoints: React.Dispatch<React.SetStateAction<string[]>>;
  resolutions: ResolutionInput[]; setResolutions: React.Dispatch<React.SetStateAction<ResolutionInput[]>>;
  associes: AssocieInput[]; setAssocies: React.Dispatch<React.SetStateAction<AssocieInput[]>>;
  gerants: GerantInput[]; setGerants: React.Dispatch<React.SetStateAction<GerantInput[]>>;
  buildPayload: () => Record<string, unknown>;
  /** Instantané brut de l'état (persistance inter-étapes). */
  snapshot: Record<string, unknown>;
}

export function useSeanceForm(opts: UseSeanceFormOptions): SeanceFormApi {
  const { formeJuridique, societe, defaults, initialAssocies, initialGerants,
          initialOrdreDuJour, lockOrdreDuJour, initialSnapshot } = opts;
  const isAU = formeJuridique === 'SARL_AU';
  // Instantané persisté (priorité sur BD/defaults) : restaure les saisies manuelles.
  const snap = initialSnapshot ?? undefined;
  const sv = <T,>(key: string, def: T): T =>
    snap && snap[key] !== undefined && snap[key] !== null ? (snap[key] as T) : def;

  const [type, setType] = useState<NonNullable<SeanceDefaults['type']>>(
    sv('type', defaults?.type ?? 'extraordinaire'),
  );
  const [date, setDate] = useState(sv('date', defaults?.date ?? ''));
  const [heure, setHeure] = useState(sv('heure', defaults?.heure ?? ''));
  const [lieu, setLieu] = useState(sv('lieu', defaults?.lieu ?? societe.siegeSocial ?? ''));
  const [heureCloture, setHeureCloture] = useState(sv('heureCloture', ''));
  const [presidentNom, setPresidentNom] = useState(sv('presidentNom', defaults?.presidentNom ?? ''));
  const [presidentQualite, setPresidentQualite] = useState(
    sv('presidentQualite', defaults?.presidentQualite ?? 'Gérant'),
  );
  const [secretairePresent, setSecretairePresent] = useState<'oui' | 'non'>(sv('secretairePresent', 'non'));
  const [secretaireNom, setSecretaireNom] = useState(sv('secretaireNom', ''));

  const [convAuteur, setConvAuteur] = useState(sv('convAuteur', 'la gérance'));
  const [convDate, setConvDate] = useState(sv('convDate', defaults?.convDate ?? ''));
  const [convHeure, setConvHeure] = useState(sv('convHeure', defaults?.convHeure ?? ''));
  const [convMode, setConvMode] = useState(sv('convMode', 'lettre recommandée avec accusé de réception'));
  const [convRang, setConvRang] = useState<'première' | 'deuxième'>(
    sv('convRang', defaults?.convocationRang ?? 'première'),
  );
  const [dateAgPremiere, setDateAgPremiere] = useState(sv('dateAgPremiere', ''));
  const [lieuSignature, setLieuSignature] = useState(sv('lieuSignature', societe.villeGreffe ?? ''));
  const [irregulariteNature, setIrregulariteNature] = useState(sv('irregulariteNature', ''));

  const [secondeDate, setSecondeDate] = useState(sv('secondeDate', ''));
  const [secondeHeure, setSecondeHeure] = useState(sv('secondeHeure', ''));
  const [secondeLieu, setSecondeLieu] = useState(sv('secondeLieu', ''));
  const [suiteAssemblee, setSuiteAssemblee] = useState<'renvoi' | 'régularisation'>(sv('suiteAssemblee', 'renvoi'));

  // Ordre du jour / associés / gérants : on préfère la SOURCE (modifications / BD) et on
  // ne restaure l'instantané QUE s'il contient de vraies données saisies — sinon un
  // instantané vide (capturé avant chargement) défait le pré-remplissage.
  const snapOdj = snap?.ordreDuJour as string[] | undefined;
  // Verrouillé : l'instantané n'a pas voix au chapitre (la source = les modifications).
  const snapOdjUsable = !lockOrdreDuJour && !!(snapOdj && snapOdj.some((x) => x && x.trim()));
  const autoOdj = initialOrdreDuJour && initialOrdreDuJour.length > 0 ? initialOrdreDuJour : null;
  const [ordreDuJour, setOrdreDuJour] = useState<string[]>(
    snapOdjUsable ? (snapOdj as string[]) : (autoOdj ?? ['']),
  );
  // Dernière valeur AUTO appliquée : permet de re-synchroniser dynamiquement l'ordre du
  // jour quand la sélection de modifications change, tout en respectant une édition
  // manuelle (si l'utilisateur a modifié la liste, on ne l'écrase plus).
  const lastAutoOdjRef = useRef<string[] | null>(snapOdjUsable ? null : autoOdj);
  const [documentsJoints, setDocumentsJoints] = useState<string[]>(sv('documentsJoints', [
    'Texte des résolutions proposées',
    'Formule de pouvoir',
  ]));
  const [resolutions, setResolutions] = useState<ResolutionInput[]>(sv('resolutions', []));
  const snapAssoc = snap?.associes as AssocieInput[] | undefined;
  const snapAssocUsable = !!(snapAssoc
    && snapAssoc.some((a) => a?.nom?.trim() || a?.prenom?.trim() || a?.denomination?.trim()));
  const autoAssoc = initialAssocies && initialAssocies.length > 0 ? initialAssocies : null;
  const [associes, setAssocies] = useState<AssocieInput[]>(
    snapAssocUsable ? (snapAssoc as AssocieInput[]) : (autoAssoc ?? [emptyAssocie()]),
  );
  const lastAutoAssocRef = useRef<AssocieInput[] | null>(snapAssocUsable ? null : autoAssoc);

  const snapGer = snap?.gerants as GerantInput[] | undefined;
  const snapGerUsable = !!(snapGer && snapGer.some((g) => g?.nom?.trim() || g?.prenom?.trim()));
  const autoGer = initialGerants && initialGerants.length > 0 ? initialGerants : null;
  const [gerants, setGerants] = useState<GerantInput[]>(
    snapGerUsable ? (snapGer as GerantInput[]) : (autoGer ?? [emptyGerant()]),
  );
  const lastAutoGerRef = useRef<GerantInput[] | null>(snapGerUsable ? null : autoGer);

  // Re-synchronisation quand les données BD arrivent APRÈS le montage (chargement async
  // de `getDossierParties`) : les `initialAssocies`/`initialGerants` sont vides au montage.
  // On ne remplace QUE si la liste courante est encore le défaut vierge (aucune saisie
  // utilisateur ni instantané restauré) → jamais d'écrasement des saisies manuelles.
  // Associés : dynamiques aussi (changement de société sélectionnée), sauf édition manuelle.
  useEffect(() => {
    if (!initialAssocies || initialAssocies.length === 0) return;
    setAssocies((prev) => {
      const vierge = prev.every((a) => !a?.nom?.trim() && !a?.prenom?.trim() && !a?.denomination?.trim());
      const inchangeDepuisAuto =
        lastAutoAssocRef.current !== null && sameList(prev, lastAutoAssocRef.current);
      if (vierge || inchangeDepuisAuto) {
        lastAutoAssocRef.current = initialAssocies;
        return initialAssocies;
      }
      return prev;
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [initialAssocies]);
  useEffect(() => {
    if (!initialGerants || initialGerants.length === 0) return;
    setGerants((prev) => {
      const vierge = prev.every((g) => !g?.nom?.trim() && !g?.prenom?.trim());
      const inchangeDepuisAuto =
        lastAutoGerRef.current !== null && sameList(prev, lastAutoGerRef.current);
      if (vierge || inchangeDepuisAuto) {
        lastAutoGerRef.current = initialGerants;
        return initialGerants;
      }
      return prev;
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [initialGerants]);
  /*
   * Amorçage DIFFÉRÉ des champs dérivés du dossier (2026-08-14).
   *
   * `lieu`, `lieuSignature` et `presidentNom` étaient initialisés par
   * `useState(societe.siegeSocial ?? '')` et consorts. Or le dossier est chargé de
   * façon asynchrone : au premier render ces sources valent `undefined`, les champs
   * se figent à vide et ne sont jamais réévalués. C'est exactement le défaut corrigé
   * au niveau des pages (montage différé), reproduit ici une strate plus bas.
   *
   * Constaté en auditant l'étape 1 de Dissolution : lieu de séance et lieu de
   * signature vides alors que le siège social et la ville du greffe sont en base.
   *
   * On n'écrase JAMAIS une saisie : on ne remplit que si le champ est encore vide.
   */
  useEffect(() => {
    const siege = (defaults?.lieu ?? societe.siegeSocial ?? '').toString().trim();
    if (siege) setLieu((prev) => (prev.trim() ? prev : siege));
  }, [defaults?.lieu, societe.siegeSocial]);

  useEffect(() => {
    const ville = (societe.villeGreffe ?? '').toString().trim();
    if (ville) setLieuSignature((prev) => (prev.trim() ? prev : ville));
  }, [societe.villeGreffe]);

  // Président de séance : par défaut le premier gérant. Il n'était alimenté par
  // rien, alors que la gérance est chargée depuis le dossier juste à côté.
  useEffect(() => {
    const g = gerants.find((x) => x?.nom?.trim() || x?.prenom?.trim());
    if (!g) return;
    const nom = [g.civilite, g.prenom, g.nom].filter(Boolean).join(' ').trim();
    if (nom) setPresidentNom((prev) => (prev.trim() ? prev : nom));
  }, [gerants]);

  // Date d'assemblée + date de convocation pilotées en amont (bloc PV) : on les
  // mirrore quand elles changent (les champs sont masqués via hideSeanceDate/hideConvDate).
  useEffect(() => {
    if (defaults?.date) setDate((prev) => (defaults.date && defaults.date !== prev ? defaults.date : prev));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [defaults?.date]);
  useEffect(() => {
    if (defaults?.convDate !== undefined) {
      setConvDate((prev) => ((defaults.convDate ?? '') !== prev ? defaults.convDate ?? '' : prev));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [defaults?.convDate]);
  // Ordre du jour = libellés des modifications choisies (re-sync si encore vierge).
  // Ordre du jour DYNAMIQUE : suit la sélection des modifications (ajout/retrait/
  // changement de type) tant que l'utilisateur n'a pas édité la liste à la main.
  // Sans le suivi `lastAutoOdjRef`, la liste restait figée sur la 1re modification
  // (elle n'était plus « vierge », donc jamais re-synchronisée).
  useEffect(() => {
    if (!initialOrdreDuJour) return;
    // Verrouillé (ordre du jour dérivé) : synchronisation INCONDITIONNELLE — la seule
    // commande est la sélection des modifications, aucune saisie manuelle à préserver.
    if (lockOrdreDuJour) {
      setOrdreDuJour((prev) => (sameList(prev, initialOrdreDuJour) ? prev : initialOrdreDuJour));
      lastAutoOdjRef.current = initialOrdreDuJour;
      return;
    }
    if (initialOrdreDuJour.length === 0) return;
    setOrdreDuJour((prev) => {
      const vierge = prev.every((x) => !x?.trim());
      const inchangeDepuisAuto =
        lastAutoOdjRef.current !== null && sameList(prev, lastAutoOdjRef.current);
      if (vierge || inchangeDepuisAuto) {
        lastAutoOdjRef.current = initialOrdreDuJour;
        return initialOrdreDuJour;
      }
      return prev; // édition manuelle : on respecte la saisie de l'utilisateur
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [initialOrdreDuJour, lockOrdreDuJour]);

  const buildPayload = useCallback((): Record<string, unknown> => {
    return {
      formeJuridique,
      associeUnique: isAU,
      societe: {
        denomination: societe.denomination ?? '',
        capitalChiffres: societe.capitalChiffres ?? null,
        siegeSocial: societe.siegeSocial ?? null,
        nombreParts: societe.nombreParts ?? null,
        rcNumero: societe.rcNumero ?? null,
        villeGreffe: societe.villeGreffe ?? null,
        formeJuridique,
      },
      seance: {
        type, date, heure, lieu, heureCloture, presidentNom, presidentQualite,
        secretairePresent,
        secretaireNom: secretairePresent === 'oui' ? secretaireNom : '',
      },
      convocation: {
        auteur: convAuteur,
        date: convDate,
        heure: convHeure,
        mode: convMode,
        rang: convRang,
        dateAgPremiere,
        lieuSignature,
        irregulariteNature,
      },
      secondeAssemblee: { date: secondeDate, heure: secondeHeure, lieu: secondeLieu },
      suiteAssemblee,
      ordreDuJour: ordreDuJour.map((p) => p.trim()).filter(Boolean),
      documentsJoints: documentsJoints.map((d) => d.trim()).filter(Boolean),
      resolutions: resolutions.map((r) => ({ ...r })),
      // Gérants = liste manuelle + associés cochés « gérant » (désignation #5), dédupliqués.
      gerants: mergeGerants(gerants, associes),
      associes: associes.map(serializeAssocie),
    };
  }, [
    formeJuridique, isAU, societe, type, date, heure, lieu, heureCloture, presidentNom,
    presidentQualite, secretairePresent, secretaireNom, convAuteur, convDate, convHeure, convMode,
    convRang, dateAgPremiere, lieuSignature, irregulariteNature, secondeDate, secondeHeure,
    secondeLieu, suiteAssemblee, ordreDuJour, documentsJoints, resolutions, gerants, associes,
  ]);

  // Instantané brut de l'état (persistance #7) — restauré via `initialSnapshot`.
  const snapshot = useMemo<Record<string, unknown>>(() => ({
    type, date, heure, lieu, heureCloture, presidentNom, presidentQualite,
    secretairePresent, secretaireNom, convAuteur, convDate, convHeure, convMode, convRang,
    dateAgPremiere, lieuSignature, irregulariteNature, secondeDate, secondeHeure,
    secondeLieu, suiteAssemblee, ordreDuJour, documentsJoints, resolutions, associes, gerants,
  }), [
    type, date, heure, lieu, heureCloture, presidentNom, presidentQualite,
    secretairePresent, secretaireNom, convAuteur, convDate, convHeure, convMode, convRang,
    dateAgPremiere, lieuSignature, irregulariteNature, secondeDate, secondeHeure,
    secondeLieu, suiteAssemblee, ordreDuJour, documentsJoints, resolutions, associes, gerants,
  ]);

  return {
    isAU, societe,
    type, setType, date, setDate, heure, setHeure, lieu, setLieu, heureCloture, setHeureCloture,
    presidentNom, setPresidentNom, presidentQualite, setPresidentQualite,
    secretairePresent, setSecretairePresent, secretaireNom, setSecretaireNom,
    convAuteur, setConvAuteur, convDate, setConvDate, convHeure, setConvHeure,
    convMode, setConvMode,
    convRang, setConvRang, dateAgPremiere, setDateAgPremiere, lieuSignature, setLieuSignature,
    irregulariteNature, setIrregulariteNature,
    secondeDate, setSecondeDate, secondeHeure, setSecondeHeure, secondeLieu, setSecondeLieu,
    suiteAssemblee, setSuiteAssemblee,
    ordreDuJour, setOrdreDuJour, documentsJoints, setDocumentsJoints,
    resolutions, setResolutions, associes, setAssocies, gerants, setGerants,
    buildPayload, snapshot,
  };
}

/** Fusionne la liste de gérants manuels et les associés cochés « gérant » (dédup par nom). */
function mergeGerants(gerants: GerantInput[], associes: AssocieInput[]) {
  const out: { civilite: string; prenom: string; nom: string }[] = [];
  const seen = new Set<string>();
  const push = (civilite: string, prenom: string, nom: string) => {
    const key = `${prenom.trim().toLowerCase()}|${nom.trim().toLowerCase()}`;
    if ((!prenom.trim() && !nom.trim()) || seen.has(key)) return;
    seen.add(key);
    out.push({ civilite, prenom, nom });
  };
  for (const g of gerants) push(g.civilite, g.prenom, g.nom);
  for (const a of associes) {
    if (a.estGerant && a.typePersonne === 'PHYSIQUE') push(a.civilite, a.prenom, a.nom);
  }
  return out;
}

export function serializeAssocie(a: AssocieInput) {
  return {
    typePersonne: a.typePersonne,
    civilite: a.civilite,
    prenom: a.prenom,
    nom: a.nom,
    denomination: a.denomination,
    adresse: a.adresse,
    nombreParts: a.nombreParts,
    nombreVoix: a.nombreVoix,
    presence: a.presence,
    mandataireNom: a.presence === 'représenté' ? a.mandataireNom : '',
    // Fix M6 — alimente $ASSOCIE_PIECE_TYPE / $ASSOCIE_PIECE_NUMERO (comparution AU).
    pieceType: a.pieceType?.trim() || 'CIN',
    pieceNumero: a.pieceNumero ?? '',
  };
}

// --------------------------------------------------------------------------
// Rendu des sections
// --------------------------------------------------------------------------
export function SeanceFormFields({
  form,
  sections,
  lockType,
  hideSeanceDate,
  hideSeanceHeure,
  hideConvDate,
  lockOrdreDuJour,
}: {
  form: SeanceFormApi;
  sections: SeanceSections;
  /** Masque le sélecteur « Type d'assemblée » quand il est déjà saisi en amont
   *  (ex. workflow Modification : nature choisie à l'étape 1). Évite le doublon. */
  lockType?: boolean;
  /** Masque la date de séance (= date de l'assemblée, déjà saisie en amont). */
  hideSeanceDate?: boolean;
  /** Masque l'heure de l'assemblée (déjà saisie en amont). */
  hideSeanceHeure?: boolean;
  /**
   * Masque la convocation DATE ET HEURE (déjà saisies en amont). Les deux vont
   * ensemble : une étape 1 qui porte la convocation porte les deux champs.
   */
  hideConvDate?: boolean;
  /**
   * Ordre du jour ENTIÈREMENT DÉRIVÉ des modifications choisies : affiché en lecture
   * seule, sans ajout / suppression / édition. Le seul contrôle est la sélection des
   * types de modification (étape 1).
   */
  lockOrdreDuJour?: boolean;
}) {
  const f = form;
  return (
    <div className="space-y-5">
      {sections.seance && (
        <Card title="Séance">
          <div className="grid gap-3 md:grid-cols-3">
            {!lockType && (
              <Select
                label="Type d'assemblée"
                value={f.type}
                onChange={(e) => f.setType(e.target.value as NonNullable<SeanceDefaults['type']>)}
                options={[
                  { value: 'ordinaire', label: 'Ordinaire' },
                  { value: 'extraordinaire', label: 'Extraordinaire' },
                  { value: 'mixte', label: 'Mixte' },
                ]}
              />
            )}
            {!hideSeanceDate && (
              <TextField label="Date" type="date" value={f.date} onChange={(e) => f.setDate(e.target.value)} />
            )}
            {!hideSeanceHeure && (
              <TextField label="Heure de l'assemblée" type="time" value={f.heure} onChange={(e) => f.setHeure(e.target.value)} />
            )}
            <div className="md:col-span-3">
              <TextField label="Lieu" value={f.lieu} onChange={(e) => f.setLieu(e.target.value)} placeholder="Ex : siège social" />
            </div>
            {sections.bureau && (
              <>
                <TextField label="Heure de clôture" type="time" value={f.heureCloture} onChange={(e) => f.setHeureCloture(e.target.value)} />
                <TextField label="Président — Nom" value={f.presidentNom} onChange={(e) => f.setPresidentNom(e.target.value)} />
                <TextField label="Président — Qualité" value={f.presidentQualite} onChange={(e) => f.setPresidentQualite(e.target.value)} placeholder="Ex : Gérant" />
                <Select
                  label="Secrétaire présent ?"
                  value={f.secretairePresent}
                  onChange={(e) => f.setSecretairePresent(e.target.value as 'oui' | 'non')}
                  options={[{ value: 'non', label: 'Non' }, { value: 'oui', label: 'Oui' }]}
                />
                {f.secretairePresent === 'oui' && (
                  <TextField label="Secrétaire — Nom" value={f.secretaireNom} onChange={(e) => f.setSecretaireNom(e.target.value)} />
                )}
              </>
            )}
          </div>
        </Card>
      )}

      {sections.convocation && (
        <Card title="Convocation">
          <div className="grid gap-3 md:grid-cols-3">
            <TextField label="Auteur de la convocation" value={f.convAuteur} onChange={(e) => f.setConvAuteur(e.target.value)} placeholder="Ex : la gérance" />
            {/* Date ET heure sont masquees ensemble : quand l'etape 1 porte la
                convocation, elle porte les deux. Les redemander ici obligeait a
                ressaisir la meme donnee a deux endroits. */}
            {!hideConvDate && (
              <>
                <TextField label="Date de convocation" type="date" value={f.convDate} onChange={(e) => f.setConvDate(e.target.value)} />
                <TextField label="Heure de convocation" type="time" value={f.convHeure} onChange={(e) => f.setConvHeure(e.target.value)} />
              </>
            )}
            <TextField label="Mode de convocation" value={f.convMode} onChange={(e) => f.setConvMode(e.target.value)} placeholder="Ex : LRAR" />
            {sections.convocationRang && (
              <>
                <Select
                  label="Rang de convocation"
                  value={f.convRang}
                  onChange={(e) => f.setConvRang(e.target.value as 'première' | 'deuxième')}
                  options={[{ value: 'première', label: 'Première' }, { value: 'deuxième', label: 'Deuxième' }]}
                />
                {f.convRang === 'deuxième' && (
                  <TextField label="Date 1re assemblée" type="date" value={f.dateAgPremiere} onChange={(e) => f.setDateAgPremiere(e.target.value)} />
                )}
                <TextField label="Lieu de signature" value={f.lieuSignature} onChange={(e) => f.setLieuSignature(e.target.value)} placeholder="Ex : Casablanca" />
              </>
            )}
            {sections.irregularite && (
              <div className="md:col-span-3">
                <TextField
                  label="Nature de l'irrégularité"
                  value={f.irregulariteNature}
                  onChange={(e) => f.setIrregulariteNature(e.target.value)}
                  placeholder="Ex : délai non respecté, associé non convoqué…"
                  hint="Uniquement pour le PV d'irrégularité de convocation."
                />
              </div>
            )}
          </div>
        </Card>
      )}

      {sections.secondeAssemblee && (
        <Card title="Seconde assemblée et suite donnée">
          <div className="grid gap-3 md:grid-cols-4">
            <TextField label="Date (2e assemblée)" type="date" value={f.secondeDate} onChange={(e) => f.setSecondeDate(e.target.value)} />
            <TextField label="Heure (2e assemblée)" type="time" value={f.secondeHeure} onChange={(e) => f.setSecondeHeure(e.target.value)} />
            <div className="md:col-span-2">
              <TextField label="Lieu (2e assemblée)" value={f.secondeLieu} onChange={(e) => f.setSecondeLieu(e.target.value)} />
            </div>
            <div className="md:col-span-2">
              <Select
                label="Suite donnée"
                value={f.suiteAssemblee}
                onChange={(e) => f.setSuiteAssemblee(e.target.value as 'renvoi' | 'régularisation')}
                options={[
                  { value: 'renvoi', label: 'Renvoi (2e convocation)' },
                  { value: 'régularisation', label: 'Régularisation' },
                ]}
              />
            </div>
          </div>
        </Card>
      )}

      {sections.ordreDuJour && (
        lockOrdreDuJour ? (
          /* Ordre du jour DÉRIVÉ : lecture seule, piloté uniquement par la sélection
             des modifications (aucun ajout / retrait / édition possible ici). */
          <Card title="Ordre du jour">
            <p className="mb-2 text-xs text-fg-subtle">
              Découle automatiquement des <strong>modifications choisies</strong> (étape 1) —
              pour le changer, modifiez la sélection.
            </p>
            {f.ordreDuJour.filter((pt) => pt?.trim()).length === 0 ? (
              <p className="rounded-lg bg-bg-overlay p-3 text-xs text-fg-subtle">
                Aucune modification sélectionnée pour le moment.
              </p>
            ) : (
              <ol className="space-y-1.5" data-testid="seance-odj-readonly">
                {f.ordreDuJour
                  .filter((pt) => pt?.trim())
                  .map((pt, i) => (
                    <li
                      key={`${i}-${pt}`}
                      className="flex items-start gap-2 rounded-lg bg-bg-overlay px-3 py-2 text-sm text-fg"
                    >
                      <span className="flex h-5 w-5 flex-shrink-0 items-center justify-center rounded bg-accent text-[10px] font-bold text-bg-raised">
                        {i + 1}
                      </span>
                      <span className="flex-1">{pt}</span>
                    </li>
                  ))}
              </ol>
            )}
          </Card>
        ) : (
          <Card
            title="Ordre du jour"
            action={<SmallAddButton label="Ajouter un point" onClick={() => f.setOrdreDuJour((p) => [...p, ''])} />}
          >
            <div className="space-y-2">
              {f.ordreDuJour.map((pt, i) => (
                <div key={i} className="flex items-center gap-2">
                  <div className="flex-1">
                    <TextField
                      value={pt}
                      onChange={(e) => f.setOrdreDuJour((p) => p.map((x, j) => (j === i ? e.target.value : x)))}
                      placeholder={`Point ${i + 1}`}
                      // Le titre de bloc porte le sens a l'ecran ; hors contexte
                      // visuel, « Point 1 » seul ne dit pas de quoi il s'agit.
                      aria-label={`Point ${i + 1} de l'ordre du jour`}
                    />
                  </div>
                  <RemoveButton onClick={() => f.setOrdreDuJour((p) => p.filter((_, j) => j !== i))} disabled={f.ordreDuJour.length <= 1} />
                </div>
              ))}
            </div>
          </Card>
        )
      )}

      {sections.documentsJoints && (
        <Card
          title="Documents joints à la convocation"
          action={<SmallAddButton label="Ajouter un document" onClick={() => f.setDocumentsJoints((p) => [...p, ''])} />}
        >
          <div className="space-y-2">
            {f.documentsJoints.map((d, i) => (
              <div key={i} className="flex items-center gap-2">
                <div className="flex-1">
                  <TextField
                    value={d}
                    onChange={(e) => f.setDocumentsJoints((p) => p.map((x, j) => (j === i ? e.target.value : x)))}
                    placeholder={`Document ${i + 1}`}
                    aria-label={`Document joint ${i + 1} a la convocation`}
                  />
                </div>
                <RemoveButton onClick={() => f.setDocumentsJoints((p) => p.filter((_, j) => j !== i))} disabled={f.documentsJoints.length <= 0} />
              </div>
            ))}
          </div>
        </Card>
      )}

      {sections.resolutions && (
        <Card
          title="Résolutions (optionnel)"
          action={<SmallAddButton label="Ajouter une résolution" onClick={() => f.setResolutions((p) => [...p, emptyResolution()])} />}
        >
          <div className="space-y-3">
            {f.resolutions.length === 0 && (
              <p className="text-xs text-fg-subtle">Aucune résolution.</p>
            )}
            {f.resolutions.map((r, i) => (
              <div key={i} className="rounded-lg border border-border bg-bg-overlay p-3">
                <div className="mb-2 flex items-center justify-between">
                  <span className="text-xs font-semibold text-fg-muted">Résolution {i + 1}</span>
                  <RemoveButton onClick={() => f.setResolutions((p) => p.filter((_, j) => j !== i))} />
                </div>
                <div className="grid gap-2 md:grid-cols-2">
                  <TextField label="Intitulé" value={r.intitule} onChange={(e) => updateRes(f, i, { intitule: e.target.value })} />
                  <TextField label="Résultat" value={r.resultat} onChange={(e) => updateRes(f, i, { resultat: e.target.value })} placeholder="adoptée / rejetée" />
                  <div className="md:col-span-2">
                    <TextField label="Texte" value={r.texte} onChange={(e) => updateRes(f, i, { texte: e.target.value })} />
                  </div>
                  <TextField label="Voix pour" type="number" value={r.voixPour} onChange={(e) => updateRes(f, i, { voixPour: e.target.value })} />
                  <TextField label="Voix contre" type="number" value={r.voixContre} onChange={(e) => updateRes(f, i, { voixContre: e.target.value })} />
                  <TextField label="Abstentions" type="number" value={r.abstentions} onChange={(e) => updateRes(f, i, { abstentions: e.target.value })} />
                </div>
              </div>
            ))}
          </div>
        </Card>
      )}

      {sections.presence && (
        <Card
          title={f.isAU ? 'Associé unique et gérance' : 'Associés et présence (quorum)'}
          action={
            f.isAU ? undefined : (
              <SmallAddButton label="Ajouter un associé" onClick={() => f.setAssocies((p) => [...p, emptyAssocie()])} />
            )
          }
        >
          <div className="space-y-3">
            {f.associes.map((a, i) => (
              <div key={i} className="rounded-lg border border-border bg-bg-overlay p-3">
                <div className="mb-2 flex items-center justify-between">
                  <span className="text-xs font-semibold text-fg-muted">
                    {f.isAU ? 'Associé unique' : `Associé ${i + 1}`}
                  </span>
                  {!f.isAU && (
                    <RemoveButton onClick={() => f.setAssocies((p) => p.filter((_, j) => j !== i))} disabled={f.associes.length <= 1} />
                  )}
                </div>
                <AssocieFields
                  value={a}
                  showVoix={!!sections.voix}
                  onChange={(next) => f.setAssocies((p) => p.map((x, j) => (j === i ? next : x)))}
                />
              </div>
            ))}
          </div>

          {/* Gérance (utile pour la signature convocation/feuille + PV AU). */}
          <div className="mt-4 flex items-center justify-between">
            <h5 className="text-xs font-semibold text-fg-muted">Gérance</h5>
            <SmallAddButton label="Ajouter un gérant" onClick={() => f.setGerants((p) => [...p, emptyGerant()])} />
          </div>
          <div className="mt-2 space-y-2">
            {f.gerants.map((g, i) => (
              <div key={i} className="grid items-end gap-2 md:grid-cols-[6rem_1fr_1fr_auto]">
                <TextField label={i === 0 ? 'Civilité' : undefined} value={g.civilite} onChange={(e) => updateGer(f, i, { civilite: e.target.value })} />
                <TextField label={i === 0 ? 'Prénom' : undefined} value={g.prenom} onChange={(e) => updateGer(f, i, { prenom: e.target.value })} />
                <TextField label={i === 0 ? 'Nom' : undefined} value={g.nom} onChange={(e) => updateGer(f, i, { nom: e.target.value })} />
                <RemoveButton onClick={() => f.setGerants((p) => p.filter((_, j) => j !== i))} disabled={f.gerants.length <= 1} />
              </div>
            ))}
          </div>
        </Card>
      )}
    </div>
  );
}

function updateRes(f: SeanceFormApi, i: number, patch: Partial<ResolutionInput>) {
  f.setResolutions((p) => p.map((x, j) => (j === i ? { ...x, ...patch } : x)));
}
function updateGer(f: SeanceFormApi, i: number, patch: Partial<GerantInput>) {
  f.setGerants((p) => p.map((x, j) => (j === i ? { ...x, ...patch } : x)));
}

// --------------------------------------------------------------------------
// Sous-composants
// --------------------------------------------------------------------------
function Card({ title, action, children }: { title: string; action?: React.ReactNode; children: React.ReactNode }) {
  return (
    <div className="rounded-lg border border-border bg-bg-raised p-4">
      <div className="mb-3 flex items-center justify-between">
        <h4 className="text-sm font-semibold text-fg">{title}</h4>
        {action}
      </div>
      {children}
    </div>
  );
}

export function AssocieFields({
  value,
  onChange,
  showVoix,
}: {
  value: AssocieInput;
  onChange: (next: AssocieInput) => void;
  showVoix?: boolean;
}) {
  const isMorale = value.typePersonne === 'MORALE';
  return (
    <div className="grid gap-2 md:grid-cols-3">
      <Select
        label="Type"
        value={value.typePersonne}
        onChange={(e) => onChange({ ...value, typePersonne: e.target.value as 'PHYSIQUE' | 'MORALE' })}
        options={[
          { value: 'PHYSIQUE', label: 'Personne physique' },
          { value: 'MORALE', label: 'Personne morale' },
        ]}
      />
      {isMorale ? (
        <div className="md:col-span-2">
          <TextField label="Dénomination" value={value.denomination} onChange={(e) => onChange({ ...value, denomination: e.target.value })} />
        </div>
      ) : (
        <>
          <TextField label="Prénom" value={value.prenom} onChange={(e) => onChange({ ...value, prenom: e.target.value })} />
          <TextField label="Nom" value={value.nom} onChange={(e) => onChange({ ...value, nom: e.target.value })} />
        </>
      )}
      <div className="md:col-span-3">
        <TextField label="Adresse" value={value.adresse} onChange={(e) => onChange({ ...value, adresse: e.target.value })} />
      </div>
      {/* Fix M6 — n° de CIN : repris de la BD quand il y est, saisissable sinon.
          Il alimente la comparution des PV d'associé unique (« titulaire de la
          CIN n° … »), qui sortait vide faute de champ porteur. */}
      {!isMorale && (
        <TextField
          label="N° de CIN"
          value={value.pieceNumero ?? ''}
          onChange={(e) => onChange({ ...value, pieceNumero: e.target.value })}
          hint="Repris de la fiche société ; complétez s'il manque."
        />
      )}
      <TextField label="Nombre de parts" type="number" value={value.nombreParts} onChange={(e) => onChange({ ...value, nombreParts: e.target.value })} />
      {showVoix && (
        <TextField label="Nombre de voix" type="number" value={value.nombreVoix} onChange={(e) => onChange({ ...value, nombreVoix: e.target.value })} placeholder="= parts" />
      )}
      <Select
        label="Présence"
        value={value.presence}
        onChange={(e) => onChange({ ...value, presence: e.target.value as AssocieInput['presence'] })}
        options={[
          { value: 'présent', label: 'Présent' },
          { value: 'représenté', label: 'Représenté' },
          { value: 'absent', label: 'Absent' },
        ]}
      />
      {value.presence === 'représenté' && (
        <TextField label="Mandataire — Nom" value={value.mandataireNom} onChange={(e) => onChange({ ...value, mandataireNom: e.target.value })} />
      )}
      {/* Désignation #5 : cocher un associé comme gérant depuis la liste des associés. */}
      {value.typePersonne === 'PHYSIQUE' && (
        <label className="flex items-center gap-2 self-end pb-2 text-xs font-medium text-fg">
          <input
            type="checkbox"
            checked={!!value.estGerant}
            onChange={(e) => onChange({ ...value, estGerant: e.target.checked })}
            className="h-4 w-4 rounded border-border-hi text-accent focus:ring-accent"
          />
          Également gérant
        </label>
      )}
    </div>
  );
}

export function SmallAddButton({ label, onClick }: { label: string; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="inline-flex items-center gap-1 rounded-lg border border-border bg-bg-raised px-2.5 py-1 text-xs font-medium text-fg hover:border-accent"
    >
      <Plus className="h-3.5 w-3.5" /> {label}
    </button>
  );
}

export function RemoveButton({ onClick, disabled }: { onClick: () => void; disabled?: boolean }) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className="inline-flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-lg border border-border bg-bg-raised text-fg-subtle transition hover:border-danger hover:text-danger disabled:cursor-not-allowed disabled:opacity-40"
      aria-label="Supprimer"
    >
      <Trash2 className="h-3.5 w-3.5" />
    </button>
  );
}

/** Nettoie un nom de fichier (convention Phase A). */
export function sanitizeFilename(base: string): string {
  return base.replace(/[\\/:*?"<>|]+/g, ' ').replace(/\s+/g, ' ').trim();
}
