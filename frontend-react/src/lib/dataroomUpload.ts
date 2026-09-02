import { dataroomService } from '../services/dataroom.service';
import type { DocumentType } from '../types/dataroom';

/**
 * Sprint 2026-06-24 — Source UNIQUE de vérité du versioning Data Room juridique.
 *
 * <p>Avant ce helper, deux chemins divergeaient :
 * <ul>
 *   <li>l'upload SIMPLE ({@code UploadDocumentDialog}) gérait correctement le
 *       choix « Nouveau document » vs « Nouvelle version de … » avec motif ;</li>
 *   <li>l'upload MULTIPLE traitait TOUS les fichiers comme de nouveaux documents
 *       (ancienne logique {@code uploadJuridiqueBatch}), sans versioning ni
 *       motif.</li>
 * </ul>
 *
 * <p>{@link uploadOrReplace} centralise la décision et réutilise EXACTEMENT les
 * mêmes endpoints backend que l'upload simple :
 * <ul>
 *   <li>{@code mode: 'new'} → {@code dataroomService.uploadJuridique} →
 *       POST /dataroom/dossiers/&#123;id&#125;/juridique/upload (nouveau Document logique).</li>
 *   <li>{@code mode: 'version'} → {@code dataroomService.replaceAsNewVersion} →
 *       POST /dataroom/documents/&#123;id&#125;/versions (l'ancienne version bascule
 *       en historique avec motif, la nouvelle devient ACTIVE).</li>
 * </ul>
 *
 * Aucune logique de versioning n'est donc dupliquée : tout caller (dialog simple
 * ET drawer multiple) passe par ici.
 */

export type UploadMode = 'new' | 'version';

/** Longueur minimale du motif de remplacement (traçabilité). */
export const MOTIF_MIN_LENGTH = 5;

export interface UploadOrReplaceOptions {
  /** Dossier cible (requis en mode 'new'). */
  dossierId: string;
  /** 'new' = nouveau Document logique ; 'version' = nouvelle version d'un Document existant. */
  mode: UploadMode;
  // --- mode 'new' ---
  /** Type de document (défaut "AUTRE"). */
  documentType?: DocumentType | string;
  /** Titre ; si vide, dérivé du nom de fichier (sans extension). */
  title?: string;
  /** Ticket lié optionnel. */
  ticketId?: string;
  // --- mode 'version' ---
  /** Id du Document logique à remplacer (requis en mode 'version'). */
  targetDocId?: string;
  /** Motif du remplacement (requis, >= {@link MOTIF_MIN_LENGTH} caractères). */
  motif?: string;
}

/** Vrai si le motif est valide (>= {@link MOTIF_MIN_LENGTH} caractères une fois trimmé). */
export function isMotifValid(motif?: string): boolean {
  return (motif ?? '').trim().length >= MOTIF_MIN_LENGTH;
}

/** Retire l'extension d'un nom de fichier ("statuts.pdf" -> "statuts"). */
export function stripExtension(name: string): string {
  const dot = name.lastIndexOf('.');
  return dot > 0 ? name.substring(0, dot) : name;
}

/**
 * Dépose un fichier dans le dossier juridique, soit comme NOUVEAU document,
 * soit comme NOUVELLE VERSION d'un document existant.
 *
 * @throws Error si le mode 'version' n'a pas de cible ou un motif trop court.
 */
export async function uploadOrReplace(
  file: File,
  opts: UploadOrReplaceOptions,
): Promise<void> {
  if (opts.mode === 'version') {
    if (!opts.targetDocId) {
      throw new Error('Document cible requis pour créer une nouvelle version.');
    }
    if (!isMotifValid(opts.motif)) {
      throw new Error(
        `Le motif du remplacement doit faire au moins ${MOTIF_MIN_LENGTH} caractères.`,
      );
    }
    await dataroomService.replaceAsNewVersion(
      opts.targetDocId,
      file,
      (opts.motif ?? '').trim(),
    );
    return;
  }

  // mode 'new'
  const title = (opts.title ?? '').trim() || stripExtension(file.name);
  const params: {
    file: File;
    documentType: DocumentType | string;
    title: string;
    ticketId?: string;
  } = {
    file,
    documentType: opts.documentType ?? 'AUTRE',
    title,
  };
  const ticketId = opts.ticketId?.trim();
  if (ticketId) params.ticketId = ticketId;
  await dataroomService.uploadJuridique(opts.dossierId, params);
}
