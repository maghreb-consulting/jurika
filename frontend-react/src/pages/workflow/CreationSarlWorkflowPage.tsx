import { useNavigate } from 'react-router-dom';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ChevronLeft, ChevronRight, Eye, Save, X } from 'lucide-react';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { ticketService } from '../../services/ticket.service';
import { workflowService } from '../../services/workflow.service';
import { dataroomService } from '../../services/dataroom.service';
import { extractError } from '../../lib/api';
import type { FormeJuridique } from '../../types/ticket';
import type { DocumentType } from '../../types/dataroom';
import { WorkflowRoadmap } from './WorkflowRoadmap';
import { formatObjetSocial } from './objetSocial';
import { Step1Denomination } from './steps/Step1Denomination';
import { Step2Siege } from './steps/Step2Siege';
import { Step3Capital } from './steps/Step3Capital';
import { Step4Activite } from './steps/Step4Activite';
import { Step5Dirigeants } from './steps/Step5Dirigeants';
import { Step6Associes } from './steps/Step6Associes';
import { Step7Generation } from './steps/Step7Generation';
import { Step8PiecesJointes } from './steps/Step8PiecesJointes';
import { Step9Synthese } from './steps/Step9Synthese';
import { CancelTicketDialog } from '../tickets/CancelTicketDialog';
import { useWorkflow } from './useWorkflow';
import { WorkflowBoot } from './WorkflowBoot';

/** Forme juridique restreinte aux 2 valeurs supportees par la creation SARL. */
type SarlForm = 'SARL' | 'SARL_AU';

function readFormeJuridique(stepData: Record<string, Record<string, unknown>>): SarlForm {
  const step1 = stepData.step1 ?? {};
  const direct = step1.formeJuridique as FormeJuridique | undefined;
  const nested = (step1.denomination as Record<string, unknown> | undefined)?.formeJuridique as
    | FormeJuridique
    | undefined;
  const forme = direct ?? nested;
  return forme === 'SARL_AU' ? 'SARL_AU' : 'SARL';
}

/**
 * Mapping code piece -> DocumentType juridique (dataroom V2).
 *
 * RG transverse 2026-06-05 : tout document uploade OU genere doit etre depose
 * automatiquement dans la Data Room du dossier, dans la bonne categorie.
 * Le mapping ci-dessous reste volontairement simple ; en l'absence de
 * categorie exacte (CN, JUSTIFICATIF_SIEGE), on tombe sur AUTRE -- la dataroom
 * V2 conserve le titre litteral pour retrouvabilite.
 */
function mapPieceToDocumentType(code: string): DocumentType {
  // Fix A1 (2026-08-16) — les 3 documents GÉNÉRÉS de la création tombaient tous
  // en 'AUTRE' : le mapping testait `STATUTS_SARL` / `ANNONCE_JAL` /
  // `ACTE_NOMINATION` alors que la voie directeur émet les codes suffixés
  // `_DIRECTEUR` (`STATUTS_SARL_DIRECTEUR`, `ANNONCE_LEGALE_DIRECTEUR`,
  // `ACTE_NOMINATION_GERANT_DIRECTEUR`). Seules les pièces UPLOADÉES étaient donc
  // correctement classées ; les actes produits par la plateforme, eux, devenaient
  // invisibles dans « Documents en vigueur » et dans les filtres par type.
  // On neutralise le suffixe en tête de fonction : le reste du mapping est inchangé.
  const base = code.replace(/_DIRECTEUR$/, '');
  if (base === 'STATUTS_SARL' || base === 'STATUTS_SARL_AU' || base === 'STATUTS_VALIDES'
      || base === 'STATUTS') return 'STATUTS';
  if (base === 'ANNONCE_LEGALE' || base === 'ANNONCE_JAL') return 'ANNONCE_JAL';
  if (base === 'ACTE_NOMINATION_GERANT' || base === 'ACTE_NOMINATION') return 'ACTE_NOMINATION';
  if (code === 'STATUTS_SARL' || code === 'STATUTS_VALIDES' || code === 'STATUTS') return 'STATUTS';
  if (code.startsWith('CIN_DIRIGEANT_') || code.startsWith('CIN_ASSOCIE_') || code === 'CIN_DIRIGEANTS')
    return 'CNIE_GERANT';
  if (code === 'JUSTIFICATIF_SIEGE' || code === 'CONTRAT_BAIL'
      || code === 'ATTESTATION_DOMICILIATION') return 'CONTRAT_BAIL';
  if (code === 'ACTE_NOMINATION') return 'ACTE_NOMINATION';
  if (code === 'ANNONCE_JAL') return 'ANNONCE_JAL';
  if (code === 'RC') return 'RC';
  if (code === 'ICE') return 'ICE';
  // EX5 2026-06-09 — Justificatifs MORALE (dirigeant ou associe entite).
  if (code.startsWith('RC_DIRIGEANT_MORALE_') || code.startsWith('RC_ASSOCIE_MORALE_')) return 'RC';
  if (code.startsWith('STATUTS_ENTITE_DIRIGEANT_') || code.startsWith('STATUTS_ENTITE_ASSOCIE_')) return 'STATUTS';
  // F2 2026-06-09 — CIN du representant legal MORALE.
  if (code.startsWith('CIN_REP_DIRIGEANT_MORALE_') || code.startsWith('CIN_REP_ASSOCIE_MORALE_')) return 'CNIE_GERANT';
  // CN, BLOCAGE_CAPITAL, AUTORISATION_PREF, EXTRA_* etc.
  return 'AUTRE';
}

function buildPayloadCreationSarl(stepData: Record<string, Record<string, unknown>>): Record<string, unknown> {
  // 2026-06-11 (fix payload nesting tous steps) — le backend stocke chaque step
  // sous une cle dediee (Step1->denomination, Step2->siege, Step3->capital,
  // Step4->activite). Cf. CreationSarlWorkflow.handleSiege qui fait
  // StepResult.ok(Map.of("siege", p)). Sans denester ici, siege.adresse /
  // cap.capitalSocialMad / den.ice etaient undefined -> rendu "Capital 0",
  // "Siège : ", "ICE : " vides + denomination serialisee {ice=..., ...}.
  const unwrapStep = (step: unknown, key: string): Record<string, unknown> => {
    const raw = (step as Record<string, unknown> | undefined) ?? {};
    const nested = raw[key];
    if (typeof nested === 'object' && nested !== null && !Array.isArray(nested)) {
      return nested as Record<string, unknown>;
    }
    return raw;
  };
  const den = unwrapStep(stepData.step1, 'denomination');
  const siege = unwrapStep(stepData.step2, 'siege');
  const cap = unwrapStep(stepData.step3, 'capital');
  const act = unwrapStep(stepData.step4, 'activite');
  const dirs = ((stepData.step5 as { dirigeants?: Array<Record<string, unknown>> })?.dirigeants ?? []) as Array<Record<string, unknown>>;
  const ass = ((stepData.step6 as { associes?: Array<Record<string, unknown>> })?.associes ?? []) as Array<Record<string, unknown>>;
  // 2026-08 (Phase 3-B) — Step9 : options avancees + post-immatriculation (RC, depot legal).
  const postImmat = unwrapStep(stepData.step9, 'postImmat');
  // 2026-08 (contrat CREATION) — Step9 : parametres de l'acte (societe.*).
  const acte = unwrapStep(stepData.step9, 'acteParams');
  // 2026-08 (contrat CREATION) — Step5 : signataires autorises (art. 15).
  const signataires5 =
    ((stepData.step5 as { signataires?: Array<Record<string, unknown>> })?.signataires ?? []) as Array<Record<string, unknown>>;
  const statutaire = dirs.find((d) => d.isStatutaire) ?? dirs[0];
  const formeJuridique = readFormeJuridique(stepData);

  // EX5 2026-06-09 — Le gerant statutaire peut etre PHYSIQUE ou MORALE.
  // Pour MORALE, on serialise l'entite + son representant legal pour les
  // mappers backend (statuts / acte nomination).
  function gerantPayload(d: Record<string, unknown> | undefined): Record<string, unknown> | undefined {
    if (!d) return undefined;
    const typePersonne = (d.typePersonne as string) ?? 'PHYSIQUE';
    if (typePersonne === 'MORALE') {
      return {
        typePersonne: 'MORALE',
        denomination: d.denomination,
        formeJuridique: d.formeJuridiqueEntite,
        rc: d.rc,
        ice: d.ice,
        ifFiscal: d.ifFiscal,
        siege: d.siege,
        // 2026-06-11 — capital + delib pour Statuts MORALE.
        capitalEntite: d.capitalEntite,
        deliberationDate: d.deliberationDate,
        nom: d.denomination, // fallback pour mappers legacy (champ {GERANT_NOM})
        adresse: d.siege,
        representantLegal: {
          civilite: d.repCivilite,
          nom: d.repNom,
          prenom: d.repPrenom,
          cin: d.repCin,
          adresse: d.repAdresse,
          dateNaissance: d.repDateNaissance,
          lieuNaissance: d.repLieuNaissance,
          nationalite: d.repNationalite ?? 'Marocaine',
          pieceValidite: d.repPieceValidite,
          qualite: d.repQualite,
        },
      };
    }
    return {
      typePersonne: 'PHYSIQUE',
      // 2026-08 (Phase 3) — identite separee pour la voie directeur.
      civilite: d.civilite,
      prenom: d.prenom,
      nom: d.nom,
      cin: d.cinNumero,
      pieceType: d.pieceType ?? 'CIN',
      pieceNumero: d.pieceNumero ?? d.cinNumero,
      nationalite: d.nationalite ?? 'Marocaine',
      adresse: d.adresse,
      // 2026-06-11 — etat civil pour Statuts.
      dateNaissance: d.dateNaissance,
      lieuNaissance: d.lieuNaissance,
      pieceValidite: d.pieceValidite,
    };
  }

  function associePayload(a: Record<string, unknown>): Record<string, unknown> {
    const typePersonne = (a.typePersonne as string) ?? 'PHYSIQUE';
    // BLOC A 2026-06-21 — Apports mixtes : on transmet apports[] (1 ligne par
    // type) au mapper Java pour que les Statuts ventilent NUMERAIRE/NATURE/
    // INDUSTRIE par associe. nombreParts/montantApport restent transmis pour
    // les totaux (les anciens consommateurs lisent encore ces cles).
    const apportsRaw = Array.isArray(a.apports) ? (a.apports as Array<Record<string, unknown>>) : [];
    const valeurNominale = Number(cap.valeurNominale ?? 0);
    const apports = apportsRaw.map((line) => {
      const parts = Number(line?.parts ?? 0);
      const montant = valeurNominale > 0 ? Math.round(parts * valeurNominale * 100) / 100 : 0;
      return {
        type: (line?.type as string) ?? 'NUMERAIRE',
        parts,
        montant,
      };
    });
    const common = {
      typePersonne,
      // 2026-06-21 — typeApport (dominant) propage en compat retro. Le mapper
      // lit apports[] en priorite quand present.
      typeApport: (a.typeApport as string) ?? 'NUMERAIRE',
      // 2026-08 (Phase 3) — voie directeur : apportType (libelle exact) + estGerant.
      apportType: (a.apportType as string) ?? (a.typeApport as string) ?? 'numéraire',
      estGerant: a.estGerant === true || a.estGerant === 'oui',
      // 2026-08 (contrat CREATION) — genre grammatical saisi en Step6 (defaut civilite).
      genre:
        (a.genre as string) ??
        (typePersonne === 'MORALE'
          ? 'féminin'
          : (a.civilite as string) === 'Mme'
            ? 'féminin'
            : 'masculin'),
      apportNatureDescription: a.apportNatureDescription ?? a.descriptionNature,
      apportNatureValeur: a.apportNatureValeur ?? a.montantNature,
      apportIndustrieDescription: a.apportIndustrieDescription ?? a.descriptionIndustrie,
      apports,
      // Totaux transmis pour les consommateurs legacy (bloc APPORTS_NUMERAIRE
      // sans apports[], SARL_AU, etc.).
      nombreParts: Number(a.nombreParts ?? 0),
      montantApport: Number(a.montantApport ?? 0),
      partsChiffres: Number(a.nombreParts ?? 0),
      pourcentage: Number(a.pourcentageDetention ?? 0),
      montantSouscrit: Number(a.montantApport ?? 0),
      montantVerse: Number(a.montantApport ?? 0),
    };
    if (typePersonne === 'MORALE') {
      return {
        ...common,
        nom: a.denomination, // alias pour mappers legacy
        adresse: a.siege,
        denomination: a.denomination,
        formeJuridique: a.formeJuridiqueEntite,
        rc: a.rc,
        ice: a.ice,
        ifFiscal: a.ifFiscal,
        siege: a.siege,
        // 2026-08 (Phase 3) — clés attendues par la voie directeur (associé PM).
        forme: a.forme ?? a.formeJuridiqueEntite,
        capital: a.capital ?? a.capitalEntite,
        rcVille: a.rcVille,
        rcNumero: a.rcNumero ?? a.rc,
        representantNom:
          (a.representantNom as string) ??
          (a.representant as string) ??
          `${(a.repPrenom as string) ?? ''} ${(a.repNom as string) ?? ''}`.trim(),
        representantQualite: a.repQualite ?? a.representantQualite ?? 'Gérant',
        representantLegal: {
          civilite: a.repCivilite,
          nom: a.repNom,
          prenom: a.repPrenom,
          cin: a.repCin,
          adresse: a.repAdresse,
          dateNaissance: a.repDateNaissance,
          lieuNaissance: a.repLieuNaissance,
          nationalite: a.repNationalite ?? 'Marocaine',
          pieceValidite: a.repPieceValidite,
          qualite: a.repQualite,
        },
      };
    }
    return {
      ...common,
      // 2026-08 (Phase 3) — voie directeur : identite separee (civilite/prenom/nom).
      // Le mapper legacy reconstruit le nom complet via computeFullName(prenom+nom).
      civilite: a.civilite,
      prenom: a.prenom,
      nom: a.nom,
      adresse: a.adresse,
      cin: a.cin ?? a.cinNumero,
      pieceType: a.pieceType ?? 'CIN',
      pieceNumero: a.pieceNumero ?? a.cin ?? a.cinNumero,
      dateNaissance: a.dateNaissance,
      lieuNaissance: a.lieuNaissance,
      nationalite: a.nationalite ?? 'Marocaine',
    };
  }

  // 2026-06-21 — depot bancaire saisi en Step3 (objet imbrique + variantes plates).
  const capDepot = (cap.depot as Record<string, unknown> | undefined) ?? {};
  // C1 2026-06-21 — Gouvernance (Step5) injectee dans le gerant statutaire :
  // le mapper lit dureeMandat / dureeAnnees / remunerationMode / remunerationMontant
  // depuis l'objet gerant. Champs rendus OBLIGATOIRES cote Step5 + back.
  const gerance5 =
    ((stepData.step5 as { gerance?: Record<string, unknown> })?.gerance) ?? {};
  const geranceForGerant = {
    dureeMandat: gerance5.dureeMandat,
    dureeAnnees: gerance5.dureeAnnees,
    remunerationMode: gerance5.remunerationMode,
    remunerationMontant: gerance5.remunerationMontant,
  };

  /**
   * 2026-08-18 — Mandat PROPRE au dirigeant, prioritaire sur l'agrégat.
   *
   * La gérance se saisit désormais par dirigeant (durée, rémunération,
   * limitation de pouvoirs). Chaque gérant emporte donc SON mandat ; l'agrégat
   * `geranceForGerant` ne sert plus que de repli pour les brouillons antérieurs,
   * où seule la saisie globale existait.
   */
  function mandatDuDirigeant(d: Record<string, unknown>): Record<string, unknown> {
    const type = d.dureeMandatType as string | undefined;
    if (!type) return {};
    const annees = Number(d.dureeAnnees ?? 0);
    const mode = (d.remunerationMode as string) ?? '';
    const phrases: Record<string, string> = {
      non_remunere: 'non rémunéré',
      decision_collective: 'fixée par décision collective des associés',
      montant_fixe: 'montant fixe',
    };
    return {
      dureeMandatType: type,
      dureeAnnees: type === 'determinee' ? annees : null,
      dureeMandat:
        type === 'determinee' && annees > 0
          ? `${annees} année(s)`
          : "illimitée (jusqu'à révocation)",
      remunerationMode: mode ? phrases[mode] ?? mode : '',
      remunerationModeKey: mode,
      remunerationMontant:
        mode === 'montant_fixe' ? Number(d.remunerationMontant ?? 0) : null,
    };
  }
  return {
    societe: {
      denomination: den.denomination,
      formeJuridique,
      // 2026-06-22 — sigle facultatif : vide => « néant » automatiquement
      // (pas de saisie forcée ; le gabarit reçoit toujours une valeur).
      sigle: ((den.sigle as string) ?? '').trim() || 'néant',
      capitalChiffres: Number(cap.capitalSocialMad ?? 0),
      adresseSiege: siege.adresse,
      // 2026-08 — objet social multi-activités : phrase si 1 activité, liste à
      // tirets (retours ligne) si plusieurs. Le moteur rend les \n en <w:br/>.
      objetSocial: formatObjetSocial(act.activites, act.description),
      activiteSociete: act.description,
      nombreParts: Number(cap.nombreParts ?? 0),
      valeurPart: Number(cap.valeurNominale ?? 0),
      dureeAnnees: Number(cap.dureeAnnees ?? 99),
      iceNumero: den.ice,
      // 2026-08 (dé-dup) — exercice social : SOURCE UNIQUE = Step3 (capital) qui
      // saisit la date de commencement ET calcule la fin (début + durée). La saisie
      // redondante Step4 (dateDebutExercice) a été supprimée. La valeur textuelle
      // « 1er janvier » du statut vient de Step9 (acteParams.exerciceDebut) si fournie.
      exerciceDebut: (acte.exerciceDebut as string) ?? cap.dateCommencement,
      exerciceFin: (acte.exerciceFin as string) ?? cap.dateFin,
      premierExerciceFin: cap.dateFin,
      // 2026-08 (Phase 2-B) — champs consommés par la VOIE DIRECTEUR
      // (CreationDirecteurVarsBuilder). Lisent les saisies existantes quand
      // elles existent (durée mandat Step5, exercices, commissaire aux apports,
      // dépôt Step3) ; sinon le mapper applique ses défauts légaux.
      // 2026-08 (dé-dup) — VILLE_GREFFE : source unique = Step2 (siege.villeGreffe),
      // la seule saisie du tribunal de commerce compétent. Fallback legacy rcVille.
      // 2026-08 (fix VILLE_GREFFE) — TOUJOURS une ville de tribunal de commerce,
      // jamais une commune : villeGreffe (Step2) puis, à défaut, la province/ville
      // du siège. `cap.rcVille` reste en dernier recours (jamais `siege.commune`).
      villeGreffe:
        (siege.villeGreffe as string) ??
        (siege.province as string) ??
        cap.rcVille,
      // 2026-08 (dé-dup) — durée de la société : SOURCE UNIQUE = Step3 (capital,
      // clé dureeAnnees), qui l'utilise aussi pour calculer la date de fin d'exercice.
      dureeSociete: Number(cap.dureeAnnees ?? 99),
      premierExerciceCloture: (acte.premierExerciceCloture as string) ?? cap.dateFin,
      gerantModeDesignation:
        (gerance5.gerantModeDesignation as string) ??
        (statutaire ? 'statutaire' : 'non statutaire'),
      dureeGerance: gerance5.dureeGerance ?? gerance5.dureeMandat,
      limitationPouvoirs: gerance5.limitationPouvoirs,
      // 2026-08 (contrat CREATION) — commissaire aux comptes saisi en Step9.
      commissaireComptesNom: (acte.commissaireComptesNom as string) ?? cap.commissaireComptesNom,
      commissaireApportsNom: cap.commissaireApportsNom,
      // Options de constitution (UI dédiée à compléter — défauts mapper sinon).
      modeLiberation: cap.modeLiberation,
      depotFondsBloque: cap.depotFondsBloque,
      banqueDepositaire: cap.depotBanqueNom ?? capDepot.banque,
      compteBancaireNumero: cap.depotNumero ?? capDepot.numero,
      // 2026-08 (contrat CREATION) — signature sociale (art. 15) saisie en Step5.
      modeSignature: cap.modeSignature ?? gerance5.modeSignature,
      signaturePlafond: cap.signaturePlafond ?? gerance5.signaturePlafond,
      signatureMandataire: gerance5.signatureMandataire ?? cap.signatureMandataire,
      mandataireNom: gerance5.mandataireNom,
      mandataireActeDelegation: gerance5.mandataireActeDelegation,
      modeSignatureAdmin: gerance5.modeSignatureAdmin,
      engagementsMandat: (acte.engagementsMandat as string) ?? cap.engagementsMandat,
      // 2026-08 (contrat CREATION) — options avancees saisies en Step9 (acteParams).
      lieuSignature:
        (acte.lieuSignature as string) ??
        (cap.lieuSignature as string) ??
        (siege.villeGreffe as string) ??
        (siege.ville as string) ??
        cap.rcVille,
      dureeMandatCac: acte.dureeMandatCac ?? cap.dureeMandatCac,
      nombreOriginaux: acte.nombreOriginaux ?? cap.nombreOriginaux,
      articleDesignationStatuts: (acte.articleDesignationStatuts as string) ?? cap.articleDesignationStatuts,
      heureActe: (acte.heureActe as string) ?? cap.heureActe,
      // 2026-08 (Phase 3-B) — Post-immatriculation : RC + depot legal saisis en
      // Step9 (post-depot). Consommes par ANNONCE_LEGALE_DIRECTEUR ; tant qu'absents,
      // le mapper rend « a completer apres immatriculation ».
      rcNumero: (postImmat.rcNumero as string) ?? den.rcNumero,
      dateDepotLegal: (postImmat.dateDepotLegal as string) ?? postImmat.dateDepot,
      dateActe: (postImmat.dateSignature as string) ?? cap.dateSignature,
    },
    gerant: statutaire
      ? {
          ...gerantPayload(statutaire),
          ...geranceForGerant,
          ...mandatDuDirigeant(statutaire),
        }
      : undefined,
    // 2026-08 (Phase 3) — voie directeur : TOUS les gérants (boucle GERANTS).
    // L'agrégat d'abord, le mandat propre du dirigeant ENSUITE : il l'emporte.
    gerants: dirs.map((d) => ({
      ...gerantPayload(d),
      ...geranceForGerant,
      ...mandatDuDirigeant(d),
      isStatutaire: d.isStatutaire,
    })),
    associes: ass.map(associePayload),
    // 2026-08 (contrat CREATION) — signataires autorises (art. 15) saisis en Step5.
    signataires: signataires5.map((s) => ({ nom: s.nom, qualite: s.qualite })),
    depot: {
      capitalLibere: Number(cap.capitalLibere ?? 0),
      capitalNumeraireLibere: Number(cap.apportNumeraireLibere ?? 0),
      // 2026-06-21 — banque + numero de depot saisis Step3 (etaient droppes
      // -> DEPOT_BANQUE_NOM / DEPOT_NUMERO en rouge).
      banque: cap.depotBanqueNom ?? capDepot.banque,
      numero: cap.depotNumero ?? capDepot.numero,
    },
    // 2026-06-21 — extra "variables par etape" lu au niveau racine par le mapper
    // (applyStatutsExtras2026_06_19) : commissaire aux apports si apport en nature.
    commissaireApportsNom: cap.commissaireApportsNom,
  };
}

/**
 * Monte {@link CreationSarlWorkflowPageBody} SOUS {@link WorkflowBoot} : les champs de chaque
 * etape s'initialisent avec `useState(stepData...)`, qui ne lit sa valeur qu'au
 * premier render. Sans ce montage differe, ce premier render a lieu AVANT la
 * reponse du serveur et tous les champs restent vides apres un rechargement
 * (F5, deconnexion/reconnexion), meme sur une etape deja validee.
 */
export function CreationSarlWorkflowPage() {
  return (
    <WorkflowBoot>
      <CreationSarlWorkflowPageBody />
    </WorkflowBoot>
  );
}

function CreationSarlWorkflowPageBody() {
  const navigate = useNavigate();
  const {
    ticket, progress, loading, saving, error, stepData,
    viewStep, maxStep, saveDraft, executeStep, goToStep, goPrev, goNext, reload,
    registerDirty,
  } = useWorkflow();
  const [showCancel, setShowCancel] = useState(false);
  const [datasetWarnings, setDatasetWarnings] = useState<string[]>([]);
  // Fix 2026-06-07 (BUG 3) — Forme juridique definie a la creation du
  // ticket, lue depuis le dossier rattache et propagee aux steps en
  // lecture seule. Plus jamais redemandee a l'utilisateur.
  const [ticketForme, setTicketForme] = useState<SarlForm | null>(null);
  // 2026-08-12 (consultation lecture seule) — miroir `ref` du flag `readOnly`
  // (calculé plus bas après chargement de progress/ticket). Les callbacks
  // mémoïsées (onPieceUploaded / onDocumentGenerated) le lisent à l'exécution
  // pour NE JAMAIS écrire côté backend quand le ticket est clôturé/annulé/terminé.
  const readOnlyRef = useRef(false);

  useEffect(() => {
    if (!ticket?.dossierId) return;
    let cancelled = false;
    void dataroomService.listDossiers()
      .then((list) => {
        if (cancelled) return;
        const d = list.find((x) => x.id === ticket.dossierId);
        const forme = (d?.formeJuridique ?? '').toString().toUpperCase();
        if (forme === 'SARL_AU' || forme === 'SARL') {
          setTicketForme(forme as SarlForm);
        }
      })
      .catch(() => {
        // Best-effort : si on ne peut pas lire la forme du dossier on
        // tombe sur le legacy (lecture depuis stepData.step1).
      });
    return () => { cancelled = true; };
  }, [ticket?.dossierId]);

  /**
   * Cascade depot Dataroom + registre piece persistant. Le best-effort cote
   * dataroom ne bloque jamais le wizard (les erreurs sont consignees dans
   * datasetWarnings pour visibilite UX, sans interrompre l'utilisateur).
   */
  const onPieceUploaded = useCallback(
    async (code: string, label: string, file: File) => {
      if (readOnlyRef.current) return; // consultation lecture seule : aucune écriture.
      if (!ticket?.id) return;
      // 1) Registre cross-step (metadata seulement, pas le binaire).
      try {
        await workflowService.registerPiece(ticket.id, {
          code,
          label,
          filename: file.name,
          sizeBytes: file.size,
          contentType: file.type || 'application/octet-stream',
          uploadedAtStep: viewStep,
        });
      } catch (err) {
        setDatasetWarnings((prev) => [
          ...prev,
          `Enregistrement piece ${code} : ${err instanceof Error ? err.message : 'echec'}`,
        ]);
      }
      // 2) Depot Dataroom (uniquement si dossier deja attache au ticket).
      if (ticket.dossierId) {
        try {
          await dataroomService.uploadJuridique(ticket.dossierId, {
            file,
            documentType: mapPieceToDocumentType(code),
            title: label,
            ticketId: ticket.id,
          });
        } catch (err) {
          setDatasetWarnings((prev) => [
            ...prev,
            `Depot Dataroom ${label} : ${err instanceof Error ? err.message : 'echec'}`,
          ]);
        }
      }
      // 3) Reload progress pour rafraichir les pieces enregistrees.
      void reload();
    },
    [ticket?.id, ticket?.dossierId, viewStep, reload],
  );

  const onDocumentGenerated = useCallback(
    async (
      templateCode: string,
      title: string,
      blob: Blob,
      filename: string,
    ) => {
      if (readOnlyRef.current) return; // consultation lecture seule : aucun dépôt.
      if (!ticket?.id || !ticket.dossierId) return;
      try {
        const file = new File([blob], filename, {
          type: blob.type || 'application/octet-stream',
        });
        await dataroomService.uploadJuridique(ticket.dossierId, {
          file,
          documentType: mapPieceToDocumentType(templateCode),
          title,
          ticketId: ticket.id,
        });
      } catch (err) {
        setDatasetWarnings((prev) => [
          ...prev,
          `Depot Dataroom (genere) ${title} : ${err instanceof Error ? err.message : 'echec'}`,
        ]);
      }
    },
    [ticket?.id, ticket?.dossierId],
  );

  // 2026-08-12 (consultation lecture seule) — registerDirty NEUTRALISÉ : en
  // lecture seule, les étapes ne s'enregistrent pas → la navigation entre étapes
  // (goToStep/goPrev/goNext → flushDraft) ne déclenche AUCUN saveDraft backend.
  const noopRegisterDirty = useCallback(() => () => {}, []);

  // Liste dirigeants propages (isAssociate=true) pour Step6.
  // EX5 2026-06-09 — Supporte aussi typePersonne MORALE (propage RC/ICE/IF + representant).
  const propagatedAssociates = useMemo(() => {
    type RawDir = {
      id: string;
      typePersonne?: 'PHYSIQUE' | 'MORALE';
      civilite?: 'M' | 'Mme';
      nom?: string;
      prenom?: string;
      cinNumero?: string;
      adresse?: string;
      isAssociate?: boolean;
      cinUploaded?: boolean;
      cinFileName?: string;
      // 2026-06-11 — Etat civil PHYSIQUE.
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
      repCinUploaded?: boolean;
      repCinFileName?: string;
    };
    const dirs = ((stepData.step5 as {
      dirigeants?: RawDir[];
    })?.dirigeants ?? []) as RawDir[];
    return dirs
      .filter((d) => d.isAssociate)
      .map((d) => ({
        id: d.id,
        typePersonne: (d.typePersonne ?? 'PHYSIQUE') as 'PHYSIQUE' | 'MORALE',
        civilite: d.civilite ?? 'M',
        nom: d.nom ?? '',
        prenom: d.prenom ?? '',
        cinNumero: d.cinNumero ?? '',
        adresse: d.adresse ?? '',
        cinUploaded: !!d.cinUploaded,
        cinFileName: d.cinFileName,
        // 2026-06-11 — Etat civil PHYSIQUE propage.
        nationalite: d.nationalite,
        dateNaissance: d.dateNaissance,
        lieuNaissance: d.lieuNaissance,
        pieceValidite: d.pieceValidite,
        // MORALE pass-through
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
      }));
  }, [stepData.step5]);

  // Registre pieces persistantes (lecture seule depuis progress.data.pieces).
  const registeredPieces = useMemo(
    () =>
      (progress?.data?.pieces as
        | Record<string, { code: string; label: string; filename?: string }>
        | undefined) ?? {},
    [progress?.data?.pieces],
  );

  if (loading) {
    return (
      <div className="flex h-64 items-center justify-center">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-border border-t-indigo-600" />
      </div>
    );
  }

  if (!ticket || !progress) {
    return (
      <div className="rounded-lg border border-danger/40 bg-danger/10 p-6 text-sm text-danger">
        {error ?? 'Workflow introuvable'}
      </div>
    );
  }

  const step = viewStep;
  // Fix BUG 3 : si la forme est connue depuis le ticket/dossier, elle
  // EMPORTE sur ce que pourrait contenir stepData.step1 (legacy).
  const formeJuridique: SarlForm = ticketForme ?? readFormeJuridique(stepData);
  const formeLabel =
    formeJuridique === 'SARL_AU'
      ? 'Mode : SARL_AU (1 associe)'
      : 'Mode : SARL multi-associes';

  // 2026-08-12 (consultation lecture seule) — un workflow TERMINÉ, ou un ticket
  // CLOTURÉ / ANNULÉ, reste CONSULTABLE mais NON modifiable : valeurs saisies +
  // étapes + documents générés visibles, zéro écriture backend. Le flag est
  // ADDITIF : le mode normal (NOUVEAU / EN_COURS) est inchangé.
  const readOnly =
    progress.statut === 'TERMINE' ||
    ticket.statut === 'CLOTURE_DOSSIER' ||
    ticket.statut === 'ANNULE';
  // Miroir ref lu par les callbacks mémoïsées (upload / dépôt document).
  readOnlyRef.current = readOnly;
  // registerDirty effectif : neutralisé en lecture seule.
  const effectiveRegisterDirty = readOnly ? noopRegisterDirty : registerDirty;

  async function submit(s: number, p: Record<string, unknown>): Promise<void> {
    if (readOnly) return; // aucune validation d'étape en consultation.
    await executeStep(s, p);
  }
  async function draft(s: number, p: Record<string, unknown>) {
    if (readOnly) return; // aucun brouillon en consultation.
    await saveDraft(s, p);
  }

  /**
   * Finalisation Creation (etape 9). Apres la finalisation du workflow
   * (executeStep 9 -> ticket CLOTURE + creation/lien du dossier cote backend),
   * on met a jour les identifiants du dossier. L'orchestration est cote front
   * (workflow-service n'a pas de client dataroom-service).
   *
   * Lot 1 (2026-09-04) : l'ouverture d'un exercice fiscal a disparu avec le
   * dossier fiscal. Les echeances du dossier viennent desormais des delais
   * legaux portes par les demarches du parcours, sinon
   * annee courante. Le 409 EXERCICE_EXISTS est absorbe (best-effort, ne bloque jamais).
   */
  async function finalizeCreation(): Promise<void> {
    if (readOnly) return; // consultation lecture seule : pas de finalisation.
    await executeStep(9, {});
    try {
      // 2026-08 (dé-dup) — la date de commencement de l'exercice a une source UNIQUE :
      // Step3 (capital.dateCommencement). Step4 ne la saisit plus.
      // Le dossier peut etre (re)cree/relie a la finalisation cote backend :
      // on relit le ticket pour obtenir le dossierId le plus a jour.
      let dossierId = ticket?.dossierId ?? '';
      try {
        const fresh = await ticketService.get(ticket!.id);
        if (fresh?.dossierId) dossierId = fresh.dossierId;
      } catch {
        // best-effort : on garde le dossierId courant.
      }

      if (dossierId) {
        /*
         * Fix DR5 (2026-08-16) — LES IDENTIFIANTS SAISIS EN WORKFLOW REJOIGNENT LA FICHE.
         *
         * `PATCH /dossiers/{id}/identifiants` n'était appelé QUE par le tiroir
         * « Identifiants » de la Data Room (saisie manuelle). Aucune page de workflow
         * ne l'appelait : le post-immatriculation de la création alimentait seulement
         * les variables du document (`ANNONCE_LEGALE_DIRECTEUR`), pas le dossier. RC,
         * ICE, IF, patente et CNSS renseignés ici n'apparaissaient donc nulle part
         * dans la fiche — et l'utilisateur devait les RE-SAISIR, ce qu'interdit la
         * règle « zéro re-saisie ».
         *
         * Best-effort : un échec de propagation ne doit jamais annuler une
         * finalisation déjà réussie ; il est remonté comme avertissement.
         */
        try {
          const s1 = (stepData.step1 as Record<string, unknown> | undefined) ?? {};
          const den = (s1.denomination as Record<string, unknown> | undefined) ?? s1;
          const s2 = (stepData.step2 as Record<string, unknown> | undefined) ?? {};
          const siege = (s2.siege as Record<string, unknown> | undefined) ?? s2;
          const s9 = (stepData.step9 as Record<string, unknown> | undefined) ?? {};
          const pi = (s9.postImmat as Record<string, unknown> | undefined) ?? {};
          const txt = (v: unknown) => {
            const s = v == null ? '' : String(v).trim();
            return s ? s : null;
          };
          const payload = {
            ice: txt(pi.ice ?? den.ice),
            rcNumero: txt(pi.rcNumero ?? den.rcNumero),
            rcTribunal: txt(pi.rcTribunal ?? siege.villeGreffe ?? siege.tribunal),
            identifiantFiscal: txt(pi.identifiantFiscal ?? pi.ifNumero ?? den.ifFiscal),
            taxeProfessionnelle: txt(pi.taxeProfessionnelle ?? pi.patente),
            cnss: txt(pi.cnss),
          };
          // N'appelle l'API que si au moins un identifiant est réellement connu.
          if (Object.values(payload).some((v) => v !== null)) {
            await dataroomService.updateIdentifiants(dossierId, payload);
          }
        } catch (err) {
          setDatasetWarnings((prev) => [
            ...prev,
            `Mise a jour des identifiants du dossier : ${extractError(err).message}`,
          ]);
        }

        // Lot 1 (2026-09-04) — l'ouverture d'un exercice fiscal a la finalisation
        // disparait avec le dossier fiscal. Les echeances du dossier viennent
        // desormais des delais legaux portes par les demarches du parcours.
      }
    } catch {
      // best-effort : la finalisation du workflow a deja reussi.
    }
  }

  // 2026-08-12 (consultation lecture seule) — corps des étapes HORS étape 7,
  // extrait pour pouvoir l'envelopper dans un <fieldset disabled> en lecture
  // seule (désactive tous les inputs + boutons d'action → aucune écriture) SANS
  // neutraliser les boutons Aperçu/Télécharger de l'étape 7, qui gère son propre
  // `readOnly`. `effectiveRegisterDirty` est neutralisé en lecture seule.
  const stepBodyExcept7 = (
    <>
      {step === 1 && (
        <Step1Denomination
          existing={stepData.step1}
          lockedFormeJuridique={ticketForme}
          defaultDenomination={ticket?.titre}
          saving={saving}
          onSubmit={(p) => submit(1, p)}
          onSave={(p) => draft(1, { step1: p })}
          registerDirty={effectiveRegisterDirty}
          onPieceUploaded={onPieceUploaded}
          dossierId={ticket?.dossierId ?? null}
        />
      )}
      {step === 2 && (
        <Step2Siege existing={stepData.step2} saving={saving} onSubmit={(p) => submit(2, p)} onSave={(p) => draft(2, { step2: p })} registerDirty={effectiveRegisterDirty} onPieceUploaded={onPieceUploaded} />
      )}
      {step === 3 && (
        <Step3Capital existing={stepData.step3} saving={saving} onSubmit={(p) => submit(3, p)} onSave={(p) => draft(3, { step3: p })} registerDirty={effectiveRegisterDirty} />
      )}
      {step === 4 && (
        <Step4Activite existing={stepData.step4} saving={saving} onSubmit={(p) => submit(4, p)} onSave={(p) => draft(4, { step4: p })} registerDirty={effectiveRegisterDirty} />
      )}
      {step === 5 && (
        <Step5Dirigeants
          existing={stepData.step5}
          formeJuridique={formeJuridique}
          saving={saving}
          onSubmit={(p) => submit(5, p)}
          onSave={(p) => draft(5, { step5: p })}
          registerDirty={effectiveRegisterDirty}
          onPieceUploaded={onPieceUploaded}
          dossierId={ticket?.dossierId ?? null}
        />
      )}
      {step === 6 && (
        // Fix 2026-06-10 — handleCapital(step3) cote back wrap les donnees dans
        // {capital: out}. stepData.step3 == {capital: {nombreParts, capitalSocialMad, ...}}.
        // On lit step3.capital.{cle}, avec fallback step3.{cle} pour compat ascendante.
        <Step6Associes
          existing={stepData.step6}
          formeJuridique={formeJuridique}
          totalPartsExpected={(() => {
            const s3 = (stepData.step3 as Record<string, unknown> | undefined) ?? {};
            const cap = (s3.capital as Record<string, unknown> | undefined) ?? {};
            const v = Number((cap.nombreParts ?? s3.nombreParts) ?? 0);
            return v > 0 ? v : undefined;
          })()}
          capitalSocialExpected={(() => {
            const s3 = (stepData.step3 as Record<string, unknown> | undefined) ?? {};
            const cap = (s3.capital as Record<string, unknown> | undefined) ?? {};
            const v = Number((cap.capitalSocialMad ?? s3.capitalSocialMad) ?? 0);
            return v > 0 ? v : undefined;
          })()}
          valeurNominaleExpected={(() => {
            const s3 = (stepData.step3 as Record<string, unknown> | undefined) ?? {};
            const cap = (s3.capital as Record<string, unknown> | undefined) ?? {};
            const v = Number((cap.valeurNominaleMad ?? cap.valeurNominale ?? s3.valeurNominale) ?? 0);
            return v > 0 ? v : undefined;
          })()}
          apportNumeraireExpected={(() => {
            const cap = ((stepData.step3 as Record<string, unknown> | undefined)?.capital as Record<string, unknown> | undefined) ?? {};
            return Number(cap.apportNumeraire ?? 0);
          })()}
          apportNatureExpected={(() => {
            const cap = ((stepData.step3 as Record<string, unknown> | undefined)?.capital as Record<string, unknown> | undefined) ?? {};
            return Number(cap.apportNature ?? 0);
          })()}
          apportIndustrieExpected={(() => {
            const cap = ((stepData.step3 as Record<string, unknown> | undefined)?.capital as Record<string, unknown> | undefined) ?? {};
            return Number(cap.apportIndustrie ?? 0);
          })()}
          propagatedFromDirigeants={propagatedAssociates}
          saving={saving}
          onSubmit={(p) => submit(6, p)}
          onSave={(p) => draft(6, { step6: p })}
          registerDirty={effectiveRegisterDirty}
          onPieceUploaded={onPieceUploaded}
          dossierId={ticket?.dossierId ?? null}
        />
      )}
      {step === 8 && (
        <Step8PiecesJointes
          existing={stepData.step8}
          data={stepData}
          registeredPieces={registeredPieces}
          saving={saving}
          onSubmit={(p) => submit(8, p)}
          onPieceUploaded={onPieceUploaded}
        />
      )}
      {step === 9 && (
        <Step9Synthese
          data={stepData}
          saving={saving}
          onSubmit={finalizeCreation}
          onSave={(p) => draft(9, { step9: p })}
          registerDirty={effectiveRegisterDirty}
        />
      )}
    </>
  );

  return (
    <div className="space-y-5">
      <header className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <div>
          <p className="font-mono text-xs text-fg-subtle">{ticket.reference}</p>
          <h1 className="text-2xl font-bold text-fg">{ticket.titre}</h1>
          <div className="mt-1">
            <Badge variant={formeJuridique === 'SARL_AU' ? 'info' : 'default'}>
              {formeLabel}
            </Badge>
          </div>
        </div>
        <div className="flex gap-2">
          {readOnly ? (
            // Consultation lecture seule : pas d'action mutante dans l'en-tête.
            <Button variant="secondary" onClick={() => navigate('/tickets')}>
              <ChevronLeft className="mr-1 h-4 w-4" /> Retour aux tickets
            </Button>
          ) : (
            <>
              <Button variant="secondary" onClick={() => navigate(-1)}>
                <Save className="mr-1 h-4 w-4" /> Sauvegarder & Quitter
              </Button>
              <Button variant="danger" onClick={() => setShowCancel(true)}>
                <X className="mr-1 h-4 w-4" /> Annuler le ticket
              </Button>
            </>
          )}
        </div>
      </header>

      <WorkflowRoadmap
        currentStep={maxStep}
        viewStep={step}
        totalSteps={progress.totalSteps}
        onNavigate={goToStep}
      />

      {step < maxStep && (
        <div className="rounded-lg border border-amber-300 bg-amber-50 px-3 py-2 text-xs text-amber-800">
          Vous consultez l'etape {step}/{progress.totalSteps} (etape la plus avancee atteinte : {maxStep}).
          {!readOnly &&
            ' Vous pouvez modifier les valeurs puis revalider — la progression ne sera pas perdue.'}
        </div>
      )}

      {error && (
        <div className="rounded-lg border border-danger/40 bg-danger/10 p-3 text-sm text-danger">{error}</div>
      )}

      {datasetWarnings.length > 0 && (
        <div className="rounded-lg border border-warning/40 bg-warning/10 p-3 text-xs text-warning">
          <p className="font-semibold">Avertissements Data Room :</p>
          <ul className="ml-4 list-disc">
            {datasetWarnings.slice(-3).map((w, i) => (
              <li key={i}>{w}</li>
            ))}
          </ul>
          <button
            type="button"
            onClick={() => setDatasetWarnings([])}
            className="mt-1 text-[11px] underline"
          >
            Effacer
          </button>
        </div>
      )}

      <div className="rounded-2xl border border-border bg-bg-raised p-6">
        {/* 2026-08-12 (consultation lecture seule) — bandeau unique, fusionne
            l'ancien bloc « Workflow terminé ». */}
        {readOnly && (
          <div className="mb-5 rounded-lg border border-amber-300 bg-amber-50 px-4 py-3">
            <p className="flex items-start gap-2 text-sm text-amber-900">
              <Eye className="mt-0.5 h-4 w-4 flex-shrink-0" />
              <span>
                <span className="font-semibold">Consultation (lecture seule)</span>
                {' — '}
                {ticket.statut === 'ANNULE'
                  ? 'ticket annulé'
                  : ticket.statut === 'CLOTURE_DOSSIER'
                    ? 'ticket clôturé'
                    : 'workflow terminé'}
                . Les valeurs saisies, les étapes et les documents générés sont
                consultables ; aucune modification n'est possible.
              </span>
            </p>
          </div>
        )}

        {/*
          2026-06-05 — Bloc "Documents disponibles" SUPPRIME du parent : la
          generation est entierement prise en charge par l'etape 7.
          2026-08-12 — L'étape 7 gère son propre `readOnly` (Aperçu + Télécharger
          seulement) et reste HORS du <fieldset disabled> pour que ces deux
          boutons restent actifs. Les autres étapes sont enveloppées dans un
          <fieldset disabled> en lecture seule → inputs + actions neutralisés.
        */}
        {step === 7 ? (
          <Step7Generation
            existing={stepData.step7}
            data={stepData}
            saving={saving}
            onSubmit={(p) => submit(7, p)}
            onNavigate={goToStep}
            onDocumentGenerated={onDocumentGenerated}
            dossierId={ticket?.dossierId ?? null}
            ticketId={ticket?.id ?? null}
            readOnly={readOnly}
          />
        ) : readOnly ? (
          <fieldset disabled className="m-0 min-w-0 border-0 p-0">
            {stepBodyExcept7}
          </fieldset>
        ) : (
          stepBodyExcept7
        )}
      </div>

      <footer className="flex flex-wrap items-center justify-between gap-2 pt-1">
        <Button variant="secondary" onClick={goPrev} disabled={step <= 1 || saving}>
          <ChevronLeft className="mr-1 h-4 w-4" /> Precedent
        </Button>
        {step < maxStep && (
          <Button variant="secondary" onClick={goNext} disabled={saving}>
            Suivant (sans modifier) <ChevronRight className="ml-1 h-4 w-4" />
          </Button>
        )}
      </footer>

      {showCancel && (
        <CancelTicketDialog
          ticket={ticket}
          onClose={() => setShowCancel(false)}
          onConfirm={async (comment) => {
            await ticketService.transition(ticket.id, { target: 'ANNULE', comment });
            navigate('/tickets');
          }}
        />
      )}
    </div>
  );
}

// Exporte le builder pour les consumers externes (tests, autres pages).
export { buildPayloadCreationSarl };
