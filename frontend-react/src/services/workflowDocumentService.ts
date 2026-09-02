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

export async function generateDocument(
  workflowCode: string,
  templateCode: string,
  payload: Record<string, unknown>,
): Promise<{ blob: Blob; filename: string }> {
  const response = await api.post<Blob>(
    `/ai/workflows/${encodeURIComponent(workflowCode)}/documents/${encodeURIComponent(templateCode)}`,
    payload,
    { responseType: 'blob' },
  );
  const disposition =
    (response.headers as Record<string, string | undefined>)['content-disposition'] ??
    (response.headers as Record<string, string | undefined>)['Content-Disposition'];
  const filename = extractFilename(disposition, `${templateCode}.docx`);
  const blob = new Blob([response.data], {
    type:
      'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  });
  return { blob, filename };
}
