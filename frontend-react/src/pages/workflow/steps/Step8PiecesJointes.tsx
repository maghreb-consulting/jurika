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
import { InfoBulle, TexteAide } from '../../../components/ui/Aide';
import { useStepAutosave, type RegisterDirty } from '../useStepAutosave';

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
  /**
   * Lot L7 (E12) : enregistrement automatique de la saisie de l'etape (choix des
   * pieces, pieces complementaires, pieces retirees, libelle en cours). Les fichiers
   * eux-memes partent a l'import (registre et Data Room) et ne sont jamais renvoyes.
   */
  registerDirty?: RegisterDirty;
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
    label: 'Certificat négatif',
    description: 'Délivré depuis moins de 90 jours',
    obligatoire: false,
  },
  {
    code: 'JUSTIFICATIF_SIEGE',
    label: 'Justificatif du siège',
    description: 'Contrat de bail ou contrat de domiciliation signé',
    obligatoire: false,
  },
  {
    code: 'STATUTS_VALIDES',
    label: 'Statuts validés',
    description: 'Statuts générés et validés à l’étape 7',
    obligatoire: false,
  },
  {
    code: 'BLOCAGE_CAPITAL',
    label: 'Attestation de blocage du capital',
    description: 'Requise si le capital dépasse 100 000 MAD (apport en numéraire)',
    obligatoire: false,
  },
  {
    code: 'AUTORISATION_PREF',
    label: 'Autorisation préfectorale ou sectorielle',
    description: 'Requise si l’activité est réglementée',
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
  registerDirty,
}: Props) {
  // Pre-remplit a partir du registre + ancien stockage local (compat).
  // L6 : memoise (la liste servait de dependance a un useMemo et changeait a chaque rendu).
  const uploadedFromData = useMemo(
    () => (existing?.pieces as { uploaded?: string[] })?.uploaded ?? [],
    [existing?.pieces],
  );

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

  // Lot L7 (E12) : saisie de l'etape relue (brouillon enregistre ou etape validee).
  const detailsBruts = existing?.piecesDetails;
  const retireesBrutes = existing?.piecesRetirees;
  const detailsSaisis = useMemo(
    () => (Array.isArray(detailsBruts) ? (detailsBruts as Piece[]) : []),
    [detailsBruts],
  );
  const retireesSaisies = useMemo(
    () => (Array.isArray(retireesBrutes)
      ? (retireesBrutes as unknown[]).filter((c): c is string => typeof c === 'string')
      : []),
    [retireesBrutes],
  );

  const initialPieces: Piece[] = useMemo(() => {
    const reprises = DEFAULT_PIECES.map((p): Piece => {
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
          fileName: 'Certificat négatif archivé à l’étape 1',
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
      // Lot L7 (E12) : nom du fichier importe a cette etape, tel que saisi.
      const saisie = detailsSaisis.find((d) => d.code === p.code);
      if (saisie?.fileName) {
        return { ...p, fileName: saisie.fileName, ...(saisie.fromRegistry ? { fromRegistry: true } : {}) };
      }
      if (uploadedFromData.includes(p.code)) {
        return { ...p, fileName: `${p.code}.pdf` };
      }
      return p;
    });
    // Lot L7 (E12) : pieces complementaires ajoutees a cette etape (libelle et fichier).
    const complementaires = detailsSaisis
      .filter((d) => typeof d?.code === 'string' && d.code.startsWith('EXTRA_') && typeof d.label === 'string')
      .map((d): Piece => ({
        code: d.code,
        label: d.label,
        description: typeof d.description === 'string' ? d.description : 'Document complémentaire',
        obligatoire: false,
        ...(d.fileName ? { fileName: d.fileName } : {}),
        ...(d.fromRegistry ? { fromRegistry: true } : {}),
      }));
    // Lot L7 (E12) : une piece retiree a cette etape le reste a la relecture (le
    // fichier depose en Data Room n'est pas supprime pour autant).
    return [...reprises, ...complementaires].map((p) =>
      retireesSaisies.includes(p.code) ? { ...p, fileName: undefined, fromRegistry: false } : p,
    );
  }, [registeredPieces, uploadedFromData, cnArchivedId, justificatifSiege, statutsValides,
    detailsSaisis, retireesSaisies]);

  const [pieces, setPieces] = useState<Piece[]>(initialPieces);
  const [retirees, setRetirees] = useState<string[]>(retireesSaisies);
  const [extraLabel, setExtraLabel] = useState(
    typeof existing?.libellePieceComplementaire === 'string' ? existing.libellePieceComplementaire : '',
  );

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
        label: `CIN — ${d.prenom ?? ''} ${d.nom ?? ''} (dirigeant${
          d.isAssociate ? ' et associé' : ''
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
        label: `CIN — ${a.prenom ?? ''} ${a.nom ?? ''} (associé)`.trim(),
        role: 'ASSOCIE',
        cinNumero: a.cin,
        fileName: reg?.filename ?? a.cinFileName,
      });
    }
    return out;
  }, [data?.step5, data?.step6, registeredPieces]);

  function uploadFor(code: string, file: File, label: string) {
    setRetirees((r) => r.filter((c) => c !== code));
    setPieces((arr) =>
      arr.map((p) =>
        p.code === code ? { ...p, fileName: file.name, fromRegistry: true } : p,
      ),
    );
    void onPieceUploaded?.(code, label, file);
  }
  function removeFor(code: string) {
    setRetirees((r) => (r.includes(code) ? r : [...r, code]));
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
        description: 'Document complémentaire',
        obligatoire: false,
      },
    ]);
    setExtraLabel('');
  }

  /**
   * Saisie de l'etape, dans la forme que le serveur range sous `step8` a la validation
   * (CreationSarlWorkflow.handlePiecesJointes : `piecesUploaded`, `pieces.uploaded`...)
   * et que cette etape relit. Les fichiers n'y figurent pas : seulement leur nom.
   */
  function saisieEtape(): Record<string, unknown> {
    const uploaded = pieces.filter((p) => p.fileName).map((p) => p.code);
    const cinUploaded = cinPersonnes.filter((p) => p.fileName).map((p) => p.code);
    const piecesUploaded = [...uploaded, ...cinUploaded];
    return {
      piecesUploaded,
      piecesDetails: pieces,
      cinPersonnes,
      pieces: { uploaded: piecesUploaded },
      piecesRetirees: retirees,
      libellePieceComplementaire: extraLabel,
    };
  }
  useStepAutosave(8, saisieEtape, registerDirty);

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
                  Facultative
                </span>
              )}
              {piece.fromRegistry && done && (
                <span className="rounded bg-accent/10 px-1.5 py-0.5 text-[10px] font-semibold uppercase text-accent">
                  Déjà déposée
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
                  aria-label={`Retirer : ${piece.label}`}
                  title="Retirer la pièce"
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
              {p.role === 'DIRIGEANT' ? 'Dirigeant' : 'Associé'}
            </span>
          </div>
          <p className="text-[11px] text-fg-subtle">
            {p.cinNumero ? `N° ${p.cinNumero}` : 'N° de CIN non renseigné'}
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
              <Upload className="h-3.5 w-3.5" /> CIN non encore importée : importer
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
        onSubmit(saisieEtape());
      }}
      className="mx-auto max-w-[860px] space-y-6"
    >
      <div className="flex items-start gap-3 rounded-lg border border-accent/20 bg-accent/10 p-4">
        <Info className="h-5 w-5 flex-shrink-0 text-accent" />
        <div>
          <h3 className="text-sm font-semibold text-fg">
            Pièces jointes pour l’immatriculation
          </h3>
          <p className="text-xs text-fg-subtle">
            Les documents déjà importés aux étapes précédentes sont repris ici
            (mention « Déjà déposée »). Les CIN sont regroupées par personne. Chaque
            pièce est automatiquement déposée dans la Data Room du dossier.
          </p>
        </div>
      </div>
      <TexteAide cle="creation-etape-8" titre="Ce qu’il faut faire">
        Importez les pièces que vous avez déjà ; aucune ne bloque l’étape, vous pourrez
        compléter plus tard depuis la Data Room. Ajoutez au besoin une pièce
        complémentaire par son libellé, puis cliquez sur « Valider et continuer » :
        l’étape suivante présente la synthèse du dossier. Vos choix (pièces importées,
        retirées ou ajoutées, libellé en cours) sont enregistrés automatiquement ; un
        fichier importé part tout de suite dans la Data Room et n’est jamais renvoyé.
      </TexteAide>

      {/* Bloc CIN regroupees par personne */}
      {cinPersonnes.length > 0 && (
        <div className="rounded-xl border border-border bg-bg-raised p-6 shadow-sm">
          <h3 className="mb-1 flex items-center gap-2 text-base font-bold text-fg">
            <IdCard className="h-4 w-4 text-accent" /> CIN — {cinPersonnes.length}{' '}
            personne(s)
          </h3>
          <p className="mb-4 text-xs text-fg-subtle">
            Une CIN par dirigeant et par associé (un dirigeant également associé ne
            compte qu’une fois). Reprises des étapes 5 et 6.
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
            <span className="h-2 w-2 rounded-full bg-danger" /> Pièces obligatoires
          </h3>
          <p className="mb-4 text-xs text-fg-subtle">
            Requises dans tous les cas pour finaliser la création.
          </p>
          <div className="space-y-3">{obligatoires.map(renderPiece)}</div>
        </div>
      )}

      <div className="rounded-xl border border-border bg-bg-raised p-6 shadow-sm">
        <h3 className="mb-1 flex items-center gap-2 text-base font-bold text-fg">
          <span className="h-2 w-2 rounded-full bg-fg-subtle" /> Pièces
          facultatives
        </h3>
        <p className="mb-4 text-xs text-fg-subtle">
          Selon le dossier (capital, activité, etc.).
        </p>
        <div className="space-y-3">{optionnelles.map(renderPiece)}</div>

        <div className="mt-4 flex items-center gap-2">
          <input
            type="text"
            aria-label="Libellé d’une pièce complémentaire"
            value={extraLabel}
            onChange={(e) => setExtraLabel(e.target.value)}
            placeholder="Libellé d’une pièce complémentaire"
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
            <Plus className="h-4 w-4" /> Ajouter la pièce
          </button>
          <InfoBulle
            libelle="Ajouter une pièce complémentaire"
            texte="Saisissez le libellé de la pièce (par exemple « Attestation bancaire »), cliquez sur « Ajouter la pièce », puis importez le fichier dans la ligne qui apparaît."
          />
        </div>
      </div>

      <div className="rounded-xl border border-border bg-bg-overlay p-5">
        <div className="mb-2 flex items-center justify-between">
          <span className="text-sm font-bold text-fg">
            Progression des pièces jointes
          </span>
          <span className="text-sm font-bold text-accent">
            {totalUploaded} / {totalCount} importées
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
