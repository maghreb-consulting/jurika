import { useEffect, useMemo, useState } from 'react';
import { ticketService } from '../../../services/ticket.service';
import { extractError } from '../../../lib/api';
import { InfoBulle, TexteAide } from '../../../components/ui/Aide';
import type { DossierTicket } from '../../../types/dataroom';
import type { Debours } from '../../../types/ticket';
import { CATEGORIE_LABELS } from '../../../types/ticket';

/**
 * Lot L1 (RG-DEB-03) : le client consulte l'etat des debours de chaque operation de
 * son dossier, si ses permissions le prevoient (consultation des documents, RG-CLI-01).
 * Le serveur decide : une operation qui lui est refusee affiche le motif.
 */

type EtatOperation =
  | { type: 'chargement' }
  | { type: 'ok'; items: Debours[]; total: number }
  | { type: 'refus'; message: string };

const mad = (n: number) =>
  new Intl.NumberFormat('fr-MA', { style: 'currency', currency: 'MAD' }).format(n);

export function ClientDebours({ operations }: { operations: DossierTicket[] }) {
  const avecTicket = useMemo(() => operations.filter((o) => o.ticketId), [operations]);
  const [etats, setEtats] = useState<Record<string, EtatOperation>>({});

  useEffect(() => {
    let actif = true;
    for (const op of avecTicket) {
      const id = op.ticketId as string;
      ticketService
        .listDebours(id)
        .then((r) => actif && setEtats((e) => ({ ...e, [id]: { type: 'ok', items: r.items, total: r.total } })))
        .catch((err) => actif && setEtats((e) => ({ ...e, [id]: { type: 'refus', message: extractError(err).message } })));
    }
    return () => {
      actif = false;
    };
  }, [avecTicket]);

  return (
    <section aria-labelledby="debours-client" className="space-y-3">
      <h2 id="debours-client" className="flex items-center gap-1 text-base font-semibold text-fg">
        Débours
        <InfoBulle
          libelle="Que sont les débours ?"
          texte="Les frais avancés par votre cabinet pour votre compte (greffe, annonces légales, timbres…), opération par opération."
        />
      </h2>
      <TexteAide cle="debours-client" titre="Consulter vos débours">
        <p>
          Chaque opération de votre dossier affiche les frais engagés et leur total. Vous pouvez
          télécharger l’état des débours en PDF. Pour toute question sur un montant, adressez une
          demande à votre cabinet depuis l’onglet « Mes Demandes ».
        </p>
      </TexteAide>
      {avecTicket.length === 0 && (
        <p className="text-sm text-fg-subtle">Aucune opération en cours ou terminée pour ce dossier.</p>
      )}
      {avecTicket.map((op) => {
        const id = op.ticketId as string;
        const etat = etats[id] ?? { type: 'chargement' };
        return (
          <article key={id} className="rounded-lg border border-border">
            <header className="flex flex-wrap items-center justify-between gap-2 border-b border-border px-4 py-2">
              <p className="text-sm font-medium text-fg">
                {op.libelle}
                {op.reference ? <span className="ml-2 font-mono text-xs text-fg-subtle">{op.reference}</span> : null}
              </p>
              {etat.type === 'ok' && etat.items.length > 0 && (
                <button
                  type="button"
                  onClick={() => void ticketService.downloadDeboursPdf(id, op.reference ?? id)}
                  className="text-xs font-medium text-accent underline"
                >
                  Télécharger l’état des débours (PDF)
                </button>
              )}
            </header>
            {etat.type === 'chargement' && <p className="px-4 py-3 text-sm text-fg-subtle">Chargement…</p>}
            {etat.type === 'refus' && <p className="px-4 py-3 text-sm text-fg-muted">{etat.message}</p>}
            {etat.type === 'ok' && etat.items.length === 0 && (
              <p className="px-4 py-3 text-sm text-fg-subtle">Aucun débours pour cette opération.</p>
            )}
            {etat.type === 'ok' && etat.items.length > 0 && (
              <table className="w-full text-sm">
                <caption className="sr-only">Débours de l’opération {op.libelle}</caption>
                <thead>
                  <tr className="text-left text-xs text-fg-subtle">
                    <th className="px-4 py-1 font-medium">Libellé</th>
                    <th className="px-4 py-1 font-medium">Catégorie</th>
                    <th className="px-4 py-1 font-medium">Date</th>
                    <th className="px-4 py-1 text-right font-medium">Montant</th>
                  </tr>
                </thead>
                <tbody>
                  {etat.items.map((d) => (
                    <tr key={d.id} className="border-t border-border">
                      <td className="px-4 py-1.5 text-fg">{d.libelle}</td>
                      <td className="px-4 py-1.5 text-fg-muted">{CATEGORIE_LABELS[d.categorie] ?? d.categorie}</td>
                      <td className="px-4 py-1.5 text-fg-muted">{new Date(d.dateEngagement).toLocaleDateString('fr-MA')}</td>
                      <td className="px-4 py-1.5 text-right font-mono text-fg">{mad(d.montantMad)}</td>
                    </tr>
                  ))}
                </tbody>
                <tfoot>
                  <tr className="border-t border-border font-medium">
                    <td colSpan={3} className="px-4 py-1.5 text-fg">Total</td>
                    <td className="px-4 py-1.5 text-right font-mono text-fg">{mad(etat.total)}</td>
                  </tr>
                </tfoot>
              </table>
            )}
          </article>
        );
      })}
    </section>
  );
}
