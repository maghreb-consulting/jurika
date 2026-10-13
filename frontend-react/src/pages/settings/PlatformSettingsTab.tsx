import { useEffect, useState } from 'react';
import { Globe, ServerCog } from 'lucide-react';
import { adminService } from '../../services/admin.service';
import { TexteAide } from '../../components/ui/Aide';

/**
 * Onglet Parametres reserve au SUPER_ADMIN : reglages plateforme.
 *
 * V1 : liste (lecture seule) des origines CORS autorisees, pollee par la
 * gateway toutes les 5 min (RG-SAAS-01/02). Donnees 100% reelles via
 * GET /api/v1/admin/cors-origins ; aucun 403 declenche.
 */
export function PlatformSettingsTab() {
  const [origins, setOrigins] = useState<string[] | null>(null);
  const [error, setError] = useState(false);

  useEffect(() => {
    let mounted = true;
    adminService
      .listCorsOrigins()
      .then((o) => mounted && setOrigins(o))
      .catch(() => mounted && setError(true));
    return () => {
      mounted = false;
    };
  }, []);

  return (
    <div className="space-y-5">
      <div>
        <h2 className="flex items-center gap-2 font-heading text-lg font-semibold text-fg">
          <ServerCog className="h-5 w-5 text-accent" /> Réglages de la plateforme
        </h2>
        <p className="mt-1 text-sm text-fg-muted">
          Paramètres communs à tous les cabinets, gérés par le super-administrateur.
        </p>
      </div>

      <TexteAide cle="reglages-plateforme" titre="À quoi sert cet onglet ?">
        <p>
          Il affiche, en lecture seule, les adresses web autorisées à appeler la plateforme. Rien n’est
          à modifier ici ; si un cabinet ne parvient pas à se connecter depuis sa propre adresse, vérifiez
          qu’elle figure dans la liste.
        </p>
      </TexteAide>

      <div className="rounded-xl border border-border bg-bg-raised p-5">
        <div className="mb-3 flex items-center gap-2">
          <Globe className="h-4 w-4 text-fg-subtle" />
          <h3 className="text-sm font-semibold text-fg">Adresses web autorisées (origines CORS)</h3>
        </div>
        <p className="mb-4 text-xs text-fg-subtle">
          Liste lue par la passerelle pour accepter les requêtes venant d’autres adresses web que celle
          de la plateforme (lecture seule ; la liste se règle cabinet par cabinet).
        </p>

        {error && (
          <p role="alert" className="text-sm text-danger">
            Impossible de charger la liste des adresses autorisées. Rechargez la page.
          </p>
        )}

        {!error && origins === null && (
          <div className="space-y-2">
            {Array.from({ length: 3 }).map((_, i) => (
              <div key={i} className="h-8 animate-pulse rounded-md border border-border bg-bg-overlay" />
            ))}
          </div>
        )}

        {!error && origins !== null && origins.length === 0 && (
          <p className="text-sm text-fg-subtle">Aucune adresse supplémentaire autorisée.</p>
        )}

        {!error && origins !== null && origins.length > 0 && (
          <ul className="flex flex-wrap gap-2">
            {origins.map((o) => (
              <li
                key={o}
                className="rounded-md border border-border bg-bg-overlay px-2.5 py-1 font-mono text-xs text-fg-muted"
              >
                {o}
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
