import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { OcrSuggestionsPanel, type OcrSuggestion } from '../OcrSuggestionsPanel';

const SAMPLE_SUGGESTIONS: OcrSuggestion[] = [
  { field: 'ice', label: 'ICE', value: '002345678000077' },
  { field: 'denomination', label: 'Denomination', value: 'ATLAS SARL' },
];

describe('OcrSuggestionsPanel', () => {
  it('affiche un spinner en mode loading sans rendre les suggestions', () => {
    const onApplyOne = vi.fn();
    const onApplyAll = vi.fn();
    render(
      <OcrSuggestionsPanel
        loading
        filename="cn.pdf"
        suggestions={[]}
        onApplyOne={onApplyOne}
        onApplyAllEmpty={onApplyAll}
      />,
    );
    expect(screen.getByText(/Extraction IA en cours/i)).toBeDefined();
    expect(screen.queryByText(/Appliquer/i)).toBeNull();
  });

  it('rend une suggestion par champ + bouton Appliquer individuel', () => {
    const onApplyOne = vi.fn();
    const onApplyAll = vi.fn();
    render(
      <OcrSuggestionsPanel
        suggestions={SAMPLE_SUGGESTIONS}
        source="OCR_TESSERACT"
        extractionMode="PDFBOX_TEXT"
        confidence={0.83}
        onApplyOne={onApplyOne}
        onApplyAllEmpty={onApplyAll}
      />,
    );
    expect(screen.getByText('002345678000077')).toBeDefined();
    expect(screen.getByText('ATLAS SARL')).toBeDefined();
    // Affiche la source et le mode lisible.
    expect(screen.getByText(/PDF numerique \(couche texte\)/)).toBeDefined();
    expect(screen.getByText(/confiance 83%/)).toBeDefined();

    const buttons = screen.getAllByRole('button', { name: /Appliquer$/ });
    expect(buttons.length).toBe(2);
    fireEvent.click(buttons[0]);
    expect(onApplyOne).toHaveBeenCalledWith('ice', '002345678000077');
    fireEvent.click(buttons[1]);
    expect(onApplyOne).toHaveBeenCalledWith('denomination', 'ATLAS SARL');
  });

  it('le bouton "Appliquer aux champs vides uniquement" appelle onApplyAllEmpty', () => {
    const onApplyOne = vi.fn();
    const onApplyAll = vi.fn();
    render(
      <OcrSuggestionsPanel
        suggestions={SAMPLE_SUGGESTIONS}
        onApplyOne={onApplyOne}
        onApplyAllEmpty={onApplyAll}
      />,
    );
    fireEvent.click(screen.getByRole('button', { name: /Appliquer aux champs vides/i }));
    expect(onApplyAll).toHaveBeenCalledTimes(1);
    // Ne pas appeler onApplyOne globalement -- c'est au parent d'orchestrer le merge.
    expect(onApplyOne).not.toHaveBeenCalled();
  });

  it('affiche le panneau fallback quand manualFallback=true et aucune suggestion', () => {
    const onApplyOne = vi.fn();
    const onApplyAll = vi.fn();
    render(
      <OcrSuggestionsPanel
        suggestions={[]}
        manualFallback
        extractionMode="PDFBOX_TEXT"
        warnings={['ICE non detecte']}
        onApplyOne={onApplyOne}
        onApplyAllEmpty={onApplyAll}
      />,
    );
    expect(screen.getByText(/Aucun champ extrait automatiquement/)).toBeDefined();
    expect(screen.getByText(/PDF numerique/)).toBeDefined();
    expect(screen.getByText(/ICE non detecte/)).toBeDefined();
  });

  it('ne rend rien (null) si suggestions vide ET pas de manualFallback ET pas loading', () => {
    const { container } = render(
      <OcrSuggestionsPanel
        suggestions={[]}
        onApplyOne={() => {}}
        onApplyAllEmpty={() => {}}
      />,
    );
    expect(container.firstChild).toBeNull();
  });
});
