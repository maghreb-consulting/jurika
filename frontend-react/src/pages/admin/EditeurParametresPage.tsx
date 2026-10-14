import { useCallback, useEffect, useState } from 'react';
import { AlertCircle, Building, Loader2, Save } from 'lucide-react';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { InfoBulle, TexteAide } from '../../components/ui/Aide';
import { useToast } from '../../components/ui/useToast';
import { contratService } from '../../services/contrat.service';
import { extractError } from '../../lib/api';
import type { EditeurParametres, EditeurTrace, UsageEditeur } from '../../types/contrat';

/** Mentions de l'editeur, dans l'ordre d'affichage (cles de l'API billing). */
const MENTIONS: { champ: string; libelle: string; aide?: string }[] = [
  { champ: 'raisonSociale', libelle: 'Raison sociale', aide: 'Par exemple « Maghreb Consulting ».' },
  { champ: 'formeJuridique', libelle: 'Forme juridique', aide: 'SARL, SA… : imprimée « Maghreb Consulting, [forme] au capital de… ».' },
  { champ: 'capitalSocial', libelle: 'Capital social (en dirhams)', aide: 'Montant seul : le contrat ajoute « dirhams ».' },
  { champ: 'siegeSocial', libelle: 'Adresse du siège' },
  { champ: 'ville', libelle: 'Ville du siège' },
  { champ: 'rcNumero', libelle: 'Numéro du registre du commerce' },
  { champ: 'rcTribunal', libelle: 'Ville du registre du commerce', aide: 'Imprimée « immatriculée au registre du commerce de [ville] ».' },
  { champ: 'ice', libelle: 'ICE', aide: '15 chiffres.' },
  { champ: 'identifiantFiscal', libelle: 'Identifiant fiscal (IF)' },
  { champ: 'taxeProfessionnelle', libelle: 'Taxe professionnelle (TP)' },
  { champ: 'representantNom', libelle: 'Nom du représentant légal', aide: 'Avec la civilité, par exemple « M. Prénom Nom ».' },
  { champ: 'representantQualite', libelle: 'Qualité du représentant légal', aide: 'Par exemple « gérant ».' },
];

const LIBELLE_USAGE: Record<UsageEditeur, string> = { FACTURES: 'factures', CONTRAT: 'contrat d’abonnement' };

/**
 * Lot L7 (E6) : parametres de l'editeur (identite de Maghreb Consulting, taux de TVA), saisis une
 * fois par le super-admin. Ils alimentent les factures (RG-PAY-03) et le contrat d'abonnement
 * (CDC 4.8). Aucune valeur n'est proposee par defaut ; chaque modification est tracee.
 */
export function EditeurParametresPage() {
  const toast = useToast();
  const [params, setParams] = useState<EditeurParametres | null>(null);
  const [valeurs, setValeurs] = useState<Record<string, string>>({});
  const [tva, setTva] = useState('');
  const [traces, setTraces] = useState<EditeurTrace[]>([]);
  const [chargement, setChargement] = useState(true);
  const [envoi, setEnvoi] = useState(false);
  const [erreur, setErreur] = useState<string | null>(null);

  const appliquer = (p: EditeurParametres) => {
    setParams(p);
    setValeurs(Object.fromEntries(MENTIONS.map((m) => [m.champ, p.valeurs[m.champ] ?? ''])));
    setTva(String(p.tauxTvaPourcent));
  };

  const charger = useCallback(
    () =>
      Promise.all([contratService.editeur(), contratService.historiqueEditeur()])
        .then(([p, h]) => {
          appliquer(p);
          setTraces(h);
          setErreur(null);
        })
        .catch((e: unknown) => setErreur(extractError(e).message))
        .finally(() => setChargement(false)),
    [],
  );

  useEffect(() => {
    void charger();
  }, [charger]);

  async function enregistrer() {
    setEnvoi(true);
    setErreur(null);
    try {
      const corps = Object.fromEntries(MENTIONS.map((m) => [m.champ, valeurs[m.champ]?.trim() ? valeurs[m.champ].trim() : null]));
      const p = await contratService.modifierEditeur(corps, Number(tva.replace(',', '.')));
      appliquer(p);
      setTraces(await contratService.historiqueEditeur());
      toast.success('Paramètres de l’éditeur enregistrés.');
    } catch (e) {
      setErreur(extractError(e).message);
    } finally {
      setEnvoi(false);
    }
  }

  return (
    <div className="mx-auto flex max-w-4xl flex-col gap-4">
      <header className="flex flex-wrap items-center gap-3">
        <Building className="h-6 w-6 text-accent" aria-hidden="true" />
        <div className="flex-1">
          <h1 className="font-heading text-2xl font-semibold text-fg">Paramètres de l’éditeur</h1>
          <p className="text-sm text-fg-subtle">Identité de Maghreb Consulting et taux de TVA.</p>
        </div>
      </header>

      <TexteAide cle="admin-editeur-parametres" titre="Saisir l’identité de l’éditeur">
        <p>
          Saisissez une fois l’identité de Maghreb Consulting, telle qu’elle figure au registre du commerce. Elle est
          imprimée sur chaque facture et dans le contrat d’abonnement (pièce A, conditions particulières). Une mention
          laissée vide n’est jamais inventée : les documents la signalent « non renseignée ». Chaque modification est
          tracée (auteur, date, ancienne et nouvelle valeur) ; elle vaut pour les documents produits ensuite, les
          contrats déjà acceptés restent tels qu’ils ont été acceptés.
        </p>
      </TexteAide>

      {erreur && (
        <p role="alert" className="flex items-start gap-2 rounded-lg bg-danger/10 px-3 py-2 text-sm text-danger">
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
          <span>{erreur}</span>
        </p>
      )}

      {chargement ? (
        <p className="flex items-center gap-2 text-sm text-fg-subtle" role="status">
          <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> Chargement…
        </p>
      ) : params ? (
        <>
          {params.manquants.length > 0 ? (
            <Card className="p-4 text-sm" role="note" aria-label="Mentions manquantes">
              <p className="font-medium text-fg">
                {params.manquants.length} mention{params.manquants.length > 1 ? 's' : ''} manquante
                {params.manquants.length > 1 ? 's' : ''} :
              </p>
              <ul className="ml-5 mt-1 list-disc text-fg-muted">
                {params.manquants.map((m) => (
                  <li key={m.champ}>
                    {m.libelle} — utilisée sur : {m.usages.map((u) => LIBELLE_USAGE[u]).join(' et ')}
                  </li>
                ))}
              </ul>
            </Card>
          ) : (
            <Card className="p-4 text-sm text-fg-muted">Toutes les mentions de l’éditeur sont renseignées.</Card>
          )}

          <Card className="flex flex-col gap-3 p-4">
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              {MENTIONS.map((m) => (
                <div key={m.champ} className="flex flex-col gap-1 text-sm">
                  <span>
                    <label htmlFor={`editeur-${m.champ}`}>{m.libelle}</label>{' '}
                    {m.aide && <InfoBulle libelle={`Aide : ${m.libelle}`} texte={m.aide} />}
                  </span>
                  <input
                    id={`editeur-${m.champ}`}
                    value={valeurs[m.champ] ?? ''}
                    onChange={(e) => setValeurs((v) => ({ ...v, [m.champ]: e.target.value }))}
                    className="rounded-md border border-border bg-bg px-2 py-1.5"
                  />
                </div>
              ))}
              <div className="flex flex-col gap-1 text-sm">
                <span>
                  <label htmlFor="editeur-tva">Taux de TVA (%)</label>{' '}
                  <InfoBulle libelle="Aide : taux de TVA" texte="Appliqué au calcul de chaque facture (montants HT, TVA et TTC). Entre 0 et 30, deux décimales au plus." />
                </span>
                <input
                  id="editeur-tva"
                  value={tva}
                  onChange={(e) => setTva(e.target.value)}
                  inputMode="decimal"
                  className="rounded-md border border-border bg-bg px-2 py-1.5"
                />
              </div>
            </div>
            <div>
              <Button onClick={enregistrer} loading={envoi} disabled={envoi}>
                <Save className="mr-1 h-4 w-4" aria-hidden="true" /> Enregistrer
              </Button>
            </div>
          </Card>

          <Card className="flex flex-col gap-2 p-4">
            <h2 className="text-lg font-semibold text-fg">Historique des modifications</h2>
            {traces.length === 0 ? (
              <p className="text-sm text-fg-subtle">Aucune modification enregistrée.</p>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="text-left text-fg-subtle">
                      <th scope="col" className="py-1 pr-3">Date</th>
                      <th scope="col" className="py-1 pr-3">Mention</th>
                      <th scope="col" className="py-1 pr-3">Ancienne valeur</th>
                      <th scope="col" className="py-1">Nouvelle valeur</th>
                    </tr>
                  </thead>
                  <tbody>
                    {traces.map((t, i) => (
                      <tr key={`${t.modifieLe}-${t.champ}-${i}`} className="border-t border-border">
                        <td className="py-1 pr-3">{new Date(t.modifieLe).toLocaleString('fr-FR')}</td>
                        <td className="py-1 pr-3">{t.libelle}</td>
                        <td className="py-1 pr-3">{t.ancienneValeur ?? '—'}</td>
                        <td className="py-1">{t.nouvelleValeur ?? '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </Card>
        </>
      ) : null}
    </div>
  );
}
