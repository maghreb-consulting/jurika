import { api } from '../lib/api';
import { identifiantUnique } from '../lib/identifiant';

/**
 * Lot L7 (E4) : essai gratuit (CDC 4.2).
 * - inscription publique : /public/essai/** (cinq informations, conditions, SMS, lien) ;
 * - etat de l'essai du cabinet : /workspace/essai (bandeau, rappels, lecture seule) ;
 * - super-admin : /admin/essais et /admin/workspaces/{id}/essai/**.
 */

export interface ConditionsEssai {
  version: string;
  empreinte: string;
  texte: string;
}

export interface DemandeEssaiPayload {
  nom: string;
  prenom: string;
  organisation: string;
  telephone: string;
  email: string;
  conditionsAcceptees: boolean;
  conditionsVersion: string;
}

export interface DemandeEssaiCreee {
  demandeId: string;
  telephoneMasque: string;
  email: string;
  smsEnvoye: boolean;
  emailEnvoye: boolean;
  expireLe: string;
}

export interface EtatDemandeEssai {
  demandeId: string;
  emailVerifie: boolean;
  telephoneVerifie: boolean;
  essaiCree: boolean;
  workspaceCode: string | null;
  loginEmail: string | null;
  identifiantsEnvoyes: boolean;
}

export interface LimitesEssai {
  dossiers: number;
  superviseurs: number;
  employes: number;
  stockageOctets: number;
  questionsChatbot: number;
  forfait: string;
}

export interface EtatEssai {
  enEssai: boolean;
  statut: 'TRIAL_ACTIVE' | 'TRIAL_EXPIRED' | 'CONVERTED' | 'CANCELLED' | null;
  debut: string | null;
  fin: string | null;
  joursRestants: number;
  rappel: 'J-3' | 'J-1' | null;
  lectureSeule: boolean;
  lectureSeuleDepuis: string | null;
  suppressionLe: string | null;
  avertissementLe: string | null;
  prolongeLe: string | null;
  prolongeable: boolean;
  limites: LimitesEssai | null;
}

export interface EssaiAdmin {
  workspaceId: string;
  code: string;
  nom: string | null;
  contactEmail: string | null;
  statut: string | null;
  statutEssai: string | null;
  debut: string | null;
  fin: string | null;
  expireLe: string | null;
  avertissementLe: string | null;
  prolongeLe: string | null;
  prolongationJours: number | null;
  finMotif: string | null;
  abonnement: string | null;
  adresseIp: string | null;
  appareil: string | null;
  signalement: string | null;
  signalementExamineLe: string | null;
  creeLe: string;
  supprimeLe: string | null;
}

const CLE_APPAREIL = 'jurika.appareil';

/**
 * Identifiant aleatoire de l'appareil (RG-ESS-03 : meme appareil a l'origine de
 * plusieurs essais). Conserve dans le navigateur ; absent si le stockage est
 * indisponible (l'inscription reste possible, le signalement se fonde alors sur le reseau).
 */
export function identifiantAppareil(): string | null {
  try {
    const existant = localStorage.getItem(CLE_APPAREIL);
    if (existant && /^[A-Za-z0-9-]{8,64}$/.test(existant)) return existant;
    const nouveau = identifiantUnique();
    localStorage.setItem(CLE_APPAREIL, nouveau);
    return nouveau;
  } catch {
    return null;
  }
}

export const essaiService = {
  async conditions(): Promise<ConditionsEssai> {
    const { data } = await api.get<ConditionsEssai>('/public/essai/conditions');
    return data;
  },

  async demander(payload: DemandeEssaiPayload): Promise<DemandeEssaiCreee> {
    const appareil = identifiantAppareil();
    const { data } = await api.post<DemandeEssaiCreee>('/public/essai/demandes', payload, {
      headers: appareil ? { 'X-Jurika-Appareil': appareil } : undefined,
    });
    return data;
  },

  async etatDemande(demandeId: string): Promise<EtatDemandeEssai> {
    const { data } = await api.get<EtatDemandeEssai>(`/public/essai/demandes/${demandeId}`);
    return data;
  },

  async verifierTelephone(demandeId: string, code: string): Promise<EtatDemandeEssai> {
    const { data } = await api.post<EtatDemandeEssai>(`/public/essai/demandes/${demandeId}/telephone`, { code });
    return data;
  },

  async renvoyerCode(demandeId: string): Promise<{ smsEnvoye: boolean }> {
    const { data } = await api.post<{ smsEnvoye: boolean }>(`/public/essai/demandes/${demandeId}/code`);
    return data;
  },

  async confirmerAdresse(jeton: string): Promise<EtatDemandeEssai> {
    const { data } = await api.post<EtatDemandeEssai>('/public/essai/confirmer-adresse', { jeton });
    return data;
  },

  async etat(): Promise<EtatEssai> {
    const { data } = await api.get<EtatEssai>('/workspace/essai');
    return data;
  },

  // ----- Super-admin -----
  async liste(): Promise<EssaiAdmin[]> {
    const { data } = await api.get<EssaiAdmin[]>('/admin/essais');
    return data;
  },

  async prolonger(workspaceId: string, jours: number): Promise<EtatEssai> {
    const { data } = await api.post<EtatEssai>(`/admin/workspaces/${workspaceId}/essai/prolonger`, { jours });
    return data;
  },

  async mettreFin(workspaceId: string, motif: string): Promise<EtatEssai> {
    const { data } = await api.post<EtatEssai>(`/admin/workspaces/${workspaceId}/essai/mettre-fin`, { motif });
    return data;
  },

  async signalementExamine(workspaceId: string): Promise<void> {
    await api.post(`/admin/workspaces/${workspaceId}/essai/signalement-examine`);
  },

  async suspendre(workspaceId: string): Promise<void> {
    await api.post(`/admin/workspaces/${workspaceId}/suspend`);
  },

  async leverSuspension(workspaceId: string): Promise<void> {
    await api.post(`/admin/workspaces/${workspaceId}/activate`);
  },
};
