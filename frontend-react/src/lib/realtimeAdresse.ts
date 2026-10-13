/**
 * Lot L5b (E1, RG-COM-06) : adresse du service temps reel (messagerie, notifications).
 *
 * Par defaut, la MEME origine que le front : nginx relaie /temps-reel/ vers le service
 * (WebSocket compris). Aucune adresse ni aucun port a configurer, et jamais localhost :
 * sur le Z440, la variable lue au build manquait (.env.local portait VITE_WS_URL) et le
 * front visait http://localhost:3000, c'est-a-dire la machine de chaque utilisateur.
 * Une adresse explicite (VITE_REALTIME_URL) reste possible, en developpement avec Vite.
 */
export interface AdresseTempsReel {
  /** Origine passee a socket.io (sans chemin : un chemin y serait pris pour un espace de noms). */
  origine: string;
  /** Chemin du transport socket.io. */
  cheminSocket: string;
  /** Base des routes REST du service (historique, envoi, notifications). */
  baseRest: string;
}

export function adresseTempsReel(explicite: string | undefined, origineDuFront: string): AdresseTempsReel {
  const v = (explicite ?? '').trim().replace(/\/+$/, '');
  if (v) {
    return { origine: v, cheminSocket: '/socket.io', baseRest: `${v}/api/v1` };
  }
  const o = origineDuFront.replace(/\/+$/, '');
  return { origine: o, cheminSocket: '/temps-reel/socket.io', baseRest: `${o}/temps-reel/api/v1` };
}
