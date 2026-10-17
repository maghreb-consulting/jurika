import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Clock, Lock } from 'lucide-react';
import { InfoBulle } from '../ui/Aide';
import { useAuthStore } from '../../store/authStore';
import { essaiService, type EtatEssai } from '../../services/essai.service';
import { LIBELLE_FORFAIT_ESSAI, messageEssai } from '../../lib/essai';

/**
 * Lot L7 (E4) : bandeau de l'essai gratuit, sur toutes les pages du cabinet.
 * RG-ESS-08 : rappels a J-3 et J-1 ; RG-ESS-10 : lecture seule et date de
 * suppression definitive (avertissement dans l'application). Invisible hors essai.
 */
export function BandeauEssai() {
  const role = useAuthStore((s) => s.user?.role);
  const [etat, setEtat] = useState<EtatEssai | null>(null);
  const cabinet = role === 'SUPERVISEUR' || role === 'EMPLOYE' || role === 'CLIENT';

  useEffect(() => {
    if (!cabinet) return;
    let actif = true;
    essaiService.etat()
      .then((e) => { if (actif) setEtat(e); })
      .catch(() => { /* bandeau absent : l'etat est rappele par le serveur a chaque refus */ });
    return () => { actif = false; };
  }, [cabinet]);

  if (!etat) return null;
  const m = messageEssai(etat);
  if (!m) return null;
  const Icone = etat.lectureSeule ? Lock : Clock;
  return (
    <div
      role={m.urgent ? 'alert' : 'status'}
      data-testid="bandeau-essai"
      className={`border-b px-6 py-2 text-sm ${m.urgent ? 'border-warning/40 bg-warning/10 text-fg' : 'border-border bg-bg-overlay text-fg-muted'}`}
    >
      <div className="mx-auto flex max-w-7xl flex-wrap items-center gap-x-3 gap-y-1">
        <Icone className="h-4 w-4 shrink-0" aria-hidden="true" />
        <span className="font-medium text-fg">{m.titre}</span>
        {/* Recette R1, RG-ESS-07 : le forfait de l'essai, visible sur toutes les pages. */}
        {etat.statut === 'TRIAL_ACTIVE' && (
          <span className="text-fg" data-testid="bandeau-essai-forfait">
            Forfait : {LIBELLE_FORFAIT_ESSAI}
          </span>
        )}
        <span className="flex items-center gap-1">
          {m.detail}
          <InfoBulle
            libelle="Aide sur l’essai gratuit"
            texte="L’essai donne accès aux fonctions du forfait Professionnel, dans ses limites. Une souscription, pendant ou après l’essai, conserve tout le travail du cabinet. Seul le superviseur peut souscrire."
          />
        </span>
        {role === 'SUPERVISEUR' && (
          <Link to="/app/billing" className="ml-auto font-medium text-accent hover:underline">
            Souscrire un forfait
          </Link>
        )}
      </div>
    </div>
  );
}
