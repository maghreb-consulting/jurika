import { api } from '../lib/api';
import type {
  AcceptationVue,
  ApercuContrat,
  ConditionsSaisie,
  EditeurParametres,
  EditeurTrace,
  EtatContrat,
  EtatContratEmploye,
  HistoriqueContrat,
  LigneAdminContrat,
  ReponseVersion,
  ResiliationVue,
  SignataireSaisie,
  VersionVue,
} from '../types/contrat';

/**
 * Lot L7 (E6) : contrat d'abonnement (cote cabinet et super-admin) et parametres de l'editeur.
 * Le serveur verifie chaque droit (P6) ; l'acceptation exige la case cochee ET la confirmation.
 */
export const contratService = {
  async etat(): Promise<EtatContrat | EtatContratEmploye> {
    const { data } = await api.get<EtatContrat | EtatContratEmploye>('/billing/contrat/etat');
    return data;
  },

  async apercu(signataire: SignataireSaisie): Promise<ApercuContrat> {
    const { data } = await api.post<ApercuContrat>('/billing/contrat/apercu', {
      ...signataire,
      empreinteLue: null,
      caseCochee: false,
      confirmation: false,
    });
    return data;
  },

  async accepter(signataire: SignataireSaisie, empreinteLue: string, caseCochee: boolean): Promise<AcceptationVue> {
    const { data } = await api.post<AcceptationVue>('/billing/contrat/accepter', {
      ...signataire,
      empreinteLue,
      caseCochee,
      confirmation: true,
    });
    return data;
  },

  /** Article 16 : refus de la nouvelle version, qui vaut resiliation a l'echeance (confirmation exigee). */
  async refuserVersion(versionCode: string): Promise<ReponseVersion> {
    const { data } = await api.post<ReponseVersion>('/billing/contrat/refuser-version', {
      versionCode,
      confirmation: true,
    });
    return data;
  },

  async historique(): Promise<HistoriqueContrat> {
    const { data } = await api.get<HistoriqueContrat>('/billing/contrat/historique');
    return data;
  },

  async exemplaire(acceptationId: string): Promise<Blob> {
    const { data } = await api.get(`/billing/contrat/acceptations/${acceptationId}/exemplaire`, {
      responseType: 'blob',
    });
    return data as Blob;
  },

  async renvoyerCopie(acceptationId: string): Promise<void> {
    await api.post(`/billing/contrat/acceptations/${acceptationId}/copie`);
  },

  async resilier(motif: string): Promise<ResiliationVue> {
    const { data } = await api.post<ResiliationVue>('/billing/contrat/resilier', { motif });
    return data;
  },

  async versions(): Promise<VersionVue[]> {
    const { data } = await api.get<VersionVue[]>('/billing/contrat/versions');
    return data;
  },

  async versionTexte(code: string): Promise<string> {
    const { data } = await api.get<string>(`/billing/contrat/versions/${encodeURIComponent(code)}`, {
      responseType: 'text',
    });
    return data;
  },

  // ----- Super-admin -----
  async listeAdmin(): Promise<LigneAdminContrat[]> {
    const { data } = await api.get<LigneAdminContrat[]>('/billing/admin/contrats');
    return data;
  },

  async ouvrirConditions(workspaceId: string, c: ConditionsSaisie): Promise<{ conditionsId: string }> {
    const { data } = await api.post<{ conditionsId: string }>(`/billing/admin/contrats/${workspaceId}/conditions`, c);
    return data;
  },

  async resilierAdmin(workspaceId: string, dateEffet: string, motif: string): Promise<ResiliationVue> {
    const { data } = await api.post<ResiliationVue>(`/billing/admin/contrats/${workspaceId}/resilier`, {
      dateEffet,
      motif,
    });
    return data;
  },

  async historiqueAdmin(workspaceId: string): Promise<HistoriqueContrat> {
    const { data } = await api.get<HistoriqueContrat>(`/billing/admin/contrats/${workspaceId}/historique`);
    return data;
  },

  async exemplaireAdmin(workspaceId: string, acceptationId: string): Promise<Blob> {
    const { data } = await api.get(`/billing/admin/contrats/${workspaceId}/acceptations/${acceptationId}/exemplaire`, {
      responseType: 'blob',
    });
    return data as Blob;
  },

  async versionsAdmin(): Promise<VersionVue[]> {
    const { data } = await api.get<VersionVue[]>('/billing/admin/contrats/versions');
    return data;
  },

  async versionTexteAdmin(code: string): Promise<string> {
    const { data } = await api.get<string>(`/billing/admin/contrats/versions/${encodeURIComponent(code)}`, {
      responseType: 'text',
    });
    return data;
  },

  async publierVersion(code: string, texteModele: string, entreeEnVigueur: string): Promise<VersionVue> {
    const { data } = await api.post<VersionVue>('/billing/admin/contrats/versions', { code, texteModele, entreeEnVigueur });
    return data;
  },

  // ----- Parametres de l'editeur (super-admin) -----
  async editeur(): Promise<EditeurParametres> {
    const { data } = await api.get<EditeurParametres>('/billing/admin/editeur');
    return data;
  },

  async modifierEditeur(valeurs: Record<string, string | null>, tauxTvaPourcent: number): Promise<EditeurParametres> {
    const { data } = await api.put<EditeurParametres>('/billing/admin/editeur', { ...valeurs, tauxTvaPourcent });
    return data;
  },

  async historiqueEditeur(): Promise<EditeurTrace[]> {
    const { data } = await api.get<EditeurTrace[]>('/billing/admin/editeur/historique');
    return data;
  },
};

/** Etat complet (superviseur) ou reduit (employe). */
export function estEtatComplet(e: EtatContrat | EtatContratEmploye): e is EtatContrat {
  return 'objetAAccepter' in e;
}

/** Telecharge un fichier recu en Blob. */
export function enregistrerFichier(blob: Blob, nom: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = nom;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

/** Evenement emis par l'intercepteur d'API quand la passerelle repond CONTRAT_EN_ATTENTE (423). */
export const EVENEMENT_CONTRAT_EN_ATTENTE = 'jurika:contrat-en-attente';
