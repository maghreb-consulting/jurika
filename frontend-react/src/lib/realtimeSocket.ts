import { io, Socket } from 'socket.io-client';
import { tokenStorage } from './api';
import { adresseTempsReel } from './realtimeAdresse';

/** Lot L5b (E1) : meme origine que le front par defaut (voir realtimeAdresse.ts). */
export const TEMPS_REEL = adresseTempsReel(
  import.meta.env.VITE_REALTIME_URL as string | undefined,
  typeof window !== 'undefined' ? window.location.origin : '',
);
/** Base REST du service temps reel. */
export const REALTIME_URL = TEMPS_REEL.baseRest;

let socket: Socket | null = null;
let currentToken: string | null = null;

function buildSocket(token: string): Socket {
  return io(TEMPS_REEL.origine, {
    path: TEMPS_REEL.cheminSocket,
    transports: ['websocket', 'polling'],
    auth: { token },
    autoConnect: true,
    reconnection: true,
    // Lot L5b (E1) : reconnexion sans limite, espacee jusqu'a 30 s. Avant, trois
    // tentatives puis plus rien : une coupure breve laissait l'ecran « Hors-ligne »
    // jusqu'au rechargement de la page.
    reconnectionDelay: 2000,
    reconnectionDelayMax: 30_000,
    timeout: 8_000,
  });
}

/**
 * Lazy Socket.io singleton. Reconnects if the underlying access token has changed
 * since the last call (e.g. after a refresh).
 */
export function getSocket(): Socket | null {
  const token = tokenStorage.getAccessToken();
  if (!token) {
    if (socket) {
      socket.removeAllListeners();
      socket.disconnect();
      socket = null;
      currentToken = null;
    }
    return null;
  }

  if (!socket) {
    currentToken = token;
    socket = buildSocket(token);
    return socket;
  }

  if (token !== currentToken) {
    socket.removeAllListeners();
    socket.disconnect();
    currentToken = token;
    socket = buildSocket(token);
  } else if (!socket.connected) {
    socket.connect();
  }

  return socket;
}

export function disconnectSocket(): void {
  if (socket) {
    socket.removeAllListeners();
    socket.disconnect();
    socket = null;
    currentToken = null;
  }
}
