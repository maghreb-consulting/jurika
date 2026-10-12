/**
 * Lot L5 (E4) : operations de liquidation saisies pour le rapport final du liquidateur du
 * corpus (RAPPORT_LIQUIDATEUR_SARL / _SARL_AU). Onze tableaux en lignes ajoutables, rappel de
 * la dissolution, periode, diligences fiscales et sociales, signature.
 *
 * Les totaux, les plus ou moins-values, le solde de chaque creancier, la tresorerie, le boni
 * ou le mali et la quote-part de chaque associe se CALCULENT (miroir du serveur,
 * RapportLiquidateurCorpusVarsBuilder) : rien n'est saisi deux fois, rien n'est suppose. Les
 * montants se comptent en centimes entiers (aucune erreur d'arrondi).
 */

/** Une ligne d'un tableau : une valeur par colonne. */
export type Ligne = Record<string, string>;

export interface RapportLiquidateur {
  periodeDebut?: string;
  periodeFin?: string;
  dissolutionOrgane?: string;
  dissolutionDateDecision?: string;
  dissolutionMotifLibelle?: string;
  dissolutionDepotGreffeDate?: string;
  dissolutionRcInscriptionNumero?: string;
  dissolutionBoDateParution?: string;
  inventaireDateArrete?: string;
  prolongee?: string;
  prolongationMotifs?: string;
  declarationResultatProvisoireDate?: string;
  realisations?: Ligne[];
  apurements?: Ligne[];
  creancesIrrecouvrablesExistent?: string;
  creancesIrrecouvrables?: Ligne[];
  passifsContestesExistent?: string;
  passifsContestes?: Ligne[];
  passifsContestesMesures?: string;
  cessionsRestreintesExistent?: string;
  cessionsRestreintes?: Ligne[];
  frais?: Ligne[];
  fraisRembourses?: string;
  personnelExiste?: string;
  personnel?: Ligne[];
  chargesPersonnelTotal?: string;
  salariesDixOuPlus?: string;
  autorisationDemandeDate?: string;
  autorisationSuite?: string;
  contratsExistent?: string;
  contrats?: Ligne[];
  bauxExistent?: string;
  diligences?: Ligne[];
  tvaAssujettie?: string;
  tvaCessationDateLimite?: string;
  tpCessationDateLimite?: string;
  cnssRadiationDateLimite?: string;
  attestationsEtat?: string;
  retenueObservations?: string;
  maliObservations?: string;
  observationsGenerales?: string;
  resolutionsComplementaires?: string;
  annexes?: Ligne[];
  dateSignature?: string;
  lieuSignature?: string;
}

export type CleTableau =
  | 'realisations' | 'apurements' | 'creancesIrrecouvrables' | 'passifsContestes' | 'cessionsRestreintes'
  | 'frais' | 'personnel' | 'contrats' | 'diligences' | 'annexes';

export interface Colonne {
  cle: string;
  libelle: string;
  type: 'texte' | 'montant' | 'date';
  /** Colonne facultative (aucune ne l'est dans le modele, sauf mention). */
  facultative?: boolean;
  aide?: string;
}

const OBS = 'Écrivez « néant » s’il n’y a rien à signaler : le rapport imprime toujours cette rubrique.';

/** Colonnes de chaque tableau, dans l'ordre ou le rapport les imprime. */
export const COLONNES: Record<CleTableau, Colonne[]> = {
  realisations: [
    { cle: 'designation', libelle: 'Bien ou droit réalisé', type: 'texte' },
    { cle: 'mode', libelle: 'Mode de réalisation', type: 'texte', aide: 'Cession de gré à gré, vente aux enchères, recouvrement…' },
    { cle: 'acquereur', libelle: 'Acquéreur ou débiteur', type: 'texte' },
    { cle: 'prix', libelle: 'Prix ou montant encaissé (DH)', type: 'montant' },
    { cle: 'date', libelle: 'Date de réalisation', type: 'date' },
    { cle: 'valeurInventaire', libelle: 'Valeur à l’inventaire d’ouverture (DH)', type: 'montant' },
    { cle: 'observations', libelle: 'Observations', type: 'texte', aide: OBS },
  ],
  apurements: [
    { cle: 'creancier', libelle: 'Créancier', type: 'texte' },
    { cle: 'nature', libelle: 'Nature de la dette', type: 'texte' },
    { cle: 'montantAdmis', libelle: 'Montant admis (DH)', type: 'montant' },
    { cle: 'montantRegle', libelle: 'Montant réglé (DH)', type: 'montant' },
    { cle: 'dateReglement', libelle: 'Date du règlement', type: 'date' },
    { cle: 'modeReglement', libelle: 'Mode de règlement', type: 'texte' },
    { cle: 'observations', libelle: 'Observations', type: 'texte', aide: OBS },
  ],
  creancesIrrecouvrables: [
    { cle: 'debiteur', libelle: 'Débiteur', type: 'texte' },
    { cle: 'montant', libelle: 'Montant de la créance (DH)', type: 'montant' },
    { cle: 'diligences', libelle: 'Diligences de recouvrement accomplies', type: 'texte' },
    { cle: 'motif', libelle: 'Motif de l’irrécouvrabilité', type: 'texte' },
    { cle: 'suite', libelle: 'Suite proposée', type: 'texte' },
  ],
  passifsContestes: [
    { cle: 'creancier', libelle: 'Créancier', type: 'texte' },
    { cle: 'montantReclame', libelle: 'Montant réclamé (DH)', type: 'montant' },
    { cle: 'montantAdmis', libelle: 'Montant admis par le liquidateur (DH)', type: 'montant' },
    { cle: 'motif', libelle: 'Motif de la contestation', type: 'texte' },
    { cle: 'etat', libelle: 'État de la contestation', type: 'texte' },
    { cle: 'provision', libelle: 'Provision constituée (DH)', type: 'montant' },
  ],
  cessionsRestreintes: [
    { cle: 'bien', libelle: 'Bien cédé', type: 'texte' },
    { cle: 'cessionnaire', libelle: 'Cessionnaire', type: 'texte' },
    { cle: 'lien', libelle: 'Lien avec la société ou le liquidateur', type: 'texte' },
    { cle: 'article', libelle: 'Disposition applicable', type: 'texte', aide: 'Article 365 ou 366 de la loi n° 17-95.' },
    { cle: 'prix', libelle: 'Prix de cession (DH)', type: 'montant' },
    { cle: 'date', libelle: 'Date de la cession', type: 'date' },
    { cle: 'autorisationNature', libelle: 'Autorisation obtenue', type: 'texte', aide: 'Autorisation du tribunal ou consentement des associés.' },
    { cle: 'autorisationDate', libelle: 'Date de l’autorisation', type: 'date' },
    { cle: 'autorisationReference', libelle: 'Référence de l’autorisation', type: 'texte' },
  ],
  frais: [
    { cle: 'nature', libelle: 'Nature du frais ou de l’honoraire', type: 'texte' },
    { cle: 'beneficiaire', libelle: 'Bénéficiaire', type: 'texte' },
    { cle: 'montant', libelle: 'Montant (DH)', type: 'montant' },
    { cle: 'date', libelle: 'Date', type: 'date' },
    { cle: 'statut', libelle: 'État', type: 'texte', aide: 'Réglé ou à régler.' },
  ],
  personnel: [
    { cle: 'nom', libelle: 'Salarié', type: 'texte' },
    { cle: 'fonction', libelle: 'Fonction', type: 'texte' },
    { cle: 'sortContrat', libelle: 'Sort du contrat de travail', type: 'texte' },
    { cle: 'dateEffet', libelle: 'Date d’effet', type: 'date' },
    { cle: 'indemnites', libelle: 'Indemnités versées (DH)', type: 'montant' },
    { cle: 'soldeToutCompteDate', libelle: 'Reçu pour solde de tout compte délivré le', type: 'date' },
  ],
  contrats: [
    { cle: 'designation', libelle: 'Contrat', type: 'texte' },
    { cle: 'cocontractant', libelle: 'Cocontractant', type: 'texte' },
    { cle: 'sort', libelle: 'Sort du contrat', type: 'texte', aide: 'Poursuite, résiliation ou cession.' },
    { cle: 'dateEffet', libelle: 'Date d’effet', type: 'date' },
    { cle: 'indemnite', libelle: 'Indemnité ou solde (DH)', type: 'montant' },
  ],
  diligences: [
    { cle: 'nature', libelle: 'Diligence', type: 'texte' },
    { cle: 'organisme', libelle: 'Organisme destinataire', type: 'texte' },
    { cle: 'referenceTexte', libelle: 'Fondement', type: 'texte', aide: 'Texte qui impose la diligence (article du code général des impôts…).' },
    { cle: 'dateLimite', libelle: 'Date limite', type: 'date' },
    { cle: 'dateAccomplissement', libelle: 'Date d’accomplissement', type: 'date' },
    { cle: 'etat', libelle: 'État', type: 'texte' },
    { cle: 'observations', libelle: 'Observations', type: 'texte', aide: OBS },
  ],
  annexes: [
    { cle: 'designation', libelle: 'Pièce annexée', type: 'texte' },
  ],
};

/** Tableaux facultatifs : existence saisie (oui / non), jamais deduite d'une liste vide. */
export const EXISTENCE: Partial<Record<CleTableau, keyof RapportLiquidateur>> = {
  creancesIrrecouvrables: 'creancesIrrecouvrablesExistent',
  passifsContestes: 'passifsContestesExistent',
  cessionsRestreintes: 'cessionsRestreintesExistent',
  personnel: 'personnelExiste',
  contrats: 'contratsExistent',
};

export function ligneVide(t: CleTableau): Ligne {
  const l: Ligne = {};
  for (const c of COLONNES[t]) l[c.cle] = '';
  return l;
}

/** "12 500,50" ou "12500.5" : centimes ; vide ou illisible (negatif, trois decimales) : null. */
export function centimes(s: string | number | null | undefined): number | null {
  if (s === null || s === undefined) return null;
  const t = String(s).replace(/[\s_]/g, '').replace(',', '.');  // \s couvre les espaces insecables
  if (!t) return null;
  if (!/^\d+(\.\d{1,2})?$/.test(t)) return null;
  const [e, d = ''] = t.split('.');
  return Number(e) * 100 + Number((d + '00').slice(0, 2));
}

/** 1250050 -> "12 500,50" ; 1250000 -> "12 500". */
export function formatDh(c: number): string {
  const neg = c < 0;
  const a = Math.abs(c);
  const e = Math.floor(a / 100);
  const d = a % 100;
  const entier = String(e).replace(/\B(?=(\d{3})+(?!\d))/g, ' ');
  return `${neg ? '-' : ''}${entier}${d ? `,${String(d).padStart(2, '0')}` : ''}`;
}

const vide = (s: string | undefined) => !(s ?? '').trim();

/** Lignes non entierement vides. */
export function lignesSaisies(l: Ligne[] | undefined): Ligne[] {
  return (l ?? []).filter((x) => Object.values(x).some((v) => !vide(v)));
}

function somme(l: Ligne[] | undefined, cle: string): number | null {
  let s = 0;
  for (const x of lignesSaisies(l)) {
    const c = centimes(x[cle]);
    if (c === null) return null;
    s += c;
  }
  return s;
}

/** Contexte du calcul : donnees deja saisies ailleurs dans le parcours. */
export interface ContexteRapport {
  /** Capital social (DH). */
  capital?: string | number | null;
  /** Remuneration du liquidateur (DH) si elle est applicable, sinon 0 ou vide. */
  remuneration?: string | number | null;
  /** Impot sur les societes de la liquidation (PV de cloture), facultatif. */
  impot?: string | number | null;
  /** Totaux des comptes finaux de l'etape 1 et frais du PV de cloture. */
  totalActif?: string | number | null;
  totalPassif?: string | number | null;
  fraisPv?: string | number | null;
}

export interface CalculRapport {
  actif: number | null;
  passif: number | null;
  frais: number | null;
  passifRestant: number | null;
  tresorerie: number | null;
  resultat: { sens: 'boni' | 'mali' | 'nul'; montant: number } | null;
}

/** Totaux et resultat, miroir du serveur ; null quand une donnee du calcul manque. */
export function calculer(r: RapportLiquidateur, ctx: ContexteRapport): CalculRapport {
  const actif = somme(r.realisations, 'prix');
  const passif = somme(r.apurements, 'montantRegle');
  const admis = somme(r.apurements, 'montantAdmis');
  const frais = somme(r.frais, 'montant');
  const passifRestant = admis === null || passif === null ? null : admis - passif;
  const impot = vide(String(ctx.impot ?? '')) ? 0 : centimes(ctx.impot);
  const capital = centimes(ctx.capital);
  let tresorerie: number | null = null;
  if (actif !== null && passif !== null && frais !== null && impot !== null) tresorerie = actif - passif - frais - impot;
  let resultat: CalculRapport['resultat'] = null;
  if (tresorerie !== null && capital !== null && tresorerie >= 0) {
    const ecart = tresorerie - capital;
    resultat = { sens: ecart > 0 ? 'boni' : ecart < 0 ? 'mali' : 'nul', montant: Math.abs(ecart) };
  }
  return { actif, passif, frais, passifRestant, tresorerie, resultat };
}

/**
 * Ce qui manque ou ne concorde pas pour rediger le rapport final (messages nommes). Le
 * serveur fait le meme controle et refuse la generation en nommant les memes donnees.
 */
export function manquesRapport(r: RapportLiquidateur, ctx: ContexteRapport, isAU: boolean): string[] {
  const m: string[] = [];
  const exiger = (v: string | undefined, msg: string) => { if (vide(v)) m.push(msg); };
  exiger(r.periodeDebut, 'Indiquez le début de la période couverte par le rapport.');
  exiger(r.periodeFin, 'Indiquez la fin de la période couverte par le rapport.');
  if (!isAU) exiger(r.dissolutionOrgane, 'Indiquez l’organe qui a décidé la dissolution.');
  exiger(r.dissolutionDateDecision, 'Indiquez la date de la décision de dissolution.');
  exiger(r.dissolutionMotifLibelle, 'Indiquez le motif de la dissolution, rédigé en clair.');
  exiger(r.prolongee, 'Indiquez si la liquidation s’est prolongée au-delà de la période prévue.');
  if (r.prolongee === 'oui') {
    exiger(r.prolongationMotifs, 'Indiquez les causes de la prolongation de la liquidation.');
    exiger(r.declarationResultatProvisoireDate, 'Indiquez la date de la déclaration du résultat provisoire.');
  }
  const tableau = (t: CleTableau, nom: string) => {
    const lignes = lignesSaisies(r[t] as Ligne[] | undefined);
    lignes.forEach((l, i) => {
      for (const c of COLONNES[t]) {
        if (c.facultative) continue;
        if (vide(l[c.cle])) m.push(`${nom}, ligne ${i + 1} : renseignez « ${c.libelle} ».`);
        else if (c.type === 'montant' && centimes(l[c.cle]) === null) {
          m.push(`${nom}, ligne ${i + 1} : « ${c.libelle} » n’est pas un montant en dirhams (au plus deux décimales).`);
        }
      }
    });
    return lignes;
  };
  tableau('realisations', 'Réalisation de l’actif');
  tableau('apurements', 'Apurement du passif');
  for (const [t, nom] of [
    ['creancesIrrecouvrables', 'Créances irrécouvrables'], ['passifsContestes', 'Passifs contestés'],
    ['cessionsRestreintes', 'Cessions soumises à restriction'], ['personnel', 'Personnel'], ['contrats', 'Contrats en cours'],
  ] as [CleTableau, string][]) {
    const cle = EXISTENCE[t] as keyof RapportLiquidateur;
    const existe = r[cle] as string | undefined;
    if (vide(existe)) { m.push(`${nom} : répondez par oui ou par non.`); continue; }
    if (existe !== 'oui') continue;
    if (tableau(t, nom).length === 0) m.push(`${nom} : ajoutez au moins une ligne, ou répondez « non ».`);
  }
  if (r.passifsContestesExistent === 'oui') exiger(r.passifsContestesMesures, 'Indiquez les mesures de consignation ou de provision retenues pour les passifs contestés.');
  if (r.personnelExiste === 'oui') {
    exiger(r.chargesPersonnelTotal, 'Indiquez le total des salaires, indemnités et charges sociales acquittés.');
    exiger(r.salariesDixOuPlus, 'Indiquez si la société occupait habituellement dix salariés ou plus.');
    if (r.salariesDixOuPlus === 'oui') {
      exiger(r.autorisationDemandeDate, 'Indiquez la date de la demande d’autorisation de fermeture.');
      exiger(r.autorisationSuite, 'Indiquez la suite donnée à la demande d’autorisation de fermeture.');
    }
  }
  if (r.contratsExistent === 'oui') exiger(r.bauxExistent, 'Indiquez si des baux d’immeubles figurent parmi les contrats en cours.');
  tableau('frais', 'Frais et honoraires');
  if (vide(r.fraisRembourses)) m.push('Indiquez les frais remboursés au liquidateur sur justificatifs (0 s’il n’y en a pas).');
  else if (centimes(r.fraisRembourses) === null) m.push('Les frais remboursés au liquidateur ne sont pas un montant en dirhams.');
  if (tableau('diligences', 'Diligences fiscales et sociales').length === 0) {
    m.push('Ajoutez au moins une diligence fiscale ou sociale accomplie : le rapport en rend compte.');
  }
  exiger(r.tvaAssujettie, 'Indiquez si la société est assujettie à la TVA.');
  if (r.tvaAssujettie === 'oui') exiger(r.tvaCessationDateLimite, 'Indiquez la date limite de la déclaration de cessation en matière de TVA.');
  exiger(r.attestationsEtat, 'Indiquez l’état des attestations fiscales et sociales.');
  exiger(r.dateSignature, 'Indiquez la date du rapport.');

  // Concordance avec le PV de cloture et resultat.
  const k = calculer(r, ctx);
  const concorde = (attendu: string | number | null | undefined, calcule: number | null, libelle: string, source: string) => {
    const a = centimes(attendu);
    if (a !== null && calcule !== null && a !== calcule) {
      m.push(`${libelle} (${formatDh(calcule)} DH) différent du ${source} (${formatDh(a)} DH).`);
    }
  };
  concorde(ctx.totalActif, k.actif, 'Total des réalisations de l’actif', 'total de l’actif de l’étape 1');
  concorde(ctx.totalPassif, k.passif, 'Total du passif réglé', 'total du passif de l’étape 1');
  const remuneration = centimes(ctx.remuneration) ?? 0;
  const fraisPv = centimes(ctx.fraisPv);
  if (fraisPv !== null && k.frais !== null && fraisPv + remuneration !== k.frais) {
    m.push(`Total des frais et honoraires (${formatDh(k.frais)} DH) différent des frais du procès-verbal augmentés de la rémunération du liquidateur (${formatDh(fraisPv + remuneration)} DH).`);
  }
  if (k.frais !== null && k.frais < remuneration) {
    m.push('La rémunération du liquidateur doit figurer au tableau des frais et honoraires, dont le total la comprend.');
  }
  if (k.passifRestant !== null && k.passifRestant > 0) {
    m.push(`Il reste ${formatDh(k.passifRestant)} DH de passif admis non réglé : le rapport final déclare tout le passif connu acquitté (déclarez les prétentions contestées au tableau des passifs contestés).`);
  }
  if (k.tresorerie !== null && k.tresorerie < 0) {
    m.push('L’actif réalisé ne couvre pas le passif, les frais et les impôts : vérifiez les tableaux.');
  }
  if (k.resultat?.sens === 'nul') {
    m.push('Résultat nul : le modèle du rapport du cabinet ne prévoit qu’un boni ou un mali (point à décider par le cabinet).');
  }
  return m;
}

/** Charge utile transmise au serveur (bloc `rapportLiquidateur`) : lignes vides retirees. */
export function rapportPayload(r: RapportLiquidateur): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(r)) {
    if (Array.isArray(v)) {
      const lignes = lignesSaisies(v as Ligne[]).map((l) => {
        const t: Ligne = {};
        for (const [c, x] of Object.entries(l)) t[c] = (x ?? '').trim();
        return t;
      });
      out[k] = k === 'annexes' ? lignes.map((l) => l.designation) : lignes;
    } else if (typeof v === 'string' && v.trim()) {
      out[k] = v.trim();
    }
  }
  return out;
}
