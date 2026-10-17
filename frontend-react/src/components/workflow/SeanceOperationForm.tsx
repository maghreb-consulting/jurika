/**
 * Sous-formulaire de seance + operation Dissolution / Liquidation (Phase C, 2026-08-08).
 *
 * Reutilise le noyau seance partage ({@link SeanceForm}) et n'ajoute que les champs propres
 * a l'operation. Lot L4d (E11d) : l'ecran demande toujours PV_DISSOLUTION_LIQUIDATION_SARL(/_AU),
 * mais le serveur rend le modele du corpus selon le parcours : PV_DISSOLUTION_ANTICIPEE_* pour
 * la dissolution, PV_CLOTURE_LIQUIDATION_* pour la cloture (et RAPPORT_LIQUIDATEUR_* pour le
 * rapport). Les mentions et decisions de ces modeles se saisissent dans {@link MentionsPvFields}.
 *
 * - Mode "dissolution" : le liquidateur, son mandat et le siege de la liquidation viennent de
 *   l'etape 1 (prop {@code operation}) ; le bloc de saisie ci-dessous ne sert que de repli.
 * - Mode "liquidation" : le liquidateur (base) et les comptes de cloture (etape 1) sont
 *   fournis par la page hote ; le rapport du liquidateur est genere en plus du PV.
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
  useSeanceForm,
  type AssocieInput,
  type GerantInput,
  type SeanceSections,
  type SeanceSocieteInput,
} from './seanceFormEtat';
import type { DocumentType } from '../../types/dataroom';
import { USAGES_CABINET } from '../../lib/usagesCabinet';
import { MentionsPvFields } from './MentionsPvFields';
import { cloturePayload, mentionsPayload, type MentionsPv } from '../../lib/liquidationPv';

type Mode = 'dissolution' | 'liquidation';

/** Données d'opération fournies par la page hôte (mode liquidation). */
export interface OperationInput {
  liquidateur: {
    civilite?: string;
    prenom?: string;
    nom?: string;
    adresse?: string;
    pieceType?: string;
    pieceNumero?: string;
    genre?: 'masculin' | 'féminin';
    remuneration?: string;
    siege?: string;
    /** Lot L4d (E11d) : autres donnees du liquidateur lues par les PV du corpus. */
    [cle: string]: unknown;
  };
  cloture?: {
    /** L4d (E11d) : sens calcule des totaux ; absent, rien n'est suppose. */
    resultatSens?: 'boni' | 'mali' | null;
    resultatMontant?: string | number | null;
    boniParPart?: string | number | null;
    dateClotureLiquidation?: string | null;
    actifRealise?: string | number | null;
    passifRegle?: string | number | null;
    /** Lot L4d (E11d) : donnees de l'avis de cloture reprises par le PV (quitus, dates). */
    [cle: string]: unknown;
  };
  dissolutionDate?: string;
  /**
   * Lot L4d (E11d) : precisions de la dissolution saisies a l'etape 1 (date d'effet, terme,
   * exercice, article et circonstances, evenement de l'objet, article 86).
   */
  dissolution?: Record<string, unknown>;
  /**
   * Lot L5 (E4) : operations de liquidation saisies pour le rapport final du liquidateur
   * (RAPPORT_LIQUIDATEUR_SARL / _SARL_AU du corpus), bloc `rapportLiquidateur`.
   */
  rapportLiquidateur?: Record<string, unknown>;
}

export interface SeanceOperationFormProps {
  mode: Mode;
  dossierId?: string | null;
  ticketId?: string | null;
  formeJuridique: 'SARL' | 'SARL_AU';
  societe: SeanceSocieteInput;
  defaultDate?: string;
  /**
   * Lot L6b (E15) : date et heure de convocation saisies a l'etape 1 de la page hote.
   * Fournies, le proces-verbal les reprend et ne les redemande pas ; absentes, il les demande.
   */
  defaultConvocation?: { date: string; heure?: string };
  /** Motif de dissolution saisi à l'étape 1 (mode dissolution). */
  motif?: string;
  /** L4a (E2) : nature de la dissolution (anticipee, article 86, arrivee du terme), saisie. */
  nature?: string;
  /**
   * Données d'opération fournies par la page hôte ; sinon saisies ici. Depuis le lot
   * « Dissolution 4 étapes » (2026-08-12), la page Dissolution fournit aussi ce bloc
   * (liquidateur + siège de la liquidation saisis à l'étape 1) — plus AUCUNE re-saisie.
   */
  operation?: OperationInput;
  /**
   * Fix D1 (2026-08-16) — associés / gérants pré-remplis DEPUIS LA BASE.
   *
   * Ce formulaire appelait `useSeanceForm` sans eux : la liste d'associés
   * démarrait donc sur `[emptyAssocie()]` et le PV sortait avec une section de
   * présence VIDE (« M., propriétaire de ␣ parts sociales », « présidée par ␣ »)
   * — alors que la convocation et la feuille de présence du MÊME workflow, elles,
   * les recevaient et les listaient correctement. En SARL AU c'était la
   * comparution entière de l'associé unique qui manquait.
   *
   * Le président de séance en découle : `useSeanceForm` le déduit du 1er gérant.
   */
  initialAssocies?: AssocieInput[];
  initialGerants?: GerantInput[];
  /** Instantané persisté du formulaire de séance (survit au changement d'étape). */
  initialSnapshot?: Record<string, unknown> | null;
  onSnapshot?: (snapshot: Record<string, unknown>) => void;
  /** Notifié quand TOUS les documents requis (PV, + rapport en liquidation) sont validés. */
  onReady?: (ready: boolean) => void;
  /**
   * Override du nommage des documents (convention `type - dénom - forme (- v<n>)`).
   * Sans cette prop, le nommage historique `type - dénomination.docx` est conservé.
   */
  filenameFor?: (tpl: TemplateInfo, opts?: { version?: number }) => string | undefined;
  /** Dépôt Data Room versionné (opt-in) : un document de même type+titre part en v+1. */
  versioned?: boolean;
  /** Motif tracé sur le dépôt versionné (distinct du motif de dissolution). */
  depositMotif?: string;
  /** Lot L4d (E11d) : reference du ticket, proposee comme reference du dossier. */
  referenceDossier?: string;
  /** Lot L4d (E11d) : mentions du PV deja saisies (brouillon de l'etape), et leur suivi. */
  initialMentions?: MentionsPv | null;
  onMentions?: (mentions: MentionsPv) => void;
}

const SECTIONS: SeanceSections = {
  seance: true,
  bureau: true,
  convocation: true,
  // L4d (E11d) : les PV du corpus impriment le rang de la convocation (seconde assemblee).
  convocationRang: true,
  irregularite: false,
  secondeAssemblee: false,
  ordreDuJour: false,
  documentsJoints: false,
  resolutions: false,
  presence: true,
  voix: false,
  vote: true, // L3 (D6) : quorum et vote saisis
};

const PV_SARL = 'PV_DISSOLUTION_LIQUIDATION_SARL';
const PV_SARL_AU = 'PV_DISSOLUTION_LIQUIDATION_SARL_AU';
const RAPPORT = 'RAPPORT_LIQUIDATION_DIRECTEUR';

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

export function SeanceOperationForm({
  mode,
  dossierId,
  ticketId,
  formeJuridique,
  societe,
  defaultDate,
  defaultConvocation,
  motif,
  nature,
  operation,
  initialAssocies,
  initialGerants,
  initialSnapshot,
  onSnapshot,
  onReady,
  filenameFor,
  versioned,
  depositMotif,
  referenceDossier,
  initialMentions,
  onMentions,
}: SeanceOperationFormProps) {
  const isAU = formeJuridique === 'SARL_AU';
  const workflowCode = mode === 'dissolution' ? 'DISSOLUTION' : 'LIQUIDATION';
  const pvEtape = mode === 'dissolution' ? 'dissolution' : 'clôture';
  const denomination = societe.denomination ?? '';
  const pvCode = isAU ? PV_SARL_AU : PV_SARL;

  const form = useSeanceForm({
    formeJuridique,
    societe,
    defaults: {
      type: 'extraordinaire',
      date: defaultDate,
      ...(defaultConvocation?.date
        ? { convDate: defaultConvocation.date, convHeure: defaultConvocation.heure ?? '' }
        : {}),
    },
    initialAssocies,
    initialGerants,
    initialSnapshot,
  });

  useEffect(() => {
    onSnapshot?.(form.snapshot);
  }, [form.snapshot, onSnapshot]);

  // Liquidateur saisi ici uniquement en mode dissolution (sinon fourni par la page).
  const [liqCivilite, setLiqCivilite] = useState('M.');
  const [liqPrenom, setLiqPrenom] = useState('');
  const [liqNom, setLiqNom] = useState('');
  const [liqAdresse, setLiqAdresse] = useState('');
  const [liqPieceType, setLiqPieceType] = useState<string | undefined>(undefined); // L3 (D6)
  const [liqPieceNumero, setLiqPieceNumero] = useState('');
  const [liqGenre, setLiqGenre] = useState<'masculin' | 'féminin'>('masculin');
  const [liqRemuneration, setLiqRemuneration] = useState<string | undefined>(undefined); // L4a (E2)
  // L3 (D6) : "quatre" originaux est une valeur PROPOSEE, transmise une fois confirmee.
  const [nombreOriginaux, setNombreOriginaux] = useState<string | undefined>(undefined);
  const [liquidationSiege, setLiquidationSiege] = useState(societe.siegeSocial ?? '');
  // L4d (E11d) : mentions et decisions des PV du corpus (brouillon conserve par la page).
  const [mentions, setMentions] = useState<MentionsPv>(() => ({ ...(initialMentions ?? {}) }));
  const changerMentions = (patch: MentionsPv) =>
    setMentions((m) => {
      const next = { ...m, ...patch };
      onMentions?.(next);
      return next;
    });

  const [docs, setDocs] = useState<Record<string, DocState>>({});
  const [templates, setTemplates] = useState<TemplateInfo[]>([]);
  // L6 : erreur de chargement rattachee a la demande ; « en cours » se deduit (plus de
  // mise a jour synchrone d'etat au debut de l'effet). La liste chargee n'est jamais vide.
  const [erreurModeles, setErreurModeles] = useState<{ cle: string; message: string } | null>(null);
  const loadTemplatesError = erreurModeles?.cle === pvCode ? erreurModeles.message : null;
  const loadingTemplates = templates.length === 0 && !loadTemplatesError;

  const requiredCodes = useMemo(
    () => (mode === 'liquidation' ? [pvCode, RAPPORT] : [pvCode]),
    [mode, pvCode],
  );

  // Résolutions dérivées de l'opération (types pilotant les branches du modèle).
  const derived = useMemo(() => {
    // L3 (D6) : plus de "a l'unanimite" suppose ; la majorite vient du vote saisi.
    const R = (objet: string, type: string) => ({ objet, type });
    const resolutions =
      mode === 'dissolution'
        ? [
            R('Dissolution anticipée et mise en liquidation', 'dissolution_anticipee'),
            R('Nomination du liquidateur', 'nomination_liquidateur'),
            R('Pouvoirs pour les formalités', 'pouvoirs_formalites'),
          ]
        : [
            R('Approbation du rapport et des comptes définitifs de liquidation', 'approbation_comptes_cloture'),
            R('Quitus au liquidateur', 'quitus_liquidateur'),
            R('Répartition du résultat de liquidation', 'repartition_boni'),
            R('Clôture de la liquidation', 'cloture_liquidation'),
            R('Pouvoirs pour les formalités', 'pouvoirs_formalites'),
          ];
    return { resolutions, ordreDuJour: resolutions.map((r) => r.objet) };
  }, [mode]);

  const buildPayload = (): Record<string, unknown> => {
    const base = form.buildPayload();
    const liquidateur = operation
      ? operation.liquidateur
      : {
          civilite: liqCivilite,
          prenom: liqPrenom,
          nom: liqNom,
          adresse: liqAdresse,
          pieceType: liqPieceType ?? '',
          pieceNumero: liqPieceNumero,
          genre: liqGenre,
          remuneration: liqRemuneration ?? '',
          siege: liquidationSiege,
        };
    const cloture = operation?.cloture;
    const clotureBlock = cloture
      ? {
          resultatSens: cloture.resultatSens,
          resultatType: cloture.resultatSens,
          resultatMontant: cloture.resultatMontant ?? null,
          boniExiste: cloture.resultatSens === 'boni' ? 'oui' : 'non',
          boniMontant: cloture.resultatSens === 'boni' ? cloture.resultatMontant ?? null : null,
          maliMontant: cloture.resultatSens === 'mali' ? cloture.resultatMontant ?? null : null,
          boniParPart: cloture.boniParPart ?? null,
          dateClotureLiquidation: cloture.dateClotureLiquidation ?? null,
          actifRealise: cloture.actifRealise ?? null,
          passifRegle: cloture.passifRegle ?? null,
        }
      : undefined;
    const dissoDate = operation?.dissolutionDate ?? defaultDate ?? '';
    // L4d (E11d) : la date d'effet est celle confirmee a l'etape 1 (plus de reprise de la
    // date de l'assemblee), avec les precisions du motif.
    const dissolution = { motif: motif ?? '', nature: nature ?? '', ...(operation?.dissolution ?? {}), date: dissoDate };
    return {
      ...base,
      pvEtape,
      dissolution,
      liquidateur,
      liquidationSiege: operation ? operation.liquidateur.siege ?? '' : liquidationSiege,
      cloture: mode === 'liquidation' ? { ...(operation?.cloture ?? {}), ...(clotureBlock ?? {}), ...cloturePayload(mentions) } : clotureBlock,
      rapportLiquidateur: mode === 'liquidation' ? operation?.rapportLiquidateur : undefined,
      mentionsPv: { ...mentionsPayload(mentions), nombreOriginaux: nombreOriginaux ?? '' },
      resolutions: derived.resolutions,
      ordreDuJour: derived.ordreDuJour,
      signature: {
        lieu: form.lieuSignature,
        date: clotureBlock?.dateClotureLiquidation ?? defaultDate ?? '',
        nombreOriginaux: nombreOriginaux ?? '',
      },
    };
  };

  const { updateDoc, generateOne, downloadOne, validateOne } = useDocumentBlocks({
    workflowCode,
    dossierId,
    ticketId,
    mapType: (code: string): DocumentType => {
      // Fix L2 (2026-08-16) — le rapport de liquidation avait son propre acte mais
      // pas son propre type : il tombait dans « AUTRE », donc introuvable par
      // filtre. Type dédié depuis la migration V23.
      if (code.startsWith('RAPPORT_LIQUIDATION')) return 'RAPPORT_LIQUIDATION';
      return mode === 'dissolution' ? 'PV_DISSOLUTION' : 'PV_LIQUIDATION';
    },
    docs,
    setDocs,
    buildPayload,
    versioned,
    motif: depositMotif,
    // Nommage : l'hôte peut imposer la convention `type - dénom - forme (- v<n>)`
    // (Dissolution) ; sinon on garde le nommage historique.
    filenameFor: (tpl, opts) => {
      const override = filenameFor?.(tpl, opts);
      if (override) return override;
      if (tpl.code.startsWith('RAPPORT_LIQUIDATION')) return `Rapport de liquidation - ${denomination}.docx`;
      const type = mode === 'dissolution' ? 'PV — Dissolution' : 'PV — Clôture de liquidation';
      return `${type} - ${denomination}.docx`;
    },
  });

  useEffect(() => {
    if (templates.length > 0) return;
    let cancelled = false;
    listTemplatesForWorkflow(workflowCode)
      .then((items) => {
        if (cancelled) return;
        const filtered = items.filter((t) => {
          if (t.code.startsWith('RAPPORT_LIQUIDATION')) return mode === 'liquidation';
          return t.code === pvCode;
        });
        if (!filtered.some((t) => t.code === pvCode)) {
          filtered.unshift(makeTemplateInfo(pvCode, 'PV Dissolution / Liquidation'));
        }
        if (mode === 'liquidation' && !filtered.some((t) => t.code === RAPPORT)) {
          filtered.push(makeTemplateInfo(RAPPORT, 'Rapport de liquidation'));
        }
        setTemplates(filtered);
      })
      .catch((err) => {
        if (cancelled) return;
        setErreurModeles({ cle: pvCode, message: err instanceof Error ? err.message : 'Impossible de charger les modèles.' });
      });
    return () => {
      cancelled = true;
    };
  }, [workflowCode, mode, pvCode, templates.length]);

  useEffect(() => {
    if (requiredCodes.some((c) => documentTravaille(docs[c]))) {
      onReady?.(requiredCodes.every((c) => !!docs[c]?.validated));
    }
  }, [docs, requiredCodes, onReady]);

  return (
    <div className="space-y-5">
      <TexteAide cle={`seance-operation-${mode}`} titre="Ce qu’il faut faire">
        Renseignez la séance (date, lieu, bureau, présences et vote), confirmez les valeurs
        proposées, puis générez chaque document, relisez son aperçu et validez-le : il est
        déposé dans la Data Room du dossier. Une donnée manquante vous est nommée à la
        génération.
      </TexteAide>
      <SeanceFormFields form={form} sections={SECTIONS} hideConvDate={!!defaultConvocation?.date} />

      <section className="rounded-xl border border-border bg-bg-raised p-4">
        <div className="max-w-xs">
          <ValeurProposee
            id="seance-nombre-originaux"
            label="Nombre d’exemplaires originaux"
            proposition={USAGES_CABINET.nombreOriginauxEnLettres}
            valeur={nombreOriginaux}
            onChange={setNombreOriginaux}
          />
        </div>
      </section>

      <MentionsPvFields
        mode={mode}
        isAU={isAU}
        valeurs={mentions}
        onChange={changerMentions}
        referenceDossier={referenceDossier}
        villeEnregistrement={societe.villeGreffe ?? ''}
        quitusAvis={typeof operation?.cloture?.quitus === 'string' ? (operation.cloture.quitus as string) : undefined}
      />

      {mode === 'dissolution' && !operation && (
        <section className="space-y-4 rounded-xl border border-border bg-bg-raised p-4">
          <div className="flex items-center gap-2">
            <Sparkles className="h-4 w-4 text-info" />
            <h4 className="text-sm font-semibold text-fg">Liquidateur et siège de la liquidation</h4>
          </div>
          <div className="grid gap-3 md:grid-cols-2">
            <Select
              label="Civilité"
              value={liqCivilite}
              onChange={(e) => {
                setLiqCivilite(e.target.value);
                setLiqGenre(e.target.value === 'Mme' || e.target.value === 'Mlle' ? 'féminin' : 'masculin');
              }}
              options={[
                { value: 'M.', label: 'M.' },
                { value: 'Mme', label: 'Mme' },
                { value: 'Mlle', label: 'Mlle' },
              ]}
            />
            <TextField label="Prénom" value={liqPrenom} onChange={(e) => setLiqPrenom(e.target.value)} />
            <TextField label="Nom" value={liqNom} onChange={(e) => setLiqNom(e.target.value)} />
            <TextField label="Adresse" value={liqAdresse} onChange={(e) => setLiqAdresse(e.target.value)} />
            <TypePieceProposee id="seance-liquidateur-piece-type" valeur={liqPieceType} onChange={setLiqPieceType} />
            <TextField label="N° de pièce" value={liqPieceNumero} onChange={(e) => setLiqPieceNumero(e.target.value)} />
            <ValeurProposee id="seance-liquidateur-remuneration" label="Rémunération du liquidateur"
              proposition="exercées à titre gratuit" valeur={liqRemuneration} onChange={setLiqRemuneration} />
            <TextField label="Siège de la liquidation" value={liquidationSiege} onChange={(e) => setLiquidationSiege(e.target.value)} />
          </div>
        </section>
      )}

      <div className="space-y-3">
        <div className="flex items-center gap-2">
          <Sparkles className="h-4 w-4 text-info" />
          <h4 className="text-sm font-semibold text-fg">
            {mode === 'dissolution' ? 'Générer le PV de dissolution' : 'Générer le PV de clôture et le rapport'}
          </h4>
        </div>
        {loadingTemplates && <p className="text-sm text-fg-subtle">Chargement des modèles…</p>}
        {loadTemplatesError && (
          <div className="rounded-lg border border-danger/30 bg-danger-soft p-3 text-sm text-danger" role="alert">
            {loadTemplatesError}
          </div>
        )}
        <div className="grid gap-4 md:grid-cols-1">
          {templates.map((tpl) => (
            <WorkflowDocumentBlock
              key={tpl.code}
              tpl={tpl}
              state={docs[tpl.code] ?? freshDocState()}
              onGenerate={() => generateOne(tpl)}
              onDownload={() => downloadOne(tpl)}
              onRegenerate={() => generateOne(tpl)}
              onValidate={() => validateOne(tpl)}
              onTogglePreview={() => updateDoc(tpl.code, { previewOpen: !docs[tpl.code]?.previewOpen })}
            />
          ))}
        </div>
      </div>
    </div>
  );
}
