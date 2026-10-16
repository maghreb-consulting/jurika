/**
 * Lot L6b : chargement a la demande des ecrans, resistant aux coupures et aux redeploiements.
 *
 * Constat (exploration de bout en bout sur le Z440) : un fichier d'ecran qui ne se charge pas
 * (reseau qui change, ou fichiers renommes par un redeploiement alors que la page est ouverte)
 * laissait une page blanche. Ici : deux nouveaux essais espaces, puis, si l'erreur persiste, un
 * seul rechargement de la page (qui recupere les nouveaux fichiers) ; un second echec juste apres
 * remonte a l'ErrorBoundary (pas de boucle).
 */
const CLE = 'jurika.rechargement-module';
const FENETRE_MS = 30_000;
const ESSAIS = 3;
const DELAI_MS = 600;

function estErreurDeModule(e: unknown): boolean {
  const m = e instanceof Error ? `${e.name} ${e.message}` : String(e);
  return /dynamically imported module|Importing a module script failed|ChunkLoadError|Loading chunk|error loading dynamically/i.test(m);
}

function rechargeRecent(): boolean {
  try { return Date.now() - Number(sessionStorage.getItem(CLE) ?? 0) < FENETRE_MS; } catch { return true; }
}

export async function chargerAvecReprise<M>(
  charger: () => Promise<M>,
  options: { recharger?: () => void } = {},
): Promise<M> {
  let derniere: unknown;
  for (let essai = 1; essai <= ESSAIS; essai++) {
    try {
      return await charger();
    } catch (e) {
      if (!estErreurDeModule(e)) throw e;
      derniere = e;
      if (essai < ESSAIS) await new Promise((r) => setTimeout(r, DELAI_MS * essai));
    }
  }
  if (!rechargeRecent()) {
    try { sessionStorage.setItem(CLE, String(Date.now())); } catch { /* stockage indisponible */ }
    (options.recharger ?? (() => window.location.reload()))();
    return new Promise<M>(() => { /* la page se recharge */ });
  }
  throw derniere;
}
