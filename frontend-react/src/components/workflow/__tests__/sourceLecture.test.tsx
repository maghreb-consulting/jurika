import '@testing-library/jest-dom/vitest';
import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { libelleSourceLecture } from '../sourceLecture';
import { OcrSuggestionsPanel } from '../OcrSuggestionsPanel';

/**
 * Lot L6b (E5), backlog L6 : "OcrSuggestionsPanel affiche la source tesseract / llm".
 * Le nom technique du moteur ne s'affiche plus ; la source se dit en francais.
 */
describe('Source de la lecture automatique', () => {
  it.each([
    ['OCR_TESSERACT', 'lecture automatique du document'],
    ['OCR_DOCTR', 'lecture automatique du document'],
    ['OCR_MANUAL_FALLBACK', 'aucune lecture automatique'],
    ['OCR_EMPTY', 'aucune lecture automatique'],
    ['LLM_OPENAI', 'lecture assistée par l’IA'],
    ['LLM_DEGRADED', 'lecture automatique simplifiée'],
    ['merged', 'lecture du document et de sa bande de lecture automatique (MRZ)'],
    ['kie', 'lecture automatique du document'],
  ])('%s -> %s', (source, libelle) => {
    expect(libelleSourceLecture(source)).toBe(libelle);
  });

  it('source absente ou inconnue : rien', () => {
    expect(libelleSourceLecture(undefined)).toBeNull();
    expect(libelleSourceLecture('')).toBeNull();
    expect(libelleSourceLecture('AUTRE')).toBeNull();
  });

  it('le panneau des suggestions ne montre plus le nom du moteur', () => {
    render(
      <OcrSuggestionsPanel
        suggestions={[{ field: 'ice', label: 'Numéro ICE', value: '002345678000077' }]}
        source="OCR_TESSERACT"
        extractionMode="PDFBOX_TEXT"
        onApplyOne={() => {}}
        onApplyAllEmpty={() => {}}
      />,
    );
    expect(screen.queryByText(/tesseract/i)).toBeNull();
    expect(screen.getByText(/source : lecture automatique du document/)).toBeInTheDocument();
  });

  it('une source LLM se dit lecture assistee, sans "llm"', () => {
    render(
      <OcrSuggestionsPanel
        suggestions={[{ field: 'ice', label: 'Numéro ICE', value: '002345678000077' }]}
        source="LLM_OPENAI"
        onApplyOne={() => {}}
        onApplyAllEmpty={() => {}}
      />,
    );
    expect(screen.queryByText(/llm/i)).toBeNull();
    expect(screen.getByText(/source : lecture assistée par l’IA/)).toBeInTheDocument();
  });
});
