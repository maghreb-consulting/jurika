import { useEffect } from 'react';
import { ShieldAlert } from 'lucide-react';
import { BrandLogo } from '../../components/ui/BrandLogo';
import { Lien } from '../../components/ui/Lien';

/**
 * Acces refuse (hors coquille : la route est publique). Lot L6b (E9) : jetons du systeme de
 * design, lien qui ressemble a un bouton (et non un bouton dans un lien).
 */
export function ForbiddenPage() {
  useEffect(() => {
    const avant = document.title;
    document.title = 'Accès refusé - JURIKA';
    return () => {
      document.title = avant;
    };
  }, []);

  return (
    <main className="flex min-h-dvh flex-col items-center justify-center gap-4 bg-bg px-4 py-10 text-center text-fg">
      <BrandLogo size={40} />
      <span aria-hidden="true" className="mt-4 flex h-14 w-14 items-center justify-center rounded-full bg-danger-soft text-danger">
        <ShieldAlert className="h-7 w-7" />
      </span>
      <h1 className="text-2xl font-semibold tracking-tight text-heading">Accès refusé</h1>
      <p className="max-w-md text-fg-muted">
        Vous n’avez pas les droits nécessaires pour accéder à cette page. Si vous pensez devoir y
        accéder, demandez-le au responsable de votre cabinet.
      </p>
      <Lien to="/dashboard" commeBouton={{ variant: 'primary' }}>
        Retour au tableau de bord
      </Lien>
    </main>
  );
}
