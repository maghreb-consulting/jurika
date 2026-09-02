import { test as base, expect, type APIRequestContext, type Page } from '@playwright/test';

/**
 * Fixtures Playwright JURIKA — Sprint 14 ter TASK E1.
 *
 * - apiClient : helper REST direct sur le gateway (bypass UI pour setup/cleanup rapides)
 * - authenticatedUser : login via API + injection token dans le storage avant goto
 * - seededWorkspace : appelle l'endpoint admin de seed pour creer workspace + user + dossiers deterministes
 *
 * Pre-requis backend (peuvent etre stubbed cote CI) :
 *   POST /api/v1/test/seed/workspace -> cree workspace + admin + 5 dossiers + 10 tickets + 3 echeances
 *   DELETE /api/v1/test/cleanup/{workspaceId} -> teardown
 * Ces endpoints sont actifs uniquement avec le profil Spring "!prod" (cf. SecurityConfig backend a creer en TASK E1).
 */

export interface SeededWorkspace {
  workspaceId: string;
  workspaceCode: string;
  adminEmail: string;
  adminPassword: string;
  cleanup(): Promise<void>;
}

export interface AuthenticatedSession {
  email: string;
  password: string;
  workspaceCode: string;
  accessToken: string;
  refreshToken: string;
}

const API_BASE = process.env.E2E_API_BASE_URL || 'http://localhost:8080';

async function seedWorkspaceViaApi(request: APIRequestContext): Promise<SeededWorkspace> {
  const r = await request.post(`${API_BASE}/api/v1/test/seed/workspace`, {
    data: { profile: 'e2e-default' },
  });
  if (!r.ok()) {
    throw new Error(
      `Seed endpoint /api/v1/test/seed/workspace returned ${r.status()} — backend doit exposer l'endpoint en profil !prod`,
    );
  }
  const body = await r.json();
  return {
    workspaceId: body.workspaceId,
    workspaceCode: body.workspaceCode,
    adminEmail: body.adminEmail,
    adminPassword: body.adminPassword,
    cleanup: async () => {
      await request.delete(`${API_BASE}/api/v1/test/cleanup/${body.workspaceId}`);
    },
  };
}

async function loginViaApi(
  request: APIRequestContext,
  workspaceCode: string,
  email: string,
  password: string,
): Promise<AuthenticatedSession> {
  const r = await request.post(`${API_BASE}/api/v1/auth/login`, {
    data: { workspaceCode, email, password },
  });
  if (!r.ok()) {
    throw new Error(`Login failed for ${email} : ${r.status()} ${await r.text()}`);
  }
  const body = await r.json();
  return {
    email,
    password,
    workspaceCode,
    accessToken: body.accessToken,
    refreshToken: body.refreshToken,
  };
}

async function injectTokensInPage(page: Page, session: AuthenticatedSession): Promise<void> {
  await page.addInitScript((s) => {
    window.localStorage.setItem('jurika.accessToken', s.accessToken);
    window.localStorage.setItem('jurika.refreshToken', s.refreshToken);
    window.localStorage.setItem('jurika.workspaceCode', s.workspaceCode);
  }, session);
}

type Fixtures = {
  apiClient: APIRequestContext;
  seededWorkspace: SeededWorkspace;
  authenticatedUser: AuthenticatedSession;
};

export const test = base.extend<Fixtures>({
  apiClient: async ({ request }, use) => {
    await use(request);
  },
  seededWorkspace: async ({ request }, use) => {
    const ws = await seedWorkspaceViaApi(request);
    await use(ws);
    await ws.cleanup();
  },
  authenticatedUser: async ({ request, page, seededWorkspace }, use) => {
    const session = await loginViaApi(
      request,
      seededWorkspace.workspaceCode,
      seededWorkspace.adminEmail,
      seededWorkspace.adminPassword,
    );
    await injectTokensInPage(page, session);
    await use(session);
  },
});

export { expect };
