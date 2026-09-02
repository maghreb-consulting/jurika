import { useMemo, useState } from 'react';
import {
  AlertTriangle,
  CheckCircle,
  ChevronRight,
  FileText,
  IdCard,
  Info,
  Plus,
  Upload,
  X,
} from 'lucide-react';

interface Props {
  existing?: Record<string, unknown>;
  /** Donnees consolidees du wizard pour pre-remplir les CIN par personne. */
  data?: Record<string, Record<string, unknown>>;
  /** Registre persistant des pieces deja uploadees (cross-step). */
  registeredPieces?: Record<string, RegisteredPiece>;
  saving: boolean;
  onSubmit: (payload: Record<string, unknown>) => Promise<void>;
  /** Cascade depot dataroom (cross-step). */
  onPieceUploaded?: (
    code: string,
    label: string,
    file: File,
  ) => Promise<void> | void;
}

interface RegisteredPiece {
  code: string;
  label: string;
  filename?: string;
  uploadedAt?: string;
  uploadedAtStep?: number;
}

interface Piece {
  code: string;
  label: string;
  description: string;
  obligatoire: boolean;
  fileName?: string;
  /** Marque que la piece provient du registre cross-step (deja uploadee ailleurs). */
  fromRegistry?: boolean;
}

interface CinPersonne {
  code: string;
  label: string;
  role: 'DIRIGEANT' | 'ASSOCIE';
  cinNumero?: string;
  fileName?: string;
}

const REQUIRED_BASE_CODES = ['CN', 'JUSTIFICATIF_SIEGE', 'STATUTS_VALIDES'];

const DEFAULT_PIECES: Piece[] = [
  {
    code: 'CN',
    label: 'Certificat negatif (CN)',
    description: 'Date ne depassant pas 90 jours',
    obligatoire: false,
  },
  {
    code: 'JUSTIFICATIF_SIEGE',
    label: 'Justificatif siege',
    description: 'Contrat de bail ou contrat de domiciliation signe',
    obligatoire: false,
  },
  {
    code: 'STATUTS_VALIDES',
    label: 'Statuts valides',
    description: "Statuts generes et valides a l'etape 7",
    obligatoire: false,
  },
  {
    code: 'BLOCAGE_CAPITAL',
    label: 'Attestation de blocage de capital',
    description: 'Requis si capital > 100 000 MAD (apport en numeraire)',
    obligatoire: false,
  },
  {
    code: 'AUTORISATION_PREF',
    label: 'Autorisation prefectorale / sectorielle',
    description: 'Requis si activite reglementee',
    obligatoire: false,
  },
];

export function Step8PiecesJointes({
  existing,
  data,
  registeredPieces,
  saving,
  onSubmit,
  onPieceUploaded,
}: Props) {
  // Pre-remplit a partir du registre + ancien stockage local (compat).
  const uploadedFromData =
    (existing?.pieces as { uploaded?: string[] })?.uploaded ?? [];

  // BUG 2 (fix 2026-07-18) — Le Certificat Negatif est archive des l'etape 1
  // via IdentityExtractor (mode="cn") : il alimente `step1.cnArchivedDocumentId`
  // mais PAS le registre cross-step `registeredPieces['CN']`. On le reconnait
  // donc ici pour pre-remplir la case CN (badge « Deja deposee ») et l'inclure
  // dans `piecesUploaded` a la soumission, sans re-upload.
  const cnArchivedId = useMemo(() => {
    const step1 = (data?.step1 ?? {}) as Record<string, unknown>;
    const nested = (step1.denomination ?? {}) as Record<string, unknown>;
    const id = step1.cnArchivedDocumentId ?? nested.cnArchivedDocumentId;
    return typeof id === 'string' && id.trim() ? id : null;
  }, [data?.step1]);

  /**
   * Fix A8 (2026-08-16) — pieces DEJA deposees en amont, reconnues ici.
   *
   * Le justificatif de siege est joint a l'etape 2 (bail / attestation de
   * domiciliation) et les statuts sont generes puis valides a l'etape 7. Cette
   * etape les redemandait pourtant a vide, SANS badge « Deja deposee » : rien ne
   * distinguait une piece manquante d'une piece deja fournie, et l'employe la
   * redeposait « au cas ou ». On reconnait donc les deux, comme le CN l'etait deja.
   */
  const justificatifSiege = useMemo(() => {
    const step2 = (data?.step2 ?? {}) as Record<string, unknown>;
    const siege = ((step2.siege ?? step2) ?? {}) as Record<string, unknown>;
    const nom = siege.justificatifFileName ?? siege.justificatifNom ?? step2.justificatifFileName;
    const type = siege.justificatifType ?? step2.justificatifType;
    if (typeof nom === 'string' && nom.trim()) return nom.trim();
    // Pas de nom de fichier persiste, mais un type de justificatif choisi et
    // archive : on le signale quand meme comme fourni.
    if (typeof type === 'string' && type.trim()) {
      return type.trim() === 'BAIL' ? 'contrat-de-bail.pdf' : 'attestation-domiciliation.pdf';
    }
    return null;
  }, [data?.step2]);

  const statutsValides = useMemo(() => {
    const step7 = (data?.step7 ?? {}) as Record<string, unknown>;
    const docs = (step7.documents ?? step7.generated ?? {}) as Record<string, unknown>;
    for (const [code, v] of Object.entries(docs)) {
      if (!code.startsWith('STATUTS')) continue;
      const st = (v ?? {}) as Record<string, unknown>;
      if (st.validated === true || st.depositedToDataroom === true) {
        return (typeof st.filename === 'string' && st.filename) || 'statuts.docx';
      }
    }
    return typeof step7.statutsFileName === 'string' && step7.statutsFileName
      ? step7.statutsFileName
      : null;
  }, [data?.step7]);

  const initialPieces: Piece[] = useMemo(() => {
    return DEFAULT_PIECES.map((p) => {
      const reg = registeredPieces?.[p.code];
      if (reg) {
        return {
          ...p,
          fileName: reg.filename ?? `${p.code}.pdf`,
          fromRegistry: true,
        };
      }
      if (p.code === 'CN' && cnArchivedId) {
        return {
          ...p,
          fileName: `certificat-negatif-${cnArchivedId.slice(0, 8)}.pdf`,
          fromRegistry: true,
        };
      }
      // Fix A8 — deposees en amont : badge « Deja deposee », pas de re-upload.
      if (p.code === 'JUSTIFICATIF_SIEGE' && justificatifSiege) {
        return { ...p, fileName: justificatifSiege, fromRegistry: true };
      }
      if (p.code === 'STATUTS_VALIDES' && statutsValides) {
        return { ...p, fileName: statutsValides, fromRegistry: true };
      }
      if (uploadedFromData.includes(p.code)) {
        return { ...p, fileName: `${p.code}.pdf` };
      }
      return p;
    });
  }, [registeredPieces, uploadedFromData, cnArchivedId, justificatifSiege, statutsValides]);

  const [pieces, setPieces] = useState<Piece[]>(initialPieces);
  const [extraLabel, setExtraLabel] = useState('');

  // Construit la liste des personnes (dirigeants + associes) depuis le stepData
  // amont. Chaque CIN devient un bloc dedie pre-rempli depuis le registre.
  const cinPersonnes: CinPersonne[] = useMemo(() => {
    const out: CinPersonne[] = [];
    const dirs =
      ((data?.step5 as {
        dirigeants?: Array<{
          id?: string;
          nom?: string;
          prenom?: string;
          cinNumero?: string;
          cinFileName?: string;
          isAssociate?: boolean;
        }>;
      })?.dirigeants ?? []) as Array<{
        id?: string;
        nom?: string;
        prenom?: string;
        cinNumero?: string;
        cinFileName?: string;
        isAssociate?: boolean;
      }>;
    const ass =
      ((data?.step6 as {
        associes?: Array<{
          id?: string;
          nom?: string;
          prenom?: string;
          cin?: string;
          cinFileName?: string;
          fromDirigeantId?: string;
        }>;
      })?.associes ?? []) as Array<{
        id?: string;
        nom?: string;
        prenom?: string;
        cin?: string;
        cinFileName?: string;
        fromDirigeantId?: string;
      }>;

    const seenIds = new Set<string>();
    for (const d of dirs) {
      const id = d.id ?? `dir-${out.length}`;
      seenIds.add(id);
      const code = `CIN_DIRIGEANT_${id.slice(0, 8)}`;
      const reg = registeredPieces?.[code];
      out.push({
        code,
        label: `CIN — ${d.prenom ?? ''} ${d.nom ?? ''} (Dirigeant${
          d.isAssociate ? ' + Associe' : ''
        })`.trim(),
        role: 'DIRIGEANT',
        cinNumero: d.cinNumero,
        fileName: reg?.filename ?? d.cinFileName,
      });
    }
    for (const a of ass) {
      // Skip si deja represente comme dirigeant (propagation Step5->Step6).
      if (a.fromDirigeantId && seenIds.has(a.fromDirigeantId)) continue;
      const id = a.id ?? `ass-${out.length}`;
      const code = `CIN_ASSOCIE_${id.slice(0, 8)}`;
      const reg = registeredPieces?.[code];
      out.push({
        code,
        label: `CIN — ${a.prenom ?? ''} ${a.nom ?? ''} (Associe)`.trim(),
        role: 'ASSOCIE',
        cinNumero: a.cin,
        fileName: reg?.filename ?? a.cinFileName,
      });
    }
    return out;
  }, [data?.step5, data?.step6, registeredPieces]);

  function uploadFor(code: string, file: File, label: string) {
    setPieces((arr) =>
      arr.map((p) =>
        p.code === code ? { ...p, fileName: file.name, fromRegistry: true } : p,
      ),
    );
    void onPieceUploaded?.(code, label, file);
  }
  function removeFor(code: string) {
    setPieces((arr) =>
      arr.map((p) =>
        p.code === code ? { ...p, fileName: undefined, fromRegistry: false } : p,
      ),
    );
  }

  function addExtra() {
    if (!extraLabel.trim()) return;
    setPieces((arr) => [
      ...arr,
      {
        code: `EXTRA_${Date.now()}`,
        label: extraLabel.trim(),
        description: 'Document additionnel',
        obligatoire: false,
      },
    ]);
    setExtraLabel('');
  }

  const obligatoires = pieces.filter((p) => p.obligatoire);
  const optionnelles = pieces.filter((p) => !p.obligatoire);

  // Politique produit : AUCUNE piece n'est obligatoire. L'employe depose les
  // justificatifs quand il les a, sans jamais bloquer la progression de l'etape.
  const requiredOk = true;

  const totalUploadedBase = pieces.filter((p) => p.fileName).length;
  const totalUploadedCin = cinPersonnes.filter((p) => p.fileName).length;
  const totalUploaded = totalUploadedBase + totalUploadedCin;
  const totalCount = pieces.length + cinPersonnes.length;
  const progress = totalCount > 0 ? (totalUploaded / totalCount) * 100 : 0;

  function renderPiece(piece: Piece) {
    const done = !!piece.fileName;
    return (
      <div
        key={piece.code}
        className={`rounded-lg border-2 p-4 transition ${
          done ? 'border-success bg-success/10' : 'border-border bg-bg-raised'
        }`}
      >
        <div className="flex items-start gap-3">
          <div className="mt-0.5">
            {done ? (
              <CheckCircle className="h-5 w-5 text-success" />
            ) : (
              <div className="h-5 w-5 rounded-full border-2 border-border" />
            )}
          </div>
          <div className="min-w-0 flex-1">
            <div className="mb-0.5 flex flex-wrap items-center gap-2">
              <p className="text-sm font-semibold text-fg">{piece.label}</p>
              {piece.obligatoire && (
                <span className="rounded bg-danger/10 px-1.5 py-0.5 text-[10px] font-bold uppercase text-danger">
                  Obligatoire
                </span>
              )}
              {!piece.obligatoire && (
                <span className="rounded bg-fg-subtle/10 px-1.5 py-0.5 text-[10px] font-bold uppercase text-fg-subtle">
                  Optionnel
                </span>
              )}
              {piece.fromRegistry && done && (
                <span className="rounded bg-accent/10 px-1.5 py-0.5 text-[10px] font-semibold uppercase text-accent">
                  Deja deposee
                </span>
              )}
            </div>
            <p className="mb-2 text-xs text-fg-subtle">{piece.description}</p>

            {done ? (
              <div className="flex items-center gap-2 rounded border border-success/20 bg-bg-raised p-2">
                <FileText className="h-4 w-4 text-danger" />
                <span className="flex-1 truncate text-xs text-fg">
                  {piece.fileName}
                </span>
                <label className="cursor-pointer text-[11px] text-accent hover:underline">
                  Remplacer
                  <input
                    type="file"
                    accept="application/pdf,image/*"
                    className="hidden"
                    onChange={(e) =>
                      e.target.files?.[0] &&
                      uploadFor(piece.code, e.target.files[0], piece.label)
                    }
                  />
                </label>
                <button
                  type="button"
                  onClick={() => removeFor(piece.code)}
                  className="rounded p-1 text-danger hover:bg-danger/10"
                >
                  <X className="h-3 w-3" />
                </button>
              </div>
            ) : (
              <label className="flex w-full cursor-pointer items-center justify-center gap-2 rounded-lg border-2 border-dashed border-border px-3 py-2 text-xs text-accent transition hover:border-accent hover:bg-accent/10">
                <Upload className="h-3.5 w-3.5" /> Importer le document
                <input
                  type="file"
                  accept="application/pdf,image/*"
                  className="hidden"
                  onChange={(e) =>
                    e.target.files?.[0] &&
                    uploadFor(piece.code, e.target.files[0], piece.label)
                  }
                />
              </label>
            )}
          </div>
        </div>
      </div>
    );
  }

  function renderCinPersonne(p: CinPersonne) {
    const done = !!p.fileName;
    return (
      <div
        key={p.code}
        className={`flex items-start gap-3 rounded-lg border-2 p-3 ${
          done ? 'border-success bg-success/10' : 'border-danger/30 bg-danger/5'
        }`}
      >
        <IdCard
          className={`mt-0.5 h-5 w-5 flex-shrink-0 ${
            done ? 'text-success' : 'text-danger'
          }`}
        />
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <p className="text-sm font-semibold text-fg">{p.label}</p>
            <span
              className={`rounded px-1.5 py-0.5 text-[10px] font-bold uppercase ${
                p.role === 'DIRIGEANT'
                  ? 'bg-accent/10 text-accent'
                  : 'bg-warning/15 text-warning'
              }`}
            >
              {p.role}
            </span>
          </div>
          <p className="text-[11px] text-fg-subtle">
            {p.cinNumero ? `N° ${p.cinNumero}` : 'N° CIN non renseigne'}
          </p>
          {done ? (
            <div className="mt-2 flex items-center gap-2 rounded border border-success/20 bg-bg-raised p-2">
              <FileText className="h-3.5 w-3.5 text-danger" />
              <span className="flex-1 truncate text-xs text-fg">
                {p.fileName}
              </span>
              <label className="cursor-pointer text-[11px] text-accent hover:underline">
                Remplacer
                <input
                  type="file"
                  accept="application/pdf,image/*"
                  className="hidden"
                  onChange={(e) =>
                    e.target.files?.[0] &&
                    onPieceUploaded?.(p.code, p.label, e.target.files[0])
                  }
                />
              </label>
            </div>
          ) : (
            <label className="mt-2 flex w-full cursor-pointer items-center justify-center gap-2 rounded-lg border-2 border-dashed border-danger/40 px-3 py-2 text-xs text-danger transition hover:bg-danger/10">
              <Upload className="h-3.5 w-3.5" /> CIN obligatoire manquante —
              importer
              <input
                type="file"
                accept="application/pdf,image/*"
                className="hidden"
                onChange={(e) =>
                  e.target.files?.[0] &&
                  onPieceUploaded?.(p.code, p.label, e.target.files[0])
                }
              />
            </label>
          )}
        </div>
      </div>
    );
  }

  return (
    <form
      noValidate
      onSubmit={(e) => {
        e.preventDefault();
        const uploaded = pieces.filter((p) => p.fileName).map((p) => p.code);
        const cinUploaded = cinPersonnes
          .filter((p) => p.fileName)
          .map((p) => p.code);
        onSubmit({
          piecesUploaded: [...uploaded, ...cinUploaded],
          piecesDetails: pieces,
          cinPersonnes,
        });
      }}
      className="mx-auto max-w-[860px] space-y-6"
    >
      <div className="flex items-start gap-3 rounded-lg border border-accent/20 bg-accent/10 p-4">
        <Info className="h-5 w-5 flex-shrink-0 text-accent" />
        <div>
          <p className="text-sm font-semibold text-fg">
            Pieces jointes requises pour l'immatriculation
          </p>
          <p className="text-xs text-fg-subtle">
            Les documents deja uploades dans les etapes precedentes sont affiches
            ici dans leurs champs appropries (badge &laquo; Deja deposee
            &raquo;). Les CIN sont regroupees en blocs par personne. Chaque
            piece est automatiquement deposee dans la Data Room du dossier.
          </p>
        </div>
      </div>

      {/* Bloc CIN regroupees par personne */}
      {cinPersonnes.length > 0 && (
        <div className="rounded-xl border border-border bg-bg-raised p-6 shadow-sm">
          <h3 className="mb-1 flex items-center gap-2 text-base font-bold text-fg">
            <IdCard className="h-4 w-4 text-accent" /> CIN — {cinPersonnes.length}{' '}
            personne(s)
          </h3>
          <p className="mb-4 text-xs text-fg-subtle">
            Une CIN par dirigeant et par associe (les dirigeants-associes ne
            comptent qu'une fois). Pre-remplis depuis les etapes 5 et 6.
          </p>
          <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
            {cinPersonnes.map(renderCinPersonne)}
          </div>
        </div>
      )}

      {/*
        Fix A9 (2026-08-16) — la section « Pieces obligatoires » s'affichait TOUJOURS,
        titre rouge et promesse « Requises dans tous les cas » comprise, alors que la
        politique produit ne rend AUCUNE piece obligatoire (`requiredOk = true`) :
        elle etait donc systematiquement VIDE. On ne la rend plus que si elle a un
        contenu — un bloc vide ne fait qu'inquieter sur une exigence introuvable.
      */}
      {obligatoires.length > 0 && (
        <div className="rounded-xl border border-border bg-bg-raised p-6 shadow-sm">
          <h3 className="mb-1 flex items-center gap-2 text-base font-bold text-fg">
            <span className="h-2 w-2 rounded-full bg-danger" /> Pieces obligatoires
          </h3>
          <p className="mb-4 text-xs text-fg-subtle">
            Requises dans tous les cas pour finaliser la creation.
          </p>
          <div className="space-y-3">{obligatoires.map(renderPiece)}</div>
        </div>
      )}

      <div className="rounded-xl border border-border bg-bg-raised p-6 shadow-sm">
        <h3 className="mb-1 flex items-center gap-2 text-base font-bold text-fg">
          <span className="h-2 w-2 rounded-full bg-fg-subtle" /> Pieces
          optionnelles
        </h3>
        <p className="mb-4 text-xs text-fg-subtle">
          Selon la configuration du dossier (capital, activite, etc.)
        </p>
        <div className="space-y-3">{optionnelles.map(renderPiece)}</div>

        <div className="mt-4 flex items-center gap-2">
          <input
            type="text"
            value={extraLabel}
            onChange={(e) => setExtraLabel(e.target.value)}
            placeholder="Libelle d'une piece additionnelle"
            className="h-10 flex-1 rounded-lg border border-border bg-bg-overlay px-3 text-sm focus:border-accent focus:outline-none"
          />
          <button
            type="button"
            onClick={addExtra}
            disabled={!extraLabel.trim()}
            className={`flex h-10 items-center gap-2 rounded-lg px-4 text-sm font-medium transition ${
              extraLabel.trim()
                ? 'bg-accent text-bg-raised hover:bg-accent-hover'
                : 'cursor-not-allowed bg-border text-fg-subtle'
            }`}
          >
            <Plus className="h-4 w-4" /> Ajouter piece
          </button>
        </div>
      </div>

      <div className="rounded-xl border border-border bg-bg-overlay p-5">
        <div className="mb-2 flex items-center justify-between">
          <span className="text-sm font-bold text-fg">
            Progression des pieces jointes
          </span>
          <span className="text-sm font-bold text-accent">
            {totalUploaded} / {totalCount} importees
          </span>
        </div>
        <div className="h-2 w-full overflow-hidden rounded-full bg-border">
          <div
            className="h-full rounded-full bg-success transition-all"
            style={{ width: `${progress}%` }}
          />
        </div>
      </div>

      {!requiredOk && (
        <div className="flex items-start gap-3 rounded-lg border-l-4 border-danger bg-danger/10 p-4">
          <AlertTriangle className="mt-0.5 h-5 w-5 flex-shrink-0 text-danger" />
          <div>
            <p className="text-sm font-semibold text-danger">
              Documents obligatoires manquants
            </p>
            <p className="text-xs text-danger">
              {[
                ...REQUIRED_BASE_CODES.filter(
                  (c) => !pieces.find((p) => p.code === c && p.fileName),
                ).map(
                  (c) => DEFAULT_PIECES.find((p) => p.code === c)?.label ?? c,
                ),
                ...cinPersonnes
                  .filter((p) => !p.fileName)
                  .map((p) => p.label),
              ].join(', ')}
            </p>
          </div>
        </div>
      )}

      <div className="flex items-center justify-end pt-2">
        <button
          type="submit"
          disabled={!requiredOk || saving}
          className={`flex items-center gap-2 rounded-lg px-8 h-12 font-medium transition ${
            requiredOk && !saving
              ? 'bg-accent text-bg-raised hover:bg-accent-hover'
              : 'cursor-not-allowed bg-border text-fg-subtle'
          }`}
        >
          {saving ? 'Enregistrement…' : 'Valider et continuer'}
          <ChevronRight className="h-4 w-4" />
        </button>
      </div>
    </form>
  );
}
