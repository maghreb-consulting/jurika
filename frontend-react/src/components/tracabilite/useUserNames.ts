import { useEffect, useState } from 'react';
import { authService } from '../../services/auth.service';
import type { WorkspaceUser } from '../../types/auth';
import { useCurrentUser } from '../../store/authStore';

/**
 * Cache module-level (1 fetch par session) des contacts du workspace. Partage par
 * {@link useUserNames} (resolution id->nom dans les lignes) et {@link useUserContacts}
 * (liste pour les <Select>) afin d'eviter un double appel reseau sur une meme page.
 * Best-effort : en cas d'echec on retombe sur une liste vide et on reessaiera.
 */
let contactsCache: Promise<WorkspaceUser[]> | null = null;

function loadContacts(): Promise<WorkspaceUser[]> {
  if (!contactsCache) {
    contactsCache = authService.listChatContacts().catch(() => {
      contactsCache = null; // permet un retry au prochain mount
      return [];
    });
  }
  return contactsCache;
}

/**
 * Charge une fois la liste des contacts du workspace (employes/superviseurs
 * actifs, scopee par le JWT). Sert a alimenter un <Select> par nom.
 */
export function useUserContacts(): { contacts: WorkspaceUser[]; loading: boolean } {
  const [contacts, setContacts] = useState<WorkspaceUser[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let alive = true;
    loadContacts()
      .then((list) => {
        if (alive) setContacts(list);
      })
      .finally(() => {
        if (alive) setLoading(false);
      });
    return () => {
      alive = false;
    };
  }, []);

  return { contacts, loading };
}

/**
 * Resout un userId (UUID des events audit) en libelle lisible. Charge une fois
 * les contacts du workspace (employes/superviseurs actifs). Fallbacks : "Vous"
 * pour l'utilisateur courant, sinon les 8 premiers caracteres de l'UUID.
 */
export function useUserNames(): (userId: string | null | undefined) => string {
  const current = useCurrentUser();
  const { contacts } = useUserContacts();
  const [names, setNames] = useState<Record<string, string>>({});

  useEffect(() => {
    const map: Record<string, string> = {};
    for (const u of contacts) map[u.userId] = `${u.firstName} ${u.lastName}`.trim();
    setNames(map);
  }, [contacts]);

  return (userId) => {
    if (!userId) return 'Systeme';
    if (current && userId === current.userId) return 'Vous';
    return names[userId] ?? `${userId.slice(0, 8)}…`;
  };
}
