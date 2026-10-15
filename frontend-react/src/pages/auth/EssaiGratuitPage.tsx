import { useEffect, useState, type FormEvent } from 'react';
import { PageAccueil, TitreAccueil } from '../../components/auth/PageAccueil';
import { Button } from '../../components/ui/Button';
import { Case } from '../../components/ui/Choix';
import { Lien } from '../../components/ui/Lien';
import { SqueletteTexte, SqueletteZone } from '../../components/ui/LoadingSkeleton';
import { Message } from '../../components/ui/Message';
import { TextField } from '../../components/ui/TextField';
import { PhoneNumberInput } from '../../components/ui/PhoneNumberInput';
import { TexteAide } from '../../components/ui/Aide';
import { VerificationEssai } from '../../components/essai/VerificationEssai';
import { extractError } from '../../lib/api';
import {
  essaiService,
  type ConditionsEssai,
  type DemandeEssaiCreee,
  type EtatDemandeEssai,
} from '../../services/essai.service';

interface Saisie {
  nom: string;
  prenom: string;
  organisation: string;
  telephone: string;
  email: string;
}

const VIDE: Saisie = { nom: '', prenom: '', organisation: '', telephone: '', email: '' };

/**
 * Lot L7 (E4) : inscription a l'essai gratuit de 7 jours (CDC 4.2), distincte de
 * l'inscription payante. RG-ESS-01 : cinq informations seulement, aucun ICE ;
 * RG-CTR-10 : conditions d'utilisation de l'essai lues et acceptees (case non
 * pre-cochee) ; RG-ESS-02 : verification du telephone (SMS) et de l'adresse (lien).
 */
export function EssaiGratuitPage() {
  const [saisie, setSaisie] = useState<Saisie>(VIDE);
  const [conditions, setConditions] = useState<ConditionsEssai | null>(null);
  const [erreurConditions, setErreurConditions] = useState<string | null>(null);
  const [accepte, setAccepte] = useState(false);
  const [erreurs, setErreurs] = useState<Partial<Record<keyof Saisie | 'conditions', string>>>({});
  const [erreurServeur, setErreurServeur] = useState<string | null>(null);
  const [envoi, setEnvoi] = useState(false);
  const [demande, setDemande] = useState<DemandeEssaiCreee | null>(null);
  const [etat, setEtat] = useState<EtatDemandeEssai | null>(null);

  useEffect(() => {
    essaiService.conditions()
      .then(setConditions)
      .catch((e) => setErreurConditions(extractError(e).message));
  }, []);

  function champ<K extends keyof Saisie>(cle: K, valeur: string) {
    setSaisie((s) => ({ ...s, [cle]: valeur }));
    setErreurs((e) => ({ ...e, [cle]: undefined }));
  }

  function valider(): boolean {
    const e: Partial<Record<keyof Saisie | 'conditions', string>> = {};
    if (!saisie.nom.trim()) e.nom = 'Indiquez votre nom.';
    if (!saisie.prenom.trim()) e.prenom = 'Indiquez votre prénom.';
    if (!saisie.organisation.trim()) e.organisation = 'Indiquez le nom de votre cabinet.';
    if (!/^\+[1-9]\d{6,14}$/.test(saisie.telephone)) e.telephone = 'Indiquez un numéro de téléphone valide.';
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(saisie.email.trim())) e.email = 'Indiquez une adresse électronique valide.';
    if (!accepte) e.conditions = 'Cochez la case pour accepter les conditions d’utilisation de l’essai.';
    setErreurs(e);
    return Object.keys(e).length === 0;
  }

  async function soumettre(ev: FormEvent) {
    ev.preventDefault();
    setErreurServeur(null);
    if (!conditions || !valider()) return;
    setEnvoi(true);
    try {
      const d = await essaiService.demander({
        ...saisie,
        email: saisie.email.trim(),
        conditionsAcceptees: accepte,
        conditionsVersion: conditions.version,
      });
      setDemande(d);
      setEtat({
        demandeId: d.demandeId, emailVerifie: false, telephoneVerifie: false,
        essaiCree: false, workspaceCode: null, loginEmail: null, identifiantsEnvoyes: false,
      });
    } catch (e) {
      setErreurServeur(extractError(e).message);
    } finally {
      setEnvoi(false);
    }
  }

  return (
    <PageAccueil
      titre="Essai gratuit"
      enTete={
        <>
          Déjà un compte ? <Lien to="/login">Se connecter</Lien>
        </>
      }
    >
      <TitreAccueil
        titre="Essai gratuit de 7 jours"
        centre={false}
        libelleInfoBulle="Aide sur l’essai gratuit"
        infoBulle="L’essai sert à découvrir JURIKA, sans moyen de paiement. Vous recevez un code par SMS et un lien par courriel : l’essai s’ouvre quand les deux sont confirmés. Vos identifiants arrivent ensuite par courriel."
      >
        Un essai par cabinet. Si vous souhaitez souscrire directement un forfait,{' '}
        <Lien to="/signup">inscrivez votre cabinet</Lien>.
      </TitreAccueil>

      {demande && etat ? (
        <section aria-label="Vérifications">
          <VerificationEssai
            etat={etat}
            onEtat={setEtat}
            telephoneMasque={demande.telephoneMasque}
            email={demande.email}
          />
          {(!demande.smsEnvoye || !demande.emailEnvoye) && !etat.essaiCree && (
            <Message ton="erreur" className="mt-4">
              {!demande.smsEnvoye && 'Le SMS n’a pas pu être envoyé : demandez un nouveau code. '}
              {!demande.emailEnvoye && 'Le courriel de confirmation n’a pas pu être envoyé : contactez JURIKA.'}
            </Message>
          )}
        </section>
      ) : (
        <form onSubmit={soumettre} noValidate className="space-y-4">
          <TexteAide cle="essai.inscription" titre="Ce qu’il faut faire">
            Renseignez ces cinq informations, lisez les conditions de l’essai et acceptez-les. Ensuite, saisissez le
            code reçu par SMS et cliquez sur le lien reçu par courriel. Les adresses jetables ne sont pas acceptées ;
            une adresse ou un numéro qui a déjà servi à un essai ou à un compte est refusé.
          </TexteAide>
          <div className="grid gap-4 sm:grid-cols-2">
            <TextField label="Prénom" autoComplete="given-name" value={saisie.prenom}
                       onChange={(e) => champ('prenom', e.target.value)} error={erreurs.prenom} required />
            <TextField label="Nom" autoComplete="family-name" value={saisie.nom}
                       onChange={(e) => champ('nom', e.target.value)} error={erreurs.nom} required />
          </div>
          <TextField label="Organisation (nom du cabinet)" autoComplete="organization" value={saisie.organisation}
                     onChange={(e) => champ('organisation', e.target.value)} error={erreurs.organisation} required />
          <PhoneNumberInput label="Téléphone" value={saisie.telephone}
                            onChange={(v) => champ('telephone', v)} error={erreurs.telephone}
                            defaultCountry="MA" required hint="Un code de vérification vous sera envoyé par SMS."
                            data-testid="essai-telephone" />
          <TextField label="Adresse électronique" type="email" autoComplete="email" value={saisie.email}
                     onChange={(e) => champ('email', e.target.value)} error={erreurs.email}
                     hint="Un lien de confirmation vous sera envoyé à cette adresse." required />

          <fieldset className="space-y-2">
            <legend className="mb-1 text-sm font-medium text-fg">Conditions d’utilisation de l’essai</legend>
            {erreurConditions && (
              <Message ton="erreur">
                Les conditions de l’essai n’ont pas pu être chargées : {erreurConditions} L’inscription est
                impossible tant qu’elles ne sont pas affichées.
              </Message>
            )}
            {!conditions && !erreurConditions && (
              <SqueletteZone libelle="Chargement des conditions de l’essai">
                <div className="h-48 rounded-lg border border-border bg-bg p-3">
                  <SqueletteTexte lignes={6} />
                </div>
              </SqueletteZone>
            )}
            {conditions && (
              <>
                {/* Texte long : defilement interne de l'encadre seulement (la page defile normalement). */}
                <div className="max-h-48 overflow-y-auto whitespace-pre-line rounded-lg border border-border bg-bg p-3 text-xs leading-relaxed text-fg-muted focus-visible:outline-2 focus-visible:outline-focus"
                     tabIndex={0} aria-label="Texte des conditions d’utilisation de l’essai">
                  {conditions.texte}
                </div>
                <p className="text-xs text-fg-subtle">Version {conditions.version}</p>
              </>
            )}
            <Case
              label="J’ai lu et j’accepte les conditions d’utilisation de l’essai gratuit JURIKA."
              checked={accepte}
              disabled={!conditions}
              aria-invalid={erreurs.conditions ? true : undefined}
              onChange={(e) => { setAccepte(e.target.checked); setErreurs((x) => ({ ...x, conditions: undefined })); }}
            />
            {erreurs.conditions && <p className="text-xs font-medium text-danger" role="alert">{erreurs.conditions}</p>}
          </fieldset>

          {erreurServeur && <Message ton="erreur">{erreurServeur}</Message>}
          <Button type="submit" size="lg" loading={envoi} disabled={!conditions} fullWidth>
            Recevoir mes codes de vérification
          </Button>
        </form>
      )}
    </PageAccueil>
  );
}
