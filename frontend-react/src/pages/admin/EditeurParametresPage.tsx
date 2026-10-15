import { useCallback, useEffect, useState } from 'react';
import { Save } from 'lucide-react';
import { Page } from '../../components/layout/Page';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { TextField } from '../../components/ui/TextField';
import { Message } from '../../components/ui/Message';
import { EmptyState } from '../../components/ui/EmptyState';
import { Tableau, type ColonneTableau } from '../../components/ui/Tableau';
import { CardSkeleton, SqueletteZone } from '../../components/ui/LoadingSkeleton';
import { TexteAide } from '../../components/ui/Aide';
import { useToast } from '../../components/ui/useToast';
import { contratService } from '../../services/contrat.service';
import { extractError } from '../../lib/api';
import type { EditeurParametres, EditeurTrace, UsageEditeur } from '../../types/contrat';

/** Mentions de l'editeur, dans l'ordre d'affichage (cles de l'API billing). */
const MENTIONS: { champ: string; libelle: string; aide?: string }[] = [
  { champ: 'raisonSociale', libelle: 'Raison sociale', aide: 'Par exemple « Maghreb Consulting » : imprimée en tête des parties du contrat.' },
  { champ: 'formeJuridique', libelle: 'Forme juridique', aide: 'SARL, SA… : imprimée « [raison sociale], [forme] au capital de… ».' },
  { champ: 'capitalSocial', libelle: 'Capital social (en dirhams)', aide: 'Montant seul : le contrat ajoute « dirhams ».' },
  { champ: 'siegeSocial', libelle: 'Adresse du siège', aide: 'Sans la ville : le contrat et les factures impriment « [adresse], [ville du siège] ».' },
  { champ: 'ville', libelle: 'Ville du siège', aide: 'Imprimée après l’adresse du siège ; la ville du registre du commerce se saisit à part.' },
  { champ: 'rcNumero', libelle: 'Numéro du registre du commerce' },
  { champ: 'rcTribunal', libelle: 'Ville du registre du commerce', aide: 'Imprimée « immatriculée au registre du commerce de [ville] ».' },
  { champ: 'ice', libelle: 'ICE', aide: '15 chiffres.' },
  { champ: 'identifiantFiscal', libelle: 'Identifiant fiscal (IF)' },
  { champ: 'taxeProfessionnelle', libelle: 'Taxe professionnelle (TP)' },
  { champ: 'representantNom', libelle: 'Nom du représentant légal', aide: 'Avec la civilité, par exemple « M. Prénom Nom ».' },
  { champ: 'representantQualite', libelle: 'Qualité du représentant légal', aide: 'Par exemple « gérant » : imprimée « en qualité de [qualité] ».' },
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
      toast.enregistre('Paramètres de l’éditeur enregistrés');
    } catch (e) {
      setErreur(extractError(e).message);
    } finally {
      setEnvoi(false);
    }
  }

  return (
    <Page
      titre="Paramètres de l’éditeur"
      description="Identité de Maghreb Consulting et taux de TVA, imprimés sur les factures et dans le contrat d’abonnement."
      largeur="etroite"
      aide={
        <TexteAide cle="admin-editeur-parametres" titre="Saisir l’identité de l’éditeur">
          <p>
            Saisissez une fois l’identité de Maghreb Consulting, telle qu’elle figure au registre du commerce. Elle est
            imprimée sur chaque facture et dans le contrat d’abonnement (pièce A, conditions particulières). Une mention
            laissée vide n’est jamais inventée : les documents l’impriment « [non renseigné : …] » ; complétez donc
            toutes les mentions du contrat avant d’activer un abonnement. Chaque modification est tracée (auteur, date,
            ancienne et nouvelle valeur) ; elle vaut pour les documents produits ensuite : les contrats déjà acceptés
            restent tels qu’ils ont été acceptés (texte et empreinte figés).
          </p>
        </TexteAide>
      }
    >
      <div className="flex flex-col gap-4">
        {erreur && <Message ton="erreur">{erreur}</Message>}

        {chargement ? (
          <SqueletteZone libelle="Chargement des paramètres…" className="space-y-4">
            <CardSkeleton className="h-20" />
            <CardSkeleton className="h-96" />
          </SqueletteZone>
        ) : params ? (
          <>
            {params.manquants.length > 0 ? (
              <div role="note" aria-label="Mentions manquantes">
                <Message
                  ton="attention"
                  titre={`${params.manquants.length} mention${params.manquants.length > 1 ? 's' : ''} manquante${params.manquants.length > 1 ? 's' : ''} :`}
                >
                  <ul className="ml-5 mt-1 list-disc">
                    {params.manquants.map((m) => (
                      <li key={m.champ}>
                        {m.libelle} — utilisée sur : {m.usages.map((u) => LIBELLE_USAGE[u]).join(' et ')}
                      </li>
                    ))}
                  </ul>
                </Message>
              </div>
            ) : (
              <Message ton="succes">Toutes les mentions de l’éditeur sont renseignées.</Message>
            )}

            <Card title="Identité de l’éditeur">
              <form
                className="flex flex-col gap-4"
                onSubmit={(e) => {
                  e.preventDefault();
                  void enregistrer();
                }}
              >
                <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                  {MENTIONS.map((m) => (
                    <TextField
                      key={m.champ}
                      id={`editeur-${m.champ}`}
                      label={m.libelle}
                      hint={m.aide}
                      value={valeurs[m.champ] ?? ''}
                      onChange={(e) => setValeurs((v) => ({ ...v, [m.champ]: e.target.value }))}
                    />
                  ))}
                  <TextField
                    id="editeur-tva"
                    label="Taux de TVA (%)"
                    hint="Appliqué au calcul de chaque facture (montants HT, TVA et TTC). Entre 0 et 30, deux décimales au plus."
                    value={tva}
                    onChange={(e) => setTva(e.target.value)}
                    inputMode="decimal"
                  />
                </div>
                <div>
                  <Button type="submit" loading={envoi} disabled={envoi} icon={<Save />}>
                    Enregistrer
                  </Button>
                </div>
              </form>
            </Card>

            <section aria-labelledby="titre-historique-editeur">
              <h2 id="titre-historique-editeur" className="mb-3 text-lg font-semibold text-heading">
                Historique des modifications
              </h2>
              <Tableau
                legende="Historique des modifications des paramètres de l’éditeur"
                colonnes={COLONNES_TRACES}
                lignes={traces}
                cleLigne={(t) => `${t.modifieLe}-${t.champ}-${traces.indexOf(t)}`}
                enTeteCollant={false}
                vide={<EmptyState compact title="Aucune modification enregistrée." description="Chaque enregistrement des paramètres ajoute ici une ligne par mention modifiée." />}
              />
            </section>
          </>
        ) : null}
      </div>
    </Page>
  );
}

const COLONNES_TRACES: ColonneTableau<EditeurTrace>[] = [
  { cle: 'date', titre: 'Date', rendu: (t) => <span className="whitespace-nowrap tabular-nums">{new Date(t.modifieLe).toLocaleString('fr-FR')}</span> },
  { cle: 'mention', titre: 'Mention', rendu: (t) => t.libelle },
  { cle: 'ancienne', titre: 'Ancienne valeur', rendu: (t) => t.ancienneValeur ?? '--' },
  { cle: 'nouvelle', titre: 'Nouvelle valeur', rendu: (t) => t.nouvelleValeur ?? '--' },
];
