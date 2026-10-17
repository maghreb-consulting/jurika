import { useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { PageAccueil, TitreAccueil } from '../../components/auth/PageAccueil';
import { Lien } from '../../components/ui/Lien';
import { Skeleton, SqueletteZone } from '../../components/ui/LoadingSkeleton';
import { Message } from '../../components/ui/Message';
import { VerificationEssai } from '../../components/essai/VerificationEssai';
import { extractError } from '../../lib/api';
import { essaiService, type EtatDemandeEssai } from '../../services/essai.service';

/**
 * Lot L7 (E4), RG-ESS-02 : confirmation de l'adresse de l'inscription a l'essai
 * (lien recu par courriel). Si le telephone n'est pas encore verifie, le code SMS
 * se saisit ici ; l'essai s'ouvre quand les deux sont confirmes.
 */
export function EssaiConfirmerPage() {
  const [params] = useSearchParams();
  const jeton = params.get('jeton');
  const [etat, setEtat] = useState<EtatDemandeEssai | null>(null);
  const [erreur, setErreur] = useState<string | null>(jeton ? null : 'Lien de confirmation incomplet : ouvrez le lien reçu par courriel.');
  const appele = useRef(false);

  useEffect(() => {
    if (!jeton || appele.current) return;
    appele.current = true;
    essaiService.confirmerAdresse(jeton)
      .then(setEtat)
      .catch((e) => setErreur(extractError(e).message));
  }, [jeton]);

  return (
    <PageAccueil titre="Confirmation de votre adresse" enTete={<Lien to="/login">Se connecter</Lien>}>
      <TitreAccueil titre="Confirmation de votre adresse" centre={false} />
      {erreur && (
        <div className="space-y-3">
          <Message ton="erreur">{erreur}</Message>
          <p className="text-sm text-fg-muted">
            Vous pouvez <Lien to="/essai">recommencer l’inscription à l’essai</Lien> ou{' '}
            <Lien to="/login">vous connecter</Lien>.
          </p>
        </div>
      )}
      {!erreur && !etat && (
        <SqueletteZone libelle="Vérification du lien en cours">
          <p className="mb-3 text-sm text-fg-muted">Vérification du lien en cours…</p>
          <div className="space-y-3">
            <Skeleton className="h-24 w-full rounded-lg" />
            <Skeleton className="h-20 w-full rounded-lg" />
          </div>
        </SqueletteZone>
      )}
      {etat && <VerificationEssai etat={etat} onEtat={setEtat} />}
    </PageAccueil>
  );
}
