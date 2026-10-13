import { useCallback, useEffect, useMemo, useState } from 'react';
import { Button } from '../../components/ui/Button';
import { InfoBulle, TexteAide } from '../../components/ui/Aide';
import { extractError } from '../../lib/api';
import { useCurrentUser } from '../../store/authStore';
import {
  delaisGreffeService,
  type NatureActe,
  type SaisieDelai,
} from '../../services/delaisGreffe.service';

/**
 * Lot L4d (E7), décision du directeur du 11 octobre 2026 : délais de dépôt au greffe par
 * nature d'acte. Le délai et l'article viennent du corpus du cabinet ; un délai sans article,
 * ou marqué « probable » par la matrice, est « à confirmer ». Le superviseur peut l'ajuster
 * pour le cabinet : l'ajustement s'applique aux démarches de dépôt des tickets.
 */
function libelleDelai(valeur: number | null, unite: string | null): string {
  if (valeur == null || !unite) return '—';
  return `${valeur} ${unite === 'JOURS' ? 'jours' : 'mois'}`;
}

function libelleMatrice(m: string): string {
  return m.replace(/^MATRICE_/, '').replace(/_/g, ' ').toLowerCase();
}

export function DelaisGreffePage() {
  const user = useCurrentUser();
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
      charger();
    } catch (e) {
      setErreur(extractError(e).message);
    }
  }

  async function retirer(n: NatureActe) {
    try {
      await delaisGreffeService.retirer(n.matrice, n.decisionCode);
      charger();
    } catch (e) {
      setErreur(extractError(e).message);
    }
  }

  return (
    <div className="space-y-4 p-4 md:p-6">
      <h1 className="text-xl font-bold text-fg">Délais de dépôt au greffe</h1>
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

      {erreur && <p role="alert" className="text-sm text-danger">{erreur}</p>}

      {natures && (
        <p className="text-sm text-fg-subtle" data-testid="delais-resume">
          {natures.length} nature{natures.length > 1 ? 's' : ''} d’acte ; {aConfirmer} délai{aConfirmer > 1 ? 's' : ''} à
          confirmer.
        </p>
      )}

      <input
        type="search"
        value={filtre}
        onChange={(e) => setFiltre(e.target.value)}
        placeholder="Rechercher une nature d’acte, un article…"
        aria-label="Rechercher une nature d’acte"
        className="w-full rounded-lg border border-border-hi bg-bg-raised px-3 py-2 text-sm md:w-96"
      />

      {natures === null && !erreur && <p className="text-sm text-fg-subtle">Chargement…</p>}
      {natures && natures.length === 0 && (
        <p className="text-sm text-fg-subtle">
          Aucun délai : le référentiel des démarches n’est pas encore importé.
        </p>
      )}
      {natures && natures.length > 0 && visibles.length === 0 && (
        <p className="text-sm text-fg-subtle">
          Aucune nature d’acte ne correspond à « {filtre.trim()} ». Vérifiez l’orthographe ou effacez la recherche.
        </p>
      )}

      <ul className="divide-y divide-border rounded-xl border border-border bg-bg-raised">
        {visibles.map((n) => {
          const cle = `${n.matrice}|${n.decisionCode}`;
          return (
            <li key={cle} className="space-y-1 p-3" data-testid={`delai-${n.decisionCode}`}>
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div>
                  <p className="text-sm font-medium text-fg">{n.decisionLibelle ?? n.decisionCode}</p>
                  <p className="text-xs text-fg-subtle">Matrice : {libelleMatrice(n.matrice)}</p>
                </div>
                <div className="flex items-center gap-2 text-sm">
                  <span className="font-semibold text-fg">
                    {n.ajustement
                      ? libelleDelai(n.ajustement.delaiValeur, n.ajustement.delaiUnite)
                      : libelleDelai(n.delaiValeur, n.delaiUnite)}
                  </span>
                  {n.ajustement ? (
                    <span className="rounded-full bg-accent/10 px-2 py-0.5 text-[11px] text-accent">ajusté par le cabinet</span>
                  ) : n.statut === 'A_CONFIRMER' ? (
                    <span className="rounded-full bg-warning/10 px-2 py-0.5 text-[11px] text-warning" data-testid="a-confirmer">
                      à confirmer
                    </span>
                  ) : (
                    <span className="rounded-full bg-success/10 px-2 py-0.5 text-[11px] text-success">cité par le corpus</span>
                  )}
                </div>
              </div>
              <p className="text-xs text-fg-subtle">
                Article : {n.ajustement?.article ?? n.article ?? 'aucun article cité par le corpus'}
                {' '}
                <InfoBulle
                  libelle="D’où vient ce délai ?"
                  texte={n.delaiTexte ? `Texte de la matrice : ${n.delaiTexte}` : 'La matrice ne donne pas de texte pour ce délai.'}
                />
              </p>
              {n.ajustement?.note && <p className="text-xs text-fg-subtle">Note du cabinet : {n.ajustement.note}</p>}
              {peutAjuster && edition !== cle && (
                <div className="flex gap-2">
                  <Button variant="secondary" size="sm" onClick={() => ouvrir(n)}>Ajuster</Button>
                  {n.ajustement && (
                    <Button variant="ghost" size="sm" onClick={() => retirer(n)}>Rétablir le délai du corpus</Button>
                  )}
                </div>
              )}
              {peutAjuster && edition === cle && (
                <div className="grid gap-2 md:grid-cols-4" data-testid="edition-delai">
                  <input aria-label="Délai" inputMode="numeric" value={saisie.valeur}
                    onChange={(e) => setSaisie((s) => ({ ...s, valeur: e.target.value }))}
                    className="rounded-lg border border-border-hi bg-bg px-2 py-1 text-sm" />
                  <select aria-label="Unité" value={saisie.unite}
                    onChange={(e) => setSaisie((s) => ({ ...s, unite: e.target.value as 'JOURS' | 'MOIS' }))}
                    className="rounded-lg border border-border-hi bg-bg px-2 py-1 text-sm">
                    <option value="JOURS">jours</option>
                    <option value="MOIS">mois</option>
                  </select>
                  <input aria-label="Article" placeholder="Article (ex. art. 75 du code de commerce)" value={saisie.article}
                    onChange={(e) => setSaisie((s) => ({ ...s, article: e.target.value }))}
                    className="rounded-lg border border-border-hi bg-bg px-2 py-1 text-sm md:col-span-2" />
                  <input aria-label="Note" placeholder="Note du cabinet (facultative)" value={saisie.note}
                    onChange={(e) => setSaisie((s) => ({ ...s, note: e.target.value }))}
                    className="rounded-lg border border-border-hi bg-bg px-2 py-1 text-sm md:col-span-3" />
                  <div className="flex gap-2">
                    <Button size="sm" onClick={() => enregistrer(n)}>Enregistrer</Button>
                    <Button variant="ghost" size="sm" onClick={() => setEdition(null)}>Annuler</Button>
                  </div>
                </div>
              )}
            </li>
          );
        })}
      </ul>
    </div>
  );
}
