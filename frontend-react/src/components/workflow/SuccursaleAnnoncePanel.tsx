/**
 * Annonce légale de SUCCURSALE — lot DIVERS (2026-08-13).
 *
 * Deux avis, un seul panneau :
 *  - `ouverture` → `ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_SARL(/_AU)` (workflow SUCCURSALE_MA) ;
 *  - `fermeture` → `ANNONCE_LEGALE_FERMETURE_SUCCURSALE_SARL(/_AU)` (workflow FERMETURE_SUCCURSALE).
 *
 * ZÉRO re-saisie : l'identité de la société mère vient de la BD (enrichie côté ai-service
 * par `SocieteIdentityEnricher` à partir du `dossierId`), la date d'assemblée vient de
 * l'étape 1 et le bloc `succursale` est EXACTEMENT celui de l'étape de saisie — le même
 * qui alimente le PV. L'avis et l'acte ne peuvent donc pas diverger.
 *
 * Seuls champs saisis ici : le **numéro** et la **date de dépôt légal**, attribués par le
 * greffe APRÈS le dépôt — donc inconnus à la génération. Ils sont **facultatifs** :
 * l'annonce se génère sans eux (emplacements rendus vides, sans marqueur résiduel).
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

export type SuccursaleAnnonceMode = 'ouverture' | 'ouverture-etrangere' | 'fermeture';

export const ANNONCE_OUVERTURE_SUCCURSALE_SARL = 'ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_SARL';
export const ANNONCE_OUVERTURE_SUCCURSALE_SARL_AU = 'ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_SARL_AU';
export const ANNONCE_OUVERTURE_SUCCURSALE_ETR_SARL =
  'ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_ETRANGERE_SARL';
export const ANNONCE_OUVERTURE_SUCCURSALE_ETR_SARL_AU =
  'ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_ETRANGERE_SARL_AU';
export const ANNONCE_FERMETURE_SUCCURSALE_SARL = 'ANNONCE_LEGALE_FERMETURE_SUCCURSALE_SARL';
export const ANNONCE_FERMETURE_SUCCURSALE_SARL_AU = 'ANNONCE_LEGALE_FERMETURE_SUCCURSALE_SARL_AU';

const CODES: Record<SuccursaleAnnonceMode, { sarl: string; au: string }> = {
  ouverture: {
    sarl: ANNONCE_OUVERTURE_SUCCURSALE_SARL,
    au: ANNONCE_OUVERTURE_SUCCURSALE_SARL_AU,
  },
  'ouverture-etrangere': {
    sarl: ANNONCE_OUVERTURE_SUCCURSALE_ETR_SARL,
    au: ANNONCE_OUVERTURE_SUCCURSALE_ETR_SARL_AU,
  },
  fermeture: {
    sarl: ANNONCE_FERMETURE_SUCCURSALE_SARL,
    au: ANNONCE_FERMETURE_SUCCURSALE_SARL_AU,
  },
};

const WORKFLOW_CODE: Record<SuccursaleAnnonceMode, string> = {
  ouverture: 'SUCCURSALE_MA',
  'ouverture-etrangere': 'SUCCURSALE_ETR',
  fermeture: 'FERMETURE_SUCCURSALE',
};

/** Société mère étrangère (mode `ouverture-etrangere`) — variables `$SOCIETE_MERE_*`. */
export interface SocieteMereAnnonceInput {
  denomination?: string;
  forme?: string;
  pays?: string;
  capital?: string;
  siege?: string;
  registre?: string;
  registreNumero?: string;
  loiApplicable?: string;
}

/** Bloc succursale tel que saisi à l'étape dédiée — passé tel quel au back. */
export interface SuccursaleAnnonceInput {
  enseigne?: string;
  adresse?: string;
  ville?: string;
  villeGreffe?: string;
  activite?: string;
  /** ouverture */
  dateOuverture?: string;
  dotationPresente?: boolean;
  dotationMontant?: string | number;
  responsablePresent?: boolean;
  responsable?: {
    civilite?: string;
    prenom?: string;
    nom?: string;
    nationalite?: string;
    adresse?: string;
    pieceType?: string;
    pieceNumero?: string;
    pouvoirs?: string;
  };
  /** fermeture */
  rcNumero?: string;
  dateFermeture?: string;
  motif?: string;
}

export interface SuccursaleAnnoncePanelProps {
  mode: SuccursaleAnnonceMode;
  dossierId?: string | null;
  ticketId?: string | null;
  formeJuridique: 'SARL' | 'SARL_AU';
  /** Dénomination (repli si la BD ne répond pas) — le reste de l'identité vient de la BD. */
  denomination?: string | null;
  /** Date de l'assemblée / de la décision de l'associé unique = `$ASSEMBLEE_DATE`. */
  dateAssemblee: string;
  /** Type d'assemblée retenu à l'étape 1 (ordinaire / extraordinaire). */
  typeAssemblee?: 'ordinaire' | 'extraordinaire';
  /** Bloc succursale de l'étape de saisie — source unique, partagée avec le PV. */
  succursale: SuccursaleAnnonceInput;
  /** Mode `ouverture-etrangere` : identité de la mère (aucune identité en base). */
  societeMere?: SocieteMereAnnonceInput;
  /** Mode `ouverture-etrangere` : organe décisionnaire publié (`$ORGANE_COMPETENT`). */
  organeCompetent?: string;
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

function makeTemplateInfo(code: string, mode: SuccursaleAnnonceMode): TemplateInfo {
  return {
    code,
    documentKind:
      mode === 'fermeture'
        ? 'Annonce légale (fermeture de succursale)'
        : 'Annonce légale (ouverture de succursale)',
    file: `${code}.docx`,
    // ⚠ La variante étrangère est DÉRIVÉE, pas livrée par le directeur.
    origin: mode === 'ouverture-etrangere' ? 'derive-jurika' : 'directeur-2026-08',
    deprecated: false,
    placeholderStyle: 'dollar_nu_directeur',
  };
}

export function SuccursaleAnnoncePanel({
  mode,
  dossierId,
  ticketId,
  formeJuridique,
  denomination,
  dateAssemblee,
  typeAssemblee = 'extraordinaire',
  succursale,
  societeMere,
  organeCompetent,
  depotLegalNumero,
  depotLegalDate,
  onDepotLegalNumeroChange,
  onDepotLegalDateChange,
  onReady,
  filenameFor,
  depositMotif,
}: SuccursaleAnnoncePanelProps) {
  const isAU = formeJuridique === 'SARL_AU';
  const code = isAU ? CODES[mode].au : CODES[mode].sarl;
  const workflowCode = WORKFLOW_CODE[mode];

  const [docs, setDocs] = useState<Record<string, DocState>>({});
  const [tpl, setTpl] = useState<TemplateInfo>(() => makeTemplateInfo(code, mode));

  // Libellé réel du modèle (documentKind) depuis le manifest — repli local si le
  // service est indisponible (dégradation gracieuse, jamais bloquante).
  useEffect(() => {
    let cancelled = false;
    setTpl(makeTemplateInfo(code, mode));
    listTemplatesForWorkflow(workflowCode)
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
  }, [code, mode, workflowCode]);

  const buildPayload = (): Record<string, unknown> => ({
    formeJuridique,
    // Identité société : la BD gagne (enrichissement via dossierId) ; on ne fournit
    // que la dénomination en repli — AUCUNE re-saisie d'identité.
    societe: { denomination: denomination ?? '' },
    seance: { type: typeAssemblee, date: dateAssemblee },
    // Mode étranger : la mère n'est PAS en base, elle est saisie à l'étape 1.
    ...(mode === 'ouverture-etrangere'
      ? {
          societeMere: societeMere ?? {},
          organe: { competent: organeCompetent ?? '', date: dateAssemblee },
        }
      : {}),
    // Bloc succursale IDENTIQUE à celui du PV : l'avis ne peut pas en diverger.
    succursale: {
      enseigne: succursale.enseigne ?? '',
      adresse: succursale.adresse ?? '',
      ville: succursale.ville ?? '',
      villeGreffe: succursale.villeGreffe || succursale.ville || '',
      activite: succursale.activite ?? '',
      dateOuverture: succursale.dateOuverture ?? '',
      dotationPresente: succursale.dotationPresente ? 'oui' : 'non',
      dotationMontant: succursale.dotationPresente ? succursale.dotationMontant ?? '' : '',
      responsablePresent: succursale.responsablePresent ? 'oui' : 'non',
      responsable: succursale.responsablePresent ? succursale.responsable ?? {} : {},
      rcNumero: succursale.rcNumero ?? '',
      dateFermeture: succursale.dateFermeture ?? '',
      motif: succursale.motif ?? '',
    },
    // Attribués par le greffe APRÈS dépôt → facultatifs (rendus vides si absents).
    depotLegal: { numero: depotLegalNumero || null, date: depotLegalDate || null },
  });

  const { updateDoc, generateOne, downloadOne, validateOne } = useDocumentBlocks({
    workflowCode,
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

  const titre =
    mode === 'fermeture'
      ? 'Annonce légale de fermeture de succursale'
      : mode === 'ouverture-etrangere'
        ? "Annonce légale d'ouverture de succursale (société mère étrangère)"
        : "Annonce légale d'ouverture de succursale";

  return (
    <section
      className="space-y-4 rounded-xl border border-border bg-bg-raised p-4"
      data-testid={`succursale-annonce-${mode}`}
    >
      <div className="flex items-center gap-2">
        <Megaphone className="h-4 w-4 text-accent" />
        <h4 className="text-sm font-semibold text-fg">{titre}</h4>
      </div>
      <p className="text-xs text-fg-subtle">
        Avis à publier au Journal d'Annonces Légales. La société mère (dénomination, capital,
        siège, RC, ville du greffe), la date de l'
        {isAU ? "décision de l'associé unique" : 'assemblée'} et les caractéristiques de la
        succursale sont repris des étapes précédentes et de la Data Room —{' '}
        <strong>rien à re-saisir</strong>.
        {mode !== 'fermeture' && (
          <>
            {' '}
            La succursale sera immatriculée au registre du commerce de{' '}
            <strong>{succursale.villeGreffe || succursale.ville || '—'}</strong>, distinct de
            celui du siège.
          </>
        )}
      </p>

      <div className="grid gap-3 md:grid-cols-2">
        <TextField
          label="N° de dépôt légal (facultatif)"
          value={depotLegalNumero}
          data-testid={`succursale-${mode}-depot-numero`}
          onChange={(e) => onDepotLegalNumeroChange(e.target.value)}
        />
        <TextField
          label="Date de dépôt légal (facultatif)"
          type="date"
          value={depotLegalDate}
          data-testid={`succursale-${mode}-depot-date`}
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
