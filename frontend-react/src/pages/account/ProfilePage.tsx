import { useEffect, useState } from 'react';
import { AlertCircle, CheckCircle, Loader2, Lock, Mail, User } from 'lucide-react';
import { Button } from '../../components/ui/Button';
import { TextField } from '../../components/ui/TextField';
import { authService } from '../../services/auth.service';
import { extractError } from '../../lib/api';
import { emailMsg } from '../../lib/formValidation';

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

/**
 * BUG 7 (chore 2026-06-08) — Page Profil utilisateur courant.
 *  - loginEmail (identifiant @jurika.ma) affiche en lecture seule avec cadenas.
 *  - contactEmail (email perso pour notifications) editable via PATCH
 *    /auth/me/contact-email.
 *  - firstName/lastName/phone/role/status read-only V1 (l'edition completera
 *    dans une iteration ulterieure).
 */
export function ProfilePage() {
  const [me, setMe] = useState<MeData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [draftContact, setDraftContact] = useState('');
  const [saving, setSaving] = useState(false);
  const [feedback, setFeedback] = useState<{ kind: 'success' | 'error'; message: string } | null>(
    null,
  );

  async function load() {
    setError(null);
    try {
      const data = await authService.getMe();
      setMe(data);
      setDraftContact(data.contactEmail || '');
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, []);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!me) return;
    // Validation JS AVANT l'appel API (form noValidate : pas de bulle native).
    const emailErr = emailMsg(draftContact);
    if (emailErr) {
      setFeedback({ kind: 'error', message: emailErr });
      document.querySelector<HTMLElement>('[data-testid="profile-contact-email"]')?.focus();
      return;
    }
    setSaving(true);
    setFeedback(null);
    try {
      const r = await authService.updateContactEmail(draftContact.trim());
      if (!r.changed) {
        setFeedback({ kind: 'success', message: r.message });
      } else {
        setFeedback({ kind: 'success', message: r.message });
        // Refresh /me pour stocker le nouvel etat
        setMe({ ...me, contactEmail: r.contactEmail });
      }
    } catch (err) {
      setFeedback({ kind: 'error', message: extractError(err).message });
    } finally {
      setSaving(false);
    }
  }

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
        {error ?? 'Erreur de chargement du profil.'}
      </div>
    );
  }

  const dirty = draftContact.trim().toLowerCase() !== (me.contactEmail || '').toLowerCase();

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div>
        <h1 className="flex items-center gap-2 font-heading text-2xl font-semibold text-fg">
          <User className="h-6 w-6 text-accent" /> Profil
        </h1>
        <p className="mt-1 text-sm text-fg-muted">
          Gerez votre identifiant de connexion et votre email de notifications.
        </p>
      </div>

      {/* Identite + role */}
      <div className="rounded-2xl border border-border bg-bg-raised p-5">
        <h2 className="mb-4 text-sm font-semibold uppercase tracking-wide text-fg-subtle">
          Identite
        </h2>
        <dl className="grid grid-cols-2 gap-4 text-sm">
          <div>
            <dt className="text-xs text-fg-muted">Prenom</dt>
            <dd className="mt-1 text-fg">{me.firstName ?? '—'}</dd>
          </div>
          <div>
            <dt className="text-xs text-fg-muted">Nom</dt>
            <dd className="mt-1 text-fg">{me.lastName ?? '—'}</dd>
          </div>
          <div>
            <dt className="text-xs text-fg-muted">Telephone</dt>
            <dd className="mt-1 text-fg">{me.phone ?? '—'}</dd>
          </div>
          <div>
            <dt className="text-xs text-fg-muted">Role</dt>
            <dd className="mt-1 text-fg">{me.role}</dd>
          </div>
        </dl>
      </div>

      {/* Identifiant de connexion (lecture seule) */}
      <div className="rounded-2xl border border-border bg-bg-raised p-5">
        <h2 className="mb-2 flex items-center gap-2 text-sm font-semibold uppercase tracking-wide text-fg-subtle">
          <Lock className="h-3.5 w-3.5" /> Identifiant de connexion
        </h2>
        <p className="mb-3 text-xs text-fg-muted">
          Cet identifiant est immuable cote utilisateur. Il sert uniquement a vous
          authentifier. Si vous devez le modifier, contactez le support.
        </p>
        <div className="flex items-center gap-2 rounded-lg border border-border-hi bg-bg-overlay px-3 py-2 font-mono text-sm text-fg">
          <Mail className="h-4 w-4 text-accent" />
          <span data-testid="profile-login-email">{me.loginEmail}</span>
        </div>
      </div>

      {/* Contact email (editable) */}
      <form
        onSubmit={handleSubmit}
        className="rounded-2xl border border-border bg-bg-raised p-5"
        noValidate
      >
        <h2 className="mb-2 flex items-center gap-2 text-sm font-semibold uppercase tracking-wide text-fg-subtle">
          <Mail className="h-3.5 w-3.5" /> Email de notifications
        </h2>
        <p className="mb-3 text-xs text-fg-muted">
          Adresse a laquelle vous recevez les emails JURIKA (welcome, changement
          de mot de passe, alertes 2FA, etc.). Vous pouvez la modifier sans
          impact sur votre identifiant de connexion.
        </p>
        <TextField
          label=""
          type="email"
          value={draftContact}
          onChange={(e) => setDraftContact(e.target.value)}
          placeholder="vous@example.ma"
          data-testid="profile-contact-email"
          required
        />

        {feedback && (
          <div
            className={`mt-3 flex items-start gap-2 rounded-lg border px-3 py-2 text-sm ${
              feedback.kind === 'success'
                ? 'border-emerald-200 bg-emerald-50 text-emerald-800'
                : 'border-danger/40 bg-danger/10 text-danger'
            }`}
          >
            {feedback.kind === 'success' ? (
              <CheckCircle className="mt-0.5 h-4 w-4 flex-shrink-0" />
            ) : (
              <AlertCircle className="mt-0.5 h-4 w-4 flex-shrink-0" />
            )}
            <span>{feedback.message}</span>
          </div>
        )}

        <div className="mt-4 flex justify-end gap-2">
          <Button
            type="button"
            variant="secondary"
            onClick={() => setDraftContact(me.contactEmail || '')}
            disabled={!dirty || saving}
          >
            Annuler
          </Button>
          <Button type="submit" disabled={!dirty} loading={saving}>
            Enregistrer
          </Button>
        </div>
      </form>
    </div>
  );
}
