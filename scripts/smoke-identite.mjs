/**
 * Identite du cabinet cree par le test de fumee (scripts/smoke-test.mjs).
 *
 * Recette R1 (point 2) : toutes les adresses sont dans le domaine reserve @jurika.test. Avant, le
 * courriel de bienvenue partait a la vraie boite SMOKE_CONTACT_EMAIL (sous-adresse
 * "+smoke-<horodatage>") : "Bonjour Smoke", meme sujet que le vrai courriel de bienvenue (regroupe
 * avec lui dans la meme conversation par la messagerie), code d'un cabinet supprime a la fin du
 * test. SMOKE_CONTACT_EMAIL n'est plus lu. Le controle de l'envoi reste le meme : courriel accepte
 * par le relais (verifyEmailSent de la reponse d'inscription).
 */
export function identiteDeFumee(ts) {
  return {
    workspaceName: `SmokeTest Cabinet ${ts}`,
    contactEmail: `contact+smoke-${ts}@jurika.test`,
    firstName: 'Smoke',
    lastName: 'Test',
    email: `admin+smoke-${ts}@jurika.test`,
  };
}
