import { useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { Shield, KeyRound, Smartphone, RefreshCw } from 'lucide-react';
import { Card } from '../../components/ui/Card';
import { Message } from '../../components/ui/Message';
import { EcranOuOnglet } from '../settings/EcranOuOnglet';
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
    <EcranOuOnglet
      titre="Sécurité du compte"
      description="Double authentification, codes de récupération et mot de passe."
      largeur="etroite"
      aide={
        <TexteAide cle="securite-compte" titre="Protéger votre compte">
          <p>
            La double authentification confirme chaque connexion par un code à 6 chiffres. Vous pouvez
            changer de méthode (votre méthode actuelle vous sera demandée) et générer de nouveaux codes
            de récupération : gardez-les en lieu sûr, ils servent si vous perdez votre téléphone. Pour
            changer votre mot de passe, passez par « Mon compte ».
          </p>
        </TexteAide>
      }
    >
      <div className="space-y-4">
        <Card
          title={
            <span className="flex items-center gap-2">
              <Shield className="h-4 w-4 text-fg-subtle" aria-hidden="true" />
              Double authentification
              <InfoBulle
                libelle="Aide : codes de récupération"
                texte="Chaque code de récupération ne sert qu’une fois et remplace le code à 6 chiffres si vous n’avez plus accès à votre méthode. En générer de nouveaux rend les anciens inutilisables."
              />
            </span>
          }
          description={
            user?.role === 'CLIENT'
              ? 'La double authentification est facultative pour les comptes clients.'
              : 'La double authentification est obligatoire pour accéder à la plateforme.'
          }
        >
          <div className="flex flex-wrap items-center gap-2">
            <Button size="sm" variant="secondary" onClick={startChange2fa} loading={busy} icon={<Smartphone />}>
              Changer de méthode
            </Button>
            <Button size="sm" variant="secondary" onClick={() => setConfirmRegen(true)} loading={busy} icon={<RefreshCw />}>
              Générer de nouveaux codes de récupération
            </Button>
          </div>
          <p className="mt-3 text-xs text-fg-muted">
            Tout changement de méthode de double authentification demande une confirmation avec la méthode actuelle.
          </p>

          {error && (
            <Message ton="erreur" className="mt-3">
              {error}
            </Message>
          )}
        </Card>

        <Card
          title={
            <span className="flex items-center gap-2">
              <KeyRound className="h-4 w-4 text-fg-subtle" aria-hidden="true" />
              Mot de passe
            </span>
          }
          description="Changez votre mot de passe à tout moment, en particulier si vous pensez qu’il a pu être découvert. Le changement se fait dans « Mon compte » : votre mot de passe actuel vous sera demandé."
        >
          <Button size="sm" variant="secondary" onClick={() => navigate(cheminMonCompte)}>
            Changer mon mot de passe
          </Button>
        </Card>
      </div>

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
    </EcranOuOnglet>
  );
}
