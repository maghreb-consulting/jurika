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
  type SeanceSections,
  type SeanceSocieteInput,
} from './seanceFormEtat';
import { OUI_NON, RES_SPECS, flattenResolution, type ResolutionState } from './modificationResolutionsSpecs';
import type { DocumentType } from '../../types/dataroom';
import { ValeurProposee } from './ValeurProposee';
import { InfoBulle, TexteAide } from '../ui/Aide';
import { USAGES_CABINET } from '../../lib/usagesCabinet';

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
  vote: true, // L3 (D6) : quorum et vote saisis
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

/**
 * L4d (E11c) : le PV de modification est rendu par les modeles du corpus du cabinet
 * (PV_AG_SARL, PV_DECISIONS_ASSOCIE_UNIQUE_SARL_AU) ; le code logique reste PV_MODIFICATION_*.
 */
function makeTemplateInfo(code: string): TemplateInfo {
  return {
    code,
    documentKind: 'PV de modification',
    file: `${code}.docx`,
    origin: 'corpus',
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
  // L4d (E11c) : nombre d'originaux PROPOSE (usage du cabinet), transmis une fois confirme ;
  // registre des decisions de l'associe unique (numero et feuillet) saisi.
  const [nombreOriginaux, setNombreOriginaux] = useState<string | undefined>(undefined);
  const [registreNumero, setRegistreNumero] = useState('');
  const [registreFeuillet, setRegistreFeuillet] = useState('');

  const [docs, setDocs] = useState<Record<string, DocState>>({});
  const [templates, setTemplates] = useState<TemplateInfo[]>([]);
  // L6 : erreur de chargement rattachee au modele demande ; « en cours » se deduit (plus
  // de mise a jour synchrone d'etat au debut de l'effet).
  const [erreurModele, setErreurModele] = useState<{ code: string; message: string } | null>(null);
  const loadTemplatesError = erreurModele?.code === pvCode ? erreurModele.message : null;
  const loadingTemplates = templates.length === 0 && !loadTemplatesError;

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
      // L3 (D6) : quorumAtteint vient du formulaire de seance (saisi), plus de "oui" impose.
      gerantEstAssocie,
      nombreOriginaux: nombreOriginaux ?? '',
      ...(isAU ? { registre: { numero: registreNumero.trim(), feuillet: registreFeuillet.trim() } } : {}),
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
    listTemplatesForWorkflow(WORKFLOW_CODE)
      .then((items) => {
        if (cancelled) return;
        const found = items.find((t) => t.code === pvCode);
        setTemplates([found ?? makeTemplateInfo(pvCode)]);
      })
      .catch((err) => {
        if (cancelled) return;
        setErreurModele({
          code: pvCode,
          message: err instanceof Error ? err.message : 'Impossible de charger le modèle.',
        });
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
      <Section title={isAU ? 'Options des décisions de l’associé unique' : 'Options de l’assemblée'}>
        <TexteAide cle={isAU ? 'pv-modification-options-au' : 'pv-modification-options'} titre="Que renseigner ici ?">
          {isAU
            ? 'Confirmez le nombre d’exemplaires originaux et indiquez le numéro et le feuillet du registre des décisions de l’associé unique, que le procès-verbal cite. Sans ces données, la génération est refusée et les nomme. Ensuite, générez le procès-verbal, relisez-le puis validez-le.'
            : 'Indiquez si un commissaire aux comptes est en fonction et confirmez le nombre d’exemplaires originaux du procès-verbal. Sans ces données, la génération est refusée et les nomme. Ensuite, générez le procès-verbal, relisez-le puis validez-le.'}
        </TexteAide>
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
              label="Le gérant est-il l’associé unique ?"
              value={gerantEstAssocie}
              onChange={(e) => setGerantEstAssocie(e.target.value as 'oui' | 'non')}
              options={OUI_NON}
            />
          )}
          <ValeurProposee
            id="pv-modification-nombre-originaux"
            label="Nombre d’exemplaires originaux"
            proposition={USAGES_CABINET.nombreOriginauxEnLettres}
            valeur={nombreOriginaux}
            onChange={setNombreOriginaux}
          />
          {isAU && (
            <>
              <div className="flex items-start gap-1">
                <div className="min-w-0 flex-1">
                  <TextField
                    label="N° du registre des décisions"
                    value={registreNumero}
                    onChange={(e) => setRegistreNumero(e.target.value)}
                  />
                </div>
                <span className="mt-6">
                  <InfoBulle
                    libelle="Aide : registre des décisions"
                    texte="Le procès-verbal est répertorié au registre des décisions de l’associé unique, coté et paraphé, tenu au siège social (articles 76 et 112 de la loi n° 5-96) : reportez le numéro et le feuillet où il sera transcrit."
                  />
                </span>
              </div>
              <TextField
                label="Feuillet du registre"
                value={registreFeuillet}
                onChange={(e) => setRegistreFeuillet(e.target.value)}
              />
            </>
          )}
        </div>
      </Section>

      {/* Récapitulatif des résolutions (saisie à l'étape 2). */}
      <Section title="Résolutions à intégrer au procès-verbal">
        {resolutions.length === 0 ? (
          <p className="text-sm text-fg-subtle">
            Aucune résolution : revenez à l’étape 1 pour sélectionner des décisions, puis
            renseignez-les à l’étape 2.
          </p>
        ) : (
          <ol className="space-y-2">
            {resolutions.map((r, i) => (
              <li
                key={r.id}
                className="flex items-start gap-2 rounded-lg border border-border bg-bg p-2 text-sm"
              >
                <span className="flex h-5 w-5 flex-shrink-0 items-center justify-center rounded bg-info text-[10px] font-bold text-info-fg">
                  {i + 1}
                </span>
                <span className="text-fg">{r.objet.trim() || RES_SPECS[r.type]?.label || 'Résolution'}</span>
              </li>
            ))}
          </ol>
        )}
      </Section>

      {/* Génération du PV. */}
      <div className="space-y-3">
        <div className="flex items-center gap-2">
          <Sparkles className="h-4 w-4 text-info" />
          <h4 className="text-sm font-semibold text-fg">Générer le procès-verbal de modification</h4>
        </div>
        {loadingTemplates && <p className="text-sm text-fg-subtle">Chargement du modèle…</p>}
        {loadTemplatesError && (
          <div className="rounded-lg border border-danger/30 bg-danger-soft p-3 text-sm text-danger" role="alert">
            {loadTemplatesError}
          </div>
        )}
        {resolutions.length === 0 && (
          <p className="text-sm text-fg-subtle">Ajoutez au moins une résolution pour générer le procès-verbal.</p>
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
        <Sparkles className="h-4 w-4 text-info" />
        <h4 className="text-sm font-semibold text-fg">{title}</h4>
      </div>
      {children}
    </section>
  );
}
