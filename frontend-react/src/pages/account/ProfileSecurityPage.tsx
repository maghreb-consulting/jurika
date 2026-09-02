import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Shield, KeyRound, Smartphone, RefreshCw } from 'lucide-react';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Drawer } from '../../components/ui/Drawer';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { RecoveryCodesDisplay } from '../../components/auth/RecoveryCodesDisplay';
import { useCurrentUser } from '../../store/authStore';
import { authService } from '../../services/auth.service';
import { extractError } from '../../lib/api';

/**
 * RG-AU38 : ecran "Profile &gt; Securite" pour changer la methode 2FA
 * apres le 1er login. Le user doit valider avec sa methode actuelle puis re-passer
 * par le flow de choix (RG-AU32).
 *
 * Sprint 3 / TASK 3 — la regeneration des codes de recuperation affiche le
 * composant ONE-SHOT {@link RecoveryCodesDisplay} dans un Drawer (RG-AU33).
 */
export function ProfileSecurityPage() {
  const user = useCurrentUser();
  const navigate = useNavigate();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [regenCodes, setRegenCodes] = useState<string[] | null>(null);
  // Confirmation de régénération des codes (remplace window.confirm).
  const [confirmRegen, setConfirmRegen] = useState(false);

  async function startChange2fa() {
    setBusy(true);
    setError(null);
    try {
      navigate('/account/2fa-choose?reset=1');
    } catch (e) {
      setError((e as Error).message || 'Erreur');
    } finally {
      setBusy(false);
    }
  }

  async function regenerateRecoveryCodes() {
    setConfirmRegen(false);
    setBusy(true);
    setError(null);
    try {
      const { codes } = await authService.generateRecoveryCodes();
      setRegenCodes(codes);
    } catch (e) {
      setError(extractError(e).message || 'Echec generation codes');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="mx-auto max-w-2xl space-y-4 py-6">
      <header className="flex items-center gap-3">
        <Shield className="h-6 w-6 text-accent" />
        <h1 className="text-xl font-semibold text-fg">Profile &gt; Securite</h1>
      </header>

      <Card className="p-4">
        <div className="flex items-start justify-between gap-3">
          <div>
            <h2 className="text-sm font-semibold text-fg">Methode 2FA</h2>
            <p className="mt-1 text-xs text-fg-subtle">
              {user?.role === 'CLIENT'
                ? 'Le 2FA est optionnel pour les comptes Client (RG-CA05).'
                : 'Une methode 2FA active est requise pour acceder a la plateforme.'}
            </p>
          </div>
          <span className="rounded-full bg-emerald-50 px-2 py-0.5 text-[11px] font-semibold text-emerald-700">
            Active
          </span>
        </div>

        <div className="mt-4 flex flex-wrap items-center gap-2">
          <Button size="sm" variant="secondary" onClick={startChange2fa} loading={busy}>
            <Smartphone className="mr-1 h-4 w-4" />
            Changer ma methode 2FA
          </Button>
          <Button size="sm" variant="ghost" onClick={() => setConfirmRegen(true)} loading={busy}>
            <RefreshCw className="mr-1 h-4 w-4" />
            Regenerer codes de recuperation
          </Button>
        </div>

        {error && (
          <p className="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-700">
            {error}
          </p>
        )}
      </Card>

      <Card className="p-4">
        <div className="flex items-center gap-2">
          <KeyRound className="h-4 w-4 text-fg-subtle" />
          <h2 className="text-sm font-semibold text-fg">Mot de passe</h2>
        </div>
        <p className="mt-1 text-xs text-fg-subtle">
          Changez votre mot de passe si vous suspectez une compromission.
        </p>
        <Button
          size="sm"
          variant="secondary"
          className="mt-3"
          onClick={() => navigate('/account/change-password?reset=1')}
        >
          Changer mon mot de passe
        </Button>
      </Card>

      <p className="text-center text-[11px] text-fg-subtle">
        RG-AU38 — Toute modification 2FA necessite une confirmation avec la methode actuelle.
      </p>

      <ConfirmDialog
        open={confirmRegen}
        onOpenChange={setConfirmRegen}
        title="Regenerer les codes de recuperation"
        description="Generer 10 nouveaux codes de recuperation ? Les anciens seront immediatement invalides."
        confirmLabel="Regenerer"
        variant="danger"
        loading={busy}
        onConfirm={regenerateRecoveryCodes}
      />

      <Drawer
        open={regenCodes !== null}
        onClose={() => {
          /* fermeture seulement via "J'ai sauvegarde + Continuer" pour eviter
             les fermetures accidentelles. On laisse onClose vide. */
        }}
        title="Nouveaux codes de recuperation"
        subtitle="Vos anciens codes sont desormais invalides — sauvegardez ceux-ci immediatement."
        width="md"
      >
        {regenCodes && (
          <RecoveryCodesDisplay
            codes={regenCodes}
            context="REGENERATION"
            continueLabel="J'ai sauvegarde, fermer"
            onContinue={() => setRegenCodes(null)}
          />
        )}
      </Drawer>
    </div>
  );
}
