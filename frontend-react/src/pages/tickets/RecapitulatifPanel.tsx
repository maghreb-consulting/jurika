import { useEffect, useState } from 'react';
import { AlertTriangle, CheckCircle, EyeOff, FileText, Loader2 } from 'lucide-react';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { demarcheService } from '../../services/demarche.service';
import { extractError } from '../../lib/api';
import { DOCUMENT_TYPE_LABELS } from '../../types/dataroom';
import type { DocumentType } from '../../types/dataroom';
import type { RecapitulatifCloture } from '../../types/demarche';

/**
 * Lot B (2026-09-11) — LE RÉCAPITULATIF DU TICKET.
 *
 * <p>Deux emplois, une seule vue :
 * <ul>
 *   <li><b>avant de clore</b> — le statut « Clôture de dossier » ne se contente
 *       pas d'un bouton, il montre d'abord ce que le dossier contient ;</li>
 *   <li><b>après</b> — c'est cette même vue que le détail d'un ticket clos
 *       affiche. En lecture seule : une vue de consultation, pas un poste de
 *       travail.</li>
 * </ul>
 *
 * <p>Le panneau n'offre AUCUNE action. Ce n'est pas un oubli : la clôture reste
 * une transition de statut, décidée explicitement depuis le bandeau du ticket.
 * Ce panneau l'éclaire, il ne la déclenche pas.
 *
 * <p>Ce qu'il montre en premier, ce sont les <b>pièces qui manquent</b>. Un
 * récapitulatif qui n'annonce que ce qui est présent laisse clore un dossier
 * troué sans que personne s'en aperçoive.
 */
export function RecapitulatifPanel({ ticketId }: { ticketId: string }) {
  const [recap, setRecap] = useState<RecapitulatifCloture | null>(null);
  const [chargement, setChargement] = useState(true);
  const [erreur, setErreur] = useState<string | null>(null);

  useEffect(() => {
    let vivant = true;
    setChargement(true);
    demarcheService
      .recapitulatif(ticketId)
      .then((r) => {
        if (vivant) {
          setRecap(r);
          setErreur(null);
        }
      })
      .catch((err) => vivant && setErreur(extractError(err).message))
      .finally(() => vivant && setChargement(false));
    return () => {
      vivant = false;
    };
  }, [ticketId]);

  if (chargement) {
    return (
      <Card className="p-5">
        <div className="flex items-center gap-2 text-sm text-fg-subtle">
          <Loader2 className="h-4 w-4 animate-spin" /> Récapitulatif du ticket…
        </div>
      </Card>
    );
  }
  if (erreur) {
    return (
      <Card className="p-5">
        <p className="text-sm text-warning">{erreur}</p>
      </Card>
    );
  }
  // Un workflow sans référentiel chargé — les huit autres — ne rend rien.
  if (!recap || (recap.demarches.length === 0 && recap.justificatifs.length === 0)) {
    return null;
  }

  const identifiants: { libelle: string; valeur: string | null }[] = [
    { libelle: 'Registre du commerce', valeur: recap.identifiants.rcNumero },
    { libelle: 'Identifiant fiscal', valeur: recap.identifiants.identifiantFiscal },
    { libelle: 'ICE', valeur: recap.identifiants.ice },
    { libelle: 'Taxe professionnelle', valeur: recap.identifiants.taxeProfessionnelle },
    { libelle: 'CNSS', valeur: recap.identifiants.cnss },
  ];

  return (
    <Card className="overflow-hidden" data-testid="recapitulatif-ticket">
      <div className="border-b border-border px-5 py-3">
        <h3 className="text-sm font-semibold text-fg">Récapitulatif du ticket</h3>
        <p className="mt-0.5 text-xs text-fg-subtle">
          Consultation seule. La clôture se décide depuis le bandeau du ticket.
        </p>
      </div>

      {/* — Ce qui manque, d'abord ————————————————————————— */}
      <section className="px-5 py-4">
        {recap.manquants.length === 0 ? (
          <p className="flex items-center gap-2 text-sm text-success">
            <CheckCircle className="h-4 w-4" />
            Tous les justificatifs attendus sont archivés.
          </p>
        ) : (
          <div className="rounded-lg border-l-4 border-warning bg-warning/10 p-3">
            <p className="flex items-center gap-2 text-sm font-semibold text-warning">
              <AlertTriangle className="h-4 w-4" />
              {recap.manquants.length} justificatif(s) manquant(s)
            </p>
            <ul className="mt-2 space-y-1 text-xs text-fg" data-testid="justificatifs-manquants">
              {recap.manquants.map((m) => (
                <li key={m}>• {m}</li>
              ))}
            </ul>
          </div>
        )}
      </section>

      {/* — Les identifiants obtenus ——————————————————————— */}
      <section className="border-t border-border px-5 py-4">
        <p className="text-xs font-semibold uppercase tracking-wide text-fg-subtle">
          Identifiants obtenus — {recap.identifiants ? identifiants.filter((i) => i.valeur).length : 0} / 5
        </p>
        <dl className="mt-2 grid grid-cols-1 gap-x-6 gap-y-1 text-sm sm:grid-cols-2">
          {identifiants.map((i) => (
            <div key={i.libelle} className="flex justify-between gap-3">
              <dt className="text-fg-subtle">{i.libelle}</dt>
              <dd className={i.valeur ? 'font-medium text-fg' : 'text-fg-subtle'}>
                {i.valeur ?? '— non obtenu'}
              </dd>
            </div>
          ))}
        </dl>
      </section>

      {/* — Les documents produits ————————————————————————— */}
      <section className="border-t border-border px-5 py-4">
        <p className="text-xs font-semibold uppercase tracking-wide text-fg-subtle">
          Documents produits — {recap.documents.length}
        </p>
        <ul className="mt-2 space-y-1 text-sm">
          {recap.documents.map((d) => (
            <li key={d.id} className="flex items-center gap-2">
              <FileText className="h-3.5 w-3.5 shrink-0 text-fg-subtle" />
              <span className="min-w-0 flex-1 truncate text-fg">{d.titre}</span>
              <Badge variant="info">
                {DOCUMENT_TYPE_LABELS[d.documentType as DocumentType] ?? d.documentType}
              </Badge>
              {!d.visibleClient && (
                <span
                  className="flex items-center gap-1 text-[11px] text-fg-subtle"
                  title="Ce document n'est pas montré au client"
                >
                  <EyeOff className="h-3 w-3" /> masqué
                </span>
              )}
            </li>
          ))}
          {recap.documents.length === 0 && (
            <li className="text-xs text-fg-subtle">Aucun document rattaché à ce ticket.</li>
          )}
        </ul>
      </section>

      {/* — Les démarches accomplies, avec leurs dates —————————— */}
      <section className="border-t border-border px-5 py-4">
        <p className="text-xs font-semibold uppercase tracking-wide text-fg-subtle">
          Démarches accomplies — {recap.demarches.length}
        </p>
        <ul className="mt-2 space-y-1.5 text-sm">
          {recap.demarches.map((d) => (
            <li key={d.ordre} className="flex flex-wrap items-baseline gap-x-2">
              <span className="font-medium text-fg">{d.ordre}.</span>
              <span className="min-w-0 flex-1 text-fg">{d.libelle}</span>
              {d.cocheAt && (
                <span className="tabular-nums text-xs text-fg-subtle">
                  {new Date(d.cocheAt).toLocaleDateString('fr-FR')}
                </span>
              )}
              {d.etat === 'NON_APPLICABLE' && <Badge variant="warning">écartée</Badge>}
              {d.motif && (
                <span className="w-full text-xs italic text-fg-subtle">« {d.motif} »</span>
              )}
            </li>
          ))}
        </ul>
      </section>

      {/* — Ce que le parcours énumère, verbatim ————————————— */}
      {recap.piecesSelonLeParcours && (
        <section className="border-t border-border px-5 py-4">
          <p className="text-xs font-semibold uppercase tracking-wide text-fg-subtle">
            Les pièces à archiver, selon le parcours
          </p>
          <p className="mt-1 text-xs leading-relaxed text-fg-subtle">
            {recap.piecesSelonLeParcours}
          </p>
        </section>
      )}
    </Card>
  );
}
