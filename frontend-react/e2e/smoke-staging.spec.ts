import { test, expect } from '@playwright/test';

/**
 * Smoke staging — Sprint 14 ter F5.
 * Lance par le workflow .github/workflows/deploy-staging.yml step "smoke".
 * Env requis : E2E_BASE_URL=https://staging.jurika.ma, SMOKE_PASSWORD.
 *
 * Verifie :
 *   1. Healthcheck du gateway + des services backend principaux
 *   2. Page d'accueil charge avec bundle React monte
 *   3. Login via API (compte smoke pre-seede) + dashboard accessible
 *
 * Sur echec : workflow declenche rollback-on-failure job qui appelle scripts/rollback-staging.sh
 */

const SERVICES_HEALTH_PATHS = [
  '/actuator/health',
  '/auth/actuator/health',
  '/ticket/actuator/health',
  '/workflow/actuator/health',
  '/dataroom/actuator/health',
  '/dashboard/actuator/health',
];

test.describe('staging smoke @smoke-staging', () => {
  test('gateway + services repondent UP', async ({ request, baseURL }) => {
    expect(baseURL).toBeTruthy();
    for (const path of SERVICES_HEALTH_PATHS) {
      const r = await request.get(`${baseURL}${path}`);
      expect(r.status(), `${path} should be 200`).toBe(200);
      const body = await r.json();
      expect(body.status, `${path} should report UP`).toBe('UP');
    }
  });

  test('homepage charge le bundle React', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveTitle(/JURIKA/i);
    await expect(page.locator('#root')).not.toBeEmpty();
  });

  test('login smoke + acces dashboard', async ({ page, request, baseURL }) => {
    const password = process.env.SMOKE_PASSWORD;
    test.skip(!password, 'SMOKE_PASSWORD non defini, skip login smoke');

    const r = await request.post(`${baseURL}/api/v1/auth/login`, {
      data: {
        workspaceCode: 'SMOKE-TEST-WORKSPACE',
        email: 'smoke-test@maghreb-consulting.ma',
        password,
      },
    });
    expect(r.status(), 'login API doit etre 200').toBe(200);
    const { accessToken, refreshToken } = await r.json();
    expect(accessToken).toBeTruthy();

    await page.addInitScript(
      ({ at, rt }) => {
        window.localStorage.setItem('jurika.accessToken', at);
        window.localStorage.setItem('jurika.refreshToken', rt);
        window.localStorage.setItem('jurika.workspaceCode', 'SMOKE-TEST-WORKSPACE');
      },
      { at: accessToken, rt: refreshToken },
    );
    await page.goto('/dashboard');
    await expect(page.locator('[data-testid=dashboard]')).toBeVisible({ timeout: 15_000 });
  });
});
