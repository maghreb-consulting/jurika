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
import { Select } from '../ui/Select';
import { InfoBulle, TexteAide } from '../ui/Aide';
import { ValeurProposee } from './ValeurProposee';
import { WorkflowDocumentBlock } from './WorkflowDocumentBlock';
import { freshDocState, useDocumentBlocks, type DocState, documentTravaille } from './documentBlocks';
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

/** L4b (E7) : données de l'avis de clôture (modèle du corpus). */
export interface AvisCloture {
  acteNature?: string;
  dissolutionDateEffet?: string;
  compteDefinitifDate?: string;
  quitus?: string;
  siegeLiquidation?: string;
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
  /** Comptes finaux (etape 1), transmis tels quels (l'avis ne publie pas le resultat). */
  totalActif: string;
  totalPassif: string;
  /** Numéro / date de dépôt légal persistés par la page hôte (facultatifs). */
  depotLegalNumero: string;
  depotLegalDate: string;
  onDepotLegalNumeroChange: (v: string) => void;
  onDepotLegalDateChange: (v: string) => void;
  /**
   * L4b (E7, D5) : données de l'avis de clôture du corpus, saisies ici et conservées par la
   * page (brouillon de l'étape 2). Aucune n'est supposée.
   */
  avis?: AvisCloture;
  onAvisChange?: (avis: AvisCloture) => void;
  /** Date de dissolution connue du dossier : proposée pour la date d'effet. */
  dateDissolution?: string;
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
  avis,
  onAvisChange,
  dateDissolution,
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
  // Libellé réel du modèle (documentKind) depuis le manifest — repli local si le
  // service est indisponible (dégradation gracieuse, jamais bloquante). L6 : le modele
  // trouve est rattache a son code ; le repli se deduit au rendu.
  const [trouve, setTrouve] = useState<TemplateInfo | null>(null);
  const tpl = useMemo(
    () => (trouve?.code === code ? trouve : makeTemplateInfo(code)),
    [trouve, code],
  );
  useEffect(() => {
    let cancelled = false;
    listTemplatesForWorkflow('LIQUIDATION')
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

  // Boni / mali DÉRIVÉ des comptes finaux (miroir du calcul backend).
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
      acteNature: avis?.acteNature ?? '',
      dissolutionDateEffet: avis?.dissolutionDateEffet ?? '',
      compteDefinitifDate: avis?.compteDefinitifDate ?? '',
      quitus: avis?.quitus ?? '',
      siegeLiquidation: avis?.siegeLiquidation ?? '',
      actifRealise: toNumber(totalActif),
      passifRegle: toNumber(totalPassif),
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
  const travaille = documentTravaille(docs[code]);
  useEffect(() => {
    if (travaille) onReady?.(ready);
  }, [ready, travaille, onReady]);

  return (
    <section className="space-y-4 rounded-xl border border-border bg-bg-raised p-4" data-testid="liquidation-annonce">
      <div className="flex items-center gap-2">
        <Megaphone className="h-4 w-4 text-fg-muted" />
        <h4 className="text-sm font-semibold text-fg">Annonce légale de clôture de liquidation</h4>
      </div>
      <p className="text-xs text-fg-subtle">
        Avis de clôture des opérations de liquidation à publier dans un journal d’annonces
        légales. La société (dénomination, capital, siège, RC, ville du greffe), la date de{' '}
        {isAU ? 'la décision de l’associé unique' : 'l’assemblée de clôture'}, le liquidateur
        et le résultat de liquidation sont repris de l’étape 1 et de la Data Room. Complétez
        ci-dessous ce que l’avis publie en plus : l’acte de clôture, la date d’effet de la
        dissolution, la date du compte définitif, le quitus et le siège de la liquidation. Avec le
        dépôt du dossier de clôture, cet avis fonde la <strong>radiation</strong> de la société au
        registre du commerce.
      </p>
      <TexteAide cle="liquidation-annonce" titre="Ce qu’il faut faire">
        Complétez les rubriques marquées d’un astérisque, générez l’avis, relisez l’aperçu
        puis validez-le : il est déposé dans la Data Room du dossier. Le numéro et la date de
        dépôt légal se saisissent quand le greffe les délivre ; régénérez alors l’avis.
      </TexteAide>

      {onAvisChange && (
        <div className="grid gap-3 md:grid-cols-2" data-testid="avis-cloture">
          <div>
            <label htmlFor="avis-acte-nature" className="mb-1.5 flex items-center gap-1 text-sm font-medium text-fg">
              Acte constatant la clôture *
              <InfoBulle
                libelle="Quel acte constate la clôture ?"
                texte="En principe la décision des associés (ou de l’associé unique) qui approuve le compte définitif. À défaut de délibération, ou si les comptes ne sont pas approuvés, le tribunal statue : l’avis le dit alors."
              />
            </label>
            <Select id="avis-acte-nature"
              value={avis?.acteNature ?? ''} onChange={(e) => onAvisChange({ ...avis, acteNature: e.target.value })}
              options={[
                { value: '', label: 'Choisir…' },
                {
                  value: isAU ? "Décision de l'associé unique" : 'Décision des associés',
                  label: isAU ? 'Décision de l’associé unique' : 'Décision des associés',
                },
                { value: 'Décision de justice', label: 'Décision de justice' },
              ]} />
          </div>
          <ValeurProposee
            id="avis-dissolution-date-effet"
            label="Date d’effet de la dissolution *"
            type="date"
            proposition={dateDissolution ?? ''}
            valeur={avis?.dissolutionDateEffet || undefined}
            onChange={(v) => onAvisChange({ ...avis, dissolutionDateEffet: v })}
          />
          <TextField label="Compte définitif arrêté au *" type="date" value={avis?.compteDefinitifDate ?? ''}
            onChange={(e) => onAvisChange({ ...avis, compteDefinitifDate: e.target.value })} />
          <Select id="avis-quitus" label="Quitus au liquidateur *"
            value={avis?.quitus ?? ''} onChange={(e) => onAvisChange({ ...avis, quitus: e.target.value })}
            options={[
              { value: '', label: 'Choisir…' },
              { value: 'accorde', label: 'Quitus accordé' },
              { value: 'sous_reserve', label: 'Quitus accordé sous réserves' },
              { value: 'sans_quitus', label: 'Décharge du mandat, sans quitus' },
            ]} />
          <div className="md:col-span-2">
            <TextField label="Siège de la liquidation *" value={avis?.siegeLiquidation ?? ''}
              onChange={(e) => onAvisChange({ ...avis, siegeLiquidation: e.target.value })}
              hint="Adresse publiée en tête de l’avis (celle fixée à la dissolution)." />
          </div>
        </div>
      )}

      {/* L6b (E19) : l'avis du corpus ne publie pas le resultat ; il affichait "Resultat publie :
          boni de 100 000 DH" (actif - passif) pour un mali de 5 000 au rapport final. */}
      <p className="rounded-lg border border-border bg-bg-overlay p-3 text-xs text-fg-muted" data-testid="annonce-resultat">
        L’avis de clôture ne publie pas le résultat de la liquidation : le boni ou le mali figure au
        procès-verbal de clôture et au rapport final du liquidateur, qui le calcule.
      </p>

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
