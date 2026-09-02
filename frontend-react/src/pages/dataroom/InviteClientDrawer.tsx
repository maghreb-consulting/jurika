import { useState } from 'react';
import { CheckCircle, Copy, Mail, UserPlus, X } from 'lucide-react';
import { Button } from '../../components/ui/Button';
import { TextField } from '../../components/ui/TextField';
import { PhoneNumberInput, isValidPhone } from '../../components/ui/PhoneNumberInput';
import { authService } from '../../services/auth.service';
import { extractError } from '../../lib/api';
import { firstError, requiredMsg, emailMsg } from '../../lib/formValidation';

interface Props {
  open: boolean;
  dossierId: string;
  raisonSociale: string;
  onClose: () => void;
}

export function InviteClientDrawer({ open, dossierId, raisonSociale, onClose }: Props) {
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
    const firstNameErr = requiredMsg(firstName, 'Le prenom est requis.');
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
      setError('Numero de telephone invalide pour le pays selectionne.');
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
    const creds = `Workspace : JUR-DEMO1\nEmail : ${email}\nMot de passe temporaire : ${result.temporaryPassword}`;
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

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="w-full max-w-md rounded-2xl bg-bg-raised shadow-2xl">
        <div className="flex items-center justify-between border-b border-border px-5 py-3">
          <h3 className="flex items-center gap-2 text-base font-semibold text-fg">
            <UserPlus className="h-4 w-4 text-accent" />
            Inviter le client
          </h3>
          <button
            type="button"
            onClick={() => {
              onClose();
              reset();
            }}
            className="rounded-lg p-1 text-fg-subtle hover:bg-bg-overlay"
            aria-label="Fermer"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        <div className="p-5">
          <p className="mb-4 text-xs text-fg-muted">
            Cree un compte CLIENT pour <strong>{raisonSociale}</strong>. Un email
            avec les identifiants temporaires sera envoye automatiquement.
          </p>

          {result ? (
            <div className="space-y-4">
              <div className="flex items-start gap-3 rounded-lg border border-emerald-200 bg-emerald-50 p-3">
                <CheckCircle className="mt-0.5 h-5 w-5 flex-shrink-0 text-success" />
                <div className="text-sm text-emerald-800">
                  <p className="font-semibold">{result.message}</p>
                  <p className="mt-1 text-xs">
                    Le client se connectera via <code>/login</code> avec son email et le mot
                    de passe temporaire. Il devra le changer au premier login.
                  </p>
                </div>
              </div>

              {/* BUG 7 (2026-06-08) — identifiant @jurika.ma genere */}
              {result.loginEmail && (
                <div className="rounded-lg border border-accent/40 bg-accent/10 p-3">
                  <p className="text-[11px] font-bold uppercase tracking-wide text-accent">
                    Identifiant de connexion genere
                  </p>
                  <code className="mt-1 block break-all rounded bg-bg-raised px-2 py-1 font-mono text-sm text-fg">
                    {result.loginEmail}
                  </code>
                  {result.contactEmail && result.contactEmail !== result.loginEmail && (
                    <p className="mt-2 text-[11px] text-fg-muted">
                      Notifications envoyees a <span className="font-mono">{result.contactEmail}</span>.
                    </p>
                  )}
                </div>
              )}

              {result.temporaryPassword && (
                <div className="rounded-lg border border-amber-200 bg-warning/10 p-3">
                  <p className="mb-2 text-xs font-bold text-amber-900">
                    Mot de passe temporaire (a noter ou copier)
                  </p>
                  <div className="flex items-center justify-between gap-2">
                    <code className="break-all rounded bg-bg-raised px-2 py-1 font-mono text-sm">
                      {result.temporaryPassword}
                    </code>
                    <Button size="sm" variant="secondary" onClick={copyCreds}>
                      <Copy className="mr-1 h-3.5 w-3.5" /> Copier
                    </Button>
                  </div>
                </div>
              )}

              <Button
                onClick={() => {
                  onClose();
                  reset();
                }}
                className="w-full"
              >
                Terminer
              </Button>
            </div>
          ) : (
            <form onSubmit={handleSubmit} noValidate className="space-y-4">
              <div className="grid grid-cols-2 gap-3">
                <TextField
                  label="Prenom"
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
                label="Email"
                name="email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="client@exemple.ma"
                required
              />
              <PhoneNumberInput
                label="Telephone (optionnel)"
                name="phone"
                value={phone}
                onChange={setPhone}
                defaultCountry="MA"
              />

              {error && (
                <div className="rounded-lg border border-danger/40 bg-danger/10 px-3 py-2 text-sm text-danger">
                  {error}
                </div>
              )}

              <div className="flex items-center gap-2 rounded-lg bg-accent/10 p-3 text-xs text-accent">
                <Mail className="h-4 w-4 flex-shrink-0" />
                <span>
                  Le client recevra par email : code workspace + email + mot de passe temporaire +
                  lien direct de connexion.
                </span>
              </div>

              <div className="flex justify-end gap-2 pt-2">
                <Button type="button" variant="secondary" onClick={() => onClose()}>
                  Annuler
                </Button>
                <Button type="submit" loading={loading}>
                  <UserPlus className="mr-1 h-3.5 w-3.5" />
                  Inviter
                </Button>
              </div>
            </form>
          )}
        </div>
      </div>
    </div>
  );
}
