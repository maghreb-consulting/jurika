import { useEffect, useMemo, useState } from 'react';
import { Download, Paperclip, Receipt } from 'lucide-react';
import { Button } from '../../../components/ui/Button';
import { Card } from '../../../components/ui/Card';
import { Message } from '../../../components/ui/Message';
import { EmptyState } from '../../../components/ui/EmptyState';
import { Skeleton } from '../../../components/ui/LoadingSkeleton';
import { ticketService } from '../../../services/ticket.service';
import { dataroomService } from '../../../services/dataroom.service';
import { extractError } from '../../../lib/api';
import { InfoBulle, TexteAide } from '../../../components/ui/Aide';
import type { DossierTicket } from '../../../types/dataroom';
import type { Debours } from '../../../types/ticket';
import { CATEGORIE_LABELS } from '../../../types/ticket';

/**
 * Lot L1 puis lot L5 (E6) (RG-DEB-03) : le client consulte l'etat des debours de chaque
 * operation de son dossier si ses permissions le prevoient (consultation des documents,
 * RG-CLI-01) ; il telecharge l'etat (PDF, lot L5b E3) et les pieces justificatives si la permission de
 * telecharger le prevoit. Le serveur decide : un refus affiche son motif.
 */

type EtatOperation =
  | { type: 'chargement' }
  | { type: 'ok'; items: Debours[]; total: number }
  | { type: 'refus'; message: string };

const mad = (n: number) =>
  new Intl.NumberFormat('fr-MA', { style: 'currency', currency: 'MAD' }).format(n);

export function ClientDebours({ operations, canDownload }: { operations: DossierTicket[]; canDownload: boolean }) {
  const avecTicket = useMemo(() => operations.filter((o) => o.ticketId), [operations]);
  const [etats, setEtats] = useState<Record<string, EtatOperation>>({});
  const [erreur, setErreur] = useState<string | null>(null);

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

  function telecharger(action: () => Promise<void>) {
    setErreur(null);
    action().catch((e) => setErreur(extractError(e).message));
  }

  return (
    <section aria-labelledby="debours-client" className="space-y-4">
      <h2 id="debours-client" className="flex items-center gap-1 text-base font-semibold text-heading">
        Débours
        <InfoBulle
          libelle="Que sont les débours ?"
          texte="Les frais avancés par votre cabinet pour votre compte (greffe, annonces légales, timbres…), opération par opération, avec la pièce qui justifie chacun d’eux."
        />
      </h2>
      <TexteAide cle="debours-client" titre="Consulter vos débours">
        <p>
          Chaque opération de votre dossier affiche les frais engagés, leur total et leurs pièces
          justificatives. {canDownload
            ? 'Vous pouvez télécharger l’état des débours (document PDF) et chaque pièce.'
            : 'Le téléchargement n’est pas ouvert sur votre accès : demandez-le à votre cabinet.'}{' '}
          Pour toute question sur un montant, adressez une demande à votre cabinet depuis l’onglet
          « Demandes du client ».
        </p>
      </TexteAide>
      {erreur && <Message ton="erreur">{erreur}</Message>}
      {avecTicket.length === 0 && (
        <Card padding={false}>
          <EmptyState
            compact
            icon={<Receipt />}
            title="Aucune opération pour ce dossier"
            description="Les frais avancés par votre cabinet apparaîtront ici dès qu’une opération sera ouverte."
          />
        </Card>
      )}
      {avecTicket.map((op) => {
        const id = op.ticketId as string;
        const etat = etats[id] ?? { type: 'chargement' };
        return (
          <article key={id} className="@container overflow-hidden rounded-xl border border-border bg-bg-raised shadow-card">
            <header className="flex flex-wrap items-center justify-between gap-2 border-b border-border px-5 py-3">
              <p className="text-sm font-semibold text-heading">
                {op.libelle}
                {op.reference ? <span className="ml-2 font-mono text-xs font-normal text-fg-muted">{op.reference}</span> : null}
              </p>
              {etat.type === 'ok' && etat.items.length > 0 && canDownload && (
                <Button
                  type="button"
                  size="sm"
                  variant="secondary"
                  icon={<Download />}
                  onClick={() => telecharger(() => ticketService.downloadEtatDebours(id, op.reference ?? id))}
                >
                  Télécharger l’état des débours (PDF)
                </Button>
              )}
            </header>
            {etat.type === 'chargement' && (
              <div className="space-y-2 px-5 py-3" aria-busy="true" aria-label="Chargement des débours">
                <Skeleton className="h-4 w-full" />
                <Skeleton className="h-4 w-2/3" />
              </div>
            )}
            {etat.type === 'refus' && <p className="px-5 py-3 text-sm text-fg-muted">{etat.message}</p>}
            {etat.type === 'ok' && etat.items.length === 0 && (
              <p className="px-5 py-3 text-sm text-fg-muted">Aucun débours pour cette opération.</p>
            )}
            {etat.type === 'ok' && etat.items.length > 0 && (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <caption className="sr-only">Débours de l’opération {op.libelle}</caption>
                  <thead className="bg-bg-overlay">
                    <tr className="text-left text-xs text-fg-muted">
                      <th scope="col" className="px-5 py-2 font-medium">Libellé</th>
                      <th scope="col" className="hidden px-3 py-2 font-medium @md:table-cell">Type</th>
                      <th scope="col" className="px-3 py-2 font-medium">Date</th>
                      <th scope="col" className="px-3 py-2 font-medium">Pièce justificative</th>
                      <th scope="col" className="px-5 py-2 text-right font-medium">Montant</th>
                    </tr>
                  </thead>
                  <tbody>
                    {etat.items.map((d) => (
                      <tr key={d.id} className="border-t border-border">
                        <td className="px-5 py-2 text-fg">{d.libelle}</td>
                        <td className="hidden px-3 py-2 text-fg-muted @md:table-cell">{CATEGORIE_LABELS[d.categorie] ?? d.categorie}</td>
                        <td className="whitespace-nowrap px-3 py-2 text-fg-muted">{new Date(d.dateEngagement).toLocaleDateString('fr-MA')}</td>
                        <td className="px-3 py-2 text-fg-muted">
                          {d.pieceJointeFilename && d.pieceDocumentId && canDownload ? (
                            <Button
                              type="button"
                              size="sm"
                              variant="ghost"
                              icon={<Paperclip />}
                              onClick={() => telecharger(() => dataroomService.downloadDocument(
                                d.pieceDocumentId as string, d.pieceJointeFilename as string))}
                            >
                              {d.pieceJointeFilename}
                            </Button>
                          ) : (
                            d.pieceJointeFilename ?? 'Aucune'
                          )}
                        </td>
                        <td className="whitespace-nowrap px-5 py-2 text-right tabular-nums text-fg">{mad(d.montantMad)}</td>
                      </tr>
                    ))}
                  </tbody>
                  <tfoot>
                    <tr className="border-t border-border bg-bg-overlay font-semibold">
                      <td colSpan={4} className="px-5 py-2 text-fg">Total</td>
                      <td className="whitespace-nowrap px-5 py-2 text-right tabular-nums text-fg">{mad(etat.total)}</td>
                    </tr>
                  </tfoot>
                </table>
              </div>
            )}
          </article>
        );
      })}
    </section>
  );
}
