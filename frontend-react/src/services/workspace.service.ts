import { api } from '../lib/api';

/**
 * Simplification inscription (2026-07-13) — informations legales du cabinet
 * (workspace courant), lisibles / modifiables apres inscription.
 *
 * Sert a completer l'ICE laisse optionnel au signup (il devient necessaire
 * pour les dossiers) et alimente le bandeau de rappel "ICE manquant".
 */
export interface WorkspaceProfile {
  name: string;
  professionalType: string | null;
  ice: string | null;
  city: string | null;
  contactEmail: string | null;
  /** true tant que l'ICE du workspace est vide (pilote le bandeau de rappel). */
  iceMissing: boolean;
  /**
   * En-tete PDF (2026-07-14) — nom affiche personnalise (brut, nullable) et
   * valeur effective (nom affiche -> sinon denomination) affichee en en-tete
   * des documents generes.
   */
  nomAfficheDocuments: string | null;
  documentDisplayName: string;
  // Papier a en-tete V31 — coordonnees + mentions legales du cabinet.
  adresse: string | null;
  telephone: string | null;
  siteWeb: string | null;
  rcNumber: string | null;
  ifFiscal: string | null;
  /** true si un logo est stocke (pilote l'apercu + le bouton supprimer). */
  hasLogo: boolean;
}

export const workspaceService = {
  async getProfile(): Promise<WorkspaceProfile> {
    const { data } = await api.get<WorkspaceProfile>('/workspace/profile');
    return data;
  },

  /** SUPERVISEUR only (le backend enforce le RBAC). Champs omis = inchanges. */
  async updateProfile(payload: {
    ice?: string;
    city?: string;
    nomAfficheDocuments?: string;
    adresse?: string;
    telephone?: string;
    siteWeb?: string;
    rcNumber?: string;
    ifFiscal?: string;
  }): Promise<WorkspaceProfile> {
    const { data } = await api.patch<WorkspaceProfile>('/workspace/profile', payload);
    return data;
  },

  /**
   * Papier a en-tete V31 — logo du cabinet.
   * getLogoObjectUrl : recupere le logo (blob) et renvoie une object-URL pour
   * l'apercu, ou null si aucun logo (404). L'appelant doit revoke l'URL.
   */
  async getLogoObjectUrl(): Promise<string | null> {
    try {
      const res = await api.get('/workspace/profile/logo', { responseType: 'blob' });
      return URL.createObjectURL(res.data as Blob);
    } catch (err) {
      if ((err as { response?: { status?: number } })?.response?.status === 404) {
        return null;
      }
      throw err;
    }
  },

  /** SUPERVISEUR only. PNG/JPG, ~1 Mo max (validation backend). */
  async uploadLogo(file: File): Promise<void> {
    const form = new FormData();
    form.append('file', file);
    await api.post('/workspace/profile/logo', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },

  /** SUPERVISEUR only. */
  async deleteLogo(): Promise<void> {
    await api.delete('/workspace/profile/logo');
  },
};
