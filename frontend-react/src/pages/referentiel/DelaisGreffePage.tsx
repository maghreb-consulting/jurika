import { useCallback, useEffect, useMemo, useState } from 'react';
import { Search } from 'lucide-react';
import { Page } from '../../components/layout/Page';
import { Button } from '../../components/ui/Button';
import { TextField } from '../../components/ui/TextField';
import { Select } from '../../components/ui/Select';
import { Badge } from '../../components/ui/Badge';
import { Message } from '../../components/ui/Message';
import { EmptyState } from '../../components/ui/EmptyState';
import { Skeleton, SqueletteZone } from '../../components/ui/LoadingSkeleton';
import { InfoBulle, TexteAide } from '../../components/ui/Aide';
import { useEnregistre } from '../../components/misc/useEnregistre';
import { extractError } from '../../lib/api';
import { useCurrentUser } from '../../store/authStore';
import { delaisGreffeService, type NatureActe, type SaisieDelai } from '../../services/delaisGreffe.service';

/**
 * Lot L4d (E7), décision du directeur du 11 octobre 2026 : délais de dépôt au greffe par
 * nature d'acte. Le délai et l'article viennent du corpus du cabinet ; un délai sans article,
 * ou marqué « probable » par la matrice, est « à confirmer ». Le superviseur peut l'ajuster
 * pour le cabinet : l'ajustement s'applique aux démarches de dépôt des tickets.
 *
 * Lot L6b (E9) : composants de la bibliotheque, confirmation " Enregistre ".
 */
function libelleDelai(valeur: number | null, unite: string | null): string {
  if (valeur == null || !unite) return '--';
  return `${valeur} ${unite === 'JOURS' ? 'jours' : 'mois'}`;
}

function libelleMatrice(m: string): string {
  return m.replace(/^MATRICE_/, '').replace(/_/g, ' ').toLowerCase();
}

export function DelaisGreffePage() {
  const user = useCurrentUser();
  const enregistre = useEnregistre();
  const peutAjuster = user?.role === 'SUPERVISEUR';
  const [natures, setNatures] = useState<NatureActe[] | null>(null);
  const [erreur, setErreur] = useState<string | null>(null);
  const [filtre, setFiltre] = useState('');
  const [edition, setEdition] = useState<string | null>(null);
  const [saisie, setSaisie] = useState<{ valeur: string; unite: 'JOURS' | 'MOIS'; article: string; note: string }>(
    { valeur: '', unite: 'JOURS', article: '', note: '' },
  );

  const charger = useCallback(() => {
    delaisGreffeService
      .lister()
      .then(setNatures)
      .catch((e) => setErreur(extractError(e).message));
  }, []);
  useEffect(charger, [charger]);

  const visibles = useMemo(() => {
    const q = filtre.trim().toLowerCase();
    return (natures ?? []).filter((n) => !q
      || `${n.decisionLibelle ?? ''} ${n.decisionCode} ${n.matrice} ${n.article ?? ''}`.toLowerCase().includes(q));
  }, [natures, filtre]);

  const aConfirmer = (natures ?? []).filter((n) => n.statut === 'A_CONFIRMER' && !n.ajustement).length;

  function ouvrir(n: NatureActe) {
    const cle = `${n.matrice}|${n.decisionCode}`;
    setEdition(cle);
    setErreur(null);
    setSaisie({
      valeur: String(n.ajustement?.delaiValeur ?? n.delaiValeur ?? ''),
      unite: n.ajustement?.delaiUnite ?? n.delaiUnite ?? 'JOURS',
      article: n.ajustement?.article ?? n.article ?? '',
      note: n.ajustement?.note ?? '',
    });
  }

  async function enregistrer(n: NatureActe) {
    const valeur = Number.parseInt(saisie.valeur, 10);
    if (!Number.isFinite(valeur) || valeur <= 0) {
      setErreur('Indiquez le délai ajusté : un nombre de jours ou de mois supérieur à zéro.');
      return;
    }
    const corps: SaisieDelai = { delaiValeur: valeur, delaiUnite: saisie.unite,
      article: saisie.article.trim() || undefined, note: saisie.note.trim() || undefined };
    try {
      await delaisGreffeService.ajuster(n.matrice, n.decisionCode, corps);
      setEdition(null);
      enregistre('Délai ajusté pour le cabinet');
      charger();
    } catch (e) {
      setErreur(extractError(e).message);
    }
  }

  async function retirer(n: NatureActe) {
    try {
      await delaisGreffeService.retirer(n.matrice, n.decisionCode);
      enregistre('Délai du corpus rétabli');
      charger();
    } catch (e) {
      setErreur(extractError(e).message);
    }
  }

  return (
    <Page
      titre="Délais de dépôt au greffe"
      description="Le délai de dépôt de chaque nature d’acte et l’article qui le fixe, tels que le corpus du cabinet les cite."
      aide={
        <TexteAide cle="delais-greffe" titre="À quoi sert ce référentiel ?">
          <p>
            Pour chaque nature d’acte, le délai de dépôt au greffe et l’article qui le fixe, tels que le
            corpus du cabinet les cite (code de commerce, loi n° 15-95 ; loi n° 5-96 sur la SARL). Un délai
            sans article, ou que la matrice qualifie de probable, est marqué « à confirmer » : vérifiez-le
            avant de vous y fier.
          </p>
          <p className="mt-1">
            {peutAjuster
              ? 'Vous pouvez ajuster un délai pour le cabinet : il s’applique alors aux démarches de dépôt des tickets, avec la mention « délai ajusté par le cabinet ». Retirer l’ajustement rétablit le délai du corpus.'
              : 'Seul le superviseur peut ajuster un délai pour le cabinet.'}
          </p>
        </TexteAide>
      }
    >
      <div className="space-y-4">
        {erreur && (
          <Message ton="erreur" onFermer={() => setErreur(null)}>
            {erreur}
          </Message>
        )}

        <div className="flex flex-wrap items-end justify-between gap-3">
          <div className="relative w-full md:w-96">
            <Search className="pointer-events-none absolute left-3 top-2.5 z-10 h-4 w-4 text-fg-subtle" aria-hidden="true" />
            <TextField
              type="search"
              value={filtre}
              onChange={(e) => setFiltre(e.target.value)}
              placeholder="Rechercher une nature d’acte, un article…"
              aria-label="Rechercher une nature d’acte"
              className="pl-9"
            />
          </div>
          {natures && (
            <p className="text-sm text-fg-muted" data-testid="delais-resume">
              {natures.length} nature{natures.length > 1 ? 's' : ''} d’acte ; {aConfirmer} délai{aConfirmer > 1 ? 's' : ''} à
              confirmer.
            </p>
          )}
        </div>

        {natures === null && !erreur && (
          <SqueletteZone libelle="Chargement des délais…" className="divide-y divide-border rounded-xl border border-border bg-bg-raised">
            {Array.from({ length: 4 }).map((_, i) => (
              <div key={i} className="space-y-2 p-4">
                <Skeleton className="h-4 w-1/3" />
                <Skeleton className="h-3 w-1/2" />
              </div>
            ))}
          </SqueletteZone>
        )}
        {natures && natures.length === 0 && (
          <EmptyState
            title="Aucun délai"
            description="Le référentiel des démarches n’est pas encore importé. Les délais apparaîtront ici dès son import par l’éditeur."
          />
        )}
        {natures && natures.length > 0 && visibles.length === 0 && (
          <EmptyState
            compact
            title="Aucun résultat"
            description={`Aucune nature d’acte ne correspond à « ${filtre.trim()} ». Vérifiez l’orthographe ou effacez la recherche.`}
            cta={
              <Button type="button" variant="secondary" size="sm" onClick={() => setFiltre('')}>
                Effacer la recherche
              </Button>
            }
          />
        )}

        {visibles.length > 0 && (
          <ul className="divide-y divide-border rounded-xl border border-border bg-bg-raised shadow-card">
            {visibles.map((n) => {
              const cle = `${n.matrice}|${n.decisionCode}`;
              return (
                <li key={cle} className="space-y-1.5 p-4" data-testid={`delai-${n.decisionCode}`}>
                  <div className="flex flex-wrap items-start justify-between gap-2">
                    <div className="min-w-0">
                      <p className="font-medium text-heading">{n.decisionLibelle ?? n.decisionCode}</p>
                      <p className="text-xs text-fg-muted">Matrice : {libelleMatrice(n.matrice)}</p>
                    </div>
                    <div className="flex items-center gap-2 text-sm">
                      <span className="font-semibold tabular-nums text-fg">
                        {n.ajustement
                          ? libelleDelai(n.ajustement.delaiValeur, n.ajustement.delaiUnite)
                          : libelleDelai(n.delaiValeur, n.delaiUnite)}
                      </span>
                      {n.ajustement ? (
                        <Badge variant="info">ajusté par le cabinet</Badge>
                      ) : n.statut === 'A_CONFIRMER' ? (
                        <span data-testid="a-confirmer">
                          <Badge variant="warning">à confirmer</Badge>
                        </span>
                      ) : (
                        <Badge variant="success">cité par le corpus</Badge>
                      )}
                    </div>
                  </div>
                  <p className="text-sm text-fg-muted">
                    Article : {n.ajustement?.article ?? n.article ?? 'aucun article cité par le corpus'}{' '}
                    <InfoBulle
                      libelle="D’où vient ce délai ?"
                      texte={n.delaiTexte ? `Texte de la matrice : ${n.delaiTexte}` : 'La matrice ne donne pas de texte pour ce délai.'}
                    />
                  </p>
                  {n.ajustement?.note && <p className="text-sm text-fg-muted">Note du cabinet : {n.ajustement.note}</p>}
                  {peutAjuster && edition !== cle && (
                    <div className="flex flex-wrap gap-2 pt-1">
                      <Button type="button" variant="secondary" size="sm" onClick={() => ouvrir(n)}>
                        Ajuster
                      </Button>
                      {n.ajustement && (
                        <Button type="button" variant="ghost" size="sm" onClick={() => retirer(n)}>
                          Rétablir le délai du corpus
                        </Button>
                      )}
                    </div>
                  )}
                  {peutAjuster && edition === cle && (
                    <form
                      className="grid gap-3 rounded-lg border border-border bg-bg-overlay p-3 md:grid-cols-4"
                      data-testid="edition-delai"
                      onSubmit={(e) => {
                        e.preventDefault();
                        void enregistrer(n);
                      }}
                    >
                      <TextField
                        label="Délai"
                        inputMode="numeric"
                        value={saisie.valeur}
                        onChange={(e) => setSaisie((s) => ({ ...s, valeur: e.target.value }))}
                      />
                      <Select
                        label="Unité"
                        value={saisie.unite}
                        onChange={(e) => setSaisie((s) => ({ ...s, unite: e.target.value as 'JOURS' | 'MOIS' }))}
                        options={[
                          { value: 'JOURS', label: 'jours' },
                          { value: 'MOIS', label: 'mois' },
                        ]}
                      />
                      <TextField
                        label="Article"
                        placeholder="Ex. art. 75 du code de commerce"
                        value={saisie.article}
                        onChange={(e) => setSaisie((s) => ({ ...s, article: e.target.value }))}
                        containerClassName="md:col-span-2"
                      />
                      <TextField
                        label="Note"
                        hint="Facultative : la raison de l’ajustement."
                        value={saisie.note}
                        onChange={(e) => setSaisie((s) => ({ ...s, note: e.target.value }))}
                        containerClassName="md:col-span-3"
                      />
                      <div className="flex items-end gap-2">
                        <Button type="submit" size="sm">
                          Enregistrer
                        </Button>
                        <Button type="button" variant="ghost" size="sm" onClick={() => setEdition(null)}>
                          Annuler
                        </Button>
                      </div>
                    </form>
                  )}
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </Page>
  );
}
