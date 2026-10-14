import { api } from '../lib/api';
import type {
  AbonnementAdminVue,
  AbonnementVue,
  ApercuChangementVue,
  BilanTacheVue,
  ChangementForfaitRequest,
  ChangementForfaitVue,
  ContactSalesRequest,
  ContactSalesResponse,
  EnregistrementPaiementRequest,
  FactureVue,
  PaiementVue,
  PreparationPaiementRequest,
  PreparationPaiementVue,
  PricingResponse,
  SurDevisVue,
  UsageSnapshot,
  ValidationPaiementRequest,
} from '../types/billing';

/**
 * Lot L7 (E5) : client de la facturation (billing-service via la passerelle). Stripe est retire ;
 * le paiement par carte passe par la page hebergee du CMI (formulaire auto-soumis), et n'est acquis
 * qu'au retour serveur signe du CMI.
 */
export const billingService = {
  /** Grille publique (auth-service, catalogue du CDC 4.3). */
  async getPricing(): Promise<PricingResponse> {
    const { data } = await api.get<PricingResponse>('/public/pricing');
    return data;
  },

  /** Usage du forfait (dossiers du mois d'abonnement, acces employes, stockage). 204 : controle coupe. */
  async getUsage(): Promise<UsageSnapshot | null> {
    try {
      const { data, status } = await api.get<UsageSnapshot>('/workspace/usage');
      if (status === 204) return null;
      return data;
    } catch (err) {
      if ((err as { response?: { status?: number } })?.response?.status === 404) return null;
      throw err;
    }
  },

  async getAbonnement(): Promise<AbonnementVue> {
    const { data } = await api.get<AbonnementVue>('/billing/abonnement');
    return data;
  },

  async apercuChangement(req: ChangementForfaitRequest): Promise<ApercuChangementVue> {
    const { data } = await api.post<ApercuChangementVue>('/billing/changement-forfait/apercu', req);
    return data;
  },

  async changerForfait(req: ChangementForfaitRequest): Promise<ChangementForfaitVue> {
    const { data } = await api.post<ChangementForfaitVue>('/billing/changement-forfait', req);
    return data;
  },

  async annulerChangement(): Promise<void> {
    await api.delete('/billing/changement-forfait');
  },

  async preparerPaiement(req: PreparationPaiementRequest): Promise<PreparationPaiementVue> {
    const { data } = await api.post<PreparationPaiementVue>('/billing/paiements', req);
    return data;
  },

  async listerPaiements(): Promise<PaiementVue[]> {
    const { data } = await api.get<PaiementVue[]>('/billing/paiements');
    return data;
  },

  async getPaiement(id: number): Promise<PaiementVue> {
    const { data } = await api.get<PaiementVue>(`/billing/paiements/${id}`);
    return data;
  },

  async listerFactures(): Promise<FactureVue[]> {
    const { data } = await api.get<FactureVue[]>('/billing/factures');
    return data;
  },

  async telechargerFacture(id: number): Promise<Blob> {
    const { data } = await api.get<Blob>(`/billing/factures/${id}/pdf`, { responseType: 'blob' });
    return data;
  },

  async contactSales(req: ContactSalesRequest): Promise<ContactSalesResponse> {
    const { data } = await api.post<ContactSalesResponse>('/billing/contact-sales', req);
    return data;
  },
};

/** Lot L7 (E5) : facturation vue du super-admin (tous les cabinets). */
export const billingAdminService = {
  async paiementsAEnregistrer(): Promise<PaiementVue[]> {
    const { data } = await api.get<PaiementVue[]>('/billing/admin/paiements/a-enregistrer');
    return data;
  },

  async paiementsRecents(): Promise<PaiementVue[]> {
    const { data } = await api.get<PaiementVue[]>('/billing/admin/paiements');
    return data;
  },

  async enregistrer(req: EnregistrementPaiementRequest): Promise<PaiementVue> {
    const { data } = await api.post<PaiementVue>('/billing/admin/paiements', req);
    return data;
  },

  async valider(id: number, req: ValidationPaiementRequest): Promise<PaiementVue> {
    const { data } = await api.post<PaiementVue>(`/billing/admin/paiements/${id}/valider`, req);
    return data;
  },

  async rejeter(id: number, motif: string): Promise<PaiementVue> {
    const { data } = await api.post<PaiementVue>(`/billing/admin/paiements/${id}/rejeter`, { motif });
    return data;
  },

  async abonnements(): Promise<AbonnementAdminVue[]> {
    const { data } = await api.get<AbonnementAdminVue[]>('/billing/admin/abonnements');
    return data;
  },

  async surDevis(workspaceId: string): Promise<SurDevisVue> {
    const { data } = await api.get<SurDevisVue>(`/billing/admin/abonnements/${workspaceId}/sur-devis`);
    return data;
  },

  async factures(workspaceId?: string): Promise<FactureVue[]> {
    const { data } = await api.get<FactureVue[]>('/billing/admin/factures', {
      params: workspaceId ? { workspaceId } : undefined,
    });
    return data;
  },

  async telechargerFacture(id: number): Promise<Blob> {
    const { data } = await api.get<Blob>(`/billing/admin/factures/${id}/pdf`, { responseType: 'blob' });
    return data;
  },

  async tacheFacturesEnAttente(): Promise<BilanTacheVue> {
    const { data } = await api.post<BilanTacheVue>('/billing/admin/taches/factures-en-attente');
    return data;
  },

  async tacheActivations(): Promise<BilanTacheVue> {
    const { data } = await api.post<BilanTacheVue>('/billing/admin/taches/activations');
    return data;
  },

  async tacheArchivage(): Promise<BilanTacheVue> {
    const { data } = await api.post<BilanTacheVue>('/billing/admin/taches/archivage');
    return data;
  },
};

/** Ouvre le telechargement d'un PDF recu en blob (facture). */
export function enregistrerPdf(blob: Blob, nom: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = nom;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
