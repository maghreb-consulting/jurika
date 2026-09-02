/**
 * Sous-formulaire de séance + génération du PV de modification (Phase E1, refondu
 * Phase 2 2026-08-10).
 *
 * Réutilise le noyau séance partagé ({@link SeanceForm}) pour la partie AG (SARL) /
 * décisions de l'associé unique (SARL AU). Les RÉSOLUTIONS sont désormais SAISIES à
 * l'étape 2 (page {@code ModificationWorkflowPage}, seedées depuis la sélection
 * officielle) et transmises ici en PROPS ({@code resolutions}). Ce composant ne fait
 * plus qu'afficher un récapitulatif des résolutions + générer le PV directeur :
 *  - SARL     → {@code PV_MODIFICATION_SARL}
 *  - SARL AU  → {@code PV_MODIFICATION_SARL_AU}
 *
 * Le payload étend le contrat de séance ({@code form.buildPayload()}) en REMPLAÇANT sa
 * clé {@code resolutions} par les résolutions typées (aplati via {@link flattenResolution}).
 */
import { useEffect, useMemo, useState } from 'react';
import { Sparkles } from 'lucide-react';
import { TextField } from '../ui/TextField';
import { Select } from '../ui/Select';
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
  sanitizeFilename,
  useSeanceForm,
  type AssocieInput,
  type GerantInput,
  type FormeJuridique,
  type SeanceSections,
  type SeanceSocieteInput,
} from './SeanceForm';
import {
  OUI_NON,
  RES_SPECS,
  flattenResolution,
  type ResolutionState,
} from './modificationResolutions';
import type { DocumentType } from '../../types/dataroom';

const AG_SECTIONS: SeanceSections = {
  seance: true,
  bureau: true,
  convocation: true,
  convocationRang: true,
  irregularite: false,
  secondeAssemblee: false,
  ordreDuJour: false,
  documentsJoints: false,
  resolutions: false,
  presence: true,
  voix: false,
};

const AU_SECTIONS: SeanceSections = {
  seance: true,
  bureau: false,
  convocation: false,
  presence: false,
};

const PV_CODE_SARL = 'PV_MODIFICATION_SARL';
const PV_CODE_AU = 'PV_MODIFICATION_SARL_AU';
const WORKFLOW_CODE = 'MODIFICATION';

export interface ModificationOperationFormProps {
  dossierId?: string | null;
  ticketId?: string | null;
  formeJuridique: FormeJuridique;
  /** Identité société — pré-remplie BD via dossierId. */
  societe: SeanceSocieteInput;
  /** Résolutions typées saisies à l'étape 2 (seedées depuis la sélection). */
  resolutions: ResolutionState[];
  defaultDate?: string;
  /** Nature d'assemblée choisie à l'étape 1 (ordinaire/extraordinaire) — évite le doublon. */
  defaultType?: 'ordinaire' | 'extraordinaire' | 'mixte';
  /** Associés / gérants pré-remplis depuis la BD (fiche société) — zéro re-saisie. */
  initialAssocies?: AssocieInput[];
  initialGerants?: GerantInput[];
  /** Ordre du jour pré-rempli (libellés des modifications). */
  initialOrdreDuJour?: string[];
  /** Ordre du jour dérivé : lecture seule, piloté par la sélection des modifications. */
  lockOrdreDuJour?: boolean;
  /** Masque le sélecteur « Type d'assemblée » (déjà saisi à l'étape 1). */
  lockType?: boolean;
  /** Masque la date de séance (= date du PV, déjà saisie à l'étape 1). */
  hideSeanceDate?: boolean;
  /** Persistance inter-étapes : instantané restauré au remontage. */
  initialSnapshot?: Record<string, unknown> | null;
  /** Persistance inter-étapes : notifie l'état courant (à sauvegarder dans le brouillon). */
  onSnapshot?: (snapshot: Record<string, unknown>) => void;
  /** Fabrique le nom de fichier (convention Phase 7 : type - dénom - forme (- v)). */
  filenameFor?: (tpl: TemplateInfo, opts?: { version?: number }) => string | undefined;
  /** Phase 7 — dépôt Data Room versionné (opt-in). */
  versioned?: boolean;
  /** Motif tracé sur le dépôt versionné. */
  motif?: string;
  onReady?: (ready: boolean) => void;
}

function makeTemplateInfo(code: string): TemplateInfo {
  return {
    code,
    documentKind: 'PV de modification',
    file: `${code}.docx`,
    origin: 'directeur-2026-08',
    deprecated: false,
    placeholderStyle: 'dollar_nu_directeur',
  };
}

export function ModificationOperationForm({
  dossierId,
  ticketId,
  formeJuridique,
  societe,
  resolutions,
  defaultDate,
  defaultType,
  initialAssocies,
  initialGerants,
  initialOrdreDuJour,
  lockOrdreDuJour,
  initialSnapshot,
  onSnapshot,
  lockType,
  hideSeanceDate,
  filenameFor,
  versioned,
  motif,
  onReady,
}: ModificationOperationFormProps) {
  const isAU = formeJuridique === 'SARL_AU';
  const pvCode = isAU ? PV_CODE_AU : PV_CODE_SARL;
  const denomination = (societe.denomination ?? '').toString().trim() || 'Société';

  const form = useSeanceForm({
    formeJuridique,
    societe,
    defaults: { type: defaultType ?? 'extraordinaire', date: defaultDate },
    initialAssocies,
    initialGerants,
    initialOrdreDuJour,
    lockOrdreDuJour,
    initialSnapshot,
  });
  // Persistance inter-étapes : remonte l'instantané dès qu'il change.
  useEffect(() => {
    onSnapshot?.(form.snapshot);
  }, [form.snapshot, onSnapshot]);

  // Options d'assemblée référencées par les modèles (hors noyau séance).
  const [cacExiste, setCacExiste] = useState<'oui' | 'non'>('non');
  const [cacPresent, setCacPresent] = useState<'oui' | 'non'>('non');
  const [commissaireComptesNom, setCommissaireComptesNom] = useState('');
  const [gerantEstAssocie, setGerantEstAssocie] = useState<'oui' | 'non'>(isAU ? 'oui' : 'non');

  const [docs, setDocs] = useState<Record<string, DocState>>({});
  const [templates, setTemplates] = useState<TemplateInfo[]>([]);
  const [loadingTemplates, setLoadingTemplates] = useState(false);
  const [loadTemplatesError, setLoadTemplatesError] = useState<string | null>(null);

  const ordreDuJour = useMemo(
    () => resolutions.map((r) => r.objet.trim() || RES_SPECS[r.type]?.label || 'Résolution'),
    [resolutions],
  );

  const buildPayload = (): Record<string, unknown> => {
    const base = form.buildPayload();
    return {
      ...base,
      resolutions: resolutions.map(flattenResolution),
      ordreDuJour,
      commissaireComptes: { existe: cacExiste, present: cacPresent, nom: commissaireComptesNom },
      quorumAtteint: 'oui',
      gerantEstAssocie,
    };
  };

  const { updateDoc, generateOne, downloadOne, validateOne } = useDocumentBlocks({
    workflowCode: WORKFLOW_CODE,
    dossierId,
    ticketId,
    mapType: (): DocumentType => 'PV_MODIFICATION',
    docs,
    setDocs,
    buildPayload,
    filenameFor:
      filenameFor ??
      (() => sanitizeFilename(`PV — Modification (AGE) - ${denomination}`) + '.docx'),
    versioned,
    motif,
  });

  useEffect(() => {
    if (templates.length > 0) return;
    let cancelled = false;
    setLoadingTemplates(true);
    setLoadTemplatesError(null);
    listTemplatesForWorkflow(WORKFLOW_CODE)
      .then((items) => {
        if (cancelled) return;
        const found = items.find((t) => t.code === pvCode);
        setTemplates([found ?? makeTemplateInfo(pvCode)]);
      })
      .catch((err) => {
        if (cancelled) return;
        setLoadTemplatesError(
          err instanceof Error ? err.message : 'Impossible de charger le modèle.',
        );
      })
      .finally(() => {
        if (!cancelled) setLoadingTemplates(false);
      });
    return () => {
      cancelled = true;
    };
  }, [pvCode, templates.length]);

  useEffect(() => {
    onReady?.(!!docs[pvCode]?.validated);
  }, [docs, pvCode, onReady]);

  return (
    <div className="space-y-5">
      {/* Noyau séance (AG des associés SARL / comparution associé unique SARL AU). */}
      <SeanceFormFields
        form={form}
        sections={isAU ? AU_SECTIONS : AG_SECTIONS}
        lockType={lockType}
        hideSeanceDate={hideSeanceDate}
        lockOrdreDuJour={lockOrdreDuJour}
      />

      {/* Options d'assemblée référencées par les résolutions. */}
      <Section title="Options de l'assemblée">
        <div className="grid gap-3 md:grid-cols-2">
          <Select
            label="Commissaire aux comptes ?"
            value={cacExiste}
            onChange={(e) => setCacExiste(e.target.value as 'oui' | 'non')}
            options={OUI_NON}
          />
          {cacExiste === 'oui' && (
            <>
              <Select
                label="Présent à la séance ?"
                value={cacPresent}
                onChange={(e) => setCacPresent(e.target.value as 'oui' | 'non')}
                options={OUI_NON}
              />
              <div className="md:col-span-2">
                <TextField
                  label="Nom du commissaire aux comptes"
                  value={commissaireComptesNom}
                  onChange={(e) => setCommissaireComptesNom(e.target.value)}
                />
              </div>
            </>
          )}
          {isAU && (
            <Select
              label="Le gérant est-il l'associé unique ?"
              value={gerantEstAssocie}
              onChange={(e) => setGerantEstAssocie(e.target.value as 'oui' | 'non')}
              options={OUI_NON}
            />
          )}
        </div>
      </Section>

      {/* Récapitulatif des résolutions (saisie à l'étape 2). */}
      <Section title="Résolutions à intégrer au PV">
        {resolutions.length === 0 ? (
          <p className="text-sm text-fg-subtle">
            Aucune résolution — revenez à l'étape 1 pour sélectionner des décisions, puis
            renseignez-les à l'étape 2.
          </p>
        ) : (
          <ol className="space-y-2">
            {resolutions.map((r, i) => (
              <li
                key={r.id}
                className="flex items-start gap-2 rounded-lg border border-border bg-bg p-2 text-sm"
              >
                <span className="flex h-5 w-5 flex-shrink-0 items-center justify-center rounded bg-violet-600 text-[10px] font-bold text-white">
                  {i + 1}
                </span>
                <span className="text-fg">{r.objet.trim() || RES_SPECS[r.type]?.label || r.type}</span>
              </li>
            ))}
          </ol>
        )}
      </Section>

      {/* Génération du PV. */}
      <div className="space-y-3">
        <div className="flex items-center gap-2">
          <Sparkles className="h-4 w-4 text-violet-600" />
          <h4 className="text-sm font-semibold text-fg">Générer le PV de modification</h4>
        </div>
        {loadingTemplates && <p className="text-sm text-fg-subtle">Chargement du modèle…</p>}
        {loadTemplatesError && (
          <div className="rounded-lg border border-danger/30 bg-danger/10 p-3 text-sm text-danger" role="alert">
            {loadTemplatesError}
          </div>
        )}
        {resolutions.length === 0 && (
          <p className="text-sm text-fg-subtle">Ajoutez au moins une résolution pour générer le PV.</p>
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

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="space-y-4 rounded-xl border border-border bg-bg-raised p-4">
      <div className="flex items-center gap-2">
        <Sparkles className="h-4 w-4 text-violet-600" />
        <h4 className="text-sm font-semibold text-fg">{title}</h4>
      </div>
      {children}
    </section>
  );
}
