import { useState } from 'react';
import { AlertTriangle, CheckCircle, Mail, UserPlus, X } from 'lucide-react';
import { Button } from '../../components/ui/Button';
import { TextField } from '../../components/ui/TextField';
import { PhoneNumberInput, isValidPhone } from '../../components/ui/PhoneNumberInput';
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
    const fnErr = requiredMsg(firstName, 'Le prenom est requis.');
    if (fnErr) errors.firstName = fnErr;
    const lnErr = requiredMsg(lastName, 'Le nom est requis.');
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
      setError('Numero de telephone invalide pour le pays selectionne.');
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

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="w-full max-w-md rounded-2xl bg-bg-raised shadow-2xl">
        <div className="flex items-center justify-between border-b border-border px-5 py-3">
          <h3 className="flex items-center gap-2 text-base font-semibold text-fg">
            <UserPlus className="h-4 w-4 text-accent" />
            Inviter un membre
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
          {result ? (
            <div className="space-y-4">
              <div className="flex items-start gap-3 rounded-lg border border-emerald-200 bg-emerald-50 p-3">
                <CheckCircle className="mt-0.5 h-5 w-5 flex-shrink-0 text-success" />
                <div className="text-sm text-emerald-800">
                  <p className="font-semibold">{result.message}</p>
                  <p className="mt-1 text-xs">
                    Le nouveau membre apparait dans l'equipe avec le statut <strong>En attente</strong>.
                    Il passera <strong>Actif</strong> automatiquement apres sa 1ere connexion reussie.
                  </p>
                </div>
              </div>

              {/* BUG 7 (2026-06-08) — identifiant @jurika.ma genere : a transmettre au membre. */}
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

              {!result.emailDelivered && result.tempPassword && (
                <div className="flex items-start gap-3 rounded-lg border border-amber-300 bg-warning/10 p-3">
                  <AlertTriangle className="mt-0.5 h-5 w-5 flex-shrink-0 text-warning" />
                  <div className="text-xs text-amber-900">
                    <p className="font-semibold">Email non delivre — communiquez le MDP temporaire</p>
                    <p className="mt-1">
                      Transmettez ces identifiants au membre par un canal sur (en personne, telephone).
                      Il devra changer le MDP au 1er login.
                    </p>
                    <div className="mt-2 rounded bg-bg-raised px-2 py-1 font-mono text-sm text-fg">
                      {result.tempPassword}
                    </div>
                  </div>
                </div>
              )}

              <div className="flex gap-2 pt-2">
                <Button
                  variant="secondary"
                  onClick={() => {
                    reset();
                  }}
                  className="flex-1"
                >
                  Inviter un autre
                </Button>
                <Button
                  onClick={() => {
                    onClose();
                    reset();
                  }}
                  className="flex-1"
                >
                  Terminer
                </Button>
              </div>
            </div>
          ) : (
            <form onSubmit={handleSubmit} className="space-y-4" noValidate>
              <p className="text-xs text-fg-muted">
                Cree un compte interne (EMPLOYE ou SUPERVISEUR) attache a votre workspace.
                Un email avec MDP temporaire + lien de connexion est envoye automatiquement.
              </p>

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
                label="Email professionnel"
                name="email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="prenom.nom@cabinet.ma"
                required
              />
              <PhoneNumberInput
                label="Telephone (optionnel)"
                name="phone"
                value={phone}
                onChange={setPhone}
                defaultCountry="MA"
              />
              <div>
                <label className="mb-1 block text-xs font-medium text-fg-subtle">
                  Role
                </label>
                <div className="grid grid-cols-2 gap-2">
                  {(['EMPLOYE', 'SUPERVISEUR'] as const).map((r) => {
                    const disabled = r === 'SUPERVISEUR' && supervisorTaken;
                    return (
                      <button
                        key={r}
                        type="button"
                        onClick={() => !disabled && setRole(r)}
                        disabled={disabled}
                        title={
                          disabled
                            ? 'Un SUPERVISEUR existe deja dans ce cabinet (1 max).'
                            : undefined
                        }
                        className={`rounded-lg border px-3 py-2 text-sm font-medium transition ${
                          disabled
                            ? 'cursor-not-allowed border-border bg-bg-overlay text-fg-subtle opacity-50'
                            : role === r
                              ? 'border-accent bg-accent/10 text-accent'
                              : 'border-border bg-bg-raised text-fg-subtle hover:bg-bg-overlay'
                        }`}
                      >
                        {r === 'EMPLOYE' ? 'Employe' : 'Superviseur'}
                      </button>
                    );
                  })}
                </div>
                <p className="mt-1 text-[11px] text-fg-muted">
                  {supervisorTaken
                    ? 'Le siege SUPERVISEUR est deja occupe — desactivez d\'abord le titulaire pour en designer un autre.'
                    : 'Seul un EMPLOYE compte dans le quota du plan. Un seul SUPERVISEUR par cabinet.'}
                </p>
              </div>

              {error && (
                <div className="rounded-lg border border-danger/40 bg-danger/10 px-3 py-2 text-sm text-danger">
                  {error}
                </div>
              )}

              <div className="flex items-center gap-2 rounded-lg bg-accent/10 p-3 text-xs text-accent">
                <Mail className="h-4 w-4 flex-shrink-0" />
                <span>
                  Le membre recevra : code workspace + email + MDP temporaire + lien direct
                  de connexion. Il devra changer son MDP et choisir une 2FA au 1er login.
                </span>
              </div>

              <div className="flex justify-end gap-2 pt-2">
                <Button type="button" variant="secondary" onClick={() => { onClose(); reset(); }}>
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
