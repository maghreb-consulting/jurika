import { useState } from 'react';
import { UserPlus } from 'lucide-react';
import { Button } from '../../components/ui/Button';
import { GroupeRadio } from '../../components/ui/Choix';
import { Message } from '../../components/ui/Message';
import { Modal } from '../../components/ui/Modal';
import { TextField } from '../../components/ui/TextField';
import { PhoneNumberInput } from '../../components/ui/PhoneNumberInput';
import { isValidPhone } from '../../components/ui/phoneNumber';
import { authService } from '../../services/auth.service';
import { extractError } from '../../lib/api';
import { emailMsg, requiredMsg, focusFirstError, hasErrors, type FieldErrors } from '../../lib/formValidation';

interface Props {
  open: boolean;
  onClose: () => void;
  /** Notifie le parent qu'une invitation a abouti pour rafraichir la liste. */
  onInvited: () => void;
  /**
   * Session 5 (2026-06-08) — un cabinet ne peut compter qu'UN seul
   * SUPERVISEUR a la fois. Quand le siege est deja pris (PENDING ou
   * ACTIVE), on desactive le bouton "Superviseur" et on force EMPLOYE.
   */
  supervisorTaken?: boolean;
}

/**
 * BUG 6 (2026-06-07) — Drawer "Inviter un employe" reutilisable depuis la page
 * Equipe. Calque sur {@link InviteClientDrawer} pour l'UX (succes -> badge vert
 * + reset). L'employe cree est PENDING jusqu'a son 1er login reussi : il recoit
 * un email avec MDP temporaire + lien direct vers /login.
 */
export function InviteEmployeeDrawer({ open, onClose, onInvited, supervisorTaken = false }: Props) {
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [role, setRole] = useState<'EMPLOYE' | 'SUPERVISEUR'>('EMPLOYE');
  // Si le siege SUPERVISEUR est deja pris, on force EMPLOYE silencieusement.
  if (supervisorTaken && role === 'SUPERVISEUR') {
    setRole('EMPLOYE');
  }
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<{
    emailDelivered: boolean;
    tempPassword: string | null;
    /** BUG 7 (2026-06-08) — identifiant @jurika.ma genere par le backend. */
    loginEmail?: string;
    contactEmail?: string;
    message: string;
  } | null>(null);

  if (!open) return null;

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    // Validation JS AVANT l'appel API (form noValidate : pas de bulle native).
    const errors: FieldErrors = {};
    const fnErr = requiredMsg(firstName, 'Le prénom est obligatoire.');
    if (fnErr) errors.firstName = fnErr;
    const lnErr = requiredMsg(lastName, 'Le nom est obligatoire.');
    if (lnErr) errors.lastName = lnErr;
    const emErr = emailMsg(email);
    if (emErr) errors.email = emErr;
    if (hasErrors(errors)) {
      setError(errors.firstName ?? errors.lastName ?? errors.email ?? null);
      focusFirstError(errors, ['firstName', 'lastName', 'email']);
      return;
    }
    // Telephone optionnel : valide seulement s'il est renseigne (E.164).
    if (phone.trim() && !isValidPhone(phone.trim())) {
      setError('Numéro de téléphone invalide pour le pays sélectionné.');
      document.querySelector<HTMLElement>('[name="phone"]')?.focus();
      return;
    }
    setLoading(true);
    try {
      const r = await authService.inviteEmployee({
        email: email.trim(),
        firstName: firstName.trim(),
        lastName: lastName.trim(),
        phone: phone.trim() || undefined,
        role,
      });
      setResult({
        emailDelivered: r.emailDelivered,
        tempPassword: r.tempPassword,
        loginEmail: r.loginEmail,
        contactEmail: r.contactEmail,
        message: r.message,
      });
      onInvited();
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setLoading(false);
    }
  }

  function reset() {
    setFirstName('');
    setLastName('');
    setEmail('');
    setPhone('');
    setRole('EMPLOYE');
    setError(null);
    setResult(null);
  }

  function fermer() {
    onClose();
    reset();
  }

  return (
    <Modal
      open={open}
      onOpenChange={(o) => {
        if (!o) fermer();
      }}
      title={
        <span className="flex items-center gap-2">
          <UserPlus className="h-4 w-4 text-fg-subtle" aria-hidden="true" />
          Inviter un membre
        </span>
      }
      size="md"
      closeOnBackdrop={false}
    >
      {result ? (
        <div className="space-y-4">
          <Message ton="succes" titre={result.message}>
            Le nouveau membre apparaît dans l’équipe avec le statut <strong>En attente</strong>. Il passera{' '}
            <strong>Actif</strong> automatiquement après sa première connexion réussie.
          </Message>

          {/* BUG 7 (2026-06-08) — identifiant @jurika.ma genere : a transmettre au membre. */}
          {result.loginEmail && (
            <div className="rounded-lg border border-border bg-bg-overlay p-3">
              <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Identifiant de connexion attribué</p>
              <code className="mt-1 block break-all rounded-md bg-bg-raised px-2 py-1 font-mono text-sm text-fg ring-1 ring-border">
                {result.loginEmail}
              </code>
              {result.contactEmail && result.contactEmail !== result.loginEmail && (
                <p className="mt-2 text-xs text-fg-muted">
                  Notifications envoyées à <span className="font-mono">{result.contactEmail}</span>.
                </p>
              )}
            </div>
          )}

          {!result.emailDelivered && result.tempPassword && (
            <Message ton="attention" titre="Courriel non remis : communiquez le mot de passe temporaire">
              Transmettez ces identifiants au membre par un moyen sûr (en personne, par téléphone). Il devra
              changer ce mot de passe à sa première connexion.
              <span className="mt-2 block rounded-md bg-bg-raised px-2 py-1 font-mono text-sm text-fg ring-1 ring-border">
                {result.tempPassword}
              </span>
            </Message>
          )}

          <div className="flex flex-wrap justify-end gap-2 pt-2">
            <Button type="button" variant="secondary" onClick={() => reset()}>
              Inviter un autre
            </Button>
            <Button type="button" onClick={fermer}>
              Terminer
            </Button>
          </div>
        </div>
      ) : (
        <form onSubmit={handleSubmit} className="space-y-4" noValidate>
          <p className="text-sm text-fg-muted">
            Crée un compte interne (employé ou superviseur) rattaché à votre cabinet. Un courriel contenant un mot
            de passe temporaire et le lien de connexion lui est envoyé automatiquement.
          </p>

          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <TextField label="Prénom" name="firstName" value={firstName} onChange={(e) => setFirstName(e.target.value)} required />
            <TextField label="Nom" name="lastName" value={lastName} onChange={(e) => setLastName(e.target.value)} required />
          </div>
          <TextField
            label="Adresse électronique professionnelle"
            name="email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="prenom.nom@cabinet.ma"
            required
          />
          <PhoneNumberInput label="Téléphone (facultatif)" name="phone" value={phone} onChange={setPhone} defaultCountry="MA" />

          <GroupeRadio
            legende="Rôle"
            name="role-invitation"
            horizontal
            value={role}
            onChange={(v) => setRole(v as 'EMPLOYE' | 'SUPERVISEUR')}
            options={[
              { value: 'EMPLOYE', label: 'Employé' },
              {
                value: 'SUPERVISEUR',
                label: 'Superviseur',
                disabled: supervisorTaken,
                description: supervisorTaken ? 'Ce cabinet a déjà un superviseur (un seul par cabinet).' : undefined,
              },
            ]}
          />
          <p className="-mt-2 text-xs text-fg-muted">
            {supervisorTaken
              ? 'Le poste de superviseur est déjà occupé : désactivez d’abord son titulaire pour en désigner un autre.'
              : 'Seuls les employés comptent dans le quota du forfait. Un seul superviseur par cabinet.'}
          </p>

          {error && <Message ton="erreur">{error}</Message>}

          <Message ton="info">
            Le membre recevra le code du cabinet, son identifiant, un mot de passe temporaire et le lien de
            connexion. À sa première connexion, il changera son mot de passe et choisira sa méthode de double
            authentification.
          </Message>

          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="secondary" onClick={fermer}>
              Annuler
            </Button>
            <Button type="submit" loading={loading} icon={<UserPlus />}>
              Inviter
            </Button>
          </div>
        </form>
      )}
    </Modal>
  );
}
