import { MAINTENANT } from '../constantes';
import { PROFILS, WS_DEMO, WS_ESSAI, type Profil } from './session';
import { reponse, type Contexte, type RouteSimulee } from './routeur';

/**
 * Lot L6b (E4) : jeux de donnees simules, DETERMINISTES (aucun hasard, dates calculees a
 * partir de l'instant fige MAINTENANT) et FICTIFS : cabinet "Atlas Conseil", societes,
 * personnes et numeros inventes pour la demonstration (aucune donnee reelle). Les prix
 * sont ceux du cahier des charges (4.3). Les formes suivent les types du front
 * (src/types, src/services) ; un champ ajoute par le serveur et absent ici reste absent.
 */

// ---------------------------------------------------------------- dates

const JOUR = 86_400_000;
/** Date ISO (aaaa-mm-jj) a n jours de MAINTENANT. */
export function jour(n: number): string {
  return new Date(MAINTENANT.getTime() + n * JOUR).toISOString().slice(0, 10);
}
/** Instant ISO a n jours (et h heures) de MAINTENANT. */
export function instant(n: number, h = 0): string {
  return new Date(MAINTENANT.getTime() + n * JOUR + h * 3_600_000).toISOString();
}

// ---------------------------------------------------------------- identifiants

export const IDS = {
  wsDemo: WS_DEMO,
  wsEssai: WS_ESSAI,
  ticketCreation: '70000000-0000-4000-8000-000000000001',
  dossierAtlas: 'f0000000-0000-4000-8000-000000000001',
};

const P = PROFILS;
const EMPLOYE2 = { userId: 'b0000000-0000-4000-8000-000000000003', prenom: 'Omar', nom: 'Berrada', email: 'o.berrada@atlas-conseil.jurika.ma' };
const EMPLOYE3 = { userId: 'b0000000-0000-4000-8000-000000000004', prenom: 'Houda', nom: 'El Fassi', email: 'h.elfassi@atlas-conseil.jurika.ma' };

// ---------------------------------------------------------------- utilisateurs

function utilisateur(id: string, prenom: string, nom: string, email: string, role: 'EMPLOYE' | 'SUPERVISEUR' | 'CLIENT', statut = 'ACTIVE', connexion: string | null = instant(-1)) {
  return {
    userId: id,
    email,
    firstName: prenom,
    lastName: nom,
    phone: '+212 6 00 00 00 00',
    role,
    status: statut,
    mustChangePassword: false,
    lastLoginAt: connexion,
    createdAt: instant(-200),
  };
}

const EQUIPE = [
  utilisateur(P.superviseur.userId, P.superviseur.prenom, P.superviseur.nom, P.superviseur.email, 'SUPERVISEUR'),
  utilisateur(P.employe.userId, P.employe.prenom, P.employe.nom, P.employe.email, 'EMPLOYE'),
  utilisateur(EMPLOYE2.userId, EMPLOYE2.prenom, EMPLOYE2.nom, EMPLOYE2.email, 'EMPLOYE'),
  utilisateur(EMPLOYE3.userId, EMPLOYE3.prenom, EMPLOYE3.nom, EMPLOYE3.email, 'EMPLOYE', 'PENDING', null),
];
const ANNUAIRE = [...EQUIPE, utilisateur(P.client.userId, P.client.prenom, P.client.nom, P.client.email, 'CLIENT')];

// ---------------------------------------------------------------- dossiers

interface DossierFictif {
  id: string;
  raisonSociale: string;
  reference: string;
  formeJuridique: string;
  ice: string | null;
  ville: string;
  statut: string;
  responsableId: string;
}

const DOSSIERS: DossierFictif[] = [
  { id: IDS.dossierAtlas, raisonSociale: 'Atlas Distribution', reference: 'DOS-2026-0012', formeJuridique: 'SARL', ice: '000000000000012', ville: 'Casablanca', statut: 'ACTIF', responsableId: P.employe.userId },
  { id: 'f0000000-0000-4000-8000-000000000002', raisonSociale: 'Oasis Agro', reference: 'DOS-2026-0013', formeJuridique: 'SARL_AU', ice: '000000000000013', ville: 'Marrakech', statut: 'ACTIF', responsableId: P.employe.userId },
  { id: 'f0000000-0000-4000-8000-000000000003', raisonSociale: 'Rif Logistique', reference: 'DOS-2026-0014', formeJuridique: 'SARL', ice: null, ville: 'Tanger', statut: 'EN_COURS', responsableId: P.employe.userId },
  { id: 'f0000000-0000-4000-8000-000000000004', raisonSociale: 'Saharienne Immobilier', reference: 'DOS-2025-0041', formeJuridique: 'SARL', ice: '000000000000041', ville: 'Agadir', statut: 'CLOTURE', responsableId: EMPLOYE2.userId },
  { id: 'f0000000-0000-4000-8000-000000000005', raisonSociale: 'Medina Tech', reference: 'DOS-2026-0015', formeJuridique: 'SARL_AU', ice: '000000000000015', ville: 'Fes', statut: 'ACTIF', responsableId: EMPLOYE2.userId },
  { id: 'f0000000-0000-4000-8000-000000000006', raisonSociale: 'Cooperative Argane du Souss', reference: 'DOS-2026-0016', formeJuridique: 'SARL', ice: '000000000000016', ville: 'Taroudant', statut: 'ACTIF', responsableId: P.employe.userId },
];

function dossierBrief(d: DossierFictif) {
  return {
    id: d.id,
    raisonSociale: d.raisonSociale,
    reference: d.reference,
    formeJuridique: d.formeJuridique,
    ice: d.ice,
    ville: d.ville,
    statut: d.statut,
    accessStatus: 'ACTIVE',
    dateDissolution: null,
    origine: 'MAROCAINE',
    pays: null,
    formeJuridiqueOrigine: null,
  };
}

function dossiersDu(ctx: Contexte) {
  const p = ctx.profil;
  if (!p) return [];
  if (p.cabinet === 'essai') return [dossierBrief({ ...DOSSIERS[0], id: 'f1000000-0000-4000-8000-000000000001', raisonSociale: 'Societe d essai', reference: 'DOS-2026-0001' })];
  if (p.role === 'EMPLOYE') return DOSSIERS.filter((d) => d.responsableId === p.userId).map(dossierBrief);
  if (p.role === 'CLIENT') return [dossierBrief(DOSSIERS[0])];
  return DOSSIERS.map(dossierBrief);
}

// ---------------------------------------------------------------- tickets

interface TicketFictif {
  id: string;
  reference: string;
  titre: string;
  type: string;
  statut: string;
  priorite: string;
  dossier: number | null;
  assigne: { userId: string; prenom: string; nom: string } | null;
  echeance: number | null;
  cree: number;
}

const TICKETS: TicketFictif[] = [
  { id: IDS.ticketCreation, reference: 'TK-2026-0101', titre: 'Creation SARL Atlas Distribution', type: 'CREATION', statut: 'CREATION_TICKET', priorite: 'HAUTE', dossier: 0, assigne: P.employe, echeance: 3, cree: -6 },
  { id: '70000000-0000-4000-8000-000000000002', reference: 'TK-2026-0102', titre: 'Transfert du siege social Oasis Agro', type: 'MODIFICATION', statut: 'GENERATION_DOCUMENTS', priorite: 'NORMALE', dossier: 1, assigne: P.employe, echeance: -4, cree: -20 },
  { id: '70000000-0000-4000-8000-000000000003', reference: 'TK-2026-0103', titre: 'Approbation des comptes 2025 Rif Logistique', type: 'PV_AGO', statut: 'DEROULEMENT_DEMARCHE', priorite: 'URGENTE', dossier: 2, assigne: P.employe, echeance: 1, cree: -30 },
  { id: '70000000-0000-4000-8000-000000000004', reference: 'TK-2026-0104', titre: 'Dissolution anticipee Saharienne Immobilier', type: 'DISSOLUTION', statut: 'CLOTURE_DOSSIER', priorite: 'BASSE', dossier: 3, assigne: EMPLOYE2, echeance: null, cree: -60 },
  { id: '70000000-0000-4000-8000-000000000005', reference: 'TK-2026-0105', titre: 'Cession de parts Medina Tech', type: 'MODIFICATION', statut: 'CREATION_TICKET', priorite: 'NORMALE', dossier: 4, assigne: EMPLOYE2, echeance: 45, cree: -2 },
  { id: '70000000-0000-4000-8000-000000000006', reference: 'TK-2026-0106', titre: 'Augmentation de capital Cooperative Argane du Souss', type: 'MODIFICATION', statut: 'DEROULEMENT_DEMARCHE', priorite: 'HAUTE', dossier: 5, assigne: P.employe, echeance: 30, cree: -12 },
  { id: '70000000-0000-4000-8000-000000000007', reference: 'TK-2026-0107', titre: 'Import du dossier Medina Tech', type: 'IMPORT', statut: 'ANNULE', priorite: 'BASSE', dossier: 4, assigne: EMPLOYE2, echeance: null, cree: -40 },
  { id: '70000000-0000-4000-8000-000000000008', reference: 'TK-2026-0108', titre: 'Ouverture d une succursale a Rabat', type: 'SUCCURSALE_MA', statut: 'GENERATION_DOCUMENTS', priorite: 'NORMALE', dossier: 0, assigne: null, echeance: 12, cree: -1 },
];

function ticketVue(t: TicketFictif) {
  const d = t.dossier === null ? null : DOSSIERS[t.dossier];
  return {
    id: t.id,
    workspaceId: WS_DEMO,
    reference: t.reference,
    titre: t.titre,
    type: t.type,
    statut: t.statut,
    priorite: t.priorite,
    dossierId: d?.id ?? null,
    assigneId: t.assigne?.userId ?? null,
    creeParId: P.superviseur.userId,
    description: 'Dossier suivi par le cabinet. Pieces du client recues le ' + jour(t.cree + 1) + '.',
    deadline: t.echeance === null ? null : instant(t.echeance),
    annulationMotif: t.statut === 'ANNULE' ? 'Doublon du ticket TK-2026-0105' : null,
    repriseMotif: null,
    clotureAt: t.statut === 'CLOTURE_DOSSIER' ? instant(-3) : null,
    annuleAt: t.statut === 'ANNULE' ? instant(-35) : null,
    createdAt: instant(t.cree),
    transferred: false,
    assignePrenom: t.assigne?.prenom ?? null,
    assigneNom: t.assigne?.nom ?? null,
  };
}

function ticketsDu(ctx: Contexte) {
  const p = ctx.profil;
  if (!p || p.cabinet !== 'demo') return [];
  const liste = p.role === 'EMPLOYE' ? TICKETS.filter((t) => t.assigne?.userId === p.userId) : TICKETS;
  return liste.map(ticketVue);
}

function page<T>(items: T[], q: URLSearchParams) {
  const limit = Number(q.get('limit') ?? q.get('size') ?? 50) || 50;
  const offset = Number(q.get('offset') ?? 0) || 0;
  return { items: items.slice(offset, offset + limit), total: items.length, limit, offset };
}

// ---------------------------------------------------------------- echeances

const ECHEANCES = [
  { id: 'e0000000-0000-4000-8000-000000000001', dossier: 1, libelle: 'Depot au greffe du PV de transfert du siege', jours: -4, severite: 'CRITICAL' },
  { id: 'e0000000-0000-4000-8000-000000000002', dossier: 2, libelle: 'Depot des comptes annuels au tribunal de commerce', jours: 1, severite: 'CRITICAL' },
  { id: 'e0000000-0000-4000-8000-000000000003', dossier: 0, libelle: 'Immatriculation au registre du commerce', jours: 3, severite: 'WARNING' },
  { id: 'e0000000-0000-4000-8000-000000000004', dossier: 5, libelle: 'Publication de l augmentation de capital', jours: 30, severite: 'INFO' },
  { id: 'e0000000-0000-4000-8000-000000000005', dossier: 4, libelle: 'Declaration CNSS des nouveaux associes', jours: 75, severite: 'INFO' },
];

function echeanceItem(e: (typeof ECHEANCES)[number]) {
  return { id: e.id, dossierId: DOSSIERS[e.dossier].id, libelle: e.libelle, dateEcheance: jour(e.jours), severite: e.severite };
}

// ---------------------------------------------------------------- facturation et contrat

const FONCTIONS = [
  { code: 'DATA_ROOM', libelle: 'Data Room client', incluse: true, disponibleDes: 'ESSENTIEL' },
  { code: 'CHATBOT', libelle: 'Assistant juridique (chatbot)', incluse: true, disponibleDes: 'ESSENTIEL' },
  { code: 'EXTRACTION', libelle: 'Lecture des pieces d identite', incluse: true, disponibleDes: 'PROFESSIONNEL' },
  { code: 'TABLEAU_SUPERVISION', libelle: 'Tableau de bord de supervision', incluse: false, disponibleDes: 'MAX' },
];

function montants(htCentimes: number) {
  const tva = Math.round(htCentimes * 0.2);
  return { htCentimes, tauxTva: 20, tvaCentimes: tva, ttcCentimes: htCentimes + tva };
}

function abonnement(ctx: Contexte) {
  const essai = ctx.profil?.cabinet === 'essai';
  return {
    workspaceId: ctx.profil?.workspaceId ?? WS_DEMO,
    statut: essai ? 'AUCUN' : 'ACTIF',
    forfait: essai ? null : 'professionnel',
    forfaitLibelle: essai ? null : 'Professionnel',
    rythme: essai ? null : 'yearly',
    engagementDebut: essai ? null : jour(-120),
    engagementFin: essai ? null : jour(245),
    periodeDebut: essai ? null : jour(-120),
    periodeFin: essai ? null : jour(245),
    prochaineEcheance: essai
      ? null
      : { debut: jour(245), fin: jour(610), forfait: 'professionnel', forfaitLibelle: 'Professionnel', rythme: 'yearly', montants: montants(750_000), motif: null },
    changementAVenir: null,
    archiveMotif: null,
    fonctions: FONCTIONS,
    carteDisponible: false,
    carteIndisponibleMotif: 'Le paiement par carte (CMI) n est pas encore ouvert.',
    paiementsEnAttente: 0,
    impaye: null,
  };
}

const CONDITIONS = {
  id: 'cc000000-0000-4000-8000-000000000001',
  reference: 'CP-2026-0007',
  forfait: 'PROFESSIONNEL',
  forfaitLibelle: 'Professionnel',
  rythmePaiement: 'ANNUEL',
  prixHt: 7500,
  dateDebut: jour(-120),
  dateFin: jour(245),
  echeanceCourante: jour(245),
  modePaiement: 'VIREMENT',
  statut: 'ACCEPTE',
  origine: 'ABONNEMENT',
  creeLe: instant(-121),
};
const VERSION = {
  id: '5e000000-0000-4000-8000-000000000001',
  code: 'V1',
  entreeEnVigueur: jour(-200),
  empreinteModele: 'a1b2c3d4e5f6',
  empreinteCg: 'b2c3d4e5f6a1',
  empreinteDpa: 'c3d4e5f6a1b2',
  origine: 'INITIALE',
  publieeLe: instant(-200),
  retiree: false,
};
const ACCEPTATION = {
  id: 'ac000000-0000-4000-8000-000000000001',
  conditionsId: CONDITIONS.id,
  objet: 'CONDITIONS_PARTICULIERES',
  versionCode: 'V1',
  empreinteContrat: 'd4e5f6a1b2c3',
  signataire: 'M. Karim Alaoui',
  signataireQualite: 'Gerant',
  signataireCompte: P.superviseur.email,
  accepteLeCasablanca: instant(-120),
  adresseIp: '192.0.2.10',
  cabinetEmail: 'contact@atlas-conseil.example',
};

function etatContrat(ctx: Contexte) {
  if (ctx.profil?.role === 'EMPLOYE' || ctx.profil?.role === 'CLIENT') return { statut: 'ACCEPTE', acceptationRequise: false };
  return {
    statut: 'ACCEPTE',
    acceptationRequise: false,
    objetAAccepter: null,
    enAttente: null,
    courant: CONDITIONS,
    derniereAcceptation: ACCEPTATION,
    resiliation: null,
    versionEnVigueur: VERSION,
    versionAVenir: null,
    joursAvantEcheance: 245,
    avertissementEcheance: false,
    reprise: null,
  };
}

const TIERS = [
  { id: 'tier-1', code: 'essentiel', label: 'Essentiel', rang: 1, quotas: { maxUsers: 2, maxDossiers: 25, maxStorageGb: 5 }, mensuel: 375, annuel: 3750 },
  { id: 'tier-2', code: 'professionnel', label: 'Professionnel', rang: 2, quotas: { maxUsers: 3, maxDossiers: 50, maxStorageGb: 10 }, mensuel: 750, annuel: 7500 },
  { id: 'tier-3', code: 'max', label: 'Max', rang: 3, quotas: { maxUsers: 5, maxDossiers: 100, maxStorageGb: 25 }, mensuel: 1500, annuel: 15000 },
].map((t) => ({
  id: t.id,
  code: t.code,
  label: t.label,
  rang: t.rang,
  currency: 'MAD',
  quoteOnly: false,
  monthly: { priceMad: t.mensuel },
  yearly: { priceMad: t.annuel },
  totalAnnuelEnMensuelMad: t.mensuel * 12,
  quotas: { ...t.quotas, usersUnlimited: false, dossiersUnlimited: false, storageUnlimited: false },
  fonctions: FONCTIONS.map((f) => ({ code: f.code, libelle: f.libelle, incluse: f.disponibleDes === 'ESSENTIEL' || t.rang >= (f.disponibleDes === 'PROFESSIONNEL' ? 2 : 3) })),
}));
const PRICING = {
  tiers: [
    ...TIERS,
    {
      id: 'tier-4',
      code: 'sur_devis',
      label: 'Sur Devis',
      rang: 4,
      currency: 'MAD',
      quoteOnly: true,
      quotas: { maxUsers: 0, maxDossiers: 0, maxStorageGb: 0, usersUnlimited: true, dossiersUnlimited: true, storageUnlimited: true },
      fonctions: FONCTIONS.map((f) => ({ code: f.code, libelle: f.libelle, incluse: true })),
    },
  ],
  currency: 'MAD',
  prixHorsTaxes: true,
};

function paiement(id: number, statut: string, jours: number, objet = 'ECHEANCE') {
  return {
    id,
    workspaceId: WS_DEMO,
    objet,
    forfait: 'professionnel',
    forfaitLibelle: 'Professionnel',
    rythme: 'yearly',
    mode: 'BANK_TRANSFER',
    statut,
    montants: montants(750_000),
    periodeDebut: jour(jours),
    periodeFin: jour(jours + 365),
    reference: statut === 'COMPLETED' ? `VIR-2026-${String(id).padStart(4, '0')}` : null,
    recuLe: statut === 'COMPLETED' ? jour(jours) : null,
    creeLe: instant(jours - 2),
    acquisLe: statut === 'COMPLETED' ? instant(jours) : null,
    factureId: statut === 'COMPLETED' ? id : null,
    factureNumero: statut === 'COMPLETED' ? `FAC-2026-${String(id).padStart(5, '0')}` : null,
    factureRefus: null,
    activationPropagee: statut === 'COMPLETED',
    notes: null,
  };
}

function facture(id: number, jours: number) {
  return {
    id,
    numero: `FAC-2026-${String(id).padStart(5, '0')}`,
    workspaceId: WS_DEMO,
    cabinet: 'Atlas Conseil',
    dateEmission: jour(jours),
    designation: 'Abonnement JURIKA, forfait Professionnel, paiement annuel',
    periodeDebut: jour(jours),
    periodeFin: jour(jours + 365),
    mode: 'BANK_TRANSFER',
    montants: montants(750_000),
  };
}

function etatEssai(ctx: Contexte) {
  if (ctx.profil?.cabinet !== 'essai') {
    return {
      enEssai: false,
      statut: 'CONVERTED',
      debut: jour(-140),
      fin: jour(-126),
      joursRestants: 0,
      rappel: null,
      lectureSeule: false,
      lectureSeuleDepuis: null,
      suppressionLe: null,
      avertissementLe: null,
      prolongeLe: null,
      prolongeable: false,
      limites: null,
    };
  }
  return {
    enEssai: true,
    statut: 'TRIAL_ACTIVE',
    debut: jour(-11),
    fin: jour(3),
    joursRestants: 3,
    rappel: 'J-3',
    lectureSeule: false,
    lectureSeuleDepuis: null,
    suppressionLe: null,
    avertissementLe: null,
    prolongeLe: null,
    prolongeable: true,
    limites: { dossiers: 3, superviseurs: 1, employes: 1, stockageOctets: 524_288_000, questionsChatbot: 20, forfait: 'ESSENTIEL' },
  };
}

function profilCabinet(ctx: Contexte) {
  const essai = ctx.profil?.cabinet === 'essai';
  return {
    name: essai ? 'Cabinet Chraibi (essai)' : 'Atlas Conseil',
    professionalType: 'CONSEIL_JURIDIQUE',
    ice: essai ? null : '000000000000099',
    city: 'Casablanca',
    contactEmail: essai ? 'm.chraibi@example.com' : 'contact@atlas-conseil.example',
    iceMissing: essai,
    nomAfficheDocuments: null,
    documentDisplayName: essai ? 'Cabinet Chraibi' : 'Atlas Conseil',
    adresse: '12, boulevard d Anfa, Casablanca',
    telephone: '+212 5 22 00 00 00',
    siteWeb: null,
    rcNumber: essai ? null : '000099',
    ifFiscal: essai ? null : '00000099',
    hasLogo: false,
  };
}

function usage(ctx: Contexte) {
  const essai = ctx.profil?.cabinet === 'essai';
  return {
    planCode: essai ? 'essai' : 'professionnel',
    planLabel: essai ? 'Essai gratuit' : 'Professionnel',
    users: essai ? 1 : 4,
    maxUsers: essai ? 2 : 3,
    usersUnlimited: false,
    dossiers: essai ? 1 : 6,
    maxDossiers: essai ? 3 : 50,
    dossiersUnlimited: false,
    storageBytes: essai ? 12_582_912 : 2_684_354_560,
    maxStorageGb: essai ? 0.5 : 10,
    storageUnlimited: false,
    forfaitDefini: true,
    periodeDebut: essai ? jour(-11) : jour(-120),
    periodeFin: essai ? jour(3) : jour(245),
    storageMesure: true,
  };
}

// ---------------------------------------------------------------- notifications, messagerie

function notifications(p: Profil | null) {
  if (!p) return [];
  const base = (id: number, type: string, titre: string, message: string, jours: number, lue: boolean, lien: string | null) => ({
    id: `40000000-0000-4000-8000-00000000000${id}`,
    workspaceId: p.workspaceId,
    userId: p.userId,
    type,
    title: titre,
    message,
    actionUrl: lien,
    metadata: null,
    readAt: lue ? instant(jours, 1) : null,
    createdAt: instant(jours),
  });
  return [
    base(1, 'DEADLINE_DUE', 'Echeance depassee', 'Depot au greffe du PV de transfert du siege (Oasis Agro) : echeance depassee de 4 jours.', -0.1, false, `/tickets/${TICKETS[1].id}`),
    base(2, 'TICKET_ASSIGNED', 'Nouveau ticket', 'Le ticket TK-2026-0101 vous est attribue.', -1, false, `/tickets/${IDS.ticketCreation}`),
    base(3, 'DEMANDE_CLIENT', 'Demande du client', 'Atlas Distribution a envoye une demande : attestation de domiciliation.', -2, true, '/data-rooms'),
    base(4, 'CHAT_MESSAGE', 'Message de Karim Alaoui', 'Pouvez-vous verifier les statuts avant la signature ?', -3, true, '/chat'),
  ];
}

function conversations(p: Profil | null) {
  if (!p || p.role === 'CLIENT' || p.role === 'SUPER_ADMIN') return [];
  const pair = p.role === 'EMPLOYE' ? P.superviseur.userId : P.employe.userId;
  return [
    { id: 'c5000000-0000-4000-8000-000000000001', peerId: pair, workspaceId: p.workspaceId, lastMessage: 'Pouvez-vous verifier les statuts avant la signature ?', lastMessageAt: instant(-0.2), unreadCount: 1 },
    { id: 'c5000000-0000-4000-8000-000000000002', peerId: EMPLOYE2.userId, workspaceId: p.workspaceId, lastMessage: 'Le PV est pret pour relecture.', lastMessageAt: instant(-2), unreadCount: 0 },
  ];
}

function messages(p: Profil | null, conv: string) {
  if (!p) return [];
  const pair = p.role === 'EMPLOYE' ? P.superviseur.userId : P.employe.userId;
  return [
    { id: 'm1', conversationId: conv, from: pair, to: p.userId, body: 'Bonjour, ou en est la creation de la SARL Atlas Distribution ?', createdAt: instant(-0.4) },
    { id: 'm2', conversationId: conv, from: p.userId, to: pair, body: 'Les statuts sont rediges ; il manque le certificat negatif.', createdAt: instant(-0.35) },
    { id: 'm3', conversationId: conv, from: pair, to: p.userId, body: 'Pouvez-vous verifier les statuts avant la signature ?', createdAt: instant(-0.2) },
  ];
}

// ---------------------------------------------------------------- demandes et requetes

const DEMANDES = [
  { id: 'd0000000-0000-4000-8000-000000000001', dossier: 0, sujet: 'Attestation de domiciliation', description: 'Merci de nous transmettre l attestation pour la banque.', statut: 'NON_TRAITEE', jours: -2 },
  { id: 'd0000000-0000-4000-8000-000000000002', dossier: 0, sujet: 'Copie des statuts a jour', description: null, statut: 'EN_COURS', jours: -6 },
  { id: 'd0000000-0000-4000-8000-000000000003', dossier: 1, sujet: 'Modele de PV d AGO', description: 'Pour l approbation des comptes.', statut: 'TRAITEE', jours: -15 },
];

function demande(d: (typeof DEMANDES)[number]) {
  return {
    id: d.id,
    dossierId: DOSSIERS[d.dossier].id,
    soumisPar: P.client.userId,
    sujet: d.sujet,
    description: d.description,
    statut: d.statut,
    ticketId: null,
    prisEnChargePar: d.statut === 'NON_TRAITEE' ? null : P.employe.userId,
    noteInterne: null,
    traiteAt: d.statut === 'TRAITEE' ? instant(d.jours + 2) : null,
    createdAt: instant(d.jours),
    etatSuivi: d.statut,
  };
}

const REQUETES = [
  { id: '9a000000-0000-4000-8000-000000000001', sujet: 'Copie de la CIN du gerant', type: 'PIECE', statut: 'OUVERTE', jours: -1 },
  { id: '9a000000-0000-4000-8000-000000000002', sujet: 'Adresse exacte du siege social', type: 'INFO', statut: 'REPONDUE', jours: -5 },
  { id: '9a000000-0000-4000-8000-000000000003', sujet: 'Signature des statuts', type: 'SIGNATURE', statut: 'CLOTUREE', jours: -12 },
];

function requete(r: (typeof REQUETES)[number]) {
  return {
    id: r.id,
    dossierId: IDS.dossierAtlas,
    soumisPar: P.employe.userId,
    sujet: r.sujet,
    description: 'Necessaire pour constituer le dossier du registre du commerce.',
    statut: r.statut,
    ticketId: IDS.ticketCreation,
    prisEnChargePar: P.employe.userId,
    noteInterne: null,
    traiteAt: null,
    createdAt: instant(r.jours),
    direction: 'EMPLOYE_TO_CLIENT',
    typeRequete: r.type,
    reponduAt: r.statut === 'OUVERTE' ? null : instant(r.jours + 1),
    clotureAt: r.statut === 'CLOTUREE' ? instant(r.jours + 2) : null,
    noteClient: r.statut === 'OUVERTE' ? null : 'Document depose dans la Data Room.',
    etatSuivi: r.statut === 'OUVERTE' ? 'NON_TRAITEE' : r.statut === 'REPONDUE' ? 'EN_COURS' : 'TRAITEE',
  };
}

// ---------------------------------------------------------------- super-administrateur

const CABINETS = [
  { id: WS_DEMO, code: 'JUR-DEMO1', name: 'Atlas Conseil', contactEmail: 'contact@atlas-conseil.example', statut: 'ACTIVE', forfait: 'professionnel', employes: 3, clients: 4, storageBytes: 2_684_354_560, storageFiles: 312, createdAt: instant(-200) },
  { id: WS_ESSAI, code: 'JUR-ESSAI7', name: 'Cabinet Chraibi', contactEmail: 'm.chraibi@example.com', statut: 'TRIAL', forfait: null, employes: 0, clients: 0, storageBytes: 12_582_912, storageFiles: 4, createdAt: instant(-11) },
  { id: '33333333-3333-4333-8333-333333333333', code: 'JUR-NORD2', name: 'Nord Juris', contactEmail: 'contact@nordjuris.example', statut: 'SUSPENDED', forfait: 'essentiel', employes: 1, clients: 2, storageBytes: 734_003_200, storageFiles: 88, createdAt: instant(-320) },
  { id: '44444444-4444-4444-8444-444444444444', code: 'JUR-SUD3', name: 'Sud Conseil et Associes', contactEmail: 'contact@sudconseil.example', statut: 'ACTIVE', forfait: 'max', employes: 4, clients: 11, storageBytes: 9_663_676_416, storageFiles: 1204, createdAt: instant(-410) },
];

function detailCabinet(id: string) {
  const c = CABINETS.find((x) => x.id === id) ?? CABINETS[0];
  return {
    id: c.id,
    code: c.code,
    name: c.name,
    statut: c.statut,
    forfait: c.forfait,
    contactEmail: c.contactEmail,
    city: 'Casablanca',
    ice: '000000000000099',
    ifFiscal: '00000099',
    rcNumber: '000099',
    adresse: '12, boulevard d Anfa, Casablanca',
    telephone: '+212 5 22 00 00 00',
    siteWeb: null,
    createdAt: c.createdAt,
    trialStartedAt: instant(-140),
    trialEndsAt: instant(-126),
    trialStatus: 'CONVERTED',
    subscriptionStatus: 'ACTIVE',
    employes: c.employes,
    clients: c.clients,
    ticketsTotal: TICKETS.length,
    dossiers: DOSSIERS.length,
    storageBytes: c.storageBytes,
    storageFiles: c.storageFiles,
    usersByRole: [
      { label: 'SUPERVISEUR', count: 1 },
      { label: 'EMPLOYE', count: 3 },
      { label: 'CLIENT', count: 4 },
    ],
    docsByType: [
      { label: 'STATUTS', count: 14 },
      { label: 'PV', count: 22 },
      { label: 'PIECE_IDENTITE', count: 31 },
    ],
    ticketsByStatut: [
      { label: 'CREATION_TICKET', count: 2 },
      { label: 'GENERATION_DOCUMENTS', count: 2 },
      { label: 'DEROULEMENT_DEMARCHE', count: 2 },
      { label: 'CLOTURE_DOSSIER', count: 1 },
      { label: 'ANNULE', count: 1 },
    ],
    eventsByAction: [
      { label: 'DOCUMENT_GENERATED', count: 41 },
      { label: 'TICKET_CREATED', count: 8 },
      { label: 'LOGIN_SUCCESS', count: 96 },
    ],
    activity30d: Array.from({ length: 30 }, (_, i) => ({ day: jour(i - 29), count: (i * 7) % 13 + 2 })),
    recentEvents: [
      { id: 9001, action: 'DOCUMENT_GENERATED', userId: P.employe.userId, entityType: 'TICKET', createdAt: instant(-0.1) },
      { id: 9002, action: 'TICKET_CREATED', userId: P.superviseur.userId, entityType: 'TICKET', createdAt: instant(-1) },
    ],
    generatedAt: instant(0),
  };
}

const UTILISATEURS_ADMIN = [
  ...ANNUAIRE.map((u) => ({
    id: u.userId,
    firstName: u.firstName,
    lastName: u.lastName,
    loginEmail: u.email,
    contactEmail: u.email.replace('jurika.ma', 'example.com'),
    role: u.role,
    status: u.status,
    totpEnabled: u.role !== 'CLIENT',
    workspaceId: WS_DEMO,
    workspaceCode: 'JUR-DEMO1',
    workspaceName: 'Atlas Conseil',
    createdAt: u.createdAt,
    lastLoginAt: u.lastLoginAt,
  })),
  {
    id: P['superviseur-essai'].userId,
    firstName: 'Meryem',
    lastName: 'Chraibi',
    loginEmail: P['superviseur-essai'].email,
    contactEmail: 'm.chraibi@example.com',
    role: 'SUPERVISEUR',
    status: 'ACTIVE',
    totpEnabled: true,
    workspaceId: WS_ESSAI,
    workspaceCode: 'JUR-ESSAI7',
    workspaceName: 'Cabinet Chraibi',
    createdAt: instant(-11),
    lastLoginAt: instant(-0.5),
  },
];

const JOURNAL = [
  ['LOGIN_SUCCESS', 'auth-service', 'USER', -0.05],
  ['DOCUMENT_GENERATED', 'ai-service', 'TICKET', -0.1],
  ['TICKET_STATUS_CHANGED', 'ticket-service', 'TICKET', -0.3],
  ['PAYMENT_VALIDATED', 'billing-service', 'PAYMENT', -1],
  ['WORKSPACE_SUSPENDED', 'auth-service', 'WORKSPACE', -2],
  ['DATAROOM_DOCUMENT_UPLOADED', 'dataroom-service', 'DOCUMENT', -2.5],
  ['LOGIN_FAILED', 'auth-service', 'USER', -3],
] as const;

function journalAudit() {
  return JOURNAL.map(([action, service, entite, j], i) => ({
    id: 5000 + i,
    workspaceId: i === 4 ? CABINETS[2].id : WS_DEMO,
    userId: i === 4 ? P['super-admin'].userId : P.employe.userId,
    action,
    entityType: entite,
    entityId: 'ab000000-0000-4000-8000-00000000000' + i,
    sourceService: service,
    correlationId: null,
    metadata: null,
    createdAt: instant(j),
  }));
}

function essaisAdmin() {
  return [
    {
      workspaceId: WS_ESSAI,
      code: 'JUR-ESSAI7',
      nom: 'Cabinet Chraibi',
      contactEmail: 'm.chraibi@example.com',
      statut: 'TRIAL',
      statutEssai: 'TRIAL_ACTIVE',
      debut: jour(-11),
      fin: jour(3),
      expireLe: null,
      avertissementLe: null,
      prolongeLe: null,
      prolongationJours: null,
      finMotif: null,
      abonnement: null,
      adresseIp: '192.0.2.44',
      appareil: 'appareil-0001',
      signalement: null,
      signalementExamineLe: null,
      creeLe: instant(-11),
      supprimeLe: null,
    },
    {
      workspaceId: '55555555-5555-4555-8555-555555555555',
      code: 'JUR-ESSAI5',
      nom: 'Cabinet Ouazzani',
      contactEmail: 'contact@ouazzani.example',
      statut: 'TRIAL',
      statutEssai: 'TRIAL_EXPIRED',
      debut: jour(-30),
      fin: jour(-16),
      expireLe: jour(-16),
      avertissementLe: jour(-2),
      prolongeLe: null,
      prolongationJours: null,
      finMotif: null,
      abonnement: null,
      adresseIp: '192.0.2.45',
      appareil: 'appareil-0002',
      signalement: 'Meme appareil qu un essai precedent',
      signalementExamineLe: null,
      creeLe: instant(-30),
      supprimeLe: null,
    },
  ];
}

function abonnementsAdmin() {
  return CABINETS.filter((c) => c.forfait).map((c, i) => ({
    workspaceId: c.id,
    abonnementId: 100 + i,
    statut: c.statut === 'SUSPENDED' ? 'SUSPENDU' : 'ACTIF',
    forfait: c.forfait,
    forfaitLibelle: c.forfait === 'max' ? 'Max' : c.forfait === 'essentiel' ? 'Essentiel' : 'Professionnel',
    rythme: i % 2 ? 'monthly' : 'yearly',
    engagementDebut: jour(-120 - i * 30),
    engagementFin: jour(245 - i * 30),
    periodeDebut: jour(-120 - i * 30),
    periodeFin: jour(245 - i * 30),
    prochaineEcheance: null,
    changementAVenir: null,
    archiveMotif: null,
    surDevis: null,
    impaye:
      c.statut === 'SUSPENDED'
        ? { echeance: jour(-20), suspensionLe: jour(-5), suspendu: true, suspenduLe: jour(-5), rappelEnvoyeLe: jour(-12), arretLe: null, delaiJours: 15 }
        : null,
    journalImpaye: [],
  }));
}

function lignesContratAdmin() {
  return CABINETS.filter((c) => c.forfait).map((c) => ({
    workspaceId: c.id,
    statut: 'ACCEPTE',
    enAttente: null,
    courant: { ...CONDITIONS, reference: `CP-2026-${c.code.slice(-1)}` },
    derniereAcceptation: ACCEPTATION,
    resiliation: null,
    joursAvantEcheance: 245,
    versionSansReponse: null,
    derniereReponse: null,
  }));
}

function editeur() {
  return {
    valeurs: {
      raisonSociale: 'Maghreb Consulting',
      formeJuridique: 'SARL',
      siege: null,
      rc: null,
      ice: null,
      ifFiscal: null,
      capital: null,
      email: null,
    },
    tauxTvaPourcent: 20,
    manquants: [
      { champ: 'siege', libelle: 'Adresse du siege', usages: ['FACTURES', 'CONTRAT'] },
      { champ: 'rc', libelle: 'Numero du registre du commerce', usages: ['FACTURES', 'CONTRAT'] },
      { champ: 'ice', libelle: 'ICE', usages: ['FACTURES'] },
    ],
    modifieLe: instant(-30),
    modifiePar: P['super-admin'].email,
  };
}

// ---------------------------------------------------------------- tableaux de bord

function tableauSuperAdmin() {
  return {
    activeWorkspaces30d: 3,
    totalWorkspaces: CABINETS.length,
    totalUsers: UTILISATEURS_ADMIN.length,
    signups30d: Array.from({ length: 30 }, (_, i) => ({ day: jour(i - 29), count: i % 9 === 0 ? 1 : 0 })),
    topWorkspacesByUsage: CABINETS.map((c, i) => ({ workspaceId: c.id, name: c.name, eventsLast7d: 120 - i * 25 })),
    criticalAuditEvents24h: journalAudit()
      .slice(0, 2)
      .map((e) => ({ id: String(e.id), action: e.action, userId: e.userId, workspaceId: e.workspaceId, entityType: e.entityType, createdAt: e.createdAt })),
    servicesHealth: { auth: 'UP', ticket: 'UP', dataroom: 'UP', ai: 'UP', billing: 'UP', workflow: 'UP' },
    storage: { totalBytes: 13_094_617_088, fileCount: 1608 },
    workspacesParForfait: [
      { label: 'Essentiel', count: 1 },
      { label: 'Professionnel', count: 1 },
      { label: 'Max', count: 1 },
      { label: 'Essai', count: 1 },
    ],
    workspacesParStatut: [
      { label: 'ACTIVE', count: 2 },
      { label: 'TRIAL', count: 1 },
      { label: 'SUSPENDED', count: 1 },
    ],
    signupsMensuels: Array.from({ length: 6 }, (_, i) => ({ month: jour(-30 * (5 - i)).slice(0, 7), count: (i * 3) % 5 + 1 })),
    generatedAt: instant(0),
  };
}

function tableauSuperviseur() {
  return {
    ticketsOuverts: 6,
    ticketsClos30d: 1,
    tempsMoyenClotureHeures: 196,
    evolutionTickets30j: Array.from({ length: 30 }, (_, i) => ({ day: jour(i - 29), count: (i * 5) % 4 })),
    topWorkflows: [
      { label: 'MODIFICATION', count: 3 },
      { label: 'CREATION', count: 1 },
      { label: 'PV_AGO', count: 1 },
    ],
    chargeParEmploye: [
      { userId: P.employe.userId, email: P.employe.email, ticketsOuverts: 4, echeancesAssignees: 3 },
      { userId: EMPLOYE2.userId, email: EMPLOYE2.email, ticketsOuverts: 2, echeancesAssignees: 1 },
    ],
    echeancesJ30J15J3: [
      { label: 'J-3', items: ECHEANCES.filter((e) => e.jours <= 3).map(echeanceItem) },
      { label: 'J-15', items: [] },
      { label: 'J-30', items: ECHEANCES.filter((e) => e.jours > 15 && e.jours <= 30).map(echeanceItem) },
    ],
    dossiersARisque: [{ dossierId: DOSSIERS[1].id, raisonSociale: DOSSIERS[1].raisonSociale, motif: 'Echeance de depot depassee de 4 jours' }],
    ticketsParStatut: [
      { label: 'CREATION_TICKET', count: 2 },
      { label: 'GENERATION_DOCUMENTS', count: 2 },
      { label: 'DEROULEMENT_DEMARCHE', count: 2 },
      { label: 'CLOTURE_DOSSIER', count: 1 },
      { label: 'ANNULE', count: 1 },
    ],
    evolutionMensuelle: Array.from({ length: 6 }, (_, i) => ({ month: jour(-30 * (5 - i)).slice(0, 7), crees: 2 + (i % 3), clotures: 1 + (i % 2) })),
    demandesParStatut: [
      { label: 'NON_TRAITEE', count: 1 },
      { label: 'EN_COURS', count: 1 },
      { label: 'TRAITEE', count: 1 },
    ],
    generatedAt: instant(0),
    productiviteIncluse: true,
  };
}

function ticketLite(t: TicketFictif) {
  return { id: t.id, reference: t.reference, titre: t.titre, statut: t.statut, priorite: t.priorite, createdAt: instant(t.cree) };
}

function tableauEmploye() {
  const miens = TICKETS.filter((t) => t.assigne?.userId === P.employe.userId);
  return {
    mesTicketsOuverts: miens.filter((t) => t.statut !== 'CLOTURE_DOSSIER' && t.statut !== 'ANNULE').length,
    derniersTickets: miens.map(ticketLite),
    maChargeSemaine: Array.from({ length: 7 }, (_, i) => ({ day: jour(i), charge: [3, 4, 2, 5, 1, 0, 0][i] })),
    mesEcheancesAssignees: ECHEANCES.filter((e) => DOSSIERS[e.dossier].responsableId === P.employe.userId).map(echeanceItem),
    dernieresOperationsDataroom: [
      { type: 'UPLOAD', label: 'CIN du gerant (Atlas Distribution)', at: instant(-0.2) },
      { type: 'GENERATION', label: 'Statuts SARL (Atlas Distribution)', at: instant(-1) },
    ],
    generatedAt: instant(0),
  };
}

function tableauClient() {
  return {
    mesDossiers: [
      { id: IDS.dossierAtlas, raisonSociale: 'Atlas Distribution', statut: 'ACTIF', lastEventAt: instant(-1), lastEventLabel: 'Statuts deposes dans votre espace' },
    ],
    mesTicketsEnCours: [ticketLite(TICKETS[0]), ticketLite(TICKETS[7])],
    mesDocumentsRecents: [
      { id: 'doc-1', title: 'Statuts de la SARL', filename: 'statuts-atlas-distribution.docx', createdAt: instant(-1) },
      { id: 'doc-2', title: 'Certificat negatif', filename: 'certificat-negatif.pdf', createdAt: instant(-4) },
    ],
    mesEcheancesAVenir: [echeanceItem(ECHEANCES[2])],
    generatedAt: instant(0),
  };
}

function briefing() {
  const sig = ECHEANCES.filter((e) => DOSSIERS[e.dossier].responsableId === P.employe.userId).map((e) => ({
    source: 'DEADLINE',
    refId: e.id,
    intitule: e.libelle,
    date: jour(e.jours),
    severite: e.severite,
    depassee: e.jours < 0,
    joursRestants: e.jours,
    dossierId: DOSSIERS[e.dossier].id,
    dossierNom: DOSSIERS[e.dossier].raisonSociale,
    lien: '/calendar',
  }));
  return {
    id: 'bb000000-0000-4000-8000-000000000001',
    workspaceId: WS_DEMO,
    employeeId: P.employe.userId,
    summary: 'Une echeance est depassee et deux arrivent dans les trois jours.',
    texteLlm: null,
    genereParIa: false,
    planDuJour: [
      { ordre: 1, urgence: 'CRITIQUE', action: 'Deposer le PV de transfert du siege d Oasis Agro', justification: 'Echeance depassee de 4 jours', type: 'ECHEANCE', targetId: ECHEANCES[0].id, dossierId: DOSSIERS[1].id, lien: `/tickets/${TICKETS[1].id}` },
      { ordre: 2, urgence: 'HAUTE', action: 'Deposer les comptes annuels de Rif Logistique', justification: 'Echeance demain', type: 'ECHEANCE', targetId: ECHEANCES[1].id, dossierId: DOSSIERS[2].id, lien: `/tickets/${TICKETS[2].id}` },
      { ordre: 3, urgence: 'MOYENNE', action: 'Completer la creation d Atlas Distribution', justification: 'Ticket en collecte depuis 6 jours', type: 'TICKET', targetId: IDS.ticketCreation, dossierId: IDS.dossierAtlas, lien: `/tickets/${IDS.ticketCreation}` },
    ],
    signaux: {
      echeances: sig,
      resteAFaire: [
        { type: 'DEMANDE', refId: DEMANDES[0].id, reference: null, sujet: DEMANDES[0].sujet, statut: 'NON_TRAITEE', ancienneteJours: 2, dossierId: IDS.dossierAtlas, dossierNom: 'Atlas Distribution', lien: '/data-rooms' },
      ],
      counts: { echeances: sig.length, echeancesDepassees: sig.filter((s) => s.depassee).length, tickets: 4, demandes: 1 },
    },
    createdAt: instant(-0.1),
    seenAt: null,
  };
}

// ---------------------------------------------------------------- dossier juridique (Data Room)

function document(id: number, dossierId: string, type: string, titre: string, fichier: string, jours: number, ticketId: string | null = IDS.ticketCreation) {
  return {
    id: `d1000000-0000-4000-8000-0000000000${String(id).padStart(2, '0')}`,
    dossierId,
    ticketId,
    documentType: type,
    title: titre,
    version: 1,
    current: true,
    filename: fichier,
    contentType: fichier.endsWith('.pdf') ? 'application/pdf' : 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    sizeBytes: 48_000 + id * 1000,
    createdAt: instant(jours),
    replacedAt: null,
    motif: null,
    editeManuellementAt: null,
  };
}

function vueJuridique(id: string) {
  const d = DOSSIERS.find((x) => x.id === id) ?? DOSSIERS[0];
  const docs = [
    document(1, d.id, 'STATUTS', 'Statuts de la SARL', 'statuts.docx', -1),
    document(2, d.id, 'PV_AGE', 'PV de l assemblee constitutive', 'pv-constitutif.docx', -1),
    document(3, d.id, 'CN', 'Certificat negatif', 'certificat-negatif.pdf', -4, null),
    document(4, d.id, 'CNIE_GERANT', 'CNIE du gerant', 'cnie-gerant.pdf', -5, null),
    document(5, d.id, 'CONTRAT_BAIL', 'Contrat de bail du siege', 'bail-siege.pdf', -5, null),
  ];
  return {
    dossierId: d.id,
    raisonSociale: d.raisonSociale,
    formeJuridique: d.formeJuridique,
    ice: d.ice,
    rcNumero: d.statut === 'EN_COURS' ? null : '000123',
    rcTribunal: `Tribunal de commerce de ${d.ville}`,
    identifiantFiscal: d.statut === 'EN_COURS' ? null : '00000123',
    taxeProfessionnelle: null,
    cnss: null,
    adresseSiege: `24, rue des Orangers, ${d.ville}`,
    ville: d.ville,
    capitalSocialMad: 100_000,
    dateConstitution: jour(-90),
    statut: d.statut,
    documentsEnVigueur: docs,
    historiqueOperations: [
      {
        ticketId: IDS.ticketCreation,
        reference: 'TK-2026-0101',
        titre: 'Creation SARL ' + d.raisonSociale,
        type: 'CREATION',
        clotureAt: null,
        description: 'Constitution de la societe',
        replacedDocuments: [],
        generatedDocuments: docs.slice(0, 2),
      },
    ],
    dossiersParTicket: [
      {
        ticketId: IDS.ticketCreation,
        libelle: 'Creation de la societe',
        reference: 'TK-2026-0101',
        type: 'CREATION',
        statut: 'CREATION_TICKET',
        ouvertLe: instant(-6),
        groupes: [
          { code: 'ACTES', libelle: 'Actes rediges', documents: docs.slice(0, 2) },
          { code: 'PIECES', libelle: 'Pieces du client', documents: docs.slice(2) },
        ],
        totalDocuments: docs.length,
      },
    ],
  };
}

// ---------------------------------------------------------------- page du ticket et parcours

function ligneDemarche(ordre: number, phase: [string, string], libelle: string, etat: string, opts: { organisme?: string; delai?: string; echeance?: number | null; actionnable?: boolean } = {}) {
  return {
    demarcheId: `a2000000-0000-4000-8000-0000000000${String(ordre).padStart(2, '0')}`,
    ordre,
    phaseCode: phase[0],
    phaseLibelle: phase[1],
    libelle,
    statutTicket: 'CREATION_TICKET',
    acteur: 'Cabinet',
    organisme: opts.organisme ?? null,
    obligatoire: true,
    conditionApplication: null,
    piecesEntrantes: null,
    documentProduit: null,
    justificatifsTexte: null,
    modeleJurika: null,
    delai: opts.delai ?? null,
    coutIndicatif: null,
    variablesAlimentees: null,
    delaiCalculable: opts.echeance !== undefined && opts.echeance !== null,
    delaiDepartManquant: null,
    decision: null,
    applicabilite: 'APPLICABLE',
    raisonApplicabilite: null,
    delaiDepart: opts.echeance != null ? jour(-6) : null,
    echeance: opts.echeance != null ? jour(opts.echeance) : null,
    delaiAttendu: null,
    delaiDonnee: null,
    justificatifsAttendus: [],
    etat,
    motif: null,
    cocheAt: etat === 'COCHEE' ? instant(-3) : null,
    documentsDeposes: [],
    actionnableMaintenant: opts.actionnable ?? etat === 'A_FAIRE',
    horsSequence: false,
    formaliteCode: null,
    formaliteVolet: null,
    depotOrdre: null,
    deposeLe: null,
    journal: [],
  };
}

function vueDemarches() {
  const p1: [string, string] = ['PREALABLES', 'Formalites prealables'];
  const p2: [string, string] = ['IMMATRICULATION', 'Immatriculation'];
  const l1 = [
    ligneDemarche(1, p1, 'Obtenir le certificat negatif', 'COCHEE', { organisme: 'OMPIC' }),
    ligneDemarche(2, p1, 'Ouvrir le compte bancaire et bloquer le capital', 'COCHEE', { organisme: 'Banque' }),
    ligneDemarche(3, p1, 'Faire enregistrer les statuts', 'A_FAIRE', { organisme: 'Direction generale des impots', delai: 'trente jours', echeance: -2 }),
  ];
  const l2 = [
    ligneDemarche(4, p2, 'Deposer le dossier au registre du commerce', 'A_FAIRE', { organisme: 'Tribunal de commerce', delai: 'trois jours', echeance: 3, actionnable: false }),
    ligneDemarche(5, p2, 'Publier l annonce legale', 'A_FAIRE', { organisme: 'Journal d annonces legales', delai: 'trente jours', echeance: 40, actionnable: false }),
  ];
  return {
    workflowType: 'CREATION',
    statutCourant: 'CREATION_TICKET',
    phases: [
      { code: p1[0], libelle: p1[1], demarches: l1, traitees: 2, total: 3 },
      { code: p2[0], libelle: p2[1], demarches: l2, traitees: 0, total: 2 },
    ],
    avancement: {
      statutCourant: 'CREATION_TICKET',
      position: 1,
      totalStatuts: 4,
      traitees: 2,
      applicables: 5,
      prochainOrdre: 3,
      prochainLibelle: 'Faire enregistrer les statuts',
      pointsAttention: [
        { ordre: 3, libelle: 'Faire enregistrer les statuts', delai: 'trente jours', echeance: jour(-2), joursRestants: -2, severite: 'DEPASSE' },
        { ordre: 4, libelle: 'Deposer le dossier au registre du commerce', delai: 'trois jours', echeance: jour(3), joursRestants: 3, severite: 'CRITIQUE' },
        { ordre: 5, libelle: 'Publier l annonce legale', delai: 'trente jours', echeance: jour(40), joursRestants: 40, severite: 'APPROCHE' },
      ],
    },
  };
}

function progression(ticketId: string) {
  return {
    id: 'a3000000-0000-4000-8000-000000000001',
    workspaceId: WS_DEMO,
    ticketId,
    type: 'CREATION',
    currentStep: 2,
    totalSteps: 9,
    data: {
      step1: { denomination: 'Atlas Distribution', formeJuridique: 'SARL', sigle: null },
      step2: { adresse: '24, rue des Orangers', province: 'Casablanca', commune: 'Anfa', codePostal: '20000', villeGreffe: 'Casablanca', justificatifType: 'BAIL' },
    },
    statut: 'EN_COURS',
    startedById: P.employe.userId,
    completedAt: null,
    updatedAt: instant(-0.5),
  };
}

const VARIABLES = [
  ['DENOMINATION', 'Atlas Distribution', 'SAISIE'],
  ['FORME_JURIDIQUE', 'SARL', 'SAISIE'],
  ['SIEGE_ADRESSE', '24, rue des Orangers, Casablanca', 'SAISIE'],
  ['CAPITAL_CHIFFRES', '100 000', 'SAISIE'],
  ['VILLE_GREFFE', 'Casablanca', 'DERIVEE'],
  ['GERANT_NOM', 'Tazi', 'EXTRAITE'],
] as const;

// ---------------------------------------------------------------- routes

const r = (methode: RouteSimulee['methode'], motif: RegExp, rep: RouteSimulee['reponse']): RouteSimulee => ({ methode, motif, reponse: rep });

function cleVisite(p: Profil | null) {
  return p ? `visite-${p.role.toLowerCase().replace(/_/g, '-')}` : 'visite';
}

export const ROUTES: RouteSimulee[] = [
  // ---- Compte, preferences, aides ----
  r('GET', /^\/users\/me\/preferences$/, (_q, c) => ({ theme: c.theme === 'sombre' ? 'SOMBRE' : 'CLAIR', juriMasque: false })),
  r('PUT', /^\/users\/me\/preferences$/, () => reponse(204)),
  // La visite guidee est marquee vue : elle ne s'ouvre pas sur les captures.
  r('GET', /^\/auth\/me\/aides$/, (_q, c) => ({ cles: [cleVisite(c.profil)] })),
  r('*', /^\/auth\/me\/aides(\/.*)?$/, () => reponse(204)),
  r('GET', /^\/auth\/me$/, (_q, c) => {
    const p = c.profil ?? P.employe;
    return {
      userId: p.userId,
      workspaceId: p.workspaceId,
      email: p.email,
      loginEmail: p.email,
      contactEmail: p.email.replace(/@.*/, '@example.com'),
      firstName: p.prenom,
      lastName: p.nom,
      phone: '+212 6 00 00 00 00',
      role: p.role,
      status: 'ACTIVE',
    };
  }),
  r('GET', /^\/auth\/me\/demandes$/, () => ({ adresse: null, identifiant: null })),
  r('GET', /^\/auth\/users$/, () => EQUIPE),
  r('GET', /^\/auth\/users\/contacts$/, (_q, c) => EQUIPE.filter((u) => u.userId !== c.profil?.userId && u.status === 'ACTIVE')),
  r('GET', /^\/auth\/users\/workspace-directory$/, () => ANNUAIRE),
  r('GET', /^\/auth\/users\/droit-suppression-dataroom$/, () => ({ employesAvecLeDroit: [P.employe.userId] })),

  // ---- Cabinet, essai, facturation, contrat ----
  r('GET', /^\/workspace\/profile$/, (_q, c) => profilCabinet(c)),
  r('GET', /^\/workspace\/essai$/, (_q, c) => etatEssai(c)),
  r('GET', /^\/workspace\/usage$/, (_q, c) => usage(c)),
  r('GET', /^\/billing\/abonnement$/, (_q, c) => abonnement(c)),
  r('GET', /^\/billing\/contrat\/etat$/, (_q, c) => etatContrat(c)),
  r('GET', /^\/billing\/contrat\/historique$/, () => ({ conditions: [CONDITIONS], acceptations: [ACCEPTATION], resiliations: [], renouvellements: [], avis: [], reponses: [] })),
  r('GET', /^\/billing\/contrat\/versions$/, () => [VERSION]),
  r('GET', /^\/billing\/paiements$/, (_q, c) => (c.profil?.cabinet === 'essai' ? [] : [paiement(1, 'COMPLETED', -120, 'SOUSCRIPTION')])),
  r('GET', /^\/billing\/factures$/, (_q, c) => (c.profil?.cabinet === 'essai' ? [] : [facture(1, -120)])),
  r('GET', /^\/public\/pricing$/, () => PRICING),
  r('GET', /^\/public\/essai\/conditions$/, () => ({
    version: 'ESSAI-V1',
    empreinte: 'e5f6a1b2c3d4',
    texte: "Conditions de l'essai gratuit (texte simule pour la verification visuelle).\n\n1. L'essai dure quatorze jours.\n2. Les donnees saisies restent celles du cabinet.",
  })),
  r('POST', /^\/public\/events$/, () => reponse(204)),

  // ---- Tableaux de bord ----
  r('GET', /^\/dashboards\/super-admin$/, () => tableauSuperAdmin()),
  r('GET', /^\/dashboards\/superviseur$/, () => tableauSuperviseur()),
  r('GET', /^\/dashboards\/employe$/, () => tableauEmploye()),
  r('GET', /^\/dashboards\/client$/, () => tableauClient()),
  r('GET', /^\/agent\/briefing\/me$/, () => briefing()),
  r('GET', /^\/supervision\/kpis$/, () => ({
    tickets: 8,
    ticketsNouveaux: 2,
    ticketsEnCours: 4,
    ticketsClotures: 1,
    ticketsAnnules: 1,
    dossiers: 6,
    dossiersActifs: 5,
    deboursTotalMad: 12_450,
    demandesNonTraitees: 1,
    computedAt: instant(0),
  })),
  r('GET', /^\/supervision\/tickets-per-type$/, () => [
    { type: 'MODIFICATION', count: 3 },
    { type: 'CREATION', count: 1 },
    { type: 'PV_AGO', count: 1 },
    { type: 'DISSOLUTION', count: 1 },
    { type: 'IMPORT', count: 1 },
    { type: 'SUCCURSALE_MA', count: 1 },
  ]),
  r('GET', /^\/supervision\/tickets-per-statut$/, () => [
    { statut: 'CREATION_TICKET', count: 2 },
    { statut: 'GENERATION_DOCUMENTS', count: 2 },
    { statut: 'DEROULEMENT_DEMARCHE', count: 2 },
    { statut: 'CLOTURE_DOSSIER', count: 1 },
    { statut: 'ANNULE', count: 1 },
  ]),
  r('GET', /^\/supervision\/tickets-per-employe$/, () => [
    { userId: P.employe.userId, firstName: P.employe.prenom, lastName: P.employe.nom, enCours: 4, clotures: 0, annules: 0 },
    { userId: EMPLOYE2.userId, firstName: EMPLOYE2.prenom, lastName: EMPLOYE2.nom, enCours: 1, clotures: 1, annules: 1 },
  ]),
  r('GET', /^\/supervision\/tickets-last-30-days$/, () => Array.from({ length: 30 }, (_, i) => ({ date: jour(i - 29), count: (i * 5) % 4 }))),

  // ---- Tickets ----
  r('GET', /^\/tickets$/, (q, c) => page(ticketsDu(c), q.query)),
  r('GET', /^\/tickets\/([0-9a-f-]{36})$/, (_q, _c, [id]) => {
    const t = TICKETS.find((x) => x.id === id);
    return t ? ticketVue(t) : reponse(404, { code: 'TICKET_INTROUVABLE', message: 'Ticket introuvable' });
  }),
  r('GET', /^\/tickets\/([0-9a-f-]{36})\/acces$/, (_q, c) => ({ enCharge: c.profil?.role === 'EMPLOYE', peutAgir: c.profil?.role === 'EMPLOYE', raison: c.profil?.role === 'EMPLOYE' ? null : 'Ticket suivi par Nadia Idrissi' })),
  r('GET', /^\/tickets\/([0-9a-f-]{36})\/passage$/, () => ({ statut: 'CREATION_TICKET', suivant: 'GENERATION_DOCUMENTS', obstacles: ['Etape 3 du parcours (capital) a completer'] })),
  r('GET', /^\/tickets\/([0-9a-f-]{36})\/note$/, (_q, _c, [id]) => ({ ticketId: id, contenu: 'Client joignable le matin. Banque : agence Anfa.', modifiePar: P.employe.userId, modifieLe: instant(-1) })),
  r('GET', /^\/tickets\/([0-9a-f-]{36})\/debours$/, (_q, _c, [id]) => ({
    items: [
      { id: 'a4000000-0000-4000-8000-000000000001', ticketId: id, libelle: 'Certificat negatif', categorie: 'FRAIS_ORGANISME', montantMad: 230, dateEngagement: jour(-5), pieceJointeUrl: null, pieceJointeFilename: null, notes: null, createdAt: instant(-5) },
      { id: 'a4000000-0000-4000-8000-000000000002', ticketId: id, libelle: 'Legalisation des signatures', categorie: 'LEGALISATION', montantMad: 40, dateEngagement: jour(-3), pieceJointeUrl: null, pieceJointeFilename: null, notes: 'Deux signataires', createdAt: instant(-3) },
    ],
    // total : somme des montants (DeboursPanel l affiche comme un montant), pas un nombre de lignes.
    total: 270,
  })),
  r('GET', /^\/tickets\/([0-9a-f-]{36})\/demarches$/, () => vueDemarches()),
  r('GET', /^\/workflows\/([0-9a-f-]{36})$/, (_q, _c, [id]) => progression(id)),
  r('GET', /^\/workflows\/([0-9a-f-]{36})\/variables$/, () =>
    VARIABLES.map(([variable, valeur, origine]) => ({ variable, boucle: null, rang: null, valeur, origine, saisiePar: P.employe.userId, saisieLe: instant(-2), occasion: 'Parcours de creation' })),
  ),
  r('GET', /^\/workflows\/([0-9a-f-]{36})\/donnees-attendues$/, () => []),
  r('GET', /^\/dataroom\/document-types$/, () => [
    { code: 'STATUTS', libelle: 'Statuts', groupe: 'Actes' },
    { code: 'PV_AGE', libelle: 'PV d assemblee generale extraordinaire', groupe: 'Actes' },
    { code: 'CN', libelle: 'Certificat negatif', groupe: 'Pieces' },
    { code: 'CNIE_GERANT', libelle: 'CNIE du gerant', groupe: 'Pieces' },
    { code: 'AUTRE', libelle: 'Autre document', groupe: null },
  ]),
  r('GET', /^\/dataroom\/dossiers\/([0-9a-f-]{36})\/settings\/historique$/, () => [
    { id: 'a5000000-0000-4000-8000-000000000001', nature: 'PERMISSIONS', acteurId: P.employe.userId, acteurNom: 'Nadia Idrissi', avant: { permDownload: false }, apres: { permDownload: true }, createdAt: instant(-7) },
  ]),
  r('GET', /^\/deadlines\/count-open$/, () => ({ count: 3 })),

  // ---- Data Rooms, demandes, requetes ----
  r('GET', /^\/dataroom\/dossiers$/, (_q, c) => dossiersDu(c)),
  r('GET', /^\/dataroom\/demandes$/, (q, c) => {
    if (q.query.get('direction') === 'EMPLOYE_TO_CLIENT') return REQUETES.map(requete);
    return c.profil?.cabinet === 'demo' ? DEMANDES.map(demande) : [];
  }),
  r('GET', /^\/dataroom\/supervision\/demandes$/, (q, c) => {
    if (c.profil?.cabinet !== 'demo') return [];
    if (q.query.get('direction') === 'EMPLOYE_TO_CLIENT') {
      return REQUETES.map((x) => ({ ...requete(x), raisonSociale: 'Atlas Distribution', responsableId: P.employe.userId, responsableNom: 'Nadia Idrissi' }));
    }
    return DEMANDES.map((d) => ({
      ...demande(d),
      raisonSociale: DOSSIERS[d.dossier].raisonSociale,
      responsableId: P.employe.userId,
      responsableNom: 'Nadia Idrissi',
      direction: 'CLIENT_TO_EMPLOYE',
      typeRequete: null,
    }));
  }),
  r('GET', /^\/dataroom\/dossiers\/([0-9a-f-]{36})\/juridique$/, (_q, _c, [id]) => vueJuridique(id)),
  r('GET', /^\/dataroom\/dossiers\/([0-9a-f-]{36})\/demandes$/, (q, _c, [id]) => {
    if (q.query.get('direction') === 'EMPLOYE_TO_CLIENT') return id === IDS.dossierAtlas ? REQUETES.map(requete) : [];
    return DEMANDES.filter((d) => DOSSIERS[d.dossier].id === id).map(demande);
  }),
  r('GET', /^\/dataroom\/dossiers\/([0-9a-f-]{36})\/depots$/, (_q, _c, [id]) =>
    id === IDS.dossierAtlas
      ? [
          { id: 'de000000-0000-4000-8000-000000000001', title: 'Attestation bancaire de blocage du capital', filename: 'attestation-blocage.pdf', contentType: 'application/pdf', sizeBytes: 182_400, uploadedBy: P.client.userId, createdAt: instant(-2) },
          { id: 'de000000-0000-4000-8000-000000000002', title: 'Contrat de bail du siege', filename: 'bail-siege.pdf', contentType: 'application/pdf', sizeBytes: 640_000, uploadedBy: P.client.userId, createdAt: instant(-5) },
        ]
      : [],
  ),
  r('GET', /^\/dataroom\/dossiers\/([0-9a-f-]{36})\/settings$/, (_q, _c, [id]) => ({
    dossierId: id,
    accessStatus: 'ACTIVE',
    permDownload: true,
    permPrint: true,
    permDepot: true,
    clientLinkToken: 'jeton-simule',
    accessCount: 14,
    lastAccessedAt: instant(-1),
    permConsultation: true,
    permDemandes: true,
  })),
  r('GET', /^\/dataroom\/dossiers\/([0-9a-f-]{36})\/settings\/my-permissions$/, (_q, _c, [id]) => ({
    dossierId: id,
    accessStatus: 'ACTIVE',
    permDownload: true,
    permPrint: true,
    permDepot: true,
  })),
  r('GET', /^\/dataroom\/dossiers\/([0-9a-f-]{36})\/mes-droits$/, () => ({ peutSupprimerDocuments: true, motif: null, peutReglerAccesClient: true })),
  r('GET', /^\/auth\/dossiers\/([0-9a-f-]{36})\/client$/, (_q, _c, [id]) => ({
    client: id === IDS.dossierAtlas ? { userId: P.client.userId, firstName: P.client.prenom, lastName: P.client.nom, email: P.client.email, status: 'ACTIVE' } : null,
  })),
  r('GET', /^\/dossiers\/([0-9a-f-]{36})\/reaffectations$/, () => []),
  r('GET', /^\/tracabilite\/entite$/, (q) =>
    page(
      journalAudit()
        .slice(0, 4)
        .map((e) => ({ id: e.id, action: e.action, actionLabel: e.action.replace(/_/g, ' ').toLowerCase(), userId: e.userId, entityType: 'DOSSIER', entityId: q.query.get('entityId'), metadata: null, sourceService: e.sourceService, createdAt: e.createdAt })),
      q.query,
    ),
  ),
  r('POST', /^\/agent\/briefing\/([0-9a-f-]{36})\/seen$/, () => ({ ...briefing(), seenAt: instant(0) })),
  r('GET', /^\/dossier-transfer-requests$/, () => []),
  r('GET', /^\/dossiers\/rattrapages$/, () => [
    {
      id: 'ea000000-0000-4000-8000-000000000001',
      dossierId: DOSSIERS[4].id,
      raisonSociale: DOSSIERS[4].raisonSociale,
      ancienResponsableId: EMPLOYE3.userId,
      ancienResponsableNom: 'Houda El Fassi',
      nouveauResponsableId: EMPLOYE2.userId,
      nouveauResponsableNom: 'Omar Berrada',
      nature: 'RATTRAPAGE',
      auteurId: null,
      auteurNom: null,
      motif: 'Compte de la responsable desactive',
      createdAt: instant(-3),
      responsableActuelId: EMPLOYE2.userId,
      responsableActuelNom: 'Omar Berrada',
      verifiePar: null,
      verifieParNom: null,
      verifieLe: null,
    },
  ]),

  // ---- Reperes ----
  r('GET', /^\/tickets\/delais-greffe$/, () => [
    { matrice: 'MODIFICATIONS_SARL', decisionCode: 'transfert_siege', decisionLibelle: 'Transfert du siege social', delaiTexte: 'dans le mois', delaiValeur: 1, delaiUnite: 'MOIS', article: 'Loi 5-96, art. 75', statut: 'CITE_PAR_LE_CORPUS', ajustement: null },
    { matrice: 'MODIFICATIONS_SARL', decisionCode: 'cession_parts', decisionLibelle: 'Cession de parts sociales', delaiTexte: null, delaiValeur: null, delaiUnite: null, article: null, statut: 'A_CONFIRMER', ajustement: null },
    { matrice: 'AGO_SARL', decisionCode: 'approbation_comptes', decisionLibelle: 'Approbation des comptes', delaiTexte: 'trente jours', delaiValeur: 30, delaiUnite: 'JOURS', article: null, statut: 'CITE_PAR_LE_CORPUS', ajustement: { delaiValeur: 30, delaiUnite: 'JOURS', article: 'Loi 5-96', note: 'Valeur confirmee par le cabinet', modifiePar: P.superviseur.email, modifieLe: instant(-20) } },
  ]),
  r('GET', /^\/tracabilite$/, (q) =>
    page(
      journalAudit().map((e) => ({ id: e.id, action: e.action, actionLabel: e.action.replace(/_/g, ' ').toLowerCase(), userId: e.userId, entityType: e.entityType, entityId: e.entityId, metadata: null, sourceService: e.sourceService, createdAt: e.createdAt })),
      q.query,
    ),
  ),

  // ---- Temps reel (messagerie, notifications) ----
  r('GET', /^rt:\/chat\/conversations$/, (_q, c) => conversations(c.profil)),
  r('GET', /^rt:\/chat\/conversations\/([^/]+)\/messages$/, (_q, c, [id]) => messages(c.profil, id)),
  r('GET', /^rt:\/notifications\/unread-count$/, (_q, c) => ({ count: notifications(c.profil).filter((n) => !n.readAt).length })),
  r('GET', /^rt:\/notifications$/, (_q, c) => ({ items: notifications(c.profil), total: notifications(c.profil).length })),
  r('GET', /^rt:\/notifications\/preferences$/, () => ({
    preferences: { TICKET_ASSIGNED: true, DEADLINE_DUE: true, CHAT_MESSAGE: true, DEMANDE_CLIENT: true, DOSSIER_TRANSFER: false },
    knownTypes: ['TICKET_ASSIGNED', 'DEADLINE_DUE', 'CHAT_MESSAGE', 'DEMANDE_CLIENT', 'DOSSIER_TRANSFER'],
  })),

  // ---- Super-administrateur ----
  r('GET', /^\/admin\/workspaces$/, () => CABINETS),
  r('GET', /^\/admin\/workspaces\/([0-9a-f-]{36})$/, (_q, _c, [id]) => detailCabinet(id)),
  r('GET', /^\/admin\/users$/, (q) => page(UTILISATEURS_ADMIN, q.query)),
  r('GET', /^\/admin\/audit$/, (q) => ({ ...page(journalAudit(), q.query), filters: {} })),
  r('GET', /^\/admin\/audit\/facets$/, () => ({
    actions: Array.from(new Set(JOURNAL.map((j) => j[0]))),
    sourceServices: Array.from(new Set(JOURNAL.map((j) => j[1]))),
  })),
  r('GET', /^\/admin\/essais$/, () => essaisAdmin()),
  r('GET', /^\/billing\/admin\/abonnements$/, () => abonnementsAdmin()),
  r('GET', /^\/billing\/admin\/paiements\/a-enregistrer$/, () => [paiement(3, 'PENDING', 0)]),
  r('GET', /^\/billing\/admin\/paiements$/, () => [paiement(1, 'COMPLETED', -120, 'SOUSCRIPTION'), paiement(2, 'FAILED', -60)]),
  r('GET', /^\/billing\/admin\/factures$/, () => [facture(1, -120)]),
  r('GET', /^\/billing\/admin\/contrats$/, () => lignesContratAdmin()),
  r('GET', /^\/billing\/admin\/contrats\/versions$/, () => [VERSION]),
  r('GET', /^\/billing\/admin\/editeur$/, () => editeur()),
  r('GET', /^\/billing\/admin\/editeur\/historique$/, () => [
    { champ: 'raisonSociale', libelle: 'Raison sociale', ancienneValeur: null, nouvelleValeur: 'Maghreb Consulting', modifiePar: P['super-admin'].email, modifieLe: instant(-30) },
  ]),
  r('GET', /^\/tickets\/referentiel-demarches$/, () => ({
    versionActive: 'CORPUS_2026-10-11',
    imports: [
      { id: '1e000000-0000-4000-8000-000000000001', corpus_version: 'CORPUS_2026-10-11', statut: 'APPLIQUE', lignes: 412, decisions: 96, renvois: 3, motif: null, importe_le: instant(-4), rapport: null },
      { id: '1e000000-0000-4000-8000-000000000002', corpus_version: 'CORPUS_2026-10-03', statut: 'APPLIQUE', lignes: 398, decisions: 80, renvois: 5, motif: null, importe_le: instant(-12), rapport: null },
    ],
  })),
  r('GET', /^\/chatbot\/sources$/, () => [
    { doc_id: 'src-1', source: 'Loi 5-96 relative aux societes (extrait).pdf', chunks: 184, uploaded_by: P['super-admin'].email, created_at: instant(-40) },
    { doc_id: 'src-2', source: 'Guide du registre du commerce.pdf', chunks: 62, uploaded_by: P['super-admin'].email, created_at: instant(-15) },
  ]),
];


