import { useEffect, useState } from 'react';
import { authService } from '../../services/auth.service';
import type { WorkspaceDirectoryUser } from '../../types/auth';
import { useCurrentUser } from '../../store/authStore';

/**
 * Traçabilité (2026-07-15) — annuaire COMPLET du workspace (internes + clients,
 * tous statuts) pour la page Traçabilité (SUPERVISEUR). Distinct de
 * {@link useUserNames}/{@link useUserContacts} (endpoint `/users/contacts`,
 * internes ACTIVE, accessible EMPLOYE) : ici on interroge
 * `/users/workspace-directory` (SUPERVISEUR only) afin de resoudre TOUS les
 * acteurs d'un event audit — y compris un client ou un compte desactive — et
 * de distinguer les acteurs actifs des acteurs retires.
 *
 * Cache module-level (1 fetch par session), best-effort (echec -> liste vide +
 * retry au prochain mount).
 */
let directoryCache: Promise<WorkspaceDirectoryUser[]> | null = null;

function loadDirectory(): Promise<WorkspaceDirectoryUser[]> {
  if (!directoryCache) {
    directoryCache = authService.listWorkspaceDirectory().catch(() => {
      directoryCache = null; // permet un retry au prochain mount
      return [];
    });
  }
  return directoryCache;
}

export interface WorkspaceDirectory {
  users: WorkspaceDirectoryUser[];
  loading: boolean;
  /**
   * Resout un userId (UUID des events audit) en libelle lisible. Fallbacks :
   * "Systeme" si null, "Vous" pour l'utilisateur courant, sinon
   * "Utilisateur supprime · <id court>" si l'acteur est introuvable (purge).
   * JAMAIS l'UUID brut.
   */
  nameOf: (userId: string | null | undefined) => string;
  /**
   * Un acteur est "retire" s'il est desactive (status INACTIVE) OU introuvable
   * dans l'annuaire (compte purge). null / systeme -> false.
   */
  isRetired: (userId: string | null | undefined) => boolean;
}

export function useWorkspaceDirectory(): WorkspaceDirectory {
  const current = useCurrentUser();
  const [users, setUsers] = useState<WorkspaceDirectoryUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [byId, setById] = useState<Record<string, WorkspaceDirectoryUser>>({});

  useEffect(() => {
    let alive = true;
    loadDirectory()
      .then((list) => {
        if (!alive) return;
        setUsers(list);
        const map: Record<string, WorkspaceDirectoryUser> = {};
        for (const u of list) map[u.userId] = u;
        setById(map);
      })
      .finally(() => {
        if (alive) setLoading(false);
      });
    return () => {
      alive = false;
    };
  }, []);

  const nameOf = (userId: string | null | undefined): string => {
    if (!userId) return 'Systeme';
    if (current && userId === current.userId) return 'Vous';
    const u = byId[userId];
    if (u) return `${u.firstName} ${u.lastName}`.trim() || u.email;
    // Compte purge : jamais l'UUID brut (juste un suffixe court pour distinguer).
    return `Utilisateur supprime · ${userId.slice(0, 8)}`;
  };

  const isRetired = (userId: string | null | undefined): boolean => {
    if (!userId) return false;
    const u = byId[userId];
    if (!u) return true; // introuvable = purge = retire
    return u.status === 'INACTIVE';
  };

  return { users, loading, nameOf, isRetired };
}
