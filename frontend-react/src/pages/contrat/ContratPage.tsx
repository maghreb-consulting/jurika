import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { AlertCircle, AlertTriangle, Download, FileSignature, Loader2, Mail } from 'lucide-react';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { InfoBulle, TexteAide } from '../../components/ui/Aide';
import { useToast } from '../../components/ui/useToast';
import { TexteContrat } from '../../components/contrat/TexteContrat';
import {
  LIBELLE_MODE,
  LIBELLE_RYTHME,
  LIBELLE_STATUT,
  dateLongue,
  horodatageCasablanca,
  prix,
} from '../../components/contrat/formatContrat';
import { contratService, enregistrerFichier, estEtatComplet } from '../../services/contrat.service';
import { extractError } from '../../lib/api';
import { useContratStore } from '../../store/contratStore';
import { useAuthStore } from '../../store/authStore';
import type {
  AcceptationVue,
  ApercuContrat,
  ConditionsVue,
  EtatContrat,
  HistoriqueContrat,
  SignataireSaisie,
  VersionVue,
} from '../../types/contrat';

/**
 * Lot L7 (E6) : ecran du contrat d'abonnement du cabinet (superviseur), CDC 4.8.
 * - RG-CTR-01 / 02 : lecture du texte exact, case jamais pre-cochee, confirmation ;
 * - RG-CTR-03 : tant que des conditions particulieres attendent, seul cet ecran est accessible ;
 * - RG-CTR-04 : exemplaire PDF telechargeable a tout moment, copie par courriel ;
 * - RG-CTR-06 a 09 : echeance, nouvelle version, resiliation en ligne, historique, versions.
 */
export function ContratPage() {
  const toast = useToast();
  const rechargerGarde = useContratStore((s) => s.charger);
  const userId = useAuthStore((s) => s.user?.userId);
  const [etat, setEtat] = useState<EtatContrat | null>(null);
  const [historique, setHistorique] = useState<HistoriqueContrat | null>(null);
  const [versions, setVersions] = useState<VersionVue[]>([]);
  const [chargement, setChargement] = useState(true);
  const [erreur, setErreur] = useState<string | null>(null);

  const charger = useCallback(
    () =>
      Promise.all([contratService.etat(), contratService.historique(), contratService.versions()])
        .then(([e, h, v]) => {
          if (estEtatComplet(e)) setEtat(e);
          setHistorique(h);
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

  async function apresChangement(message: string) {
    toast.success(message);
    await charger();
    if (userId) await rechargerGarde(userId);
  }

  return (
    <div className="mx-auto flex max-w-4xl flex-col gap-4">
      <header className="flex flex-wrap items-center gap-3">
        <FileSignature className="h-6 w-6 text-accent" aria-hidden="true" />
        <div className="flex-1">
          <h1 className="font-heading text-2xl font-semibold text-fg">
            Contrat d’abonnement{' '}
            <InfoBulle
              libelle="À propos du contrat"
              texte="Le contrat qui lie votre cabinet à Maghreb Consulting, éditeur de JURIKA : conditions particulières (forfait, prix, dates), conditions générales d’abonnement et accord de traitement des données personnelles."
            />
          </h1>
          <p className="text-sm text-fg-subtle">Acceptation en ligne, exemplaire, échéance, résiliation et historique.</p>
        </div>
      </header>

      <TexteAide cle="contrat-abonnement" titre="Votre contrat d’abonnement">
        <p>
          Quand JURIKA met en place ou modifie votre abonnement, de nouvelles conditions particulières vous sont
          présentées ici : lisez le contrat complet, cochez la case d’acceptation, puis confirmez. Tant qu’elles ne
          sont pas acceptées, vous et vos employés n’accédez qu’à cet écran. Une fois le contrat accepté,
          l’exemplaire complet (PDF) reste téléchargeable à tout moment et une copie est envoyée à l’adresse
          électronique du cabinet.
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
          <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> Chargement du contrat…
        </p>
      ) : etat ? (
        <>
          {etat.acceptationRequise && (
            <BlocAcceptation etat={etat} onAccepte={() => apresChangement('Contrat accepté. Une copie est envoyée par courriel.')} />
          )}
          {etat.statut === 'AUCUN' && !etat.acceptationRequise && (
            <Card className="p-4 text-sm text-fg-muted">
              Aucun contrat d’abonnement n’est encore enregistré pour votre cabinet. Il apparaîtra ici dès que JURIKA
              aura saisi vos conditions particulières (forfait, prix, dates), ou à votre souscription d’un forfait.
            </Card>
          )}
          {etat.versionAVenir && (
            <Card className="p-4 text-sm text-fg-muted">
              <p className="font-medium text-fg">
                Nouvelle version des conditions générales : version {etat.versionAVenir.code}, en vigueur le{' '}
                {dateLongue(etat.versionAVenir.entreeEnVigueur)}.
              </p>
              <p className="mt-1">
                Elle vous sera présentée pour acceptation à votre première connexion après cette date. Jusque-là, la
                version actuelle continue de s’appliquer ; si vous la refusez, vous pourrez résilier sans frais à
                l’échéance (article 16). Son texte est consultable plus bas, dans « Versions du contrat ».
              </p>
            </Card>
          )}
          {etat.courant && <BlocContratCourant etat={etat} conditions={etat.courant} />}
          {etat.resiliation && (
            <Card className="p-4 text-sm">
              <p className="font-medium text-fg">
                Résiliation enregistrée : le forfait s’arrête le {dateLongue(etat.resiliation.dateEffet)}.
              </p>
              <p className="mt-1 text-fg-muted">
                Motif : {etat.resiliation.motif}. À cette date, l’accès est fermé et tout le travail du cabinet est
                archivé, jamais supprimé : vous pourrez toujours vous connecter pour exporter vos données ou télécharger
                votre contrat, et réactiver le cabinet en souscrivant de nouveau.
              </p>
            </Card>
          )}
          {etat.courant && !etat.acceptationRequise && (
            <BlocResiliation conditions={etat.courant} onResilie={() => apresChangement('Résiliation enregistrée.')} />
          )}
          {historique && <BlocHistorique historique={historique} />}
          <BlocVersions versions={versions} />
        </>
      ) : null}
    </div>
  );
}

function ResumeConditions({ c }: { c: ConditionsVue }) {
  return (
    <dl className="grid grid-cols-1 gap-x-6 gap-y-1 text-sm sm:grid-cols-2">
      <div><dt className="inline text-fg-subtle">Référence : </dt><dd className="inline text-fg">{c.reference}</dd></div>
      <div><dt className="inline text-fg-subtle">Forfait : </dt><dd className="inline text-fg">{c.forfaitLibelle}</dd></div>
      <div><dt className="inline text-fg-subtle">Rythme de paiement : </dt><dd className="inline text-fg">{LIBELLE_RYTHME[c.rythmePaiement]}</dd></div>
      <div><dt className="inline text-fg-subtle">Prix : </dt><dd className="inline text-fg">{prix(c)}</dd></div>
      <div><dt className="inline text-fg-subtle">Début : </dt><dd className="inline text-fg">{dateLongue(c.dateDebut)}</dd></div>
      <div><dt className="inline text-fg-subtle">Échéance : </dt><dd className="inline text-fg">{dateLongue(c.echeanceCourante)}</dd></div>
      <div>
        <dt className="inline text-fg-subtle">Mode de paiement : </dt>
        <dd className="inline text-fg">{c.modePaiement ? LIBELLE_MODE[c.modePaiement] : 'non renseigné'}</dd>
      </div>
    </dl>
  );
}

const SIGNATAIRE_VIDE: SignataireSaisie = { civilite: '', qualite: '', formeCabinet: '', rcVilleCabinet: '' };

function BlocAcceptation({ etat, onAccepte }: { etat: EtatContrat; onAccepte: () => void }) {
  const r = etat.reprise;
  const [signataire, setSignataire] = useState<SignataireSaisie>(
    r
      ? {
          civilite: r.civilite ?? '',
          qualite: r.qualite ?? '',
          formeCabinet: r.formeCabinet ?? '',
          rcVilleCabinet: r.rcVilleCabinet ?? '',
        }
      : SIGNATAIRE_VIDE,
  );
  const [apercu, setApercu] = useState<ApercuContrat | null>(null);
  const [caseCochee, setCaseCochee] = useState(false);
  const [confirmer, setConfirmer] = useState(false);
  const [envoi, setEnvoi] = useState(false);
  const [erreur, setErreur] = useState<string | null>(null);
  const nouvelleVersion = etat.objetAAccepter === 'NOUVELLE_VERSION';
  const conditions = etat.enAttente ?? etat.courant;

  function changer(champ: keyof SignataireSaisie, valeur: string) {
    setSignataire((s) => ({ ...s, [champ]: valeur }));
    // Le texte affiche doit etre exactement celui accepte : toute modification oblige a le relire.
    setApercu(null);
    setCaseCochee(false);
  }

  async function afficher() {
    setErreur(null);
    if (!signataire.civilite || signataire.qualite.trim() === '') {
      setErreur('Renseignez votre civilité et votre qualité (par exemple « gérant ») avant d’afficher le contrat.');
      return;
    }
    setEnvoi(true);
    try {
      setApercu(await contratService.apercu(signataire));
      setCaseCochee(false);
    } catch (e) {
      setErreur(extractError(e).message);
    } finally {
      setEnvoi(false);
    }
  }

  async function accepter() {
    if (!apercu) return;
    setEnvoi(true);
    setErreur(null);
    try {
      await contratService.accepter(signataire, apercu.empreinte, caseCochee);
      setConfirmer(false);
      onAccepte();
    } catch (e) {
      const err = extractError(e);
      setErreur(err.message);
      setConfirmer(false);
      if (err.code === 'TEXTE_MODIFIE') setApercu(null);
    } finally {
      setEnvoi(false);
    }
  }

  return (
    <Card className="flex flex-col gap-3 border-accent p-4" aria-labelledby="titre-acceptation">
      <h2 id="titre-acceptation" className="flex items-center gap-2 text-lg font-semibold text-fg">
        <AlertTriangle className="h-5 w-5 text-accent" aria-hidden="true" />
        {nouvelleVersion
          ? `Nouvelle version des conditions générales à accepter (version ${etat.versionEnVigueur?.code ?? ''})`
          : 'Contrat d’abonnement à accepter'}
      </h2>
      <p className="text-sm text-fg-muted">
        {nouvelleVersion
          ? 'Cette version est en vigueur. Lisez-la et acceptez-la ; si vous la refusez, la version antérieure s’applique jusqu’à l’échéance, où vous pourrez résilier sans frais.'
          : 'JURIKA a saisi les conditions particulières de votre abonnement. Tant que vous ne les avez pas acceptées, l’accès à la plateforme est limité à cet écran, pour vous et pour vos employés.'}
      </p>
      {conditions && <ResumeConditions c={conditions} />}

      <fieldset className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <legend className="mb-1 text-sm font-medium text-fg">
          Vos mentions de signataire{' '}
          <InfoBulle
            libelle="Mentions du signataire"
            texte="Ces mentions sont imprimées dans le contrat. Votre nom, votre prénom et votre compte viennent de votre profil ; la dénomination, l’adresse, le numéro de RC, l’ICE et l’adresse électronique du cabinet viennent de Paramètres › Cabinet."
          />
        </legend>
        <label className="flex flex-col gap-1 text-sm">
          <span>Civilité *</span>
          <select
            value={signataire.civilite}
            onChange={(e) => changer('civilite', e.target.value)}
            className="rounded-md border border-border bg-bg px-2 py-1.5"
          >
            <option value="">— Choisir —</option>
            <option value="M.">M.</option>
            <option value="Mme">Mme</option>
          </select>
        </label>
        <label className="flex flex-col gap-1 text-sm">
          <span>Qualité au sein du cabinet *</span>
          <input
            value={signataire.qualite}
            onChange={(e) => changer('qualite', e.target.value)}
            placeholder="gérant, associé, directeur…"
            className="rounded-md border border-border bg-bg px-2 py-1.5"
          />
        </label>
        <label className="flex flex-col gap-1 text-sm">
          <span>Forme juridique du cabinet</span>
          <input
            value={signataire.formeCabinet}
            onChange={(e) => changer('formeCabinet', e.target.value)}
            placeholder="SARL, société civile professionnelle…"
            className="rounded-md border border-border bg-bg px-2 py-1.5"
          />
        </label>
        <label className="flex flex-col gap-1 text-sm">
          <span>Ville du registre du commerce du cabinet</span>
          <input
            value={signataire.rcVilleCabinet}
            onChange={(e) => changer('rcVilleCabinet', e.target.value)}
            placeholder="Casablanca, Rabat…"
            className="rounded-md border border-border bg-bg px-2 py-1.5"
          />
        </label>
      </fieldset>
      <p className="text-xs text-fg-subtle">
        Une mention laissée vide figure dans le contrat sous la forme « [non renseigné : …] ». Pour compléter
        l’identité du cabinet, ouvrez{' '}
        <Link to="/settings/cabinet" className="underline">Paramètres › Cabinet</Link>, puis revenez ici.
      </p>

      {erreur && (
        <p role="alert" className="flex items-start gap-2 rounded-lg bg-danger/10 px-3 py-2 text-sm text-danger">
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
          <span>{erreur}</span>
        </p>
      )}

      {!apercu ? (
        <div>
          <Button onClick={afficher} loading={envoi} disabled={envoi}>Afficher le contrat à accepter</Button>
        </div>
      ) : (
        <>
          {apercu.manquants.length > 0 && (
            <div role="note" className="rounded-md border border-warning/40 bg-warning/10 p-3 text-sm text-fg">
              <p className="font-medium">Mentions non renseignées dans ce contrat :</p>
              <ul className="ml-5 list-disc">
                {apercu.manquants.map((m) => <li key={m}>{m}</li>)}
              </ul>
              <p className="mt-1 text-xs text-fg-muted">
                Vous pouvez accepter en l’état : le contrat et son exemplaire les signaleront. Les mentions du cabinet
                se complètent dans Paramètres › Cabinet ; celles de l’Éditeur relèvent de JURIKA.
              </p>
            </div>
          )}
          <div
            className="max-h-[32rem] overflow-y-auto rounded-md border border-border bg-bg p-4"
            tabIndex={0}
            aria-label="Texte du contrat d’abonnement"
          >
            <TexteContrat texte={apercu.texte} />
          </div>
          <p className="break-all text-xs text-fg-subtle">
            Empreinte du texte (SHA-256) : <code>{apercu.empreinte}</code>
          </p>
          <label className="flex items-start gap-2 text-sm text-fg">
            <input
              type="checkbox"
              checked={caseCochee}
              onChange={(e) => setCaseCochee(e.target.checked)}
              className="mt-0.5"
            />
            <span>J’ai lu et j’accepte le contrat d’abonnement JURIKA</span>
          </label>
          <div>
            <Button onClick={() => setConfirmer(true)} disabled={!caseCochee || envoi}>Accepter le contrat</Button>
          </div>
        </>
      )}

      <ConfirmDialog
        open={confirmer}
        onOpenChange={setConfirmer}
        title="Confirmer l’acceptation du contrat"
        description="Votre acceptation engage le cabinet. Elle est enregistrée sans possibilité de modification : texte et empreinte du contrat, votre compte et votre qualité, la date et l’heure (heure de Casablanca) et votre adresse IP."
        confirmLabel="Je confirme mon acceptation"
        cancelLabel="Revenir au contrat"
        variant="primary"
        loading={envoi}
        onConfirm={accepter}
      />
    </Card>
  );
}

function BlocContratCourant({ etat, conditions }: { etat: EtatContrat; conditions: ConditionsVue }) {
  const toast = useToast();
  const a = etat.derniereAcceptation;
  const [travail, setTravail] = useState(false);

  async function telecharger(acc: AcceptationVue) {
    setTravail(true);
    try {
      enregistrerFichier(await contratService.exemplaire(acc.id), `contrat-abonnement-jurika-${conditions.reference}.pdf`);
    } catch (e) {
      toast.error(extractError(e).message);
    } finally {
      setTravail(false);
    }
  }

  async function renvoyer(acc: AcceptationVue) {
    setTravail(true);
    try {
      await contratService.renvoyerCopie(acc.id);
      toast.success(`Copie envoyée à ${acc.cabinetEmail ?? 'l’adresse du cabinet'}.`);
    } catch (e) {
      toast.error(extractError(e).message);
    } finally {
      setTravail(false);
    }
  }

  return (
    <Card className="flex flex-col gap-3 p-4">
      <h2 className="text-lg font-semibold text-fg">Contrat en cours</h2>
      <ResumeConditions c={conditions} />
      {etat.avertissementEcheance && etat.joursAvantEcheance !== null && (
        <p role="status" className="rounded-md bg-warning/10 px-3 py-2 text-sm text-fg">
          Échéance dans {etat.joursAvantEcheance} jour{etat.joursAvantEcheance > 1 ? 's' : ''}, le{' '}
          {dateLongue(conditions.echeanceCourante)} : sauf résiliation, l’abonnement se renouvelle pour un an aux
          conditions en vigueur (article 5 des conditions générales).
        </p>
      )}
      {a && (
        <div className="text-sm text-fg-muted">
          <p>
            Accepté le {horodatageCasablanca(a.accepteLeCasablanca)} par {a.signataire} ({a.signataireQualite}),
            depuis le compte {a.signataireCompte}. Conditions générales : version {a.versionCode}.
          </p>
          <p className="break-all text-xs text-fg-subtle">Empreinte : {a.empreinteContrat}</p>
          <div className="mt-2 flex flex-wrap gap-2">
            <Button size="sm" onClick={() => telecharger(a)} disabled={travail}>
              <Download className="mr-1 h-4 w-4" aria-hidden="true" /> Télécharger l’exemplaire (PDF)
            </Button>
            <Button size="sm" variant="secondary" onClick={() => renvoyer(a)} disabled={travail}>
              <Mail className="mr-1 h-4 w-4" aria-hidden="true" /> Renvoyer la copie par courriel
            </Button>
          </div>
        </div>
      )}
    </Card>
  );
}

function BlocResiliation({ conditions, onResilie }: { conditions: ConditionsVue; onResilie: () => void }) {
  const [motif, setMotif] = useState('');
  const [confirmer, setConfirmer] = useState(false);
  const [envoi, setEnvoi] = useState(false);
  const [erreur, setErreur] = useState<string | null>(null);

  async function resilier() {
    setEnvoi(true);
    setErreur(null);
    try {
      await contratService.resilier(motif.trim());
      setConfirmer(false);
      onResilie();
    } catch (e) {
      setErreur(extractError(e).message);
      setConfirmer(false);
    } finally {
      setEnvoi(false);
    }
  }

  return (
    <Card className="flex flex-col gap-2 p-4">
      <h2 className="text-lg font-semibold text-fg">
        Résilier le contrat{' '}
        <InfoBulle
          libelle="Effets de la résiliation"
          texte="La résiliation prend effet à l’échéance du contrat. À cette date, le forfait s’arrête, les accès sont fermés (y compris ceux de vos clients à leur Data Room) et tout le travail du cabinet est archivé, jamais supprimé."
        />
      </h2>
      <p className="text-sm text-fg-muted">
        Date d’effet : à l’échéance, le {dateLongue(conditions.echeanceCourante)}. Indiquez le motif de votre décision.
      </p>
      <label className="flex flex-col gap-1 text-sm">
        <span>Motif de la résiliation *</span>
        <textarea
          value={motif}
          onChange={(e) => setMotif(e.target.value)}
          rows={2}
          className="rounded-md border border-border bg-bg px-2 py-1.5"
        />
      </label>
      {erreur && <p role="alert" className="text-sm text-danger">{erreur}</p>}
      <div>
        <Button variant="danger" size="sm" onClick={() => setConfirmer(true)} disabled={motif.trim() === '' || envoi}>
          Demander la résiliation
        </Button>
      </div>
      <ConfirmDialog
        open={confirmer}
        onOpenChange={setConfirmer}
        title="Confirmer la résiliation"
        description={`Le forfait s’arrêtera le ${dateLongue(conditions.echeanceCourante)}. Le travail du cabinet sera archivé, jamais supprimé ; vous pourrez le reprendre en souscrivant de nouveau.`}
        confirmLabel="Résilier le contrat"
        variant="danger"
        loading={envoi}
        onConfirm={resilier}
      />
    </Card>
  );
}

function BlocHistorique({ historique }: { historique: HistoriqueContrat }) {
  const toast = useToast();
  async function telecharger(a: AcceptationVue) {
    try {
      enregistrerFichier(await contratService.exemplaire(a.id), `contrat-abonnement-jurika-${a.versionCode}-${a.id.slice(0, 8)}.pdf`);
    } catch (e) {
      toast.error(extractError(e).message);
    }
  }
  const echecs = historique.avis.filter((a) => a.statut === 'ECHEC');
  return (
    <Card className="flex flex-col gap-3 p-4">
      <h2 className="text-lg font-semibold text-fg">
        Historique du contrat{' '}
        <InfoBulle
          libelle="Conservation"
          texte="Conditions particulières successives, acceptations, renouvellements et résiliation : rien n’est supprimé, l’historique est conservé au moins dix ans."
        />
      </h2>
      {historique.conditions.length === 0 ? (
        <p className="text-sm text-fg-subtle">Aucune condition particulière enregistrée.</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-fg-subtle">
                <th scope="col" className="py-1 pr-3">Référence</th>
                <th scope="col" className="py-1 pr-3">Forfait</th>
                <th scope="col" className="py-1 pr-3">Prix</th>
                <th scope="col" className="py-1 pr-3">Début</th>
                <th scope="col" className="py-1">État</th>
              </tr>
            </thead>
            <tbody>
              {historique.conditions.map((c) => (
                <tr key={c.id} className="border-t border-border">
                  <td className="py-1 pr-3">{c.reference}</td>
                  <td className="py-1 pr-3">{c.forfaitLibelle}</td>
                  <td className="py-1 pr-3">{prix(c)}</td>
                  <td className="py-1 pr-3">{dateLongue(c.dateDebut)}</td>
                  <td className="py-1">{LIBELLE_STATUT[c.statut] ?? c.statut}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {historique.acceptations.length > 0 && (
        <ul className="flex flex-col gap-1 text-sm">
          {historique.acceptations.map((a) => (
            <li key={a.id} className="flex flex-wrap items-center gap-2">
              <span>
                {a.objet === 'NOUVELLE_VERSION' ? 'Nouvelle version acceptée' : 'Conditions acceptées'} le{' '}
                {horodatageCasablanca(a.accepteLeCasablanca)} par {a.signataire} (version {a.versionCode})
              </span>
              <Button size="sm" variant="ghost" onClick={() => telecharger(a)}>
                <Download className="mr-1 h-4 w-4" aria-hidden="true" /> Exemplaire
              </Button>
            </li>
          ))}
        </ul>
      )}
      {historique.renouvellements.map((r) => (
        <p key={r.id} className="text-sm text-fg-muted">
          Renouvelé : échéance du {dateLongue(r.ancienneEcheance)} reportée au {dateLongue(r.nouvelleEcheance)}.
        </p>
      ))}
      {historique.resiliations.map((r) => (
        <p key={r.conditionsId} className="text-sm text-fg-muted">
          Résiliation {r.demandeurRole === 'SUPER_ADMIN' ? 'enregistrée par JURIKA' : 'demandée en ligne'}, effet le{' '}
          {dateLongue(r.dateEffet)} — motif : {r.motif}
        </p>
      ))}
      {echecs.length > 0 && (
        <p role="alert" className="text-sm text-danger">
          {echecs.length} courriel{echecs.length > 1 ? 's' : ''} du contrat n’a pas pu être envoyé (dernier motif :{' '}
          {echecs[0].erreur ?? 'inconnu'}). Utilisez « Renvoyer la copie par courriel » ou contactez JURIKA.
        </p>
      )}
    </Card>
  );
}

function BlocVersions({ versions }: { versions: VersionVue[] }) {
  const [ouverte, setOuverte] = useState<string | null>(null);
  const [texte, setTexte] = useState<string | null>(null);
  const [erreur, setErreur] = useState<string | null>(null);

  async function lire(code: string) {
    if (ouverte === code) {
      setOuverte(null);
      return;
    }
    setOuverte(code);
    setTexte(null);
    setErreur(null);
    try {
      setTexte(await contratService.versionTexte(code));
    } catch (e) {
      setErreur(extractError(e).message);
    }
  }

  return (
    <Card className="flex flex-col gap-2 p-4">
      <h2 className="text-lg font-semibold text-fg">Versions du contrat</h2>
      <p className="text-sm text-fg-muted">
        Toutes les versions des conditions générales et de l’accord de traitement des données sont conservées et
        consultables (modèle, sans vos conditions particulières).
      </p>
      {versions.length === 0 ? (
        <p className="text-sm text-fg-subtle">Aucune version publiée.</p>
      ) : (
        <ul className="flex flex-col gap-1 text-sm">
          {versions.map((v) => (
            <li key={v.id}>
              <button type="button" className="underline" onClick={() => lire(v.code)} aria-expanded={ouverte === v.code}>
                Version {v.code}
              </button>{' '}
              — en vigueur le {dateLongue(v.entreeEnVigueur)}
              {ouverte === v.code && (
                <div className="mt-2 max-h-96 overflow-y-auto rounded-md border border-border p-3">
                  {erreur && <p role="alert" className="text-danger">{erreur}</p>}
                  {texte === null && !erreur ? <p>Chargement…</p> : texte && <TexteContrat texte={texte} />}
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}
