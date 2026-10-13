/**
 * Etat du formulaire de seance d'AG (lot L6) : types publics, valeurs vides, hook d'etat et
 * construction du payload. Separes du rendu (SeanceForm.tsx) pour que ce dernier n'exporte
 * que des composants (rafraichissement a chaud de Vite).
 */
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

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
  /** L4d (E11b) : date de cloture de l'exercice, saisie en amont (approbation des comptes). */
  exerciceClos?: string;
}

/**
 * L4d (E11b) : donnees des modeles du corpus pour la convocation (AGO, AGE, seconde
 * convocation). Chaque oui / non pilote une condition du modele : vide, la generation le
 * nomme (jamais suppose).
 */
export interface ConvocationCorpusInput {
  auteurQualite: string;
  /** gerant, commissaire aux comptes ou mandataire de justice (convocation AGO). */
  auteurType: string;
  ordonnanceDate: string;
  pouvoirJoint: string;
  communicationDateEnvoi: string;
  // Seconde convocation : la premiere assemblee.
  modePremiere: string;
  datePremiere: string;
  heureAgPremiere: string;
  lieuAgPremiere: string;
  clauseContraireStatuts: string;
  clauseContraireTexte: string;
  associeInteressePresent: string;
  documentsJointsSeconde: string;
  pouvoirsPremiereValables: string;
}

/** L4d (E11b) : dossier des comptes (assemblee ordinaire annuelle). */
export interface ComptesInput {
  exerciceDebut: string;
  exerciceClos: string;
  cacDesigne: string;
  cacNom: string;
  cacPresence: string;
  conventionsReglementees: string;
  reserveLegale: string;
  reserveLegaleTaux: string;
  reserveLegalePlafond: string;
  dividendes: string;
  reserves: string;
  quitusGerantVote: string;
}

function emptyConvocationCorpus(): ConvocationCorpusInput {
  return {
    auteurQualite: '', auteurType: '', ordonnanceDate: '', pouvoirJoint: '', communicationDateEnvoi: '',
    modePremiere: '', datePremiere: '', heureAgPremiere: '', lieuAgPremiere: '',
    clauseContraireStatuts: '', clauseContraireTexte: '', associeInteressePresent: '',
    documentsJointsSeconde: '', pouvoirsPremiereValables: '',
  };
}

function emptyComptes(): ComptesInput {
  return {
    exerciceDebut: '', exerciceClos: '', cacDesigne: '', cacNom: '', cacPresence: '',
    conventionsReglementees: '', reserveLegale: '', reserveLegaleTaux: '', reserveLegalePlafond: '',
    dividendes: '', reserves: '', quitusGerantVote: '',
  };
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
  /** L3 (D6) : '' tant que l'employe ne l'a pas indiquee (jamais supposee). */
  presence: 'présent' | 'représenté' | 'absent' | '';
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
  /** L4a (E2) : imprimee dans la comparution ("de nationalite ..."), jamais supposee. */
  nationalite?: string;
  /** L'associé exerce aussi la gérance (désignation depuis la liste des associés). */
  estGerant?: boolean;
  /** L4d (E11b) : adresse de la lettre de convocation envoyee par voie electronique. */
  adresseElectronique?: string;
  /** L4d (E11b) : date du pouvoir de l'associe represente (feuille de presence). */
  pouvoirDate?: string;
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
  /** L3 (D6) : quorum et vote des resolutions, saisis (documents de decision : PV). */
  vote?: boolean;
  /** L4d (E11b) : donnees des modeles de convocation du corpus (AGO, AGE, seconde). */
  convocationCorpus?: boolean;
  /** L4d (E11b) : donnees des modeles de feuille de presence du corpus (AGO, AGE). */
  feuilleCorpus?: boolean;
}

/** L3 (D6) : vote des resolutions, saisi par l'employe (jamais suppose). */
export interface VoteInput {
  /** Resultat imprime par les PV "est ... par N voix pour" : adoptee / rejetee. */
  resultat: string;
  /** Majorite imprimee par les PV "est adoptee ..." : a l'unanimite, a la majorite... */
  majorite: string;
  voixPour: string;
  voixContre: string;
  abstentions: string;
}

function emptyVote(): VoteInput {
  return { resultat: '', majorite: '', voixPour: '', voixContre: '', abstentions: '' };
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
    presence: '',
    mandataireNom: '',
    pieceType: '',
    pieceNumero: '',
    estGerant: false,
  };
}

export function emptyResolution(): ResolutionInput {
  return { intitule: '', texte: '', voixPour: '', voixContre: '', abstentions: '', resultat: '' };
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
  /** L4a (E2) : qualite proposee (l'appelant peut l'imposer : organe d'une societe etrangere). */
  presidentQualitePropose: string;
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
  // L3 (D6) : quorum et vote, saisis
  quorumAtteint: '' | 'oui' | 'non'; setQuorumAtteint: (v: '' | 'oui' | 'non') => void;
  vote: VoteInput; setVote: React.Dispatch<React.SetStateAction<VoteInput>>;
  // L4d (E11b) : modeles du corpus (convocation, feuille de presence)
  convCorpus: ConvocationCorpusInput;
  setConvCorpus: (patch: Partial<ConvocationCorpusInput>) => void;
  comptes: ComptesInput;
  setComptes: (patch: Partial<ComptesInput>) => void;
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
  // L4a (E2) : qualite du president, auteur et mode de convocation : valeurs PROPOSEES,
  // affichees et confirmees (avant : pre-remplies, transmises sans confirmation).
  const [presidentQualite, setPresidentQualite] = useState(sv('presidentQualite', ''));
  const [secretairePresent, setSecretairePresent] = useState<'oui' | 'non'>(sv('secretairePresent', 'non'));
  const [secretaireNom, setSecretaireNom] = useState(sv('secretaireNom', ''));

  const [convAuteur, setConvAuteur] = useState(sv('convAuteur', ''));
  const [convDate, setConvDate] = useState(sv('convDate', defaults?.convDate ?? ''));
  const [convHeure, setConvHeure] = useState(sv('convHeure', defaults?.convHeure ?? ''));
  const [convMode, setConvMode] = useState(sv('convMode', ''));
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
  // L6 : un etat (et non plus une reference) : il est lu au rendu, ou se fait la
  // re-synchronisation (voir plus bas).
  const [lastAutoOdj, setLastAutoOdj] = useState<string[] | null>(snapOdjUsable ? null : autoOdj);
  // L4d (E11b) : plus de liste pre-remplie. Les modeles du corpus impriment deja le texte des
  // resolutions, et la formule de pouvoir a sa propre donnee (oui / non) : les reprendre ici
  // les imprimait deux fois.
  const [documentsJoints, setDocumentsJoints] = useState<string[]>(sv('documentsJoints', []));
  const [convCorpus, setConvCorpusState] = useState<ConvocationCorpusInput>(
    { ...emptyConvocationCorpus(), ...sv<Partial<ConvocationCorpusInput>>('convCorpus', {}) },
  );
  const setConvCorpus = useCallback(
    (patch: Partial<ConvocationCorpusInput>) => setConvCorpusState((c) => ({ ...c, ...patch })), [],
  );
  const [comptesSaisis, setComptesState] = useState<ComptesInput>(
    { ...emptyComptes(), ...sv<Partial<ComptesInput>>('comptes', {}) },
  );
  const setComptes = useCallback(
    (patch: Partial<ComptesInput>) => setComptesState((c) => ({ ...c, ...patch })), [],
  );
  // Date de cloture saisie en amont (approbation des comptes) : reprise tant que le champ est vide.
  const comptes = useMemo<ComptesInput>(
    () => ({ ...comptesSaisis, exerciceClos: comptesSaisis.exerciceClos || (defaults?.exerciceClos ?? '') }),
    [comptesSaisis, defaults?.exerciceClos],
  );
  const [resolutions, setResolutions] = useState<ResolutionInput[]>(sv('resolutions', []));
  const [quorumAtteint, setQuorumAtteint] = useState<'' | 'oui' | 'non'>(sv('quorumAtteint', ''));
  const [vote, setVote] = useState<VoteInput>(sv('vote', emptyVote()));
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
   *
   * L6 : ces synchronisations se font AU RENDU, quand la source change (motif « ajuster
   * l'etat quand une prop change » de React), et non plus dans des effets qui
   * declenchaient un rendu en cascade. La source vue en dernier est gardee en etat ;
   * `null` au montage, pour que la premiere source soit appliquee comme avant.
   */
  const siegeSource = (defaults?.lieu ?? societe.siegeSocial ?? '').toString().trim();
  const [siegeVu, setSiegeVu] = useState<string | null>(null);
  if (siegeVu !== siegeSource) {
    setSiegeVu(siegeSource);
    if (siegeSource && !lieu.trim()) setLieu(siegeSource);
  }

  const villeSource = (societe.villeGreffe ?? '').toString().trim();
  const [villeVue, setVilleVue] = useState<string | null>(null);
  if (villeVue !== villeSource) {
    setVilleVue(villeSource);
    if (villeSource && !lieuSignature.trim()) setLieuSignature(villeSource);
  }

  // Président de séance : par défaut le premier gérant. Il n'était alimenté par
  // rien, alors que la gérance est chargée depuis le dossier juste à côté.
  const premierGerant = gerants.find((x) => x?.nom?.trim() || x?.prenom?.trim());
  const presidentSource = premierGerant
    ? [premierGerant.civilite, premierGerant.prenom, premierGerant.nom].filter(Boolean).join(' ').trim()
    : '';
  const [presidentVu, setPresidentVu] = useState<string | null>(null);
  if (presidentVu !== presidentSource) {
    setPresidentVu(presidentSource);
    if (presidentSource && !presidentNom.trim()) setPresidentNom(presidentSource);
  }

  // Date d'assemblée + date de convocation pilotées en amont (bloc PV) : on les
  // reprend quand elles changent (les champs sont masqués via hideSeanceDate/hideConvDate).
  const [dateVue, setDateVue] = useState<{ valeur: string | undefined } | null>(null);
  if (dateVue === null || dateVue.valeur !== defaults?.date) {
    setDateVue({ valeur: defaults?.date });
    if (defaults?.date && defaults.date !== date) setDate(defaults.date);
  }
  const [convDateVue, setConvDateVue] = useState<{ valeur: string | undefined } | null>(null);
  if (convDateVue === null || convDateVue.valeur !== defaults?.convDate) {
    setConvDateVue({ valeur: defaults?.convDate });
    if (defaults?.convDate !== undefined && (defaults.convDate ?? '') !== convDate) {
      setConvDate(defaults.convDate ?? '');
    }
  }

  // Ordre du jour = libellés des modifications choisies (re-sync si encore vierge).
  // Ordre du jour DYNAMIQUE : suit la sélection des modifications (ajout/retrait/
  // changement de type) tant que l'utilisateur n'a pas édité la liste à la main.
  // Sans le suivi `lastAutoOdj`, la liste restait figée sur la 1re modification
  // (elle n'était plus « vierge », donc jamais re-synchronisée). La source est comparee
  // par son CONTENU : une page qui la recalcule a chaque rendu ne relance rien.
  const [odjVu, setOdjVu] = useState<{ liste: string[] | undefined; lock: boolean | undefined } | null>(null);
  if (odjVu === null || odjVu.lock !== lockOrdreDuJour
      || !sameList(odjVu.liste ?? null, initialOrdreDuJour ?? null)) {
    setOdjVu({ liste: initialOrdreDuJour, lock: lockOrdreDuJour });
    if (initialOrdreDuJour && lockOrdreDuJour) {
      // Verrouillé (ordre du jour dérivé) : synchronisation INCONDITIONNELLE — la seule
      // commande est la sélection des modifications, aucune saisie manuelle à préserver.
      if (!sameList(ordreDuJour, initialOrdreDuJour)) setOrdreDuJour(initialOrdreDuJour);
      setLastAutoOdj(initialOrdreDuJour);
    } else if (initialOrdreDuJour && initialOrdreDuJour.length > 0) {
      const vierge = ordreDuJour.every((x) => !x?.trim());
      const inchangeDepuisAuto = lastAutoOdj !== null && sameList(ordreDuJour, lastAutoOdj);
      if (vierge || inchangeDepuisAuto) {
        setLastAutoOdj(initialOrdreDuJour);
        setOrdreDuJour(initialOrdreDuJour);
      }
      // sinon : édition manuelle, on respecte la saisie de l'utilisateur
    }
  }

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
        // L4d (E11b) : modeles du corpus. Vides, la generation les nomme (jamais supposes).
        ...convCorpus,
        clauseContraireTexte: convCorpus.clauseContraireStatuts === 'oui' ? convCorpus.clauseContraireTexte : '',
      },
      exercice: { dateDebut: comptes.exerciceDebut, dateCloture: comptes.exerciceClos },
      comptes: {
        cacDesigne: comptes.cacDesigne,
        cacNom: comptes.cacDesigne === 'oui' ? comptes.cacNom : '',
        cacPresence: comptes.cacDesigne === 'oui' ? comptes.cacPresence : '',
        conventionsReglementees: comptes.conventionsReglementees,
        reserveLegale: comptes.reserveLegale,
        reserveLegaleTaux: comptes.reserveLegale === 'oui' ? comptes.reserveLegaleTaux : '',
        reserveLegalePlafond: comptes.reserveLegale === 'oui' ? comptes.reserveLegalePlafond : '',
        dividendes: comptes.dividendes,
        reserves: comptes.reserves,
        quitusGerantVote: comptes.quitusGerantVote,
      },
      secondeAssemblee: { date: secondeDate, heure: secondeHeure, lieu: secondeLieu },
      suiteAssemblee,
      ordreDuJour: ordreDuJour.map((p) => p.trim()).filter(Boolean),
      documentsJoints: documentsJoints.map((d) => d.trim()).filter(Boolean),
      resolutions: resolutions.map((r) => ({ ...r })),
      // Gérants = liste manuelle + associés cochés « gérant » (désignation #5), dédupliqués.
      gerants: mergeGerants(gerants, associes),
      associes: associes.map(serializeAssocie),
      // L3 (D6) : quorum et vote saisis ; absents, la generation les nomme.
      quorumAtteint,
      vote: { ...vote },
    };
  }, [
    quorumAtteint, vote, convCorpus, comptes,
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
    quorumAtteint, vote, convCorpus, comptes,
  }), [
    quorumAtteint, vote, convCorpus, comptes,
    type, date, heure, lieu, heureCloture, presidentNom, presidentQualite,
    secretairePresent, secretaireNom, convAuteur, convDate, convHeure, convMode, convRang,
    dateAgPremiere, lieuSignature, irregulariteNature, secondeDate, secondeHeure,
    secondeLieu, suiteAssemblee, ordreDuJour, documentsJoints, resolutions, associes, gerants,
  ]);

  return {
    isAU, societe,
    type, setType, date, setDate, heure, setHeure, lieu, setLieu, heureCloture, setHeureCloture,
    presidentNom, setPresidentNom, presidentQualite, setPresidentQualite,
    presidentQualitePropose: defaults?.presidentQualite ?? 'gérant',
    secretairePresent, setSecretairePresent, secretaireNom, setSecretaireNom,
    convAuteur, setConvAuteur, convDate, setConvDate, convHeure, setConvHeure,
    convMode, setConvMode,
    convRang, setConvRang, dateAgPremiere, setDateAgPremiere, lieuSignature, setLieuSignature,
    irregulariteNature, setIrregulariteNature,
    secondeDate, setSecondeDate, secondeHeure, setSecondeHeure, secondeLieu, setSecondeLieu,
    suiteAssemblee, setSuiteAssemblee,
    ordreDuJour, setOrdreDuJour, documentsJoints, setDocumentsJoints,
    resolutions, setResolutions, associes, setAssocies, gerants, setGerants,
    quorumAtteint, setQuorumAtteint, vote, setVote,
    convCorpus, setConvCorpus, comptes, setComptes,
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
    // Fix M6 -- alimente $ASSOCIE_PIECE_TYPE / $ASSOCIE_PIECE_NUMERO (comparution AU).
    // L3 (D6) : plus de "CIN" par defaut ; la valeur proposee est confirmee a l'ecran.
    pieceType: a.pieceType?.trim() ?? '',
    pieceNumero: a.pieceNumero ?? '',
    nationalite: a.nationalite?.trim() ?? '',
    // L4d (E11b) : lettre envoyee par voie electronique ; pouvoir de l'associe represente.
    adresseElectronique: a.adresseElectronique?.trim() ?? '',
    pouvoirDate: a.presence === 'représenté' ? a.pouvoirDate ?? '' : '',
  };
}

/** Nettoie un nom de fichier (convention Phase A). */
export function sanitizeFilename(base: string): string {
  return base.replace(/[\\/:*?"<>|]+/g, ' ').replace(/\s+/g, ' ').trim();
}
