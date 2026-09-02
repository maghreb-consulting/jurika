import { io, Socket } from 'socket.io-client';
import { tokenStorage } from './api';

export const REALTIME_URL =
  (import.meta.env.VITE_REALTIME_URL as string | undefined) ?? 'http://localhost:3000';

let socket: Socket | null = null;
let currentToken: string | null = null;

function buildSocket(token: string): Socket {
  return io(REALTIME_URL, {
    path: '/socket.io',
    transports: ['websocket', 'polling'],
    auth: { token },
    autoConnect: true,
    reconnection: true,
    // 2026-06-22 — le service temps réel (backend-node, port 3000) est optionnel.
    // On limite les tentatives pour ne pas inonder la console d'erreurs réseau
    // quand il n'est pas démarré (au lieu d'un retry infini).
    reconnectionAttempts: 3,
    reconnectionDelay: 2000,
    reconnectionDelayMax: 10_000,
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
