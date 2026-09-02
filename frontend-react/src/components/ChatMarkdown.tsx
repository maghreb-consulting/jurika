/**
 * ChatMarkdown — rendu Markdown SÛR de la réponse du chatbot (LLM).
 *
 * <p>S'appuie sur `react-markdown` + `remark-gfm` (listes, tables, liens auto).
 * Sécurité : react-markdown n'exécute PAS de HTML brut par défaut — on n'active
 * NI `rehype-raw` NI `dangerouslySetInnerHTML`. Le contenu venant du LLM reste
 * donc non exécutable (pas d'injection XSS via `**`/balises).
 *
 * Le style suit la charte : paragraphes espacés, gras/italique, listes, liens
 * accent, petits blocs de code. Un texte simple (sans markdown) reste rendu
 * proprement en un paragraphe.
 */
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import type { Components } from 'react-markdown';

const COMPONENTS: Components = {
  p: ({ children }) => (
    <p className="mb-2 text-sm leading-relaxed text-fg last:mb-0">{children}</p>
  ),
  strong: ({ children }) => <strong className="font-semibold text-fg">{children}</strong>,
  em: ({ children }) => <em className="italic">{children}</em>,
  ul: ({ children }) => (
    <ul className="mb-2 ml-5 list-disc space-y-1 text-sm leading-relaxed text-fg last:mb-0">
      {children}
    </ul>
  ),
  ol: ({ children }) => (
    <ol className="mb-2 ml-5 list-decimal space-y-1 text-sm leading-relaxed text-fg last:mb-0">
      {children}
    </ol>
  ),
  li: ({ children }) => <li className="pl-0.5">{children}</li>,
  h1: ({ children }) => <h3 className="mb-2 mt-1 text-base font-semibold text-fg">{children}</h3>,
  h2: ({ children }) => <h3 className="mb-2 mt-1 text-sm font-semibold text-fg">{children}</h3>,
  h3: ({ children }) => <h4 className="mb-1 mt-1 text-sm font-semibold text-fg">{children}</h4>,
  a: ({ href, children }) => (
    <a
      href={href}
      target="_blank"
      rel="noopener noreferrer"
      className="font-medium text-accent underline underline-offset-2 hover:text-accent-hover"
    >
      {children}
    </a>
  ),
  blockquote: ({ children }) => (
    <blockquote className="my-2 border-l-2 border-border pl-3 text-sm italic text-fg-subtle">
      {children}
    </blockquote>
  ),
  code: ({ children }) => (
    <code className="rounded bg-bg-overlay px-1 py-0.5 font-mono text-[0.85em] text-fg">
      {children}
    </code>
  ),
  hr: () => <hr className="my-3 border-border" />,
  table: ({ children }) => (
    <div className="my-2 overflow-x-auto">
      <table className="w-full border-collapse text-sm text-fg">{children}</table>
    </div>
  ),
  th: ({ children }) => (
    <th className="border border-border bg-bg-overlay px-2 py-1 text-left font-semibold">
      {children}
    </th>
  ),
  td: ({ children }) => <td className="border border-border px-2 py-1">{children}</td>,
};

export function ChatMarkdown({ content }: { content: string }) {
  return (
    <div className="text-sm text-fg">
      <ReactMarkdown remarkPlugins={[remarkGfm]} components={COMPONENTS}>
        {content}
      </ReactMarkdown>
    </div>
  );
}
