import { useEffect, useRef } from 'react';
import { InfoBulle } from '../ui/Aide';
import type { DonneeAttendue } from '../../services/workflow.service';

/**
 * Lot L3 (regle des variables) : les donnees qu'un document attend d'un organisme.
 *
 * La plateforme les a reclamees a la generation (l'acte porte « À OBTENIR » a leur
 * place). Quand TOUTES sont arrivees (saisies au ticket ou a la fiche de la societe),
 * le document est regenere d'office, une fois, sans autre geste de l'employe.
 */
export function DonneesAttendues({
  attendues = [],
  enCours = false,
  onRegenerer,
}: {
  attendues?: DonneeAttendue[];
  enCours?: boolean;
  onRegenerer?: () => void | Promise<void>;
}) {
  const dejaRegenere = useRef(false);
  const toutesRecues = attendues.length > 0 && attendues.every((a) => a.recue);

  useEffect(() => {
    if (toutesRecues && !enCours && onRegenerer && !dejaRegenere.current) {
      dejaRegenere.current = true;
      void onRegenerer();
    }
  }, [toutesRecues, enCours, onRegenerer]);

  if (attendues.length === 0) return null;
  return (
    <div className="rounded-lg border border-border bg-bg-overlay p-3 text-xs text-fg">
      <p className="flex items-center gap-1 font-medium">
        Données attendues d’un organisme
        <InfoBulle
          libelle="Comment l’acte est-il complété ?"
          texte="Saisissez la donnée dès que l’organisme la délivre : au ticket, ou dans « Identifiants de la société » (Data Room) pour le RC, l’ICE, l’identifiant fiscal, la taxe professionnelle et la CNSS. Quand toutes les données attendues sont arrivées, l’acte est régénéré automatiquement."
        />
      </p>
      <ul className="mt-1 space-y-0.5">
        {attendues.map((a) => (
          <li key={a.variable} className="flex items-center gap-2">
            <span className={a.recue ? 'text-success' : 'text-warning'}>{a.recue ? 'Reçue' : 'En attente'}</span>
            <span>{a.libelle}</span>
          </li>
        ))}
      </ul>
      {toutesRecues && (
        <p role="status" className="mt-1 text-success">
          {enCours
            ? 'Toutes les données sont arrivées : régénération de l’acte en cours…'
            : 'Toutes les données sont arrivées : l’acte a été régénéré avec elles.'}
        </p>
      )}
    </div>
  );
}
