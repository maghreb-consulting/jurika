import { useCallback, useEffect, useState } from 'react';
import { Download } from 'lucide-react';
import { Page } from '../../components/layout/Page';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { Lien } from '../../components/ui/Lien';
import { Message } from '../../components/ui/Message';
import { EmptyState } from '../../components/ui/EmptyState';
import { TextField } from '../../components/ui/TextField';
import { Select } from '../../components/ui/Select';
import { Tableau, type ColonneTableau } from '../../components/ui/Tableau';
import { CardSkeleton, SqueletteZone } from '../../components/ui/LoadingSkeleton';
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
 *
 * Lot L6b (E9) : composants de la bibliotheque (champs, boutons, tableau, cartes).
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


  const enTete = {
    titre: 'Paiements',
    description: 'Paiements des cabinets à enregistrer, paiements reçus et factures.',
    actions: (
      <Lien to="/admin/abonnements" commeBouton={{ variant: 'secondary' }}>
        Abonnements
      </Lien>
    ),
  };
  const aide = (
    <TexteAide cle="admin-paiements" titre="Que faire sur cet écran ?">
      <p>
        Un virement, un chèque ou des espèces ne sont acquis que lorsque vous constatez la réception : indiquez la date de
        réception et la référence (numéro du virement, du chèque ou du reçu). L’abonnement du cabinet est alors activé ou
        prolongé et la facture est émise. La carte bancaire est confirmée automatiquement par le CMI : elle ne s’enregistre
        jamais ici. Une facture « en attente » attend une mention obligatoire (identité de l’éditeur ou ICE du cabinet) :
        complétez-la, puis relancez « Émettre les factures en attente ».
      </p>
    </TexteAide>
  );

  if (chargement) {
    return (
      <Page {...enTete} aide={aide}>
        <SqueletteZone libelle="Chargement des paiements…" className="space-y-4">
          <CardSkeleton className="h-40" />
          <CardSkeleton className="h-64" />
        </SqueletteZone>
      </Page>
    );
  }

  const colonnesRecents: ColonneTableau<PaiementVue>[] = [
    { cle: 'date', titre: 'Date', rendu: (p) => <span className="whitespace-nowrap tabular-nums">{formatDateIso(p.creeLe)}</span> },
    { cle: 'cabinet', titre: 'Cabinet', rendu: (p) => nomCabinet(p.workspaceId) },
    { cle: 'objet', titre: 'Objet', rendu: (p) => `${p.objet ? LIBELLE_OBJET[p.objet] : '--'} · ${p.forfaitLibelle}` },
    { cle: 'mode', titre: 'Mode', masquerSous: 'md', rendu: (p) => LIBELLE_MODE[p.mode] ?? p.mode },
    { cle: 'ttc', titre: 'TTC', alignement: 'droite', rendu: (p) => <span className="tabular-nums">{p.montants ? formatMadCents(p.montants.ttcCentimes) : '--'}</span> },
    {
      cle: 'etat',
      titre: 'État',
      rendu: (p) => (
        <>
          <Badge variant={p.statut === 'COMPLETED' ? 'success' : p.statut === 'PENDING' ? 'warning' : p.statut === 'FAILED' ? 'danger' : 'neutral'}>
            {LIBELLE_STATUT_PAIEMENT[p.statut] ?? p.statut}
          </Badge>
          {p.statut === 'COMPLETED' && !p.activationPropagee && <span className="mt-1 block text-xs text-warning">Activation non encore propagée</span>}
        </>
      ),
    },
    {
      cle: 'facture',
      titre: 'Facture',
      rendu: (p) => p.factureNumero ?? (p.factureRefus ? <span className="text-warning">En attente : {p.factureRefus}</span> : '--'),
    },
  ];

  return (
    <Page {...enTete} aide={aide}>
      <div className="space-y-6" data-testid="paiements-admin">
        <ErreurFacturationBloc erreur={erreur} />
        {info && (
          <Message ton="succes" onFermer={() => setInfo(null)}>
            {info}
          </Message>
        )}

        <Card title="Paiements déclarés par les cabinets, à enregistrer" data-testid="paiements-a-enregistrer">
          {aEnregistrer.length === 0 ? (
            <EmptyState compact title="Aucun paiement en attente de réception" description="Les virements, chèques et espèces déclarés par les cabinets apparaîtront ici jusqu’à leur réception." />
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
        </Card>

        <EnregistrementDirect cabinets={cabinets}
          onEnregistrer={(req) => action(() => billingAdminService.enregistrer(req),
            'Paiement enregistré : abonnement activé ou prolongé.', 'Le paiement n’a pas pu être enregistré.')} />

        <Card
          title={
            <span className="inline-flex items-center gap-1">
              Tâches de rattrapage
              <InfoBulle texte="Elles tournent aussi seules (toutes les cinq minutes ; l’archivage chaque nuit). Lancez-les à la main après avoir complété une mention ou si un service était indisponible." />
            </span>
          }
        >
          <div className="flex flex-wrap gap-2">
            <Button type="button" variant="secondary" size="sm" onClick={() => void tache('Factures en attente', billingAdminService.tacheFacturesEnAttente)}>Émettre les factures en attente</Button>
            <Button type="button" variant="secondary" size="sm" onClick={() => void tache('Activations', billingAdminService.tacheActivations)}>Propager les activations</Button>
            <Button type="button" variant="secondary" size="sm" onClick={() => void tache('Archivage', billingAdminService.tacheArchivage)}>Archiver les échéances dépassées</Button>
          </div>
        </Card>

        <section aria-labelledby="titre-paiements-recents">
          <h2 id="titre-paiements-recents" className="mb-3 text-lg font-semibold text-heading">Paiements récents</h2>
          <Tableau
            legende="Paiements récents de tous les cabinets"
            colonnes={colonnesRecents}
            lignes={recents}
            cleLigne={(p) => String(p.id)}
            hauteurMax="28rem"
            vide={<EmptyState compact title="Aucun paiement" description="Les paiements des cabinets, en ligne ou enregistrés ici, s’affichent dès leur création." />}
          />
        </section>

        <Card title="Factures de tous les cabinets">
          {factures.length === 0 ? (
            <EmptyState compact title="Aucune facture émise" description="Une facture est émise à chaque paiement constaté, dès que les mentions obligatoires sont complètes." />
          ) : (
            <ul className="divide-y divide-border text-sm">
              {factures.map((f) => (
                <li key={f.id} className="flex flex-wrap items-center justify-between gap-2 py-2">
                  <span className="text-fg"><strong>{f.numero}</strong> du {formatDateIso(f.dateEmission)} · {f.cabinet ?? nomCabinet(f.workspaceId)} · {formatMadCents(f.montants.ttcCentimes)} TTC</span>
                  <Button type="button" variant="secondary" size="sm" icon={<Download />} aria-label={`Télécharger la facture ${f.numero} (PDF)`}
                    onClick={() => void action(async () => enregistrerPdf(await billingAdminService.telechargerFacture(f.id), `Facture-${f.numero}.pdf`),
                      '', 'La facture n’a pas pu être téléchargée.')}>
                    PDF
                  </Button>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>
    </Page>
  );
}

function PaiementAEnregistrer({ p, cabinet, onValider, onRejeter }: {
  p: PaiementVue; cabinet: string;
  onValider: (recuLe: string, reference: string) => Promise<void>;
  onRejeter: (motif: string) => Promise<void>;
}) {
  const [recuLe, setRecuLe] = useState(aujourdhui());
  const [reference, setReference] = useState(p.reference ?? '');
  const [motif, setMotif] = useState('');
  return (
    <li className="rounded-lg border border-border bg-bg-raised p-4 text-sm" data-testid={`a-enregistrer-${p.id}`}>
      <p className="font-medium text-fg">
        {cabinet} · {p.objet ? LIBELLE_OBJET[p.objet] : '--'} · {p.forfaitLibelle} · {LIBELLE_MODE[p.mode]} ·{' '}
        {p.montants ? formatMadCents(p.montants.ttcCentimes) + ' TTC' : '--'} · déclaré le {formatDateIso(p.creeLe)}
      </p>
      <p className="text-xs text-fg-muted">Référence à rappeler par le cabinet : <span className="font-mono">JURIKA-{p.workspaceId.slice(0, 8).toUpperCase()}-{p.id}</span></p>
      <div className="mt-3 grid items-end gap-3 sm:grid-cols-[10rem_1fr_auto]">
        <TextField label="Date de réception" type="date" value={recuLe} onChange={(e) => setRecuLe(e.target.value)} />
        <TextField label="Référence (virement, chèque ou reçu)" value={reference} onChange={(e) => setReference(e.target.value)} />
        <Button type="button" disabled={!recuLe || !reference.trim()} onClick={() => void onValider(recuLe, reference.trim())}>
          Enregistrer la réception
        </Button>
      </div>
      <div className="mt-3 grid items-end gap-3 sm:grid-cols-[1fr_auto]">
        <TextField value={motif} onChange={(e) => setMotif(e.target.value)} placeholder="Motif du rejet" aria-label="Motif du rejet" />
        <Button type="button" variant="danger" disabled={!motif.trim()} onClick={() => void onRejeter(motif.trim())}>
          Rejeter
        </Button>
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
    <Card
      data-testid="enregistrement-direct"
      title={
        <span className="inline-flex items-center gap-1">
          Enregistrer un paiement reçu
          <InfoBulle texte="Pour un cabinet créé par JURIKA ou un règlement reçu sans déclaration. Souscription : premier abonnement ou reprise d’un cabinet archivé. Échéance : prolonge l’abonnement actif, au montant calculé." />
        </span>
      }
    >
      <form
        onSubmit={(e) => {
          e.preventDefault();
          if (!valide) return;
          void onEnregistrer({
            workspaceId, objet, forfait: objet === 'SOUSCRIPTION' ? forfait : undefined,
            rythme: objet === 'SOUSCRIPTION' ? rythme : undefined, mode, recuLe, reference: reference.trim(),
            montantHtCentimes: devis ? montantCentimes : null,
          });
        }}
      >
        <div className="grid gap-4 sm:grid-cols-2">
          <Select label="Cabinet" value={workspaceId} onChange={(e) => setWorkspaceId(e.target.value)}
            options={[{ value: '', label: 'Choisir un cabinet' }, ...cabinets.map((c) => ({ value: c.id, label: `${c.name} (${c.code})` }))]} />
          <Select label="Objet" value={objet} onChange={(e) => setObjet(e.target.value as 'SOUSCRIPTION' | 'ECHEANCE')}
            options={[
              { value: 'SOUSCRIPTION', label: 'Souscription (ou reprise)' },
              { value: 'ECHEANCE', label: 'Échéance de l’abonnement actif' },
            ]} />
          {objet === 'SOUSCRIPTION' && (
            <>
              <Select label="Forfait" value={forfait} onChange={(e) => setForfait(e.target.value as PlanCode)}
                options={[
                  { value: 'essentiel', label: 'Essentiel' },
                  { value: 'professionnel', label: 'Professionnel' },
                  { value: 'max', label: 'Max' },
                  { value: 'sur_devis', label: 'Sur Devis' },
                ]} />
              <Select label="Rythme" value={rythme} onChange={(e) => setRythme(e.target.value as BillingPeriod)}
                options={[
                  { value: 'yearly', label: 'Paiement annuel' },
                  { value: 'monthly', label: 'Paiement mensuel' },
                ]} />
            </>
          )}
          {devis && (
            <TextField label="Montant HT de l’échéance convenu par devis (DH)" inputMode="decimal" value={montantHt} onChange={(e) => setMontantHt(e.target.value)} />
          )}
          <Select label="Mode" value={mode} onChange={(e) => setMode(e.target.value as ModeHorsLigne)}
            options={[
              { value: 'BANK_TRANSFER', label: 'Virement bancaire' },
              { value: 'CHEQUE', label: 'Chèque' },
              { value: 'CASH', label: 'Espèces' },
            ]} />
          <TextField label="Date de réception" type="date" value={recuLe} onChange={(e) => setRecuLe(e.target.value)} />
          <TextField label="Référence (virement, chèque ou reçu)" value={reference} onChange={(e) => setReference(e.target.value)} />
        </div>
        <Button type="submit" className="mt-4" disabled={!valide}>
          Enregistrer le paiement
        </Button>
      </form>
    </Card>
  );
}
