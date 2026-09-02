/**
 * Annonce légale de CLÔTURE DE LIQUIDATION — lot « Liquidation 4 étapes » (2026-08-13).
 *
 * Génère l'avis de clôture ({@code ANNONCE_LEGALE_LIQUIDATION_SARL(/_AU)}, modèles directeur)
 * à publier au Journal d'Annonces Légales. Second et dernier avis du cycle : avec le dépôt du
 * dossier de clôture, il fonde la **radiation** de la société au registre du commerce.
 *
 * ZÉRO re-saisie : tout provient de l'étape 1 (date de l'AGE de clôture, comptes finaux) et de
 * la BD (dénomination, capital, siège social, RC, ville du greffe — enrichis côté ai-service
 * par `SocieteIdentityEnricher` à partir du `dossierId` ; liquidateur nommé à la dissolution).
 * Le sens du résultat (boni / mali) et son montant sont **dérivés** des comptes finaux : ils
 * ne sont jamais demandés séparément.
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

export const ANNONCE_LIQUIDATION_SARL = 'ANNONCE_LEGALE_LIQUIDATION_SARL';
export const ANNONCE_LIQUIDATION_SARL_AU = 'ANNONCE_LEGALE_LIQUIDATION_SARL_AU';

/** Identité du liquidateur, nommé à la dissolution et relu depuis la BD. */
export interface AnnonceLiquidateurInput {
  civilite?: string;
  prenom?: string;
  nom?: string;
}

export interface LiquidationAnnoncePanelProps {
  dossierId?: string | null;
  ticketId?: string | null;
  formeJuridique: 'SARL' | 'SARL_AU';
  /** Dénomination (repli si la BD ne répond pas) — le reste de l'identité vient de la BD. */
  denomination?: string | null;
  /** Date de l'AGE de clôture / de la décision de l'associé unique = $DATE_CLOTURE_LIQUIDATION. */
  dateCloture: string;
  liquidateur: AnnonceLiquidateurInput;
  /** Comptes finaux (étape 1) : le boni/mali en est DÉRIVÉ, jamais saisi séparément. */
  totalActif: string;
  totalPassif: string;
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
    documentKind: 'Annonce légale (clôture de liquidation)',
    file: `${code}.docx`,
    origin: 'directeur-2026-08',
    deprecated: false,
    placeholderStyle: 'dollar_nu_directeur',
  };
}

function toNumber(v: string): number | null {
  if (!v || !v.trim()) return null;
  const n = Number.parseFloat(v);
  return Number.isNaN(n) ? null : n;
}

export function LiquidationAnnoncePanel({
  dossierId,
  ticketId,
  formeJuridique,
  denomination,
  dateCloture,
  liquidateur,
  totalActif,
  totalPassif,
  depotLegalNumero,
  depotLegalDate,
  onDepotLegalNumeroChange,
  onDepotLegalDateChange,
  onReady,
  filenameFor,
  depositMotif,
}: LiquidationAnnoncePanelProps) {
  const isAU = formeJuridique === 'SARL_AU';
  const code = isAU ? ANNONCE_LIQUIDATION_SARL_AU : ANNONCE_LIQUIDATION_SARL;

  const [docs, setDocs] = useState<Record<string, DocState>>({});
  const [tpl, setTpl] = useState<TemplateInfo>(() => makeTemplateInfo(code));

  // Libellé réel du modèle (documentKind) depuis le manifest — repli local si le
  // service est indisponible (dégradation gracieuse, jamais bloquante).
  useEffect(() => {
    let cancelled = false;
    setTpl(makeTemplateInfo(code));
    listTemplatesForWorkflow('LIQUIDATION')
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

  // Boni / mali DÉRIVÉ des comptes finaux (miroir du calcul backend).
  const resultat = useMemo(() => {
    const actif = toNumber(totalActif);
    const passif = toNumber(totalPassif);
    if (actif === null || passif === null) return null;
    const solde = actif - passif;
    return { sens: (solde < 0 ? 'mali' : 'boni') as 'boni' | 'mali', montant: Math.abs(solde) };
  }, [totalActif, totalPassif]);

  const buildPayload = (): Record<string, unknown> => ({
    formeJuridique,
    // Identité société : la BD gagne (enrichissement via dossierId) ; on ne fournit
    // que la dénomination en repli — AUCUNE re-saisie d'identité.
    societe: { denomination: denomination ?? '' },
    seance: { type: 'extraordinaire', date: dateCloture },
    // Liquidateur nommé à la dissolution (lecture seule côté page).
    liquidateur: {
      civilite: liquidateur.civilite ?? '',
      prenom: liquidateur.prenom ?? '',
      nom: liquidateur.nom ?? '',
    },
    cloture: {
      dateClotureLiquidation: dateCloture,
      actifRealise: toNumber(totalActif),
      passifRegle: toNumber(totalPassif),
      resultatType: resultat?.sens ?? null,
      resultatMontant: resultat?.montant ?? null,
    },
    // Attribués par le greffe APRÈS dépôt → facultatifs (rendus vides si absents).
    depotLegal: { numero: depotLegalNumero || null, date: depotLegalDate || null },
  });

  const { updateDoc, generateOne, downloadOne, validateOne } = useDocumentBlocks({
    workflowCode: 'LIQUIDATION',
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
    <section className="space-y-4 rounded-xl border border-border bg-bg-raised p-4" data-testid="liquidation-annonce">
      <div className="flex items-center gap-2">
        <Megaphone className="h-4 w-4 text-accent" />
        <h4 className="text-sm font-semibold text-fg">Annonce légale de clôture de liquidation</h4>
      </div>
      <p className="text-xs text-fg-subtle">
        Avis de clôture des opérations de liquidation à publier au Journal d'Annonces Légales.
        La société (dénomination, capital, siège, RC, ville du greffe), la date de l'
        {isAU ? "assemblée / décision de l'associé unique" : 'AGE de clôture'}, le liquidateur
        et le résultat de liquidation sont repris de l'étape 1 et de la Data Room —{' '}
        <strong>rien à re-saisir</strong>. Avec le dépôt du dossier de clôture, cet avis fonde
        la <strong>radiation</strong> de la société au registre du commerce.
      </p>

      {resultat && (
        <p className="rounded-lg border border-border bg-bg-overlay p-3 text-xs text-fg-muted" data-testid="annonce-resultat">
          Résultat publié :{' '}
          <strong className={resultat.sens === 'boni' ? 'text-emerald-700' : 'text-rose-700'}>
            {resultat.sens === 'boni' ? 'boni' : 'mali'} de liquidation de{' '}
            {resultat.montant.toLocaleString('fr-FR')} DH
          </strong>{' '}
          — calculé depuis les comptes finaux de l'étape 1.
        </p>
      )}

      <div className="grid gap-3 md:grid-cols-2">
        <TextField
          label="N° de dépôt légal (facultatif)"
          value={depotLegalNumero}
          data-testid="liquidation-depot-numero"
          onChange={(e) => onDepotLegalNumeroChange(e.target.value)}
        />
        <TextField
          label="Date de dépôt légal (facultatif)"
          type="date"
          value={depotLegalDate}
          data-testid="liquidation-depot-date"
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
