import { useEffect, useMemo, useState } from 'react';
import {
  AlertTriangle,
  Building2,
  CheckCircle,
  ChevronRight,
  FileText,
  Trash2,
  Upload,
  User,
  UserPlus,
  Users,
  Link2,
} from 'lucide-react';
import { BlocageValidation } from '../../../components/workflow/BlocageValidation';
import { IdentityExtractor } from '../../../components/identity/IdentityExtractor';
import { toIsoDate } from '../../../types/identity';
import { useStepAutosave } from '../useStepAutosave';
import {
  computeDistributedAmounts,
  distributeEvenly,
} from './capital-distribution';

type TypePersonneAssocie = 'PHYSIQUE' | 'MORALE';

interface Props {
  existing?: Record<string, unknown>;
  formeJuridique: 'SARL' | 'SARL_AU';
  /**
   * Nombre total de parts attendu — provient de Step3 (data.step3.nombreParts).
   * Si fourni, on enforce sumParts === totalPartsExpected pour SARL (RG-C09).
   * Optionnel : laisser undefined pour ne pas bloquer si la valeur n'est pas
   * encore connue.
   */
  totalPartsExpected?: number;
  /**
   * EX3 2026-06-09 — Capital social total attendu (depuis Step3). Sert au
   * controle Sigma(apports) == capitalSocial et a l'affichage du restant a
   * repartir dans la section "Parts sociales & apport".
   */
  capitalSocialExpected?: number;
  /**
   * 2026-06-11 — Valeur nominale d'une part (depuis Step3). Sert au calcul
   * automatique du montant d'apport par associe : montant = nombreParts * valeurNominale.
   */
  valeurNominaleExpected?: number;
  /**
   * 2026-06-22 — Apports cibles VENTILÉS par type (depuis Step3 : numéraire /
   * nature / industrie). Affichés dans le bloc « Cible depuis l'étape 3 » pour
   * que l'employé sache combien distribuer par type, au lieu d'un total agrégé.
   */
  apportNumeraireExpected?: number;
  apportNatureExpected?: number;
  apportIndustrieExpected?: number;
  /**
   * RG-C15 transverse 2026-06-05 : dirigeants marques `isAssociate=true` a
   * l'etape 5. Sont AUTOMATIQUEMENT propages comme associes (lecture seule
   * pour l'identite + CIN reprise du dirigeant ; le user complete les parts).
   * Si la liste change (user revient en arriere et coche/decoche), on synchronise.
   *
   * EX5 2026-06-09 — Supporte aussi typePersonne MORALE (RC/ICE/IF + representant legal).
   */
  propagatedFromDirigeants?: Array<{
    id: string;
    typePersonne?: TypePersonneAssocie;
    civilite: 'M' | 'Mme';
    nom: string;
    prenom: string;
    cinNumero: string;
    adresse: string;
    cinUploaded: boolean;
    cinFileName?: string;
    /** 2026-06-11 — Etat civil PHYSIQUE propage. */
    nationalite?: string;
    dateNaissance?: string;
    lieuNaissance?: string;
    pieceValidite?: string;
    denomination?: string;
    formeJuridiqueEntite?: string;
    rc?: string;
    ice?: string;
    ifFiscal?: string;
    siege?: string;
    repCivilite?: 'M' | 'Mme';
    repNom?: string;
    repPrenom?: string;
    repCin?: string;
    repAdresse?: string;
    repDateNaissance?: string;
    repLieuNaissance?: string;
    repNationalite?: string;
    repPieceValidite?: string;
    repQualite?: string;
    rcUploaded?: boolean;
    rcFileName?: string;
    statutsEntiteUploaded?: boolean;
    statutsEntiteFileName?: string;
    /** F2 2026-06-09 — CIN representant legal propage Step5 -> Step6. */
    repCinUploaded?: boolean;
    repCinFileName?: string;
  }>;
  /**
   * 2026-06-25 (refonte IMPORT) — Societe existante : les pieces (CIN, RC,
   * statuts de l'entite) sont deposees a l'etape d'upload juridique. On n'exige
   * donc PAS les uploads inline ; seules l'identite + la repartition des parts
   * restent obligatoires. Defaut {@code false} : la CREATION reste inchangee.
   */
  importMode?: boolean;
  saving: boolean;
  onSubmit: (payload: Record<string, unknown>) => Promise<void>;
  /** P1 2026-06-21 — Cowork : draft callback pour flush au navigation. */
  onSave?: (payload: Record<string, unknown>) => Promise<void>;
  /** P1 2026-06-21 — Cowork : enregistre un getter "dirty" pour useWorkflow.flushDraft(). */
  registerDirty?: (
    step: number,
    getter: () => Record<string, unknown> | null,
  ) => () => void;
  /** RG transverse 2026-06-05 : registre pieces persistantes (cross-step). */
  onPieceUploaded?: (
    code: string,
    label: string,
    file: File,
  ) => Promise<void> | void;
  /**
   * 2026-06-15 — dossierId courant (du ticket). Utilise par l'assistant
   * d'extraction d'identite pour archiver le PDF recto+verso fusionne dans
   * la Data Room. Optionnel : sans dossierId, l'extraction se fait sans
   * archivage automatique.
   */
  dossierId?: string | null;
}

type TypeApport = 'NUMERAIRE' | 'NATURE' | 'INDUSTRIE';

/**
 * 2026-06-21 (BLOC A) — Ligne d'apport individuelle. Un associe peut combiner
 * plusieurs lignes (ex. 100 parts en nature + 400 en numeraire). Le total des
 * parts derive `nombreParts` (source unique pour totalParts/pct/validation).
 */
interface Apport {
  id: string;
  type: TypeApport;
  parts: number;
}

interface Associe {
  id: string;
  /**
   * Si renseigne, l'associe a ete propage depuis Step5 (dirigeant marque
   * isAssociate). Le user ne peut PAS modifier l'identite ni le CIN (source
   * de verite = Step5) ; il complete uniquement parts/apport/type.
   */
  fromDirigeantId?: string;
  // EX5 2026-06-09 — Type d'associe : physique ou morale (entite).
  typePersonne: TypePersonneAssocie;
  // PHYSIQUE
  civilite: 'M' | 'Mme';
  nom: string;
  prenom: string;
  cin: string;
  adresse: string;
  cinUploaded: boolean;
  cinFileName?: string;
  /** 2026-06-11 — Etat civil complet, propage depuis Step5 si fromDirigeantId.
   *  Necessaires aux Statuts (placeholders `{{associe_pp_*}}`) ; optionnels en
   *  validation -- sans eux les Statuts rendent "né(e) le ___ à ___". */
  nationalite?: string;
  dateNaissance?: string;
  lieuNaissance?: string;
  pieceValidite?: string;
  // MORALE
  denomination?: string;
  formeJuridiqueEntite?: string;
  rc?: string;
  ice?: string;
  ifFiscal?: string;
  siege?: string;
  /** Capital de l'entite associee (MAD) — pour `{{associe_pm_capital}}`. */
  capitalEntite?: number;
  /** Date de la deliberation autorisant la prise de participation — `{{associe_pm_deliberation_date}}`. */
  deliberationDate?: string;
  /** 2026-06-11 — Set complet pour les Statuts (idem dirigeant MORALE). */
  repCivilite?: 'M' | 'Mme';
  repNom?: string;
  repPrenom?: string;
  repCin?: string;
  repAdresse?: string;
  repDateNaissance?: string;
  repLieuNaissance?: string;
  repNationalite?: string;
  repPieceValidite?: string;
  repQualite?: string;
  rcUploaded?: boolean;
  rcFileName?: string;
  statutsEntiteUploaded?: boolean;
  statutsEntiteFileName?: string;
  /** F2 2026-06-09 — Upload obligatoire de la CIN du representant legal (MORALE). */
  repCinUploaded?: boolean;
  repCinFileName?: string;
  // commun
  /** 2026-08 (contrat CREATION) — genre grammatical (masculin / féminin). */
  genre?: string;
  /** 2026-08 (contrat CREATION) — l'associe est aussi gerant de la societe. */
  estGerant?: boolean;
  nombreParts: number;
  montantApport: number;
  typeApport: TypeApport;
  /**
   * 2026-06-21 (BLOC A) — Lignes d'apport (eventuellement plusieurs types
   * pour un meme associe). `nombreParts` est la somme des `parts` de cette
   * liste ; `typeApport` est le type dominant (compat retro).
   */
  apports: Apport[];
  extracting: boolean;
  /** 2026-06-19 — Associe represente par un mandataire (procuration speciale). */
  representeParMandataire?: boolean;
  mandataire?: string;
  procurationDate?: string;
}

function newAssocie(): Associe {
  return {
    id: crypto.randomUUID(),
    typePersonne: 'PHYSIQUE',
    civilite: 'M',
    nom: '',
    prenom: '',
    cin: '',
    adresse: '',
    nationalite: 'Marocaine',
    dateNaissance: '',
    lieuNaissance: '',
    pieceValidite: '',
    genre: 'masculin',
    estGerant: false,
    nombreParts: 0,
    montantApport: 0,
    typeApport: 'NUMERAIRE',
    apports: [{ id: crypto.randomUUID(), type: 'NUMERAIRE', parts: 0 }],
    cinUploaded: false,
    extracting: false,
    // MORALE defaults
    denomination: '',
    formeJuridiqueEntite: 'SARL',
    rc: '',
    ice: '',
    ifFiscal: '',
    siege: '',
    capitalEntite: 0,
    deliberationDate: '',
    repCivilite: 'M',
    repNom: '',
    repPrenom: '',
    repCin: '',
    repAdresse: '',
    repDateNaissance: '',
    repLieuNaissance: '',
    repNationalite: 'Marocaine',
    repPieceValidite: '',
    repQualite: 'Gerant',
    rcUploaded: false,
    statutsEntiteUploaded: false,
    repCinUploaded: false,
    representeParMandataire: false,
    mandataire: '',
    procurationDate: '',
  };
}

/**
 * 2026-06-21 (BLOC A) — Retro-compat : si un brouillon existant n'a pas
 * `apports[]`, le derive depuis l'ancien shape {typeApport, nombreParts}.
 * Conserve aussi un fallback si apports[] est vide.
 */
function hydrateApports(a: Partial<Associe>): Apport[] {
  const raw = a.apports;
  // 2026-06-22 — parts forcées en ENTIER naturel (anciennes données / divisions).
  if (Array.isArray(raw) && raw.length > 0) {
    return raw.map((x) => ({
      id: x?.id ?? crypto.randomUUID(),
      type: (x?.type as TypeApport) ?? 'NUMERAIRE',
      parts: Math.max(0, Math.round(Number(x?.parts) || 0)),
    }));
  }
  return [
    {
      id: crypto.randomUUID(),
      type: (a.typeApport as TypeApport) ?? 'NUMERAIRE',
      parts: Math.max(0, Math.round(Number(a.nombreParts) || 0)),
    },
  ];
}

/**
 * 2026-06-21 (BLOC A) — Derive (nombreParts, montantApport, typeApport
 * dominant) depuis la liste des apports. `montantApport` global = nombreParts
 * x valeurNominale (le mapper backend ventile par TYPE via apports[]).
 */
function deriveFromApports(
  apports: Apport[],
  valeurNominaleExpected: number | undefined,
): { nombreParts: number; montantApport: number; typeApport: TypeApport } {
  const nombreParts = apports.reduce((s, x) => s + (Number(x.parts) || 0), 0);
  const montantApport = valeurNominaleExpected
    ? Math.round(nombreParts * valeurNominaleExpected * 100) / 100
    : 0;
  // Type dominant = celui avec le plus de parts (priorite NUMERAIRE en cas d'ex aequo).
  const byType: Record<TypeApport, number> = { NUMERAIRE: 0, NATURE: 0, INDUSTRIE: 0 };
  for (const x of apports) byType[x.type] += Number(x.parts) || 0;
  const ordered: TypeApport[] = ['NUMERAIRE', 'NATURE', 'INDUSTRIE'];
  let dominant: TypeApport = 'NUMERAIRE';
  let best = -1;
  for (const t of ordered) {
    if (byType[t] > best) { best = byType[t]; dominant = t; }
  }
  return { nombreParts, montantApport, typeApport: dominant };
}

function cinInvalidMessage(value: string): string | null {
  if (!value) return null;
  const v = value.replace(/\s+/g, '').toUpperCase();
  if (!/^[A-Z]{1,2}[0-9]{5,7}$/.test(v)) {
    return 'Format CIN invalide (ex : X 123456)';
  }
  return null;
}

function iceInvalidMessage(value: string): string | null {
  if (!value) return null;
  const v = value.replace(/[\s-]/g, '');
  if (!/^\d{15}$/.test(v)) return 'ICE invalide (15 chiffres)';
  return null;
}

function rcInvalidMessage(value: string): string | null {
  if (!value) return null;
  const v = value.replace(/\s+/g, '');
  if (!/^\d{1,8}$/.test(v)) return 'RC invalide (chiffres uniquement)';
  return null;
}

function ifInvalidMessage(value: string): string | null {
  if (!value) return null;
  const v = value.replace(/\s+/g, '');
  if (!/^\d{7,8}$/.test(v)) return 'IF invalide (7 a 8 chiffres)';
  return null;
}

// 2026-06-15 — pendingFiles supprime : l'extraction passe entierement par
// IdentityExtractor + dataroom-service (archivage Donut KIE).

export function Step6Associes({
  existing,
  formeJuridique,
  totalPartsExpected,
  capitalSocialExpected,
  valeurNominaleExpected,
  apportNumeraireExpected,
  apportNatureExpected,
  apportIndustrieExpected,
  propagatedFromDirigeants,
  importMode = false,
  saving,
  onSubmit,
  registerDirty,
  onPieceUploaded,
  dossierId,
}: Props) {
  const isUnique = formeJuridique === 'SARL_AU';
  const fromData = ((existing?.associes as Associe[]) ?? []).map((a) => {
    // 2026-06-21 (BLOC A) — Retro-compat : reconstruit apports[] depuis
    // l'ancien shape {typeApport, nombreParts}. Aligne aussi nombreParts +
    // montantApport sur la somme effective des apports apres hydratation.
    const apports = hydrateApports(a);
    const derived = deriveFromApports(apports, valeurNominaleExpected);
    return {
      ...newAssocie(),
      ...a,
      id: a.id ?? crypto.randomUUID(),
      apports,
      nombreParts: derived.nombreParts,
      montantApport: derived.montantApport,
      typeApport: derived.typeApport,
    };
  });
  const [associes, setAssocies] = useState<Associe[]>(
    fromData.length > 0 ? fromData : [newAssocie()],
  );

  /**
   * Propagation Step5 -> Step6 : reconcilie la liste des associes avec les
   * dirigeants marques `isAssociate=true`. Strategie :
   *  - Pour chaque dirigeant propage, si un associe existe deja avec
   *    `fromDirigeantId === dirigeant.id`, on met a jour les champs identite
   *    (sans toucher aux parts/apport).
   *  - Sinon on l'ajoute en tete de liste.
   *  - Les associes saisis manuellement (sans fromDirigeantId) restent
   *    inchanges.
   *  - Les associes propages dont le dirigeant n'est plus marque sont
   *    retires (l'utilisateur les recree manuellement s'il le souhaite).
   */
  useEffect(() => {
    if (!propagatedFromDirigeants) return;
    setAssocies((prev) => {
      const propagatedIds = new Set(propagatedFromDirigeants.map((d) => d.id));
      // 1) Filtre les propages obsoletes
      const keptManuals = prev.filter(
        (a) => !a.fromDirigeantId || propagatedIds.has(a.fromDirigeantId),
      );
      // 2) Maj ou ajout des propages
      const result: Associe[] = [];
      for (const d of propagatedFromDirigeants) {
        const existing = keptManuals.find((a) => a.fromDirigeantId === d.id);
        // EX5 — propage tous les champs PHYSIQUE/MORALE depuis Step5.
        const baseFromDir = {
          typePersonne: (d.typePersonne ?? 'PHYSIQUE') as TypePersonneAssocie,
          civilite: d.civilite,
          nom: d.nom,
          prenom: d.prenom,
          cin: d.cinNumero,
          adresse: d.adresse,
          // 2026-08 (contrat CREATION) — un dirigeant marque « associe » EST gerant.
          estGerant: true,
          genre:
            (d.typePersonne ?? 'PHYSIQUE') === 'MORALE'
              ? 'féminin'
              : d.civilite === 'Mme'
                ? 'féminin'
                : 'masculin',
          cinUploaded: d.cinUploaded,
          cinFileName: d.cinFileName,
          // 2026-06-11 — Etat civil PHYSIQUE propage Step5 -> Step6.
          nationalite: d.nationalite,
          dateNaissance: d.dateNaissance,
          lieuNaissance: d.lieuNaissance,
          pieceValidite: d.pieceValidite,
          denomination: d.denomination,
          formeJuridiqueEntite: d.formeJuridiqueEntite,
          rc: d.rc,
          ice: d.ice,
          ifFiscal: d.ifFiscal,
          siege: d.siege,
          repCivilite: d.repCivilite,
          repNom: d.repNom,
          repPrenom: d.repPrenom,
          repCin: d.repCin,
          repAdresse: d.repAdresse,
          repDateNaissance: d.repDateNaissance,
          repLieuNaissance: d.repLieuNaissance,
          repNationalite: d.repNationalite,
          repPieceValidite: d.repPieceValidite,
          repQualite: d.repQualite,
          rcUploaded: !!d.rcUploaded,
          rcFileName: d.rcFileName,
          statutsEntiteUploaded: !!d.statutsEntiteUploaded,
          statutsEntiteFileName: d.statutsEntiteFileName,
          repCinUploaded: !!d.repCinUploaded,
          repCinFileName: d.repCinFileName,
        };
        if (existing) {
          result.push({ ...existing, ...baseFromDir });
        } else {
          result.push({
            ...newAssocie(),
            fromDirigeantId: d.id,
            ...baseFromDir,
          });
        }
      }
      // 3) Ajoute les associes manuels restants apres les propages
      for (const a of keptManuals) {
        if (!a.fromDirigeantId) result.push(a);
      }
      // Garde une coherence SARL_AU : si propages > 0, garde uniquement le 1er
      if (isUnique && result.length > 1) {
        return [result[0]];
      }
      return result.length > 0 ? result : prev;
    });
  }, [propagatedFromDirigeants, isUnique]);

  function update(id: string, patch: Partial<Associe>) {
    setAssocies((arr) =>
      arr.map((a) => (a.id === id ? { ...a, ...patch } : a)),
    );
  }

  const totalParts = useMemo(
    () => associes.reduce((s, a) => s + (Number(a.nombreParts) || 0), 0),
    [associes],
  );

  /**
   * 2026-06-19 — Apports affichés par associé : on délègue à
   * {@link computeDistributedAmounts}. Quand Σ(parts) = nbPartsTotal cible
   * ET valeurNominale + capital connus, les N-1 premiers reçoivent leur
   * arrondi standard et le DERNIER absorbe le reliquat — la somme tombe
   * alors exactement sur le capital, même si la valeur nominale n'est pas
   * entière (cas 103333 MAD / 1033 parts).
   *
   * Sinon (parts encore non équilibrées, ou champs Step3 manquants), on
   * retombe sur l'arrondi standard par associé.
   */
  const distributedApports = useMemo(
    () =>
      computeDistributedAmounts(
        associes.map((a) => Number(a.nombreParts) || 0),
        valeurNominaleExpected,
        capitalSocialExpected,
        totalPartsExpected,
      ),
    [
      associes,
      valeurNominaleExpected,
      capitalSocialExpected,
      totalPartsExpected,
    ],
  );

  const totalApport = useMemo(
    () => distributedApports.reduce((s, x) => s + (Number(x) || 0), 0),
    [distributedApports],
  );

  // 2026-06-22 — Distribution PAR TYPE d'apport (numéraire/nature/industrie) :
  // somme, sur tous les associés, des lignes d'apport de chaque type
  // (parts × valeur nominale). Permet de contrôler chaque type vs sa cible Step3
  // au lieu d'un total agrégé (un total global masquait une mauvaise ventilation).
  const distByType = useMemo(() => {
    const vn = Number(valeurNominaleExpected ?? 0);
    const acc: Record<TypeApport, number> = { NUMERAIRE: 0, NATURE: 0, INDUSTRIE: 0 };
    for (const a of associes) {
      for (const line of a.apports ?? []) {
        const t = (line.type ?? 'NUMERAIRE') as TypeApport;
        acc[t] = (acc[t] ?? 0) + Math.round((Number(line.parts) || 0) * vn * 100) / 100;
      }
    }
    return acc;
  }, [associes, valeurNominaleExpected]);

  // 2026-06-22 — Parts distribuées PAR TYPE (en plus du montant) pour l'affichage.
  const distPartsByType = useMemo(() => {
    const acc: Record<TypeApport, number> = { NUMERAIRE: 0, NATURE: 0, INDUSTRIE: 0 };
    for (const a of associes) {
      for (const line of a.apports ?? []) {
        const t = (line.type ?? 'NUMERAIRE') as TypeApport;
        acc[t] = (acc[t] ?? 0) + (Number(line.parts) || 0);
      }
    }
    return acc;
  }, [associes]);

  // Parts cibles par type = montant cible / valeur nominale (depuis Step3).
  const targetPartsByType = useMemo(() => {
    const vn = Number(valeurNominaleExpected ?? 0);
    const conv = (m?: number) => (vn > 0 ? Math.round((m ?? 0) / vn) : 0);
    return {
      NUMERAIRE: conv(apportNumeraireExpected),
      NATURE: conv(apportNatureExpected),
      INDUSTRIE: conv(apportIndustrieExpected),
    } as Record<TypeApport, number>;
  }, [valeurNominaleExpected, apportNumeraireExpected, apportNatureExpected, apportIndustrieExpected]);

  /**
   * 2026-06-19 — Bouton "Répartir équitablement" : assigne parts + montants
   * en allouant le reliquat au dernier associé (consigne Cowork). N'écrase
   * que si nbPartsTotal + capital + valeurNominale sont définis (Step3).
   */
  const canAutoDistribute =
    !isUnique &&
    typeof totalPartsExpected === 'number' && totalPartsExpected > 0 &&
    typeof capitalSocialExpected === 'number' && capitalSocialExpected > 0 &&
    typeof valeurNominaleExpected === 'number' && valeurNominaleExpected > 0 &&
    associes.length >= 1;

  // 2026-06-19 (Cowork) — Set des associés en cours de "Remplacer la CIN"
  // (force le re-affichage de l'IdentityExtractor meme si archive existante).
  const [replacingCinIds, setReplacingCinIds] = useState<Set<string>>(new Set());

  // P1 2026-06-21 — Getter "dirty" pour flush au navigation. Miroite le payload
  // de onSubmit (associes avec mandataire/procuration conditionnels + montants
  // distribues + clauses globales) pour persister les saisies meme sans clic
  // "Valider".
  useStepAutosave(
    6,
    () => ({
      associes: associes.map((a, i) => ({
        ...a,
        montantApport: distributedApports[i] ?? a.montantApport,
        pourcentageDetention: pct(a),
        mandataire: a.representeParMandataire ? (a.mandataire ?? '') : '',
        procurationDate: a.representeParMandataire ? (a.procurationDate ?? '') : '',
      })),
      formeJuridique,
      totalParts,
      totalApport,
    }),
    registerDirty,
  );

  function autoDistribute() {
    if (!canAutoDistribute) return;
    const { shares, amounts } = distributeEvenly(
      associes.length,
      totalPartsExpected ?? 0,
      capitalSocialExpected ?? 0,
      valeurNominaleExpected,
    );
    setAssocies((arr) =>
      arr.map((a, i) => ({
        ...a,
        nombreParts: shares[i] ?? a.nombreParts,
        montantApport: amounts[i] ?? a.montantApport,
      })),
    );
  }

  const pct = (a: Associe) =>
    totalParts > 0 ? (a.nombreParts / totalParts) * 100 : 0;

  const expectsTotal =
    !isUnique && typeof totalPartsExpected === 'number' && totalPartsExpected > 0;
  const sumPartsValid = !expectsTotal || totalParts === totalPartsExpected;
  // EX3 — controle Sigma(apports) == capitalSocial (rouge si ecart) + restant.
  const expectsCapital =
    typeof capitalSocialExpected === 'number' && capitalSocialExpected > 0;
  // 2026-06-22 — Quand Step3 ventile les apports par type, on EXIGE que chaque
  // type distribué corresponde à sa cible (tolérance 0,5 MAD pour l'arrondi).
  // Sinon, mettre 120k en numéraire « passait » alors que la cible était
  // 100k numéraire + 10k nature + 10k industrie.
  const expectsByType =
    (apportNumeraireExpected ?? 0) > 0 ||
    (apportNatureExpected ?? 0) > 0 ||
    (apportIndustrieExpected ?? 0) > 0;
  const byTypeValid =
    !expectsByType ||
    (Math.abs(distByType.NUMERAIRE - (apportNumeraireExpected ?? 0)) < 0.5 &&
      Math.abs(distByType.NATURE - (apportNatureExpected ?? 0)) < 0.5 &&
      Math.abs(distByType.INDUSTRIE - (apportIndustrieExpected ?? 0)) < 0.5);
  const apportValid =
    (!expectsCapital || totalApport === capitalSocialExpected) && byTypeValid;
  const partsRemaining = expectsTotal ? (totalPartsExpected ?? 0) - totalParts : 0;
  const apportRemaining = expectsCapital ? (capitalSocialExpected ?? 0) - totalApport : 0;
  // EX5 — validation conditionnelle par typePersonne.
  function isAssocieValid(a: Associe): boolean {
    if (a.nombreParts <= 0) return false;
    if (a.typePersonne === 'MORALE') {
      return Boolean(
        a.denomination &&
          a.rc && !rcInvalidMessage(a.rc) &&
          a.ice && !iceInvalidMessage(a.ice) &&
          a.ifFiscal && !ifInvalidMessage(a.ifFiscal) &&
          a.siege &&
          a.repNom && a.repPrenom && a.repCin &&
          !cinInvalidMessage(a.repCin) &&
          a.repAdresse &&
          a.repQualite &&
          // 2026-06-25 — En IMPORT, les pieces sont deposees a l'etape d'upload
          // juridique : on n'exige pas les uploads inline.
          (importMode || (a.rcUploaded &&
          a.statutsEntiteUploaded &&
          a.repCinUploaded)), // F2 2026-06-09 — CIN du representant legal obligatoire
      );
    }
    return Boolean(
      a.nom && a.prenom && a.cin && a.adresse && (importMode || a.cinUploaded) && !cinInvalidMessage(a.cin)
        // Lot 2 (2026-09-07) — DATE ET LIEU DE NAISSANCE OBLIGATOIRES.
        // L'etape laissait valider sans, et les statuts sortaient avec
        // « M. KARIM TAZI, de nationalite Marocaine, ne le a, demeurant a… » :
        // la comparution des associes imprime $ASSOCIE_DATE_NAISSANCE et
        // $ASSOCIE_LIEU_NAISSANCE. Une date de naissance manquante dans des
        // statuts est une lacune de fond, pas de forme : on bloque la saisie
        // plutot que d'omettre la mention.
        && a.dateNaissance && a.lieuNaissance,
    );
  }

  // Règle de forme : SARL = 2 associés minimum (1 seul = SARL à associé unique) ;
  // SARL AU = exactement 1 associé.
  const nombreAssociesValide = isUnique
    ? associes.length === 1
    : associes.length >= 2;
  const canSubmit =
    nombreAssociesValide &&
    associes.every(isAssocieValid) &&
    sumPartsValid &&
    apportValid;

  /**
   * Ce qui manque, associe par associe. Sans cette liste, « Valider et
   * continuer » ne faisait RIEN et ne disait rien : l'employe cliquait dans le
   * vide sans savoir quel champ lui etait reproche.
   */
  const raisonsBlocage: string[] = (() => {
    if (canSubmit) return [];
    const out: string[] = [];
    if (!nombreAssociesValide) {
      out.push(
        isUnique
          ? 'Une SARL AU compte exactement un associé.'
          : 'Une SARL requiert au moins deux associés.',
      );
    }
    associes.forEach((a, i) => {
      if (isAssocieValid(a)) return;
      const qui = [a.prenom, a.nom].filter(Boolean).join(' ').trim()
        || a.denomination
        || `Associé ${i + 1}`;
      const manques: string[] = [];
      if (a.nombreParts <= 0) manques.push('nombre de parts');
      if (a.typePersonne === 'MORALE') {
        if (!a.denomination) manques.push('dénomination');
        if (!a.rc || rcInvalidMessage(a.rc)) manques.push('RC');
        if (!a.ice || iceInvalidMessage(a.ice)) manques.push('ICE');
        if (!a.ifFiscal || ifInvalidMessage(a.ifFiscal)) manques.push('identifiant fiscal');
        if (!a.siege) manques.push('siège');
        if (!a.repNom || !a.repPrenom) manques.push('représentant légal');
        if (!a.repCin || cinInvalidMessage(a.repCin)) manques.push('CIN du représentant');
        if (!a.repAdresse) manques.push('adresse du représentant');
        if (!a.repQualite) manques.push('qualité du représentant');
        if (!importMode && !a.rcUploaded) manques.push('RC de l’entité (fichier)');
        if (!importMode && !a.statutsEntiteUploaded) manques.push('statuts de l’entité (fichier)');
        if (!importMode && !a.repCinUploaded) manques.push('CIN du représentant (extraite)');
      } else {
        if (!a.nom) manques.push('nom');
        if (!a.prenom) manques.push('prénom');
        if (!a.cin) manques.push('n° de CIN');
        else if (cinInvalidMessage(a.cin)) manques.push('n° de CIN valide');
        if (!a.adresse) manques.push('adresse');
        if (!a.dateNaissance) manques.push('date de naissance (imprimée dans les statuts)');
        if (!a.lieuNaissance) manques.push('lieu de naissance (imprimé dans les statuts)');
        if (!importMode && !a.cinUploaded) {
          manques.push('CIN extraite — joindre le fichier ne suffit pas, cliquez « Extraire »');
        }
      }
      if (manques.length) out.push(`${qui} : ${manques.join(', ')}.`);
    });
    if (!sumPartsValid) {
      out.push(
        `La somme des parts doit égaler ${totalPartsExpected ?? 0} (actuellement ${totalParts}).`,
      );
    }
    if (!apportValid) {
      out.push(
        `La somme des apports doit égaler le capital ${capitalSocialExpected ?? 0} MAD `
          + `(actuellement ${totalApport}).`,
      );
    }
    return out;
  })();

  return (
    <form
      noValidate
      onSubmit={(e) => {
        e.preventDefault();
        onSubmit({
          // 2026-06-19 — Persistance des montants ajustes (dernier absorbe le
          // reliquat) pour que le payload backend matche Sigma=capital exactement.
          associes: associes.map((a, i) => ({
            ...a,
            montantApport: distributedApports[i] ?? a.montantApport,
            pourcentageDetention: pct(a),
            // 2026-06-19 — Mandataire conditionnel : on n'envoie les champs
            // que si le toggle est actif (sinon empty -> sentinel "VALEUR
            // MANQUANTE" pour le gabarit, signal pedagogique a l'employe).
            mandataire: a.representeParMandataire ? (a.mandataire ?? '') : '',
            procurationDate: a.representeParMandataire ? (a.procurationDate ?? '') : '',
          })),
          formeJuridique,
          totalParts,
          totalApport,
        });
      }}
      className="grid grid-cols-1 gap-6 lg:grid-cols-[1fr_300px]"
    >
      <div className="space-y-5">
        {/* Fix 2026-06-10 — Si Step3 (capital) n'a pas ete valide, la cible est
            inconnue : on signale au user de retourner Step3 + on n'affiche pas de
            barre vide (qui pourrait laisser croire que tout va bien). */}
        {!isUnique && !expectsTotal && !expectsCapital && (
          <div className="flex items-start gap-2 rounded-lg border-l-4 border-warning bg-warning/10 p-3 text-xs text-warning">
            <AlertTriangle className="mt-0.5 h-4 w-4 flex-shrink-0" />
            <p>
              <strong>Nombre total de parts non defini.</strong> Retournez a
              l'etape 3 (Capital) pour saisir le nombre total de parts du capital
              et le montant du capital social — la repartition entre associes
              sera ensuite validee en temps reel.
            </p>
          </div>
        )}

        {/* F3 2026-06-09 + F5 2026-06-10 — Bandeau persistant + BARRE DE PROGRESSION
            visuelle : la cible Step3 (capital + total parts) est affichee en tete,
            les barres se remplissent au fur et a mesure de la distribution. */}
        {(expectsCapital || expectsTotal) && (
          <div className="sticky top-4 z-10 space-y-3 rounded-xl border-2 border-accent/30 bg-bg-raised p-4 shadow-md">
            <div className="flex flex-wrap items-baseline justify-between gap-3">
              <span className="text-[10px] font-bold uppercase tracking-wide text-fg-subtle">
                Cible depuis l'etape 3 (Capital)
              </span>
              <div className="flex flex-wrap items-baseline gap-x-6 gap-y-1 text-sm">
                {expectsCapital && (
                  <span>
                    <strong className="text-fg">Capital</strong>{' '}
                    <span className="font-bold text-accent">
                      {capitalSocialExpected?.toLocaleString('fr-MA')} MAD
                    </span>
                  </span>
                )}
                {expectsTotal && (
                  <span>
                    <strong className="text-fg">Total parts</strong>{' '}
                    <span className="font-bold text-accent">{totalPartsExpected}</span>
                  </span>
                )}
              </div>
            </div>

            {/* 2026-06-22 — Ventilation des apports cibles PAR TYPE (depuis Step3),
                au lieu d'un capital agrégé : l'employé sait combien distribuer en
                numéraire / nature / industrie. */}
            {((apportNumeraireExpected ?? 0) > 0 ||
              (apportNatureExpected ?? 0) > 0 ||
              (apportIndustrieExpected ?? 0) > 0) && (
              <div className="flex flex-wrap items-center gap-x-4 gap-y-1 rounded-lg bg-bg-overlay px-3 py-2 text-xs">
                <span className="font-semibold text-fg-subtle">Dont apports à répartir :</span>
                {(apportNumeraireExpected ?? 0) > 0 && (
                  <span>
                    Numéraire{' '}
                    <span className="font-bold text-accent">
                      {(apportNumeraireExpected ?? 0).toLocaleString('fr-MA')} MAD
                    </span>{' '}
                    <span className="text-fg-subtle">({targetPartsByType.NUMERAIRE} parts)</span>
                  </span>
                )}
                {(apportNatureExpected ?? 0) > 0 && (
                  <span>
                    Nature{' '}
                    <span className="font-bold text-accent">
                      {(apportNatureExpected ?? 0).toLocaleString('fr-MA')} MAD
                    </span>{' '}
                    <span className="text-fg-subtle">({targetPartsByType.NATURE} parts)</span>
                  </span>
                )}
                {(apportIndustrieExpected ?? 0) > 0 && (
                  <span>
                    Industrie{' '}
                    <span className="font-bold text-accent">
                      {(apportIndustrieExpected ?? 0).toLocaleString('fr-MA')} MAD
                    </span>{' '}
                    <span className="text-fg-subtle">({targetPartsByType.INDUSTRIE} parts)</span>
                  </span>
                )}
              </div>
            )}

            {/* F5 — Barre de progression PARTS : se remplit au fur et a mesure. */}
            {expectsTotal && (
              <DistributionBar
                label="Parts distribuees"
                current={totalParts}
                target={totalPartsExpected ?? 0}
                unit=""
                formatValue={(v) => v.toLocaleString('fr-MA')}
              />
            )}
            {/* 2026-06-22 — Barres APPORTS PAR TYPE : chaque type vs sa cible Step3,
                pour qu'une mauvaise ventilation (ex. tout en numéraire) ne soit pas
                masquée par un total global conforme. */}
            {(apportNumeraireExpected ?? 0) > 0 && (
              <DistributionBar
                label={`Numéraire — ${distPartsByType.NUMERAIRE} / ${targetPartsByType.NUMERAIRE} parts`}
                current={distByType.NUMERAIRE}
                target={apportNumeraireExpected ?? 0}
                unit=" MAD"
                formatValue={(v) => v.toLocaleString('fr-MA')}
              />
            )}
            {(apportNatureExpected ?? 0) > 0 && (
              <DistributionBar
                label={`Nature — ${distPartsByType.NATURE} / ${targetPartsByType.NATURE} parts`}
                current={distByType.NATURE}
                target={apportNatureExpected ?? 0}
                unit=" MAD"
                formatValue={(v) => v.toLocaleString('fr-MA')}
              />
            )}
            {(apportIndustrieExpected ?? 0) > 0 && (
              <DistributionBar
                label={`Industrie — ${distPartsByType.INDUSTRIE} / ${targetPartsByType.INDUSTRIE} parts`}
                current={distByType.INDUSTRIE}
                target={apportIndustrieExpected ?? 0}
                unit=" MAD"
                formatValue={(v) => v.toLocaleString('fr-MA')}
              />
            )}
            {/* Fallback : Step3 sans ventilation (anciens dossiers) -> barre globale. */}
            {!expectsByType && expectsCapital && (
              <DistributionBar
                label="Apports distribues"
                current={totalApport}
                target={capitalSocialExpected ?? 0}
                unit=" MAD"
                formatValue={(v) => v.toLocaleString('fr-MA')}
              />
            )}
          </div>
        )}

        <div className="rounded-xl border border-accent/20 bg-accent/10 p-4 text-xs text-fg">
          {isUnique
            ? 'SARL AU : un seul associe unique detient 100% des parts.'
            : 'SARL : plusieurs associes possibles. La somme des parts doit egaler le nombre total de parts du capital (verifie en temps reel ci-dessus).'}
          <span className="ml-1">
            <strong>L'upload de la CIN est obligatoire</strong> pour chaque
            associe. Les dirigeants marques &laquo; Associe &raquo; a l'etape
            precedente sont propages automatiquement (identite + CIN reprises).
          </span>
        </div>

        {/* 2026-06-19 — Bouton "Répartir équitablement" : alloue parts + apports
            en garantissant Σ exacte (reliquat au dernier associé). Affiché
            uniquement si Step3 a fixé la cible (capital + nbParts + valeur nominale). */}
        {canAutoDistribute && associes.length >= 2 && (
          <button
            type="button"
            onClick={autoDistribute}
            className="flex w-full items-center justify-center gap-2 rounded-lg border border-accent/30 bg-accent/5 px-3 py-2 text-xs font-semibold text-accent hover:bg-accent/10"
            title="Distribue les parts + apports egalement entre les associes ; le dernier absorbe le reliquat pour que la somme tombe exactement sur le capital."
          >
            Repartir equitablement ({associes.length} associes —{' '}
            {(totalPartsExpected ?? 0).toLocaleString('fr-MA')} parts /{' '}
            {(capitalSocialExpected ?? 0).toLocaleString('fr-MA')} MAD)
          </button>
        )}

        {associes.map((a, i) => {
          const cinError = cinInvalidMessage(a.cin);
          const isPropagated = !!a.fromDirigeantId;
          const isMorale = a.typePersonne === 'MORALE';
          const montantAffiche = distributedApports[i] ?? (
            valeurNominaleExpected
              ? Math.round((Number(a.nombreParts) || 0) * valeurNominaleExpected * 100) / 100
              : a.montantApport
          );
          const isLast = i === associes.length - 1;
          const reliquatActif =
            associes.length >= 2 &&
            !!valeurNominaleExpected &&
            !!capitalSocialExpected &&
            !!totalPartsExpected &&
            totalParts === totalPartsExpected;
          return (
            <div
              key={a.id}
              className={`overflow-hidden rounded-xl border shadow-sm ${
                isPropagated
                  ? 'border-accent/40 bg-accent/5'
                  : 'border-border bg-bg-raised'
              }`}
            >
              <div className="flex items-center justify-between border-b border-border bg-bg-overlay px-5 py-3">
                <div className="flex items-center gap-3">
                  <div className="flex h-8 w-8 items-center justify-center rounded-full bg-accent text-xs font-bold text-bg-raised">
                    <Users className="h-4 w-4" />
                  </div>
                  <span className="text-sm font-bold text-fg">
                    {a.prenom || a.nom
                      ? `${a.prenom} ${a.nom}`
                      : `Associe ${i + 1}`}
                  </span>
                  {isPropagated && (
                    <span
                      className="inline-flex items-center gap-1 rounded bg-accent/15 px-2 py-0.5 text-[10px] font-semibold uppercase text-accent"
                      title="Propage depuis un dirigeant marque Associe a l'etape 5"
                    >
                      <Link2 className="h-3 w-3" /> Dirigeant-Associe
                    </span>
                  )}
                </div>
                {!isUnique && !isPropagated && associes.length > 1 && (
                  <button
                    type="button"
                    onClick={() =>
                      setAssocies((arr) => arr.filter((x) => x.id !== a.id))
                    }
                    className="rounded p-1.5 text-danger hover:bg-danger/10"
                  >
                    <Trash2 className="h-4 w-4" />
                  </button>
                )}
              </div>

              <div className="space-y-4 p-5">
                {/* EX5 — Toggle PHYSIQUE / MORALE — desactive si propage (source = Step5). */}
                <div>
                  <p className="mb-2 text-[11px] font-bold uppercase tracking-wide text-fg-subtle">
                    Nature de l'associe
                  </p>
                  <div className="grid grid-cols-1 gap-2 md:grid-cols-2">
                    <button
                      type="button"
                      role="radio"
                      aria-checked={a.typePersonne === 'PHYSIQUE'}
                      disabled={isPropagated}
                      onClick={() => update(a.id, { typePersonne: 'PHYSIQUE' })}
                      className={`flex items-center gap-3 rounded-lg border-2 px-3 py-2.5 text-left transition disabled:opacity-60 ${
                        a.typePersonne === 'PHYSIQUE'
                          ? 'border-accent bg-accent/5'
                          : 'border-border bg-bg-overlay hover:border-accent/40'
                      }`}
                    >
                      <User className={`h-4 w-4 ${a.typePersonne === 'PHYSIQUE' ? 'text-accent' : 'text-fg-subtle'}`} />
                      <div>
                        <p className="text-sm font-bold text-fg">Personne physique</p>
                        <p className="text-[11px] text-fg-subtle">Individu titulaire d'une CIN</p>
                      </div>
                    </button>
                    <button
                      type="button"
                      role="radio"
                      aria-checked={a.typePersonne === 'MORALE'}
                      disabled={isPropagated}
                      onClick={() => update(a.id, { typePersonne: 'MORALE' })}
                      className={`flex items-center gap-3 rounded-lg border-2 px-3 py-2.5 text-left transition disabled:opacity-60 ${
                        a.typePersonne === 'MORALE'
                          ? 'border-accent bg-accent/5'
                          : 'border-border bg-bg-overlay hover:border-accent/40'
                      }`}
                    >
                      <Building2 className={`h-4 w-4 ${a.typePersonne === 'MORALE' ? 'text-accent' : 'text-fg-subtle'}`} />
                      <div>
                        <p className="text-sm font-bold text-fg">Personne morale</p>
                        <p className="text-[11px] text-fg-subtle">Societe / entite (RC + ICE + IF + representant)</p>
                      </div>
                    </button>
                  </div>
                  {isPropagated && (
                    <p className="mt-1 text-[10px] text-fg-subtle">
                      Type fige par le dirigeant correspondant (revenir a l'etape 5 pour modifier).
                    </p>
                  )}
                </div>

                {/* 2026-08 (contrat CREATION) — genre grammatical + associe-gerant. */}
                <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                  <div>
                    <label className="mb-1 block text-xs text-fg-subtle">
                      Genre (accord grammatical)
                    </label>
                    <select
                      value={
                        a.genre ??
                        (a.typePersonne === 'MORALE'
                          ? 'féminin'
                          : a.civilite === 'Mme'
                            ? 'féminin'
                            : 'masculin')
                      }
                      /*
                       * Fix B1 (2026-08-16) — SOURCE UNIQUE civilité / genre.
                       *
                       * Les deux champs vivaient séparément : on pouvait choisir
                       * « féminin » ici alors que la civilité restait « M. », et les
                       * 3 documents de création sortaient « M. … né » pour une
                       * associée. Pire, pour un associé PROPAGÉ depuis l'étape 5, la
                       * civilité est verrouillée ET l'effet de propagation recalcule
                       * `genre` depuis `d.civilite` à chaque passage : le choix fait
                       * ici était systématiquement écrasé, sans trace.
                       *
                       * Désormais le genre PILOTE la civilité (et réciproquement) :
                       * les deux ne peuvent plus diverger, quel que soit le sens de
                       * saisie. Pour un associé propagé le contrôle est verrouillé
                       * comme la civilité — l'identité appartient à l'étape 5.
                       */
                      disabled={isPropagated}
                      onChange={(e) =>
                        update(a.id, {
                          genre: e.target.value,
                          civilite: e.target.value === 'féminin' ? 'Mme' : 'M',
                        })
                      }
                      className="h-9 w-full rounded-lg border border-border bg-bg-overlay px-3 text-sm disabled:opacity-60"
                    >
                      <option value="masculin">Masculin</option>
                      <option value="féminin">Feminin</option>
                    </select>
                    {isPropagated && (
                      <p className="mt-1 text-[10px] text-fg-subtle">
                        Decoule de la civilite du dirigeant (etape 5).
                      </p>
                    )}
                  </div>
                  <label className="flex items-center gap-2 self-end pb-2 text-sm text-fg">
                    <input
                      type="checkbox"
                      checked={!!a.estGerant}
                      disabled={isPropagated}
                      onChange={(e) => update(a.id, { estGerant: e.target.checked })}
                      className="h-4 w-4 rounded border-border accent-accent disabled:opacity-60"
                    />
                    Associe egalement gerant
                    {isPropagated && (
                      <span className="text-[10px] text-fg-subtle">(repris de l'etape 5)</span>
                    )}
                  </label>
                </div>

                {/* EX5 — Formulaire MORALE pour associe. */}
                {isMorale && (
                  <MoraleAssocieForm
                    associe={a}
                    isPropagated={isPropagated}
                    dossierId={dossierId}
                    onChange={(patch) => update(a.id, patch)}
                    onPieceUploaded={onPieceUploaded}
                  />
                )}

                {/* PHYSIQUE : CIN + identite. */}
                {!isMorale && (
                <>
                {/*
                 * 2026-06-15 — Assistant d'extraction CIN dedie aux Etapes
                 * 5/6 : mode="cin" (recto + verso + toggle [Ancienne |
                 * Nouvelle], AUCUNE option CN). archive=true + dossierId
                 * declenche la fusion recto+verso et l'archivage automatique
                 * en Data Room (DataroomIdentityArchiver). meta.
                 * archivedDocumentId est utilise pour flagger cinUploaded
                 * cote validation. L'ancien bloc d'upload single-file +
                 * extraction Tesseract est SUPPRIME (doublon). La saisie
                 * manuelle de chaque champ reste possible.
                 */}
                {/* 2026-06-19 (Cowork) — Re-hydratation : si la CIN est deja
                    archivee en Data Room (cinUploaded + cinFileName), on n'affiche
                    PAS l'IdentityExtractor par defaut — on indique l'archive
                    existante + bouton "Remplacer" qui rouvre l'extracteur. Evite
                    l'effet "redemande d'upload" a la navigation Step6 -> Step5. */}
                {!isPropagated && (!a.cinUploaded || !a.cinFileName || replacingCinIds.has(a.id)) && (
                  <div className="mb-3">
                    <IdentityExtractor
                      mode="cin"
                      dossierId={dossierId}
                      onApply={(values, meta) => {
                        const patch: Partial<Associe> = {};
                        if (values.nom) patch.nom = values.nom;
                        if (values.prenom) patch.prenom = values.prenom;
                        if (values.cin) patch.cin = values.cin;
                        if (values.adresse) patch.adresse = values.adresse;
                        // 2026-06-16 — Dates : DD.MM.YYYY (backend) -> YYYY-MM-DD
                        // (input type="date" du formulaire associe).
                        if (values.date_naissance) patch.dateNaissance = toIsoDate(values.date_naissance);
                        if (values.lieu_naissance) patch.lieuNaissance = values.lieu_naissance;
                        if (values.date_validite) patch.pieceValidite = toIsoDate(values.date_validite);
                        if (values.nationalite) patch.nationalite = values.nationalite;
                        if (values.sexe) {
                          patch.civilite = values.sexe.toUpperCase() === 'F' ? 'Mme' : 'M';
                        }
                        if (meta?.archivedDocumentId) {
                          patch.cinUploaded = true;
                          patch.cinFileName = `Archive Data Room ${meta.archivedDocumentId.slice(0, 8)}`;
                        }
                        if (Object.keys(patch).length > 0) update(a.id, patch);
                        // Sortir du mode "remplacement" — l'archive est mise a jour.
                        setReplacingCinIds((s) => {
                          const next = new Set(s); next.delete(a.id); return next;
                        });
                      }}
                    />
                  </div>
                )}
                {/* Trace d'archivage : visible des qu'une pièce est presente. */}
                {(isPropagated || a.cinUploaded) && a.cinFileName && (
                  <div className="mb-3 flex items-center justify-between gap-2 rounded-md border border-success/30 bg-success/5 p-2">
                    <p className="flex items-center gap-1.5 text-[11px] text-success">
                      <CheckCircle className="h-3 w-3" />
                      {a.cinFileName}
                      {isPropagated && ' (reprise de l\'etape 5)'}
                    </p>
                    {!isPropagated && !replacingCinIds.has(a.id) && (
                      <button
                        type="button"
                        onClick={() => setReplacingCinIds((s) => new Set(s).add(a.id))}
                        className="text-[11px] font-medium text-accent hover:underline"
                      >
                        Remplacer
                      </button>
                    )}
                    {!isPropagated && replacingCinIds.has(a.id) && (
                      <button
                        type="button"
                        onClick={() =>
                          setReplacingCinIds((s) => {
                            const next = new Set(s); next.delete(a.id); return next;
                          })
                        }
                        className="text-[11px] text-fg-subtle hover:underline"
                      >
                        Annuler
                      </button>
                    )}
                  </div>
                )}

                <div className="grid grid-cols-1 gap-3 md:grid-cols-4">
                  <div>
                    <label className="mb-1 block text-xs text-fg-subtle">
                      Civilite
                    </label>
                    <select
                      value={a.civilite}
                      /* Fix B1 — la civilité pilote le genre : les deux ne peuvent
                         plus diverger (« M. … née »). Cf. le sélecteur de genre. */
                      onChange={(e) =>
                        update(a.id, {
                          civilite: e.target.value as 'M' | 'Mme',
                          genre: e.target.value === 'Mme' ? 'féminin' : 'masculin',
                        })
                      }
                      disabled={isPropagated}
                      className="h-9 w-full rounded-lg border border-border bg-bg-overlay px-3 text-sm disabled:opacity-60"
                    >
                      <option value="M">Monsieur</option>
                      <option value="Mme">Madame</option>
                    </select>
                  </div>
                  <Field
                    label="Nom"
                    required
                    value={a.nom}
                    onChange={(e) => update(a.id, { nom: e.target.value })}
                    disabled={isPropagated}
                  />
                  <Field
                    label="Prenom"
                    required
                    value={a.prenom}
                    onChange={(e) => update(a.id, { prenom: e.target.value })}
                    disabled={isPropagated}
                  />
                  <div>
                    <label className="mb-1 block text-xs text-fg-subtle">
                      N° CIN <span className="text-danger">*</span>
                    </label>
                    <input aria-label="N° CIN"
                      type="text"
                      value={a.cin}
                      onChange={(e) =>
                        update(a.id, { cin: e.target.value.toUpperCase() })
                      }
                      placeholder="X 123456"
                      disabled={isPropagated}
                      aria-invalid={!!cinError}
                      className={`h-9 w-full rounded-lg border bg-bg-overlay px-3 text-sm uppercase disabled:opacity-60 ${
                        cinError ? 'border-danger' : 'border-border'
                      }`}
                    />
                    {cinError && (
                      <p className="mt-0.5 text-[11px] font-medium text-danger">
                        {cinError}
                      </p>
                    )}
                  </div>
                  <div className="md:col-span-4">
                    <label className="mb-1 block text-xs text-fg-subtle">
                      Adresse <span className="text-danger">*</span>
                    </label>
                    <input aria-label="Adresse"
                      type="text"
                      value={a.adresse}
                      onChange={(e) =>
                        update(a.id, { adresse: e.target.value })
                      }
                      placeholder="Adresse complete"
                      disabled={isPropagated}
                      className="h-9 w-full rounded-lg border border-border bg-bg-overlay px-3 text-sm disabled:opacity-60"
                    />
                  </div>
                </div>
                {/* 2026-06-11 — Champs etat civil pour les Statuts */}
                <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                  <div>
                    <label className="mb-1 block text-xs text-fg-subtle">Date de naissance</label>
                    <input aria-label="Date de naissance"
                      type="date"
                      value={a.dateNaissance ?? ''}
                      onChange={(e) => update(a.id, { dateNaissance: e.target.value })}
                      disabled={isPropagated}
                      className="h-9 w-full rounded-lg border border-border bg-bg-overlay px-3 text-sm disabled:opacity-60"
                    />
                  </div>
                  <div>
                    <label className="mb-1 block text-xs text-fg-subtle">Lieu de naissance</label>
                    <input aria-label="Lieu de naissance"
                      type="text"
                      value={a.lieuNaissance ?? ''}
                      onChange={(e) => update(a.id, { lieuNaissance: e.target.value })}
                      placeholder="Ex: Rabat"
                      disabled={isPropagated}
                      className="h-9 w-full rounded-lg border border-border bg-bg-overlay px-3 text-sm disabled:opacity-60"
                    />
                  </div>
                  <div>
                    <label className="mb-1 block text-xs text-fg-subtle">Nationalite</label>
                    <input aria-label="Nationalite"
                      type="text"
                      value={a.nationalite ?? 'marocaine'}
                      onChange={(e) => update(a.id, { nationalite: e.target.value })}
                      placeholder="marocaine"
                      disabled={isPropagated}
                      className="h-9 w-full rounded-lg border border-border bg-bg-overlay px-3 text-sm disabled:opacity-60"
                    />
                  </div>
                  <div>
                    <label className="mb-1 block text-xs text-fg-subtle">CIN valable jusqu'au</label>
                    <input aria-label="CIN valable jusqu'au"
                      type="date"
                      value={a.pieceValidite ?? ''}
                      onChange={(e) => update(a.id, { pieceValidite: e.target.value })}
                      disabled={isPropagated}
                      className="h-9 w-full rounded-lg border border-border bg-bg-overlay px-3 text-sm disabled:opacity-60"
                    />
                  </div>
                </div>
                </>
                )}

                <div className="rounded-xl border border-warning/20 bg-warning/10 p-4">
                  <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
                    <p className="text-xs font-semibold uppercase text-warning">
                      Parts sociales & apport
                    </p>
                    {/* EX3 — Total parts visible en tete de chaque carte associe */}
                    {(expectsTotal || expectsCapital) && (
                      <div className="flex flex-wrap items-center gap-3 text-[10px]">
                        {expectsTotal && (
                          <span
                            className={`inline-flex items-center gap-1 rounded-full border px-2 py-0.5 font-bold uppercase ${
                              sumPartsValid
                                ? 'border-success/40 bg-success/10 text-success'
                                : 'border-danger/40 bg-danger/10 text-danger'
                            }`}
                          >
                            Total parts {totalParts}/{totalPartsExpected}
                            {!sumPartsValid && partsRemaining !== 0 && (
                              <span className="ml-1">
                                ({partsRemaining > 0 ? `+${partsRemaining} a repartir` : `${partsRemaining}`})
                              </span>
                            )}
                          </span>
                        )}
                        {expectsCapital && (
                          <span
                            className={`inline-flex items-center gap-1 rounded-full border px-2 py-0.5 font-bold uppercase ${
                              apportValid
                                ? 'border-success/40 bg-success/10 text-success'
                                : 'border-danger/40 bg-danger/10 text-danger'
                            }`}
                          >
                            Apports {totalApport.toLocaleString('fr-MA')}/{capitalSocialExpected?.toLocaleString('fr-MA')} MAD
                            {!apportValid && apportRemaining !== 0 && (
                              <span className="ml-1">
                                ({apportRemaining > 0 ? `+${apportRemaining.toLocaleString('fr-MA')} a repartir` : `${apportRemaining.toLocaleString('fr-MA')}`})
                              </span>
                            )}
                          </span>
                        )}
                      </div>
                    )}
                  </div>
                  {/* 2026-06-21 (BLOC A) — Apports REPETABLES : un associe
                      peut combiner plusieurs lignes (ex. 100 parts en nature
                      + 400 en numeraire). nombreParts = Σ parts (derive) ;
                      montantApport global = nombreParts × valeurNominale. */}
                  <div className="space-y-3">
                    <p className="text-[11px] font-semibold uppercase text-fg-subtle">
                      Lignes d'apport
                    </p>
                    {a.apports.map((line, lineIdx) => {
                      const lineMontant = valeurNominaleExpected
                        ? Math.round((Number(line.parts) || 0) * valeurNominaleExpected * 100) / 100
                        : 0;
                      return (
                        <div
                          key={line.id}
                          className="grid grid-cols-1 gap-2 md:grid-cols-[160px_120px_1fr_auto] md:items-end"
                        >
                          <div>
                            <label className="mb-1 block text-[10px] text-fg-subtle">
                              Type
                            </label>
                            <select
                              value={line.type}
                              onChange={(e) => {
                                const newApports = a.apports.map((x, i) =>
                                  i === lineIdx ? { ...x, type: e.target.value as TypeApport } : x,
                                );
                                const derived = deriveFromApports(newApports, valeurNominaleExpected);
                                update(a.id, { apports: newApports, ...derived });
                              }}
                              className="h-9 w-full rounded-lg border-2 border-border bg-bg-overlay px-3 text-sm focus:border-accent focus:outline-none"
                            >
                              <option value="NUMERAIRE">Numéraire</option>
                              <option value="NATURE">Nature</option>
                              <option value="INDUSTRIE">Industrie</option>
                            </select>
                          </div>
                          <div>
                            <label className="mb-1 block text-[10px] text-fg-subtle">
                              Parts <span className="text-danger">*</span>
                            </label>
                            <input aria-label="Parts"
                              type="number"
                              min={0}
                              step={1}
                              value={line.parts}
                              onChange={(e) => {
                                // 2026-06-22 — Les parts sont des ENTIERS naturels :
                                // on arrondit toute saisie/division décimale.
                                const intParts = Math.max(0, Math.round(Number(e.target.value) || 0));
                                const newApports = a.apports.map((x, i) =>
                                  i === lineIdx ? { ...x, parts: intParts } : x,
                                );
                                const derived = deriveFromApports(newApports, valeurNominaleExpected);
                                update(a.id, { apports: newApports, ...derived });
                              }}
                              className="h-9 w-full rounded-lg border-2 border-warning/30 bg-bg-raised px-3 text-sm font-bold focus:border-warning focus:outline-none"
                            />
                          </div>
                          <div>
                            <label className="mb-1 block text-[10px] text-fg-subtle">
                              Montant ligne (auto)
                            </label>
                            <div className="flex h-9 items-center justify-between rounded-lg border border-border bg-bg-overlay px-3 text-sm">
                              <span className="font-bold text-fg">
                                {lineMontant.toLocaleString('fr-MA')}
                              </span>
                              <span className="text-[11px] text-fg-subtle">MAD</span>
                            </div>
                          </div>
                          {a.apports.length > 1 && (
                            <button
                              type="button"
                              onClick={() => {
                                const newApports = a.apports.filter((_, i) => i !== lineIdx);
                                const derived = deriveFromApports(newApports, valeurNominaleExpected);
                                update(a.id, { apports: newApports, ...derived });
                              }}
                              className="h-9 rounded-lg border border-danger/30 px-2 text-danger hover:bg-danger/10"
                              title="Supprimer cette ligne d'apport"
                            >
                              <Trash2 className="h-4 w-4" />
                            </button>
                          )}
                        </div>
                      );
                    })}
                    <button
                      type="button"
                      onClick={() => {
                        const newApports: Apport[] = [
                          ...a.apports,
                          { id: crypto.randomUUID(), type: 'NUMERAIRE', parts: 0 },
                        ];
                        const derived = deriveFromApports(newApports, valeurNominaleExpected);
                        update(a.id, { apports: newApports, ...derived });
                      }}
                      className="flex w-full items-center justify-center gap-2 rounded-lg border-2 border-dashed border-border py-2 text-xs text-accent transition hover:border-accent hover:bg-accent/10"
                    >
                      <UserPlus className="h-3.5 w-3.5" /> Ajouter une ligne d'apport
                    </button>

                    {/* Totaux derives (parts + montant + %) — synthese visible */}
                    <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
                      <div>
                        <label className="mb-1 block text-[10px] text-fg-subtle">
                          Total parts (Σ)
                        </label>
                        <div className="flex h-9 items-center rounded-lg border border-warning/30 bg-bg-raised px-3 text-sm font-bold">
                          {a.nombreParts}
                        </div>
                        {valeurNominaleExpected && (
                          <p className="mt-0.5 text-[10px] text-fg-subtle">
                            x {valeurNominaleExpected.toLocaleString('fr-MA')} MAD/part
                          </p>
                        )}
                      </div>
                      <div>
                        <label className="mb-1 block text-[10px] text-fg-subtle">
                          Montant total (MAD) — calcule auto
                        </label>
                        <div className="flex h-9 items-center justify-between rounded-lg border border-border bg-bg-overlay px-3 text-sm">
                          <span className="font-bold text-fg">
                            {montantAffiche.toLocaleString('fr-MA')}
                          </span>
                          <span className="text-[11px] text-fg-subtle">MAD</span>
                        </div>
                        {!valeurNominaleExpected && (
                          <p className="mt-0.5 text-[10px] text-warning">
                            Definissez le capital a l'etape 3 pour le calcul auto.
                          </p>
                        )}
                        {isLast && reliquatActif && (
                          <p className="mt-0.5 text-[10px] text-accent">
                            Reliquat alloue ici pour Σ = capital exacte.
                          </p>
                        )}
                      </div>
                      <div>
                        <label className="mb-1 block text-[10px] text-fg-subtle">
                          % detention
                        </label>
                        <div className="flex h-9 items-center rounded-lg border border-warning/30 bg-bg-raised px-3 text-sm font-bold text-warning">
                          {pct(a).toFixed(2)}%
                        </div>
                      </div>
                    </div>
                  </div>
                </div>

                {/* 2026-06-19 — Bloc mandataire (conditionnel) — Step6 Cowork.
                    L'associe PHYSIQUE peut etre represente par un mandataire au
                    moment de la signature ; le gabarit Statuts attend alors
                    {{associe_pp_mandataire}} + {{associe_pp_procuration_date}}. */}
                {!isMorale && (
                  <div className="rounded-lg border border-border bg-bg-overlay p-3">
                    <label className="flex items-center gap-2 text-xs font-bold text-fg">
                      <input
                        type="checkbox"
                        checked={!!a.representeParMandataire}
                        onChange={(ev) =>
                          update(a.id, { representeParMandataire: ev.target.checked })
                        }
                        className="h-4 w-4 accent-accent"
                      />
                      Represente par un mandataire (procuration speciale)
                    </label>
                    {a.representeParMandataire && (
                      <div className="mt-3 grid grid-cols-1 gap-3 md:grid-cols-2">
                        <div>
                          <label className="mb-1 block text-xs text-fg-subtle">
                            Nom du mandataire
                          </label>
                          <input aria-label="Nom du mandataire"
                            type="text"
                            value={a.mandataire ?? ''}
                            onChange={(ev) => update(a.id, { mandataire: ev.target.value })}
                            placeholder="Ex: Me Hassan TAZI, Avocat au Barreau de Casablanca"
                            className="h-9 w-full rounded-lg border border-border bg-bg-raised px-3 text-sm"
                          />
                        </div>
                        <div>
                          <label className="mb-1 block text-xs text-fg-subtle">
                            Date de la procuration
                          </label>
                          <input aria-label="Date de la procuration"
                            type="date"
                            value={a.procurationDate ?? ''}
                            onChange={(ev) => update(a.id, { procurationDate: ev.target.value })}
                            className="h-9 w-full rounded-lg border border-border bg-bg-raised px-3 text-sm"
                          />
                        </div>
                      </div>
                    )}
                  </div>
                )}
              </div>
            </div>
          );
        })}

        {!isUnique && (
          <button
            type="button"
            onClick={() => setAssocies([...associes, newAssocie()])}
            className="flex w-full items-center justify-center gap-2 rounded-lg border-2 border-dashed border-border py-3 text-sm text-accent transition hover:border-accent hover:bg-accent/10"
          >
            <UserPlus className="h-4 w-4" /> Ajouter un associe
          </button>
        )}

        {!isUnique && associes.length < 2 && (
          <div className="rounded-lg border-l-4 border-warning bg-warning/10 p-3 text-sm text-warning">
            Une SARL requiert <strong>au moins 2 associés</strong>. Ajoutez un second
            associé pour continuer (une société à <strong>un seul</strong> associé relève
            du workflow « SARL AU »).
          </div>
        )}

        {isUnique && associes.length > 1 && (
          <div className="flex items-start gap-2 rounded-lg border-l-4 border-danger bg-danger/10 p-3">
            <AlertTriangle className="h-4 w-4 flex-shrink-0 text-danger" />
            <p className="text-xs text-danger">
              SARL AU : un seul associe est autorise. Supprimez les associes
              supplementaires.
            </p>
          </div>
        )}

        {expectsTotal && !sumPartsValid && (
          <div className="flex items-start gap-2 rounded-lg border-l-4 border-warning bg-warning/10 p-3">
            <AlertTriangle className="h-4 w-4 flex-shrink-0 text-warning" />
            <p className="text-xs text-warning">
              La somme des parts distribuees ({totalParts}) doit egaler le
              nombre total de parts du capital ({totalPartsExpected}). Ajustez
              les repartitions pour pouvoir continuer.
              {partsRemaining !== 0 && (
                <strong className="ml-1">
                  Restant a repartir : {partsRemaining > 0 ? `+${partsRemaining}` : partsRemaining} parts.
                </strong>
              )}
            </p>
          </div>
        )}

        {expectsCapital && !apportValid && (
          <div className="flex items-start gap-2 rounded-lg border-l-4 border-danger bg-danger/10 p-3">
            <AlertTriangle className="h-4 w-4 flex-shrink-0 text-danger" />
            <p className="text-xs text-danger">
              La somme des apports distribues ({totalApport.toLocaleString('fr-MA')} MAD) doit
              egaler le capital social ({capitalSocialExpected?.toLocaleString('fr-MA')} MAD).
              {apportRemaining !== 0 && (
                <strong className="ml-1">
                  Restant a repartir : {apportRemaining > 0 ? `+${apportRemaining.toLocaleString('fr-MA')}` : apportRemaining.toLocaleString('fr-MA')} MAD.
                </strong>
              )}
            </p>
          </div>
        )}

        {/* Lot 2 (2026-09-07) — l'etape bloquait EN SILENCE : le bouton etait
            desactive sans dire pourquoi, et un clic ne produisait rien. On
            enonce ce qui manque, associe par associe, comme le fait l'etape 5. */}
        <BlocageValidation raisons={raisonsBlocage} testId="step6-blocage" />

        <div className="flex items-center justify-end pt-2">
          <button
            type="submit"
            disabled={!canSubmit || saving}
            className={`flex items-center gap-2 rounded-lg px-8 h-12 font-medium transition ${
              canSubmit && !saving
                ? 'bg-accent text-bg-raised hover:bg-accent-hover'
                : 'cursor-not-allowed bg-border text-fg-subtle'
            }`}
          >
            {saving ? 'Enregistrement…' : 'Valider et continuer'}
            <ChevronRight className="h-4 w-4" />
          </button>
        </div>
      </div>

      {/* Sidebar synthese — 2026-06-11 : affiche PHYSIQUE *ET* MORALE. */}
      <aside className="sticky top-6 h-fit self-start space-y-4">
        <div className="rounded-xl border border-border bg-bg-raised p-5 shadow-sm">
          <h4 className="mb-4 text-sm font-bold text-fg">Synthese des parts</h4>
          <div className="mb-4 space-y-3">
            {associes
              // Affiche TOUT associe ayant une identite (physique : nom/prenom,
              // morale : denomination). Avant : `.filter((a) => a.nom || a.prenom)`
              // excluait les MORALE.
              .filter((a) =>
                a.typePersonne === 'MORALE' ? !!a.denomination : !!(a.nom || a.prenom),
              )
              .map((a) => {
                const isMorale = a.typePersonne === 'MORALE';
                const display = isMorale
                  ? a.denomination ?? ''
                  : `${a.prenom} ${a.nom}`.trim();
                const initials = isMorale
                  ? (a.denomination ?? 'E').slice(0, 2).toUpperCase()
                  : (a.prenom[0] ?? '') + (a.nom[0] ?? '');
                return (
                  <div key={a.id} className="flex items-center gap-3">
                    <div
                      className={`flex h-7 w-7 items-center justify-center rounded-full text-[10px] font-bold text-bg-raised ${
                        isMorale ? 'bg-fg' : 'bg-accent'
                      }`}
                      title={isMorale ? 'Personne morale' : 'Personne physique'}
                    >
                      {initials}
                    </div>
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-xs font-medium text-fg">
                        {display || (isMorale ? 'Entite' : 'Associe')}
                        {isMorale && (
                          <span
                            className="ml-1 text-[10px] font-bold text-fg-subtle"
                            title="Personne morale"
                          >
                            (M)
                          </span>
                        )}
                        {a.fromDirigeantId && (
                          <span className="ml-1 text-[10px] text-accent">
                            (D)
                          </span>
                        )}
                      </p>
                      <div className="mt-1 h-1.5 w-full overflow-hidden rounded-full bg-bg-overlay">
                        <div
                          className={`h-full rounded-full ${isMorale ? 'bg-fg' : 'bg-accent'}`}
                          style={{ width: `${pct(a)}%` }}
                        />
                      </div>
                    </div>
                    <div className="text-right">
                      <p className="text-sm font-bold text-fg">
                        {a.nombreParts}
                      </p>
                      <p className="text-[10px] text-fg-subtle">
                        {pct(a).toFixed(1)}%
                      </p>
                    </div>
                  </div>
                );
              })}
          </div>
          <div className="space-y-2 border-t border-border pt-3 text-xs">
            <div className="flex justify-between">
              <span className="text-fg-subtle">Total parts</span>
              <span className="font-bold text-fg">{totalParts}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-fg-subtle">Total apports</span>
              <span className="font-bold text-fg">
                {totalApport.toLocaleString('fr-MA')} MAD
              </span>
            </div>
            {expectsTotal && (
              <div
                className={`mt-3 flex items-center justify-between rounded-lg px-3 py-2 text-xs font-semibold ${
                  sumPartsValid
                    ? 'bg-success/10 text-success'
                    : totalParts > (totalPartsExpected ?? 0)
                    ? 'bg-danger/10 text-danger'
                    : 'bg-warning/10 text-warning'
                }`}
              >
                <span>Parts distribuees</span>
                <span>
                  {totalParts}/{totalPartsExpected}
                </span>
              </div>
            )}
          </div>
        </div>
      </aside>
    </form>
  );
}

/**
 * F5 2026-06-10 — Barre de progression visuelle qui se remplit a mesure
 * que les associes sont saisis. Couleurs :
 *   - vert     : cible atteinte exactement (current == target)
 *   - bleu     : en dessous (under-distribution, normal en cours de saisie)
 *   - rouge    : depasse la cible (over-distribution, doit etre corrige)
 * Affiche systematiquement la valeur courante / cible + % rempli + restant.
 */
function DistributionBar({
  label,
  current,
  target,
  unit,
  formatValue,
}: {
  label: string;
  current: number;
  target: number;
  unit: string;
  formatValue: (v: number) => string;
}) {
  if (target <= 0) return null;
  const pct = (current / target) * 100;
  const pctClamped = Math.max(0, Math.min(100, pct));
  const remaining = target - current;
  const over = current > target;
  const exact = current === target;
  // 3 etats de couleur : exact (vert), en cours (bleu accent), depasse (rouge).
  const barColor = exact
    ? 'bg-success'
    : over
      ? 'bg-danger'
      : 'bg-accent';
  const textColor = exact
    ? 'text-success'
    : over
      ? 'text-danger'
      : 'text-accent';
  return (
    <div>
      <div className="mb-1 flex items-baseline justify-between gap-3 text-xs">
        <span className="font-semibold text-fg">{label}</span>
        <span className={`font-bold ${textColor}`}>
          {formatValue(current)}
          {unit} / {formatValue(target)}
          {unit}
          <span className="ml-1 text-[10px] font-medium text-fg-subtle">
            ({Math.round(pct)}%)
          </span>
        </span>
      </div>
      <div className="relative h-3 w-full overflow-hidden rounded-full border border-border bg-bg-overlay">
        <div
          className={`h-full ${barColor} transition-all duration-300 ease-out`}
          style={{ width: `${pctClamped}%` }}
          role="progressbar"
          aria-valuenow={current}
          aria-valuemin={0}
          aria-valuemax={target}
          aria-label={`${label} : ${formatValue(current)}${unit} sur ${formatValue(target)}${unit}`}
        />
        {/* Marqueur cible 100% (utile quand current >> target pour reperer la cible) */}
        {over && (
          <div
            className="absolute top-0 h-full w-0.5 bg-fg-subtle"
            style={{ left: '100%' }}
          />
        )}
      </div>
      {!exact && (
        <p className={`mt-0.5 text-[10px] font-medium ${textColor}`}>
          {over
            ? `Depasse de ${formatValue(Math.abs(remaining))}${unit} — reduisez les valeurs.`
            : `Restant a repartir : ${formatValue(remaining)}${unit}`}
        </p>
      )}
      {exact && (
        <p className="mt-0.5 text-[10px] font-medium text-success">
          ✓ Distribution conforme a la cible
        </p>
      )}
    </div>
  );
}

function Field({
  label,
  required,
  ...rest
}: { label: string; required?: boolean } & React.InputHTMLAttributes<HTMLInputElement>) {
  return (
    <div>
      <label className="mb-1 block text-xs text-fg-subtle">
        {label} {required && <span className="text-danger">*</span>}
      </label>
      {/* `aria-label` derive du libelle : ce champ vit dans des listes (.map),
          ou un `id` statique se dupliquerait et casserait l'association. */}
      <input
        aria-label={label}
        {...rest}
        className="h-9 w-full rounded-lg border border-border bg-bg-overlay px-3 text-sm disabled:opacity-60"
      />
    </div>
  );
}

/**
 * EX5 2026-06-09 — Formulaire associe MORALE (identique en structure a celui
 * de Step5Dirigeants, mais embarque dans Step6 pour rester self-contained).
 * Si l'associe est propage depuis Step5 (isPropagated=true), tous les champs
 * sont en lecture seule -- source de verite = Step5.
 */
function MoraleAssocieForm({
  associe: a,
  isPropagated,
  dossierId,
  onChange,
  onPieceUploaded,
}: {
  associe: Associe;
  isPropagated: boolean;
  dossierId?: string | null;
  onChange: (patch: Partial<Associe>) => void;
  onPieceUploaded?: (code: string, label: string, file: File) => Promise<void> | void;
}) {
  const rcError = rcInvalidMessage(a.rc ?? '');
  const iceError = iceInvalidMessage(a.ice ?? '');
  const ifError = ifInvalidMessage(a.ifFiscal ?? '');
  // 2026-06-21 (Cowork) — CIN representant legal MORALE non saisie manuellement :
  // IdentityExtractor + Data Room sont la source unique.

  function uploadRcFor(file: File) {
    onChange({ rcUploaded: true, rcFileName: file.name });
    void onPieceUploaded?.(
      `RC_ASSOCIE_MORALE_${a.id.slice(0, 8)}`,
      `RC entite associee ${a.denomination || a.id.slice(0, 8)}`,
      file,
    );
  }
  function uploadStatutsFor(file: File) {
    onChange({ statutsEntiteUploaded: true, statutsEntiteFileName: file.name });
    void onPieceUploaded?.(
      `STATUTS_ENTITE_ASSOCIE_${a.id.slice(0, 8)}`,
      `Statuts entite associee ${a.denomination || a.id.slice(0, 8)}`,
      file,
    );
  }
  // 2026-06-21 (Cowork) — uploadRepCinFor RETIRE : doublon avec IdentityExtractor
  // qui archive deja la CIN du representant en Data Room.

  const ro = isPropagated;
  return (
    <div className="space-y-4">
      <div className="rounded-lg border border-border bg-bg-overlay p-4">
        <p className="mb-3 flex items-center gap-2 text-xs font-bold uppercase text-fg-subtle">
          <Building2 className="h-3.5 w-3.5" /> Identification de l'entite associee
        </p>
        <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
          <div>
            <label className="mb-1 block text-xs text-fg-subtle">
              Denomination <span className="text-danger">*</span>
            </label>
            <input aria-label="Denomination"
              type="text"
              value={a.denomination ?? ''}
              disabled={ro}
              onChange={(e) => onChange({ denomination: e.target.value })}
              className="h-9 w-full rounded-lg border border-border bg-bg-raised px-3 text-sm disabled:opacity-60"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs text-fg-subtle">Forme juridique</label>
            <select
              value={a.formeJuridiqueEntite ?? 'SARL'}
              disabled={ro}
              onChange={(e) => onChange({ formeJuridiqueEntite: e.target.value })}
              className="h-9 w-full rounded-lg border border-border bg-bg-raised px-3 text-sm disabled:opacity-60"
            >
              <option value="SARL">SARL</option>
              <option value="SARL_AU">SARL AU</option>
              <option value="SA">SA</option>
              <option value="SAS">SAS</option>
              <option value="SNC">SNC</option>
              <option value="GIE">GIE</option>
              <option value="ASSOC">Association</option>
              <option value="AUTRE">Autre</option>
            </select>
          </div>
          <div>
            <label className="mb-1 block text-xs text-fg-subtle">
              RC <span className="text-danger">*</span>
            </label>
            <input aria-label="RC"
              type="text"
              value={a.rc ?? ''}
              disabled={ro}
              onChange={(e) => onChange({ rc: e.target.value })}
              aria-invalid={!!rcError}
              className={`h-9 w-full rounded-lg border bg-bg-raised px-3 text-sm disabled:opacity-60 ${
                rcError ? 'border-danger' : 'border-border'
              }`}
            />
            {rcError && <p className="mt-0.5 text-[11px] text-danger">{rcError}</p>}
          </div>
          <div>
            <label className="mb-1 block text-xs text-fg-subtle">
              ICE <span className="text-danger">*</span>
            </label>
            <input aria-label="ICE"
              type="text"
              value={a.ice ?? ''}
              disabled={ro}
              maxLength={15}
              onChange={(e) => onChange({ ice: e.target.value.replace(/[^\d]/g, '') })}
              aria-invalid={!!iceError}
              className={`h-9 w-full rounded-lg border bg-bg-raised px-3 text-sm font-mono disabled:opacity-60 ${
                iceError ? 'border-danger' : 'border-border'
              }`}
            />
            {iceError && <p className="mt-0.5 text-[11px] text-danger">{iceError}</p>}
          </div>
          <div>
            <label className="mb-1 block text-xs text-fg-subtle">
              IF <span className="text-danger">*</span>
            </label>
            <input aria-label="IF"
              type="text"
              value={a.ifFiscal ?? ''}
              disabled={ro}
              maxLength={8}
              onChange={(e) => onChange({ ifFiscal: e.target.value.replace(/[^\d]/g, '') })}
              aria-invalid={!!ifError}
              className={`h-9 w-full rounded-lg border bg-bg-raised px-3 text-sm font-mono disabled:opacity-60 ${
                ifError ? 'border-danger' : 'border-border'
              }`}
            />
            {ifError && <p className="mt-0.5 text-[11px] text-danger">{ifError}</p>}
          </div>
          <div className="md:col-span-2">
            <label className="mb-1 block text-xs text-fg-subtle">
              Adresse du siege <span className="text-danger">*</span>
            </label>
            <input aria-label="Adresse du siege"
              type="text"
              value={a.siege ?? ''}
              disabled={ro}
              onChange={(e) => onChange({ siege: e.target.value })}
              className="h-9 w-full rounded-lg border border-border bg-bg-raised px-3 text-sm disabled:opacity-60"
            />
          </div>
          {/* 2026-06-11 — capital & deliberation pour Statuts */}
          <div>
            <label className="mb-1 block text-xs text-fg-subtle">Capital de l'entite (MAD)</label>
            <input aria-label="Capital de l'entite (MAD)"
              type="number"
              min={0}
              value={a.capitalEntite ?? 0}
              disabled={ro}
              onChange={(e) => onChange({ capitalEntite: Number(e.target.value || 0) })}
              placeholder="Ex: 5000000"
              className="h-9 w-full rounded-lg border border-border bg-bg-raised px-3 text-sm disabled:opacity-60"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs text-fg-subtle">Date de la deliberation</label>
            <input aria-label="Date de la deliberation"
              type="date"
              value={a.deliberationDate ?? ''}
              disabled={ro}
              onChange={(e) => onChange({ deliberationDate: e.target.value })}
              className="h-9 w-full rounded-lg border border-border bg-bg-raised px-3 text-sm disabled:opacity-60"
            />
          </div>
        </div>
      </div>

      {/* Justificatifs entite */}
      <div className="rounded-lg border border-border bg-bg-overlay p-4">
        <p className="mb-3 text-xs font-bold uppercase text-fg-subtle">
          Justificatifs entite (obligatoires)
        </p>
        <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
          <AssocUploadCard
            uploaded={!!a.rcUploaded}
            filename={a.rcFileName}
            label="Modele J / RC recent"
            disabled={ro}
            onFile={uploadRcFor}
          />
          <AssocUploadCard
            uploaded={!!a.statutsEntiteUploaded}
            filename={a.statutsEntiteFileName}
            label="Statuts de l'entite"
            disabled={ro}
            onFile={uploadStatutsFor}
          />
        </div>
      </div>

      {/* Representant legal + upload CIN obligatoire */}
      <div className="rounded-lg border border-accent/30 bg-accent/5 p-4">
        <p className="mb-3 flex items-center gap-2 text-xs font-bold uppercase text-accent">
          <User className="h-3.5 w-3.5" /> Representant legal (personne physique)
        </p>
        {/* 2026-06-19 — Representant legal = personne physique : meme assistant
            CIN recto/verso + toggle nouvelle/ancienne que pour un associe PP.
            Aucune option CN (le representant porte une CIN). archive=true +
            dossierId declenche la fusion recto+verso et l'archivage
            automatique en Data Room. */}
        {!ro && (
          <div className="mb-3">
            <IdentityExtractor
              mode="cin"
              dossierId={dossierId}
              onApply={(values, meta) => {
                const patch: Partial<Associe> = {};
                if (values.nom) patch.repNom = values.nom;
                if (values.prenom) patch.repPrenom = values.prenom;
                if (values.cin) patch.repCin = values.cin.toUpperCase();
                if (values.adresse) patch.repAdresse = values.adresse;
                if (values.date_naissance) patch.repDateNaissance = toIsoDate(values.date_naissance);
                if (values.lieu_naissance) patch.repLieuNaissance = values.lieu_naissance;
                if (values.date_validite) patch.repPieceValidite = toIsoDate(values.date_validite);
                if (values.nationalite) patch.repNationalite = values.nationalite;
                if (values.sexe) {
                  patch.repCivilite = values.sexe.toUpperCase() === 'F' ? 'Mme' : 'M';
                }
                // 2026-06-21 (Cowork) — Robustesse : flag repCinUploaded des
                // que values.cin est present, meme sans archivedDocumentId.
                if (meta?.archivedDocumentId) {
                  patch.repCinUploaded = true;
                  patch.repCinFileName = `Archive Data Room ${meta.archivedDocumentId.slice(0, 8)}`;
                } else if (values.cin) {
                  patch.repCinUploaded = true;
                  patch.repCinFileName = `CIN ${values.cin.toUpperCase()}`;
                }
                if (Object.keys(patch).length > 0) onChange(patch);
              }}
            />
          </div>
        )}
        {/* 2026-06-21 (Cowork) — AssocUploadCard CIN du representant RETIREE :
            l'IdentityExtractor archive deja la piece. Affichage lecture seule
            pour controle humain de l'OCR. */}
        {a.repCinUploaded && (
          <div className="mb-3 flex items-start gap-2 rounded-md border border-success/30 bg-success/5 p-2 text-xs">
            <CheckCircle className="mt-0.5 h-3.5 w-3.5 shrink-0 text-success" />
            <div className="flex-1">
              <p className="font-semibold text-success">CIN du representant captee</p>
              <p className="text-fg-subtle">
                {a.repPrenom || a.repNom
                  ? `${a.repPrenom ?? ''} ${a.repNom ?? ''}`.trim() + (a.repCin ? ` — ${a.repCin}` : '')
                  : (a.repCin ? `Numero : ${a.repCin}` : 'Donnees extraites')}
                {a.repCinFileName ? ` · ${a.repCinFileName}` : ''}
              </p>
              {ro && (
                <p className="mt-1 text-[10px]">Reprise de l'etape 5 (dirigeant correspondant).</p>
              )}
            </div>
          </div>
        )}
        <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
          <div>
            <label className="mb-1 block text-xs text-fg-subtle">Civilite</label>
            <select
              value={a.repCivilite ?? 'M'}
              disabled={ro}
              onChange={(e) =>
                onChange({ repCivilite: e.target.value as 'M' | 'Mme' })
              }
              className="h-9 w-full rounded-lg border border-border bg-bg-raised px-3 text-sm disabled:opacity-60"
            >
              <option value="M">Monsieur</option>
              <option value="Mme">Madame</option>
            </select>
          </div>
          <div>
            <label className="mb-1 block text-xs text-fg-subtle">
              Nom <span className="text-danger">*</span>
            </label>
            <input aria-label="Nom"
              type="text"
              value={a.repNom ?? ''}
              disabled={ro}
              onChange={(e) => onChange({ repNom: e.target.value })}
              className="h-9 w-full rounded-lg border border-border bg-bg-raised px-3 text-sm disabled:opacity-60"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs text-fg-subtle">
              Prenom <span className="text-danger">*</span>
            </label>
            <input aria-label="Prenom"
              type="text"
              value={a.repPrenom ?? ''}
              disabled={ro}
              onChange={(e) => onChange({ repPrenom: e.target.value })}
              className="h-9 w-full rounded-lg border border-border bg-bg-raised px-3 text-sm disabled:opacity-60"
            />
          </div>
          {/* 2026-06-21 (Cowork) — Input CIN representant RETIRE : capte par
              l'IdentityExtractor (values.cin -> repCin). Numero visible en
              lecture seule dans la banniere "CIN captee" ci-dessus. */}
          {/* 2026-06-11 — Champs ajoutes : Nationalite, Date+Lieu naissance,
              CIN valable jusqu'au, Adresse (idem dirigeant MORALE). */}
          <div>
            <label className="mb-1 block text-xs text-fg-subtle">Nationalite</label>
            <input aria-label="Nationalite"
              type="text"
              value={a.repNationalite ?? ''}
              disabled={ro}
              onChange={(e) => onChange({ repNationalite: e.target.value })}
              placeholder="Marocaine"
              className="h-9 w-full rounded-lg border border-border bg-bg-raised px-3 text-sm disabled:opacity-60"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs text-fg-subtle">Date de naissance</label>
            <input aria-label="Date de naissance"
              type="date"
              value={a.repDateNaissance ?? ''}
              disabled={ro}
              onChange={(e) => onChange({ repDateNaissance: e.target.value })}
              className="h-9 w-full rounded-lg border border-border bg-bg-raised px-3 text-sm disabled:opacity-60"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs text-fg-subtle">Lieu de naissance</label>
            <input aria-label="Lieu de naissance"
              type="text"
              value={a.repLieuNaissance ?? ''}
              disabled={ro}
              onChange={(e) => onChange({ repLieuNaissance: e.target.value })}
              placeholder="Ville, pays"
              className="h-9 w-full rounded-lg border border-border bg-bg-raised px-3 text-sm disabled:opacity-60"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs text-fg-subtle">CIN valable jusqu'au</label>
            <input aria-label="CIN valable jusqu'au"
              type="date"
              value={a.repPieceValidite ?? ''}
              disabled={ro}
              onChange={(e) => onChange({ repPieceValidite: e.target.value })}
              className="h-9 w-full rounded-lg border border-border bg-bg-raised px-3 text-sm disabled:opacity-60"
            />
          </div>
          <div className="md:col-span-3">
            <label className="mb-1 block text-xs text-fg-subtle">
              Adresse <span className="text-danger">*</span>
            </label>
            <input aria-label="Adresse"
              type="text"
              value={a.repAdresse ?? ''}
              disabled={ro}
              onChange={(e) => onChange({ repAdresse: e.target.value })}
              placeholder="Adresse complete"
              className="h-9 w-full rounded-lg border border-border bg-bg-raised px-3 text-sm disabled:opacity-60"
            />
          </div>
          <div className="md:col-span-3">
            <label className="mb-1 block text-xs text-fg-subtle">
              Qualite <span className="text-danger">*</span>
            </label>
            <input aria-label="Qualite"
              type="text"
              value={a.repQualite ?? ''}
              disabled={ro}
              onChange={(e) => onChange({ repQualite: e.target.value })}
              placeholder="Gerant / President / Mandataire..."
              className="h-9 w-full rounded-lg border border-border bg-bg-raised px-3 text-sm disabled:opacity-60"
            />
          </div>
        </div>
      </div>
    </div>
  );
}

function AssocUploadCard({
  uploaded,
  filename,
  label,
  disabled,
  onFile,
}: {
  uploaded: boolean;
  filename?: string;
  label: string;
  disabled?: boolean;
  onFile: (file: File) => void;
}) {
  if (uploaded) {
    return (
      <div className="flex items-center gap-2 rounded-lg border border-success bg-success/10 p-3">
        <FileText className="h-4 w-4 text-success" />
        <span className="flex-1 truncate text-xs">{filename ?? label}</span>
        <CheckCircle className="h-4 w-4 text-success" />
        {!disabled && (
          <label className="cursor-pointer text-[11px] text-accent hover:underline">
            Remplacer
            <input
              type="file"
              accept="image/*,application/pdf"
              onChange={(ev) => ev.target.files?.[0] && onFile(ev.target.files[0])}
              className="hidden"
            />
          </label>
        )}
      </div>
    );
  }
  return (
    <label className={`block rounded-lg border-2 border-dashed p-4 text-center transition ${
      disabled
        ? 'cursor-not-allowed border-border text-fg-subtle opacity-60'
        : 'cursor-pointer border-danger/40 hover:border-danger hover:bg-danger/5'
    }`}>
      <Upload className="mx-auto mb-1 h-5 w-5 text-danger" />
      <p className="text-xs font-semibold text-fg">
        {label} <span className="text-danger">*</span>
      </p>
      <input
        type="file"
        accept="image/*,application/pdf"
        disabled={disabled}
        onChange={(ev) => ev.target.files?.[0] && onFile(ev.target.files[0])}
        className="hidden"
      />
    </label>
  );
}
