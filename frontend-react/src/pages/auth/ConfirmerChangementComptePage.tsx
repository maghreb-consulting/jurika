import { useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { CheckCircle2, RefreshCw, ShieldAlert } from 'lucide-react';
import { PageAccueil, TitreAccueil } from '../../components/auth/PageAccueil';
import { TexteAide } from '../../components/ui/Aide';
import { Lien } from '../../components/ui/Lien';
import { SqueletteZone } from '../../components/ui/LoadingSkeleton';
import { Message } from '../../components/ui/Message';
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
    <PageAccueil
      titre="Confirmation du changement"
      largeur="etroite"
      enTete={<Lien to="/login">Se connecter</Lien>}
    >
      {etat === 'confirmation' && (
        <SqueletteZone libelle="Confirmation du changement en cours">
          <TitreAccueil icone={<RefreshCw className="motion-safe:animate-spin" />} titre="Confirmation en cours…" />
        </SqueletteZone>
      )}

      {etat === 'confirme' && (
        <div className="space-y-5">
          <TitreAccueil
            icone={<CheckCircle2 />}
            ton="succes"
            titre={motif === 'identifiant' ? 'Identifiant de connexion modifié' : 'Adresse électronique modifiée'}
          >
            {motif === 'identifiant' ? (
              <>
                Votre identifiant de connexion est désormais <strong className="text-fg">{valeur}</strong>.
                Utilisez-le, avec le code de votre cabinet, à votre prochaine connexion.
              </>
            ) : (
              <>
                Votre adresse électronique est désormais <strong className="text-fg">{valeur}</strong>. Les
                courriels de JURIKA vous y seront envoyés.
              </>
            )}
          </TitreAccueil>
          <Lien to="/login" commeBouton={{ variant: 'primary', size: 'lg' }} className="w-full">
            Aller à la connexion
          </Lien>
        </div>
      )}

      {etat === 'erreur' && (
        <div className="space-y-5">
          <TitreAccueil icone={<ShieldAlert />} ton="erreur" titre="Confirmation impossible" />
          <Message ton="erreur">{erreur}</Message>
          <TexteAide cle="confirmer-changement-compte" titre="Et maintenant ?">
            Aucune modification n’a été faite : vos valeurs actuelles restent actives. Connectez-vous, puis refaites
            la demande depuis l’écran « Mon compte » (menu de votre compte, en haut à droite) ; seul le dernier lien
            reçu est valable.
          </TexteAide>
          <Lien to="/login" commeBouton={{ variant: 'secondary', size: 'lg' }} className="w-full">
            Aller à la connexion
          </Lien>
        </div>
      )}
    </PageAccueil>
  );
}
