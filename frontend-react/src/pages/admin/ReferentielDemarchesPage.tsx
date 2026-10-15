import { useEffect, useState } from 'react';
import { Download } from 'lucide-react';
import { Page } from '../../components/layout/Page';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { Message } from '../../components/ui/Message';
import { EmptyState } from '../../components/ui/EmptyState';
import { CardSkeleton, SqueletteZone } from '../../components/ui/LoadingSkeleton';
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
 * precedente ; lot L4d (E3) : l'avancement des tickets en cours suit la nouvelle version (V37).
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
    <Page
      titre="Référentiel des démarches"
      infoBulle="Des onglets « Démarches » des matrices du corpus livré par le cabinet : une ligne par démarche, avec son statut, son organisme, sa condition, son délai, son justificatif et son modèle. Il pilote le déroulement des tickets hors création."
      description={
        <>
          Version appliquée : <strong className="font-mono text-fg" data-testid="version-active">{etat ? (etat.versionActive ?? 'aucune') : '…'}</strong>
        </>
      }
      action={
        <Button type="button" variant="primary" loading={enCours} disabled={enCours} onClick={() => void importer()} icon={<Download />}>
          {enCours ? 'Import en cours…' : 'Importer le référentiel du corpus'}
        </Button>
      }
      aide={
      <TexteAide cle="referentiel-import" titre="Quand importer le référentiel ?">
        Après chaque livraison du corpus par le cabinet. L’import lit les onglets « Démarches » et « Renvois » et
        les décisions de chaque matrice. Il est refusé si l'analyse relève une erreur (statut inconnu, onglet
        illisible) : la version précédente reste alors en service. Une version appliquée remplace la précédente ;
        l’avancement des tickets en cours (démarches cochées, délais saisis) suit la ligne de même place dans la
        nouvelle version, et ce qui ne trouve pas de ligne correspondante est compté au rapport. Une nouvelle
        version du corpus montée sur le serveur s’importe d’elle-même au démarrage. Les délais que la matrice
        exprime en texte et les conditions rédigées en phrase sont listés au rapport : l’employé les appréciera
        sur la ligne.
      </TexteAide>
      }
    >
    <div className="space-y-6">
      {erreur && <Message ton="erreur">{erreur}</Message>}
      {resultat && (
        <div data-testid="resultat-import">
          <Message ton={resultat.statut === 'APPLIQUE' ? 'succes' : 'erreur'}>
            {resultat.statut === 'APPLIQUE'
              ? `Version ${resultat.version} appliquée : ${resultat.lignes} démarches, ${resultat.decisions} décisions, ${resultat.renvois} renvois.`
              : `Version ${resultat.version} refusée : ${resultat.motif ?? ''}`}
          </Message>
        </div>
      )}

      <section className="space-y-3" aria-labelledby="titre-journal-imports">
        <h2 id="titre-journal-imports" className="text-lg font-semibold text-heading">Journal des imports</h2>
        {etat === null && !erreur && (
          <SqueletteZone libelle="Chargement du journal des imports…" className="space-y-2">
            <CardSkeleton className="h-16" />
            <CardSkeleton className="h-16" />
          </SqueletteZone>
        )}
        {etat && etat.imports.length === 0 && (
          <EmptyState
            title="Aucun import"
            description="Les tickets hors création n’ont pas encore de démarches du corpus. Cliquez sur « Importer le référentiel du corpus », en haut à droite."
          />
        )}
        <ul className="space-y-2">
          {etat?.imports.map((i) => {
            const rapport = lireRapport(i.rapport);
            return (
              <li key={i.id} data-testid={`import-${i.id}`} className="rounded-xl border border-border bg-bg-raised p-4 text-sm shadow-card">
                <div className="flex flex-wrap items-center gap-3">
                  <Badge variant={i.statut === 'APPLIQUE' ? 'success' : 'danger'}>
                    {i.statut === 'APPLIQUE' ? 'Appliquée' : 'Refusée'}
                  </Badge>
                  <span className="font-mono text-fg">{i.corpus_version}</span>
                  <span className="tabular-nums text-fg-muted">{new Date(i.importe_le).toLocaleString('fr-FR')}</span>
                  {i.statut === 'APPLIQUE' && (
                    <span className="text-fg-muted">{i.lignes} démarches · {i.decisions} décisions · {i.renvois} renvois</span>
                  )}
                  <Button type="button" variant="secondary" size="sm" className="ml-auto" aria-expanded={ouvert === i.id} onClick={() => setOuvert(ouvert === i.id ? null : i.id)}>
                    {ouvert === i.id ? 'Masquer le rapport' : 'Voir le rapport'}
                  </Button>
                </div>
                {i.motif && <p className="mt-1 text-danger">{i.motif}</p>}
                {ouvert === i.id && (
                  <div className="mt-3 space-y-2 border-t border-border pt-3 text-sm text-fg-muted [&_summary]:cursor-pointer [&_summary]:rounded-sm [&_summary]:font-medium [&_summary]:text-fg">
                    <p>
                      {rapport.total ?? 0} lignes lues ; {rapport.conditionsPhrases?.length ?? 0} conditions rédigées en
                      phrase (à apprécier par l’employé) ; {rapport.delaisTextuels?.length ?? 0} délais en texte seul
                      (point de départ ou durée non chiffrés par la matrice).
                    </p>
                    {rapport.etatsReportes !== undefined && (
                      <p data-testid="etats-reportes">
                        Avancement des tickets en cours : {rapport.etatsReportes} état(s) reporté(s) sur la nouvelle
                        version ; {rapport.etatsNonReportes ?? 0} sans ligne correspondante (restés sur l’ancienne ligne).
                      </p>
                    )}
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
                    {rapport.ecartsCreation && (
                      <details data-testid="ecarts-creation">
                        <summary className="inline-flex items-center gap-1">
                          Création : {rapport.ecartsCreation.length} écart(s) avec les lignes en service
                          <InfoBulle
                            libelle="Pourquoi comparer la création ?"
                            texte="La création reste servie par ses 51 lignes en service, auxquelles sont rattachés les cochages et les formalités. Chaque différence avec le parcours livré par le cabinet est listée ici pour être arbitrée, jamais alignée en silence."
                          />
                        </summary>
                        <ul className="list-disc pl-5">{rapport.ecartsCreation.map((e) => <li key={e}>{e}</li>)}</ul>
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
    </Page>
  );
}
