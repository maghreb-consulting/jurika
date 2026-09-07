import { useCallback, useEffect, useState } from 'react';
import {
  AlertTriangle,
  Check,
  ChevronDown,
  ChevronRight,
  Loader2,
  Paperclip,
  Undo2,
  X,
} from 'lucide-react';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { Drawer } from '../../components/ui/Drawer';
import { TextField } from '../../components/ui/TextField';
import { demarcheService } from '../../services/demarche.service';
import { dataroomService } from '../../services/dataroom.service';
import { extractError } from '../../lib/api';
import { formatDate } from '../../lib/date';
import { AvancementPanel } from './AvancementPanel';
import type {
  JustificatifAttendu,
  LigneDemarche,
  VueDemarches,
} from '../../types/demarche';

/**
 * D.3 — Le cochage des démarches, et D.4 — l'avancement du ticket.
 *
 * Le contrôle vit côté serveur : cocher sans justificatif est refusé, une
 * démarche obligatoire ne peut pas être écartée, et le décochage n'est possible
 * que tant que le ticket n'a pas quitté le statut. L'interface ne se contente
 * pas de griser un bouton — elle DIT pourquoi, en nommant ce qui manque.
 */

/** Les alternatives d'un même groupe : l'une d'elles suffit. */
function groupesAttendus(attendus: JustificatifAttendu[]): JustificatifAttendu[][] {
  const parGroupe = new Map<number, JustificatifAttendu[]>();
  for (const a of attendus) {
    const liste = parGroupe.get(a.alternativeGroupe);
    if (liste) liste.push(a);
    else parGroupe.set(a.alternativeGroupe, [a]);
  }
  return [...parGroupe.entries()].sort(([a], [b]) => a - b).map(([, v]) => v);
}

export function DemarchesPanel({
  ticketId,
  dossierId,
  canAct,
}: {
  ticketId: string;
  dossierId: string | null;
  canAct: boolean;
}) {
  const [vue, setVue] = useState<VueDemarches | null>(null);
  const [chargement, setChargement] = useState(true);
  const [erreur, setErreur] = useState<string | null>(null);
  const [ecarter, setEcarter] = useState<LigneDemarche | null>(null);

  const charger = useCallback(async () => {
    setChargement(true);
    try {
      setVue(await demarcheService.vue(ticketId));
      setErreur(null);
    } catch (err) {
      setErreur(extractError(err).message);
    } finally {
      setChargement(false);
    }
  }, [ticketId]);

  useEffect(() => {
    void charger();
  }, [charger]);

  if (chargement) {
    return (
      <Card>
        <div className="flex items-center gap-2 px-5 py-6 text-sm text-fg-subtle">
          <Loader2 className="h-4 w-4 animate-spin" /> Chargement des démarches…
        </div>
      </Card>
    );
  }

  if (erreur) {
    return (
      <Card>
        <p className="px-5 py-6 text-sm text-danger" role="alert">
          {erreur}
        </p>
      </Card>
    );
  }

  // Un workflow sans référentiel chargé (tout sauf CREATION à ce jour) n'a
  // aucune démarche : on n'affiche alors rien plutôt qu'un panneau vide.
  if (!vue || vue.phases.length === 0) return null;

  return (
    <div className="space-y-4">
      <AvancementPanel avancement={vue.avancement} phases={vue.phases} />

      <Card>
        <header className="border-b border-border px-5 py-3">
          <h3 className="text-sm font-semibold text-fg">Démarches</h3>
          <p className="mt-0.5 text-xs text-fg-subtle">
            Dans l&apos;ordre du guide. Seules les démarches du statut courant sont
            actionnables.
          </p>
        </header>

        {vue.phases.map((phase) => (
          <PhaseSection
            key={phase.code}
            code={phase.code}
            libelle={phase.libelle}
            demarches={phase.demarches}
            ticketId={ticketId}
            dossierId={dossierId}
            canAct={canAct}
            onChanged={setVue}
            onEcarter={setEcarter}
          />
        ))}
      </Card>

      {ecarter && (
        <EcarterDrawer
          demarche={ecarter}
          ticketId={ticketId}
          onClose={() => setEcarter(null)}
          onDone={(v) => {
            setVue(v);
            setEcarter(null);
          }}
        />
      )}
    </div>
  );
}

function PhaseSection({
  code,
  libelle,
  demarches,
  ticketId,
  dossierId,
  canAct,
  onChanged,
  onEcarter,
}: {
  code: string;
  libelle: string;
  demarches: LigneDemarche[];
  ticketId: string;
  dossierId: string | null;
  canAct: boolean;
  onChanged: (v: VueDemarches) => void;
  onEcarter: (d: LigneDemarche) => void;
}) {
  // Les phases dont une démarche est actionnable s'ouvrent d'elles-mêmes : c'est
  // là que l'employé travaille.
  const [ouvert, setOuvert] = useState(demarches.some((d) => d.actionnableMaintenant));
  const traitees = demarches.filter((d) => d.etat !== 'A_FAIRE').length;

  return (
    <section data-testid={`phase-${code}`}>
      <button
        type="button"
        onClick={() => setOuvert((v) => !v)}
        aria-expanded={ouvert}
        className="flex w-full items-center gap-2 border-b border-border bg-bg-overlay/60 px-5 py-2 text-left transition hover:bg-bg-overlay"
      >
        {ouvert ? (
          <ChevronDown className="h-4 w-4 shrink-0 text-fg-subtle" />
        ) : (
          <ChevronRight className="h-4 w-4 shrink-0 text-fg-subtle" />
        )}
        <span className="text-sm font-semibold text-fg">{libelle}</span>
        <span className="ml-auto tabular-nums text-xs text-fg-subtle">
          {traitees} / {demarches.length}
        </span>
      </button>

      {ouvert && (
        <ul className="divide-y divide-border">
          {demarches.map((d) => (
            <DemarcheRow
              key={d.demarcheId}
              demarche={d}
              ticketId={ticketId}
              dossierId={dossierId}
              canAct={canAct}
              onChanged={onChanged}
              onEcarter={onEcarter}
            />
          ))}
        </ul>
      )}
    </section>
  );
}

function DemarcheRow({
  demarche: d,
  ticketId,
  dossierId,
  canAct,
  onChanged,
  onEcarter,
}: {
  demarche: LigneDemarche;
  ticketId: string;
  dossierId: string | null;
  canAct: boolean;
  onChanged: (v: VueDemarches) => void;
  onEcarter: (d: LigneDemarche) => void;
}) {
  const [enCours, setEnCours] = useState(false);
  const [erreur, setErreur] = useState<string | null>(null);
  const [fichiers, setFichiers] = useState<Map<string, File>>(new Map());

  const groupes = groupesAttendus(d.justificatifsAttendus);
  const sansJustificatif = groupes.length === 0;
  const actionnable = canAct && d.actionnableMaintenant;

  /** Groupes encore sans fichier choisi : ce que l'interface doit nommer. */
  const groupesManquants = groupes.filter(
    (alternatives) => !alternatives.some((a) => fichiers.has(a.documentType)),
  );

  async function cocher() {
    if (!dossierId && !sansJustificatif) {
      setErreur("Le ticket n'a pas de dossier rattaché : le justificatif ne peut pas être déposé.");
      return;
    }
    setEnCours(true);
    setErreur(null);
    try {
      // 1. Dépôt automatique dans le dossier juridique du ticket, avec le type
      //    issu du référentiel — l'employé ne le choisit jamais.
      const documentIds: string[] = [];
      for (const [documentType, file] of fichiers) {
        const doc = await dataroomService.uploadJuridique(dossierId as string, {
          file,
          documentType,
          title: `${d.ordre}. ${d.libelle}`,
          ticketId,
        });
        documentIds.push(doc.id);
      }
      // 2. Cochage : le serveur revérifie type, rattachement et couverture.
      onChanged(await demarcheService.cocher(ticketId, d.ordre, documentIds));
      setFichiers(new Map());
    } catch (err) {
      setErreur(extractError(err).message);
    } finally {
      setEnCours(false);
    }
  }

  async function decocher() {
    setEnCours(true);
    setErreur(null);
    try {
      onChanged(await demarcheService.decocher(ticketId, d.ordre));
    } catch (err) {
      setErreur(extractError(err).message);
    } finally {
      setEnCours(false);
    }
  }

  return (
    <li className="px-5 py-3" data-testid={`demarche-${d.ordre}`}>
      <div className="flex items-start gap-3">
        <span
          className={`mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded border ${
            d.etat === 'COCHEE'
              ? 'border-success bg-success text-bg-raised'
              : d.etat === 'NON_APPLICABLE'
                ? 'border-border bg-bg-overlay text-fg-subtle'
                : 'border-border'
          }`}
          aria-hidden
        >
          {d.etat === 'COCHEE' && <Check className="h-3.5 w-3.5" />}
          {d.etat === 'NON_APPLICABLE' && <X className="h-3.5 w-3.5" />}
        </span>

        <div className="min-w-0 flex-1">
          <p className="text-sm text-fg">
            <span className="font-semibold">{d.ordre}.</span> {d.libelle}
          </p>

          <p className="mt-0.5 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-fg-subtle">
            <Badge variant={d.obligatoire ? 'info' : 'warning'}>
              {d.obligatoire ? 'Obligatoire' : 'Conditionnelle'}
            </Badge>
            {d.organisme && <span>{d.organisme}</span>}
            {d.acteur && <span>· {d.acteur}</span>}
            {d.horsSequence && (
              <span className="flex items-center gap-1 text-warning">
                <AlertTriangle className="h-3 w-3" />
                cochée avant une précédente de la même phase
              </span>
            )}
          </p>

          {!d.obligatoire && d.conditionApplication && d.etat === 'A_FAIRE' && (
            <p className="mt-1 text-xs text-fg-subtle">
              <span className="font-medium text-fg">Condition : </span>
              {d.conditionApplication}
            </p>
          )}

          {/* Délai : jamais de date fabriquée. */}
          {d.delai && (
            <p className="mt-1 text-xs text-fg-subtle">
              <span className="font-medium text-fg">Délai : </span>
              {d.delai}
              {!d.delaiCalculable && (
                <span className="italic">
                  {' '}
                  — aucune alerte : le guide ne permet pas de déterminer le point de départ
                </span>
              )}
            </p>
          )}

          {d.etat === 'NON_APPLICABLE' && d.motif && (
            <p className="mt-1 text-xs text-fg-subtle">
              <span className="font-medium text-fg">Écartée : </span>
              {d.motif}
            </p>
          )}

          {d.etat === 'COCHEE' && d.cocheAt && (
            <p className="mt-1 text-xs text-success">
              Accomplie le {formatDate(d.cocheAt)}
              {d.documentsDeposes.length > 0 &&
                ` · ${d.documentsDeposes.length} justificatif${d.documentsDeposes.length > 1 ? 's' : ''}`}
            </p>
          )}

          {/* ── Justificatifs à téléverser ────────────────────────── */}
          {actionnable && d.etat === 'A_FAIRE' && !sansJustificatif && (
            <div className="mt-2 space-y-2">
              <p className="text-xs text-fg-subtle">
                <span className="font-medium text-fg">Justificatif attendu : </span>
                {d.justificatifsTexte}
              </p>
              {groupes.map((alternatives, i) => {
                const choisi = alternatives.find((a) => fichiers.has(a.documentType));
                return (
                  <div key={i} className="flex flex-wrap items-center gap-2">
                    {alternatives.map((a) => (
                      <label
                        key={a.documentType}
                        className={`inline-flex cursor-pointer items-center gap-1.5 rounded-lg border px-2.5 py-1 text-xs transition ${
                          fichiers.has(a.documentType)
                            ? 'border-success bg-success/10 text-success'
                            : 'border-border text-fg-subtle hover:border-accent'
                        }`}
                      >
                        <Paperclip className="h-3 w-3" />
                        {a.documentType}
                        <input
                          type="file"
                          className="hidden"
                          onChange={(ev) => {
                            const f = ev.target.files?.[0];
                            if (!f) return;
                            setFichiers((prev) => {
                              const suivant = new Map(prev);
                              // Une alternative choisie remplace l'autre du groupe.
                              for (const alt of alternatives) suivant.delete(alt.documentType);
                              suivant.set(a.documentType, f);
                              return suivant;
                            });
                            ev.target.value = '';
                          }}
                        />
                      </label>
                    ))}
                    {alternatives.length > 1 && !choisi && (
                      <span className="text-[11px] text-fg-subtle">(l&apos;un des trois suffit)</span>
                    )}
                    {choisi && (
                      <span className="truncate text-[11px] text-success">
                        {fichiers.get(choisi.documentType)?.name}
                      </span>
                    )}
                  </div>
                );
              })}
            </div>
          )}

          {/* L'interface DIT pourquoi le cochage est impossible. */}
          {actionnable && d.etat === 'A_FAIRE' && groupesManquants.length > 0 && (
            <p className="mt-2 text-xs text-warning">
              Cochage impossible tant que ces justificatifs ne sont pas fournis :{' '}
              {groupesManquants
                .map((alt) => alt.map((a) => a.documentType).join(' ou '))
                .join(', ')}
              .
            </p>
          )}

          {!canAct && d.etat === 'A_FAIRE' && (
            <p className="mt-2 text-xs text-fg-subtle">
              Consultation seule — le cochage est réservé à l&apos;employé en charge.
            </p>
          )}

          {canAct && !d.actionnableMaintenant && d.etat === 'A_FAIRE' && (
            <p className="mt-2 text-xs text-fg-subtle">
              Cette démarche relève d&apos;un autre statut du parcours : elle ne peut pas être
              cochée maintenant.
            </p>
          )}

          {erreur && (
            <p className="mt-2 text-xs text-danger" role="alert">
              {erreur}
            </p>
          )}

          {/* ── Actions ───────────────────────────────────────────── */}
          {actionnable && (
            <div className="mt-2 flex flex-wrap gap-2">
              {d.etat === 'A_FAIRE' && (
                <>
                  <Button
                    size="sm"
                    disabled={enCours || groupesManquants.length > 0}
                    onClick={() => void cocher()}
                  >
                    {enCours ? <Loader2 className="mr-1 h-3.5 w-3.5 animate-spin" /> : null}
                    Cocher
                  </Button>
                  {!d.obligatoire && (
                    <Button size="sm" variant="secondary" onClick={() => onEcarter(d)}>
                      Non applicable
                    </Button>
                  )}
                  {d.obligatoire && (
                    <span className="self-center text-xs text-fg-subtle">
                      Obligatoire dans tous les dossiers : elle ne peut pas être écartée.
                    </span>
                  )}
                </>
              )}
              {d.etat !== 'A_FAIRE' && (
                <Button
                  size="sm"
                  variant="secondary"
                  disabled={enCours}
                  onClick={() => void decocher()}
                >
                  <Undo2 className="mr-1 h-3.5 w-3.5" /> Revenir dessus
                </Button>
              )}
            </div>
          )}
        </div>
      </div>
    </li>
  );
}

/** Écarter une démarche conditionnelle : le motif est obligatoire. */
function EcarterDrawer({
  demarche: d,
  ticketId,
  onClose,
  onDone,
}: {
  demarche: LigneDemarche;
  ticketId: string;
  onClose: () => void;
  onDone: (v: VueDemarches) => void;
}) {
  const [motif, setMotif] = useState('');
  const [enCours, setEnCours] = useState(false);
  const [erreur, setErreur] = useState<string | null>(null);

  async function valider() {
    if (!motif.trim()) {
      setErreur('Motif obligatoire.');
      return;
    }
    setEnCours(true);
    setErreur(null);
    try {
      onDone(await demarcheService.marquerNonApplicable(ticketId, d.ordre, motif.trim()));
    } catch (err) {
      setErreur(extractError(err).message);
      setEnCours(false);
    }
  }

  return (
    <Drawer open onClose={onClose} title={`Écarter la démarche ${d.ordre}`} width="md">
      <div className="space-y-4">
        <p className="text-sm text-fg">{d.libelle}</p>
        {d.conditionApplication && (
          <p className="rounded-lg border border-border bg-bg-overlay/60 px-3 py-2 text-sm text-fg-subtle">
            <span className="font-medium text-fg">Condition du guide : </span>
            {d.conditionApplication}
          </p>
        )}
        <TextField
          label="Motif"
          value={motif}
          onChange={(e) => setMotif(e.target.value)}
          placeholder="Pourquoi cette démarche ne s'applique pas à ce dossier"
          error={erreur ?? undefined}
        />
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose} disabled={enCours}>
            Annuler
          </Button>
          <Button onClick={() => void valider()} disabled={enCours}>
            {enCours ? <Loader2 className="mr-1 h-4 w-4 animate-spin" /> : null}
            Écarter
          </Button>
        </div>
      </div>
    </Drawer>
  );
}
