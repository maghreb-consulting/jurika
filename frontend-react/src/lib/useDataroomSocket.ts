import { useEffect } from 'react';
import { getSocket } from './realtimeSocket';
import type { DataroomDocumentEvent } from '../types/dataroom';

/**
 * Sprint 7 / TASK 5.3 -- Hook Socket.io pour les events temps reel du
 * Data Room d'un dossier.
 *
 * Le realtime-service (Node.js, port 3000) consomme dataroom.events depuis
 * RabbitMQ et push 'dataroom:document' aux clients connectes a la room
 * "dataroom:{dossierId}".
 *
 * Strategie connexion :
 *   - join la room a l'ouverture
 *   - leave a l'unmount ou changement de dossierId
 *
 * Le callback recoit le payload JSON complet de DataroomDocumentEvent.
 */
export function useDataroomSocket(
  dossierId: string | null | undefined,
  onEvent: (event: DataroomDocumentEvent) => void,
) {
  useEffect(() => {
    if (!dossierId) return;
    const socket = getSocket();
    if (!socket) return;

    const room = `dataroom:${dossierId}`;

    // join la room (le realtime-service repond a 'join' avec un confirm)
    socket.emit('join', { room });

    const handler = (payload: DataroomDocumentEvent) => {
      if (!payload || payload.dossierId !== dossierId) return;
      onEvent(payload);
    };

    socket.on('dataroom:document', handler);

    return () => {
      socket.off('dataroom:document', handler);
      socket.emit('leave', { room });
    };
  }, [dossierId, onEvent]);
}
