import { useEffect, useState } from 'react';
import { Download } from 'lucide-react';
import { Button } from '../../components/ui/Button';
import { InfoBulle, TexteAide } from '../../components/ui/Aide';
import { extractError } from '../../lib/api';
import {
  lireRapport,
  referentielService,
  type EtatReferentiel,
  type ResultatImport,
} from '../../services/referentiel.service';

/**
 * Lot L4b (E2, RG-DEM-01) : le referentiel des demarches vient du corpus du cabinet
 * (onglets « Demarches » des matrices). Le super-administrateur l'importe apres chaque
 * livraison ; une version refusee ne change rien, une version appliquee remplace la
 * precedente pour les nouveaux tickets sans toucher aux cochages existants.
 */
export function ReferentielDemarchesPage() {
  const [etat, setEtat] = useState<EtatReferentiel | null>(null);
  const [erreur, setErreur] = useState<string | null>(null);
  const [enCours, setEnCours] = useState(false);
  const [resultat, setResultat] = useState<ResultatImport | null>(null);
  const [ouvert, setOuvert] = useState<string | null>(null);

  useEffect(() => {
    let actif = true;
    referentielService
      .etat()
      .then((e) => actif && setEtat(e))
      .catch((err) => actif && setErreur(extractError(err).message));
    return () => {
      actif = false;
    };
  }, []);

  async function importer() {
    setEnCours(true);
    setErreur(null);
    try {
      setResultat(await referentielService.importer());
      setEtat(await referentielService.etat());
    } catch (err) {
      setErreur(extractError(err).message);
    } finally {
      setEnCours(false);
    }
  }

  return (
    <div className="space-y-6 p-6">
      <header className="space-y-1">
        <h1 className="flex items-center gap-1 text-xl font-semibold text-fg">
          Référentiel des démarches
          <InfoBulle
            libelle="D'où vient ce référentiel ?"
            texte="Des onglets « Démarches » des matrices du corpus livré par le cabinet : une ligne par démarche, avec son statut, son organisme, sa condition, son délai, son justificatif et son modèle. Il pilote le déroulement des tickets hors création."
          />
        </h1>
        <p className="text-sm text-fg-muted">
          Version appliquée : <strong data-testid="version-active">{etat?.versionActive ?? 'aucune'}</strong>
        </p>
      </header>

      <TexteAide cle="referentiel-import" titre="Quand importer le référentiel ?">
        Après chaque livraison du corpus par le cabinet. L'import lit les onglets « Démarches » et « Renvois » et
        les décisions de chaque matrice. Il est refusé si l'analyse relève une erreur (statut inconnu, onglet
        illisible) : la version précédente reste alors en service. Une version appliquée sert aux nouveaux
        tickets ; les démarches déjà cochées restent rattachées à leur ligne d'origine. Les délais que la matrice
        exprime en texte et les conditions rédigées en phrase sont listés au rapport : l'employé les appréciera
        sur la ligne.
      </TexteAide>

      <div className="flex items-center gap-3">
        <Button variant="primary" disabled={enCours} onClick={() => void importer()}>
          <Download className="mr-1 h-4 w-4" /> {enCours ? 'Import en cours…' : 'Importer le référentiel du corpus'}
        </Button>
      </div>

      {erreur && <p role="alert" className="text-sm text-danger">{erreur}</p>}
      {resultat && (
        <p data-testid="resultat-import"
          className={`rounded-lg border p-3 text-sm ${resultat.statut === 'APPLIQUE' ? 'border-success/40 bg-success/10' : 'border-danger/40 bg-danger/10'}`}>
          {resultat.statut === 'APPLIQUE'
            ? `Version ${resultat.version} appliquée : ${resultat.lignes} démarches, ${resultat.decisions} décisions, ${resultat.renvois} renvois.`
            : `Version ${resultat.version} refusée : ${resultat.motif ?? ''}`}
        </p>
      )}

      <section className="space-y-2">
        <h2 className="text-sm font-semibold text-fg">Journal des imports</h2>
        {etat && etat.imports.length === 0 && (
          <p className="text-sm text-fg-subtle">Aucun import : les tickets hors création n'ont pas encore de démarches du corpus.</p>
        )}
        <ul className="space-y-2">
          {etat?.imports.map((i) => {
            const rapport = lireRapport(i.rapport);
            return (
              <li key={i.id} data-testid={`import-${i.id}`} className="rounded-lg border border-border p-3 text-sm">
                <div className="flex flex-wrap items-center gap-3">
                  <span className={i.statut === 'APPLIQUE' ? 'font-medium text-success' : 'font-medium text-danger'}>
                    {i.statut === 'APPLIQUE' ? 'Appliquée' : 'Refusée'}
                  </span>
                  <span className="font-mono">{i.corpus_version}</span>
                  <span className="text-fg-muted">{new Date(i.importe_le).toLocaleString('fr-FR')}</span>
                  {i.statut === 'APPLIQUE' && (
                    <span className="text-fg-muted">{i.lignes} démarches · {i.decisions} décisions · {i.renvois} renvois</span>
                  )}
                  <Button variant="secondary" onClick={() => setOuvert(ouvert === i.id ? null : i.id)}>
                    {ouvert === i.id ? 'Masquer le rapport' : 'Voir le rapport'}
                  </Button>
                </div>
                {i.motif && <p className="mt-1 text-danger">{i.motif}</p>}
                {ouvert === i.id && (
                  <div className="mt-2 space-y-2 text-xs text-fg-muted">
                    <p>
                      {rapport.total ?? 0} lignes lues ; {rapport.conditionsPhrases?.length ?? 0} conditions rédigées en
                      phrase (à apprécier par l'employé) ; {rapport.delaisTextuels?.length ?? 0} délais en texte seul
                      (point de départ ou durée non chiffrés par la matrice).
                    </p>
                    {rapport.lignesParOnglet && (
                      <ul className="list-disc pl-5">
                        {Object.entries(rapport.lignesParOnglet).map(([m, n]) => (
                          <li key={m}><span className="font-mono">{m}</span> : {n}</li>
                        ))}
                      </ul>
                    )}
                    {(rapport.erreurs ?? []).length > 0 && (
                      <ul className="list-disc pl-5 text-danger">
                        {rapport.erreurs!.map((e) => <li key={e}>{e}</li>)}
                      </ul>
                    )}
                    {(rapport.conditionsPhrases ?? []).length > 0 && (
                      <details>
                        <summary>Conditions en phrase</summary>
                        <ul className="list-disc pl-5">{rapport.conditionsPhrases!.map((c) => <li key={c}>{c}</li>)}</ul>
                      </details>
                    )}
                    {(rapport.delaisTextuels ?? []).length > 0 && (
                      <details>
                        <summary>Délais en texte seul</summary>
                        <ul className="list-disc pl-5">{rapport.delaisTextuels!.map((d) => <li key={d}>{d}</li>)}</ul>
                      </details>
                    )}
                  </div>
                )}
              </li>
            );
          })}
        </ul>
      </section>
    </div>
  );
}
