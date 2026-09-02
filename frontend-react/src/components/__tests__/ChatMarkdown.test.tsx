import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ChatMarkdown } from '../ChatMarkdown';

// Rendu markdown SÛR de la reponse du chatbot (react-markdown + remark-gfm,
// sans rehype-raw ni dangerouslySetInnerHTML).
describe('ChatMarkdown', () => {
  it('rend le gras et l\'italique comme des elements (pas de markdown litteral)', () => {
    const { container } = render(
      <ChatMarkdown content="Le capital est **libere** a *25%* minimum." />,
    );
    expect(container.querySelector('strong')?.textContent).toBe('libere');
    expect(container.querySelector('em')?.textContent).toBe('25%');
    // Les etoiles brutes ne doivent plus apparaitre.
    expect(container.textContent).not.toContain('**');
  });

  it('rend les listes (remark-gfm)', () => {
    const md = ['Etapes :', '', '- Depot statuts', '- Publication JAL'].join('\n');
    const { container } = render(<ChatMarkdown content={md} />);
    const items = container.querySelectorAll('li');
    expect(items).toHaveLength(2);
    expect(items[0].textContent).toContain('Depot statuts');
  });

  it('rend un texte simple (sans markdown) en paragraphe', () => {
    render(<ChatMarkdown content="Bonjour, voici une reponse simple." />);
    expect(screen.getByText('Bonjour, voici une reponse simple.')).toBeInTheDocument();
  });

  it('n\'execute pas le HTML brut (securite : pas de rehype-raw)', () => {
    const { container } = render(
      <ChatMarkdown content={'Avant <img src=x onerror="alert(1)"> apres'} />,
    );
    // Le HTML brut n'est pas rendu comme un vrai element : aucune balise <img>.
    expect(container.querySelector('img')).toBeNull();
  });
});
