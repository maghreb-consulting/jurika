import type { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { Clock, LogOut } from 'lucide-react';
import { BrandLogo } from '../ui/BrandLogo';
import { TexteAide } from '../ui/Aide';
import { Button } from '../ui/Button';
import { Message } from '../ui/Message';
import { TitreAccueil } from '../auth/PageAccueil';
import { useContratStore } from '../../store/contratStore';
import { useDeconnexion } from '../layout/useDeconnexion';

/** Mise en page reduite pendant l'attente : logo, deconnexion, contenu. */
export function EcranContratRestreint({ children }: { children: ReactNode }) {
  const viderContrat = useContratStore((s) => s.vider);
  // Lot L7 (E12) : saisies gardees dans ce navigateur envoyees avant la deconnexion.
  const { deconnecter, dialogue } = useDeconnexion(viderContrat);
  return (
    <div className="flex min-h-dvh flex-col bg-bg text-fg">
      <header className="flex h-[var(--hauteur-entete)] items-center justify-between border-b border-border bg-bg-raised px-4 md:px-6">
        <BrandLogo variant="full" size={32} />
        <Button type="button" variant="ghost" size="sm" onClick={deconnecter} icon={<LogOut />}>
          Déconnexion
        </Button>
      </header>
      <main className="flex-1">
        <div className="mx-auto max-w-7xl px-4 py-8 md:px-6">{children}</div>
      </main>
      {dialogue}
    </div>
  );
}

/** Message de l'employe : il attend l'acceptation du superviseur. */
export function AttenteEmploye() {
  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-4 rounded-xl border border-border border-t-2 border-t-accent bg-bg-raised p-6 shadow-card sm:p-8">
      <TitreAccueil icone={<Clock />} ton="attention" titre="Contrat en attente d’acceptation" centre={false} />
      <Message ton="attention">
        Le contrat d’abonnement de votre cabinet attend l’acceptation du superviseur. Tant qu’il ne l’a pas accepté,
        la plateforme n’est pas accessible. Prévenez votre superviseur : dès son acceptation, reconnectez-vous ou
        rechargez cette page pour reprendre votre travail.
      </Message>
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
