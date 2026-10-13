import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { CheckCircle2, RefreshCw, ShieldAlert } from 'lucide-react';
import { BrandLogo } from '../../components/ui/BrandLogo';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { TexteAide } from '../../components/ui/Aide';
import { authService } from '../../services/auth.service';
import { messageCompte } from '../account/messagesCompte';

type Etat = 'confirmation' | 'confirme' | 'erreur';

/**
 * Lot L5 (E10), RG-CPT-02 : page publique ouverte par le lien recu par courriel
 * apres une demande de changement d'adresse electronique ou d'identifiant de
 * connexion. Le lien ne sert qu'une fois : l'appel n'est fait qu'une fois par
 * affichage (garde contre le double effet du mode strict de React).
 */
export function ConfirmerChangementComptePage() {
  const [params] = useSearchParams();
  const token = params.get('token');
  const [etat, setEtat] = useState<Etat>(token ? 'confirmation' : 'erreur');
  const [motif, setMotif] = useState<'adresse' | 'identifiant' | null>(null);
  const [valeur, setValeur] = useState('');
  const [erreur, setErreur] = useState<string | null>(
    token ? null : 'Ce lien est incomplet : ouvrez le lien reçu par courriel tel quel.',
  );
  const appele = useRef(false);

  useEffect(() => {
    if (!token || appele.current) return;
    appele.current = true;
    authService
      .confirmerChangementCompte(token)
      .then((r) => {
        setMotif(r.motif);
        setValeur(r.nouvelleValeur);
        setEtat('confirme');
      })
      .catch((err) => {
        setErreur(messageCompte(err));
        setEtat('erreur');
      });
  }, [token]);

  return (
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-bg via-bg-raised to-accent/10 px-4">
      <div className="w-full max-w-md">
        <Link to="/" className="mb-8 flex items-center justify-center" aria-label="JURIKA, retour à l’accueil">
          <BrandLogo variant="full" size={48} />
        </Link>

        <Card className="p-8">
          {etat === 'confirmation' && (
            <div className="space-y-4 text-center">
              <RefreshCw className="mx-auto h-10 w-10 animate-spin text-accent" />
              <h1 className="font-heading text-xl font-semibold text-fg">Confirmation en cours…</h1>
            </div>
          )}

          {etat === 'confirme' && (
            <div className="space-y-5 text-center">
              <CheckCircle2 className="mx-auto h-12 w-12 text-success" />
              <h1 className="font-heading text-xl font-semibold text-fg">
                {motif === 'identifiant' ? 'Identifiant de connexion modifié' : 'Adresse électronique modifiée'}
              </h1>
              <p className="text-sm text-fg-muted">
                {motif === 'identifiant' ? (
                  <>
                    Votre identifiant de connexion est désormais <strong>{valeur}</strong>. Utilisez-le,
                    avec le code de votre cabinet, à votre prochaine connexion.
                  </>
                ) : (
                  <>
                    Votre adresse électronique est désormais <strong>{valeur}</strong>. Les courriels de
                    JURIKA vous y seront envoyés.
                  </>
                )}
              </p>
              <Link to="/login">
                <Button className="w-full">Aller à la connexion</Button>
              </Link>
            </div>
          )}

          {etat === 'erreur' && (
            <div className="space-y-5 text-center">
              <ShieldAlert className="mx-auto h-12 w-12 text-danger" />
              <h1 className="font-heading text-xl font-semibold text-fg">Confirmation impossible</h1>
              <p className="text-sm text-fg-muted" role="alert">
                {erreur}
              </p>
              <TexteAide cle="confirmer-changement-compte" titre="Et maintenant ?">
                Aucune modification n’a été faite : vos valeurs actuelles restent actives. Connectez-vous,
                puis refaites la demande depuis l’écran « Mon compte » (menu de votre compte, en haut à droite) ; seul le
                dernier lien reçu est valable.
              </TexteAide>
              <Link to="/login">
                <Button variant="secondary" className="w-full">
                  Aller à la connexion
                </Button>
              </Link>
            </div>
          )}
        </Card>
      </div>
    </div>
  );
}
