import { useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { CheckCircle2, Mail, Smartphone } from 'lucide-react';
import { Button } from '../ui/Button';
import { TextField } from '../ui/TextField';
import { InfoBulle } from '../ui/Aide';
import { extractError } from '../../lib/api';
import { essaiService, type EtatDemandeEssai } from '../../services/essai.service';

interface Props {
  etat: EtatDemandeEssai;
  onEtat: (etat: EtatDemandeEssai) => void;
  telephoneMasque?: string;
  email?: string;
}

/**
 * Lot L7 (E4), RG-ESS-02 : les deux verifications de l'inscription a l'essai
 * (telephone par code SMS, adresse par le lien recu). L'essai s'ouvre des que
 * les deux sont faites, dans n'importe quel ordre.
 */
export function VerificationEssai({ etat, onEtat, telephoneMasque, email }: Props) {
  const [code, setCode] = useState('');
  const [erreur, setErreur] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);
  const [envoi, setEnvoi] = useState(false);

  // L'adresse se confirme dans un autre onglet : l'etat est relu regulierement.
  useEffect(() => {
    if (etat.essaiCree || etat.emailVerifie) return undefined;
    const minuterie = window.setInterval(() => {
      essaiService.etatDemande(etat.demandeId)
        .then((e) => { if (e.emailVerifie !== etat.emailVerifie || e.essaiCree) onEtat(e); })
        .catch(() => { /* nouvel essai au tour suivant */ });
    }, 5000);
    return () => window.clearInterval(minuterie);
  }, [etat, onEtat]);

  async function verifier(e: FormEvent) {
    e.preventDefault();
    setErreur(null);
    setInfo(null);
    if (!/^\d{6}$/.test(code.trim())) {
      setErreur('Saisissez les six chiffres du code reçu par SMS.');
      return;
    }
    setEnvoi(true);
    try {
      onEtat(await essaiService.verifierTelephone(etat.demandeId, code.trim()));
    } catch (err) {
      setErreur(extractError(err).message);
    } finally {
      setEnvoi(false);
    }
  }

  async function renvoyer() {
    setErreur(null);
    setInfo(null);
    try {
      const r = await essaiService.renvoyerCode(etat.demandeId);
      setInfo(r.smsEnvoye
        ? 'Un nouveau code vient de vous être envoyé par SMS.'
        : 'Le SMS n’a pas pu être envoyé. Réessayez dans une minute ou contactez JURIKA.');
    } catch (err) {
      setErreur(extractError(err).message);
    }
  }

  if (etat.essaiCree) {
    return (
      <div className="space-y-4 text-center" data-testid="essai-ouvert">
        <CheckCircle2 className="mx-auto h-10 w-10 text-success" aria-hidden="true" />
        <h2 className="text-xl font-semibold text-fg">Votre essai gratuit est ouvert</h2>
        {etat.workspaceCode ? (
          <p className="text-sm text-fg-muted">
            Code de votre cabinet : <strong className="font-mono text-fg">{etat.workspaceCode}</strong>
            {etat.loginEmail && (
              <> — identifiant de connexion : <strong className="font-mono text-fg">{etat.loginEmail}</strong></>
            )}
          </p>
        ) : null}
        <p className="text-sm text-fg-muted">
          {etat.identifiantsEnvoyes || etat.workspaceCode === null
            ? 'Votre mot de passe provisoire vous a été envoyé par courriel. À la première connexion, vous le remplacerez puis choisirez la double authentification.'
            : 'Le courriel contenant votre mot de passe provisoire n’a pas pu être envoyé : contactez JURIKA en indiquant le code de votre cabinet.'}
        </p>
        <p className="text-sm text-fg-muted">
          L’essai dure 7 jours : 3 dossiers, 1 superviseur et 1 employé, 500 Mo de stockage, 20 questions au chatbot,
          aucun téléchargement.
        </p>
        <Link to="/login" className="inline-block font-medium text-accent hover:underline">
          Se connecter
        </Link>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-start gap-3 rounded-md border border-border p-4">
        <Smartphone className="mt-0.5 h-5 w-5 text-fg-muted" aria-hidden="true" />
        <div className="flex-1">
          <p className="flex items-center gap-1 font-medium text-fg">
            Téléphone {etat.telephoneVerifie ? 'vérifié' : 'à vérifier'}
            <InfoBulle
              libelle="Aide sur le code SMS"
              texte="Un code de six chiffres vous a été envoyé par SMS. Il est valable quelques minutes ; vous pouvez en demander un nouveau au bout d’une minute."
            />
          </p>
          {etat.telephoneVerifie ? (
            <p className="text-sm text-success">Votre numéro est confirmé.</p>
          ) : (
            <form onSubmit={verifier} className="mt-2 space-y-2" noValidate>
              <TextField
                label={telephoneMasque ? `Code reçu par SMS au ${telephoneMasque}` : 'Code reçu par SMS'}
                inputMode="numeric"
                autoComplete="one-time-code"
                maxLength={6}
                value={code}
                onChange={(e) => setCode(e.target.value)}
                error={erreur ?? undefined}
                mono
              />
              {info && <p className="text-sm text-fg-muted" role="status">{info}</p>}
              <div className="flex gap-2">
                <Button type="submit" loading={envoi}>Vérifier le code</Button>
                <Button type="button" variant="ghost" onClick={() => { void renvoyer(); }}>
                  Recevoir un nouveau code
                </Button>
              </div>
            </form>
          )}
        </div>
      </div>

      <div className="flex items-start gap-3 rounded-md border border-border p-4">
        <Mail className="mt-0.5 h-5 w-5 text-fg-muted" aria-hidden="true" />
        <div>
          <p className="font-medium text-fg">Adresse électronique {etat.emailVerifie ? 'confirmée' : 'à confirmer'}</p>
          <p className="text-sm text-fg-muted">
            {etat.emailVerifie
              ? 'Votre adresse est confirmée.'
              : `Ouvrez le courriel envoyé${email ? ` à ${email}` : ''} et cliquez sur « Confirmer mon adresse ». Cette page se met à jour toute seule.`}
          </p>
        </div>
      </div>
      <p className="text-xs text-fg-subtle">
        L’essai s’ouvre dès que le téléphone et l’adresse sont confirmés. La demande expire au bout de 24 heures.
      </p>
    </div>
  );
}
