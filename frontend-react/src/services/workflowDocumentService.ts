import { api } from '../lib/api';

/**
 * Sprint 12.X — Frontend service pour le manifest documentaire des workflows.
 *
 * Couple le composant generique GenerateDocumentPanel au backend
 * WorkflowDocumentController (GET templates + POST document generation).
 *
 * Le manifest YAML est charge cote backend par TemplateManifestLoader ;
 * chaque entree expose code / documentKind / origin / file / deprecated /
 * placeholderStyle. Le front ne fait AUCUNE logique metier : il liste,
 * affiche, et delegue la generation (responseType: blob, telechargement DOCX).
 */
export interface TemplateInfo {
  code: string;
  documentKind: string;
  file: string;
  origin: string;
  deprecated: boolean;
  placeholderStyle: string;
}

const CONTENT_DISPOSITION_FILENAME =
  /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i;

function extractFilename(disposition: string | undefined, fallback: string): string {
  if (!disposition) return fallback;
  const match = CONTENT_DISPOSITION_FILENAME.exec(disposition);
  if (!match || !match[1]) return fallback;
  try {
    return decodeURIComponent(match[1]).trim();
  } catch {
    return match[1].trim();
  }
}

export async function listTemplatesForWorkflow(
  workflowCode: string,
): Promise<TemplateInfo[]> {
  const { data } = await api.get<TemplateInfo[]>(
    `/ai/workflows/${encodeURIComponent(workflowCode)}/templates`,
  );
  return data;
}

/** Lot L3 : une donnee nommee (variable du dictionnaire unique, libelle a l'ecran). */
export interface DonneeNommee {
  variable: string;
  libelle: string;
}

/** Lot L3 : donnee interne manquante, avec la phrase de l'acte ou elle s'imprime. */
export interface DonneeManquante extends DonneeNommee {
  endroit: string;
}

/**
 * Lot L3 (regle des variables) : generation refusee ou impossible. `donneesManquantes`
 * est rempli quand le serveur nomme les donnees internes qui manquent (422).
 */
export class GenerationRefuseeError extends Error {
  readonly code: string;
  readonly donneesManquantes: DonneeManquante[];

  constructor(message: string, code: string, donneesManquantes: DonneeManquante[] = []) {
    super(message);
    this.name = 'GenerationRefuseeError';
    this.code = code;
    this.donneesManquantes = donneesManquantes;
  }
}

export interface DocumentGenere {
  blob: Blob;
  filename: string;
  /** Lot L3 : donnees externes manquantes (marquees « À OBTENIR » dans l'acte). */
  donneesAObtenir: DonneeNommee[];
}

function lireDonneesAObtenir(entete: string | undefined): DonneeNommee[] {
  if (!entete) return [];
  try {
    const l = JSON.parse(decodeURIComponent(entete.replace(/\+/g, ' '))) as DonneeNommee[];
    return Array.isArray(l) ? l : [];
  } catch {
    return [];
  }
}

/**
 * La reponse d'erreur arrive en Blob (`responseType: 'blob'`) : elle est relue en JSON,
 * sans quoi l'employe ne verrait que « Request failed with status code 422 ».
 */
async function erreurLisible(err: unknown): Promise<Error> {
  const reponse = (err as { response?: { status?: number; data?: unknown } })?.response;
  if (!reponse) {
    return new GenerationRefuseeError('La plateforme est injoignable : vérifiez la connexion, puis réessayez.', 'RESEAU');
  }
  let corps: Record<string, unknown> | null = null;
  try {
    const d = reponse.data;
    const texte = d instanceof Blob ? await d.text() : typeof d === 'string' ? d : JSON.stringify(d ?? {});
    corps = JSON.parse(texte) as Record<string, unknown>;
  } catch {
    corps = null;
  }
  const message = typeof corps?.message === 'string' && corps.message
    ? corps.message
    : `La génération a échoué (erreur ${reponse.status ?? 'inconnue'}).`;
  const code = typeof corps?.code === 'string' ? corps.code : String(reponse.status ?? 'INCONNU');
  const donnees = Array.isArray(corps?.donneesManquantes) ? (corps!.donneesManquantes as DonneeManquante[]) : [];
  return new GenerationRefuseeError(message, code, donnees);
}

export async function generateDocument(
  workflowCode: string,
  templateCode: string,
  payload: Record<string, unknown>,
): Promise<DocumentGenere> {
  let response;
  try {
    response = await api.post<Blob>(
      `/ai/workflows/${encodeURIComponent(workflowCode)}/documents/${encodeURIComponent(templateCode)}`,
      payload,
      { responseType: 'blob' },
    );
  } catch (err) {
    throw await erreurLisible(err);
  }
  const headers = response.headers as Record<string, string | undefined>;
  const disposition = headers['content-disposition'] ?? headers['Content-Disposition'];
  const filename = extractFilename(disposition, `${templateCode}.docx`);
  const blob = new Blob([response.data], {
    type:
      'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  });
  return { blob, filename, donneesAObtenir: lireDonneesAObtenir(headers['x-donnees-a-obtenir']) };
}
