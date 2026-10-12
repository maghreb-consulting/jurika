import { useEffect, useState } from 'react';
import { FileDown, Pencil } from 'lucide-react';
import { Drawer } from '../../../components/ui/Drawer';
import { Button } from '../../../components/ui/Button';
import { InfoBulle, TexteAide } from '../../../components/ui/Aide';
import { dataroomService } from '../../../services/dataroom.service';
import { extractError } from '../../../lib/api';
import type {
  FicheClientVue,
  FicheDossierVue,
  IdentifiantFiche,
} from '../../../types/dataroom';

/**
 * Lot L5 (E7) : fiche client et fiche dossier (cahier des charges 10.2 et 10.3).
 *
 * - Fiche client : les identifiants de la societe en vigueur et leur provenance
 *   (RG-FIC-01, 03), les versions de la taxe professionnelle (RG-FIC-02), la trace des
 *   modifications (RG-FIC-04).
 * - Fiche dossier : les memes identifiants et toutes les operations du dossier (RG-FIC-06).
 * - Generation Word a la demande (P5), conservee dans la Data Room quand l'employe
 *   responsable la produit (versions successives).
 */

type Onglet = 'client' | 'dossier';

interface Props {
  open: boolean;
  dossierId: string;
  raisonSociale: string;
  onClose: () => void;
  /** Ouvre le formulaire des identifiants (employe responsable). */
  onModifier: () => void;
  /** Change a chaque enregistrement des identifiants : la fiche se relit. */
  versionDonnees?: number;
}

const formatDate = (iso: string | null) =>
  iso ? new Date(iso).toLocaleDateString('fr-MA', { timeZone: 'Africa/Casablanca' }) : '—';

export function FichesSocieteDrawer({
  open,
  dossierId,
  raisonSociale,
  onClose,
  onModifier,
  versionDonnees = 0,
}: Props) {
  const [onglet, setOnglet] = useState<Onglet>('client');
  const [vue, setVue] = useState<FicheDossierVue | null>(null);
  const [erreur, setErreur] = useState<string | null>(null);
  const [generation, setGeneration] = useState<Onglet | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  // Relue a chaque ouverture et apres chaque enregistrement des identifiants.
  useEffect(() => {
    if (!open) return undefined;
    let actif = true;
    dataroomService
      .ficheDossier(dossierId)
      .then((v) => {
        if (!actif) return;
        setVue(v);
        setErreur(null);
      })
      .catch((err: unknown) => {
        if (actif) setErreur(extractError(err).message);
      });
    return () => {
      actif = false;
    };
  }, [open, dossierId, versionDonnees]);

  async function generer(type: Onglet) {
    setGeneration(type);
    setMessage(null);
    setErreur(null);
    try {
      const r = await dataroomService.genererFicheWord(
        dossierId,
        type === 'client' ? 'fiche-client' : 'fiche-dossier',
      );
      const nom = type === 'client' ? 'La fiche client' : 'La fiche dossier';
      setMessage(
        r.conservee
          ? `${nom} a été téléchargée et conservée dans la Data Room du dossier (version ${r.version ?? '—'}).`
          : `${nom} a été téléchargée. Elle n’est pas conservée dans la Data Room : seul l’employé responsable conserve les fiches, et une société archivée n’en reçoit plus.`,
      );
    } catch (err) {
      setErreur(extractError(err).message);
    } finally {
      setGeneration(null);
    }
  }

  const fiche: FicheClientVue | null = vue?.fiche ?? null;
  const manquants = fiche ? fiche.identifiants.filter((i) => i.valeur === null) : [];

  return (
    <Drawer
      open={open}
      onClose={onClose}
      title="Fiche client et fiche dossier"
      subtitle={raisonSociale}
      width="xl"
      footer={
        <div className="flex flex-wrap items-center justify-end gap-2">
          {fiche?.modifiable && (
            <Button variant="secondary" size="sm" onClick={onModifier} data-testid="fiche-modifier">
              <Pencil className="mr-1 h-4 w-4" />
              Modifier les identifiants
            </Button>
          )}
          <Button
            size="sm"
            onClick={() => void generer(onglet)}
            loading={generation === onglet}
            disabled={!vue}
            data-testid="fiche-generer"
          >
            <FileDown className="mr-1 h-4 w-4" />
            {onglet === 'client' ? 'Générer la fiche client (Word)' : 'Générer la fiche dossier (Word)'}
          </Button>
        </div>
      }
    >
      <TexteAide cle="fiches-societe" titre="À quoi servent ces fiches ?">
        <p>
          La <strong>fiche client</strong> présente uniquement les identifiants de la société, à leur
          valeur en vigueur. La <strong>fiche dossier</strong> y ajoute toutes les opérations
          juridiques du dossier. Les deux se remplissent seules à partir des données connues ;
          chaque information indique d’où elle vient. Générez-les en Word quand vous en avez
          besoin : chaque génération est conservée dans la Data Room, en versions successives.
        </p>
      </TexteAide>

      <div role="tablist" aria-label="Fiches" className="mt-4 flex gap-1 border-b border-border">
        {(['client', 'dossier'] as const).map((o) => (
          <button
            key={o}
            type="button"
            role="tab"
            aria-selected={onglet === o}
            onClick={() => setOnglet(o)}
            className={`-mb-px border-b-2 px-3 py-2 text-sm font-medium ${
              onglet === o
                ? 'border-accent text-fg'
                : 'border-transparent text-fg-subtle hover:text-fg'
            }`}
          >
            {o === 'client' ? 'Fiche client' : 'Fiche dossier'}
          </button>
        ))}
      </div>

      {erreur && (
        <p role="alert" className="mt-4 rounded-lg border border-danger/40 bg-danger/10 px-3 py-2 text-sm text-danger">
          {erreur}
        </p>
      )}
      {message && (
        <p role="status" className="mt-4 rounded-lg border border-border bg-bg-overlay px-3 py-2 text-sm text-fg">
          {message}
        </p>
      )}
      {!vue && !erreur && <p className="mt-4 text-sm text-fg-subtle">Chargement de la fiche…</p>}

      {fiche && (
        <div className="mt-4 space-y-6">
          {manquants.length > 0 && (
            <p className="rounded-md border border-warning/40 bg-warning/10 px-3 py-2 text-sm text-fg" data-testid="fiche-manquants">
              {manquants.length === 1 ? 'Une information manque' : `${manquants.length} informations manquent`}{' '}
              : {manquants.map((m) => m.libelle).join(', ')}.{' '}
              {fiche.modifiable
                ? 'Saisissez-les avec « Modifier les identifiants », ou attendez qu’elles arrivent en fin de parcours.'
                : 'L’employé responsable du dossier peut les saisir.'}{' '}
              Elles figurent « non renseignées » sur la fiche générée.
            </p>
          )}

          <section aria-labelledby="fiche-identifiants">
            <h3 id="fiche-identifiants" className="mb-2 flex items-center gap-1 text-sm font-semibold text-fg">
              Identifiants de la société
              <InfoBulle
                libelle="D’où viennent ces informations ?"
                texte="Chaque valeur indique sa provenance : saisie manuelle (avec son auteur et sa date), obtenue en fin de parcours, lue sur une pièce et confirmée, reprise à l’import du dossier ou posée par un parcours. Une valeur antérieure au suivi est dite « provenance non tracée »."
              />
            </h3>
            <TableIdentifiants identifiants={fiche.identifiants} />
          </section>

          <section aria-labelledby="fiche-tp">
            <h3 id="fiche-tp" className="mb-2 flex items-center gap-1 text-sm font-semibold text-fg">
              Taxe professionnelle : versions successives
              <InfoBulle
                libelle="Pourquoi plusieurs versions ?"
                texte="Quand le numéro de taxe professionnelle change, l’ancien est conservé avec sa date de prise d’effet ; la version la plus récente est en vigueur. Une date de prise d’effet inconnue reste « non renseignée » : elle se complète dans le formulaire des identifiants."
              />
            </h3>
            {fiche.versionsTp.length === 0 ? (
              <p className="text-sm text-fg-subtle">Aucune taxe professionnelle enregistrée.</p>
            ) : (
              <table className="w-full text-sm">
                <thead className="text-left text-xs text-fg-subtle">
                  <tr>
                    <th className="py-1 pr-2">Numéro</th>
                    <th className="py-1 pr-2">Date de prise d’effet</th>
                    <th className="py-1">État</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {[...fiche.versionsTp].reverse().map((v, i) => (
                    <tr key={`${v.numero}-${i}`}>
                      <td className="py-1 pr-2 font-mono">{v.numero}</td>
                      <td className="py-1 pr-2">{v.dateEffet ? formatDate(v.dateEffet) : 'Non renseignée'}</td>
                      <td className="py-1">
                        {v.enVigueur ? (
                          <span className="rounded-full bg-accent/10 px-2 py-0.5 text-xs font-medium text-accent">
                            En vigueur
                          </span>
                        ) : (
                          'Remplacée'
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </section>

          {onglet === 'client' && (
            <section aria-labelledby="fiche-modifications">
              <h3 id="fiche-modifications" className="mb-2 flex items-center gap-1 text-sm font-semibold text-fg">
                Modifications des identifiants
                <InfoBulle
                  libelle="Qu’est-ce qui est tracé ?"
                  texte="Toute modification d’un identifiant est tracée : auteur, date, ancienne et nouvelle valeur, provenance. Cette trace s’affiche ici ; elle ne figure pas sur la fiche client générée, qui ne présente que les identifiants."
                />
              </h3>
              {fiche.modifications.length === 0 ? (
                <p className="text-sm text-fg-subtle">Aucune modification tracée.</p>
              ) : (
                <ol className="divide-y divide-border rounded-md border border-border" data-testid="fiche-trace">
                  {fiche.modifications.map((m, i) => (
                    <li key={`${m.champ}-${m.date}-${i}`} className="px-3 py-2 text-sm">
                      <span className="font-medium text-fg">{m.libelle}</span> :{' '}
                      <span className="text-fg-muted">{m.ancienneValeur ?? 'non renseigné'}</span> →{' '}
                      <span className="text-fg">{m.nouvelleValeur ?? 'non renseigné'}</span>
                      <span className="block text-xs text-fg-subtle">
                        {formatDate(m.date)} · {m.provenance}
                        {m.auteur && m.auteur !== 'Non renseigné' ? ` · ${m.auteur}` : ''}
                      </span>
                    </li>
                  ))}
                </ol>
              )}
            </section>
          )}

          {onglet === 'dossier' && vue && (
            <section aria-labelledby="fiche-operations">
              <h3 id="fiche-operations" className="mb-2 flex items-center gap-1 text-sm font-semibold text-fg">
                Opérations juridiques du dossier
                <InfoBulle
                  libelle="Quelles opérations ?"
                  texte="Chaque ticket du dossier est une opération : type, dates d’ouverture et de clôture ou d’annulation, statut et employé responsable. La fiche est relue à chaque ouverture : elle est à jour de la dernière clôture."
                />
              </h3>
              <p className="mb-2 text-xs text-fg-subtle">
                {vue.derniereCloture
                  ? `À jour de la dernière clôture, le ${formatDate(vue.derniereCloture)}.`
                  : 'Aucune opération clôturée à ce jour.'}
              </p>
              {vue.operations.length === 0 ? (
                <p className="text-sm text-fg-subtle">Aucune opération enregistrée.</p>
              ) : (
                <table className="w-full text-sm" data-testid="fiche-operations-table">
                  <thead className="text-left text-xs text-fg-subtle">
                    <tr>
                      <th className="py-1 pr-2">Opération</th>
                      <th className="py-1 pr-2">Ticket</th>
                      <th className="py-1 pr-2">Ouverture</th>
                      <th className="py-1 pr-2">Clôture ou annulation</th>
                      <th className="py-1 pr-2">Statut</th>
                      <th className="py-1">Employé responsable</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border">
                    {vue.operations.map((o) => (
                      <tr key={o.ticketId}>
                        <td className="py-1 pr-2">
                          {o.typeLabel}
                          {o.sousTypeLabel && <span className="block text-xs text-fg-subtle">{o.sousTypeLabel}</span>}
                        </td>
                        <td className="py-1 pr-2 font-mono text-xs">{o.reference ?? '—'}</td>
                        <td className="py-1 pr-2">{formatDate(o.dateOuverture)}</td>
                        <td className="py-1 pr-2">
                          {o.dateFin
                            ? `${o.natureFin === 'ANNULATION' ? 'Annulé le' : 'Clôturé le'} ${formatDate(o.dateFin)}`
                            : 'En cours'}
                        </td>
                        <td className="py-1 pr-2">{o.statutLabel}</td>
                        <td className="py-1">{o.responsable}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </section>
          )}

          <p className="text-xs text-fg-subtle">
            Ensuite : générez la fiche en Word pour la transmettre ou l’archiver. Les fiches
            générées se retrouvent dans les documents en vigueur du dossier, avec leurs versions
            précédentes.
          </p>
        </div>
      )}
    </Drawer>
  );
}

function TableIdentifiants({ identifiants }: { identifiants: IdentifiantFiche[] }) {
  return (
    <table className="w-full text-sm" data-testid="fiche-identifiants-table">
      <thead className="text-left text-xs text-fg-subtle">
        <tr>
          <th className="py-1 pr-2">Information</th>
          <th className="py-1 pr-2">Valeur en vigueur</th>
          <th className="py-1">Provenance</th>
        </tr>
      </thead>
      <tbody className="divide-y divide-border">
        {identifiants.map((i) => (
          <tr key={i.code}>
            <td className="py-1 pr-2 text-fg-muted">{i.libelle}</td>
            <td className="py-1 pr-2 text-fg">
              {i.valeur ?? <span className="italic text-fg-subtle">Non renseigné</span>}
            </td>
            <td className="py-1 text-xs text-fg-subtle">{i.provenance}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
