/**
 * Sous-formulaire de séance + opération Succursale (Phase D, 2026-08-09).
 *
 * Réutilise le noyau séance partagé ({@link SeanceForm}) — comme
 * {@link SeanceOperationForm} pour la dissolution/liquidation — et n'ajoute que
 * les blocs propres à l'opération succursale. Trois modes couvrent les modèles
 * directeur unifiés (SARL / SARL AU choisi par la forme juridique) :
 *
 *  - `creation-ma`  → `PV_CREATION_SUCCURSALE_MAROC_SARL(/_AU)` (AG des associés
 *    d'une société marocaine ; identité société pré-remplie depuis la BD via
 *    `dossierId`).
 *  - `creation-etr` → `PV_CREATION_SUCCURSALE_ETRANGERE_SARL(/_AU)` (organe
 *    compétent d'une société ÉTRANGÈRE, saisie libre — PAS d'identité BD ;
 *    ajoute société mère + organe + représentant résident).
 *  - `fermeture`    → `PV_FERMETURE_SUCCURSALE_SARL(/_AU)` (AG des associés ;
 *    identité société pré-remplie via `dossierId`).
 *
 * Le payload étend le contrat de séance ({@code form.buildPayload()}) avec les
 * clés attendues par le noyau back {@code SuccursaleVarsBuilder} :
 * `succursale`, (ETR) `societeMere` / `organe` / `representant`, et
 * `formalitesMandataireNom`. Les résolutions sont construites côté back ; on ne
 * fournit qu'un ordre du jour lisible.
 */
import { useEffect, useMemo, useState } from 'react';
import { Sparkles } from 'lucide-react';
import { TexteAide } from '../ui/Aide';
import { TextField } from '../ui/TextField';
import { Select } from '../ui/Select';
import { TypePieceProposee, ValeurProposee } from './ValeurProposee';
import { WorkflowDocumentBlock } from './WorkflowDocumentBlock';
import { freshDocState, useDocumentBlocks, type DocState, documentTravaille } from './documentBlocks';
import {
  listTemplatesForWorkflow,
  type TemplateInfo,
} from '../../services/workflowDocumentService';
import { SeanceFormFields } from './SeanceForm';
import {
  sanitizeFilename,
  useSeanceForm,
  type AssocieInput,
  type GerantInput,
  type SeanceSections,
  type SeanceSocieteInput,
} from './seanceFormEtat';
import type { DocumentType } from '../../types/dataroom';
import { USAGES_CABINET } from '../../lib/usagesCabinet';

export type SuccursaleMode = 'creation-ma' | 'creation-etr' | 'fermeture';

/** Valeurs pré-collectées par les étapes hôtes (pré-remplissage des blocs). */
export interface SuccursaleSeed {
  enseigne?: string;
  adresse?: string;
  ville?: string;
  villeGreffe?: string;
  activite?: string;
  /** création */
  dateOuverture?: string;
  /** fermeture */
  rcNumero?: string;
  dateFermeture?: string;
  motif?: string;
  /**
   * Lot DIVERS (2026-08-13) — dotation saisie à l'étape dédiée. Avec `locked`, ces
   * valeurs pilotent directement le PV : plus aucun champ de dotation ici.
   */
  dotationPresente?: boolean;
  dotationMontant?: string;
  /** responsable / représentant (identité pré-remplie du directeur saisi) */
  responsablePresent?: boolean;
  responsableCivilite?: string;
  responsablePrenom?: string;
  responsableNom?: string;
  responsableNationalite?: string;
  responsableAdresse?: string;
  responsablePieceType?: string;
  responsablePieceNumero?: string;
  responsablePouvoirs?: string;
}

/** Société mère étrangère (mode `creation-etr`, saisie libre). */
export interface SocieteMereSeed {
  denomination?: string;
  forme?: string;
  pays?: string;
  capital?: string;
  siege?: string;
  registre?: string;
  registreNumero?: string;
  loiApplicable?: string;
}

export interface SuccursaleOperationFormProps {
  mode: SuccursaleMode;
  dossierId?: string | null;
  ticketId?: string | null;
  formeJuridique: 'SARL' | 'SARL_AU';
  /** Identité société marocaine (AG) — pré-remplie BD via dossierId (MA / fermeture). */
  societe: SeanceSocieteInput;
  defaultDate?: string;
  /**
   * Lot L6b (E15) : date et heure de convocation saisies a l'etape 1 de la page hote.
   * Fournies, le proces-verbal les reprend et ne les redemande pas ; absentes, il les demande.
   */
  defaultConvocation?: { date: string; heure?: string };
  /** Pré-remplissage des blocs succursale depuis les étapes hôtes. */
  succursaleSeed?: SuccursaleSeed;
  /** Pré-remplissage société mère (ETR). */
  mereSeed?: SocieteMereSeed;
  /** Dénomination utilisée pour le nom de fichier (mère). */
  denominationForFilename?: string;
  /** Notifié quand le PV est validé (et déposé si un dossier est rattaché). */
  onReady?: (ready: boolean) => void;
  /**
   * Lot DIVERS (2026-08-13) — <b>anti-duplication</b>. Quand `true`, les blocs
   * « succursale », « dotation » et « responsable » ne sont plus SAISIS ici : ils
   * sont repris tels quels de `succursaleSeed` (l'étape de saisie dédiée du
   * workflow) et affichés en RÉCAPITULATIF non éditable. Sans ce verrou, la même
   * donnée avait deux champs concurrents — l'étape de saisie et ce formulaire —
   * et c'est la dernière valeur tapée qui gagnait dans le PV.
   */
  locked?: boolean;
  /**
   * Fix D1 (2026-08-16) — associés / gérants de la société MÈRE, lus en base.
   *
   * Sans eux, `useSeanceForm` démarrait sur un associé vierge et les PV de
   * création / fermeture de succursale sortaient avec une section de présence
   * vide. En succursale ÉTRANGÈRE il n'y a pas d'AG d'associés marocains : la
   * séance est la décision de l'organe compétent de la mère (sections `presence`
   * désactivées, cf. ETR_SECTIONS) — on n'alimente alors que le bureau.
   */
  initialAssocies?: AssocieInput[];
  initialGerants?: GerantInput[];
  /** Instantané persisté du formulaire de séance (survit au changement d'étape). */
  initialSnapshot?: Record<string, unknown> | null;
  onSnapshot?: (snapshot: Record<string, unknown>) => void;
  /** Nommage transverse `type - dénomination - forme (- v<n>)` (remplace le nommage local). */
  filenameFor?: (tpl: TemplateInfo, opts?: { version?: number }) => string | undefined;
  /** Dépose la génération en version Data Room (au lieu d'un nouveau document). */
  versioned?: boolean;
  /** Motif tracé sur le dépôt Data Room versionné. */
  depositMotif?: string;
}

const AG_SECTIONS: SeanceSections = {
  seance: true,
  bureau: true,
  convocation: true,
  convocationRang: false,
  irregularite: false,
  secondeAssemblee: false,
  ordreDuJour: false,
  documentsJoints: false,
  resolutions: false,
  presence: true,
  voix: false,
  vote: true, // L3 (D6) : quorum et vote saisis
};

const ETR_SECTIONS: SeanceSections = {
  seance: true,
  bureau: true,
  convocation: false,
  presence: false,
  vote: true, // L3 (D6) : resultat du vote saisi (les voix pour viennent de l'organe)
};

const PV_CODE: Record<SuccursaleMode, { sarl: string; au: string }> = {
  'creation-ma': {
    sarl: 'PV_CREATION_SUCCURSALE_MAROC_SARL',
    au: 'PV_CREATION_SUCCURSALE_MAROC_SARL_AU',
  },
  'creation-etr': {
    sarl: 'PV_CREATION_SUCCURSALE_ETRANGERE_SARL',
    au: 'PV_CREATION_SUCCURSALE_ETRANGERE_SARL_AU',
  },
  fermeture: {
    sarl: 'PV_FERMETURE_SUCCURSALE_SARL',
    au: 'PV_FERMETURE_SUCCURSALE_SARL_AU',
  },
};

const WORKFLOW_CODE: Record<SuccursaleMode, string> = {
  'creation-ma': 'SUCCURSALE_MA',
  'creation-etr': 'SUCCURSALE_ETR',
  fermeture: 'FERMETURE_SUCCURSALE',
};

function makeTemplateInfo(code: string, kind: string): TemplateInfo {
  return {
    code,
    documentKind: kind,
    file: `${code}.docx`,
    origin: 'directeur-2026-08',
    deprecated: false,
    placeholderStyle: 'dollar_nu_directeur',
  };
}

export function SuccursaleOperationForm({
  mode,
  dossierId,
  ticketId,
  formeJuridique,
  societe,
  defaultDate,
  defaultConvocation,
  succursaleSeed,
  mereSeed,
  denominationForFilename,
  onReady,
  locked = false,
  initialAssocies,
  initialGerants,
  initialSnapshot,
  onSnapshot,
  filenameFor,
  versioned = false,
  depositMotif,
}: SuccursaleOperationFormProps) {
  const isAU = formeJuridique === 'SARL_AU';
  const isEtr = mode === 'creation-etr';
  const isFermeture = mode === 'fermeture';
  const workflowCode = WORKFLOW_CODE[mode];
  const pvCode = isAU ? PV_CODE[mode].au : PV_CODE[mode].sarl;
  const denomination =
    (denominationForFilename ?? societe.denomination ?? '').toString().trim() || 'Société';

  const form = useSeanceForm({
    formeJuridique,
    societe,
    defaults: {
      type: 'extraordinaire',
      date: defaultDate,
      ...(defaultConvocation?.date
        ? { convDate: defaultConvocation.date, convHeure: defaultConvocation.heure ?? '' }
        : {}),
      // Fix D1/ETR — la séance est la décision de l'organe compétent de la mère
      // étrangère, pas une AG d'associés marocains : la qualité par défaut
      // « Gérant » y était juridiquement fausse.
      ...(isEtr ? { presidentQualite: 'Président' } : {}),
    },
    initialAssocies,
    initialGerants,
    initialSnapshot,
  });

  /**
   * Fix D1/ETR — le bureau ne peut PAS être pré-rempli depuis la base : la société
   * mère étrangère n'y est pas décrite. Le PV sortait donc « présidée par ␣ ».
   * Plutôt qu'un silence, on bloque la génération avec un message explicite
   * (principe transverse : jamais d'action désactivée sans motif affiché).
   */
  const bureauManquant = isEtr && !form.presidentNom.trim();

  useEffect(() => {
    onSnapshot?.(form.snapshot);
  }, [form.snapshot, onSnapshot]);

  // ---- Blocs succursale (owned by the form, pré-remplis via seed) ----
  const [enseigne, setEnseigne] = useState(succursaleSeed?.enseigne ?? '');
  const [adresse, setAdresse] = useState(succursaleSeed?.adresse ?? '');
  const [ville, setVille] = useState(succursaleSeed?.ville ?? '');
  const [villeGreffe, setVilleGreffe] = useState(
    succursaleSeed?.villeGreffe ?? succursaleSeed?.ville ?? '',
  );
  const [activite, setActivite] = useState(succursaleSeed?.activite ?? '');
  const [dateOuverture, setDateOuverture] = useState(succursaleSeed?.dateOuverture ?? '');
  // fermeture
  const [rcNumero, setRcNumero] = useState(succursaleSeed?.rcNumero ?? '');
  const [dateFermeture, setDateFermeture] = useState(succursaleSeed?.dateFermeture ?? '');
  const [motif, setMotif] = useState(succursaleSeed?.motif ?? '');

  // Dotation (création uniquement)
  const [dotationPresente, setDotationPresente] = useState<'oui' | 'non'>(
    succursaleSeed?.dotationPresente ? 'oui' : 'non',
  );
  const [dotationMontant, setDotationMontant] = useState(succursaleSeed?.dotationMontant ?? '');

  // Responsable / représentant (identité pré-remplie du directeur saisi)
  const [respPresent, setRespPresent] = useState<'oui' | 'non'>(
    succursaleSeed?.responsablePresent ?? succursaleSeed?.responsableNom ? 'oui' : 'non',
  );
  const [respCivilite, setRespCivilite] = useState(succursaleSeed?.responsableCivilite ?? 'M.');
  const [respPrenom, setRespPrenom] = useState(succursaleSeed?.responsablePrenom ?? '');
  const [respNom, setRespNom] = useState(succursaleSeed?.responsableNom ?? '');
  // L4a (E2) : nationalite, pouvoirs et motif proposes a l'ecran, jamais supposes.
  const [respNationalite, setRespNationalite] = useState(succursaleSeed?.responsableNationalite ?? '');
  const [respAdresse, setRespAdresse] = useState(succursaleSeed?.responsableAdresse ?? '');
  const [respPieceType, setRespPieceType] = useState(succursaleSeed?.responsablePieceType ?? ''); // L3 (D6)
  const [respPieceNumero, setRespPieceNumero] = useState(
    succursaleSeed?.responsablePieceNumero ?? '',
  );
  const [respPouvoirs, setRespPouvoirs] = useState(succursaleSeed?.responsablePouvoirs ?? '');

  const [mandataire, setMandataire] = useState('');
  // L4a (E1) : nombre d'originaux du PV, valeur proposee puis confirmee (modele du corpus).
  const [nombreOriginaux, setNombreOriginaux] = useState<string | undefined>(undefined);

  // Société mère étrangère (ETR)
  const [mereDenomination, setMereDenomination] = useState(mereSeed?.denomination ?? '');
  const [mereForme, setMereForme] = useState(mereSeed?.forme ?? '');
  const [merePays, setMerePays] = useState(mereSeed?.pays ?? '');
  const [mereCapital, setMereCapital] = useState(mereSeed?.capital ?? '');
  const [mereSiege, setMereSiege] = useState(mereSeed?.siege ?? '');
  const [mereRegistre, setMereRegistre] = useState(mereSeed?.registre ?? '');
  const [mereRegistreNumero, setMereRegistreNumero] = useState(mereSeed?.registreNumero ?? '');
  const [mereLoi, setMereLoi] = useState(mereSeed?.loiApplicable ?? '');

  // Organe compétent (ETR)
  const [organeCompetent, setOrganeCompetent] = useState('');
  const [organeDate, setOrganeDate] = useState(defaultDate ?? '');
  const [organeHeure, setOrganeHeure] = useState('');
  const [organeLieu, setOrganeLieu] = useState('');
  const [organeVoixPour, setOrganeVoixPour] = useState('');

  const [docs, setDocs] = useState<Record<string, DocState>>({});
  const [templates, setTemplates] = useState<TemplateInfo[]>([]);
  // L6 : erreur de chargement rattachee a la demande ; « en cours » se deduit (plus de
  // mise a jour synchrone d'etat au debut de l'effet). La liste chargee n'est jamais vide.
  const [erreurModeles, setErreurModeles] = useState<{ cle: string; message: string } | null>(null);
  const loadTemplatesError = erreurModeles?.cle === pvCode ? erreurModeles.message : null;
  const loadingTemplates = templates.length === 0 && !loadTemplatesError;

  /**
   * Valeurs EFFECTIVES du bloc succursale. Avec `locked`, la source de vérité est
   * l'étape de saisie du workflow (`succursaleSeed`) — jamais l'état local, qui ne
   * serait qu'une copie divergente. Sans `locked`, comportement historique : les
   * champs de ce formulaire font foi.
   */
  const eff = useMemo(() => {
    const s = succursaleSeed ?? {};
    if (!locked) {
      return {
        enseigne, adresse, ville, villeGreffe: villeGreffe || ville, activite,
        dateOuverture, rcNumero, dateFermeture, motif,
        dotationPresente, dotationMontant,
        respPresent, respCivilite, respPrenom, respNom, respNationalite,
        respAdresse, respPieceType, respPieceNumero, respPouvoirs,
      };
    }
    return {
      enseigne: s.enseigne ?? '',
      adresse: s.adresse ?? '',
      ville: s.ville ?? '',
      villeGreffe: s.villeGreffe || s.ville || '',
      activite: s.activite ?? '',
      dateOuverture: s.dateOuverture ?? '',
      rcNumero: s.rcNumero ?? '',
      dateFermeture: s.dateFermeture ?? '',
      motif: s.motif ?? '',
      dotationPresente: (s.dotationPresente ? 'oui' : 'non') as 'oui' | 'non',
      dotationMontant: s.dotationMontant ?? '',
      respPresent: (s.responsablePresent ? 'oui' : 'non') as 'oui' | 'non',
      respCivilite: s.responsableCivilite ?? '',
      respPrenom: s.responsablePrenom ?? '',
      respNom: s.responsableNom ?? '',
      respNationalite: s.responsableNationalite ?? '',
      respAdresse: s.responsableAdresse ?? '',
      respPieceType: s.responsablePieceType ?? '',
      respPieceNumero: s.responsablePieceNumero ?? '',
      respPouvoirs: s.responsablePouvoirs ?? '',
    };
  }, [
    locked, succursaleSeed,
    enseigne, adresse, ville, villeGreffe, activite, dateOuverture, rcNumero,
    dateFermeture, motif, dotationPresente, dotationMontant, respPresent,
    respCivilite, respPrenom, respNom, respNationalite, respAdresse,
    respPieceType, respPieceNumero, respPouvoirs,
  ]);

  const ordreDuJour = useMemo(() => {
    const respPresentEff = eff.respPresent;
    if (isFermeture) {
      const pts = ['Fermeture de la succursale'];
      if (respPresentEff === 'oui') pts.push('Cessation des fonctions du responsable');
      pts.push("Pouvoirs à l'effet des formalités");
      return pts;
    }
    if (isEtr) {
      return [
        'Ouverture d’une succursale au Maroc',
        'Désignation du représentant résident au Maroc',
        "Pouvoirs à l'effet des formalités",
      ];
    }
    const pts = ['Ouverture de la succursale'];
    if (respPresentEff === 'oui') pts.push('Désignation du responsable de la succursale');
    pts.push("Pouvoirs à l'effet des formalités");
    return pts;
  }, [isFermeture, isEtr, eff.respPresent]);

  const buildPayload = (): Record<string, unknown> => {
    const base = form.buildPayload();

    const responsable = isFermeture
      ? { civilite: eff.respCivilite, prenom: eff.respPrenom, nom: eff.respNom }
      : {
          civilite: eff.respCivilite,
          prenom: eff.respPrenom,
          nom: eff.respNom,
          nationalite: eff.respNationalite,
          adresse: eff.respAdresse,
          pieceType: eff.respPieceType,
          pieceNumero: eff.respPieceNumero,
          pouvoirs: eff.respPouvoirs,
        };

    const succursale: Record<string, unknown> = {
      enseigne: eff.enseigne,
      adresse: eff.adresse,
      ville: eff.ville,
      villeGreffe: eff.villeGreffe || eff.ville,
      activite: eff.activite,
      responsablePresent: eff.respPresent,
      responsable,
    };
    if (isFermeture) {
      succursale.rcNumero = eff.rcNumero;
      succursale.dateFermeture = eff.dateFermeture;
      succursale.motif = eff.motif;
    } else {
      succursale.dateOuverture = eff.dateOuverture;
      succursale.dotationPresente = eff.dotationPresente;
      succursale.dotationMontant = eff.dotationPresente === 'oui' ? eff.dotationMontant : '';
    }

    const payload: Record<string, unknown> = {
      ...base,
      succursale,
      ordreDuJour,
      formalitesMandataireNom: mandataire,
      nombreOriginaux: nombreOriginaux ?? '',
    };

    if (isEtr) {
      payload.societeMere = {
        denomination: mereDenomination,
        forme: mereForme,
        pays: merePays,
        capital: mereCapital,
        siege: mereSiege,
        registre: mereRegistre,
        registreNumero: mereRegistreNumero,
        loiApplicable: mereLoi,
      };
      payload.organe = {
        competent: organeCompetent,
        date: organeDate,
        heure: organeHeure,
        lieu: organeLieu,
        voixPour: organeVoixPour,
      };
      payload.representant = responsable;
    }

    return payload;
  };

  const { updateDoc, generateOne, downloadOne, validateOne } = useDocumentBlocks({
    workflowCode,
    dossierId,
    ticketId,
    mapType: (): DocumentType => 'PV_AGE',
    docs,
    setDocs,
    buildPayload,
    versioned,
    motif: depositMotif,
    // Nommage transverse fourni par la page hôte ; repli sur le nommage local.
    filenameFor:
      filenameFor ??
      (() => {
        const type = isFermeture ? 'PV — Fermeture de succursale' : 'PV — Création de succursale';
        return sanitizeFilename(`${type} - ${denomination}`) + '.docx';
      }),
  });

  useEffect(() => {
    if (templates.length > 0) return;
    let cancelled = false;
    listTemplatesForWorkflow(workflowCode)
      .then((items) => {
        if (cancelled) return;
        const found = items.find((t) => t.code === pvCode);
        setTemplates([found ?? makeTemplateInfo(pvCode, 'PV de succursale')]);
      })
      .catch((err) => {
        if (cancelled) return;
        setErreurModeles({ cle: pvCode, message: err instanceof Error ? err.message : 'Impossible de charger le modèle.', });
      });
    return () => {
      cancelled = true;
    };
  }, [workflowCode, pvCode, templates.length]);

  useEffect(() => {
    if (documentTravaille(docs[pvCode])) onReady?.(!!docs[pvCode]?.validated);
  }, [docs, pvCode, onReady]);

  return (
    <div className="space-y-5">
      <TexteAide cle="succursale-operation" titre="Ce qu’il faut faire">
        Complétez la séance et, s’il y a lieu, l’identité de la société mère, puis générez le
        procès-verbal, relisez son aperçu et validez-le : il est déposé dans la Data Room du
        dossier. Une donnée manquante vous est nommée à la génération.
      </TexteAide>
      {isEtr && (
        <Section title="Société mère étrangère">
          <div className="grid gap-3 md:grid-cols-2">
            <TextField label="Dénomination" value={mereDenomination} onChange={(e) => setMereDenomination(e.target.value)} />
            <TextField label="Forme juridique (pays d’origine)" value={mereForme} onChange={(e) => setMereForme(e.target.value)} placeholder="Ltd, GmbH, SAS…" />
            <TextField label="Pays d’origine" value={merePays} onChange={(e) => setMerePays(e.target.value)} />
            <TextField label="Capital" value={mereCapital} onChange={(e) => setMereCapital(e.target.value)} placeholder="Ex : 500 000 EUR" />
            <div className="md:col-span-2">
              <TextField label="Siège social" value={mereSiege} onChange={(e) => setMereSiege(e.target.value)} />
            </div>
            <TextField label="Registre (type)" value={mereRegistre} onChange={(e) => setMereRegistre(e.target.value)} placeholder="Ex : Companies House" />
            <TextField label="N° de registre" value={mereRegistreNumero} onChange={(e) => setMereRegistreNumero(e.target.value)} />
            <div className="md:col-span-2">
              <TextField label="Loi applicable" value={mereLoi} onChange={(e) => setMereLoi(e.target.value)} placeholder="Ex : loi anglaise" />
            </div>
          </div>
        </Section>
      )}

      {isEtr && (
        <Section title="Organe compétent (décision d’ouverture)">
          <div className="grid gap-3 md:grid-cols-2">
            <TextField label="Organe compétent" value={organeCompetent} onChange={(e) => setOrganeCompetent(e.target.value)} placeholder="Ex : le conseil d’administration" />
            <TextField label="Date de la décision" type="date" value={organeDate} onChange={(e) => setOrganeDate(e.target.value)} />
            <TextField label="Heure" type="time" value={organeHeure} onChange={(e) => setOrganeHeure(e.target.value)} />
            <TextField label="Lieu" value={organeLieu} onChange={(e) => setOrganeLieu(e.target.value)} />
            <TextField label="Voix pour" value={organeVoixPour} onChange={(e) => setOrganeVoixPour(e.target.value)} placeholder="Ex : 5 / l’unanimité" />
          </div>
        </Section>
      )}

      {/* Noyau séance (AG des associés pour MA/fermeture ; bureau seul pour ETR). */}
      <SeanceFormFields
        form={form}
        sections={isEtr ? ETR_SECTIONS : AG_SECTIONS}
        hideConvDate={!!defaultConvocation?.date}
      />

      {/* Bloc succursale — RÉCAPITULATIF non éditable quand la saisie a déjà eu
          lieu à l'étape dédiée (anti-duplication : une donnée, un seul champ). */}
      {locked ? (
        <Section title={isFermeture ? 'Succursale à fermer (saisie à l’étape précédente)' : 'Succursale à ouvrir (saisie à l’étape précédente)'}>
          <dl className="grid gap-3 text-sm md:grid-cols-2" data-testid="succursale-recap">
            <Recap label="Enseigne" value={eff.enseigne} />
            <Recap label="Activité" value={eff.activite} />
            <Recap label="Adresse" value={eff.adresse} className="md:col-span-2" />
            <Recap label="Ville" value={eff.ville} />
            <Recap label="Greffe de la succursale" value={eff.villeGreffe} />
            {isFermeture ? (
              <>
                <Recap label="N° RC secondaire" value={eff.rcNumero} />
                <Recap label="Date d’effet de la fermeture" value={eff.dateFermeture} />
                <Recap label="Motif" value={eff.motif} className="md:col-span-2" />
              </>
            ) : (
              <>
                <Recap label="Date d’ouverture" value={eff.dateOuverture} />
                <Recap
                  label="Dotation"
                  value={eff.dotationPresente === 'oui' ? `${eff.dotationMontant} MAD` : 'Aucune'}
                />
              </>
            )}
            <Recap
              label="Responsable"
              className="md:col-span-2"
              value={
                eff.respPresent === 'oui'
                  ? [eff.respCivilite, eff.respPrenom, eff.respNom].filter(Boolean).join(' ')
                  : 'Aucun'
              }
            />
          </dl>
          <p className="text-[11px] text-fg-subtle">
            Ces informations proviennent de l’étape de saisie : elles ne sont pas re-saisies ici.
            Revenez à l’étape précédente pour les corriger.
          </p>
        </Section>
      ) : (
      <Section title={isFermeture ? 'Succursale à fermer' : 'Succursale à ouvrir'}>
        <div className="grid gap-3 md:grid-cols-2">
          <TextField label="Enseigne / dénomination" value={enseigne} onChange={(e) => setEnseigne(e.target.value)} />
          <TextField label="Activité" value={activite} onChange={(e) => setActivite(e.target.value)} />
          <div className="md:col-span-2">
            <TextField label="Adresse" value={adresse} onChange={(e) => setAdresse(e.target.value)} />
          </div>
          <TextField label="Ville" value={ville} onChange={(e) => setVille(e.target.value)} />
          <TextField label="Ville du greffe" value={villeGreffe} onChange={(e) => setVilleGreffe(e.target.value)} placeholder="= ville si vide" />
          {isFermeture ? (
            <>
              <TextField label="N° RC secondaire" value={rcNumero} onChange={(e) => setRcNumero(e.target.value)} />
              <TextField label="Date d’effet de la fermeture" type="date" value={dateFermeture} onChange={(e) => setDateFermeture(e.target.value)} />
              <div className="md:col-span-2">
                <ValeurProposee id="succ-motif-fermeture" label="Motif de la fermeture"
                  proposition="cessation de l'activité exercée dans la succursale"
                  valeur={motif || undefined} onChange={setMotif} />
              </div>
            </>
          ) : (
            <>
              <TextField label="Date d’ouverture" type="date" value={dateOuverture} onChange={(e) => setDateOuverture(e.target.value)} />
              <Select
                label="Dotation présente ?"
                value={dotationPresente}
                onChange={(e) => setDotationPresente(e.target.value as 'oui' | 'non')}
                options={[{ value: 'non', label: 'Non' }, { value: 'oui', label: 'Oui' }]}
              />
              {dotationPresente === 'oui' && (
                <TextField label="Montant de la dotation (MAD)" type="number" value={dotationMontant} onChange={(e) => setDotationMontant(e.target.value)} />
              )}
            </>
          )}
        </div>
      </Section>
      )}

      {/* Responsable / représentant — masqué quand la saisie est verrouillée
          (le récapitulatif ci-dessus le porte déjà). */}
      {!locked && (
      <Section title={isEtr ? 'Représentant résident au Maroc' : 'Responsable de la succursale'}>
        <div className="grid gap-3 md:grid-cols-2">
          {!isEtr && (
            <Select
              label={isFermeture ? 'Responsable dont les fonctions cessent ?' : 'Responsable désigné ?'}
              value={respPresent}
              onChange={(e) => setRespPresent(e.target.value as 'oui' | 'non')}
              options={[{ value: 'non', label: 'Non' }, { value: 'oui', label: 'Oui' }]}
            />
          )}
          {(isEtr || respPresent === 'oui') && (
            <>
              <Select
                label="Civilité"
                value={respCivilite}
                onChange={(e) => setRespCivilite(e.target.value)}
                options={[
                  { value: 'M.', label: 'M.' },
                  { value: 'Mme', label: 'Mme' },
                  { value: 'Mlle', label: 'Mlle' },
                ]}
              />
              <TextField label="Prénom" value={respPrenom} onChange={(e) => setRespPrenom(e.target.value)} />
              <TextField label="Nom" value={respNom} onChange={(e) => setRespNom(e.target.value)} />
              {!isFermeture && (
                <>
                  <ValeurProposee id="succ-op-responsable-nationalite" label="Nationalité" proposition="marocaine"
                    valeur={respNationalite || undefined} onChange={setRespNationalite} />
                  <div className="md:col-span-2">
                    <TextField label="Adresse" value={respAdresse} onChange={(e) => setRespAdresse(e.target.value)} />
                  </div>
                  <TypePieceProposee id="succ-op-responsable-piece-type" valeur={respPieceType} onChange={setRespPieceType} />
                  <TextField label="N° de pièce" value={respPieceNumero} onChange={(e) => setRespPieceNumero(e.target.value)} />
                  <div className="md:col-span-2">
                    <ValeurProposee id="succ-op-responsable-pouvoirs" label="Pouvoirs"
                      proposition={isEtr
                        ? 'diriger la succursale et représenter la société auprès des tiers et des administrations marocaines'
                        : 'la gestion courante de la succursale et la représentation de la société auprès des tiers et des administrations'}
                      valeur={respPouvoirs || undefined} onChange={setRespPouvoirs} />
                  </div>
                </>
              )}
            </>
          )}
        </div>
      </Section>
      )}

      <Section title="Formalités">
        <div className="grid gap-3 md:grid-cols-2">
          <TextField
            label="Mandataire chargé des formalités *"
            value={mandataire}
            onChange={(e) => setMandataire(e.target.value)}
            hint="La résolution des pouvoirs le nomme : sans lui, le procès-verbal n’est pas généré."
          />
          <ValeurProposee
            id="succursale-nombre-originaux"
            label="Nombre d’exemplaires originaux"
            proposition={USAGES_CABINET.nombreOriginauxEnLettres}
            valeur={nombreOriginaux}
            onChange={setNombreOriginaux}
          />
        </div>
      </Section>

      {/* Génération du PV */}
      <div className="space-y-3">
        <div className="flex items-center gap-2">
          <Sparkles className="h-4 w-4 text-info" />
          <h4 className="text-sm font-semibold text-fg">
            {isFermeture ? 'Générer le PV de fermeture' : 'Générer le PV de création de succursale'}
          </h4>
        </div>
        {loadingTemplates && <p className="text-sm text-fg-subtle">Chargement du modèle…</p>}
        {loadTemplatesError && (
          <div className="rounded-lg border border-danger/30 bg-danger-soft p-3 text-sm text-danger" role="alert">
            {loadTemplatesError}
          </div>
        )}
        {bureauManquant && (
          <div
            role="alert"
            data-testid="succursale-etr-bureau-requis"
            className="rounded-lg border border-danger/40 bg-danger-soft p-3 text-sm text-danger"
          >
            Renseignez le <strong>président de séance</strong> (bloc « Séance ») avant de
            générer le PV : la société mère étrangère n'est pas décrite en base, ce nom ne
            peut donc pas être pré-rempli — et sans lui le PV sortirait « présidée par ».
          </div>
        )}
        <div className="grid gap-4 md:grid-cols-1">
          {templates.map((tpl) => (
            <WorkflowDocumentBlock
              key={tpl.code}
              tpl={tpl}
              state={docs[tpl.code] ?? freshDocState()}
              ticketId={ticketId}
              workflowCode={workflowCode}
              onGenerate={() => { if (!bureauManquant) void generateOne(tpl); }}
              onDownload={() => downloadOne(tpl)}
              onRegenerate={() => { if (!bureauManquant) void generateOne(tpl); }}
              onValidate={() => validateOne(tpl)}
              onTogglePreview={() => updateDoc(tpl.code, { previewOpen: !docs[tpl.code]?.previewOpen })}
            />
          ))}
        </div>
      </div>
    </div>
  );
}

/** Ligne de récapitulatif non éditable (mode `locked`). */
function Recap({
  label,
  value,
  className = '',
}: {
  label: string;
  value?: string | null;
  className?: string;
}) {
  return (
    <div className={className}>
      <dt className="text-xs text-fg-subtle">{label}</dt>
      <dd className="text-sm font-medium text-fg">{value?.trim() ? value : '—'}</dd>
    </div>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="space-y-4 rounded-xl border border-border bg-bg-raised p-4">
      <div className="flex items-center gap-2">
        <Sparkles className="h-4 w-4 text-info" />
        <h4 className="text-sm font-semibold text-fg">{title}</h4>
      </div>
      {children}
    </section>
  );
}
