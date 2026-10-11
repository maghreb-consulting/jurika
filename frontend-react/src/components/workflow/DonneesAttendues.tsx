import { useEffect, useRef, useState } from 'react';
import { InfoBulle } from '../ui/Aide';
import { extractError } from '../../lib/api';
import { workflowService, type DonneeAttendue } from '../../services/workflow.service';

/** Identifiants de la societe : ils se saisissent dans sa fiche, source unique (RG-VAR-08). */
const DE_LA_FICHE = new Set(['ICE', 'IDENTIFIANT_FISCAL', 'IDENTIFIANT_TP', 'CNSS_NUMERO', 'RC_NUMERO']);

/**
 * Lot L3 (regle des variables) : les donnees qu'un document attend d'un organisme.
 *
 * La plateforme les a reclamees a la generation (l'acte porte "A OBTENIR" a leur
 * place). Quand TOUTES sont arrivees (saisies ici ou a la fiche de la societe), le
 * document est regenere d'office, une fois, sans autre geste de l'employe.
 *
 * Lot L4a (E1d) : chaque donnee attendue hors fiche se SAISIT ici (avant, la date et le
 * numero du depot legal n'avaient aucun ecran : l'acte ne pouvait jamais se regenerer).
 */
export function DonneesAttendues({
  attendues = [],
  enCours = false,
  onRegenerer,
  ticketId,
}: {
  attendues?: DonneeAttendue[];
  enCours?: boolean;
  onRegenerer?: () => void | Promise<void>;
  /** Sans ticket (apercu), la saisie n'est pas proposee. */
  ticketId?: string | null;
}) {
  const dejaRegenere = useRef(false);
  // Liste mise a jour par une saisie ; elle cede la place a la liste du parent des que
  // celui-ci en fournit une nouvelle (etat derive, sans effet).
  const [maj, setMaj] = useState<{ source: DonneeAttendue[]; liste: DonneeAttendue[] } | null>(null);
  const liste = maj && maj.source === attendues ? maj.liste : attendues;
  const [valeurs, setValeurs] = useState<Record<string, string>>({});
  const [erreur, setErreur] = useState<string | null>(null);
  const [envoi, setEnvoi] = useState<string | null>(null);

  const toutesRecues = liste.length > 0 && liste.every((a) => a.recue);

  useEffect(() => {
    if (toutesRecues && !enCours && onRegenerer && !dejaRegenere.current) {
      dejaRegenere.current = true;
      void onRegenerer();
    }
  }, [toutesRecues, enCours, onRegenerer]);

  async function enregistrer(a: DonneeAttendue) {
    const valeur = (valeurs[a.variable] ?? '').trim();
    if (!ticketId || !valeur) return;
    setEnvoi(a.variable);
    setErreur(null);
    try {
      const reponse = await workflowService.saisirDonneeAttendue(ticketId, a.variable, valeur);
      setMaj({ source: attendues, liste: reponse.filter((x) => x.templateCode === a.templateCode) });
    } catch (err) {
      setErreur(extractError(err).message);
    } finally {
      setEnvoi(null);
    }
  }

  if (liste.length === 0) return null;
  return (
    <div className="rounded-lg border border-border bg-bg-overlay p-3 text-xs text-fg">
      <p className="flex items-center gap-1 font-medium">
        Données attendues d’un organisme
        <InfoBulle
          libelle="Comment l’acte est-il complété ?"
          texte="Saisissez la donnée dès que l’organisme la délivre : ici, ou dans « Identifiants de la société » (Data Room) pour le RC, l’ICE, l’identifiant fiscal, la taxe professionnelle et la CNSS. Quand toutes les données attendues sont arrivées, l’acte est régénéré automatiquement."
        />
      </p>
      <ul className="mt-1 space-y-1">
        {liste.map((a) => (
          <li key={a.variable} className="flex flex-wrap items-center gap-2">
            <span className={a.recue ? 'text-success' : 'text-warning'}>{a.recue ? 'Reçue' : 'En attente'}</span>
            <span>{a.libelle}</span>
            {!a.recue && ticketId && !DE_LA_FICHE.has(a.variable) && (
              <span className="flex items-center gap-1">
                <input
                  aria-label={a.libelle}
                  type={a.variable.startsWith('DATE_') ? 'date' : 'text'}
                  className="rounded border border-border bg-bg-raised px-2 py-1 text-xs"
                  value={valeurs[a.variable] ?? ''}
                  onChange={(e) => setValeurs((v) => ({ ...v, [a.variable]: e.target.value }))}
                />
                <button
                  type="button"
                  disabled={envoi === a.variable || !(valeurs[a.variable] ?? '').trim()}
                  onClick={() => void enregistrer(a)}
                  className="rounded-md border border-accent/40 px-2 py-1 text-accent hover:bg-accent/10 disabled:opacity-50"
                >
                  Enregistrer
                </button>
              </span>
            )}
            {!a.recue && DE_LA_FICHE.has(a.variable) && (
              <span className="text-fg-subtle">À saisir dans « Identifiants de la société » (Data Room).</span>
            )}
          </li>
        ))}
      </ul>
      {erreur && <p role="alert" className="mt-1 text-danger">{erreur}</p>}
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
