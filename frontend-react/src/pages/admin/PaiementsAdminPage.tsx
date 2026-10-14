import { useCallback, useEffect, useState } from 'react';
import { Download, Loader2 } from 'lucide-react';
import { adminService } from '../../services/admin.service';
import { billingAdminService, enregistrerPdf } from '../../services/billing.service';
import { InfoBulle, TexteAide } from '../../components/ui/Aide';
import { ErreurFacturationBloc } from '../../components/billing/ErreurFacturation';
import type { AdminWorkspaceRow } from '../../types/admin';
import {
  formatDateIso,
  formatMadCents,
  lireErreurFacturation,
  LIBELLE_MODE,
  LIBELLE_OBJET,
  LIBELLE_STATUT_PAIEMENT,
  type BilanTacheVue,
  type BillingPeriod,
  type EnregistrementPaiementRequest,
  type ErreurFacturation,
  type FactureVue,
  type PaiementVue,
  type PlanCode,
} from '../../types/billing';

type ModeHorsLigne = EnregistrementPaiementRequest['mode'];

function aujourdhui(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/**
 * Lot L7 (E5), RG-PAY-02 et RG-PAY-03 : ecran du super-admin. Paiements par virement, cheque ou
 * especes a enregistrer (reception constatee : date et reference), enregistrement direct d'un
 * paiement recu, paiements recents, factures de tous les cabinets, taches de rattrapage.
 */
export function PaiementsAdminPage() {
  const [cabinets, setCabinets] = useState<AdminWorkspaceRow[]>([]);
  const [aEnregistrer, setAEnregistrer] = useState<PaiementVue[]>([]);
  const [recents, setRecents] = useState<PaiementVue[]>([]);
  const [factures, setFactures] = useState<FactureVue[]>([]);
  const [chargement, setChargement] = useState(true);
  const [erreur, setErreur] = useState<ErreurFacturation | null>(null);
  const [info, setInfo] = useState<string | null>(null);

  // Les mises a jour d'etat suivent les reponses (aucune mise a jour synchrone dans l'effet).
  const charger = useCallback(
    () =>
      Promise.all([
        adminService.listWorkspaces().catch(() => [] as AdminWorkspaceRow[]),
        billingAdminService.paiementsAEnregistrer(),
        billingAdminService.paiementsRecents(),
        billingAdminService.factures(),
      ])
        .then(([c, a, r, f]) => {
          setCabinets(c);
          setAEnregistrer(a);
          setRecents(r);
          setFactures(f);
        })
        .catch((err: unknown) => setErreur(lireErreurFacturation(err, 'Les paiements n’ont pas pu être chargés.')))
        .finally(() => setChargement(false)),
    [],
  );

  useEffect(() => { void charger(); }, [charger]);

  const nomCabinet = (id: string) => {
    const c = cabinets.find((x) => x.id === id);
    return c ? `${c.name} (${c.code})` : id;
  };

  async function action(f: () => Promise<unknown>, ok: string, defaut: string) {
    setErreur(null);
    setInfo(null);
    try {
      await f();
      if (ok) setInfo(ok);
      await charger();
    } catch (err) {
      setErreur(lireErreurFacturation(err, defaut));
    }
  }

  async function tache(nom: string, f: () => Promise<BilanTacheVue>) {
    await action(async () => {
      const b = await f();
      setInfo(`${nom} : ${b.traites} traité(s), ${b.reussis} réussi(s), ${b.echecs} en attente.${b.details.length ? ' ' + b.details.join(' ; ') : ''}`);
    }, '', `${nom} : échec.`);
  }

  if (chargement) {
    return <div className="flex h-64 items-center justify-center"><Loader2 className="h-6 w-6 animate-spin text-accent" aria-label="Chargement" /></div>;
  }

  return (
    <div className="space-y-6" data-testid="paiements-admin">
      <header>
        <h1 className="font-heading text-2xl font-semibold text-fg">Paiements</h1>
        <p className="mt-1 text-sm text-fg-muted">Paiements des cabinets à enregistrer, paiements reçus et factures.</p>
      </header>
      <TexteAide cle="admin-paiements" titre="Que faire sur cet écran ?">
        <p>
          Un virement, un chèque ou des espèces ne sont acquis que lorsque vous constatez la réception : indiquez la date de
          réception et la référence (numéro du virement, du chèque ou du reçu). L’abonnement du cabinet est alors activé ou
          prolongé et la facture est émise. La carte bancaire est confirmée automatiquement par le CMI : elle ne s’enregistre
          jamais ici. Une facture « en attente » attend une mention obligatoire (identité de l’éditeur ou ICE du cabinet) :
          complétez-la, puis relancez « Émettre les factures en attente ».
        </p>
      </TexteAide>
      <ErreurFacturationBloc erreur={erreur} />
      {info && <p role="status" className="rounded-lg border border-border bg-bg-overlay p-3 text-sm text-fg">{info}</p>}

      <section className="rounded-2xl bg-bg-raised p-5 shadow-sm" data-testid="paiements-a-enregistrer">
        <h2 className="mb-3 text-base font-semibold text-fg">Paiements déclarés par les cabinets, à enregistrer</h2>
        {aEnregistrer.length === 0 ? (
          <p className="text-sm text-fg-muted">Aucun paiement en attente de réception.</p>
        ) : (
          <ul className="space-y-3">
            {aEnregistrer.map((p) => (
              <PaiementAEnregistrer key={p.id} p={p} cabinet={nomCabinet(p.workspaceId)}
                onValider={(recuLe, reference) => action(() => billingAdminService.valider(p.id, { recuLe, reference }),
                  'Paiement enregistré.', 'Le paiement n’a pas pu être enregistré.')}
                onRejeter={(motif) => action(() => billingAdminService.rejeter(p.id, motif),
                  'Paiement rejeté.', 'Le paiement n’a pas pu être rejeté.')} />
            ))}
          </ul>
        )}
      </section>

      <EnregistrementDirect cabinets={cabinets}
        onEnregistrer={(req) => action(() => billingAdminService.enregistrer(req),
          'Paiement enregistré : abonnement activé ou prolongé.', 'Le paiement n’a pas pu être enregistré.')} />

      <section className="rounded-2xl bg-bg-raised p-5 shadow-sm">
        <h2 className="mb-3 flex items-center gap-1 text-base font-semibold text-fg">
          Tâches de rattrapage
          <InfoBulle texte="Elles tournent aussi seules (toutes les cinq minutes ; l’archivage chaque nuit). Lancez-les à la main après avoir complété une mention ou si un service était indisponible." />
        </h2>
        <div className="flex flex-wrap gap-2">
          <button type="button" className={btnSec} onClick={() => void tache('Factures en attente', billingAdminService.tacheFacturesEnAttente)}>Émettre les factures en attente</button>
          <button type="button" className={btnSec} onClick={() => void tache('Activations', billingAdminService.tacheActivations)}>Propager les activations</button>
          <button type="button" className={btnSec} onClick={() => void tache('Archivage', billingAdminService.tacheArchivage)}>Archiver les échéances dépassées</button>
        </div>
      </section>

      <section className="rounded-2xl bg-bg-raised p-5 shadow-sm">
        <h2 className="mb-3 text-base font-semibold text-fg">Paiements récents</h2>
        {recents.length === 0 ? <p className="text-sm text-fg-muted">Aucun paiement.</p> : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="text-xs uppercase text-fg-subtle">
                <tr><th className="py-2 pr-3">Date</th><th className="pr-3">Cabinet</th><th className="pr-3">Objet</th><th className="pr-3">Mode</th><th className="pr-3">TTC</th><th className="pr-3">État</th><th>Facture</th></tr>
              </thead>
              <tbody>
                {recents.map((p) => (
                  <tr key={p.id} className="border-t border-border">
                    <td className="py-2 pr-3">{formatDateIso(p.creeLe)}</td>
                    <td className="pr-3">{nomCabinet(p.workspaceId)}</td>
                    <td className="pr-3">{p.objet ? LIBELLE_OBJET[p.objet] : '—'} · {p.forfaitLibelle}</td>
                    <td className="pr-3">{LIBELLE_MODE[p.mode] ?? p.mode}</td>
                    <td className="pr-3">{p.montants ? formatMadCents(p.montants.ttcCentimes) : '—'}</td>
                    <td className="pr-3">
                      {LIBELLE_STATUT_PAIEMENT[p.statut] ?? p.statut}
                      {p.statut === 'COMPLETED' && !p.activationPropagee && <span className="block text-xs text-warning">Activation non encore propagée</span>}
                    </td>
                    <td>{p.factureNumero ?? (p.factureRefus ? <span className="text-warning">En attente : {p.factureRefus}</span> : '—')}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className="rounded-2xl bg-bg-raised p-5 shadow-sm">
        <h2 className="mb-3 text-base font-semibold text-fg">Factures de tous les cabinets</h2>
        {factures.length === 0 ? <p className="text-sm text-fg-muted">Aucune facture émise.</p> : (
          <ul className="divide-y divide-border text-sm">
            {factures.map((f) => (
              <li key={f.id} className="flex flex-wrap items-center justify-between gap-2 py-2">
                <span><strong>{f.numero}</strong> du {formatDateIso(f.dateEmission)} · {f.cabinet ?? nomCabinet(f.workspaceId)} · {formatMadCents(f.montants.ttcCentimes)} TTC</span>
                <button type="button" className={btnSec}
                  onClick={() => void action(async () => enregistrerPdf(await billingAdminService.telechargerFacture(f.id), `Facture-${f.numero}.pdf`),
                    '', 'La facture n’a pas pu être téléchargée.')}>
                  <Download className="mr-1 inline h-3.5 w-3.5" aria-hidden="true" />PDF
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}

const btnSec = 'rounded-lg border border-border px-3 py-1.5 text-xs font-medium text-fg hover:bg-bg-overlay';
const champ = 'w-full rounded-lg border border-border bg-bg px-3 py-2 text-sm text-fg focus:border-accent focus:outline-none';

function PaiementAEnregistrer({ p, cabinet, onValider, onRejeter }: {
  p: PaiementVue; cabinet: string;
  onValider: (recuLe: string, reference: string) => Promise<void>;
  onRejeter: (motif: string) => Promise<void>;
}) {
  const [recuLe, setRecuLe] = useState(aujourdhui());
  const [reference, setReference] = useState(p.reference ?? '');
  const [motif, setMotif] = useState('');
  return (
    <li className="rounded-lg border border-border p-3 text-sm" data-testid={`a-enregistrer-${p.id}`}>
      <p className="font-medium text-fg">
        {cabinet} · {p.objet ? LIBELLE_OBJET[p.objet] : '—'} · {p.forfaitLibelle} · {LIBELLE_MODE[p.mode]} ·{' '}
        {p.montants ? formatMadCents(p.montants.ttcCentimes) + ' TTC' : '—'} · déclaré le {formatDateIso(p.creeLe)}
      </p>
      <p className="text-xs text-fg-subtle">Référence à rappeler par le cabinet : JURIKA-{p.workspaceId.slice(0, 8).toUpperCase()}-{p.id}</p>
      <div className="mt-2 grid gap-2 sm:grid-cols-[10rem_1fr_auto]">
        <label className="text-xs text-fg-subtle">Date de réception
          <input type="date" value={recuLe} onChange={(e) => setRecuLe(e.target.value)} className={champ} />
        </label>
        <label className="text-xs text-fg-subtle">Référence (virement, chèque ou reçu)
          <input type="text" value={reference} onChange={(e) => setReference(e.target.value)} className={champ} />
        </label>
        <button type="button" disabled={!recuLe || !reference.trim()} onClick={() => void onValider(recuLe, reference.trim())}
          className="self-end rounded-lg bg-accent px-3 py-2 text-xs font-semibold text-bg hover:bg-accent-hover disabled:opacity-50">
          Enregistrer la réception
        </button>
      </div>
      <div className="mt-2 grid gap-2 sm:grid-cols-[1fr_auto]">
        <input type="text" value={motif} onChange={(e) => setMotif(e.target.value)} placeholder="Motif du rejet" aria-label="Motif du rejet" className={champ} />
        <button type="button" disabled={!motif.trim()} onClick={() => void onRejeter(motif.trim())}
          className="rounded-lg border border-danger/50 px-3 py-2 text-xs font-semibold text-danger disabled:opacity-50">
          Rejeter
        </button>
      </div>
    </li>
  );
}

function EnregistrementDirect({ cabinets, onEnregistrer }: {
  cabinets: AdminWorkspaceRow[];
  onEnregistrer: (r: EnregistrementPaiementRequest) => Promise<void>;
}) {
  const [workspaceId, setWorkspaceId] = useState('');
  const [objet, setObjet] = useState<'SOUSCRIPTION' | 'ECHEANCE'>('SOUSCRIPTION');
  const [forfait, setForfait] = useState<PlanCode>('essentiel');
  const [rythme, setRythme] = useState<BillingPeriod>('yearly');
  const [mode, setMode] = useState<ModeHorsLigne>('BANK_TRANSFER');
  const [recuLe, setRecuLe] = useState(aujourdhui());
  const [reference, setReference] = useState('');
  const [montantHt, setMontantHt] = useState('');
  const montantCentimes = Math.round(Number(montantHt.replace(',', '.')) * 100);
  const devis = objet === 'SOUSCRIPTION' && forfait === 'sur_devis';
  const valide = workspaceId && recuLe && reference.trim() && (!devis || montantCentimes > 0);

  return (
    <section className="rounded-2xl bg-bg-raised p-5 shadow-sm" data-testid="enregistrement-direct">
      <h2 className="mb-3 flex items-center gap-1 text-base font-semibold text-fg">
        Enregistrer un paiement reçu
        <InfoBulle texte="Pour un cabinet créé par JURIKA ou un règlement reçu sans déclaration. Souscription : premier abonnement ou reprise d’un cabinet archivé. Échéance : prolonge l’abonnement actif, au montant calculé." />
      </h2>
      <div className="grid gap-3 sm:grid-cols-2">
        <label className="text-xs text-fg-subtle">Cabinet
          <select value={workspaceId} onChange={(e) => setWorkspaceId(e.target.value)} className={champ}>
            <option value="">Choisir un cabinet</option>
            {cabinets.map((c) => <option key={c.id} value={c.id}>{c.name} ({c.code})</option>)}
          </select>
        </label>
        <label className="text-xs text-fg-subtle">Objet
          <select value={objet} onChange={(e) => setObjet(e.target.value as 'SOUSCRIPTION' | 'ECHEANCE')} className={champ}>
            <option value="SOUSCRIPTION">Souscription (ou reprise)</option>
            <option value="ECHEANCE">Échéance de l’abonnement actif</option>
          </select>
        </label>
        {objet === 'SOUSCRIPTION' && (
          <>
            <label className="text-xs text-fg-subtle">Forfait
              <select value={forfait} onChange={(e) => setForfait(e.target.value as PlanCode)} className={champ}>
                <option value="essentiel">Essentiel</option>
                <option value="professionnel">Professionnel</option>
                <option value="max">Max</option>
                <option value="sur_devis">Sur Devis</option>
              </select>
            </label>
            <label className="text-xs text-fg-subtle">Rythme
              <select value={rythme} onChange={(e) => setRythme(e.target.value as BillingPeriod)} className={champ}>
                <option value="yearly">Paiement annuel</option>
                <option value="monthly">Paiement mensuel</option>
              </select>
            </label>
          </>
        )}
        {devis && (
          <label className="text-xs text-fg-subtle">Montant HT de l’échéance convenu par devis (DH)
            <input type="text" inputMode="decimal" value={montantHt} onChange={(e) => setMontantHt(e.target.value)} className={champ} />
          </label>
        )}
        <label className="text-xs text-fg-subtle">Mode
          <select value={mode} onChange={(e) => setMode(e.target.value as ModeHorsLigne)} className={champ}>
            <option value="BANK_TRANSFER">Virement bancaire</option>
            <option value="CHEQUE">Chèque</option>
            <option value="CASH">Espèces</option>
          </select>
        </label>
        <label className="text-xs text-fg-subtle">Date de réception
          <input type="date" value={recuLe} onChange={(e) => setRecuLe(e.target.value)} className={champ} />
        </label>
        <label className="text-xs text-fg-subtle">Référence (virement, chèque ou reçu)
          <input type="text" value={reference} onChange={(e) => setReference(e.target.value)} className={champ} />
        </label>
      </div>
      <button type="button" disabled={!valide}
        onClick={() => void onEnregistrer({
          workspaceId, objet, forfait: objet === 'SOUSCRIPTION' ? forfait : undefined,
          rythme: objet === 'SOUSCRIPTION' ? rythme : undefined, mode, recuLe, reference: reference.trim(),
          montantHtCentimes: devis ? montantCentimes : null,
        })}
        className="mt-4 rounded-lg bg-accent px-4 py-2 text-sm font-semibold text-bg hover:bg-accent-hover disabled:opacity-50">
        Enregistrer le paiement
      </button>
    </section>
  );
}
