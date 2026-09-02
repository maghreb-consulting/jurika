/**
 * Documents de séance partagés & OPTIONNELS (Phase A, 2026-08-08).
 *
 *  - ConvocationPanel     : proposé en DÉBUT de workflow (avant l'AG).
 *  - FeuillePresencePanel : proposé APRÈS l'AG.
 *
 * Les deux réutilisent le sous-formulaire commun {@link SeanceForm} et le
 * pipeline documentaire ({@link useDocumentBlocks}) ; ils appellent le workflow
 * back transverse `SEANCE_AG` (modèles unifiés SARL / SARL AU via $ASSOCIE_UNIQUE).
 * Repliables et fermés par défaut : optionnels, ils ne se mêlent pas aux actes de
 * la page hôte. Dépôt auto dataroom (best-effort) quand un dossier est rattaché.
 */
import { useCallback, useEffect, useMemo, useState } from 'react';
import { CalendarClock, ChevronDown, ChevronRight, ClipboardList, Sparkles } from 'lucide-react';
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
  type SeanceDefaults,
  type SeanceSections,
  type SeanceSocieteInput,
} from './SeanceForm';
import type { DocumentType } from '../../types/dataroom';

const WORKFLOW_CODE = 'SEANCE_AG';

export interface SeanceDocPanelBaseProps {
  dossierId?: string | null;
  ticketId?: string | null;
  formeJuridique: FormeJuridique;
  societe: SeanceSocieteInput;
  defaultSeance?: SeanceDefaults;
  /** Associés pré-remplis depuis le dossier (optionnel). */
  initialAssocies?: AssocieInput[];
  /** Gérants pré-remplis depuis le dossier (optionnel). */
  initialGerants?: GerantInput[];
  /** Ordre du jour pré-rempli (libellés des modifications). */
  initialOrdreDuJour?: string[];
  /** Ordre du jour dérivé : lecture seule, piloté par la sélection des modifications. */
  lockOrdreDuJour?: boolean;
  /** Masque le sélecteur « Type d'assemblée » (déjà saisi en amont). */
  lockType?: boolean;
  /** Masque la date de séance / l'heure de séance / la convocation (déjà saisies en amont). */
  hideSeanceDate?: boolean;
  hideSeanceHeure?: boolean;
  hideConvDate?: boolean;
  /**
   * Mode INTÉGRÉ : le panneau est déjà à l'intérieur d'une section dédiée de la page
   * hôte (ex. « Convocation (optionnelle) » de l'étape 1). Il se rend alors à plat,
   * sans son propre bandeau repliable.
   *
   * Sans ça on empilait trois niveaux de titres quasi identiques — la section de la
   * page, le repli du panneau, puis la carte « Convocation » du formulaire — et il
   * fallait deux clics pour atteindre les champs.
   */
  embedded?: boolean;
  /** Persistance inter-étapes : instantané restauré au remontage. */
  initialSnapshot?: Record<string, unknown> | null;
  /** Persistance inter-étapes : notifie l'état courant (à sauvegarder dans le brouillon). */
  onSnapshot?: (snapshot: Record<string, unknown>) => void;
  /** Phase 7 — dépôt Data Room versionné (opt-in). */
  versioned?: boolean;
  /** Motif tracé sur le dépôt versionné. */
  motif?: string;
  /** Override du nommage (convention `type - dénom - forme (- v<n>)`). */
  filenameFor?: (tpl: TemplateInfo, opts?: { version?: number }) => string | undefined;
  /**
   * Fix L3 (2026-08-16) — DÉDUPLICATION INDUE entre deux séances d'un même dossier.
   *
   * Le dépôt identifie un document logique par (type + titre). La convocation et la
   * feuille de présence étaient déposées en type `AUTRE` sous un titre constant :
   * la feuille d'une liquidation retombait donc sur le MÊME couple que celle d'une
   * dissolution antérieure du même dossier, et le second dépôt était traité comme
   * un doublon — aucune trace du nouveau contexte.
   *
   * Le libellé du contexte (workflow + date de séance) rend le titre distinctif.
   * Les pages hôtes le fournissent ; sans lui le comportement historique demeure.
   */
  contexteSeance?: string;
}

interface GenericProps extends SeanceDocPanelBaseProps {
  templateCode: 'CONVOCATION_AG' | 'FEUILLE_PRESENCE_AG';
  title: string;
  hint: string;
  filenamePrefix: string;
  icon: React.ReactNode;
  sections: SeanceSections;
  /** Fix M3 — type Data Room dédié (V23) au lieu du fourre-tout `AUTRE`. */
  documentType: DocumentType;
}

function SeanceDocPanel({
  dossierId,
  ticketId,
  formeJuridique,
  societe,
  defaultSeance,
  initialAssocies,
  initialGerants,
  initialOrdreDuJour,
  lockOrdreDuJour,
  initialSnapshot,
  onSnapshot,
  lockType,
  hideSeanceDate,
  hideSeanceHeure,
  hideConvDate,
  embedded,
  versioned,
  motif,
  filenameFor,
  templateCode,
  title,
  hint,
  filenamePrefix,
  icon,
  sections,
  documentType,
  contexteSeance,
}: GenericProps) {
  // En mode intégré, le contenu est toujours déployé : la page hôte porte déjà la
  // section et son titre, un second repli n'ajoute qu'un clic.
  const [open, setOpen] = useState(false);
  const isOpen = embedded || open;
  const form = useSeanceForm({
    formeJuridique,
    societe,
    defaults: defaultSeance,
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

  const [docs, setDocs] = useState<Record<string, DocState>>({});
  const [template, setTemplate] = useState<TemplateInfo | null>(null);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);

  const buildPayload = form.buildPayload;

  const defaultFilenameFor = useCallback((): string => {
    const denom = (societe.denomination ?? '').toString().trim() || 'Société';
    return sanitizeFilename(`${filenamePrefix} - ${denom}`) + '.docx';
  }, [societe.denomination, filenamePrefix]);

  const { updateDoc, generateOne, downloadOne, validateOne } = useDocumentBlocks({
    workflowCode: WORKFLOW_CODE,
    dossierId,
    ticketId,
    // Fix M3 (2026-08-16) — type Data Room DÉDIÉ (migration V23). Convocation et
    // feuille de présence étaient déposées en `AUTRE` : invisibles dans les filtres
    // par type, et confondues entre elles.
    mapType: (): DocumentType => documentType,
    // Fix L3 — titre distinctif : sans le contexte de séance, la feuille d'une
    // liquidation retombait sur le même (type + titre) que celle d'une dissolution
    // antérieure du même dossier et le dépôt était dédupliqué.
    titleFor: () => (contexteSeance ? `${title} — ${contexteSeance}` : title),
    docs,
    setDocs,
    buildPayload,
    filenameFor: filenameFor ?? defaultFilenameFor,
    versioned,
    motif,
  });

  useEffect(() => {
    if (!isOpen || template) return;
    let cancelled = false;
    setLoading(true);
    setLoadError(null);
    listTemplatesForWorkflow(WORKFLOW_CODE)
      .then((items) => {
        if (cancelled) return;
        const found = items.find((t) => t.code === templateCode) ?? null;
        if (!found) {
          setLoadError('Modèle indisponible : ' + templateCode);
          return;
        }
        setTemplate(found);
      })
      .catch((err) => {
        if (cancelled) return;
        setLoadError(err instanceof Error ? err.message : 'Impossible de charger le modèle.');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [isOpen, template, templateCode]);

  return (
    <section className={embedded ? '' : 'rounded-xl border border-accent/30 bg-accent/5'}>
      {!embedded && (
        <button
          type="button"
          onClick={() => setOpen((v) => !v)}
          className="flex w-full items-center justify-between gap-2 px-4 py-3 text-left"
          aria-expanded={open}
          data-testid={`seance-doc-toggle-${templateCode}`}
        >
          <span className="flex items-center gap-2">
            <span className="flex-shrink-0 text-accent">{icon}</span>
            <span className="text-sm font-semibold text-fg">{title} (optionnel)</span>
          </span>
          {open ? (
            <ChevronDown className="h-4 w-4 flex-shrink-0 text-fg-subtle" />
          ) : (
            <ChevronRight className="h-4 w-4 flex-shrink-0 text-fg-subtle" />
          )}
        </button>
      )}

      {isOpen && (
        <div
          className={
            embedded ? 'space-y-5' : 'space-y-5 border-t border-accent/20 p-4'
          }
          data-testid={`seance-doc-body-${templateCode}`}
        >
          <p className="text-xs text-fg-subtle">{hint}</p>

          <SeanceFormFields
            form={form}
            sections={sections}
            lockType={lockType}
            hideSeanceDate={hideSeanceDate}
            hideSeanceHeure={hideSeanceHeure}
            hideConvDate={hideConvDate}
            lockOrdreDuJour={lockOrdreDuJour}
          />

          <div className="space-y-3">
            <div className="flex items-center gap-2">
              <Sparkles className="h-4 w-4 text-violet-600" />
              <h4 className="text-sm font-semibold text-fg">Générer le document</h4>
            </div>

            {loading && <p className="text-sm text-fg-subtle">Chargement du modèle…</p>}
            {loadError && (
              <div className="rounded-lg border border-danger/30 bg-danger/10 p-3 text-sm text-danger" role="alert">
                {loadError}
              </div>
            )}

            {template && (
              <div className="grid gap-4 md:grid-cols-2">
                <WorkflowDocumentBlock
                  tpl={template}
                  state={docs[template.code] ?? freshDocState()}
                  onGenerate={() => generateOne(template)}
                  onDownload={() => downloadOne(template)}
                  onRegenerate={() => generateOne(template)}
                  onValidate={() => validateOne(template)}
                  onTogglePreview={() =>
                    updateDoc(template.code, { previewOpen: !docs[template.code]?.previewOpen })
                  }
                  onEdited={(blob, filename) =>
                    updateDoc(template.code, {
                      blob,
                      filename,
                      validated: false,
                      depositedToDataroom: false,
                    })
                  }
                />
              </div>
            )}
          </div>
        </div>
      )}
    </section>
  );
}

const CONVOCATION_SECTIONS: SeanceSections = {
  seance: true,
  bureau: false,
  convocation: true,
  convocationRang: true,
  ordreDuJour: true,
  documentsJoints: true,
  presence: true,
  voix: false,
};

const FEUILLE_SECTIONS: SeanceSections = {
  seance: true,
  bureau: true,
  convocation: true,
  convocationRang: true,
  presence: true,
  voix: true,
};

export function ConvocationPanel(props: SeanceDocPanelBaseProps) {
  const sections = useMemo(() => CONVOCATION_SECTIONS, []);
  return (
    <SeanceDocPanel
      {...props}
      templateCode="CONVOCATION_AG"
      documentType="CONVOCATION"
      title="Convocation à l'assemblée"
      hint="À proposer AVANT l'assemblée. Renseignez la séance, la convocation, l'ordre du jour et les associés destinataires, puis générez la lettre de convocation (une lettre par associé)."
      filenamePrefix="Convocation"
      icon={<CalendarClock className="h-4 w-4" />}
      sections={sections}
    />
  );
}

export function FeuillePresencePanel(props: SeanceDocPanelBaseProps) {
  const sections = useMemo(() => FEUILLE_SECTIONS, []);
  return (
    <SeanceDocPanel
      {...props}
      templateCode="FEUILLE_PRESENCE_AG"
      documentType="FEUILLE_PRESENCE"
      title="Feuille de présence"
      hint="À proposer APRÈS l'assemblée. Renseignez les présences (parts, voix, mandataire) par associé, puis générez la feuille de présence certifiée par le gérant."
      filenamePrefix="Feuille de présence"
      icon={<ClipboardList className="h-4 w-4" />}
      sections={sections}
    />
  );
}
