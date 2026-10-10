import { api } from '../lib/api';
import type {
  AccessLogPage,
  ClientPermissions,
  DataroomSettings,
  DemandeDirection,
  DemandeStatut,
  DemandeSummary,
  DemandeSupervisionRow,
  RequeteSummary,
  RequeteStatut,
  CreateRequetePayload,
  RepondreRequetePayload,
  ComplementRequetePayload,
  DepotSummary,
  DocumentSummary,
  DocumentType,
  DossierBrief,
  DossierJuridiqueView,
  UpdateIdentifiantsPayload,
  ReaffectationVue,
  VersionTaxeProfessionnelle,
  DroitsDossier,
  SearchJuridiqueParams,
  SearchJuridiqueResult,
  SeanceEdition,
  HistoriqueAccesClient,
  PermissionsClientPatch,
} from '../types/dataroom';

export interface UploadJuridiqueParams {
  file: File;
  documentType: DocumentType | string;
  title: string;
  ticketId?: string;
  /**
   * Lot B — le client verra-t-il ce document ? Réglé AU DÉPÔT, et modifiable
   * ensuite depuis la Data Room : c'est la même colonne des deux côtés.
   *
   * Omis, la valeur par défaut du serveur s'applique : visible, sauf pour le
   * type « AUTRE », le seul où une note interne peut atterrir.
   */
  visibleClient?: boolean;
}

export interface CreateDemandePayload {
  sujet: string;
  description?: string;
  dossierId: string;
}

export interface UpdateDemandePayload {
  statut?: DemandeStatut;
  noteInterne?: string;
  ticketId?: string;
}

function triggerDownload(blob: Blob, filename: string) {
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(url);
}

export const dataroomService = {
  // ---- Dossiers ----
  async listDossiers(): Promise<DossierBrief[]> {
    const { data } = await api.get<DossierBrief[]>('/dataroom/dossiers');
    return data;
  },

  // ---- Dossier juridique ----
  /**
   * Sprint 7 / TASK 2 -- filtres optionnels timeline (types/from/to)
   * passes au backend qui filtre cote SQL via Specification.
   */
  async getJuridique(
    dossierId: string,
    timelineFilters?: { types?: string[]; from?: string; to?: string },
  ): Promise<DossierJuridiqueView> {
    const params: Record<string, unknown> = {};
    if (timelineFilters?.types && timelineFilters.types.length > 0) {
      params.types = timelineFilters.types.join(',');
    }
    if (timelineFilters?.from) params.from = timelineFilters.from;
    if (timelineFilters?.to) params.to = timelineFilters.to;
    const { data } = await api.get<DossierJuridiqueView>(
      `/dataroom/dossiers/${dossierId}/juridique`,
      Object.keys(params).length > 0 ? { params } : undefined,
    );
    return data;
  },

  /**
   * Sprint 7 / TASK 1 -- Recherche FTS + filtres avances juridique.
   * Voir RG-DR-FTS : endpoint backend `/juridique/search` qui combine
   * websearch_to_tsquery('french', q) + filtres types/dates/scope versions.
   */
  async searchJuridique(
    dossierId: string,
    params: SearchJuridiqueParams,
  ): Promise<SearchJuridiqueResult> {
    const query: Record<string, unknown> = {};
    if (params.q && params.q.trim()) query.q = params.q.trim();
    if (params.types && params.types.length > 0) query.types = params.types.join(',');
    if (params.from) query.from = params.from;
    if (params.to) query.to = params.to;
    if (params.versionScope) query.versionScope = params.versionScope;
    query.limit = params.limit ?? 20;
    query.offset = params.offset ?? 0;
    const { data } = await api.get<SearchJuridiqueResult>(
      `/dataroom/dossiers/${dossierId}/juridique/search`,
      { params: query },
    );
    return data;
  },

  /**
   * Depose un document juridique. Renvoie le document cree : le cochage d une
   * demarche a besoin de son identifiant pour le rattacher.
   */
  async uploadJuridique(
    dossierId: string,
    params: UploadJuridiqueParams,
  ): Promise<DocumentSummary> {
    const form = new FormData();
    form.append('file', params.file);
    form.append('documentType', String(params.documentType));
    form.append('title', params.title);
    if (params.ticketId) {
      form.append('ticketId', params.ticketId);
    }
    if (params.visibleClient !== undefined) {
      form.append('visibleClient', String(params.visibleClient));
    }
    // L'instance Axios a Content-Type=application/json par defaut.
    // On force multipart/form-data ici (Axios v1+ ajoute le boundary tout seul).
    const { data } = await api.post<DocumentSummary>(
      `/dataroom/dossiers/${dossierId}/juridique/upload`,
      form,
      { headers: { 'Content-Type': 'multipart/form-data' } },
    );
    return data;
  },

  /**
   * @deprecated Sprint 2026-06-24 — Plus utilisé par l'UI. L'upload multiple
   * passe désormais par {@link MultiUploadDrawer} → `lib/dataroomUpload.uploadOrReplace`
   * (choix par fichier : nouveau document OU nouvelle version + motif). Conservé
   * pour compat éventuelle ; ne PAS rebrancher sur ce chemin sans versioning.
   *
   * Upload multiple fichiers vers le dossier juridique.
   * Chaque fichier devient un DOCUMENT DISTINCT (pas une version) -- le titre
   * derive du nom du fichier pour rester reconnaissable dans la liste.
   */
  async uploadJuridiqueBatch(
    dossierId: string,
    files: File[],
    params: { documentType: DocumentType | string; title: string; ticketId?: string },
  ): Promise<void> {
    if (files.length === 0) return;
    const errors: string[] = [];
    for (let i = 0; i < files.length; i++) {
      const f = files[i];
      // Titre = nom du fichier sans extension (chaque fichier conserve son identite)
      const cleanName = f.name.replace(/\.[^.]+$/, '') || f.name;
      try {
        await this.uploadJuridique(dossierId, {
          file: f,
          documentType: params.documentType,
          title: cleanName,
          ticketId: params.ticketId,
        });
      } catch (err) {
        const msg = err instanceof Error ? err.message : String(err);
        errors.push(`${f.name} : ${msg}`);
      }
    }
    if (errors.length === files.length) {
      throw new Error(`Tous les uploads ont echoue :\n${errors.join('\n')}`);
    }
    if (errors.length > 0) {
      throw new Error(
        `${files.length - errors.length}/${files.length} uploades. Echecs :\n${errors.join('\n')}`,
      );
    }
  },

  /**
   * Auto-deposit (Killer Feature Bug 4) : depose un Blob genere par IA dans le
   * data room juridique. Utilise par les workflows pour pousser automatiquement
   * les PV/statuts/annonces generes.
   *
   * Sprint 2026-06-23 — versioning :
   * - `replacePrevious=true` + `existingDocumentId` => appelle
   *   {@link replaceAsNewVersion} : l'ancienne version bascule en historique,
   *   la nouvelle devient ACTIVE dans le même slot (dossier+type+titre).
   * - sinon => upload classique (nouveau Document logique).
   * - `motif` est persisté sur l'ancienne version (raison du remplacement).
   */
  async depositGeneratedDoc(
    dossierId: string,
    blob: Blob,
    params: {
      documentType: DocumentType | string;
      title: string;
      filename: string;
      ticketId?: string;
      motif?: string;
      replacePrevious?: boolean;
      existingDocumentId?: string;
    },
  ): Promise<void> {
    const file = new File([blob], params.filename, {
      type: blob.type || 'application/octet-stream',
    });
    if (params.replacePrevious && params.existingDocumentId) {
      await this.replaceAsNewVersion(params.existingDocumentId, file, params.motif);
      return;
    }
    await this.uploadJuridique(dossierId, {
      file,
      documentType: params.documentType,
      title: params.title,
      ticketId: params.ticketId,
    });
  },

  // ===========================================================================
  // Versioning explicite (Sprint 2026-06-23) — endpoints :
  //   POST  /dataroom/documents/{id}/versions          replaceAsNewVersion
  //   GET   /dataroom/documents/{id}/versions          listVersions
  //   POST  /dataroom/documents/{id}/versions/{vid}/restore   restoreVersion
  //   GET   /dataroom/documents/{id}/versions/{vid}/download  downloadVersion
  // ===========================================================================

  /**
   * Retourne le lignage complet d'un Document logique (active + historiques),
   * trié de la version la plus récente à la plus ancienne. Peut être appelé
   * avec n'importe quelle version du slot (l'API remonte le slot).
   */
  async listVersions(documentId: string): Promise<DocumentSummary[]> {
    const { data } = await api.get<DocumentSummary[]>(
      `/dataroom/documents/${documentId}/versions`,
    );
    return data;
  },

  /**
   * Remplace le Document ciblé par une nouvelle version : l'ancienne bascule
   * en historique (is_current=false + motif persisté), la nouvelle devient
   * ACTIVE dans le même slot.
   */
  async replaceAsNewVersion(
    documentId: string,
    file: File,
    motif?: string,
  ): Promise<DocumentSummary> {
    const form = new FormData();
    form.append('file', file);
    if (motif && motif.trim().length > 0) {
      form.append('motif', motif.trim());
    }
    const { data } = await api.post<DocumentSummary>(
      `/dataroom/documents/${documentId}/versions`,
      form,
      { headers: { 'Content-Type': 'multipart/form-data' } },
    );
    return data;
  },

  /**
   * Restaure une ancienne version : la version actuelle bascule en historique
   * (avec le motif fourni), la version ciblée redevient ACTIVE. Idempotent si
   * la version ciblée est déjà active.
   */
  async restoreVersion(
    documentId: string,
    versionId: string,
    motif?: string,
  ): Promise<DocumentSummary> {
    const { data } = await api.post<DocumentSummary>(
      `/dataroom/documents/${documentId}/versions/${versionId}/restore`,
      { motif: motif && motif.trim().length > 0 ? motif.trim() : null },
    );
    return data;
  },

  /**
   * Télécharge une version spécifique (active ou historique) d'un Document
   * logique. Déclenche le téléchargement navigateur via {@link triggerDownload}.
   */
  async downloadVersion(
    documentId: string,
    versionId: string,
    filename: string,
  ): Promise<void> {
    const response = await api.get(
      `/dataroom/documents/${documentId}/versions/${versionId}/download`,
      { responseType: 'blob' },
    );
    triggerDownload(response.data as Blob, filename);
  },

  async downloadDocument(documentId: string, filename: string): Promise<void> {
    const response = await api.get(`/dataroom/documents/${documentId}/download`, {
      responseType: 'blob',
    });
    triggerDownload(response.data as Blob, filename);
  },

  /**
   * Étape 7 (persistance 2026-08) — récupère le Blob binaire d'un document
   * juridique SANS déclencher de téléchargement navigateur. Sert à ré-hydrater
   * l'aperçu / l'édition d'un document déjà déposé en Data Room après une
   * navigation (le blob n'est jamais persisté côté brouillon React).
   */
  /**
   * Lot 3 (2026-09-07) — ÉDITION BUREAUTIQUE FIDÈLE (Collabora / WOPI).
   *
   * Ouvre une séance : le serveur rend l'URL de l'éditeur, le `WOPISrc` que
   * COLLABORA appellera (jamais le navigateur) et un jeton d'accès à durée de
   * vie courte, propre à ce document et à cet utilisateur. Le jeton n'est rendu
   * qu'une fois — il n'est stocké côté serveur que sous forme d'empreinte.
   */
  async ouvrirSeanceEdition(documentId: string): Promise<SeanceEdition> {
    const { data } = await api.post<SeanceEdition>(
      `/dataroom/documents/${documentId}/edition-session`,
    );
    return data;
  },

  /**
   * Ferme la séance. La lecture est coupée aussitôt côté serveur ; l'écriture
   * reste acceptée le temps d'une fenêtre de grâce, parce que Collabora
   * enregistre de façon asynchrone et appelle souvent PutFile APRÈS la
   * fermeture de l'onglet.
   */
  async fermerSeanceEdition(sessionId: string): Promise<void> {
    await api.delete(`/dataroom/edition-session/${sessionId}`);
  },

  /**
   * Lot 2 (2026-09-07) — catalogue des types acceptes par la Data Room.
   * Source unique du menu de depot, alignee sur la contrainte de la table.
   */
  async listDocumentTypes(): Promise<
    { code: string; libelle: string; groupe: string | null }[]
  > {
    const { data } = await api.get<{ code: string; libelle: string; groupe: string | null }[]>(
      '/dataroom/document-types',
    );
    return data;
  },

  /**
   * Lot 2 (2026-09-07) — BROUILLONS DE GENERATION.
   *
   * Un acte genere par un workflow est persiste des sa generation, avant toute
   * validation : c'est ce qui fait qu'il survit a un rechargement de page et a
   * une navigation entre etapes. Un brouillon n'apparait dans aucune vue du
   * dossier juridique tant qu'il n'est pas valide.
   *
   * Regenerer ou re-editer REMPLACE le brouillon du meme emplacement.
   */
  async saveBrouillon(
    dossierId: string,
    params: { file: File; documentType: DocumentType | string; title: string; ticketId: string },
  ): Promise<DocumentSummary> {
    const form = new FormData();
    form.append('file', params.file);
    form.append('documentType', String(params.documentType));
    form.append('title', params.title);
    form.append('ticketId', params.ticketId);
    const { data } = await api.post<DocumentSummary>(
      `/dataroom/dossiers/${dossierId}/juridique/brouillons`,
      form,
      { headers: { 'Content-Type': 'multipart/form-data' } },
    );
    return data;
  },

  /** Brouillons d'une operation — ré-hydratation de l'etape de generation. */
  async listBrouillons(ticketId: string): Promise<DocumentSummary[]> {
    const { data } = await api.get<DocumentSummary[]>(
      `/dataroom/tickets/${ticketId}/juridique/brouillons`,
    );
    return data;
  },

  /** Valide un brouillon : il devient l'acte en vigueur de son emplacement. */
  async validerBrouillon(documentId: string, motif?: string): Promise<DocumentSummary> {
    const { data } = await api.post<DocumentSummary>(
      `/dataroom/documents/${documentId}/valider-brouillon`,
      { motif: motif ?? null },
    );
    return data;
  },

  async fetchDocumentBlob(documentId: string): Promise<Blob> {
    const response = await api.get(`/dataroom/documents/${documentId}/download`, {
      responseType: 'blob',
    });
    return response.data as Blob;
  },

  /**
   * Sprint 7 / TASK 3 -- Apercu PDF inline. Telecharge le Blob via axios
   * (donc avec auth JWT) puis retourne une object URL utilisable dans un
   * <iframe>. Le caller DOIT appeler URL.revokeObjectURL(url) quand
   * la modal se ferme pour eviter une fuite memoire.
   *
   * Le backend renvoie Content-Disposition: inline pour signaler au
   * navigateur de RENDU plutot que telechargement (cf.
   * PreviewDocumentUseCase). RG-DR03 : 400 + message si SUSPENDED+CLIENT.
   */
  async previewDocument(
    documentId: string,
  ): Promise<{ url: string; contentType: string }> {
    const response = await api.get<Blob>(
      `/dataroom/documents/${documentId}/preview`,
      { responseType: 'blob' },
    );
    const blob = response.data as Blob;
    const ctHeader = response.headers['content-type'];
    const contentType =
      (typeof ctHeader === 'string' ? ctHeader : undefined) ??
      blob.type ??
      'application/pdf';
    const url = window.URL.createObjectURL(blob);
    return { url, contentType };
  },

  /**
   * Fix DR4 (2026-08-16) — apercu inline d'une version QUELCONQUE (courante ou
   * historique). Le backend convertit les formats Office en PDF a la volee : les
   * actes `.docx` de la voie directeur deviennent donc consultables sans
   * telechargement, y compris dans leurs versions remplacees.
   */
  async previewVersion(
    documentId: string,
    versionId: string,
  ): Promise<{ url: string; contentType: string }> {
    const response = await api.get<Blob>(
      `/dataroom/documents/${documentId}/versions/${versionId}/preview`,
      { responseType: 'blob' },
    );
    const blob = response.data as Blob;
    const ctHeader = response.headers['content-type'];
    const contentType =
      (typeof ctHeader === 'string' ? ctHeader : undefined) ??
      blob.type ??
      'application/pdf';
    const url = window.URL.createObjectURL(blob);
    return { url, contentType };
  },

  /** Supprime un document juridique (soft delete : is_current=false). */
  async deleteJuridiqueDocument(documentId: string): Promise<void> {
    await api.delete(`/dataroom/documents/${documentId}`);
  },

  /**
   * Sprint 7 / TASK 4 -- Suppression bulk en transaction unique.
   * Tous les documents ou aucun (rollback si une erreur survient).
   */
  async deleteJuridiqueBulk(documentIds: string[]): Promise<void> {
    if (documentIds.length === 0) return;
    await api.delete('/dataroom/juridique/documents', {
      data: { documentIds },
    });
  },

  /**
   * Sprint 7 / TASK 4 -- Telechargement ZIP d'une selection de documents.
   * Le backend cree un ZIP avec arborescence {documentType}/{filename}.
   */
  async exportJuridiqueZip(
    dossierId: string,
    documentIds: string[],
    includeOldVersions: boolean,
  ): Promise<void> {
    if (documentIds.length === 0) return;
    const response = await api.post(
      `/dataroom/dossiers/${dossierId}/juridique/export-zip`,
      { documentIds, includeOldVersions },
      { responseType: 'blob' },
    );
    const blob = response.data as Blob;
    const today = new Date().toISOString().slice(0, 10);
    triggerDownload(blob, `juridique_${dossierId.slice(0, 8)}_${today}.zip`);
  },

  /**
   * Fiche client (2026-07-14) — remplace « Exporter rapport PDF ». Genere la
   * carte d'identite juridique de la societe (PDF charte JURIKA). Reserve
   * EMPLOYE responsable / SUPERVISEUR / SUPER_ADMIN (CLIENT -> 403).
   */
  async exportFicheClientPdf(
    dossierId: string,
    raisonSociale: string,
  ): Promise<void> {
    const response = await api.get(
      `/dataroom/dossiers/${dossierId}/juridique/fiche-client-pdf`,
      { responseType: 'blob' },
    );
    const blob = response.data as Blob;
    const today = new Date().toISOString().slice(0, 10);
    const safeName = raisonSociale.replace(/[^a-zA-Z0-9_-]/g, '_').slice(0, 40);
    triggerDownload(blob, `Fiche_Client_${safeName}_${today}.pdf`);
  },

  /**
   * Fiche client (2026-07-14) — met a jour les identifiants de la societe
   * (RC, IF, patente, CNSS, adresse, capital...). Sert au formulaire
   * « Identifiants de la societe ». Cible ticket-service (proprietaire de
   * entreprise_dossiers) via le gateway.
   */
  async updateIdentifiants(
    dossierId: string,
    payload: UpdateIdentifiantsPayload,
  ): Promise<void> {
    await api.patch(`/dossiers/${dossierId}/identifiants`, payload);
  },

  /** Lot L1 (RG-FIC-02) : versions successives de la taxe professionnelle. */
  async listVersionsTp(dossierId: string): Promise<VersionTaxeProfessionnelle[]> {
    const { data } = await api.get<VersionTaxeProfessionnelle[]>(
      `/dossiers/${dossierId}/taxe-professionnelle/versions`,
    );
    return data;
  },

  /** Lot L1 : historique des responsables du dossier (transferts, reaffectations, rattrapages). */
  async historiqueResponsables(dossierId: string): Promise<ReaffectationVue[]> {
    const { data } = await api.get<ReaffectationVue[]>(`/dossiers/${dossierId}/reaffectations`);
    return data;
  },

  /** Lot L1 (D1) : dossiers rattrapes par la migration V28, a verifier par le superviseur. */
  async listRattrapages(): Promise<ReaffectationVue[]> {
    const { data } = await api.get<ReaffectationVue[]>('/dossiers/rattrapages');
    return data;
  },

  async verifierRattrapage(reaffectationId: string): Promise<ReaffectationVue> {
    const { data } = await api.post<ReaffectationVue>(
      `/dossiers/reaffectations/${reaffectationId}/verification`,
    );
    return data;
  },

  /** Lot L1 (RG-DOS-03) : reaffectation d'office par le superviseur. */
  async reaffecter(dossierId: string, nouveauResponsableId: string, motif: string): Promise<void> {
    await api.post(`/dossiers/${dossierId}/reaffectation`, { nouveauResponsableId, motif });
  },

  /** Lot L1 : droits de l'utilisateur sur le dossier (bouton de suppression des documents). */
  async mesDroits(dossierId: string): Promise<DroitsDossier> {
    const { data } = await api.get<DroitsDossier>(`/dataroom/dossiers/${dossierId}/mes-droits`);
    return data;
  },

  // ---- Depots (Lot V : espace « Depots » client) ----
  /** Depot libre d'un fichier (multipart). CLIENT gate par perm_depot cote back. */
  async uploadDepot(dossierId: string, file: File, title?: string): Promise<DepotSummary> {
    const form = new FormData();
    form.append('file', file);
    if (title?.trim()) form.append('title', title.trim());
    const { data } = await api.post<DepotSummary>(
      `/dataroom/dossiers/${dossierId}/depots/upload`,
      form,
      { headers: { 'Content-Type': 'multipart/form-data' } },
    );
    return data;
  },

  async listDepots(dossierId: string): Promise<DepotSummary[]> {
    const { data } = await api.get<DepotSummary[]>(
      `/dataroom/dossiers/${dossierId}/depots`,
    );
    return data;
  },

  async downloadDepot(id: string, filename: string): Promise<void> {
    const response = await api.get(`/dataroom/depots/${id}/download`, {
      responseType: 'blob',
    });
    triggerDownload(response.data as Blob, filename);
  },

  /**
   * Apercu inline d'un depot (meme logique que previewDocument). Le caller DOIT
   * appeler URL.revokeObjectURL(url) a la fermeture pour eviter la fuite memoire.
   */
  async previewDepot(id: string): Promise<{ url: string; contentType: string }> {
    const response = await api.get<Blob>(`/dataroom/depots/${id}/preview`, {
      responseType: 'blob',
    });
    const blob = response.data as Blob;
    const ctHeader = response.headers['content-type'];
    const contentType =
      (typeof ctHeader === 'string' ? ctHeader : undefined) ??
      blob.type ??
      'application/pdf';
    const url = window.URL.createObjectURL(blob);
    return { url, contentType };
  },

  async deleteDepot(id: string): Promise<void> {
    await api.delete(`/dataroom/depots/${id}`);
  },


  // ---- Demandes ----
  async createDemande(payload: CreateDemandePayload): Promise<DemandeSummary> {
    const { data } = await api.post<DemandeSummary>('/dataroom/demandes', payload);
    return data;
  },

  async listDemandesByDossier(dossierId: string): Promise<DemandeSummary[]> {
    const { data } = await api.get<DemandeSummary[]>(
      `/dataroom/dossiers/${dossierId}/demandes`,
    );
    return data;
  },

  async listDemandes(statut?: DemandeStatut): Promise<DemandeSummary[]> {
    const { data } = await api.get<DemandeSummary[]>('/dataroom/demandes', {
      params: statut ? { statut } : undefined,
    });
    return data;
  },

  async updateDemande(id: string, payload: UpdateDemandePayload): Promise<DemandeSummary> {
    const { data } = await api.patch<DemandeSummary>(`/dataroom/demandes/${id}`, payload);
    return data;
  },

  /**
   * Vue SUPERVISEUR (workspace-wide) enrichie : toutes les demandes OU requetes
   * du cabinet avec le nom du dataroom + l'employe responsable resolus par
   * jointure cote back. Reserve SUPERVISEUR / SUPER_ADMIN (403 sinon).
   * `direction` : CLIENT_TO_EMPLOYE (demandes des clients) ou EMPLOYE_TO_CLIENT
   * (requetes aux clients).
   */
  async listSupervisionDemandes(
    direction: DemandeDirection,
    statut?: string,
  ): Promise<DemandeSupervisionRow[]> {
    const { data } = await api.get<DemandeSupervisionRow[]>('/dataroom/supervision/demandes', {
      params: { direction, ...(statut ? { statut } : {}) },
    });
    return data;
  },

  // ---- Lot AG : Requetes au client (direction EMPLOYE_TO_CLIENT) ----
  async createRequete(payload: CreateRequetePayload): Promise<RequeteSummary> {
    const { data } = await api.post<RequeteSummary>('/dataroom/requetes', payload);
    return data;
  },

  /** Requetes EMPLOYE_TO_CLIENT d'un dossier (cote client : « Demandes de mon conseiller »). */
  async listRequetesByDossier(dossierId: string): Promise<RequeteSummary[]> {
    const { data } = await api.get<RequeteSummary[]>(
      `/dataroom/dossiers/${dossierId}/demandes`,
      { params: { direction: 'EMPLOYE_TO_CLIENT' } },
    );
    return data;
  },

  /** Mes requetes au client (cote employe : « Mes requetes au client »). */
  async listMesRequetes(statut?: RequeteStatut): Promise<RequeteSummary[]> {
    const { data } = await api.get<RequeteSummary[]>('/dataroom/demandes', {
      params: { direction: 'EMPLOYE_TO_CLIENT', ...(statut ? { statut } : {}) },
    });
    return data;
  },

  async repondreRequete(id: string, payload: RepondreRequetePayload): Promise<RequeteSummary> {
    const { data } = await api.post<RequeteSummary>(`/dataroom/demandes/${id}/repondre`, payload);
    return data;
  },

  async validerRequete(id: string): Promise<RequeteSummary> {
    const { data } = await api.post<RequeteSummary>(`/dataroom/demandes/${id}/valider`, null);
    return data;
  },

  async complementRequete(id: string, payload: ComplementRequetePayload): Promise<RequeteSummary> {
    const { data } = await api.post<RequeteSummary>(`/dataroom/demandes/${id}/complement`, payload);
    return data;
  },

  // ---- Settings (permissions / suspension / lien client / access count) ----
  async getSettings(dossierId: string): Promise<DataroomSettings> {
    const { data } = await api.get<DataroomSettings>(
      `/dataroom/dossiers/${dossierId}/settings`,
    );
    return data;
  },

  /**
   * 2026-06-30 — Permissions du CLIENT sur SON dossier (lecture seule, vue
   * allegee). Ouvert au role CLIENT (vs getSettings reserve au cabinet).
   */
  async getMyPermissions(dossierId: string): Promise<ClientPermissions> {
    const { data } = await api.get<ClientPermissions>(
      `/dataroom/dossiers/${dossierId}/settings/my-permissions`,
    );
    return data;
  },

  /** Lot L1 (RG-CLI-01) : seules les permissions passees changent ; chaque changement est trace. */
  async updatePermissions(dossierId: string, patch: PermissionsClientPatch): Promise<DataroomSettings> {
    const { data } = await api.patch<DataroomSettings>(
      `/dataroom/dossiers/${dossierId}/settings/permissions`,
      patch,
    );
    return data;
  },

  /** Lot L1 (RG-CLI-01) : historique des permissions et des suspensions du client. */
  async historiqueAccesClient(dossierId: string): Promise<HistoriqueAccesClient[]> {
    const { data } = await api.get<HistoriqueAccesClient[]>(
      `/dataroom/dossiers/${dossierId}/settings/historique`,
    );
    return data;
  },

  async toggleSuspension(dossierId: string, suspended: boolean): Promise<DataroomSettings> {
    const { data } = await api.patch<DataroomSettings>(
      `/dataroom/dossiers/${dossierId}/settings/suspension`,
      { suspended },
    );
    return data;
  },

  // ---- Access log (Sprint 7 / TASK 5) ----
  /**
   * Liste paginee des acces client a un dossier (drawer "Activite client").
   * RBAC : ROLE_SUPER_ADMIN / ROLE_SUPERVISEUR / ROLE_EMPLOYE (pas CLIENT).
   */
  async getAccessLog(
    dossierId: string,
    limit = 50,
    offset = 0,
  ): Promise<AccessLogPage> {
    const { data } = await api.get<AccessLogPage>(
      `/dataroom/dossiers/${dossierId}/access-log`,
      { params: { limit, offset } },
    );
    return data;
  },

  /**
   * Lot X -- Journalise une impression client (PRINT_DOC). L'impression se
   * declenche cote navigateur (window.print), aucun appel serveur ne la trace
   * naturellement : on l'appelle en fire-and-forget apres avoir lance
   * l'impression. Le backend resout le dossier depuis le document et renvoie
   * toujours 204. Best-effort : les erreurs sont avalees (ne bloque jamais).
   */
  async logPrint(documentId: string): Promise<void> {
    try {
      await api.post(`/dataroom/documents/${documentId}/print-log`);
    } catch {
      // silencieux -- le tracage ne doit jamais degrader l'UX d'impression
    }
  },
};
