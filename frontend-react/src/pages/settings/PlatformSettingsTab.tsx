import { useEffect, useState } from 'react';
import { Globe } from 'lucide-react';
import { adminService } from '../../services/admin.service';
import { TexteAide } from '../../components/ui/Aide';
import { Card } from '../../components/ui/Card';
import { Skeleton, SqueletteZone } from '../../components/ui/LoadingSkeleton';
import { Message } from '../../components/ui/Message';
import { SectionOnglet } from './EcranOuOnglet';

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
    <SectionOnglet
      titre="Réglages de la plateforme"
      description="Paramètres communs à tous les cabinets, gérés par le super-administrateur."
      aide={
        <TexteAide cle="reglages-plateforme" titre="À quoi sert cet onglet ?">
          <p>
            Il affiche, en lecture seule, les adresses web autorisées à appeler la plateforme. Rien n’est
            à modifier ici ; si un cabinet ne parvient pas à se connecter depuis sa propre adresse, vérifiez
            qu’elle figure dans la liste.
          </p>
        </TexteAide>
      }
    >
      <Card
        title={
          <span className="flex items-center gap-2">
            <Globe className="h-4 w-4 text-fg-subtle" aria-hidden="true" />
            Adresses web autorisées (origines CORS)
          </span>
        }
        description="Liste lue par la passerelle pour accepter les requêtes venant d’autres adresses web que celle de la plateforme (lecture seule ; la liste se règle cabinet par cabinet)."
      >
        {error && (
          <Message ton="erreur">Impossible de charger la liste des adresses autorisées. Rechargez la page.</Message>
        )}

        {!error && origins === null && (
          <SqueletteZone libelle="Chargement des adresses autorisées">
            <div className="flex flex-wrap gap-2">
              {Array.from({ length: 3 }).map((_, i) => (
                <Skeleton key={i} className="h-7 w-48 rounded-md" />
              ))}
            </div>
          </SqueletteZone>
        )}

        {!error && origins !== null && origins.length === 0 && (
          <p className="text-sm text-fg-muted">Aucune adresse supplémentaire autorisée.</p>
        )}

        {!error && origins !== null && origins.length > 0 && (
          <ul className="flex flex-wrap gap-2">
            {origins.map((o) => (
              <li key={o} className="rounded-md border border-border bg-bg-overlay px-2.5 py-1 font-mono text-xs text-fg">
                {o}
              </li>
            ))}
          </ul>
        )}
      </Card>
    </SectionOnglet>
  );
}
