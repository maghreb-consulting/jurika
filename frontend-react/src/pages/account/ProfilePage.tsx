import { useCallback, useEffect, useState, type ReactNode } from 'react';
import axios from 'axios';
import { AlertCircle, CheckCircle, Clock, KeyRound, Loader2, Lock, Mail, User } from 'lucide-react';
import { Button } from '../../components/ui/Button';
import { TextField } from '../../components/ui/TextField';
import { InfoBulle, TexteAide } from '../../components/ui/Aide';
import { ReafficherAides } from '../../components/ui/ReafficherAides';
import { authService, type DemandeCompte, type DemandesCompte } from '../../services/auth.service';
import { extractError, tokenStorage } from '../../lib/api';
import { emailMsg } from '../../lib/formValidation';
import { dateHeure, messageCompte } from './messagesCompte';

interface MeData {
  userId: string;
  workspaceId: string;
  email: string;
  loginEmail: string;
  contactEmail: string;
  firstName?: string;
  lastName?: string;
  phone?: string;
  role: string;
  status?: string;
}

type Retour = { kind: 'success' | 'error'; message: string } | null;

const LIBELLES_ROLE: Record<string, string> = {
  SUPER_ADMIN: 'Super-administrateur',
  SUPERVISEUR: 'Superviseur',
  EMPLOYE: 'Employé',
  CLIENT: 'Client',
};

/**
 * Lot L5 (E10) : ecran "Mon compte" (CDC 3.6, RG-CPT-01 et RG-CPT-02).
 *  - prenom et nom : modifies directement (traces) ;
 *  - adresse electronique et identifiant de connexion : une demande envoie un
 *    lien de confirmation ; tant qu'il n'est pas confirme, l'ancienne valeur
 *    reste active et l'ecran affiche la demande "en attente de confirmation" ;
 *  - mot de passe : le mot de passe actuel est exige.
 * Le client consulte ses informations sans les modifier (decision a revoir du
 * lot L5 : RG-CPT-01 nomme l'employe et le superviseur).
 */
export function ProfilePage() {
  const [me, setMe] = useState<MeData | null>(null);
  const [demandes, setDemandes] = useState<DemandesCompte>({ adresse: null, identifiant: null });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Lecture du compte et des demandes en attente (au montage et apres chaque modification).
  const lireCompte = useCallback(
    () =>
      Promise.all([authService.getMe(), authService.getDemandesCompte()])
        .then(([data, attente]) => {
          setMe(data);
          setDemandes(attente);
          setError(null);
        })
        .catch((err) => setError(extractError(err).message))
        .finally(() => setLoading(false)),
    [],
  );

  useEffect(() => {
    void lireCompte();
  }, [lireCompte]);

  const charger = lireCompte;

  if (loading) {
    return (
      <div className="flex h-64 items-center justify-center">
        <Loader2 className="h-6 w-6 animate-spin text-accent" />
      </div>
    );
  }

  if (error || !me) {
    return (
      <div className="rounded-lg border border-danger/40 bg-danger/10 px-4 py-3 text-sm text-danger">
        {error ?? 'Le chargement de votre compte a échoué.'}
      </div>
    );
  }

  const modifiable = me.role !== 'CLIENT';

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div>
        <h1 className="flex items-center gap-2 font-heading text-2xl font-semibold text-fg">
          <User className="h-6 w-6 text-accent" /> Mon compte
        </h1>
        <p className="mt-1 text-sm text-fg-muted">
          Vos informations personnelles, votre adresse électronique, votre identifiant de connexion
          et votre mot de passe.
        </p>
      </div>

      {modifiable ? (
        <TexteAide cle="mon-compte" titre="Comment modifier votre compte">
          Le prénom et le nom changent dès l’enregistrement. Un changement d’adresse électronique ou
          d’identifiant de connexion ne prend effet qu’après un clic sur le lien de confirmation reçu
          par courriel : d’ici là, vos valeurs actuelles restent actives. Le mot de passe actuel est
          demandé pour en choisir un nouveau.
        </TexteAide>
      ) : (
        <TexteAide cle="mon-compte-client" titre="Vos informations">
          Votre nom, votre adresse électronique et votre identifiant sont enregistrés par le cabinet
          qui vous a invité : pour les faire modifier, adressez-lui une demande. Vous pouvez changer
          vous-même votre mot de passe ci-dessous.
        </TexteAide>
      )}

      <BlocIdentite me={me} modifiable={modifiable} onModifie={charger} />

      <BlocChangement
        titre="Adresse électronique"
        icone={<Mail className="h-3.5 w-3.5" />}
        aide="Adresse à laquelle JURIKA vous écrit : alertes de sécurité, liens de confirmation. Le lien de confirmation part à la NOUVELLE adresse ; l’adresse actuelle reçoit un avis de la demande."
        valeur={me.contactEmail}
        testId="profile-contact-email"
        attente={demandes.adresse}
        destinationLien="à la nouvelle adresse"
        libelleChamp="Nouvelle adresse électronique"
        placeholder="prenom.nom@exemple.ma"
        motif="adresse"
        modifiable={modifiable}
        envoyer={(v) => authService.demanderChangementAdresse(v)}
        onModifie={charger}
      />

      <BlocChangement
        titre="Identifiant de connexion"
        icone={<Lock className="h-3.5 w-3.5" />}
        aide="Identifiant à saisir sur la page de connexion, avec le code du cabinet. Le lien de confirmation part à votre adresse électronique actuelle. Après confirmation, connectez-vous avec le nouvel identifiant."
        valeur={me.loginEmail}
        testId="profile-login-email"
        attente={demandes.identifiant}
        destinationLien={`à votre adresse ${me.contactEmail}`}
        libelleChamp="Nouvel identifiant de connexion"
        placeholder="prenom.nom@jurika.ma"
        motif="identifiant"
        modifiable={modifiable}
        envoyer={(v) => authService.demanderChangementIdentifiant(v)}
        onModifie={charger}
      />

      <BlocMotDePasse />

      {/* Lot L6 (E6, CDC 15.4) : reafficher les aides masquees et la visite guidee. */}
      <ReafficherAides />
    </div>
  );
}

function Message({ retour }: { retour: Retour }) {
  if (!retour) return null;
  return (
    <div
      role={retour.kind === 'error' ? 'alert' : 'status'}
      className={`mt-3 flex items-start gap-2 rounded-lg border px-3 py-2 text-sm ${
        retour.kind === 'success'
          ? 'border-emerald-200 bg-emerald-50 text-emerald-800'
          : 'border-danger/40 bg-danger/10 text-danger'
      }`}
    >
      {retour.kind === 'success' ? (
        <CheckCircle className="mt-0.5 h-4 w-4 flex-shrink-0" />
      ) : (
        <AlertCircle className="mt-0.5 h-4 w-4 flex-shrink-0" />
      )}
      <span>{retour.message}</span>
    </div>
  );
}

function BlocIdentite({ me, modifiable, onModifie }: { me: MeData; modifiable: boolean; onModifie: () => Promise<void> }) {
  const [prenom, setPrenom] = useState(me.firstName ?? '');
  const [nom, setNom] = useState(me.lastName ?? '');
  const [saving, setSaving] = useState(false);
  const [retour, setRetour] = useState<Retour>(null);
  const modifie = prenom.trim() !== (me.firstName ?? '') || nom.trim() !== (me.lastName ?? '');

  async function enregistrer(e: React.FormEvent) {
    e.preventDefault();
    if (!prenom.trim() || !nom.trim()) {
      setRetour({ kind: 'error', message: 'Le prénom et le nom sont obligatoires.' });
      return;
    }
    setSaving(true);
    setRetour(null);
    try {
      await authService.modifierNom(prenom.trim(), nom.trim());
      setRetour({ kind: 'success', message: 'Votre prénom et votre nom sont enregistrés.' });
      await onModifie();
    } catch (err) {
      setRetour({ kind: 'error', message: messageCompte(err) });
    } finally {
      setSaving(false);
    }
  }

  return (
    <form onSubmit={enregistrer} noValidate className="rounded-2xl border border-border bg-bg-raised p-5">
      <h2 className="mb-4 flex items-center gap-2 text-sm font-semibold uppercase tracking-wide text-fg-subtle">
        Identité
        {modifiable && (
          <InfoBulle
            libelle="Aide : identité"
            texte="Votre prénom et votre nom apparaissent dans l’application et dans les traces de vos actions. La modification prend effet dès l’enregistrement."
          />
        )}
      </h2>
      {modifiable ? (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <TextField label="Prénom" value={prenom} maxLength={100} onChange={(e) => setPrenom(e.target.value)} required />
          <TextField label="Nom" value={nom} maxLength={100} onChange={(e) => setNom(e.target.value)} required />
        </div>
      ) : (
        <dl className="grid grid-cols-2 gap-4 text-sm">
          <div>
            <dt className="text-xs text-fg-muted">Prénom</dt>
            <dd className="mt-1 text-fg">{me.firstName ?? '—'}</dd>
          </div>
          <div>
            <dt className="text-xs text-fg-muted">Nom</dt>
            <dd className="mt-1 text-fg">{me.lastName ?? '—'}</dd>
          </div>
        </dl>
      )}
      <dl className="mt-4 grid grid-cols-2 gap-4 text-sm">
        <div>
          <dt className="text-xs text-fg-muted">Téléphone</dt>
          <dd className="mt-1 text-fg">{me.phone ?? '—'}</dd>
        </div>
        <div>
          <dt className="text-xs text-fg-muted">Rôle</dt>
          <dd className="mt-1 text-fg">{LIBELLES_ROLE[me.role] ?? me.role}</dd>
        </div>
      </dl>
      <Message retour={retour} />
      {modifiable && (
        <div className="mt-4 flex justify-end gap-2">
          <Button
            type="button"
            variant="secondary"
            disabled={!modifie || saving}
            onClick={() => {
              setPrenom(me.firstName ?? '');
              setNom(me.lastName ?? '');
            }}
          >
            Annuler
          </Button>
          <Button type="submit" disabled={!modifie} loading={saving}>
            Enregistrer
          </Button>
        </div>
      )}
    </form>
  );
}

interface BlocChangementProps {
  titre: string;
  icone: ReactNode;
  aide: string;
  valeur: string;
  testId: string;
  attente: DemandeCompte | null;
  destinationLien: string;
  libelleChamp: string;
  placeholder: string;
  motif: 'adresse' | 'identifiant';
  modifiable: boolean;
  envoyer: (valeur: string) => Promise<DemandeCompte>;
  onModifie: () => Promise<void>;
}

function BlocChangement(p: BlocChangementProps) {
  const [saisie, setSaisie] = useState('');
  const [busy, setBusy] = useState(false);
  const [retour, setRetour] = useState<Retour>(null);

  async function demander(e: React.FormEvent) {
    e.preventDefault();
    const err = emailMsg(saisie);
    if (err) {
      setRetour({ kind: 'error', message: err });
      return;
    }
    setBusy(true);
    setRetour(null);
    try {
      await p.envoyer(saisie.trim());
      setSaisie('');
      setRetour({
        kind: 'success',
        message: `Un lien de confirmation a été envoyé ${p.destinationLien}. Ouvrez-le pour terminer le changement.`,
      });
      await p.onModifie();
    } catch (er) {
      setRetour({ kind: 'error', message: messageCompte(er) });
    } finally {
      setBusy(false);
    }
  }

  async function annuler() {
    setBusy(true);
    setRetour(null);
    try {
      await authService.annulerDemandeCompte(p.motif);
      setRetour({ kind: 'success', message: 'La demande est annulée : le lien envoyé ne fonctionne plus.' });
      await p.onModifie();
    } catch (er) {
      setRetour({ kind: 'error', message: messageCompte(er) });
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="rounded-2xl border border-border bg-bg-raised p-5">
      <h2 className="mb-2 flex items-center gap-2 text-sm font-semibold uppercase tracking-wide text-fg-subtle">
        {p.icone} {p.titre}
        <InfoBulle libelle={`Aide : ${p.titre.toLowerCase()}`} texte={p.aide} />
      </h2>
      <p className="text-xs text-fg-muted">Valeur en vigueur</p>
      <div className="mt-1 flex items-center gap-2 rounded-lg border border-border-hi bg-bg-overlay px-3 py-2 font-mono text-sm text-fg">
        <span data-testid={p.testId}>{p.valeur}</span>
      </div>

      {p.attente && (
        <div
          data-testid={`attente-${p.motif}`}
          className="mt-3 rounded-lg border border-amber-300 bg-amber-50 px-3 py-2 text-sm text-amber-900"
        >
          <p className="flex items-center gap-2 font-medium">
            <Clock className="h-4 w-4" /> En attente de confirmation : {p.attente.nouvelleValeur}
          </p>
          <p className="mt-1 text-xs">
            Le lien a été envoyé {p.destinationLien} ; il est valable jusqu’au {dateHeure(p.attente.expireLe)}.
            Tant qu’il n’est pas ouvert, la valeur en vigueur reste active. Une nouvelle demande remplace
            celle-ci.
          </p>
          {p.modifiable && (
            <Button type="button" size="sm" variant="secondary" className="mt-2" onClick={annuler} loading={busy}>
              Annuler la demande
            </Button>
          )}
        </div>
      )}

      {p.modifiable && (
        <form onSubmit={demander} noValidate className="mt-4">
          <TextField
            label={p.libelleChamp}
            type="email"
            value={saisie}
            maxLength={150}
            placeholder={p.placeholder}
            onChange={(e) => setSaisie(e.target.value)}
            hint={`Un lien de confirmation sera envoyé ${p.destinationLien}.`}
          />
          <div className="mt-3 flex justify-end">
            <Button type="submit" disabled={!saisie.trim()} loading={busy}>
              Recevoir le lien de confirmation
            </Button>
          </div>
        </form>
      )}
      <Message retour={retour} />
    </section>
  );
}

function BlocMotDePasse() {
  const [actuel, setActuel] = useState('');
  const [nouveau, setNouveau] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [busy, setBusy] = useState(false);
  const [retour, setRetour] = useState<Retour>(null);

  async function changer(e: React.FormEvent) {
    e.preventDefault();
    if (!actuel || !nouveau) {
      setRetour({ kind: 'error', message: 'Saisissez votre mot de passe actuel et le nouveau.' });
      return;
    }
    if (nouveau !== confirmation) {
      setRetour({ kind: 'error', message: 'La confirmation ne correspond pas au nouveau mot de passe.' });
      return;
    }
    setBusy(true);
    setRetour(null);
    try {
      const tokens = await authService.changePassword(actuel, nouveau, confirmation);
      tokenStorage.setTokens(tokens);
      setActuel('');
      setNouveau('');
      setConfirmation('');
      setRetour({
        kind: 'success',
        message: 'Votre mot de passe est modifié. Un avis a été envoyé à votre adresse électronique.',
      });
    } catch (er) {
      const statut = axios.isAxiosError(er) ? er.response?.status : undefined;
      setRetour({
        kind: 'error',
        message: statut === 401 ? 'Le mot de passe actuel est incorrect.' : messageCompte(er),
      });
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={changer} noValidate className="rounded-2xl border border-border bg-bg-raised p-5">
      <h2 className="mb-2 flex items-center gap-2 text-sm font-semibold uppercase tracking-wide text-fg-subtle">
        <KeyRound className="h-3.5 w-3.5" /> Mot de passe
        <InfoBulle
          libelle="Aide : mot de passe"
          texte="Le mot de passe actuel est exigé. Le nouveau compte au moins 12 caractères, avec une majuscule, une minuscule, un chiffre et un caractère spécial. Un avis de sécurité est envoyé à votre adresse électronique."
        />
      </h2>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <TextField
          label="Mot de passe actuel"
          type="password"
          autoComplete="current-password"
          value={actuel}
          onChange={(e) => setActuel(e.target.value)}
        />
        <TextField
          label="Nouveau mot de passe"
          type="password"
          autoComplete="new-password"
          value={nouveau}
          onChange={(e) => setNouveau(e.target.value)}
        />
        <TextField
          label="Confirmation"
          type="password"
          autoComplete="new-password"
          value={confirmation}
          onChange={(e) => setConfirmation(e.target.value)}
        />
      </div>
      <Message retour={retour} />
      <div className="mt-4 flex justify-end">
        <Button type="submit" loading={busy} disabled={!actuel || !nouveau || !confirmation}>
          Changer le mot de passe
        </Button>
      </div>
    </form>
  );
}
