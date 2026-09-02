/**
 * Incident de séance — panneau transverse (2026-08-06 ; refonte Phase A 2026-08-08).
 *
 * Deux PV « incident » peuvent être générés dans TOUT workflow qui tient une
 * Assemblée Générale (Dissolution / Liquidation / PV AGO / Modification) :
 *   - Constat de défaut de quorum
 *   - Irrégularité de convocation
 * Chacun existe en variante SARL et SARL_AU (workflowCode back = INCIDENT_SEANCE).
 *
 * Depuis la Phase A, la saisie s'appuie sur le sous-formulaire de séance COMMUN
 * ({@link SeanceForm}) partagé avec ConvocationPanel / FeuillePresencePanel. Le
 * panneau reste repliable, fermé par défaut et optionnel. Réutilise le pipeline
 * documentaire commun (dépôt auto dataroom sur DocumentType PV_AGE).
 */
import { useCallback, useEffect, useState } from 'react';
import { AlertTriangle, ChevronDown, ChevronRight, Sparkles } from 'lucide-react';
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
  type SeanceSections,
  type SeanceSocieteInput,
  type SeanceDefaults,
} from './SeanceForm';
import type { DocumentType } from '../../types/dataroom';

const WORKFLOW_CODE = 'INCIDENT_SEANCE';

/**
 * Point 3 (audit directeur) — nom de fichier lisible « Type + Dénomination » pour les
 * PV d'incident (au lieu du code technique backend). Pure &amp; testable.
 */
export function incidentPvFilename(
  code: string,
  denomination?: string | null,
): string | undefined {
  const denom = (denomination ?? '').toString().trim() || 'Société';
  const libelle = code.includes('DEFAUT_QUORUM')
    ? 'Défaut de quorum'
    : code.includes('IRREGULARITE_CONVOCATION')
      ? 'Irrégularité de convocation'
      : null;
  if (!libelle) return undefined;
  return sanitizeFilename(`PV — ${libelle} - ${denom}`) + '.docx';
}

// Rétro-compatibilité des props (4 pages hôtes) : alias des types communs.
export type IncidentSocieteInput = SeanceSocieteInput;
export type IncidentDefaultSeance = SeanceDefaults;

export interface IncidentSeancePanelProps {
  dossierId?: string | null;
  ticketId?: string | null;
  formeJuridique: 'SARL' | 'SARL_AU';
  societe: IncidentSocieteInput;
  defaultSeance?: IncidentDefaultSeance;
  /**
   * Masquage des données déjà saisies en amont (2026-08-14).
   *
   * L'incident porte sur L'ASSEMBLÉE DU WORKFLOW — son type, sa date et sa
   * convocation sont donc toujours connus de l'étape 1. Ce panneau les
   * réaffichait pourtant vides : l'employé devait re-saisir à l'identique ce
   * qu'il venait de renseigner, au risque d'une divergence entre le PV
   * d'incident et l'acte principal.
   */
  lockType?: boolean;
  hideSeanceDate?: boolean;
  hideConvDate?: boolean;
  /**
   * Fix D1 (2026-08-16) — associés / gérants lus en base. Un PV de défaut de
   * quorum sans la liste des associés et leurs parts ne prouve RIEN : c'est
   * précisément le décompte des parts présentes qui constate le défaut.
   */
  initialAssocies?: AssocieInput[];
  initialGerants?: GerantInput[];
  /** Phase 7 — dépôt Data Room versionné (opt-in). */
  versioned?: boolean;
  /** Motif tracé sur le dépôt versionné. */
  motif?: string;
  /** Override du nommage (convention `type - dénom - forme (- v<n>)`). */
  filenameFor?: (tpl: TemplateInfo, opts?: { version?: number }) => string | undefined;
}

const INCIDENT_SECTIONS: SeanceSections = {
  seance: true,
  bureau: true,
  convocation: true,
  convocationRang: false,
  irregularite: true,
  secondeAssemblee: true,
  ordreDuJour: true,
  resolutions: true,
  presence: true,
  voix: false,
};

// Les 2 PV « incident » se déposent dans le slot juridique PV_AGE.
function mapTemplateToDocumentType(): DocumentType {
  return 'PV_AGE';
}

export function IncidentSeancePanel({
  dossierId,
  ticketId,
  formeJuridique,
  societe,
  defaultSeance,
  lockType,
  hideSeanceDate,
  hideConvDate,
  initialAssocies,
  initialGerants,
  versioned,
  motif,
  filenameFor: filenameForProp,
}: IncidentSeancePanelProps) {
  const isAU = formeJuridique === 'SARL_AU';
  const [open, setOpen] = useState(false);

  const form = useSeanceForm({
    formeJuridique,
    societe,
    defaults: { type: 'extraordinaire', ...defaultSeance },
    initialAssocies,
    initialGerants,
  });

  const [docs, setDocs] = useState<Record<string, DocState>>({});
  const [templates, setTemplates] = useState<TemplateInfo[]>([]);
  const [loadingTemplates, setLoadingTemplates] = useState(false);
  const [loadTemplatesError, setLoadTemplatesError] = useState<string | null>(null);

  // Point 3 (audit directeur) — nommage conforme « Type + Dénomination » : chaque PV
  // d'incident sort avec un libellé lisible au lieu du code technique backend.
  const filenameFor = useCallback(
    (tpl: TemplateInfo, opts?: { version?: number }): string | undefined =>
      filenameForProp?.(tpl, opts) ?? incidentPvFilename(tpl.code, societe.denomination),
    [filenameForProp, societe.denomination],
  );

  const { updateDoc, generateOne, downloadOne, validateOne } = useDocumentBlocks({
    workflowCode: WORKFLOW_CODE,
    dossierId,
    ticketId,
    mapType: mapTemplateToDocumentType,
    docs,
    setDocs,
    buildPayload: form.buildPayload,
    filenameFor,
    versioned,
    motif,
  });

  // Chargement des modèles à l'ouverture, filtrés par forme (SARL vs SARL AU).
  useEffect(() => {
    if (!open || templates.length > 0) return;
    let cancelled = false;
    setLoadingTemplates(true);
    setLoadTemplatesError(null);
    listTemplatesForWorkflow(WORKFLOW_CODE)
      .then((items) => {
        if (cancelled) return;
        const filtered = items.filter((t) => {
          if (t.code.endsWith('_SARL_AU')) return isAU;
          if (t.code.endsWith('_SARL')) return !isAU;
          return true;
        });
        const sorted = [...filtered].sort((a, b) => {
          const wa = a.code.includes('DEFAUT_QUORUM') ? 0 : 1;
          const wb = b.code.includes('DEFAUT_QUORUM') ? 0 : 1;
          if (wa !== wb) return wa - wb;
          return a.code.localeCompare(b.code);
        });
        setTemplates(sorted);
      })
      .catch((err) => {
        if (cancelled) return;
        setLoadTemplatesError(
          err instanceof Error ? err.message : "Impossible de charger les modèles d'incident de séance.",
        );
      })
      .finally(() => {
        if (!cancelled) setLoadingTemplates(false);
      });
    return () => {
      cancelled = true;
    };
  }, [open, isAU, templates.length]);

  return (
    <section className="rounded-xl border border-amber-300/60 bg-warning/5">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="flex w-full items-center justify-between gap-2 px-4 py-3 text-left"
        aria-expanded={open}
        data-testid="incident-seance-toggle"
      >
        <span className="flex items-center gap-2">
          <AlertTriangle className="h-4 w-4 flex-shrink-0 text-warning" />
          <span className="text-sm font-semibold text-fg">Incident de séance (optionnel)</span>
          <span className="hidden text-xs text-fg-subtle sm:inline">
            — Constat de défaut de quorum / Irrégularité de convocation
          </span>
        </span>
        {open ? (
          <ChevronDown className="h-4 w-4 flex-shrink-0 text-fg-subtle" />
        ) : (
          <ChevronRight className="h-4 w-4 flex-shrink-0 text-fg-subtle" />
        )}
      </button>

      {open && (
        <div className="space-y-5 border-t border-amber-300/40 p-4">
          <p className="text-xs text-fg-subtle">
            À n'utiliser que si l'assemblée n'a pas pu se tenir normalement. Renseignez la séance,
            la convocation et (le cas échéant) la 2e assemblée, puis générez le PV correspondant.
            Les documents sont déposés en dataroom (PV AGE) quand un dossier est rattaché.
          </p>

          <SeanceFormFields
            form={form}
            sections={INCIDENT_SECTIONS}
            lockType={lockType}
            hideSeanceDate={hideSeanceDate}
            hideConvDate={hideConvDate}
          />

          <div className="space-y-3">
            <div className="flex items-center gap-2">
              <Sparkles className="h-4 w-4 text-violet-600" />
              <h4 className="text-sm font-semibold text-fg">Générer le PV d'incident</h4>
            </div>

            {loadingTemplates && <p className="text-sm text-fg-subtle">Chargement des modèles…</p>}
            {loadTemplatesError && (
              <div className="rounded-lg border border-danger/30 bg-danger/10 p-3 text-sm text-danger" role="alert">
                {loadTemplatesError}
              </div>
            )}
            {!loadingTemplates && templates.length === 0 && !loadTemplatesError && (
              <div className="rounded-lg border border-amber-200 bg-warning/10 p-3 text-sm text-amber-800">
                Aucun modèle d'incident de séance disponible.
              </div>
            )}

            <div className="grid gap-4 md:grid-cols-2">
              {templates.map((tpl) => (
                <WorkflowDocumentBlock
                  key={tpl.code}
                  tpl={tpl}
                  state={docs[tpl.code] ?? freshDocState()}
                  onGenerate={() => generateOne(tpl)}
                  onDownload={() => downloadOne(tpl)}
                  onRegenerate={() => generateOne(tpl)}
                  onValidate={() => validateOne(tpl)}
                  onTogglePreview={() =>
                    updateDoc(tpl.code, { previewOpen: !docs[tpl.code]?.previewOpen })
                  }
                  onEdited={(blob, filename) =>
                    updateDoc(tpl.code, {
                      blob,
                      filename,
                      validated: false,
                      depositedToDataroom: false,
                    })
                  }
                />
              ))}
            </div>
          </div>
        </div>
      )}
    </section>
  );
}
