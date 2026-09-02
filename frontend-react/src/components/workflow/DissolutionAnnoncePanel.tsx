/**
 * Annonce légale de DISSOLUTION — lot « Dissolution 4 étapes » (2026-08-12).
 *
 * Génère l'avis de dissolution anticipée ({@code ANNONCE_LEGALE_DISSOLUTION_SARL(/_AU)},
 * modèles directeur) à publier au Journal d'Annonces Légales.
 *
 * ZÉRO re-saisie : tout provient de l'étape 1 (date de l'AGE, liquidateur, siège de la
 * liquidation) et de la BD (dénomination, capital, siège social, RC, ville du greffe —
 * enrichis côté ai-service par `SocieteIdentityEnricher` à partir du `dossierId`).
 *
 * Seuls champs saisis ici : le **numéro** et la **date de dépôt légal**, attribués par le
 * greffe APRÈS le dépôt — donc inconnus à la génération. Ils sont **facultatifs** :
 * l'annonce se génère sans eux (rendus vides, sans marqueur résiduel).
 */
import { useEffect, useMemo, useState } from 'react';
import { Megaphone } from 'lucide-react';
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
import type { DocumentType } from '../../types/dataroom';

export const ANNONCE_DISSOLUTION_SARL = 'ANNONCE_LEGALE_DISSOLUTION_SARL';
export const ANNONCE_DISSOLUTION_SARL_AU = 'ANNONCE_LEGALE_DISSOLUTION_SARL_AU';

/** Identité du liquidateur retenue à l'étape 1 (BD ou externe). */
export interface AnnonceLiquidateurInput {
  civilite?: string;
  prenom?: string;
  nom?: string;
  adresse?: string;
}

export interface DissolutionAnnoncePanelProps {
  dossierId?: string | null;
  ticketId?: string | null;
  formeJuridique: 'SARL' | 'SARL_AU';
  /** Dénomination (repli si la BD ne répond pas) — le reste de l'identité vient de la BD. */
  denomination?: string | null;
  /** Date de l'AGE / de la décision de l'associé unique (étape 1) = $DATE_DISSOLUTION. */
  dateAGE: string;
  /** Motif de dissolution (étape 1) — tracé au dépôt, non publié dans l'avis. */
  motif?: string;
  liquidateur: AnnonceLiquidateurInput;
  /** Siège de la liquidation (étape 1) = $SIEGE_LIQUIDATION. */
  siegeLiquidation: string;
  /** Numéro / date de dépôt légal persistés par la page hôte (facultatifs). */
  depotLegalNumero: string;
  depotLegalDate: string;
  onDepotLegalNumeroChange: (v: string) => void;
  onDepotLegalDateChange: (v: string) => void;
  /** Notifié quand l'annonce est générée ET validée. */
  onReady?: (ready: boolean) => void;
  /** Nommage `type - dénomination - forme (- v<n>)`. */
  filenameFor?: (tpl: TemplateInfo, opts?: { version?: number }) => string | undefined;
  /** Motif tracé sur le dépôt Data Room versionné. */
  depositMotif?: string;
}

function makeTemplateInfo(code: string): TemplateInfo {
  return {
    code,
    documentKind: 'Annonce légale (dissolution)',
    file: `${code}.docx`,
    origin: 'directeur-2026-08',
    deprecated: false,
    placeholderStyle: 'dollar_nu_directeur',
  };
}

export function DissolutionAnnoncePanel({
  dossierId,
  ticketId,
  formeJuridique,
  denomination,
  dateAGE,
  motif,
  liquidateur,
  siegeLiquidation,
  depotLegalNumero,
  depotLegalDate,
  onDepotLegalNumeroChange,
  onDepotLegalDateChange,
  onReady,
  filenameFor,
  depositMotif,
}: DissolutionAnnoncePanelProps) {
  const isAU = formeJuridique === 'SARL_AU';
  const code = isAU ? ANNONCE_DISSOLUTION_SARL_AU : ANNONCE_DISSOLUTION_SARL;

  const [docs, setDocs] = useState<Record<string, DocState>>({});
  const [tpl, setTpl] = useState<TemplateInfo>(() => makeTemplateInfo(code));

  // Libellé réel du modèle (documentKind) depuis le manifest — repli local si le
  // service est indisponible (dégradation gracieuse, jamais bloquante).
  useEffect(() => {
    let cancelled = false;
    setTpl(makeTemplateInfo(code));
    listTemplatesForWorkflow('DISSOLUTION')
      .then((items) => {
        if (cancelled) return;
        const found = items.find((t) => t.code === code);
        if (found) setTpl(found);
      })
      .catch(() => {
        /* best-effort */
      });
    return () => {
      cancelled = true;
    };
  }, [code]);

  const buildPayload = (): Record<string, unknown> => ({
    formeJuridique,
    // Identité société : la BD gagne (enrichissement via dossierId) ; on ne fournit
    // que la dénomination en repli — AUCUNE re-saisie d'identité.
    societe: { denomination: denomination ?? '' },
    seance: { type: 'extraordinaire', date: dateAGE },
    dissolution: { motif: motif ?? 'volontaire', date: dateAGE, dateEffet: dateAGE },
    liquidateur: {
      civilite: liquidateur.civilite ?? '',
      prenom: liquidateur.prenom ?? '',
      nom: liquidateur.nom ?? '',
      adresse: liquidateur.adresse ?? '',
      siege: siegeLiquidation,
    },
    siegeLiquidation,
    // Attribués par le greffe APRÈS dépôt → facultatifs (rendus vides si absents).
    depotLegal: { numero: depotLegalNumero || null, date: depotLegalDate || null },
  });

  const { updateDoc, generateOne, downloadOne, validateOne } = useDocumentBlocks({
    workflowCode: 'DISSOLUTION',
    dossierId,
    ticketId,
    mapType: (): DocumentType => 'ANNONCE_JAL',
    docs,
    setDocs,
    buildPayload,
    versioned: true,
    motif: depositMotif,
    filenameFor,
  });

  const state = docs[code] ?? freshDocState();
  const ready = useMemo(() => !!docs[code]?.validated, [docs, code]);
  useEffect(() => {
    onReady?.(ready);
  }, [ready, onReady]);

  return (
    <section className="space-y-4 rounded-xl border border-border bg-bg-raised p-4" data-testid="dissolution-annonce">
      <div className="flex items-center gap-2">
        <Megaphone className="h-4 w-4 text-accent" />
        <h4 className="text-sm font-semibold text-fg">Annonce légale de dissolution</h4>
      </div>
      <p className="text-xs text-fg-subtle">
        Avis de dissolution anticipée à publier au Journal d'Annonces Légales. La société
        (dénomination, capital, siège, RC, ville du greffe), la date de l'
        {isAU ? "assemblée / décision de l'associé unique" : 'AGE'}, le liquidateur et le
        siège de la liquidation sont repris de l'étape 1 et de la Data Room —{' '}
        <strong>rien à re-saisir</strong>.
      </p>

      <div className="grid gap-3 md:grid-cols-2">
        <TextField
          label="N° de dépôt légal (facultatif)"
          value={depotLegalNumero}
          data-testid="dissolution-depot-numero"
          onChange={(e) => onDepotLegalNumeroChange(e.target.value)}
        />
        <TextField
          label="Date de dépôt légal (facultatif)"
          type="date"
          value={depotLegalDate}
          data-testid="dissolution-depot-date"
          onChange={(e) => onDepotLegalDateChange(e.target.value)}
        />
      </div>
      <p className="text-[11px] text-fg-subtle">
        Ces deux informations sont attribuées par le greffe <strong>après</strong> le dépôt :
        l'annonce se génère sans elles (les emplacements restent vides). Régénérez l'annonce
        une fois le numéro connu si vous souhaitez l'y faire figurer.
      </p>

      <WorkflowDocumentBlock
        tpl={tpl}
        state={state}
        onGenerate={() => generateOne(tpl)}
        onDownload={() => downloadOne(tpl)}
        onRegenerate={() => generateOne(tpl)}
        onValidate={() => validateOne(tpl)}
        onTogglePreview={() => updateDoc(code, { previewOpen: !docs[code]?.previewOpen })}
      />
    </section>
  );
}
