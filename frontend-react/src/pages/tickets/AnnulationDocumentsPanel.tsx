import { useCallback, useEffect, useMemo, useState } from 'react';
import { Download, FileText, Plus, Save, Trash2, Upload } from 'lucide-react';
import type { Ticket } from '../../types/ticket';
import { Button } from '../../components/ui/Button';
import { TextField } from '../../components/ui/TextField';
import { Select } from '../../components/ui/Select';
import { InfoBulle, TexteAide } from '../../components/ui/Aide';
import { RetourGeneration } from '../../components/workflow/RetourGeneration';
import { triggerBrowserDownload } from '../../components/workflow/WorkflowDocumentBlock';
import {
  generateDocument,
  GenerationRefuseeError,
  type DonneeManquante,
} from '../../services/workflowDocumentService';
import { annulationService, type Ligne } from '../../services/annulation.service';
import { dataroomService } from '../../services/dataroom.service';
import { extractError } from '../../lib/api';

/**
 * Lot L4a (E6, RG-TKT-04) : les DOCUMENTS D'ANNULATION, communs a tous les parcours.
 *
 * La plateforme sait deja le statut atteint, la date et le motif de l'annulation, et les
 * depots coches au suivi des demarches ; l'employe complete ce qu'elle ne peut pas savoir
 * (origine, etat de la decision sociale, adresse de chaque administration...). Puis il
 * genere la note d'annulation, une lettre de retrait par demarche interrompue et le
 * bordereau de restitution des pieces, et les depose au Dossier juridique.
 *
 * Valeurs des listes : celles des modeles du cabinet, mot pour mot (elles pilotent les
 * cases a cocher et les paragraphes conditionnels). Aucune valeur n'est pre-remplie.
 */

const WORKFLOW = 'ANNULATION';

const ORIGINES = ['Décision du client', 'Décision du cabinet', "Refus de l'administration", 'Absence de suite du client'];
const ETATS_DECISION = [
  'aucune décision adoptée',
  'décision adoptée, à rapporter',
  'décision adoptée, maintenue sans exécution',
  'décision adoptée, sort à arrêter',
];
const ORGANES = ['associé unique', 'assemblée des associés', 'gérance', 'liquidateur', 'organe de la société étrangère'];
const OBJETS_RETRAIT = [
  "Retrait d'une demande en cours",
  "Régularisation d'un dossier déposé",
  'Restitution de pièces déposées',
];
const SIGNATAIRES = ['mandataire aux formalités', 'liquidateur', 'représentant de la succursale', 'gérance'];
const OUI_NON = ['oui', 'non'];

function options(valeurs: string[]) {
  return [{ value: '', label: 'Choisir…' }, ...valeurs.map((v) => ({ value: v, label: v }))];
}

/** Champs simples de l'ecran (les autres donnees viennent du ticket ou du dossier). */
const CHAMPS_SAISIS = [
  'ANNULATION_ORIGINE', 'ANNULATION_ADMINISTRATION', 'ANNULATION_DECISION_REFERENCE', 'ANNULATION_OBSERVATIONS',
  'LIEU_SIGNATURE', 'DATE_SIGNATURE', 'ANNULATION_OPERATION_LIBELLE', 'ANNULATION_DECISION_SOCIALE_ETAT',
  'ANNULATION_DECISION_ORGANE', 'ASSEMBLEE_DATE', 'ANNULATION_DECISION_RAPPORT_DATE', 'ANNULATION_FORMALITES_EFFET',
  'ENREGISTREMENT_ACTE_DATE', 'ENREGISTREMENT_ACTE_REFERENCE', 'ANNULATION_FORMALITES_AUTRES',
  'CLIENT_SIGNATAIRE_NOM', 'CLIENT_SIGNATAIRE_QUALITE', 'PIECES_MANQUANTES', 'RETRAIT_SIGNATAIRE_TYPE',
];

function texte(v: unknown): string {
  return v === null || v === undefined ? '' : String(v);
}

function lignes(v: unknown): Ligne[] {
  if (!Array.isArray(v)) return [];
  return v.map((it) => {
    const l: Ligne = {};
    Object.entries((it ?? {}) as Record<string, unknown>).forEach(([k, x]) => {
      l[k] = texte(x);
    });
    return l;
  });
}

/** Les cles techniques (prefixe "_") ne sont pas des donnees : elles ne se renvoient pas. */
function sansTechnique(ls: Ligne[]): Ligne[] {
  return ls.map((l) => Object.fromEntries(Object.entries(l).filter(([k, v]) => !k.startsWith('_') && v !== '')));
}

interface Doc {
  cle: string;
  code: string;
  libelle: string;
  type: string;
  retraitIndex?: number;
}

interface EtatDoc {
  enCours?: boolean;
  erreur?: string | null;
  refus?: DonneeManquante[];
  blob?: Blob;
  filename?: string;
  depose?: boolean;
}

export function AnnulationDocumentsPanel({ ticket, canAct }: { ticket: Ticket; canAct: boolean }) {
  const creation = ticket.type === 'CREATION';
  const suffixe = creation ? '' : '_SOCIETE_EXISTANTE';
  const [vars, setVars] = useState<Record<string, string>>({});
  const [demarches, setDemarches] = useState<Ligne[]>([]);
  const [pieces, setPieces] = useState<Ligne[]>([]);
  const [chargement, setChargement] = useState(true);
  const [erreur, setErreur] = useState<string | null>(null);
  const [enregistre, setEnregistre] = useState<string | null>(null);
  const [docs, setDocs] = useState<Record<string, EtatDoc>>({});
  const [complements, setComplements] = useState<Record<string, string>>({});

  const appliquer = useCallback((variables: Record<string, unknown>) => {
    const v: Record<string, string> = {};
    CHAMPS_SAISIS.forEach((k) => {
      v[k] = texte(variables[k]);
    });
    setVars(v);
    setDemarches(lignes(variables.DEMARCHES_INTERROMPUES));
    setPieces(lignes(variables.PIECES_REMISES));
  }, []);

  useEffect(() => {
    let actif = true;
    annulationService
      .lire(ticket.id)
      .then((c) => actif && appliquer(c.variables))
      .catch((err) => actif && setErreur(extractError(err).message))
      .finally(() => actif && setChargement(false));
    return () => {
      actif = false;
    };
  }, [ticket.id, appliquer]);

  const documents: Doc[] = useMemo(() => {
    const l: Doc[] = [{ cle: 'note', code: `NOTE_ANNULATION_DOSSIER${suffixe}`, libelle: "Note d’annulation", type: 'NOTE_ANNULATION' }];
    demarches.forEach((d, i) => {
      l.push({
        cle: `lettre-${i}`,
        code: `LETTRE_RETRAIT_DEPOT${suffixe}`,
        libelle: `Lettre de retrait — ${d.DEMARCHE_ADMINISTRATION || d.DEMARCHE_DESIGNATION || `démarche ${i + 1}`}`,
        type: 'LETTRE_RETRAIT_DEPOT',
        retraitIndex: i,
      });
    });
    l.push({ cle: 'bordereau', code: `BORDEREAU_REMISE_DOSSIER${suffixe}`, libelle: 'Bordereau de restitution des pièces', type: 'BORDEREAU_REMISE' });
    return l;
  }, [demarches, suffixe]);

  const set = (k: string, v: string) => {
    setVars((p) => ({ ...p, [k]: v }));
    setEnregistre(null);
  };
  const setLigne = (liste: 'demarches' | 'pieces', i: number, k: string, v: string) => {
    const maj = (p: Ligne[]) => p.map((l, j) => (j === i ? { ...l, [k]: v } : l));
    if (liste === 'demarches') setDemarches(maj);
    else setPieces(maj);
    setEnregistre(null);
  };

  async function enregistrer(supplement: Record<string, string> = {}) {
    setErreur(null);
    const variables = Object.fromEntries(
      Object.entries({ ...vars, ...supplement }).filter(([, v]) => v.trim() !== ''),
    );
    try {
      const c = await annulationService.enregistrer(ticket.id, {
        variables,
        demarches: sansTechnique(demarches),
        pieces: sansTechnique(pieces),
      });
      appliquer(c.variables);
      setEnregistre('Saisies enregistrées.');
      return true;
    } catch (err) {
      setErreur(extractError(err).message);
      return false;
    }
  }

  async function generer(doc: Doc) {
    setDocs((p) => ({ ...p, [doc.cle]: { enCours: true } }));
    try {
      const r = await generateDocument(WORKFLOW, doc.code, {
        ticketId: ticket.id,
        ...(doc.retraitIndex !== undefined ? { retraitIndex: doc.retraitIndex } : {}),
      });
      setDocs((p) => ({ ...p, [doc.cle]: { blob: r.blob, filename: r.filename } }));
    } catch (err) {
      const refus = err instanceof GenerationRefuseeError ? err.donneesManquantes : [];
      setDocs((p) => ({ ...p, [doc.cle]: { erreur: (err as Error).message, refus } }));
    }
  }

  async function deposer(doc: Doc) {
    const st = docs[doc.cle];
    if (!st?.blob || !ticket.dossierId) return;
    try {
      await dataroomService.depositGeneratedDoc(ticket.dossierId, st.blob, {
        documentType: doc.type,
        title: doc.libelle,
        filename: st.filename ?? `${doc.code}.docx`,
        ticketId: ticket.id,
      });
      setDocs((p) => ({ ...p, [doc.cle]: { ...st, depose: true } }));
    } catch (err) {
      setDocs((p) => ({ ...p, [doc.cle]: { ...st, erreur: `Dépôt au Dossier juridique : ${extractError(err).message}` } }));
    }
  }

  /** Donnees nommees par un refus et absentes de l'ecran : saisies ici, puis regeneration. */
  async function completer(doc: Doc, refus: DonneeManquante[]) {
    const supplement: Record<string, string> = {};
    refus.forEach((d) => {
      const v = complements[d.variable]?.trim();
      if (v) supplement[d.variable] = v;
    });
    if (await enregistrer(supplement)) await generer(doc);
  }

  if (chargement) return <p className="text-sm text-fg-muted">Chargement des documents d’annulation…</p>;

  const v = vars;
  const decisionAdoptee = v.ANNULATION_DECISION_SOCIALE_ETAT !== '' && v.ANNULATION_DECISION_SOCIALE_ETAT !== ETATS_DECISION[0];
  const lecture = !canAct;

  return (
    <section data-testid="annulation-documents" className="space-y-4 rounded-xl border border-border p-4">
      <h3 className="flex items-center gap-1 text-sm font-semibold text-fg">
        <FileText className="h-4 w-4" /> Documents d'annulation
        <InfoBulle
          libelle="À quoi servent ces documents ?"
          texte="La note d’annulation constate l’abandon de l’opération (motif, statut atteint, démarches interrompues). Chaque dépôt fait auprès d’une administration appelle une lettre de retrait. Le bordereau constate la restitution des pièces au client."
        />
      </h3>
      <TexteAide cle="annulation-documents" titre="Comment préparer les documents d’annulation">
        Le statut atteint, la date et le motif de l'annulation viennent du ticket ; les dépôts cochés au suivi des
        démarches sont repris. Complétez ce que la plateforme ne peut pas savoir, enregistrez, puis générez chaque
        document. Si une donnée manque, la génération la nomme : saisissez-la et relancez. Déposez enfin les
        documents au Dossier juridique.
      </TexteAide>

      {erreur && <p role="alert" className="text-sm text-danger">{erreur}</p>}

      <fieldset disabled={lecture} className="grid gap-3 sm:grid-cols-2">
        <Select label="Origine de l’annulation" value={v.ANNULATION_ORIGINE} options={options(ORIGINES)}
          onChange={(e) => set('ANNULATION_ORIGINE', e.target.value)}
          hint="Case cochée dans la note d’annulation." />
        {v.ANNULATION_ORIGINE === "Refus de l'administration" && (
          <>
            <TextField label="Administration qui a refusé" value={v.ANNULATION_ADMINISTRATION}
              onChange={(e) => set('ANNULATION_ADMINISTRATION', e.target.value)} />
            <TextField label="Référence de la décision de refus" value={v.ANNULATION_DECISION_REFERENCE}
              onChange={(e) => set('ANNULATION_DECISION_REFERENCE', e.target.value)} />
          </>
        )}
        {!creation && (
          <>
            <TextField label="Opération abandonnée" value={v.ANNULATION_OPERATION_LIBELLE}
              onChange={(e) => set('ANNULATION_OPERATION_LIBELLE', e.target.value)}
              hint="Ex. : transfert du siège social. Imprimée dans la note, les lettres et le bordereau." />
            <Select label="État de la décision sociale" value={v.ANNULATION_DECISION_SOCIALE_ETAT}
              options={options(ETATS_DECISION)} onChange={(e) => set('ANNULATION_DECISION_SOCIALE_ETAT', e.target.value)}
              hint="L’annulation du ticket ne rapporte pas une décision déjà adoptée : la note le précise." />
            {decisionAdoptee && (
              <>
                <Select label="Organe qui a adopté la décision" value={v.ANNULATION_DECISION_ORGANE}
                  options={options(ORGANES)} onChange={(e) => set('ANNULATION_DECISION_ORGANE', e.target.value)} />
                <TextField type="date" label="Date de la décision" value={v.ASSEMBLEE_DATE}
                  onChange={(e) => set('ASSEMBLEE_DATE', e.target.value)} />
              </>
            )}
            {v.ANNULATION_DECISION_SOCIALE_ETAT === ETATS_DECISION[1] && (
              <TextField type="date" label="Date de la décision qui la rapporte (si adoptée)"
                value={v.ANNULATION_DECISION_RAPPORT_DATE}
                onChange={(e) => set('ANNULATION_DECISION_RAPPORT_DATE', e.target.value)}
                hint="Laissez vide si la décision n’est pas encore rapportée : la note le dira." />
            )}
            <Select label="Des formalités ont-elles déjà produit effet ?" value={v.ANNULATION_FORMALITES_EFFET}
              options={options(OUI_NON)} onChange={(e) => set('ANNULATION_FORMALITES_EFFET', e.target.value)}
              hint="Enregistrement, dépôt au greffe, inscription, annonce parue : elles ne se retirent pas par lettre." />
            {v.ANNULATION_FORMALITES_EFFET === 'oui' && (
              <>
                <TextField type="date" label="Date d’enregistrement de l’acte (s’il a été enregistré)"
                  value={v.ENREGISTREMENT_ACTE_DATE} onChange={(e) => set('ENREGISTREMENT_ACTE_DATE', e.target.value)} />
                <TextField label="Référence d’enregistrement" value={v.ENREGISTREMENT_ACTE_REFERENCE}
                  onChange={(e) => set('ENREGISTREMENT_ACTE_REFERENCE', e.target.value)} />
                <TextField label="Autres formalités accomplies" value={v.ANNULATION_FORMALITES_AUTRES}
                  onChange={(e) => set('ANNULATION_FORMALITES_AUTRES', e.target.value)} />
              </>
            )}
          </>
        )}
        <TextField label="Observations (facultatif)" value={v.ANNULATION_OBSERVATIONS}
          onChange={(e) => set('ANNULATION_OBSERVATIONS', e.target.value)} />
        <TextField label="Lieu de signature" value={v.LIEU_SIGNATURE} onChange={(e) => set('LIEU_SIGNATURE', e.target.value)} />
        <TextField type="date" label="Date de signature" value={v.DATE_SIGNATURE}
          onChange={(e) => set('DATE_SIGNATURE', e.target.value)} />
        <TextField label="Client signataire du bordereau" value={v.CLIENT_SIGNATAIRE_NOM}
          onChange={(e) => set('CLIENT_SIGNATAIRE_NOM', e.target.value)} />
        {!creation && (
          <TextField label="Qualité du client signataire" value={v.CLIENT_SIGNATAIRE_QUALITE}
            onChange={(e) => set('CLIENT_SIGNATAIRE_QUALITE', e.target.value)} hint="Ex. : gérant, associé unique." />
        )}
        <TextField label="Pièces non restituées (facultatif)" value={v.PIECES_MANQUANTES}
          onChange={(e) => set('PIECES_MANQUANTES', e.target.value)}
          hint="Pièces encore détenues par une administration, s’il y en a." />
        {!creation && demarches.length > 0 && (
          <Select label="Signataire des lettres de retrait" value={v.RETRAIT_SIGNATAIRE_TYPE}
            options={options(SIGNATAIRES)} onChange={(e) => set('RETRAIT_SIGNATAIRE_TYPE', e.target.value)} />
        )}
      </fieldset>

      <div className="space-y-2">
        <p className="flex items-center gap-1 text-sm font-medium text-fg">
          Démarches interrompues ({demarches.length})
          <InfoBulle
            libelle="D’où viennent ces démarches ?"
            texte="Les dépôts cochés au suivi des démarches, sans retrait coché, sont repris d’office et ne se retirent pas d’ici. Ajoutez une démarche faite hors du suivi si besoin. Chacune donne une lettre de retrait."
          />
        </p>
        {demarches.length === 0 && (
          <p className="text-xs text-fg-subtle">Aucun dépôt en cours : la note le dira et aucune lettre de retrait n’est nécessaire.</p>
        )}
        {demarches.map((d, i) => (
          <fieldset key={i} disabled={lecture} data-testid={`demarche-${i}`}
            className="grid gap-2 rounded-lg border border-border p-3 sm:grid-cols-2">
            <TextField label="Démarche" value={d.DEMARCHE_DESIGNATION ?? ''} readOnly={d._suivie === 'oui'}
              onChange={(e) => setLigne('demarches', i, 'DEMARCHE_DESIGNATION', e.target.value)} />
            <TextField label="Administration" value={d.DEMARCHE_ADMINISTRATION ?? ''} readOnly={d._suivie === 'oui'}
              onChange={(e) => setLigne('demarches', i, 'DEMARCHE_ADMINISTRATION', e.target.value)} />
            <TextField type="date" label="Date du dépôt" value={d.DEMARCHE_DATE_DEPOT ?? ''}
              onChange={(e) => setLigne('demarches', i, 'DEMARCHE_DATE_DEPOT', e.target.value)} />
            <TextField label="Référence du dépôt" value={d.DEMARCHE_REFERENCE ?? ''}
              onChange={(e) => setLigne('demarches', i, 'DEMARCHE_REFERENCE', e.target.value)} />
            <TextField label="Suite à donner" value={d.DEMARCHE_SUITE ?? ''}
              onChange={(e) => setLigne('demarches', i, 'DEMARCHE_SUITE', e.target.value)} hint="Ex. : lettre de retrait." />
            <TextField label="Adresse de l’administration" value={d.RETRAIT_ADMINISTRATION_ADRESSE ?? ''}
              onChange={(e) => setLigne('demarches', i, 'RETRAIT_ADMINISTRATION_ADRESSE', e.target.value)} />
            <Select label="Objet de la lettre" value={d.RETRAIT_OBJET ?? ''} options={options(OBJETS_RETRAIT)}
              onChange={(e) => setLigne('demarches', i, 'RETRAIT_OBJET', e.target.value)} />
            <TextField label="Objet de la demande déposée" value={d.RETRAIT_DEMANDE_OBJET ?? ''}
              onChange={(e) => setLigne('demarches', i, 'RETRAIT_DEMANDE_OBJET', e.target.value)} />
            {d.RETRAIT_OBJET === OBJETS_RETRAIT[1] && (
              <TextField label="Régularisation demandée" value={d.RETRAIT_REGULARISATION_OBJET ?? ''}
                onChange={(e) => setLigne('demarches', i, 'RETRAIT_REGULARISATION_OBJET', e.target.value)} />
            )}
            {d.RETRAIT_OBJET === OBJETS_RETRAIT[2] && (
              <TextField label="Pièces à restituer" value={d.RETRAIT_PIECES_DEMANDEES ?? ''}
                onChange={(e) => setLigne('demarches', i, 'RETRAIT_PIECES_DEMANDEES', e.target.value)} />
            )}
            {!creation && (
              <Select label="La formalité a-t-elle été accomplie ?" value={d.RETRAIT_FORMALITE_ACCOMPLIE ?? ''}
                options={options(OUI_NON)} onChange={(e) => setLigne('demarches', i, 'RETRAIT_FORMALITE_ACCOMPLIE', e.target.value)} />
            )}
            {!creation && d.RETRAIT_FORMALITE_ACCOMPLIE === 'oui' && (
              <>
                <TextField type="date" label="Date de la formalité" value={d.RETRAIT_FORMALITE_DATE ?? ''}
                  onChange={(e) => setLigne('demarches', i, 'RETRAIT_FORMALITE_DATE', e.target.value)} />
                <TextField label="Référence de la formalité" value={d.RETRAIT_FORMALITE_REFERENCE ?? ''}
                  onChange={(e) => setLigne('demarches', i, 'RETRAIT_FORMALITE_REFERENCE', e.target.value)} />
              </>
            )}
            <TextField label="Pièces jointes à la lettre (facultatif)" value={d.RETRAIT_PIECES_JOINTES ?? ''}
              onChange={(e) => setLigne('demarches', i, 'RETRAIT_PIECES_JOINTES', e.target.value)}
              hint="En plus de la copie du récépissé de dépôt, déjà prévue par la lettre." />
            <TextField label="Observations (facultatif)" value={d.RETRAIT_OBSERVATIONS ?? ''}
              onChange={(e) => setLigne('demarches', i, 'RETRAIT_OBSERVATIONS', e.target.value)} />
            {d._suivie !== 'oui' && (
              <div className="sm:col-span-2">
                <Button variant="secondary" onClick={() => setDemarches((p) => p.filter((_, j) => j !== i))}>
                  <Trash2 className="mr-1 h-4 w-4" /> Retirer cette démarche
                </Button>
              </div>
            )}
          </fieldset>
        ))}
        {canAct && (
          <Button variant="secondary" onClick={() => setDemarches((p) => [...p, {}])}>
            <Plus className="mr-1 h-4 w-4" /> Ajouter une démarche
          </Button>
        )}
      </div>

      <div className="space-y-2">
        <p className="text-sm font-medium text-fg">Pièces restituées au client ({pieces.length})</p>
        {pieces.map((p, i) => (
          <fieldset key={i} disabled={lecture} data-testid={`piece-${i}`} className="grid gap-2 rounded-lg border border-border p-3 sm:grid-cols-4">
            <TextField label="Pièce" value={p.PIECE_DESIGNATION ?? ''}
              onChange={(e) => setLigne('pieces', i, 'PIECE_DESIGNATION', e.target.value)} />
            <TextField label="Forme" value={p.PIECE_FORME ?? ''} hint="Original, copie, copie légalisée."
              onChange={(e) => setLigne('pieces', i, 'PIECE_FORME', e.target.value)} />
            <TextField label="Nombre d’exemplaires" inputMode="numeric" value={p.PIECE_NOMBRE ?? ''}
              onChange={(e) => setLigne('pieces', i, 'PIECE_NOMBRE', e.target.value)} />
            <TextField label="Observation (facultatif)" value={p.PIECE_OBSERVATION ?? ''}
              onChange={(e) => setLigne('pieces', i, 'PIECE_OBSERVATION', e.target.value)} />
            {canAct && (
              <div className="sm:col-span-4">
                <Button variant="secondary" onClick={() => setPieces((l) => l.filter((_, j) => j !== i))}>
                  <Trash2 className="mr-1 h-4 w-4" /> Retirer cette pièce
                </Button>
              </div>
            )}
          </fieldset>
        ))}
        {canAct && (
          <Button variant="secondary" onClick={() => setPieces((l) => [...l, {}])}>
            <Plus className="mr-1 h-4 w-4" /> Ajouter une pièce
          </Button>
        )}
      </div>

      {canAct && (
        <div className="flex items-center gap-3">
          <Button variant="primary" onClick={() => void enregistrer()}>
            <Save className="mr-1 h-4 w-4" /> Enregistrer les saisies
          </Button>
          {enregistre && <span className="text-xs text-fg-muted">{enregistre}</span>}
        </div>
      )}

      <ul className="space-y-3">
        {documents.map((doc) => {
          const st = docs[doc.cle] ?? {};
          const horsEcran = (st.refus ?? []).filter(
            (d) => !CHAMPS_SAISIS.includes(d.variable) && !/^(DEMARCHE_|RETRAIT_|PIECE_)/.test(d.variable),
          );
          return (
            <li key={doc.cle} data-testid={`doc-${doc.cle}`} className="space-y-2 rounded-lg border border-border p-3">
              <div className="flex flex-wrap items-center gap-2">
                <span className="text-sm font-medium text-fg">{doc.libelle}</span>
                {canAct && (
                  <Button variant="secondary" disabled={st.enCours} onClick={() => void generer(doc)}>
                    {st.enCours ? 'Génération…' : st.blob ? 'Régénérer' : 'Générer'}
                  </Button>
                )}
                {st.blob && (
                  <Button variant="secondary" onClick={() => triggerBrowserDownload(st.blob!, st.filename ?? `${doc.code}.docx`)}>
                    <Download className="mr-1 h-4 w-4" /> Télécharger
                  </Button>
                )}
                {st.blob && canAct && ticket.dossierId && !st.depose && (
                  <Button variant="secondary" onClick={() => void deposer(doc)}>
                    <Upload className="mr-1 h-4 w-4" /> Déposer au Dossier juridique
                  </Button>
                )}
                {st.depose && <span className="text-xs text-success">Déposé au Dossier juridique.</span>}
              </div>
              <RetourGeneration erreur={st.refus?.length ? null : st.erreur} refus={st.refus} />
              {canAct && horsEcran.length > 0 && (
                <div className="space-y-2" data-testid={`complements-${doc.cle}`}>
                  <p className="text-xs text-fg-muted">
                    Ces données ne figurent pas dans les champs ci-dessus : saisissez-les ici, elles seront
                    enregistrées au ticket et le document sera régénéré.
                  </p>
                  {horsEcran.map((d) => (
                    <TextField key={d.variable} label={d.libelle} value={complements[d.variable] ?? ''}
                      onChange={(e) => setComplements((c) => ({ ...c, [d.variable]: e.target.value }))} />
                  ))}
                  <Button variant="primary" onClick={() => void completer(doc, horsEcran)}>
                    Enregistrer et régénérer
                  </Button>
                </div>
              )}
            </li>
          );
        })}
      </ul>
    </section>
  );
}
