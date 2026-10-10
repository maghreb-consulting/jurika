import { useEffect, useState } from 'react';
import { InfoBulle, TexteAide } from '../ui/Aide';
import { Button } from '../ui/Button';
import { extractError } from '../../lib/api';
import {
  emplacementClausesLibres,
  type EmplacementClauses,
} from '../../services/workflowDocumentService';
import { workflowService, type ClauseLibre } from '../../services/workflow.service';

/**
 * Lot L3 (RG-GEN-05 a 07) : clauses libres d'un document.
 *
 * L'employe ajoute une ou plusieurs clauses redigees librement (titre et texte) a
 * l'emplacement prevu par le modele. Chaque clause est une donnee du ticket : elle est
 * enregistree au magasin, avec son auteur et sa date, et reutilisee a chaque
 * regeneration. Le correcteur orthographique du navigateur s'applique a la saisie.
 */
export function ClausesLibresPanel({
  ticketId,
  workflowCode,
  templateCode,
  onEnregistre,
}: {
  ticketId: string;
  workflowCode: string;
  templateCode: string;
  /** Appele apres l'enregistrement (par exemple pour regenerer le document). */
  onEnregistre?: () => void;
}) {
  const [emplacement, setEmplacement] = useState<EmplacementClauses | null>(null);
  const [toutes, setToutes] = useState<ClauseLibre[]>([]);
  const [brouillon, setBrouillon] = useState<ClauseLibre[]>([]);
  const [erreur, setErreur] = useState<string | null>(null);
  const [enCours, setEnCours] = useState(false);
  const [succes, setSucces] = useState<string | null>(null);

  useEffect(() => {
    let actif = true;
    Promise.all([emplacementClausesLibres(workflowCode, templateCode), workflowService.clausesLibres(ticketId)])
      .then(([e, c]) => {
        if (!actif) return;
        setEmplacement(e);
        setToutes(c);
        setBrouillon(c.filter((x) => x.document === templateCode));
      })
      .catch((err) => actif && setErreur(extractError(err).message));
    return () => {
      actif = false;
    };
  }, [ticketId, workflowCode, templateCode]);

  if (!emplacement) return erreur ? <p className="text-xs text-danger">{erreur}</p> : null;
  if (!emplacement.possible) {
    // Seuls les statuts annoncent l'attente du cabinet ; ailleurs, rien a proposer.
    return templateCode.startsWith('STATUTS') ? (
      <p className="rounded-lg border border-border bg-bg-overlay p-2 text-xs text-fg-subtle">
        Clauses particulières : {emplacement.motif}
      </p>
    ) : null;
  }

  function maj(i: number, patch: Partial<ClauseLibre>) {
    setBrouillon((b) => b.map((c, j) => (j === i ? { ...c, ...patch } : c)));
  }

  async function enregistrer() {
    setEnCours(true);
    setErreur(null);
    setSucces(null);
    try {
      const autres = toutes.filter((x) => x.document !== templateCode);
      const resultat = await workflowService.remplacerClausesLibres(ticketId, [...autres, ...brouillon]);
      setToutes(resultat);
      setBrouillon(resultat.filter((x) => x.document === templateCode));
      setSucces('Clauses enregistrées : elles seront imprimées à chaque génération du document.');
      onEnregistre?.();
    } catch (err) {
      setErreur(extractError(err).message);
    } finally {
      setEnCours(false);
    }
  }

  const champ = 'mt-1 block w-full rounded-md border border-border bg-bg-raised p-2 text-sm text-fg';
  return (
    <section aria-label="Clauses libres" className="space-y-2 rounded-lg border border-border bg-bg-overlay p-3">
      <p className="flex items-center gap-1 text-xs font-semibold text-fg">
        Clauses libres
        <InfoBulle
          libelle="Qu’est-ce qu’une clause libre ?"
          texte="Une résolution rédigée librement, ajoutée au procès-verbal à l’emplacement prévu par le modèle (à la suite des résolutions). Elle est imprimée telle que saisie, dans la mise en forme du modèle."
        />
      </p>
      <TexteAide cle="clauses-libres" titre="Ajouter une clause libre">
        <p>
          Donnez un titre et un texte, puis le résultat du vote. Relisez le texte : il est imprimé tel
          quel. La clause est enregistrée avec votre nom et la date, et elle est reprise à chaque
          régénération du document.
        </p>
      </TexteAide>
      {brouillon.map((c, i) => (
        <div key={i} className="space-y-2 rounded-md border border-border bg-bg-raised p-2">
          <p className="text-xs text-fg-subtle">
            {emplacement.libelle}
            {c.saisieLe ? ` · enregistrée le ${new Date(c.saisieLe).toLocaleString('fr-MA')}` : ' · non enregistrée'}
          </p>
          <label className="block text-xs text-fg-muted">
            Titre de la clause
            <input className={champ} value={c.titre ?? ''} spellCheck lang="fr" onChange={(e) => maj(i, { titre: e.target.value })} />
          </label>
          <label className="block text-xs text-fg-muted">
            Texte de la clause
            <textarea className={champ} rows={4} value={c.texte ?? ''} spellCheck lang="fr" onChange={(e) => maj(i, { texte: e.target.value })} />
          </label>
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
            <label className="block text-xs text-fg-muted">
              Résultat du vote
              <select className={champ} value={c.resultat ?? ''} onChange={(e) => maj(i, { resultat: e.target.value })}>
                <option value="">Choisir…</option>
                <option value="adoptée">Adoptée</option>
                <option value="rejetée">Rejetée</option>
              </select>
            </label>
            <label className="block text-xs text-fg-muted">
              Voix pour
              <input className={champ} value={c.voixPour ?? ''} onChange={(e) => maj(i, { voixPour: e.target.value })} />
            </label>
            <label className="block text-xs text-fg-muted">
              Voix contre
              <input className={champ} value={c.voixContre ?? ''} onChange={(e) => maj(i, { voixContre: e.target.value })} />
            </label>
            <label className="block text-xs text-fg-muted">
              Abstentions
              <input className={champ} value={c.abstentions ?? ''} onChange={(e) => maj(i, { abstentions: e.target.value })} />
            </label>
          </div>
          <Button size="sm" variant="ghost" onClick={() => setBrouillon((b) => b.filter((_, j) => j !== i))}>
            Retirer cette clause
          </Button>
        </div>
      ))}
      <div className="flex flex-wrap gap-2">
        <Button
          size="sm"
          variant="secondary"
          onClick={() =>
            setBrouillon((b) => [...b, { document: templateCode, emplacement: emplacement.emplacement ?? null, titre: '', texte: '' }])
          }
        >
          Ajouter une clause
        </Button>
        <Button size="sm" onClick={() => void enregistrer()} loading={enCours}>
          Enregistrer les clauses
        </Button>
      </div>
      {succes && <p role="status" className="text-xs text-success">{succes}</p>}
      {erreur && <p role="alert" className="text-xs text-danger">{erreur}</p>}
    </section>
  );
}
