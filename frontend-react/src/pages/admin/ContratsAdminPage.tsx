import { useCallback, useEffect, useMemo, useState } from 'react';
import { AlertCircle, Download, FileSignature, Loader2 } from 'lucide-react';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { InfoBulle, TexteAide } from '../../components/ui/Aide';
import { useToast } from '../../components/ui/useToast';
import {
  LIBELLE_STATUT,
  dateLongue,
  horodatageCasablanca,
  prix,
} from '../../components/contrat/formatContrat';
import { contratService, enregistrerFichier } from '../../services/contrat.service';
import { adminService } from '../../services/admin.service';
import { extractError } from '../../lib/api';
import type { AdminWorkspaceRow } from '../../types/admin';
import type {
  Forfait,
  HistoriqueContrat,
  LigneAdminContrat,
  ModePaiement,
  Rythme,
  VersionVue,
} from '../../types/contrat';

/** Grille tarifaire du cahier des charges (paragraphe 4.3), en dirhams HT : proposition modifiable. */
const GRILLE: Record<Exclude<Forfait, 'SUR_DEVIS'>, Record<Rythme, number>> = {
  ESSENTIEL: { ANNUEL: 3750, MENSUEL: 375 },
  PROFESSIONNEL: { ANNUEL: 7500, MENSUEL: 750 },
  MAX: { ANNUEL: 15000, MENSUEL: 1500 },
};

function aujourdhui(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function dansJours(n: number): string {
  const d = new Date();
  d.setDate(d.getDate() + n);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/**
 * Lot L7 (E6) : contrats d'abonnement, cote super-admin (CDC 4.8).
 * - etat de chaque contrat : en attente, accepte, resilie (RG-CTR-03) ;
 * - activation d'un abonnement par la saisie des conditions particulieres (RG-CTR-03, RG-CTR-05) ;
 * - resiliation avec date d'effet et motif (RG-CTR-08), historique et exemplaires (RG-CTR-09) ;
 * - versions du modele et publication d'une nouvelle version 30 jours avant (RG-CTR-07).
 */
export function ContratsAdminPage() {
  const toast = useToast();
  const [cabinets, setCabinets] = useState<AdminWorkspaceRow[]>([]);
  const [lignes, setLignes] = useState<LigneAdminContrat[]>([]);
  const [versions, setVersions] = useState<VersionVue[]>([]);
  const [chargement, setChargement] = useState(true);
  const [erreur, setErreur] = useState<string | null>(null);
  const [selection, setSelection] = useState<string | null>(null);
  const [mode, setMode] = useState<'conditions' | 'resiliation' | 'historique' | null>(null);

  const charger = useCallback(
    () =>
      Promise.all([adminService.listWorkspaces(), contratService.listeAdmin(), contratService.versionsAdmin()])
        .then(([c, l, v]) => {
          setCabinets(c);
          setLignes(l);
          setVersions(v);
          setErreur(null);
        })
        .catch((e: unknown) => setErreur(extractError(e).message))
        .finally(() => setChargement(false)),
    [],
  );

  useEffect(() => {
    void charger();
  }, [charger]);

  const parCabinet = useMemo(() => new Map(lignes.map((l) => [l.workspaceId, l])), [lignes]);
  const cabinet = cabinets.find((c) => c.id === selection) ?? null;

  function ouvrir(id: string, m: 'conditions' | 'resiliation' | 'historique') {
    setSelection(id);
    setMode(m);
  }

  async function apres(message: string) {
    toast.success(message);
    setMode(null);
    await charger();
  }

  return (
    <div className="mx-auto flex max-w-6xl flex-col gap-4">
      <header className="flex flex-wrap items-center gap-3">
        <FileSignature className="h-6 w-6 text-accent" aria-hidden="true" />
        <div className="flex-1">
          <h1 className="font-heading text-2xl font-semibold text-fg">Contrats d’abonnement</h1>
          <p className="text-sm text-fg-subtle">État du contrat de chaque cabinet, activation, résiliation, versions.</p>
        </div>
      </header>

      <TexteAide cle="admin-contrats" titre="Activer un abonnement et suivre les contrats">
        <p>
          Pour activer l’abonnement d’un cabinet, saisissez ses conditions particulières (forfait, rythme de paiement,
          prix hors taxes, date de début). Elles sont alors « en attente d’acceptation » : à sa prochaine connexion,
          le superviseur du cabinet ne voit que l’écran du contrat, jusqu’à ce qu’il l’accepte en ligne. Ensuite, le
          contrat passe « accepté » et son exemplaire est téléchargeable ici. Les mentions de Maghreb Consulting
          imprimées au contrat se saisissent dans « Paramètres de l’éditeur » ; une mention vide s’imprime
          « [non renseigné : …] ». Quand une nouvelle version des conditions générales entre en vigueur, chaque
          superviseur l’accepte ou la refuse ; la colonne « Contrat » indique la réponse attendue ou donnée (un refus
          vaut résiliation à l’échéance).
        </p>
      </TexteAide>

      {erreur && (
        <p role="alert" className="flex items-start gap-2 rounded-lg bg-danger/10 px-3 py-2 text-sm text-danger">
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
          <span>{erreur}</span>
        </p>
      )}

      {chargement ? (
        <p className="flex items-center gap-2 text-sm text-fg-subtle" role="status">
          <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> Chargement…
        </p>
      ) : cabinets.length === 0 ? (
        <Card className="p-4 text-sm text-fg-muted">Aucun cabinet sur la plateforme.</Card>
      ) : (
        <Card className="overflow-x-auto p-0">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-fg-subtle">
                <th scope="col" className="px-3 py-2">Cabinet</th>
                <th scope="col" className="px-3 py-2">Contrat</th>
                <th scope="col" className="px-3 py-2">Conditions</th>
                <th scope="col" className="px-3 py-2">
                  Échéance <InfoBulle libelle="Échéance" texte="Le cabinet et JURIKA sont avertis 30 jours avant ; sauf résiliation, l’abonnement se renouvelle pour un an." />
                </th>
                <th scope="col" className="px-3 py-2">Actions</th>
              </tr>
            </thead>
            <tbody>
              {cabinets.map((c) => {
                const l = parCabinet.get(c.id);
                const cp = l?.enAttente ?? l?.courant ?? null;
                return (
                  <tr key={c.id} className="border-t border-border align-top">
                    <td className="px-3 py-2">
                      <p className="font-medium text-fg">{c.name}</p>
                      <p className="text-xs text-fg-subtle">{c.code}</p>
                    </td>
                    <td className="px-3 py-2">
                      {LIBELLE_STATUT[l?.statut ?? 'AUCUN']}
                      {l?.resiliation && <p className="text-xs text-fg-subtle">Effet le {dateLongue(l.resiliation.dateEffet)}</p>}
                      {l?.versionSansReponse && (
                        <p className="text-xs text-warning" data-testid={`sans-reponse-${c.id}`}>
                          Réponse attendue à la version {l.versionSansReponse} des conditions générales
                        </p>
                      )}
                      {l?.derniereReponse && (
                        <p className="text-xs text-fg-subtle" data-testid={`reponse-${c.id}`}>
                          Version {l.derniereReponse.versionCode}{' '}
                          {l.derniereReponse.reponse === 'ACCEPTEE' ? 'acceptée' : 'refusée'} le{' '}
                          {horodatageCasablanca(l.derniereReponse.reponduLeCasablanca)}
                          {l.derniereReponse.reponse === 'REFUSEE' && ' — résiliation à l’échéance'}
                        </p>
                      )}
                    </td>
                    <td className="px-3 py-2">
                      {cp ? (
                        <>
                          <p>{cp.forfaitLibelle} — {prix(cp)}</p>
                          <p className="text-xs text-fg-subtle">{cp.reference}</p>
                        </>
                      ) : '—'}
                    </td>
                    <td className="px-3 py-2">
                      {l?.courant ? (
                        <>
                          {dateLongue(l.courant.echeanceCourante)}
                          {l.joursAvantEcheance !== null && l.joursAvantEcheance <= 30 && (
                            <p className="text-xs text-warning">dans {l.joursAvantEcheance} jour{l.joursAvantEcheance > 1 ? 's' : ''}</p>
                          )}
                        </>
                      ) : '—'}
                    </td>
                    <td className="px-3 py-2">
                      <div className="flex flex-wrap gap-1">
                        <Button size="sm" variant="secondary" onClick={() => ouvrir(c.id, 'conditions')}>
                          Saisir des conditions
                        </Button>
                        {l?.courant && (
                          <Button size="sm" variant="ghost" onClick={() => ouvrir(c.id, 'resiliation')}>Résilier</Button>
                        )}
                        {l && <Button size="sm" variant="ghost" onClick={() => ouvrir(c.id, 'historique')}>Historique</Button>}
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </Card>
      )}

      {cabinet && mode === 'conditions' && (
        <FormulaireConditions
          cabinet={cabinet}
          ligne={parCabinet.get(cabinet.id) ?? null}
          onAnnuler={() => setMode(null)}
          onFait={() => apres(`Conditions particulières enregistrées pour ${cabinet.name} : en attente d’acceptation par le superviseur.`)}
        />
      )}
      {cabinet && mode === 'resiliation' && (
        <FormulaireResiliation
          cabinet={cabinet}
          onAnnuler={() => setMode(null)}
          onFait={() => apres(`Résiliation enregistrée pour ${cabinet.name}.`)}
        />
      )}
      {cabinet && mode === 'historique' && <HistoriqueCabinet cabinet={cabinet} onFermer={() => setMode(null)} />}

      <Versions versions={versions} onPubliee={() => apres('Nouvelle version publiée et notifiée aux cabinets.')} />
    </div>
  );
}

function FormulaireConditions({ cabinet, ligne, onAnnuler, onFait }: {
  cabinet: AdminWorkspaceRow;
  ligne: LigneAdminContrat | null;
  onAnnuler: () => void;
  onFait: () => void;
}) {
  const [forfait, setForfait] = useState<Forfait>('ESSENTIEL');
  const [rythme, setRythme] = useState<Rythme>('ANNUEL');
  const [prixHt, setPrixHt] = useState(String(GRILLE.ESSENTIEL.ANNUEL));
  const [dateDebut, setDateDebut] = useState(aujourdhui());
  const [modePaiement, setModePaiement] = useState<ModePaiement | ''>('');
  const [envoi, setEnvoi] = useState(false);
  const [erreur, setErreur] = useState<string | null>(null);

  function proposer(f: Forfait, r: Rythme) {
    setPrixHt(f === 'SUR_DEVIS' ? '' : String(GRILLE[f][r]));
  }

  async function enregistrer() {
    setEnvoi(true);
    setErreur(null);
    try {
      await contratService.ouvrirConditions(cabinet.id, {
        forfait,
        rythmePaiement: rythme,
        prixHt: Number(prixHt.replace(',', '.')),
        dateDebut,
        modePaiement: modePaiement === '' ? null : modePaiement,
      });
      onFait();
    } catch (e) {
      setErreur(extractError(e).message);
    } finally {
      setEnvoi(false);
    }
  }

  return (
    <Card className="flex flex-col gap-3 p-4" aria-label={`Conditions particulières de ${cabinet.name}`}>
      <h2 className="text-lg font-semibold text-fg">Conditions particulières — {cabinet.name}</h2>
      {ligne?.enAttente && (
        <p className="rounded-md bg-warning/10 px-3 py-2 text-sm">
          Des conditions ({ligne.enAttente.reference}) attendent déjà l’acceptation : les nouvelles les remplaceront.
        </p>
      )}
      {ligne?.courant && (
        <p className="text-sm text-fg-muted">
          Contrat en cours : {ligne.courant.forfaitLibelle}, {prix(ligne.courant)}. Il reste applicable jusqu’à
          l’acceptation des nouvelles conditions (changement de forfait ou de rythme, RG-CTR-05).
        </p>
      )}
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <label className="flex flex-col gap-1 text-sm">
          <span>Forfait *</span>
          <select
            value={forfait}
            onChange={(e) => {
              const f = e.target.value as Forfait;
              setForfait(f);
              proposer(f, rythme);
            }}
            className="rounded-md border border-border bg-bg px-2 py-1.5"
          >
            <option value="ESSENTIEL">Essentiel</option>
            <option value="PROFESSIONNEL">Professionnel</option>
            <option value="MAX">Max</option>
            <option value="SUR_DEVIS">Sur Devis</option>
          </select>
        </label>
        <label className="flex flex-col gap-1 text-sm">
          <span>Rythme de paiement *</span>
          <select
            value={rythme}
            onChange={(e) => {
              const r = e.target.value as Rythme;
              setRythme(r);
              proposer(forfait, r);
            }}
            className="rounded-md border border-border bg-bg px-2 py-1.5"
          >
            <option value="ANNUEL">Annuel (un règlement par an)</option>
            <option value="MENSUEL">Mensuel (douze mensualités)</option>
          </select>
        </label>
        <div className="flex flex-col gap-1 text-sm">
          <span>
            <label htmlFor="conditions-prix">Prix HT de l’échéance (DH) *</label>{' '}
            <InfoBulle
              libelle="Aide : prix"
              texte="Montant d’une mensualité (rythme mensuel) ou de l’annuité (rythme annuel), hors taxes. Le prix de la grille tarifaire est proposé ; pour le forfait Sur Devis, saisissez le prix du devis."
            />
          </span>
          <input
            id="conditions-prix"
            value={prixHt}
            onChange={(e) => setPrixHt(e.target.value)}
            inputMode="decimal"
            className="rounded-md border border-border bg-bg px-2 py-1.5"
          />
        </div>
        <label className="flex flex-col gap-1 text-sm">
          <span>Date de début *</span>
          <input
            type="date"
            value={dateDebut}
            onChange={(e) => setDateDebut(e.target.value)}
            className="rounded-md border border-border bg-bg px-2 py-1.5"
          />
        </label>
        <label className="flex flex-col gap-1 text-sm">
          <span>Mode de paiement</span>
          <select
            value={modePaiement}
            onChange={(e) => setModePaiement(e.target.value as ModePaiement | '')}
            className="rounded-md border border-border bg-bg px-2 py-1.5"
          >
            <option value="">Non précisé (« non renseigné » au contrat)</option>
            <option value="CARTE">Carte bancaire en ligne</option>
            <option value="VIREMENT">Virement bancaire</option>
            <option value="CHEQUE">Chèque</option>
            <option value="ESPECES">Espèces</option>
          </select>
        </label>
      </div>
      <p className="text-xs text-fg-subtle">
        Durée : un an à compter de la date de début (article 5 des conditions générales) ; la première échéance en
        découle. Le superviseur du cabinet devra accepter ces conditions en ligne avant d’accéder à la plateforme.
      </p>
      {erreur && <p role="alert" className="text-sm text-danger">{erreur}</p>}
      <div className="flex gap-2">
        <Button onClick={enregistrer} loading={envoi} disabled={envoi || prixHt.trim() === '' || dateDebut === ''}>
          Enregistrer les conditions
        </Button>
        <Button variant="ghost" onClick={onAnnuler}>Annuler</Button>
      </div>
    </Card>
  );
}

function FormulaireResiliation({ cabinet, onAnnuler, onFait }: {
  cabinet: AdminWorkspaceRow;
  onAnnuler: () => void;
  onFait: () => void;
}) {
  const [dateEffet, setDateEffet] = useState(aujourdhui());
  const [motif, setMotif] = useState('');
  const [confirmer, setConfirmer] = useState(false);
  const [envoi, setEnvoi] = useState(false);
  const [erreur, setErreur] = useState<string | null>(null);

  async function resilier() {
    setEnvoi(true);
    setErreur(null);
    try {
      await contratService.resilierAdmin(cabinet.id, dateEffet, motif.trim());
      setConfirmer(false);
      onFait();
    } catch (e) {
      setErreur(extractError(e).message);
      setConfirmer(false);
    } finally {
      setEnvoi(false);
    }
  }

  return (
    <Card className="flex flex-col gap-3 p-4">
      <h2 className="text-lg font-semibold text-fg">Résilier le contrat — {cabinet.name}</h2>
      <p className="text-sm text-fg-muted">
        À la date d’effet, le forfait s’arrête et le cabinet est archivé, jamais supprimé : son superviseur peut encore
        se connecter pour exporter ses données et télécharger son contrat.
      </p>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <label className="flex flex-col gap-1 text-sm">
          <span>Date d’effet *</span>
          <input type="date" value={dateEffet} min={aujourdhui()} onChange={(e) => setDateEffet(e.target.value)}
            className="rounded-md border border-border bg-bg px-2 py-1.5" />
        </label>
        <label className="flex flex-col gap-1 text-sm sm:col-span-2">
          <span>Motif *</span>
          <textarea value={motif} onChange={(e) => setMotif(e.target.value)} rows={2}
            className="rounded-md border border-border bg-bg px-2 py-1.5" />
        </label>
      </div>
      {erreur && <p role="alert" className="text-sm text-danger">{erreur}</p>}
      <div className="flex gap-2">
        <Button variant="danger" onClick={() => setConfirmer(true)} disabled={motif.trim() === '' || dateEffet === ''}>
          Enregistrer la résiliation
        </Button>
        <Button variant="ghost" onClick={onAnnuler}>Annuler</Button>
      </div>
      <ConfirmDialog
        open={confirmer}
        onOpenChange={setConfirmer}
        title="Confirmer la résiliation"
        description={`Le forfait de ${cabinet.name} s’arrêtera le ${dateLongue(dateEffet)}.`}
        confirmLabel="Résilier"
        variant="danger"
        loading={envoi}
        onConfirm={resilier}
      />
    </Card>
  );
}

function HistoriqueCabinet({ cabinet, onFermer }: { cabinet: AdminWorkspaceRow; onFermer: () => void }) {
  const toast = useToast();
  const [h, setH] = useState<HistoriqueContrat | null>(null);
  const [erreur, setErreur] = useState<string | null>(null);

  useEffect(() => {
    contratService
      .historiqueAdmin(cabinet.id)
      .then(setH)
      .catch((e: unknown) => setErreur(extractError(e).message));
  }, [cabinet.id]);

  async function telecharger(id: string, version: string) {
    try {
      enregistrerFichier(await contratService.exemplaireAdmin(cabinet.id, id), `contrat-${cabinet.code}-${version}.pdf`);
    } catch (e) {
      toast.error(extractError(e).message);
    }
  }

  return (
    <Card className="flex flex-col gap-2 p-4">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-semibold text-fg">Historique du contrat — {cabinet.name}</h2>
        <Button size="sm" variant="ghost" onClick={onFermer}>Fermer</Button>
      </div>
      {erreur && <p role="alert" className="text-sm text-danger">{erreur}</p>}
      {!h && !erreur && <p className="text-sm text-fg-subtle">Chargement…</p>}
      {h && (
        <>
          <ul className="flex flex-col gap-1 text-sm">
            {h.conditions.map((c) => (
              <li key={c.id}>
                {c.reference} — {c.forfaitLibelle}, {prix(c)}, début {dateLongue(c.dateDebut)} :{' '}
                {LIBELLE_STATUT[c.statut] ?? c.statut}
              </li>
            ))}
          </ul>
          {h.acceptations.map((a) => (
            <p key={a.id} className="flex flex-wrap items-center gap-2 text-sm">
              Accepté le {horodatageCasablanca(a.accepteLeCasablanca)} par {a.signataire} ({a.signataireQualite}),
              compte {a.signataireCompte}, IP {a.adresseIp}, version {a.versionCode}
              <Button size="sm" variant="ghost" onClick={() => telecharger(a.id, a.versionCode)}>
                <Download className="mr-1 h-4 w-4" aria-hidden="true" /> Exemplaire
              </Button>
            </p>
          ))}
          {h.reponses.map((r) => (
            <p key={r.id} className="text-sm">
              Nouvelle version {r.versionCode} {r.reponse === 'ACCEPTEE' ? 'acceptée' : 'refusée'} le{' '}
              {horodatageCasablanca(r.reponduLeCasablanca)}, compte {r.signataireCompte}, IP {r.adresseIp}
            </p>
          ))}
          {h.resiliations.map((r) => (
            <p key={r.conditionsId} className="text-sm">
              Résiliation ({r.demandeurRole === 'SUPER_ADMIN' ? 'JURIKA' : 'superviseur'}), effet le {dateLongue(r.dateEffet)} — {r.motif}
            </p>
          ))}
          {h.avis.filter((a) => a.statut === 'ECHEC').map((a) => (
            <p key={a.id} role="alert" className="text-sm text-danger">
              Courriel non envoyé à {a.destinataire} : {a.erreur ?? 'motif inconnu'}
            </p>
          ))}
        </>
      )}
    </Card>
  );
}

function Versions({ versions, onPubliee }: { versions: VersionVue[]; onPubliee: () => void }) {
  const [code, setCode] = useState('');
  const [entree, setEntree] = useState(dansJours(30));
  const [texte, setTexte] = useState('');
  const [envoi, setEnvoi] = useState(false);
  const [erreur, setErreur] = useState<string | null>(null);

  async function partirDe(v: VersionVue) {
    setErreur(null);
    try {
      setTexte(await contratService.versionTexteAdmin(v.code));
    } catch (e) {
      setErreur(extractError(e).message);
    }
  }

  async function publier() {
    setEnvoi(true);
    setErreur(null);
    try {
      await contratService.publierVersion(code.trim(), texte, entree);
      setCode('');
      setTexte('');
      onPubliee();
    } catch (e) {
      setErreur(extractError(e).message);
    } finally {
      setEnvoi(false);
    }
  }

  return (
    <Card className="flex flex-col gap-3 p-4">
      <h2 className="text-lg font-semibold text-fg">
        Versions du contrat{' '}
        <InfoBulle
          libelle="Versions"
          texte="Une nouvelle version des conditions générales est notifiée aux cabinets au moins 30 jours avant son entrée en vigueur ; à sa connexion suivante, chaque superviseur l’accepte ou la refuse (un refus vaut résiliation sans frais à l’échéance). Toutes les versions sont conservées ; une version retirée n’est jamais présentée aux cabinets."
        />
      </h2>
      <ul className="flex flex-col gap-1 text-sm">
        {versions.map((v) => (
          <li key={v.id} className="flex flex-wrap items-center gap-2">
            <span>
              Version {v.code}
              {v.retiree
                ? ' — retirée : jamais présentée aux cabinets (projet antérieur à la validation)'
                : ` — en vigueur le ${dateLongue(v.entreeEnVigueur)}`}
              {!v.retiree && v.origine === 'INITIALE' ? ' (texte validé, chargé au démarrage)' : ''}
            </span>
            {!v.retiree && <Button size="sm" variant="ghost" onClick={() => partirDe(v)}>Partir de ce texte</Button>}
          </li>
        ))}
      </ul>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <label className="flex flex-col gap-1 text-sm">
          <span>Code de la nouvelle version *</span>
          <input value={code} onChange={(e) => setCode(e.target.value)} placeholder="2027-01-15"
            className="rounded-md border border-border bg-bg px-2 py-1.5" />
        </label>
        <label className="flex flex-col gap-1 text-sm">
          <span>Entrée en vigueur * (30 jours au moins)</span>
          <input type="date" value={entree} min={dansJours(30)} onChange={(e) => setEntree(e.target.value)}
            className="rounded-md border border-border bg-bg px-2 py-1.5" />
        </label>
      </div>
      <label className="flex flex-col gap-1 text-sm">
        <span>Texte complet du contrat (pièces A, B et C, avec leurs variables $MAJUSCULES ; une pièce D et la ligne de version en tête sont retirées) *</span>
        <textarea value={texte} onChange={(e) => setTexte(e.target.value)} rows={8}
          className="rounded-md border border-border bg-bg px-2 py-1.5 font-mono text-xs" />
      </label>
      <p className="text-xs text-fg-subtle">
        Le texte doit être validé par la direction avant sa publication. Il est contrôlé (pièces A, B, C, paragraphe
        d’acceptation, variables connues, aucune mention entre crochets restant à compléter) ; une publication ne se
        modifie plus.
      </p>
      {erreur && <p role="alert" className="text-sm text-danger">{erreur}</p>}
      <div>
        <Button onClick={publier} loading={envoi} disabled={envoi || code.trim() === '' || texte.trim() === ''}>
          Publier et notifier les cabinets
        </Button>
      </div>
    </Card>
  );
}
