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
import { TexteAide } from '../ui/Aide';
import { WorkflowDocumentBlock } from './WorkflowDocumentBlock';
import { freshDocState, useDocumentBlocks, type DocState } from './documentBlocks';
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
  /** L4b (E7) : pièce d'identité imprimée par l'avis du corpus. */
  pieceType?: string;
  cin?: string;
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
  /** L4a (E2) : nature de la dissolution, saisie. */
  nature?: string;
  /** L4b (E7) : données de l'avis du corpus saisies à l'étape 1 (aucune n'est supposée). */
  dissolutionDetail?: {
    dateEffet?: string;
    termeDate?: string;
    exerciceClosDate?: string;
    clauseArticle?: string;
    siegeIdentique?: string;
    mandatDateEffet?: string;
  };
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
  nature,
  dissolutionDetail,
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
  // Libellé réel du modèle (documentKind) depuis le manifest — repli local si le
  // service est indisponible (dégradation gracieuse, jamais bloquante). L6 : le modele
  // trouve est rattache a son code ; le repli se deduit au rendu (plus de mise a jour
  // synchrone d'etat au debut de l'effet).
  const [trouve, setTrouve] = useState<TemplateInfo | null>(null);
  const tpl = useMemo(
    () => (trouve?.code === code ? trouve : makeTemplateInfo(code)),
    [trouve, code],
  );
  useEffect(() => {
    let cancelled = false;
    listTemplatesForWorkflow('DISSOLUTION')
      .then((items) => {
        if (cancelled) return;
        const found = items.find((t) => t.code === code);
        if (found) setTrouve(found);
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
    // L4b (E7) : la date d'effet est SAISIE (ou proposée puis confirmée), jamais la date
    // de l'assemblée supposée.
    dissolution: {
      motif: motif ?? '',
      nature: nature ?? '',
      date: dateAGE,
      dateEffet: dissolutionDetail?.dateEffet ?? '',
      termeDate: dissolutionDetail?.termeDate ?? '',
      exerciceClosDate: dissolutionDetail?.exerciceClosDate ?? '',
      clauseArticle: dissolutionDetail?.clauseArticle ?? '',
      siegeLiquidationIdentique: dissolutionDetail?.siegeIdentique ?? '',
    },
    liquidateur: {
      civilite: liquidateur.civilite ?? '',
      prenom: liquidateur.prenom ?? '',
      nom: liquidateur.nom ?? '',
      adresse: liquidateur.adresse ?? '',
      pieceType: liquidateur.pieceType ?? '',
      pieceNumero: liquidateur.cin ?? '',
      mandatDateEffet: dissolutionDetail?.mandatDateEffet ?? '',
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
        <Megaphone className="h-4 w-4 text-fg-muted" />
        <h4 className="text-sm font-semibold text-fg">Annonce légale de dissolution</h4>
      </div>
      <p className="text-xs text-fg-subtle">
        Avis de dissolution anticipée à publier dans un journal d’annonces légales. La
        société (dénomination, capital, siège, RC, ville du greffe), la date de{' '}
        {isAU ? 'la décision de l’associé unique' : 'l’assemblée générale extraordinaire'}, le
        liquidateur et le siège de la liquidation sont repris de l’étape 1 et de la Data
        Room : <strong>rien à ressaisir</strong>.
      </p>
      <TexteAide cle="dissolution-annonce" titre="Ce qu’il faut faire">
        Générez l’avis, relisez l’aperçu puis validez-le : il est déposé dans la Data Room
        du dossier. Le numéro et la date de dépôt légal se saisissent quand le greffe les
        délivre ; régénérez alors l’avis pour qu’ils y figurent.
      </TexteAide>

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
        l’annonce se génère sans elles (les emplacements restent vides). Régénérez l’annonce
        une fois le numéro connu si vous souhaitez l’y faire figurer.
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
