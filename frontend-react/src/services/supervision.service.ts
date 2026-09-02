import { api } from '../lib/api';

export interface WorkspaceKpis {
  tickets: number;
  ticketsNouveaux: number;
  ticketsEnCours: number;
  ticketsClotures: number;
  ticketsAnnules: number;
  dossiers: number;
  dossiersActifs: number;
  deboursTotalMad: number;
  demandesNonTraitees: number;
  computedAt: string;
}

export interface TicketsPerType {
  type: string;
  count: number;
}

export interface TicketsPerStatut {
  statut: string;
  count: number;
}

export interface TicketsPerEmploye {
  userId: string;
  firstName: string;
  lastName: string;
  enCours: number;
  clotures: number;
  annules: number;
}

export interface TicketsDaily {
  date: string;
  count: number;
}

export const supervisionService = {
  async kpis(): Promise<WorkspaceKpis> {
    const { data } = await api.get<WorkspaceKpis>('/supervision/kpis');
    return data;
  },
  async ticketsPerType(): Promise<TicketsPerType[]> {
    const { data } = await api.get<TicketsPerType[]>('/supervision/tickets-per-type');
    return data;
  },
  async ticketsPerStatut(): Promise<TicketsPerStatut[]> {
    const { data } = await api.get<TicketsPerStatut[]>('/supervision/tickets-per-statut');
    return data;
  },
  async ticketsPerEmploye(): Promise<TicketsPerEmploye[]> {
    const { data } = await api.get<TicketsPerEmploye[]>('/supervision/tickets-per-employe');
    return data;
  },
  async ticketsLast30Days(): Promise<TicketsDaily[]> {
    const { data } = await api.get<TicketsDaily[]>('/supervision/tickets-last-30-days');
    return data;
  },
};
