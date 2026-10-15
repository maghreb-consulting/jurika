/**
 * Sous-formulaire de séance d'AG — COMMUN et RÉUTILISABLE (Phase A, 2026-08-08).
 *
 * Capture le nécessaire d'une assemblée : type/date/heure/lieu, convocation
 * (mode, date, auteur, rang, 2e convocation), bureau (président/secrétaire),
 * présences par associé (présent/représenté/absent + mandataire + voix), ordre
 * du jour, documents joints, résolutions. La liste des associés et l'identité
 * société sont pré-remplies depuis le dossier par la page hôte.
 *
 * Réutilisé par : ConvocationPanel (début de workflow), FeuillePresencePanel
 * (après l'AG) et IncidentSeancePanel (PV incident). Chaque panneau choisit les
 * sections visibles via `sections` et alimente le même contrat de séance
 * consommé côté back par SeancePvVarsBuilder (double nommage).
 */
import { Plus, Trash2 } from 'lucide-react';
import { TextField } from '../ui/TextField';
import { Select } from '../ui/Select';
import { Button } from '../ui/Button';
import { Case } from '../ui/Choix';
import { InfoBulle, TexteAide } from '../ui/Aide';
import { TypePieceProposee, ValeurProposee } from './ValeurProposee';
import {
  emptyAssocie,
  emptyGerant,
  emptyResolution,
  type AssocieInput,
  type GerantInput,
  type ResolutionInput,
  type SeanceDefaults,
  type SeanceFormApi,
  type SeanceSections,
  type VoteInput,
} from './seanceFormEtat';

// Types publics, re-exportes pour les appelants existants (les types ne genent pas le
// rafraichissement a chaud).
export type {
  FormeJuridique,
  SeanceSocieteInput,
  SeanceDefaults,
  ConvocationCorpusInput,
  ComptesInput,
  AssocieInput,
  ResolutionInput,
  GerantInput,
  SeanceSections,
  VoteInput,
  UseSeanceFormOptions,
  SeanceFormApi,
} from './seanceFormEtat';

/** Modes d'envoi tels que les modeles du corpus les testent (libelles exacts). */
const MODES_CONVOCATION = [
  { value: 'lettre recommandée avec accusé de réception', label: 'Lettre recommandée avec accusé de réception' },
  { value: 'remise en main propre contre décharge', label: 'Remise en main propre contre décharge' },
  { value: 'voie électronique', label: 'Voie électronique' },
];


// --------------------------------------------------------------------------
// Rendu des sections
// --------------------------------------------------------------------------
export function SeanceFormFields({
  form,
  sections,
  lockType,
  hideSeanceDate,
  hideSeanceHeure,
  hideConvDate,
  lockOrdreDuJour,
}: {
  form: SeanceFormApi;
  sections: SeanceSections;
  /** Masque le sélecteur « Type d'assemblée » quand il est déjà saisi en amont
   *  (ex. workflow Modification : nature choisie à l'étape 1). Évite le doublon. */
  lockType?: boolean;
  /** Masque la date de séance (= date de l'assemblée, déjà saisie en amont). */
  hideSeanceDate?: boolean;
  /** Masque l'heure de l'assemblée (déjà saisie en amont). */
  hideSeanceHeure?: boolean;
  /**
   * Masque la convocation DATE ET HEURE (déjà saisies en amont). Les deux vont
   * ensemble : une étape 1 qui porte la convocation porte les deux champs.
   */
  hideConvDate?: boolean;
  /**
   * Ordre du jour ENTIÈREMENT DÉRIVÉ des modifications choisies : affiché en lecture
   * seule, sans ajout / suppression / édition. Le seul contrôle est la sélection des
   * types de modification (étape 1).
   */
  lockOrdreDuJour?: boolean;
}) {
  const f = form;
  // L4d (E11b) : la lettre est signee par son auteur (nom), sa qualite en dessous : le premier
  // gerant est propose, jamais transmis sans confirmation.
  const premierGerant = f.gerants.find((g) => g?.nom?.trim() || g?.prenom?.trim());
  const auteurPropose = premierGerant
    ? [premierGerant.civilite, premierGerant.prenom, premierGerant.nom].filter(Boolean).join(' ').trim()
    : '';
  return (
    <div className="space-y-5">
      {sections.seance && (
        <Card title="Séance">
          <div className="grid gap-3 md:grid-cols-3">
            {!lockType && (
              <Select
                label="Type d’assemblée"
                value={f.type}
                onChange={(e) => f.setType(e.target.value as NonNullable<SeanceDefaults['type']>)}
                options={[
                  { value: 'ordinaire', label: 'Ordinaire' },
                  { value: 'extraordinaire', label: 'Extraordinaire' },
                  { value: 'mixte', label: 'Mixte' },
                ]}
              />
            )}
            {!hideSeanceDate && (
              <TextField label="Date" type="date" value={f.date} onChange={(e) => f.setDate(e.target.value)} />
            )}
            {!hideSeanceHeure && (
              <TextField label="Heure de l’assemblée" type="time" value={f.heure} onChange={(e) => f.setHeure(e.target.value)} />
            )}
            <div className="md:col-span-3">
              <TextField label="Lieu" value={f.lieu} onChange={(e) => f.setLieu(e.target.value)} placeholder="Ex : siège social" />
            </div>
            {sections.bureau && (
              <>
                <TextField label="Heure de clôture" type="time" value={f.heureCloture} onChange={(e) => f.setHeureCloture(e.target.value)} />
                <TextField label="Président — Nom" value={f.presidentNom} onChange={(e) => f.setPresidentNom(e.target.value)} />
                <ValeurProposee id="seance-president-qualite" label="Président — Qualité"
                  proposition={f.presidentQualitePropose} valeur={f.presidentQualite || undefined}
                  onChange={f.setPresidentQualite} />
                <Select
                  label="Secrétaire présent ?"
                  value={f.secretairePresent}
                  onChange={(e) => f.setSecretairePresent(e.target.value as 'oui' | 'non')}
                  options={[{ value: 'non', label: 'Non' }, { value: 'oui', label: 'Oui' }]}
                />
                {f.secretairePresent === 'oui' && (
                  <TextField label="Secrétaire — Nom" value={f.secretaireNom} onChange={(e) => f.setSecretaireNom(e.target.value)} />
                )}
              </>
            )}
          </div>
        </Card>
      )}

      {sections.convocation && (
        <Card title="Convocation">
          {sections.convocationCorpus && (
            <div className="mb-3">
              <TexteAide cle="seance-convocation-modele" titre="Quelle lettre de convocation ?">
                <p>
                  La lettre est celle du cabinet, choisie d’après la séance : assemblée ordinaire
                  (lettre de l’assemblée annuelle d’approbation des comptes), assemblée extraordinaire
                  ou mixte (lettre de l’assemblée extraordinaire), ou seconde convocation si le rang
                  est « Deuxième ». Une lettre est produite pour chaque associé de la liste.
                  Renseignez l’auteur, sa qualité et le mode d’envoi, puis les rubriques qui
                  s’affichent : les réponses oui / non choisissent les paragraphes de la lettre.
                  S’il manque une donnée, la génération vous la nomme.
                </p>
              </TexteAide>
            </div>
          )}
          <div className="grid gap-3 md:grid-cols-3">
            <ValeurProposee id="seance-convocation-auteur" label="Auteur de la convocation"
              proposition={auteurPropose} valeur={f.convAuteur || undefined} onChange={f.setConvAuteur} />
            {(sections.convocationCorpus || sections.feuilleCorpus) && (
              <ValeurProposee id="seance-convocation-auteur-qualite" label="Qualité de l’auteur"
                proposition="gérant" valeur={f.convCorpus.auteurQualite || undefined}
                onChange={(v) => f.setConvCorpus({ auteurQualite: v })} />
            )}
            {/* Date ET heure sont masquees ensemble : quand l'etape 1 porte la
                convocation, elle porte les deux. Les redemander ici obligeait a
                ressaisir la meme donnee a deux endroits. */}
            {!hideConvDate && (
              <>
                <TextField label="Date de convocation" type="date" value={f.convDate} onChange={(e) => f.setConvDate(e.target.value)} />
                <TextField label="Heure de convocation" type="time" value={f.convHeure} onChange={(e) => f.setConvHeure(e.target.value)} />
              </>
            )}
            <ValeurProposee id="seance-convocation-mode" label="Mode de convocation"
              proposition="lettre recommandée avec accusé de réception" valeur={f.convMode || undefined}
              onChange={f.setConvMode} options={MODES_CONVOCATION} />
            {sections.convocationCorpus && (
              <ValeurProposee id="seance-convocation-pouvoir" label="Formule de pouvoir jointe"
                proposition="oui" valeur={f.convCorpus.pouvoirJoint || undefined}
                onChange={(v) => f.setConvCorpus({ pouvoirJoint: v })} options={OUI_NON} />
            )}
            {sections.convocationCorpus && f.type === 'ordinaire' && f.convRang === 'première' && (
              <>
                <ValeurProposee id="seance-convocation-auteur-type" label="Convoquée par"
                  proposition="gérant" valeur={f.convCorpus.auteurType || undefined}
                  onChange={(v) => f.setConvCorpus({ auteurType: v })} options={AUTEURS_CONVOCATION} />
                {f.convCorpus.auteurType === 'mandataire de justice' && (
                  <TextField label="Date de l’ordonnance désignant le mandataire *" type="date"
                    value={f.convCorpus.ordonnanceDate}
                    onChange={(e) => f.setConvCorpus({ ordonnanceDate: e.target.value })}
                    hint="Imprimée dans la lettre : « désigné par ordonnance … en date du … »." />
                )}
                <ValeurProposee id="seance-communication-date" label="Date d’envoi des documents (art. 70)"
                  type="date" proposition={f.convDate} valeur={f.convCorpus.communicationDateEnvoi || undefined}
                  onChange={(v) => f.setConvCorpus({ communicationDateEnvoi: v })} />
              </>
            )}
            {sections.convocationRang && (
              <>
                <Select
                  label="Rang de convocation"
                  value={f.convRang}
                  onChange={(e) => f.setConvRang(e.target.value as 'première' | 'deuxième')}
                  options={[{ value: 'première', label: 'Première' }, { value: 'deuxième', label: 'Deuxième' }]}
                />
                {f.convRang === 'deuxième' && (
                  <TextField label="Date de la première assemblée" type="date" value={f.dateAgPremiere} onChange={(e) => f.setDateAgPremiere(e.target.value)} />
                )}
                <TextField label="Lieu de signature" value={f.lieuSignature} onChange={(e) => f.setLieuSignature(e.target.value)} placeholder="Ex : Casablanca" />
              </>
            )}
            {sections.irregularite && (
              <div className="md:col-span-3">
                <TextField
                  label="Nature de l’irrégularité"
                  value={f.irregulariteNature}
                  onChange={(e) => f.setIrregulariteNature(e.target.value)}
                  placeholder="Ex : délai non respecté, associé non convoqué…"
                  hint="Uniquement pour le PV d’irrégularité de convocation."
                />
              </div>
            )}
          </div>
        </Card>
      )}

      {sections.convocationCorpus && f.convRang === 'deuxième' && (
        <Card title="Première assemblée (seconde convocation)">
          <TexteAide cle="seance-seconde-convocation" titre="Seconde convocation : la première assemblée">
            <p>
              La lettre rappelle l’assemblée réunie sur première convocation, restée sans la majorité
              requise. Indiquez comment et quand elle a été convoquée, puis où et quand elle s’est
              tenue (sa date se saisit dans « Convocation »). Dans « Associés », la présence est celle
              de cette première assemblée ; dans « Résolutions », saisissez le résultat et le
              décompte des voix de chaque résolution votée. Sans réponse, la lettre n’est pas générée
              et la donnée manquante vous est indiquée.
            </p>
          </TexteAide>
          <div className="mt-3 grid gap-3 md:grid-cols-3">
            <Select label="Mode d’envoi de la première convocation *" value={f.convCorpus.modePremiere}
              onChange={(e) => f.setConvCorpus({ modePremiere: e.target.value })}
              options={[{ value: '', label: 'Choisir…' }, ...MODES_CONVOCATION]} />
            <TextField label="Date de la première convocation *" type="date" value={f.convCorpus.datePremiere}
              onChange={(e) => f.setConvCorpus({ datePremiere: e.target.value })} />
            <TextField label="Heure de la première assemblée *" type="time" value={f.convCorpus.heureAgPremiere}
              onChange={(e) => f.setConvCorpus({ heureAgPremiere: e.target.value })} />
            <div className="md:col-span-3">
              <TextField label="Lieu de la première assemblée *" value={f.convCorpus.lieuAgPremiere}
                onChange={(e) => f.setConvCorpus({ lieuAgPremiere: e.target.value })} placeholder="Ex : au siège social" />
            </div>
            <OuiNon label="Les statuts écartent-ils la règle de la seconde convocation ? *"
              value={f.convCorpus.clauseContraireStatuts}
              onChange={(v) => f.setConvCorpus({ clauseContraireStatuts: v })} />
            {f.convCorpus.clauseContraireStatuts === 'oui' && (
              <div className="md:col-span-2">
                <TextField label="Texte de la clause des statuts *" value={f.convCorpus.clauseContraireTexte}
                  onChange={(e) => f.setConvCorpus({ clauseContraireTexte: e.target.value })}
                  hint="Reproduit mot pour mot dans la lettre." />
              </div>
            )}
            <OuiNon label="Un associé ou gérant intéressé à une convention participe ? *"
              value={f.convCorpus.associeInteressePresent}
              onChange={(v) => f.setConvCorpus({ associeInteressePresent: v })} />
            <OuiNon label="Documents joints de nouveau à la lettre ? *"
              value={f.convCorpus.documentsJointsSeconde}
              onChange={(v) => f.setConvCorpus({ documentsJointsSeconde: v })} />
            <OuiNon label="Pouvoirs de la première assemblée valables ? *"
              value={f.convCorpus.pouvoirsPremiereValables}
              onChange={(v) => f.setConvCorpus({ pouvoirsPremiereValables: v })} />
          </div>
        </Card>
      )}

      {f.type === 'ordinaire'
        && ((sections.convocationCorpus && f.convRang === 'première') || sections.feuilleCorpus) && (
        <ComptesFields form={f} convocation={!!sections.convocationCorpus} />
      )}

      {sections.secondeAssemblee && (
        <Card title="Seconde assemblée et suite donnée">
          <div className="grid gap-3 md:grid-cols-4">
            <TextField label="Date (2e assemblée)" type="date" value={f.secondeDate} onChange={(e) => f.setSecondeDate(e.target.value)} />
            <TextField label="Heure (2e assemblée)" type="time" value={f.secondeHeure} onChange={(e) => f.setSecondeHeure(e.target.value)} />
            <div className="md:col-span-2">
              <TextField label="Lieu (2e assemblée)" value={f.secondeLieu} onChange={(e) => f.setSecondeLieu(e.target.value)} />
            </div>
            <div className="md:col-span-2">
              <Select
                label="Suite donnée"
                value={f.suiteAssemblee}
                onChange={(e) => f.setSuiteAssemblee(e.target.value as 'renvoi' | 'régularisation')}
                options={[
                  { value: 'renvoi', label: 'Renvoi (2e convocation)' },
                  { value: 'régularisation', label: 'Régularisation' },
                ]}
              />
            </div>
          </div>
        </Card>
      )}

      {sections.ordreDuJour && (
        lockOrdreDuJour ? (
          /* Ordre du jour DÉRIVÉ : lecture seule, piloté uniquement par la sélection
             des modifications (aucun ajout / retrait / édition possible ici). */
          <Card title="Ordre du jour">
            <p className="mb-2 text-xs text-fg-subtle">
              Découle automatiquement des <strong>modifications choisies</strong> (étape 1) —
              pour le changer, modifiez la sélection.
            </p>
            {f.ordreDuJour.filter((pt) => pt?.trim()).length === 0 ? (
              <p className="rounded-lg bg-bg-overlay p-3 text-xs text-fg-subtle">
                Aucune modification sélectionnée pour le moment.
              </p>
            ) : (
              <ol className="space-y-1.5" data-testid="seance-odj-readonly">
                {f.ordreDuJour
                  .filter((pt) => pt?.trim())
                  .map((pt, i) => (
                    <li
                      key={`${i}-${pt}`}
                      className="flex items-start gap-2 rounded-lg bg-bg-overlay px-3 py-2 text-sm text-fg"
                    >
                      <span className="flex h-5 w-5 flex-shrink-0 items-center justify-center rounded bg-info text-[10px] font-bold text-info-fg">
                        {i + 1}
                      </span>
                      <span className="flex-1">{pt}</span>
                    </li>
                  ))}
              </ol>
            )}
          </Card>
        ) : (
          <Card
            title="Ordre du jour"
            action={<SmallAddButton label="Ajouter un point" onClick={() => f.setOrdreDuJour((p) => [...p, ''])} />}
          >
            <div className="space-y-2">
              {f.ordreDuJour.map((pt, i) => (
                <div key={i} className="flex items-center gap-2">
                  <div className="flex-1">
                    <TextField
                      value={pt}
                      onChange={(e) => f.setOrdreDuJour((p) => p.map((x, j) => (j === i ? e.target.value : x)))}
                      placeholder={`Point ${i + 1}`}
                      // Le titre de bloc porte le sens a l'ecran ; hors contexte
                      // visuel, « Point 1 » seul ne dit pas de quoi il s'agit.
                      aria-label={`Point ${i + 1} de l’ordre du jour`}
                    />
                  </div>
                  <RemoveButton onClick={() => f.setOrdreDuJour((p) => p.filter((_, j) => j !== i))} disabled={f.ordreDuJour.length <= 1} />
                </div>
              ))}
            </div>
          </Card>
        )
      )}

      {sections.documentsJoints && (
        <Card
          title="Documents joints à la convocation"
          action={<SmallAddButton label="Ajouter un document" onClick={() => f.setDocumentsJoints((p) => [...p, ''])} />}
        >
          {sections.convocationCorpus && (
            <div className="mb-3">
              <TexteAide cle="seance-documents-communiques" titre="Quels documents ajouter ?">
                <p>
                  Assemblée ordinaire : la lettre cite déjà l’inventaire, les états de synthèse, le
                  rapport de gestion et le texte des résolutions ; n’ajoutez que les autres pièces.
                  Assemblée extraordinaire ou seconde convocation : indiquez au moins un document,
                  la lettre en dresse la liste. La formule de pouvoir se déclare dans « Convocation ».
                </p>
              </TexteAide>
            </div>
          )}
          <div className="space-y-2">
            {f.documentsJoints.map((d, i) => (
              <div key={i} className="flex items-center gap-2">
                <div className="flex-1">
                  <TextField
                    value={d}
                    onChange={(e) => f.setDocumentsJoints((p) => p.map((x, j) => (j === i ? e.target.value : x)))}
                    placeholder={`Document ${i + 1}`}
                    aria-label={`Document joint ${i + 1} à la convocation`}
                  />
                </div>
                <RemoveButton onClick={() => f.setDocumentsJoints((p) => p.filter((_, j) => j !== i))} disabled={f.documentsJoints.length <= 0} />
              </div>
            ))}
          </div>
        </Card>
      )}

      {sections.resolutions && (
        <Card
          title="Résolutions (facultatif)"
          action={<SmallAddButton label="Ajouter une résolution" onClick={() => f.setResolutions((p) => [...p, emptyResolution()])} />}
        >
          {sections.convocationCorpus && (
            <div className="mb-3">
              <TexteAide cle="seance-resolutions-convocation" titre="Résolutions et convocation">
                <p>
                  Assemblée ordinaire : le texte de chaque résolution proposée est annexé à la lettre
                  (au moins une). Seconde convocation : saisissez le résultat et le décompte des voix
                  de chaque résolution votée à la première assemblée. Assemblée extraordinaire : cette
                  rubrique n’est pas imprimée dans la lettre.
                </p>
              </TexteAide>
            </div>
          )}
          <div className="space-y-3">
            {f.resolutions.length === 0 && (
              <p className="text-xs text-fg-subtle">Aucune résolution : cliquez sur « Ajouter une résolution » s’il y a lieu.</p>
            )}
            {f.resolutions.map((r, i) => (
              <div key={i} className="rounded-lg border border-border bg-bg-overlay p-3">
                <div className="mb-2 flex items-center justify-between">
                  <span className="text-xs font-semibold text-fg-muted">Résolution {i + 1}</span>
                  <RemoveButton onClick={() => f.setResolutions((p) => p.filter((_, j) => j !== i))} />
                </div>
                <div className="grid gap-2 md:grid-cols-2">
                  <TextField label="Intitulé" value={r.intitule} onChange={(e) => updateRes(f, i, { intitule: e.target.value })} />
                  <TextField label="Résultat" value={r.resultat} onChange={(e) => updateRes(f, i, { resultat: e.target.value })} placeholder="adoptée / rejetée" />
                  <div className="md:col-span-2">
                    <TextField label="Texte" value={r.texte} onChange={(e) => updateRes(f, i, { texte: e.target.value })} />
                  </div>
                  <TextField label="Voix pour" type="number" value={r.voixPour} onChange={(e) => updateRes(f, i, { voixPour: e.target.value })} />
                  <TextField label="Voix contre" type="number" value={r.voixContre} onChange={(e) => updateRes(f, i, { voixContre: e.target.value })} />
                  <TextField label="Abstentions" type="number" value={r.abstentions} onChange={(e) => updateRes(f, i, { abstentions: e.target.value })} />
                </div>
              </div>
            ))}
          </div>
        </Card>
      )}

      {sections.vote && <VoteFields form={f} />}

      {sections.presence && (
        <Card
          title={f.isAU ? 'Associé unique et gérance' : 'Associés et présence (quorum)'}
          action={
            f.isAU ? undefined : (
              <SmallAddButton label="Ajouter un associé" onClick={() => f.setAssocies((p) => [...p, emptyAssocie()])} />
            )
          }
        >
          <div className="space-y-3">
            {f.associes.map((a, i) => (
              <div key={i} className="rounded-lg border border-border bg-bg-overlay p-3">
                <div className="mb-2 flex items-center justify-between">
                  <span className="text-xs font-semibold text-fg-muted">
                    {f.isAU ? 'Associé unique' : `Associé ${i + 1}`}
                  </span>
                  {!f.isAU && (
                    <RemoveButton onClick={() => f.setAssocies((p) => p.filter((_, j) => j !== i))} disabled={f.associes.length <= 1} />
                  )}
                </div>
                <AssocieFields
                  value={a}
                  showVoix={!!sections.voix}
                  showEmail={!!sections.convocationCorpus && f.convMode === 'voie électronique'}
                  showPouvoirDate={!!sections.feuilleCorpus}
                  showPresence={!(sections.convocationCorpus && f.convRang === 'première')}
                  onChange={(next) => f.setAssocies((p) => p.map((x, j) => (j === i ? next : x)))}
                />
              </div>
            ))}
          </div>

          {/* Gérance (utile pour la signature convocation/feuille + PV AU). */}
          <div className="mt-4 flex items-center justify-between">
            <h5 className="text-xs font-semibold text-fg-muted">Gérance</h5>
            <SmallAddButton label="Ajouter un gérant" onClick={() => f.setGerants((p) => [...p, emptyGerant()])} />
          </div>
          <div className="mt-2 space-y-2">
            {f.gerants.map((g, i) => (
              <div key={i} className="grid items-end gap-2 md:grid-cols-[6rem_1fr_1fr_auto]">
                <TextField label={i === 0 ? 'Civilité' : undefined} value={g.civilite} onChange={(e) => updateGer(f, i, { civilite: e.target.value })} />
                <TextField label={i === 0 ? 'Prénom' : undefined} value={g.prenom} onChange={(e) => updateGer(f, i, { prenom: e.target.value })} />
                <TextField label={i === 0 ? 'Nom' : undefined} value={g.nom} onChange={(e) => updateGer(f, i, { nom: e.target.value })} />
                <RemoveButton onClick={() => f.setGerants((p) => p.filter((_, j) => j !== i))} disabled={f.gerants.length <= 1} />
              </div>
            ))}
          </div>
        </Card>
      )}
    </div>
  );
}

function updateRes(f: SeanceFormApi, i: number, patch: Partial<ResolutionInput>) {
  f.setResolutions((p) => p.map((x, j) => (j === i ? { ...x, ...patch } : x)));
}
function updateGer(f: SeanceFormApi, i: number, patch: Partial<GerantInput>) {
  f.setGerants((p) => p.map((x, j) => (j === i ? { ...x, ...patch } : x)));
}

// --------------------------------------------------------------------------
// Sous-composants
// --------------------------------------------------------------------------
/**
 * L3 (D6) : quorum et vote des resolutions, SAISIS. Avant, "quorum atteint", "adoptee",
 * "a l'unanimite" et "voix pour = parts presentes" s'imprimaient par defaut. Absents,
 * la generation nomme la donnee manquante.
 */
function VoteFields({ form: f }: { form: SeanceFormApi }) {
  const set = (patch: Partial<VoteInput>) => f.setVote((v) => ({ ...v, ...patch }));
  return (
    <Card title="Quorum et vote des résolutions">
      <TexteAide cle="seance-vote" titre="Quorum et vote : à saisir">
        <p>
          Indiquez si le quorum est atteint et le résultat du vote : la plateforme ne suppose
          jamais une adoption ni une unanimité. Ce vote s’applique aux résolutions qui ne
          portent pas leur propre résultat. Sans ces données, le procès-verbal n’est pas
          généré et la donnée manquante vous est indiquée.
        </p>
      </TexteAide>
      <div className="mt-3 grid gap-3 md:grid-cols-3">
        {!f.isAU && (
        <Select
          label="Quorum atteint *"
          value={f.quorumAtteint}
          onChange={(e) => f.setQuorumAtteint(e.target.value as '' | 'oui' | 'non')}
          hint={f.quorumAtteint ? undefined : 'À indiquer d’après la feuille de présence.'}
          options={[
            { value: '', label: 'Choisir…' },
            { value: 'oui', label: 'Oui' },
            { value: 'non', label: 'Non' },
          ]}
        />
        )}
        <Select
          label="Résultat du vote *"
          value={f.vote.resultat}
          onChange={(e) => set({ resultat: e.target.value })}
          options={[
            { value: '', label: 'Choisir…' },
            { value: 'adoptée', label: 'Adoptée' },
            { value: 'rejetée', label: 'Rejetée' },
          ]}
        />
        <div>
          <TextField
            label="Majorité *"
            value={f.vote.majorite}
            onChange={(e) => set({ majorite: e.target.value })}
            placeholder="Ex. : à l’unanimité"
          />
          <p className="mt-1 flex items-center gap-1 text-[11px] text-fg-subtle">
            Imprimée après « est adoptée »
            <InfoBulle
              libelle="Où la majorité est-elle imprimée ?"
              texte="Les procès-verbaux de modification et de dissolution écrivent « Cette résolution est adoptée … » suivi de la majorité saisie ici (par exemple « à l’unanimité »)."
            />
          </p>
        </div>
        <TextField label="Voix pour *" type="number" value={f.vote.voixPour} onChange={(e) => set({ voixPour: e.target.value })} />
        <TextField label="Voix contre *" type="number" value={f.vote.voixContre} onChange={(e) => set({ voixContre: e.target.value })} />
        <TextField label="Abstentions *" type="number" value={f.vote.abstentions} onChange={(e) => set({ abstentions: e.target.value })} />
      </div>
    </Card>
  );
}

const OUI_NON = [
  { value: 'oui', label: 'Oui' },
  { value: 'non', label: 'Non' },
];

/** Libelles exacts testes par la convocation AGO du corpus ($CONVOCATION_AUTEUR_TYPE). */
const AUTEURS_CONVOCATION = [
  { value: 'gérant', label: 'Le gérant' },
  { value: 'commissaire aux comptes', label: 'Le commissaire aux comptes' },
  { value: 'mandataire de justice', label: 'Un mandataire de justice' },
];

/** L4d (E11b) : reponse oui / non a saisir ; vide, la generation la nomme. */
function OuiNon({ label, value, onChange }: { label: string; value: string; onChange: (v: string) => void }) {
  return (
    <Select
      label={label}
      value={value}
      onChange={(e) => onChange(e.target.value)}
      hint={value ? undefined : 'À indiquer : rien n’est supposé.'}
      options={[{ value: '', label: 'Choisir…' }, ...OUI_NON]}
    />
  );
}

/**
 * L4d (E11b) : assemblee ordinaire = assemblee annuelle d'approbation des comptes (modeles
 * du corpus). Chaque reponse choisit une rubrique imprimee ; rien n'est suppose.
 */
function ComptesFields({ form: f, convocation }: { form: SeanceFormApi; convocation: boolean }) {
  const c = f.comptes;
  return (
    <Card title="Exercice et comptes (assemblée annuelle)">
      <TexteAide cle="seance-comptes" titre="Assemblée ordinaire : les comptes de l’exercice">
        <p>
          Pour une assemblée ordinaire, le modèle du cabinet est celui de l’assemblée annuelle
          d’approbation des comptes. Indiquez l’exercice : la date limite de six mois est calculée
          depuis la date de clôture. Répondez à chaque question par oui ou non : chaque réponse
          ajoute ou retire une rubrique de l’ordre du jour ou un paragraphe. L’ordre du jour saisi
          plus bas ne doit contenir que les points propres au dossier, les rubriques annuelles étant
          déjà imprimées.
        </p>
      </TexteAide>
      <div className="mt-3 grid gap-3 md:grid-cols-3">
        {convocation && (
          <TextField label="Ouverture de l’exercice *" type="date" value={c.exerciceDebut}
            onChange={(e) => f.setComptes({ exerciceDebut: e.target.value })} />
        )}
        <TextField label="Clôture de l’exercice *" type="date" value={c.exerciceClos}
          onChange={(e) => f.setComptes({ exerciceClos: e.target.value })} />
        <OuiNon label="Commissaire aux comptes en fonction ? *" value={c.cacDesigne}
          onChange={(v) => f.setComptes({ cacDesigne: v })} />
        {c.cacDesigne === 'oui' && (
          <TextField label="Commissaire aux comptes — Nom *" value={c.cacNom}
            onChange={(e) => f.setComptes({ cacNom: e.target.value })} />
        )}
        {!convocation && c.cacDesigne === 'oui' && (
          <Select label="Commissaire aux comptes — Présence *" value={c.cacPresence}
            onChange={(e) => f.setComptes({ cacPresence: e.target.value })}
            options={[
              { value: '', label: 'Choisir…' },
              { value: 'assiste à la réunion', label: 'Assiste à la réunion' },
              { value: 'est absent et excusé', label: 'Est absent et excusé' },
            ]} />
        )}
        <OuiNon label="Conventions de l’article 64 sur l’exercice ? *" value={c.conventionsReglementees}
          onChange={(v) => f.setComptes({ conventionsReglementees: v })} />
        {convocation && (
          <>
            <OuiNon label="Dotation à la réserve légale due ? *" value={c.reserveLegale}
              onChange={(v) => f.setComptes({ reserveLegale: v })} />
            {c.reserveLegale === 'oui' && (
              <>
                <TextField label="Quotité du bénéfice affectée *" value={c.reserveLegaleTaux}
                  onChange={(e) => f.setComptes({ reserveLegaleTaux: e.target.value })} placeholder="Ex : 5 %" />
                <TextField label="Plafond de la réserve légale *" value={c.reserveLegalePlafond}
                  onChange={(e) => f.setComptes({ reserveLegalePlafond: e.target.value })}
                  placeholder="Ex : le dixième du capital social" />
              </>
            )}
            <OuiNon label="Distribution de dividendes proposée ? *" value={c.dividendes}
              onChange={(v) => f.setComptes({ dividendes: v })} />
            <OuiNon label="Distribution de réserves proposée ? *" value={c.reserves}
              onChange={(v) => f.setComptes({ reserves: v })} />
            <OuiNon label="La gérance associée vote-t-elle le quitus ? *" value={c.quitusGerantVote}
              onChange={(v) => f.setComptes({ quitusGerantVote: v })} />
          </>
        )}
      </div>
    </Card>
  );
}

function Card({ title, action, children }: { title: string; action?: React.ReactNode; children: React.ReactNode }) {
  return (
    <div className="rounded-lg border border-border bg-bg-raised p-4">
      <div className="mb-3 flex items-center justify-between">
        <h4 className="text-sm font-semibold text-fg">{title}</h4>
        {action}
      </div>
      {children}
    </div>
  );
}

export function AssocieFields({
  value,
  onChange,
  showVoix,
  showEmail,
  showPouvoirDate,
  showPresence = true,
}: {
  value: AssocieInput;
  onChange: (next: AssocieInput) => void;
  showVoix?: boolean;
  /** L4d (E11b) : adresse de la lettre de convocation envoyee par voie electronique. */
  showEmail?: boolean;
  /** L4d (E11b) : date du pouvoir de l'associe represente (feuille de presence). */
  showPouvoirDate?: boolean;
  /** L4d (E11b) : sans objet pour une premiere convocation (avant l'assemblee). */
  showPresence?: boolean;
}) {
  const isMorale = value.typePersonne === 'MORALE';
  return (
    <div className="grid gap-2 md:grid-cols-3">
      <Select
        label="Type"
        value={value.typePersonne}
        onChange={(e) => onChange({ ...value, typePersonne: e.target.value as 'PHYSIQUE' | 'MORALE' })}
        options={[
          { value: 'PHYSIQUE', label: 'Personne physique' },
          { value: 'MORALE', label: 'Personne morale' },
        ]}
      />
      {isMorale ? (
        <div className="md:col-span-2">
          <TextField label="Dénomination" value={value.denomination} onChange={(e) => onChange({ ...value, denomination: e.target.value })} />
        </div>
      ) : (
        <>
          <TextField label="Prénom" value={value.prenom} onChange={(e) => onChange({ ...value, prenom: e.target.value })} />
          <TextField label="Nom" value={value.nom} onChange={(e) => onChange({ ...value, nom: e.target.value })} />
        </>
      )}
      <div className="md:col-span-3">
        <TextField label="Adresse" value={value.adresse} onChange={(e) => onChange({ ...value, adresse: e.target.value })} />
      </div>
      {/* Fix M6 — n° de CIN : repris de la BD quand il y est, saisissable sinon.
          Il alimente la comparution des PV d'associé unique (« titulaire de la
          CIN n° … »), qui sortait vide faute de champ porteur. */}
      {!isMorale && (
        <TextField
          label="N° de CIN"
          value={value.pieceNumero ?? ''}
          onChange={(e) => onChange({ ...value, pieceNumero: e.target.value })}
          hint="Repris de la fiche société ; complétez s’il manque."
        />
      )}
      {!isMorale && (
        <ValeurProposee
          id={`associe-nationalite-${value.nom || value.prenom || 'nouveau'}`}
          label="Nationalité"
          proposition="marocaine"
          valeur={value.nationalite || undefined}
          onChange={(v) => onChange({ ...value, nationalite: v })}
        />
      )}
      {!isMorale && (
        <TypePieceProposee
          id={`associe-piece-type-${value.nom || value.prenom || 'nouveau'}`}
          valeur={value.pieceType}
          onChange={(v) => onChange({ ...value, pieceType: v })}
        />
      )}
      <TextField label="Nombre de parts" type="number" value={value.nombreParts} onChange={(e) => onChange({ ...value, nombreParts: e.target.value })} />
      {showVoix && (
        <TextField label="Nombre de voix" type="number" value={value.nombreVoix} onChange={(e) => onChange({ ...value, nombreVoix: e.target.value })} placeholder="Égal au nombre de parts" />
      )}
      {showEmail && (
        <TextField label="Adresse électronique *" type="email" value={value.adresseElectronique ?? ''}
          onChange={(e) => onChange({ ...value, adresseElectronique: e.target.value })}
          hint="La lettre est envoyée à cette adresse (envoi par voie électronique)." />
      )}
      {showPresence && (
      <Select
        label="Présence *"
        value={value.presence}
        onChange={(e) => onChange({ ...value, presence: e.target.value as AssocieInput['presence'] })}
        hint={value.presence ? undefined : 'À indiquer : la présence n’est jamais supposée.'}
        options={[
          { value: '', label: 'Choisir…' },
          { value: 'présent', label: 'Présent' },
          { value: 'représenté', label: 'Représenté' },
          { value: 'absent', label: 'Absent' },
        ]}
      />
      )}
      {showPresence && value.presence === 'représenté' && (
        <TextField label="Mandataire — Nom" value={value.mandataireNom} onChange={(e) => onChange({ ...value, mandataireNom: e.target.value })} />
      )}
      {showPresence && showPouvoirDate && value.presence === 'représenté' && (
        <TextField label="Date du pouvoir *" type="date" value={value.pouvoirDate ?? ''}
          onChange={(e) => onChange({ ...value, pouvoirDate: e.target.value })}
          hint="Imprimée sur la feuille : « en vertu d’un pouvoir en date du … »." />
      )}
      {/* Désignation #5 : cocher un associé comme gérant depuis la liste des associés. */}
      {value.typePersonne === 'PHYSIQUE' && (
        <Case
          containerClassName="self-end pb-2"
          label="Également gérant"
          checked={!!value.estGerant}
          onChange={(e) => onChange({ ...value, estGerant: e.target.checked })}
        />
      )}
    </div>
  );
}

export function SmallAddButton({ label, onClick }: { label: string; onClick: () => void }) {
  return (
    <Button type="button" variant="secondary" size="sm" icon={<Plus />} onClick={onClick}>
      {label}
    </Button>
  );
}

export function RemoveButton({ onClick, disabled }: { onClick: () => void; disabled?: boolean }) {
  return (
    <Button
      type="button"
      variant="secondary"
      iconOnly
      icon={<Trash2 />}
      onClick={onClick}
      disabled={disabled}
      className="shrink-0 text-fg-subtle hover:border-danger hover:text-danger"
      aria-label="Supprimer"
    />
  );
}
