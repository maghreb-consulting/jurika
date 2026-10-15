import { useState } from 'react';
import { Copy, UserPlus } from 'lucide-react';
import { Button } from '../../components/ui/Button';
import { Modal } from '../../components/ui/Modal';
import { Message } from '../../components/ui/Message';
import { TextField } from '../../components/ui/TextField';
import { PhoneNumberInput } from '../../components/ui/PhoneNumberInput';
import { isValidPhone } from '../../components/ui/phoneNumber';
import { authService } from '../../services/auth.service';
import { extractError } from '../../lib/api';
import { firstError, requiredMsg, emailMsg } from '../../lib/formValidation';
import { useCurrentUser } from '../../store/authStore';

interface Props {
  open: boolean;
  dossierId: string;
  raisonSociale: string;
  onClose: () => void;
}

export function InviteClientDrawer({ open, dossierId, raisonSociale, onClose }: Props) {
  const user = useCurrentUser();
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<{
    userId: string;
    userCreated: boolean;
    temporaryPassword: string | null;
    /** BUG 7 (2026-06-08) — identifiant @jurika.ma genere. */
    loginEmail?: string;
    contactEmail?: string;
    message: string;
  } | null>(null);

  if (!open) return null;

  function focus(name: string) {
    // Focus in-app du champ concerné (a11y), sans bulle native.
    document.querySelector<HTMLElement>(`[name="${name}"]`)?.focus();
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    // Validation JS in-app AVANT l'appel API (aucune bulle native).
    const firstNameErr = requiredMsg(firstName, 'Le prénom est requis.');
    if (firstNameErr) {
      setError(firstNameErr);
      focus('firstName');
      return;
    }
    const lastNameErr = requiredMsg(lastName, 'Le nom est requis.');
    if (lastNameErr) {
      setError(lastNameErr);
      focus('lastName');
      return;
    }
    const emailErr = firstError(() => emailMsg(email));
    if (emailErr) {
      setError(emailErr);
      focus('email');
      return;
    }
    // Telephone optionnel : valide seulement s'il est renseigne (E.164).
    if (phone.trim() && !isValidPhone(phone.trim())) {
      setError('Numéro de téléphone invalide pour le pays sélectionné.');
      focus('phone');
      return;
    }
    setError(null);
    setLoading(true);
    try {
      const r = await authService.inviteClient({
        dossierId,
        email: email.trim(),
        firstName: firstName.trim(),
        lastName: lastName.trim(),
        phone: phone.trim() || undefined,
      });
      setResult(r);
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setLoading(false);
    }
  }

  function copyCreds() {
    if (!result?.temporaryPassword) return;
    // Lot L6 (E9) : le code du cabinet est celui de l'employe connecte (il etait ecrit en dur,
    // JUR-DEMO1, faux pour tout autre cabinet) ; absent, la ligne n'est pas ecrite.
    const lignes = [
      ...(user?.workspaceCode ? [`Code du cabinet : ${user.workspaceCode}`] : []),
      `Identifiant : ${result.loginEmail ?? email}`,
      `Mot de passe temporaire : ${result.temporaryPassword}`,
    ];
    const creds = lignes.join('\n');
    navigator.clipboard.writeText(creds).catch(() => {});
  }

  function reset() {
    setFirstName('');
    setLastName('');
    setEmail('');
    setPhone('');
    setError(null);
    setResult(null);
  }

  function fermer() {
    onClose();
    reset();
  }

  return (
    <Modal
      open
      onOpenChange={(o) => {
        if (!o) fermer();
      }}
      title="Inviter le client"
      description={
        <>
          Créez le compte du client de <strong className="text-fg">{raisonSociale}</strong> : il pourra
          consulter sa Data Room selon les permissions réglées dans « Accès du client ». Un courriel
          avec ses identifiants temporaires lui est envoyé automatiquement.
        </>
      }
      closeOnBackdrop={false}
    >
      {result ? (
        <div className="space-y-4">
          <Message ton="succes" titre={result.message}>
            Le client se connectera depuis la page de connexion avec son identifiant et le mot de
            passe temporaire, qu’il devra changer à sa première connexion.
          </Message>

          {/* BUG 7 (2026-06-08) — identifiant @jurika.ma genere */}
          {result.loginEmail && (
            <div className="rounded-lg border border-border bg-bg-overlay p-3">
              <p className="text-xs font-semibold text-fg-muted">Identifiant de connexion attribué</p>
              <code className="mt-1 block break-all rounded-md bg-bg-raised px-2 py-1 font-mono text-sm text-fg">
                {result.loginEmail}
              </code>
              {result.contactEmail && result.contactEmail !== result.loginEmail && (
                <p className="mt-2 text-xs text-fg-muted">
                  Notifications envoyées à <span className="font-mono">{result.contactEmail}</span>.
                </p>
              )}
            </div>
          )}

          {result.temporaryPassword && (
            <div className="rounded-lg border border-warning/40 bg-warning-soft p-3">
              <p className="mb-2 text-xs font-semibold text-fg">Mot de passe temporaire (à noter ou à copier)</p>
              <div className="flex flex-wrap items-center justify-between gap-2">
                <code className="break-all rounded-md bg-bg-raised px-2 py-1 font-mono text-sm text-fg">
                  {result.temporaryPassword}
                </code>
                <Button type="button" size="sm" variant="secondary" icon={<Copy />} onClick={copyCreds}>
                  Copier
                </Button>
              </div>
            </div>
          )}

          <Button type="button" onClick={fermer} fullWidth>
            Terminer
          </Button>
        </div>
      ) : (
        <form onSubmit={handleSubmit} noValidate className="space-y-4">
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <TextField
              label="Prénom"
              name="firstName"
              value={firstName}
              onChange={(e) => setFirstName(e.target.value)}
              required
            />
            <TextField
              label="Nom"
              name="lastName"
              value={lastName}
              onChange={(e) => setLastName(e.target.value)}
              required
            />
          </div>
          <TextField
            label="Adresse électronique"
            name="email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="client@exemple.ma"
            required
          />
          <PhoneNumberInput
            label="Téléphone (facultatif)"
            name="phone"
            value={phone}
            onChange={setPhone}
            defaultCountry="MA"
          />

          {error && <Message ton="erreur">{error}</Message>}

          <Message ton="info">
            Le client recevra par courriel le code du cabinet, son identifiant, un mot de passe
            temporaire et le lien de connexion.
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
