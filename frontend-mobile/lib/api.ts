import axios, { AxiosError, AxiosRequestConfig } from "axios";
import { secureStorage } from "./secureStorage";

const API_URL =
  process.env.EXPO_PUBLIC_API_URL?.trim() || "http://localhost:8080/api/v1";

export const api = axios.create({
  baseURL: API_URL,
  timeout: 15000,
  headers: {
    "Content-Type": "application/json",
    Accept: "application/json",
  },
});

// --- request interceptor: inject access token ---
api.interceptors.request.use(async (config) => {
  const token = await secureStorage.getAccessToken();
  if (token) {
    config.headers = config.headers ?? {};
    (config.headers as Record<string, string>).Authorization = `Bearer ${token}`;
  }
  return config;
});

// --- response interceptor: try refresh on 401 (single retry) ---
let isRefreshing = false;
let pendingQueue: Array<(token: string | null) => void> = [];

function resolveQueue(token: string | null) {
  pendingQueue.forEach((cb) => cb(token));
  pendingQueue = [];
}

api.interceptors.response.use(
  (res) => res,
  async (error: AxiosError) => {
    const original = error.config as AxiosRequestConfig & { _retry?: boolean };
    if (!error.response || error.response.status !== 401 || original?._retry) {
      return Promise.reject(error);
    }

    original._retry = true;

    if (isRefreshing) {
      return new Promise((resolve, reject) => {
        pendingQueue.push((token) => {
          if (!token) {
            reject(error);
            return;
          }
          original.headers = original.headers ?? {};
          (original.headers as Record<string, string>).Authorization = `Bearer ${token}`;
          resolve(api(original));
        });
      });
    }

    isRefreshing = true;
    try {
      const refreshToken = await secureStorage.getRefreshToken();
      if (!refreshToken) {
        await secureStorage.clear();
        resolveQueue(null);
        return Promise.reject(error);
      }
      const { data } = await axios.post(`${API_URL}/auth/refresh`, {
        refreshToken,
      });
      const newToken: string =
        data?.accessToken ?? data?.tokens?.accessToken ?? "";
      if (!newToken) throw new Error("No access token returned");
      await secureStorage.setAccessToken(newToken);
      if (data?.refreshToken) {
        await secureStorage.setRefreshToken(data.refreshToken);
      }
      resolveQueue(newToken);
      original.headers = original.headers ?? {};
      (original.headers as Record<string, string>).Authorization = `Bearer ${newToken}`;
      return api(original);
    } catch (refreshErr) {
      await secureStorage.clear();
      resolveQueue(null);
      return Promise.reject(refreshErr);
    } finally {
      isRefreshing = false;
    }
  }
);

// ---------- typed helpers ----------
import type {
  ChatbotMessage,
  DossierBrief,
  DossierJuridique,
  KpisResponse,
  LoginResponse,
  Ticket,
  TicketsListResponse,
  WorkspaceCheckResponse,
} from "../types";

export const authApi = {
  workspaceCheck: (workspaceCode: string) =>
    api
      .post<WorkspaceCheckResponse>("/auth/workspace-check", { workspaceCode })
      .then((r) => r.data),

  login: (payload: { workspaceCode: string; email: string; password: string }) =>
    api.post<LoginResponse>("/auth/login", payload).then((r) => r.data),

  verify2fa: (payload: { userId: string; workspaceId: string; code: string }) =>
    api.post<LoginResponse>("/auth/verify-2fa", payload).then((r) => r.data),

  logout: () => api.post("/auth/logout").then((r) => r.data).catch(() => null),
};

export const ticketsApi = {
  list: (limit = 20) =>
    api
      .get<TicketsListResponse>(`/tickets?limit=${limit}`)
      .then((r) => r.data),

  get: (id: string) => api.get<Ticket>(`/tickets/${id}`).then((r) => r.data),

  create: (payload: Partial<Ticket>) =>
    api.post<Ticket>("/tickets", payload).then((r) => r.data),

  transition: (id: string, target: string, comment?: string) =>
    api
      .post<Ticket>(`/tickets/${id}/transition`, { target, comment })
      .then((r) => r.data),
};

export const dataroomApi = {
  listDossiers: () =>
    api.get<DossierBrief[]>("/dataroom/dossiers").then((r) => r.data),

  getJuridique: (id: string) =>
    api
      .get<DossierJuridique>(`/dataroom/dossiers/${id}/juridique`)
      .then((r) => r.data),
};

export const chatbotApi = {
  ask: (question: string) =>
    api
      .post<{ answer: string }>("/chatbot/ask", { question })
      .then((r) => r.data),
};

export const supervisionApi = {
  kpis: () =>
    api
      .get<KpisResponse>("/supervision/kpis")
      .then((r) => r.data)
      .catch(() => ({}) as KpisResponse),
};

export type { ChatbotMessage };
export { API_URL };
