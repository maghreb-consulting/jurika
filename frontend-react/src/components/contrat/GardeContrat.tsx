import type { ReactNode } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { Clock, LogOut } from 'lucide-react';
import { BrandLogo } from '../ui/BrandLogo';
import { TexteAide } from '../ui/Aide';
import { useAuthStore } from '../../store/authStore';
import { useContratStore } from '../../store/contratStore';

/** Mise en page reduite pendant l'attente : logo, deconnexion, contenu. */
export function EcranContratRestreint({ children }: { children: ReactNode }) {
  const logout = useAuthStore((s) => s.logout);
  const viderContrat = useContratStore((s) => s.vider);
  const navigate = useNavigate();
  async function deconnecter() {
    viderContrat();
    await logout();
    navigate('/login', { replace: true });
  }
  return (
    <div className="flex min-h-screen flex-col bg-bg text-fg">
      <header className="flex items-center justify-between border-b border-border px-6 py-3">
        <BrandLogo variant="full" size={32} />
        <button
          type="button"
          onClick={deconnecter}
          className="inline-flex items-center gap-1 text-sm text-fg-muted hover:text-fg"
        >
          <LogOut className="h-4 w-4" aria-hidden="true" /> Déconnexion
        </button>
      </header>
      <main className="flex-1">
        <div className="mx-auto max-w-7xl px-6 py-8">{children}</div>
      </main>
    </div>
  );
}

/** Message de l'employe : il attend l'acceptation du superviseur. */
export function AttenteEmploye() {
  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-4">
      <h1 className="flex items-center gap-2 font-heading text-2xl font-semibold text-fg">
        <Clock className="h-6 w-6 text-accent" aria-hidden="true" /> Contrat en attente d’acceptation
      </h1>
      <p className="text-sm text-fg-muted">
        Le contrat d’abonnement de votre cabinet attend l’acceptation du superviseur. Tant qu’il ne l’a pas accepté,
        la plateforme n’est pas accessible. Prévenez votre superviseur : dès son acceptation, reconnectez-vous ou
        rechargez cette page pour reprendre votre travail.
      </p>
      <TexteAide cle="contrat-attente-employe" titre="Pourquoi cet écran ?">
        <p>
          Chaque abonnement (ou changement de forfait) donne lieu à de nouvelles conditions particulières, que seul le
          superviseur du cabinet peut accepter en ligne. Aucune donnée n’est perdue pendant l’attente.
        </p>
      </TexteAide>
    </div>
  );
}

/** Redirection du superviseur vers l'ecran du contrat. */
export function VersContrat() {
  return <Navigate to="/app/contrat" replace />;
}
