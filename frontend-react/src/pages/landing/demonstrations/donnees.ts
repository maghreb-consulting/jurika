import type { LigneDemarche, PointAttention, VueDemarches } from '../../../types/demarche';

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

/** Autres tickets de la liste (fictifs). */
export const TICKETS_DEMO = [
  { titre: 'Transfert de siège Cèdre Exemple', type: 'Modification', statut: 'Génération des documents', ton: 'info' as const },
  { titre: 'Dissolution Sable Exemple', type: 'Dissolution', statut: 'Déroulement de la démarche', ton: 'warning' as const },
] as const;

/** Indicateurs du tableau de bord de l'employe (fictifs). */
export const INDICATEURS_DEMO = [
  { label: 'Tickets en cours', valeur: 12, variant: 'default' as const },
  { label: 'En retard', valeur: 2, variant: 'danger' as const },
  { label: 'Délai sous 7 jours', valeur: 3, variant: 'warning' as const },
  { label: 'Échanges à traiter', valeur: 4, variant: 'default' as const },
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
    ligne(ORDRE_COCHEE_DEMO, LIBELLE_COCHEE_DEMO, cochee ? { etat: 'COCHEE', cocheAt: '2026-04-07T10:00:00Z' } : {}),
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
