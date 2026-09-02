/**
 * Sous-formulaire de séance + opération Dissolution / Liquidation (Phase C, 2026-08-08).
 *
 * Réutilise le noyau séance partagé ({@link SeanceForm}) et n'ajoute que les champs propres
 * à l'opération. Le PV directeur est unifié ({@code PV_DISSOLUTION_LIQUIDATION_SARL(/_AU)})
 * et couvre les trois étapes du cycle via {@code $PV_ETAPE} ; l'étape est fixée par le
 * workflow (jamais saisie). Les résolutions (avec leur type) sont dérivées de l'opération.
 *
 * - Mode « dissolution » : depuis le lot « Dissolution 4 étapes » (2026-08-12), le liquidateur
 *   et le siège de la liquidation sont saisis à l'ÉTAPE 1 et transmis via {@code operation} —
 *   le bloc de saisie ci-dessous n'apparaît donc plus (il subsiste comme repli si un appelant
 *   ne fournit pas {@code operation}).
 * - Mode « liquidation » : le liquidateur et les comptes de clôture sont fournis par la page
 *   hôte (prop {@code operation}) — elle les saisit déjà pour la validation workflow — et le
 *   rapport de liquidation est généré en plus du PV.
 */
import { useEffect, useMemo, useState } from 'react';
import { Sparkles } from 'lucide-react';
import { TextField } from '../ui/TextField';
import {
  WorkflowDocumentBlock,
  freshDocState,
  useDocumentBlocks,
  type DocState,
} from './WorkflowDocumentBlock';
import {
  listTemplatesForWorkflow,
  type TemplateInfo,
} from '../../services/workflowDocumentService';
import {
  SeanceFormFields,
  useSeanceForm,
  type AssocieInput,
  type GerantInput,
  type SeanceSections,
  type SeanceSocieteInput,
} from './SeanceForm';
import type { DocumentType } from '../../types/dataroom';

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
  };
  cloture?: {
    resultatSens: 'boni' | 'mali';
    resultatMontant?: string | number | null;
    boniParPart?: string | number | null;
    dateClotureLiquidation?: string | null;
    actifRealise?: string | number | null;
    passifRegle?: string | number | null;
  };
  dissolutionDate?: string;
}

export interface SeanceOperationFormProps {
  mode: Mode;
  dossierId?: string | null;
  ticketId?: string | null;
  formeJuridique: 'SARL' | 'SARL_AU';
  societe: SeanceSocieteInput;
  defaultDate?: string;
  /** Motif de dissolution saisi à l'étape 1 (mode dissolution). */
  motif?: string;
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
}

const SECTIONS: SeanceSections = {
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
  motif,
  operation,
  initialAssocies,
  initialGerants,
  initialSnapshot,
  onSnapshot,
  onReady,
  filenameFor,
  versioned,
  depositMotif,
}: SeanceOperationFormProps) {
  const isAU = formeJuridique === 'SARL_AU';
  const workflowCode = mode === 'dissolution' ? 'DISSOLUTION' : 'LIQUIDATION';
  const pvEtape = mode === 'dissolution' ? 'dissolution' : 'clôture';
  const denomination = societe.denomination ?? '';
  const pvCode = isAU ? PV_SARL_AU : PV_SARL;

  const form = useSeanceForm({
    formeJuridique,
    societe,
    defaults: { type: 'extraordinaire', date: defaultDate },
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
  const [liqPieceType, setLiqPieceType] = useState('CIN');
  const [liqPieceNumero, setLiqPieceNumero] = useState('');
  const [liqGenre, setLiqGenre] = useState<'masculin' | 'féminin'>('masculin');
  const [liqRemuneration, setLiqRemuneration] = useState('exercées à titre gratuit');
  const [liquidationSiege, setLiquidationSiege] = useState(societe.siegeSocial ?? '');

  const [docs, setDocs] = useState<Record<string, DocState>>({});
  const [templates, setTemplates] = useState<TemplateInfo[]>([]);
  const [loadingTemplates, setLoadingTemplates] = useState(false);
  const [loadTemplatesError, setLoadTemplatesError] = useState<string | null>(null);

  const requiredCodes = useMemo(
    () => (mode === 'liquidation' ? [pvCode, RAPPORT] : [pvCode]),
    [mode, pvCode],
  );

  // Résolutions dérivées de l'opération (types pilotant les branches du modèle).
  const derived = useMemo(() => {
    const R = (objet: string, type: string) => ({ objet, type, resultat: "à l'unanimité" });
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
          pieceType: liqPieceType,
          pieceNumero: liqPieceNumero,
          genre: liqGenre,
          remuneration: liqRemuneration,
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
    return {
      ...base,
      pvEtape,
      dissolution: { motif: motif ?? 'volontaire', dateEffet: defaultDate ?? '', date: dissoDate },
      liquidateur,
      liquidationSiege: operation ? operation.liquidateur.siege ?? '' : liquidationSiege,
      cloture: clotureBlock,
      resolutions: derived.resolutions,
      ordreDuJour: derived.ordreDuJour,
      signature: {
        lieu: form.lieuSignature,
        date: clotureBlock?.dateClotureLiquidation ?? defaultDate ?? '',
        nombreOriginaux: 'quatre',
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
    setLoadingTemplates(true);
    setLoadTemplatesError(null);
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
        setLoadTemplatesError(err instanceof Error ? err.message : 'Impossible de charger les modèles.');
      })
      .finally(() => {
        if (!cancelled) setLoadingTemplates(false);
      });
    return () => {
      cancelled = true;
    };
  }, [workflowCode, mode, pvCode, templates.length]);

  useEffect(() => {
    onReady?.(requiredCodes.every((c) => !!docs[c]?.validated));
  }, [docs, requiredCodes, onReady]);

  return (
    <div className="space-y-5">
      <SeanceFormFields form={form} sections={SECTIONS} />

      {mode === 'dissolution' && !operation && (
        <section className="space-y-4 rounded-xl border border-border bg-bg-raised p-4">
          <div className="flex items-center gap-2">
            <Sparkles className="h-4 w-4 text-violet-600" />
            <h4 className="text-sm font-semibold text-fg">Liquidateur et siège de la liquidation</h4>
          </div>
          <div className="grid gap-3 md:grid-cols-2">
            <div>
              <label className="mb-1 block text-xs font-medium text-fg-muted">Civilité</label>
              <select
                value={liqCivilite}
                onChange={(e) => {
                  setLiqCivilite(e.target.value);
                  setLiqGenre(e.target.value === 'Mme' || e.target.value === 'Mlle' ? 'féminin' : 'masculin');
                }}
                className="w-full rounded-lg border border-border-hi bg-bg-raised px-3 py-2 text-sm"
              >
                <option value="M.">M.</option>
                <option value="Mme">Mme</option>
                <option value="Mlle">Mlle</option>
              </select>
            </div>
            <TextField label="Prénom" value={liqPrenom} onChange={(e) => setLiqPrenom(e.target.value)} />
            <TextField label="Nom" value={liqNom} onChange={(e) => setLiqNom(e.target.value)} />
            <TextField label="Adresse" value={liqAdresse} onChange={(e) => setLiqAdresse(e.target.value)} />
            <TextField label="Type de pièce" value={liqPieceType} onChange={(e) => setLiqPieceType(e.target.value)} />
            <TextField label="N° de pièce" value={liqPieceNumero} onChange={(e) => setLiqPieceNumero(e.target.value)} />
            <TextField label="Rémunération du liquidateur" value={liqRemuneration} onChange={(e) => setLiqRemuneration(e.target.value)} />
            <TextField label="Siège de la liquidation" value={liquidationSiege} onChange={(e) => setLiquidationSiege(e.target.value)} />
          </div>
        </section>
      )}

      <div className="space-y-3">
        <div className="flex items-center gap-2">
          <Sparkles className="h-4 w-4 text-violet-600" />
          <h4 className="text-sm font-semibold text-fg">
            {mode === 'dissolution' ? 'Générer le PV de dissolution' : 'Générer le PV de clôture et le rapport'}
          </h4>
        </div>
        {loadingTemplates && <p className="text-sm text-fg-subtle">Chargement des modèles…</p>}
        {loadTemplatesError && (
          <div className="rounded-lg border border-danger/30 bg-danger/10 p-3 text-sm text-danger" role="alert">
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
