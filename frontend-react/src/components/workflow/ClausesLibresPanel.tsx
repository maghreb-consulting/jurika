import { useEffect, useState } from 'react';
import { InfoBulle, TexteAide } from '../ui/Aide';
import { Button } from '../ui/Button';
import { Select } from '../ui/Select';
import { TextField, ZoneTexte } from '../ui/TextField';
import { extractError } from '../../lib/api';
import {
  emplacementClausesLibres,
  type EmplacementClauses,
} from '../../services/workflowDocumentService';
import { workflowService, type ClauseLibre } from '../../services/workflow.service';

/**
 * Lot L3 (RG-GEN-05 a 07) : clauses libres d'un document.
 *
 * L'employe ajoute une ou plusieurs clauses redigees librement (titre et texte).
 * Lot L4d (E10, decision du directeur du 11 octobre 2026) : il en choisit l'emplacement
 * (statuts : apres l'article n ; proces-verbal : avant la resolution n ou a la suite) ; la
 * numerotation et les renvois sont mis a jour a la generation, sinon elle est refusee avec
 * la raison. Chaque clause est une donnee du ticket : elle est
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
    // Seuls les statuts disent pourquoi ; ailleurs, rien a proposer.
    return templateCode.startsWith('STATUTS') ? (
      <p className="rounded-lg border border-border bg-bg-overlay p-2 text-xs text-fg-subtle">
        Clauses particulières : {emplacement.motif}
      </p>
    ) : null;
  }
  const statuts = emplacement.nature === 'ARTICLE';

  function maj(i: number, patch: Partial<ClauseLibre>) {
    setBrouillon((b) => b.map((c, j) => (j === i ? { ...c, ...patch } : c)));
  }

  async function enregistrer() {
    setSucces(null);
    const sansPlace = brouillon.findIndex((c) => statuts && !(c.emplacement ?? '').startsWith('APRES_ARTICLE:'));
    if (sansPlace >= 0) {
      setErreur(`Choisissez l’article après lequel placer la clause n° ${sansPlace + 1}.`);
      return;
    }
    setEnCours(true);
    setErreur(null);
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

  return (
    <section aria-label="Clauses libres" className="space-y-2 rounded-lg border border-border bg-bg-overlay p-3">
      <p className="flex items-center gap-1 text-xs font-semibold text-fg">
        {statuts ? 'Clauses particulières' : 'Clauses libres'}
        <InfoBulle
          libelle="Qu’est-ce qu’une clause libre ?"
          texte={statuts
            ? 'Un article rédigé librement, ajouté aux statuts après l’article que vous choisissez. Les articles suivants sont renumérotés et les renvois des statuts (« l’article 14 ci-dessus ») mis à jour.'
            : 'Une résolution rédigée librement, ajoutée au procès-verbal à l’emplacement que vous choisissez. Les résolutions sont renumérotées et les renvois (« la deuxième résolution ») mis à jour.'}
        />
      </p>
      <TexteAide cle="clauses-libres" titre="Ajouter une clause libre">
        <p>
          Choisissez l’emplacement, donnez un titre et un texte{statuts ? '' : ', puis le résultat du vote'}.
          Relisez le texte : il est imprimé tel quel. La numérotation et les renvois sont mis à jour à
          la génération ; si un renvoi ne peut pas l’être sans risque, la génération est refusée et la
          raison affichée : choisissez alors un autre emplacement. La clause est enregistrée avec votre
          nom et la date, et reprise à chaque régénération du document.
        </p>
      </TexteAide>
      {brouillon.map((c, i) => (
        <div key={i} className="space-y-2 rounded-md border border-border bg-bg-raised p-2">
          <p className="text-xs text-fg-subtle">
            {c.saisieLe ? `Enregistrée le ${new Date(c.saisieLe).toLocaleString('fr-MA')}` : 'Non enregistrée'}
          </p>
          <ChoixEmplacement emplacement={emplacement} valeur={c.emplacement ?? null} index={i}
            onChange={(v) => maj(i, { emplacement: v })} />
          <TextField label="Titre de la clause" value={c.titre ?? ''} spellCheck lang="fr" onChange={(e) => maj(i, { titre: e.target.value })} />
          <ZoneTexte label="Texte de la clause" rows={4} value={c.texte ?? ''} spellCheck lang="fr" onChange={(e) => maj(i, { texte: e.target.value })} />
          {!statuts && (
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
            <Select label="Résultat du vote" value={c.resultat ?? ''} onChange={(e) => maj(i, { resultat: e.target.value })}
              options={[
                { value: '', label: 'Choisir…' },
                { value: 'adoptée', label: 'Adoptée' },
                { value: 'rejetée', label: 'Rejetée' },
              ]} />
            <TextField label="Voix pour" value={c.voixPour ?? ''} onChange={(e) => maj(i, { voixPour: e.target.value })} />
            <TextField label="Voix contre" value={c.voixContre ?? ''} onChange={(e) => maj(i, { voixContre: e.target.value })} />
            <TextField label="Abstentions" value={c.abstentions ?? ''} onChange={(e) => maj(i, { abstentions: e.target.value })} />
          </div>
          )}
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
            setBrouillon((b) => [...b, { document: templateCode, emplacement: statuts ? null : emplacement.emplacement ?? null, titre: '', texte: '' }])
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

/** Lot L4d (E10) : choix de l'emplacement d'une clause (article des statuts, ou rang parmi les résolutions). */
function ChoixEmplacement({ emplacement, valeur, index, onChange }: {
  emplacement: EmplacementClauses;
  valeur: string | null;
  index: number;
  onChange: (v: string) => void;
}) {
  if (emplacement.nature === 'ARTICLE') {
    return (
      <Select label="Emplacement" aria-label={`Emplacement de la clause ${index + 1}`} value={valeur ?? ''}
        onChange={(e) => onChange(e.target.value)}
        options={[
          { value: '', label: 'Choisir l’article après lequel placer la clause…' },
          ...(emplacement.articles ?? []).map((a) => ({
            value: `APRES_ARTICLE:${a.numero}`,
            label: `Après l’article ${a.numero} — ${a.titre}`,
          })),
        ]} />
    );
  }
  const avant = valeur?.startsWith('AVANT_RESOLUTION:') ? valeur.slice('AVANT_RESOLUTION:'.length) : null;
  return (
    <div className="grid gap-2 sm:grid-cols-2">
      <Select label="Emplacement" aria-label={`Emplacement de la clause ${index + 1}`}
        value={avant === null ? 'SUITE' : 'AVANT'}
        onChange={(e) => onChange(e.target.value === 'SUITE' ? 'A_LA_SUITE_DES_RESOLUTIONS' : 'AVANT_RESOLUTION:1')}
        options={[
          { value: 'SUITE', label: 'À la suite des résolutions' },
          { value: 'AVANT', label: 'Avant une résolution du modèle' },
        ]} />
      {avant !== null && (
        <TextField label="Avant la résolution n°" aria-label={`Rang de la résolution ${index + 1}`} inputMode="numeric" value={avant}
          onChange={(e) => onChange(`AVANT_RESOLUTION:${e.target.value.replace(/\D/g, '')}`)} />
      )}
    </div>
  );
}
