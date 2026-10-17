/**
 * Documents de séance partagés & OPTIONNELS (Phase A, 2026-08-08).
 *
 *  - ConvocationPanel     : proposé en DÉBUT de workflow (avant l'AG).
 *  - FeuillePresencePanel : proposé APRÈS l'AG.
 *
 * Les deux réutilisent le sous-formulaire commun {@link SeanceForm} et le
 * pipeline documentaire ({@link useDocumentBlocks}) ; ils appellent le workflow
 * back transverse `SEANCE_AG`. L4d (E11b) : le serveur rend les modeles du corpus
 * du cabinet (convocation AGO, AGE ou seconde ; feuille AGO ou AGE). En SARL AU, ni
 * convocation ni feuille de presence (article 76 de la loi 5-96) : les panneaux le disent.
 * Repliables et fermés par défaut : optionnels, ils ne se mêlent pas aux actes de
 * la page hôte. Dépôt auto dataroom (best-effort) quand un dossier est rattaché.
 */
import { useCallback, useEffect, useMemo, useState } from 'react';
import { CalendarClock, ClipboardList, Sparkles } from 'lucide-react';
import { WorkflowDocumentBlock } from './WorkflowDocumentBlock';
import { freshDocState, useDocumentBlocks, type DocState } from './documentBlocks';
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
  type FormeJuridique,
  type SeanceDefaults,
  type SeanceSections,
  type SeanceSocieteInput,
} from './seanceFormEtat';
import type { DocumentType } from '../../types/dataroom';
import { TexteAide } from '../ui/Aide';
import { EnTeteDepliant } from '../ui/Depliant';

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
  // L6 : resultat du chargement du modele, rattache a son code ; « en cours » se deduit
  // (plus de mise a jour synchrone d'etat au debut de l'effet).
  const [chargement, setChargement] = useState<{
    code: string;
    template: TemplateInfo | null;
    erreur: string | null;
  } | null>(null);
  const charge = chargement?.code === templateCode ? chargement : null;
  const template = charge?.template ?? null;
  const loadError = charge?.erreur ?? null;
  const loading = isOpen && !charge;
  const modeleCharge = !!charge;

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
    if (!isOpen || modeleCharge) return;
    let cancelled = false;
    listTemplatesForWorkflow(WORKFLOW_CODE)
      .then((items) => {
        if (cancelled) return;
        const found = items.find((t) => t.code === templateCode) ?? null;
        setChargement({
          code: templateCode,
          template: found,
          erreur: found
            ? null
            : `Le modèle « ${title} » est indisponible. Signalez-le à l’administrateur de la plateforme.`,
        });
      })
      .catch((err) => {
        if (cancelled) return;
        setChargement({
          code: templateCode,
          template: null,
          erreur: err instanceof Error ? err.message : 'Impossible de charger le modèle.',
        });
      });
    return () => {
      cancelled = true;
    };
  }, [isOpen, modeleCharge, templateCode, title]);

  return (
    <section className={embedded ? '' : 'rounded-xl border border-info/30 bg-info-soft'}>
      {!embedded && (
        <EnTeteDepliant
          ouvert={open}
          icone={icon}
          onClick={() => setOpen((v) => !v)}
          data-testid={`seance-doc-toggle-${templateCode}`}
        >
          <span className="text-sm font-semibold text-fg">{title} (facultatif)</span>
        </EnTeteDepliant>
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
              <Sparkles className="h-4 w-4 text-info" />
              <h4 className="text-sm font-semibold text-fg">Générer le document</h4>
            </div>

            {loading && <p className="text-sm text-fg-subtle">Chargement du modèle…</p>}
            {loadError && (
              <div className="rounded-lg border border-danger/30 bg-danger-soft p-3 text-sm text-danger" role="alert">
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
  // L4d (E11b) : texte des resolutions annexe (AGO), votes de la premiere assemblee (seconde).
  resolutions: true,
  presence: true,
  voix: false,
  convocationCorpus: true,
};

const FEUILLE_SECTIONS: SeanceSections = {
  seance: true,
  bureau: true,
  convocation: true,
  convocationRang: true,
  presence: true,
  voix: true,
  feuilleCorpus: true,
};

/**
 * L4d (E11b) : SARL a associe unique. Le corpus du cabinet n'a ni convocation ni feuille de
 * presence (article 76 de la loi 5-96) : le panneau l'explique au lieu de proposer un
 * document que le serveur refuserait.
 */
function SansObjetAssocieUnique({ document }: { document: 'convocation' | 'feuille' }) {
  return (
    <div data-testid={`seance-doc-sans-objet-${document}`}>
      <TexteAide
        cle={`seance-au-${document}`}
        titre={document === 'convocation' ? 'Pas de convocation en SARL à associé unique' : 'Pas de feuille de présence en SARL à associé unique'}
      >
        <p>
          {document === 'convocation'
            ? 'L’associé unique n’est pas convoqué : il exerce seul les pouvoirs de l’assemblée (article 76 de la loi n° 5-96). Aucune lettre de convocation n’est à produire ; passez directement à la décision de l’associé unique.'
            : 'L’associé unique décide seul et consigne sa décision au registre (article 76 de la loi n° 5-96) : il n’y a ni quorum ni feuille de présence à faire émarger.'}
        </p>
      </TexteAide>
    </div>
  );
}

export function ConvocationPanel(props: SeanceDocPanelBaseProps) {
  const sections = useMemo(() => CONVOCATION_SECTIONS, []);
  if (props.formeJuridique === 'SARL_AU') return <SansObjetAssocieUnique document="convocation" />;
  return (
    <SeanceDocPanel
      {...props}
      templateCode="CONVOCATION_AG"
      documentType="CONVOCATION"
      title="Convocation à l’assemblée"
      hint="À préparer AVANT l’assemblée. Renseignez la séance, la convocation, l’ordre du jour et les associés destinataires, puis générez la lettre de convocation du cabinet (une lettre par associé) : lettre de l’assemblée annuelle pour une assemblée ordinaire, de l’assemblée extraordinaire sinon, de seconde convocation au rang « Deuxième »."
      filenamePrefix="Convocation"
      icon={<CalendarClock className="h-4 w-4" />}
      sections={sections}
    />
  );
}

export function FeuillePresencePanel(props: SeanceDocPanelBaseProps) {
  const sections = useMemo(() => FEUILLE_SECTIONS, []);
  if (props.formeJuridique === 'SARL_AU') return <SansObjetAssocieUnique document="feuille" />;
  return (
    <SeanceDocPanel
      {...props}
      templateCode="FEUILLE_PRESENCE_AG"
      documentType="FEUILLE_PRESENCE"
      title="Feuille de présence"
      hint="À préparer APRÈS l’assemblée. Renseignez les présences (parts, voix, mandataire et date du pouvoir) par associé, puis générez la feuille de présence du cabinet : totaux, quotités et majorité sont calculés d’après ces présences."
      filenamePrefix="Feuille de présence"
      icon={<ClipboardList className="h-4 w-4" />}
      sections={sections}
    />
  );
}
