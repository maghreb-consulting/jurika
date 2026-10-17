import type { LigneDemarche, PointAttention, VueDemarches } from '../../../types/demarche';
import type { Ticket } from '../../../types/ticket';

/**
 * Lot L6c (E1) : donnees de demonstration des scenes de la vitrine.
 *
 * TOUTES FICTIVES ET FIXES : la societe "OASIS EXEMPLE SARL", sa gerante "Nadia Exemple" et
 * les autres tickets sont inventes (le mot "Exemple" le signale) ; aucun client reel, aucune
 * donnee d'un cabinet de demonstration, aucune lecture du serveur. Les dates sont figees :
 * la scene montre toujours la meme chose.
 *
 * Coherence metier : creation d'une SARL au Maroc (loi 5-96), libelles des etapes et des
 * demarches repris du parcours de creation de la plateforme (referentiel des demarches de
 * ticket-service, migration V24 ; etapes de pages/workflow/aidesParcours.ts).
 */

export const SOCIETE_DEMO = {
  nom: 'OASIS EXEMPLE',
  denomination: 'OASIS EXEMPLE SARL',
  titreTicket: 'Constitution Oasis Exemple',
  forme: 'SARL',
  ville: 'Casablanca',
  siege: '12, rue de l’Exemple, Casablanca',
  capital: '100 000',
  parts: '1 000',
  nominal: '100',
  objet: 'le conseil en organisation et la formation professionnelle',
  gerante: 'Mme Nadia Exemple',
  reference: 'T-EXEMPLE-0007',
} as const;

/** Ticket fictif, au format de l'API (types/ticket.ts) : rendu par les vrais composants. */
function ticket(champs: Pick<Ticket, 'id' | 'reference' | 'titre' | 'type' | 'statut' | 'priorite'> & Partial<Ticket>): Ticket {
  return {
    workspaceId: 'demo',
    dossierId: 'demo-dossier',
    assigneId: 'demo-employe',
    creeParId: 'demo-employe',
    description: null,
    deadline: null,
    annulationMotif: null,
    clotureAt: null,
    annuleAt: null,
    createdAt: '2026-03-02T09:00:00Z',
    assignePrenom: 'Nadia',
    assigneNom: 'Exemple',
    ...champs,
  };
}

/** Tickets de la page "Mes tickets" (fictifs), par statut. */
export const TICKETS_DEMO: Ticket[] = [
  ticket({ id: 'demo-3', reference: 'T-EXEMPLE-0003', titre: 'Dissolution Sable Exemple', type: 'DISSOLUTION', statut: 'CREATION_TICKET', priorite: 'NORMALE', description: 'Dissolution anticipée décidée par les associés.' }),
  ticket({ id: 'demo-5', reference: 'T-EXEMPLE-0005', titre: 'Transfert de siège Cèdre Exemple', type: 'MODIFICATION', statut: 'GENERATION_DOCUMENTS', priorite: 'HAUTE', description: 'Transfert du siège social à Rabat.' }),
  ticket({ id: 'demo-6', reference: 'T-EXEMPLE-0006', titre: 'Cession de parts Palmier Exemple', type: 'MODIFICATION', statut: 'DEROULEMENT_DEMARCHE', priorite: 'NORMALE', description: 'Cession de parts entre associés.' }),
  ticket({ id: 'demo-2', reference: 'T-EXEMPLE-0002', titre: 'Constitution Argan Exemple', type: 'CREATION', statut: 'CLOTURE_DOSSIER', priorite: 'BASSE', description: 'Création d’une SARL à associé unique.' }),
];

/** Le ticket de la creation montree par les scenes. */
export const TICKET_OASIS: Ticket = ticket({
  id: 'demo-7',
  reference: SOCIETE_DEMO.reference,
  titre: SOCIETE_DEMO.titreTicket,
  type: 'CREATION',
  statut: 'CREATION_TICKET',
  priorite: 'NORMALE',
});

/** Indicateurs du tableau de bord de l'employe (fictifs ; libelles et aides d'EmployeeDashboard). */
export const INDICATEURS_DEMO = [
  { label: 'Tickets en cours', valeur: 12, variant: 'default' as const, hint: 'dont 3 en création' },
  { label: 'En retard', valeur: 2, variant: 'danger' as const, hint: 'délai dépassé : à traiter en priorité' },
  { label: 'Délai sous 7 jours', valeur: 3, variant: 'warning' as const, hint: 'tickets dont le délai tombe cette semaine' },
  { label: 'Échanges à traiter', valeur: 4, variant: 'warning' as const, hint: '3 demandes non traitées · 1 réponse à valider' },
];

/** Prochaines actions du tableau de bord (fictives). */
/**
 * Lignes de la carte "Prochaine action" (fictives) : le texte de l'action et le badge du delai
 * sont calcules par les fonctions reelles (aideStatutTicket, etatDelai) a partir du statut et
 * du nombre de jours restants.
 */
export const ACTIONS_DEMO = [
  { reference: 'T-EXEMPLE-0007', titre: 'Constitution Oasis Exemple', statut: 'DEROULEMENT_DEMARCHE' as const, jours: -4 },
  { reference: 'T-EXEMPLE-0005', titre: 'Transfert de siège Cèdre Exemple', statut: 'GENERATION_DOCUMENTS' as const, jours: 3 },
  { reference: 'T-EXEMPLE-0003', titre: 'Dissolution Sable Exemple', statut: 'CREATION_TICKET' as const, jours: 12 },
];

/** Documents de l'etape "Generation des actes" (libelles de Step7Generation : DOCUMENT_LABELS). */
export const ACTES_DEMO = [
  { cle: 'statuts', libelle: 'Statuts', principal: true },
  { cle: 'annonce', libelle: 'Annonce légale', principal: false },
] as const;

/** Titre de la page des tickets de l'employe (components/tickets/libellesTickets.ts). */
export const TITRE_TICKETS_DEMO = 'Mes tickets';

/** Choix du theme de l'en-tete (store/themeStore.ts, LIBELLES_THEME), dans l'ordre du menu. */
export const LIBELLES_THEME_DEMO = ['Selon l’ordinateur', 'Clair', 'Sombre'] as const;

const PHASE = { code: 'S3', libelle: 'Déroulement de la démarche' } as const;

function ligne(ordre: number, libelle: string, champs: Partial<LigneDemarche>): LigneDemarche {
  return {
    demarcheId: `demo-${ordre}`,
    ordre,
    phaseCode: PHASE.code,
    phaseLibelle: PHASE.libelle,
    libelle,
    statutTicket: 'DEROULEMENT_DEMARCHE',
    acteur: null,
    organisme: null,
    obligatoire: true,
    conditionApplication: null,
    piecesEntrantes: null,
    documentProduit: null,
    justificatifsTexte: null,
    modeleJurika: null,
    delai: null,
    coutIndicatif: null,
    variablesAlimentees: null,
    delaiCalculable: true,
    delaiDepartManquant: null,
    justificatifsAttendus: [],
    etat: 'A_FAIRE',
    motif: null,
    cocheAt: null,
    documentsDeposes: [],
    actionnableMaintenant: true,
    horsSequence: false,
    formaliteCode: null,
    formaliteVolet: null,
    depotOrdre: null,
    deposeLe: null,
    journal: [],
    ...champs,
  };
}

/** Ordre de la demarche cochee pendant la scene. */
export const ORDRE_COCHEE_DEMO = 19;
export const LIBELLE_COCHEE_DEMO = 'Enregistrement des statuts au service de l’enregistrement (DGI) — dépôt';

/** Vue des demarches du ticket fictif, avant (`cochee` faux) ou apres le cochage. */
export function vueDemarchesDemo(cochee: boolean): VueDemarches {
  // Trois demarches du referentiel (une ligne de la frise) : la scene coche la 19.
  const lignes: LigneDemarche[] = [
    ligne(14, 'Légalisation des signatures des statuts, de l’acte de nomination et du pouvoir', { etat: 'COCHEE', cocheAt: '2026-03-04T10:00:00Z' }),
    // Delai et justificatif de la ligne 19 : ceux du referentiel (ticket-service, V24).
    ligne(ORDRE_COCHEE_DEMO, LIBELLE_COCHEE_DEMO, {
      delai: 'Dans les 30 jours de l’acte',
      justificatifsTexte: 'Récépissé de dépôt à l’enregistrement',
      justificatifsAttendus: [{ alternativeGroupe: 1, documentType: 'RECEPISSE_DEPOT', libelle: 'Récépissé de dépôt à l’enregistrement' }],
      ...(cochee ? { etat: 'COCHEE' as const, cocheAt: '2026-04-07T10:00:00Z', documentsDeposes: ['demo-recepisse'] } : {}),
    }),
    ligne(23, 'Demande d’inscription à la taxe professionnelle — dépôt', { echeance: '2026-04-10' }),
  ];
  const points: PointAttention[] = [
    { ordre: 23, libelle: lignes[2].libelle, delai: null, echeance: '2026-04-10', joursRestants: 3, severite: 'CRITIQUE' },
  ];
  if (!cochee) {
    points.unshift({ ordre: ORDRE_COCHEE_DEMO, libelle: LIBELLE_COCHEE_DEMO, delai: null, echeance: '2026-04-03', joursRestants: -4, severite: 'DEPASSE' });
  }
  const traitees = lignes.filter((l) => l.etat === 'COCHEE').length;
  return {
    workflowType: 'CREATION',
    statutCourant: 'DEROULEMENT_DEMARCHE',
    phases: [{ code: PHASE.code, libelle: PHASE.libelle, demarches: lignes, traitees, total: lignes.length }],
    avancement: {
      statutCourant: 'DEROULEMENT_DEMARCHE',
      position: 3,
      totalStatuts: 4,
      traitees,
      applicables: lignes.length,
      prochainOrdre: cochee ? 23 : ORDRE_COCHEE_DEMO,
      prochainLibelle: cochee ? lignes[2].libelle : LIBELLE_COCHEE_DEMO,
      pointsAttention: points,
    },
  };
}

/* ------------------------------------------------------------------ Data Room ---- */

/** Libelle du dossier de ticket dans la Data Room (TicketDossierLabel : type, reference, date). */
export const DOSSIER_TICKET_DEMO = `Création — ${SOCIETE_DEMO.reference} — 02/03/2026`;

/** Documents du dossier juridique (types : DOCUMENT_TYPE_LABELS ; groupes : GroupeDocument). */
export const DOCUMENTS_DATAROOM_DEMO = [
  { groupe: 'Actes générés', type: 'STATUTS' as const, titre: 'Statuts de la SARL', date: '05/03/2026' },
  { groupe: 'Actes générés', type: 'ANNONCE_JAL' as const, titre: 'Avis de constitution', date: '05/03/2026' },
  { groupe: 'Pièces client', type: 'CNIE_GERANT' as const, titre: 'CNIE de la gérante', date: '03/03/2026' },
  { groupe: 'Pièces client', type: 'CONTRAT_BAIL' as const, titre: 'Contrat de bail du siège', date: '03/03/2026' },
];

/** Demande du client recue (DemandesTab) : sujet et texte fictifs. */
export const DEMANDE_DEMO = {
  sujet: 'Attestation de domiciliation à jour',
  description: 'Bonjour, pouvez-vous me transmettre l’attestation de domiciliation à jour pour la banque ? Merci.',
  soumise: '07/04/2026 10:12',
};
