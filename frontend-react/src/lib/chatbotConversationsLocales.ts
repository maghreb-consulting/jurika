/**
 * Lot L5b E2 (RG-COM-12) : les conversations du chatbot sont conservees par le serveur, pour
 * l'utilisateur connecte. L'ancienne version du panneau (lot L5 E8) les gardait dans le
 * navigateur sous ce prefixe : elles y sont purgees a l'ouverture du panneau.
 */
export const ANCIEN_PREFIXE_LOCAL = 'jurika:chatbot:conversations:';

/** Retire du navigateur les conversations qu'y gardait l'ancienne version du panneau. */
export function purgerAnciennesConversationsLocales(): void {
  try {
    const cles: string[] = [];
    for (let i = 0; i < window.localStorage.length; i += 1) {
      const cle = window.localStorage.key(i);
      if (cle?.startsWith(ANCIEN_PREFIXE_LOCAL)) cles.push(cle);
    }
    cles.forEach((cle) => window.localStorage.removeItem(cle));
  } catch {
    // Stockage indisponible : rien a purger.
  }
}
