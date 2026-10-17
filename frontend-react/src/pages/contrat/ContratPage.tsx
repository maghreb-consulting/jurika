import { useCallback, useEffect, useState } from 'react';
import { ChevronDown, Download, FileSignature, Mail } from 'lucide-react';
import { Page } from '../../components/layout/Page';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { Case } from '../../components/ui/Choix';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { InfoBulle, TexteAide } from '../../components/ui/Aide';
import { Lien } from '../../components/ui/Lien';
import { CardSkeleton, SqueletteTexte, SqueletteZone } from '../../components/ui/LoadingSkeleton';
import { Message } from '../../components/ui/Message';
import { Select } from '../../components/ui/Select';
import { Tableau } from '../../components/ui/Tableau';
import { TextField, ZoneTexte } from '../../components/ui/TextField';
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
 *
 * Lot L6b (E8) : structure unique <Page>, cartes, champs et messages de la bibliotheque.
 * Logique (apercu, empreinte, acceptation, refus, resiliation) inchangee.
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
    <Page
      titre="Contrat d’abonnement"
      largeur="etroite"
      infoBulle="Le contrat qui lie votre cabinet à Maghreb Consulting, éditeur de JURIKA : conditions particulières (forfait, prix, dates), conditions générales d’abonnement et accord de traitement des données personnelles."
      description="Acceptation en ligne, exemplaire, échéance, résiliation et historique."
      aide={
        <TexteAide cle="contrat-abonnement" titre="Votre contrat d’abonnement">
          <p>
            Quand JURIKA met en place ou modifie votre abonnement, de nouvelles conditions particulières vous sont
            présentées ici : lisez le contrat complet, cochez la case d’acceptation, puis confirmez. Tant qu’elles ne
            sont pas acceptées, vous et vos employés n’accédez qu’à cet écran. Une fois le contrat accepté,
            l’exemplaire complet (PDF) reste téléchargeable à tout moment et une copie est envoyée à l’adresse
            électronique du cabinet. Quand une nouvelle version des conditions générales entre en vigueur, elle vous
            est présentée ici à chaque connexion jusqu’à votre réponse : « Accepter » ou « Refuser » (un refus vaut
            résiliation sans frais à l’échéance).
          </p>
        </TexteAide>
      }
    >
      <div className="flex flex-col gap-4">
        {erreur && <Message ton="erreur">{erreur}</Message>}

        {chargement ? (
          <SqueletteZone libelle="Chargement du contrat…">
            <div className="flex flex-col gap-4">
              <CardSkeleton className="h-56" />
              <CardSkeleton className="h-40" />
            </div>
          </SqueletteZone>
        ) : etat ? (
          <>
            {etat.acceptationRequise && (
              <BlocAcceptation
                etat={etat}
                onAccepte={() => apresChangement('Contrat accepté. Une copie est envoyée par courriel.')}
                onRefuse={() =>
                  apresChangement('Refus enregistré : votre contrat sera résilié sans frais à l’échéance.')
                }
              />
            )}
            {etat.statut === 'AUCUN' && !etat.acceptationRequise && (
              <Card className="p-5">
                <div className="flex items-start gap-3 text-sm text-fg-muted">
                  <FileSignature className="mt-0.5 h-5 w-5 shrink-0 text-fg-subtle" aria-hidden="true" />
                  <p>
                    Aucun contrat d’abonnement n’est encore enregistré pour votre cabinet. Il apparaîtra ici dès que
                    JURIKA aura saisi vos conditions particulières (forfait, prix, dates), ou à votre souscription d’un
                    forfait.
                  </p>
                </div>
              </Card>
            )}
            {etat.versionAVenir && (
              <Message
                ton="info"
                titre={`Nouvelle version des conditions générales : version ${etat.versionAVenir.code}, en vigueur le ${dateLongue(etat.versionAVenir.entreeEnVigueur)}.`}
              >
                Elle vous sera présentée à votre première connexion après cette date : vous pourrez l’accepter ou la
                refuser. Un refus vaut résiliation sans frais à l’échéance ; jusque-là, la version actuelle continue de
                s’appliquer (article 16). Son texte est consultable plus bas, dans « Versions du contrat ».
              </Message>
            )}
            {etat.courant && <BlocContratCourant etat={etat} conditions={etat.courant} />}
            {etat.resiliation && (
              <Message
                ton="attention"
                titre={`Résiliation enregistrée : le forfait s’arrête le ${dateLongue(etat.resiliation.dateEffet)}.`}
              >
                Motif : {etat.resiliation.motif}. À cette date, l’accès est fermé et tout le travail du cabinet est
                archivé, jamais supprimé : vous pourrez toujours vous connecter pour exporter vos données ou télécharger
                votre contrat, et réactiver le cabinet en souscrivant de nouveau.
              </Message>
            )}
            {etat.courant && !etat.acceptationRequise && (
              <BlocResiliation conditions={etat.courant} onResilie={() => apresChangement('Résiliation enregistrée.')} />
            )}
            {historique && <BlocHistorique historique={historique} />}
            <BlocVersions versions={versions} />
          </>
        ) : null}
      </div>
    </Page>
  );
}

function ResumeConditions({ c }: { c: ConditionsVue }) {
  const lignes: [string, string][] = [
    ['Référence', c.reference],
    ['Forfait', c.forfaitLibelle],
    ['Rythme de paiement', LIBELLE_RYTHME[c.rythmePaiement]],
    ['Prix', prix(c)],
    ['Début', dateLongue(c.dateDebut)],
    ['Échéance', dateLongue(c.echeanceCourante)],
    ['Mode de paiement', c.modePaiement ? LIBELLE_MODE[c.modePaiement] : 'non renseigné'],
  ];
  return (
    <dl className="grid grid-cols-1 gap-x-6 gap-y-2 rounded-lg border border-border bg-bg-overlay p-3 text-sm sm:grid-cols-2">
      {lignes.map(([libelle, valeur]) => (
        <div key={libelle}>
          <dt className="inline text-fg-muted">{libelle} : </dt>
          <dd className="inline text-fg">{valeur}</dd>
        </div>
      ))}
    </dl>
  );
}

const SIGNATAIRE_VIDE: SignataireSaisie = { civilite: '', qualite: '', formeCabinet: '', rcVilleCabinet: '' };

function BlocAcceptation({
  etat,
  onAccepte,
  onRefuse,
}: {
  etat: EtatContrat;
  onAccepte: () => void;
  onRefuse: () => void;
}) {
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
  const [confirmerRefus, setConfirmerRefus] = useState(false);
  const [envoi, setEnvoi] = useState(false);
  const [erreur, setErreur] = useState<string | null>(null);
  const nouvelleVersion = etat.objetAAccepter === 'NOUVELLE_VERSION';
  const conditions = etat.enAttente ?? etat.courant;
  const versionNouvelle = etat.versionEnVigueur?.code ?? '';
  const versionAnterieure = etat.derniereAcceptation?.versionCode ?? null;
  const echeance = etat.courant ? dateLongue(etat.courant.echeanceCourante) : null;

  async function refuser() {
    setEnvoi(true);
    setErreur(null);
    try {
      await contratService.refuserVersion(versionNouvelle);
      setConfirmerRefus(false);
      onRefuse();
    } catch (e) {
      setErreur(extractError(e).message);
      setConfirmerRefus(false);
    } finally {
      setEnvoi(false);
    }
  }

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
    <Card
      accent
      aria-label={nouvelleVersion ? 'Nouvelle version à accepter ou refuser' : 'Contrat à accepter'}
      title={
        nouvelleVersion
          ? `Nouvelle version des conditions générales : votre réponse est attendue (version ${versionNouvelle})`
          : 'Contrat d’abonnement à accepter'
      }
      description={
        nouvelleVersion
          ? `Cette version est en vigueur${etat.versionEnVigueur ? ` depuis le ${dateLongue(etat.versionEnVigueur.entreeEnVigueur)}` : ''}. Lisez-la, puis acceptez-la ou refusez-la. Vous pouvez continuer à utiliser la plateforme en attendant : cette demande vous sera présentée à chaque connexion tant que vous n’aurez pas répondu.`
          : 'JURIKA a saisi les conditions particulières de votre abonnement. Tant que vous ne les avez pas acceptées, l’accès à la plateforme est limité à cet écran, pour vous et pour vos employés.'
      }
    >
      <div className="flex flex-col gap-4">
        {nouvelleVersion && (
          <TexteAide cle="contrat-nouvelle-version" titre="Accepter ou refuser la nouvelle version (article 16)">
            <p>
              <strong>Accepter</strong> : affichez le contrat, lisez-le, cochez la case puis confirmez ; la nouvelle
              version s’applique dès votre acceptation. <strong>Refuser</strong> : votre contrat est résilié sans frais à
              l’échéance{echeance ? `, le ${echeance}` : ''} ; jusque-là, la version antérieure
              {versionAnterieure ? ` (version ${versionAnterieure})` : ''} continue de s’appliquer. Votre réponse est
              définitive et enregistrée sans possibilité de modification.
            </p>
          </TexteAide>
        )}
        {conditions && <ResumeConditions c={conditions} />}

        <fieldset className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <legend className="mb-2 flex items-center gap-1 text-sm font-medium text-fg">
            Vos mentions de signataire
            <InfoBulle
              libelle="Mentions du signataire"
              texte="Ces mentions sont imprimées dans le contrat. Votre nom, votre prénom et votre compte viennent de votre profil ; la dénomination, l’adresse, le numéro de RC, l’ICE et l’adresse électronique du cabinet viennent de Paramètres › Cabinet."
            />
          </legend>
          <Select
            label="Civilité *"
            value={signataire.civilite}
            onChange={(e) => changer('civilite', e.target.value)}
            options={[
              { value: '', label: '— Choisir —' },
              { value: 'M.', label: 'M.' },
              { value: 'Mme', label: 'Mme' },
            ]}
          />
          <TextField
            label="Qualité au sein du cabinet *"
            value={signataire.qualite}
            onChange={(e) => changer('qualite', e.target.value)}
            placeholder="gérant, associé, directeur…"
          />
          <TextField
            label="Forme juridique du cabinet"
            value={signataire.formeCabinet}
            onChange={(e) => changer('formeCabinet', e.target.value)}
            placeholder="SARL, société civile professionnelle…"
          />
          <TextField
            label="Ville du registre du commerce du cabinet"
            value={signataire.rcVilleCabinet}
            onChange={(e) => changer('rcVilleCabinet', e.target.value)}
            placeholder="Casablanca, Rabat…"
          />
        </fieldset>
        <p className="text-xs text-fg-muted">
          Une mention laissée vide figure dans le contrat sous la forme « [non renseigné : …] ». Pour compléter
          l’identité du cabinet, ouvrez <Lien to="/settings/cabinet">Paramètres › Cabinet</Lien>, puis revenez ici.
        </p>

        {erreur && <Message ton="erreur">{erreur}</Message>}

        {!apercu ? (
          <div className="flex flex-wrap gap-2">
            <Button type="button" onClick={afficher} loading={envoi}>
              {nouvelleVersion ? 'Afficher la nouvelle version pour l’accepter' : 'Afficher le contrat à accepter'}
            </Button>
            {nouvelleVersion && (
              <Button
                type="button"
                variant="danger"
                onClick={() => setConfirmerRefus(true)}
                disabled={envoi || versionNouvelle === ''}
              >
                Refuser
              </Button>
            )}
          </div>
        ) : (
          <>
            {apercu.manquants.length > 0 && (
              <div role="note" className="rounded-lg border border-border border-l-4 border-l-warning bg-warning-soft p-3 text-sm text-fg">
                <p className="font-medium">Mentions non renseignées dans ce contrat :</p>
                <ul className="ml-5 list-disc">
                  {apercu.manquants.map((m) => (
                    <li key={m}>{m}</li>
                  ))}
                </ul>
                <p className="mt-1 text-xs text-fg-muted">
                  Vous pouvez accepter en l’état : le contrat et son exemplaire les signaleront. Les mentions du cabinet
                  se complètent dans Paramètres › Cabinet ; celles de l’Éditeur relèvent de JURIKA.
                </p>
              </div>
            )}
            {/* Texte long : defilement interne de l'encadre, la page defile autour. */}
            <div
              className="max-h-[32rem] overflow-y-auto rounded-lg border border-border bg-bg p-4 focus-visible:outline-2 focus-visible:outline-focus"
              tabIndex={0}
              aria-label="Texte du contrat d’abonnement"
            >
              <TexteContrat texte={apercu.texte} />
            </div>
            <p className="break-all text-xs text-fg-muted">
              Empreinte du texte (SHA-256) : <code className="font-mono">{apercu.empreinte}</code>
            </p>
            <Case
              checked={caseCochee}
              onChange={(e) => setCaseCochee(e.target.checked)}
              label={
                nouvelleVersion
                  ? `J’ai lu et j’accepte le contrat d’abonnement JURIKA, conditions générales version ${versionNouvelle}`
                  : 'J’ai lu et j’accepte le contrat d’abonnement JURIKA'
              }
            />
            <div className="flex flex-wrap gap-2">
              <Button type="button" onClick={() => setConfirmer(true)} disabled={!caseCochee || envoi}>
                {nouvelleVersion ? 'Accepter' : 'Accepter le contrat'}
              </Button>
              {nouvelleVersion && (
                <Button type="button" variant="danger" onClick={() => setConfirmerRefus(true)} disabled={envoi}>
                  Refuser
                </Button>
              )}
            </div>
          </>
        )}
      </div>

      {nouvelleVersion && (
        <ConfirmDialog
          open={confirmerRefus}
          onOpenChange={setConfirmerRefus}
          title={`Refuser la version ${versionNouvelle} des conditions générales ?`}
          description={`Votre refus vaut résiliation sans frais du contrat à l’échéance${echeance ? `, le ${echeance}` : ''} (article 16). Jusque-là, la version antérieure des conditions générales${versionAnterieure ? ` (version ${versionAnterieure})` : ''} continue de s’appliquer. À l’échéance, le forfait s’arrête et le travail du cabinet est archivé, jamais supprimé ; vous pourrez le reprendre en souscrivant de nouveau. Votre réponse est enregistrée sans possibilité de modification : version, votre compte, la date et l’heure (heure de Casablanca) et votre adresse IP.`}
          confirmLabel="Je refuse cette version"
          cancelLabel="Revenir au contrat"
          variant="danger"
          loading={envoi}
          onConfirm={refuser}
        />
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
    <Card title="Contrat en cours">
      <div className="flex flex-col gap-3">
        <ResumeConditions c={conditions} />
        {etat.avertissementEcheance && etat.joursAvantEcheance !== null && (
          <Message ton="attention">
            Échéance dans {etat.joursAvantEcheance} jour{etat.joursAvantEcheance > 1 ? 's' : ''}, le{' '}
            {dateLongue(conditions.echeanceCourante)} : sauf résiliation, l’abonnement se renouvelle pour un an aux
            conditions en vigueur (article 5 des conditions générales).
          </Message>
        )}
        {a && (
          <div className="text-sm text-fg-muted">
            <p>
              Accepté le {horodatageCasablanca(a.accepteLeCasablanca)} par {a.signataire} ({a.signataireQualite}),
              depuis le compte {a.signataireCompte}. Conditions générales : version {a.versionCode}.
            </p>
            <p className="break-all text-xs text-fg-muted">Empreinte : {a.empreinteContrat}</p>
            <div className="mt-3 flex flex-wrap gap-2">
              <Button type="button" size="sm" variant="secondary" onClick={() => telecharger(a)} disabled={travail} icon={<Download />}>
                Télécharger l’exemplaire (PDF)
              </Button>
              <Button type="button" size="sm" variant="secondary" onClick={() => renvoyer(a)} disabled={travail} icon={<Mail />}>
                Renvoyer la copie par courriel
              </Button>
            </div>
          </div>
        )}
      </div>
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
    <Card
      title={
        <span className="flex items-center gap-1">
          Résilier le contrat
          <InfoBulle
            libelle="Effets de la résiliation"
            texte="La résiliation prend effet à l’échéance du contrat. À cette date, le forfait s’arrête, les accès sont fermés (y compris ceux de vos clients à leur Data Room) et tout le travail du cabinet est archivé, jamais supprimé."
          />
        </span>
      }
      description={`Date d’effet : à l’échéance, le ${dateLongue(conditions.echeanceCourante)}. Indiquez le motif de votre décision.`}
    >
      <div className="flex flex-col gap-3">
        <ZoneTexte label="Motif de la résiliation *" value={motif} onChange={(e) => setMotif(e.target.value)} rows={2} />
        {erreur && <Message ton="erreur">{erreur}</Message>}
        <div>
          <Button
            type="button"
            variant="danger"
            size="sm"
            onClick={() => setConfirmer(true)}
            disabled={motif.trim() === '' || envoi}
          >
            Demander la résiliation
          </Button>
        </div>
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
    <Card
      title={
        <span className="flex items-center gap-1">
          Historique du contrat
          <InfoBulle
            libelle="Conservation"
            texte="Conditions particulières successives, acceptations, réponses aux nouvelles versions, renouvellements et résiliation : rien n’est supprimé, l’historique est conservé au moins dix ans."
          />
        </span>
      }
    >
      <div className="flex flex-col gap-3">
        {historique.conditions.length === 0 ? (
          <p className="text-sm text-fg-muted">Aucune condition particulière enregistrée.</p>
        ) : (
          <Tableau
            legende="Conditions particulières successives"
            enTeteCollant={false}
            lignes={historique.conditions}
            cleLigne={(c) => c.id}
            colonnes={[
              { cle: 'reference', titre: 'Référence', rendu: (c) => c.reference },
              { cle: 'forfait', titre: 'Forfait', rendu: (c) => c.forfaitLibelle },
              { cle: 'prix', titre: 'Prix', rendu: (c) => prix(c) },
              { cle: 'debut', titre: 'Début', rendu: (c) => dateLongue(c.dateDebut), masquerSous: 'md' },
              { cle: 'etat', titre: 'État', rendu: (c) => LIBELLE_STATUT[c.statut] ?? c.statut },
            ]}
          />
        )}
        {historique.acceptations.length > 0 && (
          <ul className="flex flex-col gap-2 text-sm">
            {historique.acceptations.map((a) => (
              <li key={a.id} className="flex flex-wrap items-center gap-2">
                <span className="text-fg">
                  {a.objet === 'NOUVELLE_VERSION' ? 'Nouvelle version acceptée' : 'Conditions acceptées'} le{' '}
                  {horodatageCasablanca(a.accepteLeCasablanca)} par {a.signataire} (version {a.versionCode})
                </span>
                <Button type="button" size="sm" variant="secondary" onClick={() => telecharger(a)} icon={<Download />}>
                  Exemplaire
                </Button>
              </li>
            ))}
          </ul>
        )}
        {historique.reponses.length > 0 && (
          <ul className="flex flex-col gap-1 text-sm text-fg" aria-label="Réponses aux nouvelles versions">
            {historique.reponses.map((r) => (
              <li key={r.id}>
                Conditions générales, version {r.versionCode}, {r.reponse === 'ACCEPTEE' ? 'acceptées' : 'refusées'} le{' '}
                {horodatageCasablanca(r.reponduLeCasablanca)} depuis le compte {r.signataireCompte} (adresse IP{' '}
                {r.adresseIp})
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
          <Message ton="erreur">
            {echecs.length} courriel{echecs.length > 1 ? 's' : ''} du contrat n’a pas pu être envoyé (dernier motif :{' '}
            {echecs[0].erreur ?? 'inconnu'}). Utilisez « Renvoyer la copie par courriel » ou contactez JURIKA.
          </Message>
        )}
      </div>
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
    <Card
      title="Versions du contrat"
      description="Toutes les versions des conditions générales et de l’accord de traitement des données sont conservées et consultables (modèle, sans vos conditions particulières)."
    >
      {versions.length === 0 ? (
        <p className="text-sm text-fg-muted">Aucune version publiée.</p>
      ) : (
        <ul className="flex flex-col gap-2 text-sm">
          {versions.map((v) => (
            <li key={v.id}>
              <div className="flex flex-wrap items-center gap-2">
                <Button
                  type="button"
                  size="sm"
                  variant="secondary"
                  onClick={() => lire(v.code)}
                  aria-expanded={ouverte === v.code}
                  iconEnd={<ChevronDown className={ouverte === v.code ? 'rotate-180' : undefined} />}
                >
                  Version {v.code}
                </Button>
                <span className="text-fg-muted">en vigueur le {dateLongue(v.entreeEnVigueur)}</span>
              </div>
              {ouverte === v.code && (
                <div className="mt-2 max-h-96 overflow-y-auto rounded-lg border border-border bg-bg p-3">
                  {erreur && <Message ton="erreur">{erreur}</Message>}
                  {texte === null && !erreur ? (
                    <SqueletteZone libelle="Chargement de la version…">
                      <SqueletteTexte lignes={6} />
                    </SqueletteZone>
                  ) : (
                    texte && <TexteContrat texte={texte} />
                  )}
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}
