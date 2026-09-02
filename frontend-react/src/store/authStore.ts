import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import { tokenStorage } from '../lib/api';
import { authService } from '../services/auth.service';
import type { AuthTokens, Role, User } from '../types/auth';

interface AuthState {
  user: User | null;
  isAuthenticated: boolean;
  isHydrating: boolean;

  setSession: (tokens: AuthTokens, role: Role, email: string, workspaceCode?: string) => void;
  hydrate: () => Promise<void>;
  logout: () => Promise<void>;
}

function decodeJwt(token: string): { role: Role; email: string; userId: string; workspaceId: string } | null {
  try {
    const [, payload] = token.split('.');
    const json = JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/')));
    return {
      role: json.role as Role,
      email: json.email as string,
      userId: json.uid as string,
      workspaceId: json.wsid as string,
    };
  } catch {
    return null;
  }
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      user: null,
      isAuthenticated: false,
      isHydrating: true,

      setSession: (tokens, role, email, workspaceCode) => {
        tokenStorage.setTokens(tokens);
        const decoded = decodeJwt(tokens.accessToken);
        const user: User = {
          userId: tokens.userId,
          workspaceId: tokens.workspaceId,
          // 2026-06-22 — vrai code workspace (saisi au login). Fallback sur le
          // code deja persiste si l'appelant ne le fournit pas.
          workspaceCode: workspaceCode ?? get().user?.workspaceCode,
          email,
          role: decoded?.role ?? role,
        };
        set({ user, isAuthenticated: true, isHydrating: false });
      },

      hydrate: async () => {
        const access = tokenStorage.getAccessToken();
        if (!access) {
          set({ isHydrating: false, isAuthenticated: false, user: null });
          return;
        }
        const decoded = decodeJwt(access);
        if (!decoded) {
          tokenStorage.clear();
          set({ isHydrating: false, isAuthenticated: false, user: null });
          return;
        }
        set({
          user: {
            userId: decoded.userId,
            workspaceId: decoded.workspaceId,
            // 2026-06-22 — le JWT ne porte pas le code workspace : on conserve
            // celui deja persiste (zustand persist restaure user avant hydrate).
            workspaceCode: get().user?.workspaceCode,
            email: decoded.email,
            role: decoded.role,
          },
          isAuthenticated: true,
          isHydrating: false,
        });
      },

      logout: async () => {
        const refresh = tokenStorage.getRefreshToken();
        try {
          await authService.logout(refresh ?? undefined);
        } catch {
          /* ignore */
        }
        tokenStorage.clear();
        set({ user: null, isAuthenticated: false });
      },
    }),
    {
      name: 'jurika-auth',
      storage: createJSONStorage(() => localStorage),
      partialize: (state) => ({ user: state.user, isAuthenticated: state.isAuthenticated }),
    },
  ),
);

export function useCurrentUser(): User | null {
  return useAuthStore((s) => s.user);
}

export function useHasRole(...roles: Role[]): boolean {
  return useAuthStore((s) => s.user != null && roles.includes(s.user.role));
}
