import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { AlertTriangle, Lock } from 'lucide-react';
import { InfoBulle } from '../ui/Aide';
import { useAuthStore } from '../../store/authStore';
import { billingService } from '../../services/billing.service';
import { messageImpaye } from '../../lib/impaye';
import type { ImpayeVue } from '../../types/billing';

/**
 * Lot L7 (E8), article 4.4 du contrat : bandeau d'une échéance impayée, sur toutes les pages du
 * cabinet (superviseur et employés). Il dit la date de la suspension et comment payer ; pendant la
 * suspension, il dit que l'accès est rétabli dès le paiement constaté. Invisible sans impayé.
 */
export function BandeauImpaye() {
  const role = useAuthStore((s) => s.user?.role);
  const [impaye, setImpaye] = useState<ImpayeVue | null>(null);
  const cabinet = role === 'SUPERVISEUR' || role === 'EMPLOYE';
  const superviseur = role === 'SUPERVISEUR';

  useEffect(() => {
    if (!cabinet) return;
    let actif = true;
    billingService.getAbonnement()
      .then((a) => { if (actif) setImpaye(a.impaye ?? null); })
      .catch(() => { /* bandeau absent : l'écran Facturation et le refus du serveur disent l'état */ });
    return () => { actif = false; };
  }, [cabinet]);

  const m = messageImpaye(impaye, superviseur);
  if (!impaye || !m) return null;
  const Icone = impaye.suspendu ? Lock : AlertTriangle;
  return (
    <div role="alert" data-testid="bandeau-impaye" className="border-b border-warning/40 bg-warning/10 px-6 py-2 text-sm text-fg">
      <div className="mx-auto flex max-w-7xl flex-wrap items-center gap-x-3 gap-y-1">
        <Icone className="h-4 w-4 shrink-0" aria-hidden="true" />
        <span className="font-medium">{m.titre}</span>
        <span className="flex items-center gap-1 text-fg-muted">
          {m.detail}
          <InfoBulle
            libelle="Aide sur l’échéance impayée"
            texte={`Article 4.4 du contrat d’abonnement : sans paiement ${impaye.delaiJours} jours après l’échéance, et après un rappel resté sans effet, l’accès au cabinet est suspendu. Seul le superviseur peut alors se connecter, pour payer. Aucune donnée n’est supprimée et l’accès est rétabli dès le paiement constaté.`}
          />
        </span>
        {superviseur && (
          <Link to="/app/billing/payment?objet=ECHEANCE" data-testid="bandeau-impaye-payer" className="ml-auto font-medium text-accent hover:underline">
            Payer l’échéance
          </Link>
        )}
      </div>
    </div>
  );
}
