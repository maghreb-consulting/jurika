import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { BrandLogo } from '../../components/ui/BrandLogo';
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
    <div className="min-h-screen bg-bg">
      <header className="px-6 py-4 sm:px-10">
        <Link to="/" aria-label="JURIKA, retour à l’accueil"><BrandLogo variant="full" size={44} /></Link>
      </header>
      <main className="mx-auto max-w-xl px-4 pb-16">
        <h1 className="mt-4 text-2xl font-semibold text-fg">Confirmation de votre adresse</h1>
        <section className="mt-6 rounded-lg border border-border bg-bg-raised p-6">
          {erreur && (
            <div className="space-y-2" role="alert">
              <p className="text-sm text-danger">{erreur}</p>
              <p className="text-sm text-fg-muted">
                Vous pouvez <Link to="/essai" className="text-accent hover:underline">recommencer l’inscription à l’essai</Link>{' '}
                ou <Link to="/login" className="text-accent hover:underline">vous connecter</Link>.
              </p>
            </div>
          )}
          {!erreur && !etat && <p className="text-sm text-fg-muted">Vérification du lien en cours…</p>}
          {etat && <VerificationEssai etat={etat} onEtat={setEtat} />}
        </section>
      </main>
    </div>
  );
}
