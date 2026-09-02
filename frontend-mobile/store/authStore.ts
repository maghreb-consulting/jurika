import { create } from "zustand";
import { authApi } from "../lib/api";
import { secureStorage } from "../lib/secureStorage";
import type { AuthUser, LoginResponse } from "../types";

interface PendingTwoFA {
  userId: string;
  workspaceId: string;
}

interface AuthState {
  hydrated: boolean;
  isAuthenticated: boolean;
  user: AuthUser | null;
  workspaceCode: string | null;
  pending2FA: PendingTwoFA | null;
  error: string | null;
  loading: boolean;

  hydrate: () => Promise<void>;
  checkWorkspace: (code: string) => Promise<boolean>;
  login: (email: string, password: string) => Promise<LoginResponse>;
  verify2FA: (code: string) => Promise<boolean>;
  setWorkspaceCode: (code: string) => void;
  logout: () => Promise<void>;
  clearError: () => void;
}

function extractTokens(res: LoginResponse): {
  access?: string;
  refresh?: string;
} {
  const access = res.tokens?.accessToken ?? res.accessToken;
  const refresh = res.tokens?.refreshToken ?? res.refreshToken;
  return { access, refresh };
}

export const useAuthStore = create<AuthState>((set, get) => ({
  hydrated: false,
  isAuthenticated: false,
  user: null,
  workspaceCode: null,
  pending2FA: null,
  error: null,
  loading: false,

  hydrate: async () => {
    try {
      const [token, user, wsCode] = await Promise.all([
        secureStorage.getAccessToken(),
        secureStorage.getUser<AuthUser>(),
        secureStorage.getWorkspaceCode(),
      ]);
      set({
        isAuthenticated: Boolean(token && user),
        user: user ?? null,
        workspaceCode: wsCode ?? null,
        hydrated: true,
      });
    } catch {
      set({ hydrated: true });
    }
  },

  setWorkspaceCode: (code) => {
    void secureStorage.setWorkspaceCode(code);
    set({ workspaceCode: code });
  },

  checkWorkspace: async (code: string) => {
    set({ loading: true, error: null });
    try {
      const res = await authApi.workspaceCheck(code);
      if (!res?.exists) {
        set({ error: "Workspace introuvable.", loading: false });
        return false;
      }
      await secureStorage.setWorkspaceCode(code);
      set({ workspaceCode: code, loading: false });
      return true;
    } catch (e: any) {
      set({
        error: e?.response?.data?.message ?? "Erreur de connexion au serveur.",
        loading: false,
      });
      return false;
    }
  },

  login: async (email, password) => {
    const wsCode = get().workspaceCode;
    if (!wsCode) {
      throw new Error("Code workspace manquant.");
    }
    set({ loading: true, error: null });
    try {
      const res = await authApi.login({
        workspaceCode: wsCode,
        email,
        password,
      });

      if (res.requires2FA) {
        set({
          pending2FA: {
            userId: res.userId ?? "",
            workspaceId: res.workspaceId ?? "",
          },
          loading: false,
        });
        return res;
      }

      const { access, refresh } = extractTokens(res);
      if (access) await secureStorage.setAccessToken(access);
      if (refresh) await secureStorage.setRefreshToken(refresh);
      if (res.user) await secureStorage.setUser(res.user);

      set({
        isAuthenticated: Boolean(access),
        user: res.user ?? null,
        pending2FA: null,
        loading: false,
      });
      return res;
    } catch (e: any) {
      set({
        error:
          e?.response?.data?.message ??
          "Identifiants invalides ou serveur indisponible.",
        loading: false,
      });
      throw e;
    }
  },

  verify2FA: async (code: string) => {
    const pending = get().pending2FA;
    if (!pending) {
      set({ error: "Aucune session 2FA en cours." });
      return false;
    }
    set({ loading: true, error: null });
    try {
      const res = await authApi.verify2fa({
        userId: pending.userId,
        workspaceId: pending.workspaceId,
        code,
      });

      const { access, refresh } = extractTokens(res);
      if (access) await secureStorage.setAccessToken(access);
      if (refresh) await secureStorage.setRefreshToken(refresh);
      if (res.user) await secureStorage.setUser(res.user);

      set({
        isAuthenticated: Boolean(access),
        user: res.user ?? null,
        pending2FA: null,
        loading: false,
      });
      return true;
    } catch (e: any) {
      set({
        error: e?.response?.data?.message ?? "Code 2FA invalide.",
        loading: false,
      });
      return false;
    }
  },

  logout: async () => {
    try {
      await authApi.logout();
    } catch {
      /* ignore */
    }
    await secureStorage.clear();
    set({
      isAuthenticated: false,
      user: null,
      pending2FA: null,
      // we keep workspaceCode so re-login is faster
    });
  },

  clearError: () => set({ error: null }),
}));
