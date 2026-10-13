import { useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { Shield, KeyRound, Smartphone, RefreshCw } from 'lucide-react';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Drawer } from '../../components/ui/Drawer';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { RecoveryCodesDisplay } from '../../components/auth/RecoveryCodesDisplay';
import { useCurrentUser } from '../../store/authStore';
import { authService } from '../../services/auth.service';
import { extractError } from '../../lib/api';
import { InfoBulle, TexteAide } from '../../components/ui/Aide';

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
  const location = useLocation();
  // Changement VOLONTAIRE du mot de passe : bloc « Mot de passe » de « Mon compte »
  // (et non la page de changement OBLIGATOIRE /account/change-password, reservee au
  // premier mot de passe et aux mots de passe temporaires ; dette L5).
  const cheminMonCompte = location.pathname.startsWith('/settings') ? '/settings/profile' : '/profile';
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
      setError((e as Error).message || 'Le changement de méthode n’a pas pu démarrer. Réessayez.');
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
      setError(extractError(e).message || 'Échec de la génération des codes.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="mx-auto max-w-2xl space-y-4 py-6">
      <header className="flex items-center gap-3">
        <Shield className="h-6 w-6 text-accent" />
        <h1 className="text-xl font-semibold text-fg">Sécurité du compte</h1>
      </header>

      <TexteAide cle="securite-compte" titre="Protéger votre compte">
        <p>
          La double authentification confirme chaque connexion par un code à 6 chiffres. Vous pouvez
          changer de méthode (votre méthode actuelle vous sera demandée) et générer de nouveaux codes
          de récupération : gardez-les en lieu sûr, ils servent si vous perdez votre téléphone. Pour
          changer votre mot de passe, passez par « Mon compte ».
        </p>
      </TexteAide>

      <Card className="p-4">
        <div className="flex items-start justify-between gap-3">
          <div>
            <h2 className="flex items-center gap-1 text-sm font-semibold text-fg">
              Double authentification
              <InfoBulle
                libelle="Aide : codes de récupération"
                texte="Chaque code de récupération ne sert qu’une fois et remplace le code à 6 chiffres si vous n’avez plus accès à votre méthode. En générer de nouveaux rend les anciens inutilisables."
              />
            </h2>
            <p className="mt-1 text-xs text-fg-subtle">
              {user?.role === 'CLIENT'
                ? 'La double authentification est facultative pour les comptes clients.'
                : 'La double authentification est obligatoire pour accéder à la plateforme.'}
            </p>
          </div>
        </div>

        <div className="mt-4 flex flex-wrap items-center gap-2">
          <Button size="sm" variant="secondary" onClick={startChange2fa} loading={busy}>
            <Smartphone className="mr-1 h-4 w-4" />
            Changer de méthode
          </Button>
          <Button size="sm" variant="ghost" onClick={() => setConfirmRegen(true)} loading={busy}>
            <RefreshCw className="mr-1 h-4 w-4" />
            Générer de nouveaux codes de récupération
          </Button>
        </div>

        {error && (
          <p role="alert" className="mt-3 rounded-lg border border-danger/40 bg-danger/10 px-3 py-2 text-xs text-danger">
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
          Changez votre mot de passe à tout moment, en particulier si vous pensez qu’il a pu être
          découvert. Le changement se fait dans « Mon compte » : votre mot de passe actuel vous sera
          demandé.
        </p>
        <Button
          size="sm"
          variant="secondary"
          className="mt-3"
          onClick={() => navigate(cheminMonCompte)}
        >
          Changer mon mot de passe
        </Button>
      </Card>

      <p className="text-center text-[11px] text-fg-subtle">
        Tout changement de méthode de double authentification demande une confirmation avec la méthode actuelle.
      </p>

      <ConfirmDialog
        open={confirmRegen}
        onOpenChange={setConfirmRegen}
        title="Générer de nouveaux codes de récupération"
        description="Générer 10 nouveaux codes de récupération ? Les anciens deviendront aussitôt inutilisables."
        confirmLabel="Générer"
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
        title="Nouveaux codes de récupération"
        subtitle="Vos anciens codes ne sont plus valables : conservez ceux-ci dès maintenant en lieu sûr."
        width="md"
      >
        {regenCodes && (
          <RecoveryCodesDisplay
            codes={regenCodes}
            context="REGENERATION"
            continueLabel="Je les ai conservés, fermer"
            onContinue={() => setRegenCodes(null)}
          />
        )}
      </Drawer>
    </div>
  );
}
