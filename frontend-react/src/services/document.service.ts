import { api } from '../lib/api';

/**
 * Service de generation de documents juridiques DOCX.
 * Templates dans backend-java/ai-service/src/main/resources/templates/docx/<CODE>.docx
 *
 * Templates disponibles actuellement (fournis par le directeur) :
 *  - STATUTS_CONSTITUTIFS_SARL.docx (10+ variables)
 *  - STATUTS_CONSTITUTIFS_SARL_AU.docx (idem)
 *  - STATUTS_MODIFIES_SARL.docx (texte avec [a completer] manuel)
 *  - STATUTS_MODIFIES_SARL_AU.docx (idem)
 *
 * Resolution : si templateCode='STATUTS_CONSTITUTIFS' + formeJuridique='SARL_AU',
 * le backend choisit automatiquement STATUTS_CONSTITUTIFS_SARL_AU.docx.
 */
export const documentService = {
  async listTypes(): Promise<string[]> {
    const { data } = await api.get<string[]>('/ai/documents/types');
    return data;
  },

  /**
   * Genere un document DOCX. Telecharge automatiquement.
   * @param templateCode code du document (ex 'STATUTS_CONSTITUTIFS')
   * @param variables    map des variables a substituer
   * @param downloadName nom du fichier final
   */
  async generate(
    templateCode: string,
    variables: Record<string, unknown>,
    downloadName?: string,
  ): Promise<{ filename: string; templateFound: boolean }> {
    const response = await api.post(
      `/ai/documents/generate/${templateCode}`,
      variables,
      { responseType: 'blob' },
    );
    const templateFound = response.headers['x-template-found'] === 'true';
    const blob = new Blob([response.data], {
      type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    });
    const filename = downloadName ?? `${templateCode}.docx`;
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(url);
    return { filename, templateFound };
  },

  /**
   * Genere SANS télécharger : retourne directement les bytes DOCX
   * (utilisé par l'éditeur in-app TipTap pour ouvrir le contenu).
   */
  async generateBlob(
    templateCode: string,
    variables: Record<string, unknown>,
  ): Promise<Blob> {
    const response = await api.post(
      `/ai/documents/generate/${templateCode}`,
      variables,
      { responseType: 'blob' },
    );
    return new Blob([response.data], {
      type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    });
  },

  /**
   * Sprint 2026-06-12 — Editeur in-app TipTap.
   * Convertit du HTML édité en .docx via le backend et déclenche le téléchargement.
   */
  async exportHtmlAsDocx(
    html: string,
    filename: string,
    title?: string,
  ): Promise<void> {
    const response = await api.post(
      '/ai/document-edit/html-to-docx',
      { html, filename, title },
      { responseType: 'blob' },
    );
    const blob = new Blob([response.data], {
      type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    });
    triggerDownload(blob, filename);
  },

  /**
   * Étape 7 (aperçu 2026-08) — Convertit le HTML édité en .docx et RETOURNE le
   * blob SANS déclencher de téléchargement. Utilisé par l'aperçu in-app : après
   * édition, on remplace le blob en mémoire pour que l'aperçu fidèle et le
   * bouton « Télécharger » reflètent immédiatement les modifications.
   */
  async convertHtmlToDocx(
    html: string,
    filename: string,
    title?: string,
  ): Promise<Blob> {
    const response = await api.post(
      '/ai/document-edit/html-to-docx',
      { html, filename, title },
      { responseType: 'blob' },
    );
    return new Blob([response.data], {
      type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    });
  },

  /**
   * Sprint 2026-06-12 — Export PDF du HTML édité (iText html2pdf backend).
   *
   * Aperçu éditable seulement : ne reflète pas les styles Word originaux du
   * gabarit (Mammoth.js lossy). Pour le PDF FIDELE final, utiliser
   * {@link renderTemplateAsPdf} depuis l'Étape 7.
   */
  async exportHtmlAsPdf(
    html: string,
    filename: string,
    title?: string,
  ): Promise<void> {
    const response = await api.post(
      '/ai/document-edit/html-to-pdf',
      { html, filename, title },
      { responseType: 'blob' },
    );
    const blob = new Blob([response.data], { type: 'application/pdf' });
    const finalName = filename.toLowerCase().endsWith('.pdf')
      ? filename
      : filename.replace(/\.[^.]+$/, '') + '.pdf';
    triggerDownload(blob, finalName);
  },

  /**
   * Sprint 2026-06-19 — PDF FIDELE.
   *
   * Genere le .docx style via DocxTemplateEngine puis le convertit en PDF
   * via LibreOffice headless cote serveur (DocxToPdfConverter). Le PDF
   * resultant respecte 100 % les styles Word du gabarit (Calibri,
   * JurikaTitreArticle, JurikaSousTitre, marges, variables rouges).
   *
   * @throws LibreOfficeUnavailableError si soffice n'est pas installe sur
   *         le serveur (HTTP 503 + X-LibreOffice-Unavailable). L'appelant
   *         doit basculer sur le telechargement du .docx natif.
   */
  async renderTemplateAsPdf(
    templateCode: string,
    variables: Record<string, unknown>,
    downloadName?: string,
  ): Promise<{ filename: string; missingVariables: string[] }> {
    let response;
    try {
      response = await api.post(
        `/ai/document-render/template-to-pdf/${templateCode}`,
        variables,
        { responseType: 'blob' },
      );
    } catch (err) {
      // Axios met le blob d'erreur dans err.response.data — on regarde le code+header.
      const status = (err as { response?: { status?: number; headers?: Record<string, string> } })
        ?.response?.status;
      const headers = (err as { response?: { headers?: Record<string, string> } })
        ?.response?.headers;
      if (status === 503 && headers?.['x-libreoffice-unavailable'] === 'true') {
        throw new LibreOfficeUnavailableError(
          'PDF fidele indisponible : LibreOffice n\'est pas installe sur le serveur. ' +
            'Le .docx reste disponible au telechargement.',
        );
      }
      throw err;
    }
    const blob = new Blob([response.data], { type: 'application/pdf' });
    const missingRaw = (response.headers['x-missing-variables'] ?? '') as string;
    const missingVariables = missingRaw.split(',').map((s) => s.trim()).filter(Boolean);
    const filename = downloadName ?? `${templateCode}.pdf`;
    triggerDownload(blob, filename);
    return { filename, missingVariables };
  },

  /**
   * Sprint 2026-06-19 — Sonde l'etat du moteur PDF fidele.
   * Le front peut s'en servir pour afficher ou non le bouton "PDF" en
   * fonction de la disponibilite de LibreOffice cote serveur.
   */
  async pdfRenderStatus(): Promise<{
    available: boolean;
    engine: string;
    sofficePath: string;
    version: string;
  }> {
    const { data } = await api.get('/ai/document-render/status');
    return data;
  },
};

/** Levee par renderTemplateAsPdf quand le backend retourne 503 + X-LibreOffice-Unavailable. */
export class LibreOfficeUnavailableError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'LibreOfficeUnavailableError';
  }
}

function triggerDownload(blob: Blob, filename: string) {
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(url);
}
