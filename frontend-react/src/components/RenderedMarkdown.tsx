/**
 * RenderedMarkdown — convertisseur Markdown -> JSX legere, sans dependance externe.
 *
 * Suffisant pour les Statuts SARL generes par l'IA :
 *  - # / ## / ### (titres 1/2/3)
 *  - paragraphes
 *  - listes a puces (- item) et numerotees (1. item)
 *  - blockquotes (> ...)
 *  - separateurs horizontaux (---)
 *  - inline gras (**texte**) + italique (*texte*)
 *  - commentaires HTML <!-- ... --> filtres
 *
 * Pas de support code blocks / tables / images : non requis pour les statuts.
 */
import { type JSX } from 'react';

const H1 = /^#\s+(.+)$/;
const H2 = /^##\s+(.+)$/;
const H3 = /^###\s+(.+)$/;
const BULLET = /^[\-*]\s+(.+)$/;
const NUMBERED = /^\d+\.\s+(.+)$/;
const BLOCKQUOTE = /^>\s*(.*)$/;
const HTML_COMMENT = /^<!--.*?-->\s*$/;
const HR = /^(?:---|\*\*\*)\s*$/;

const BOLD = /\*\*([^*]+)\*\*/g;
const ITALIC = /(?<!\*)\*([^*]+)\*(?!\*)/g;

function escapeHtml(s: string): string {
  return s
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;');
}

function renderInline(text: string): string {
  const escaped = escapeHtml(text);
  return escaped
    .replace(BOLD, '<strong>$1</strong>')
    .replace(ITALIC, '<em>$1</em>');
}

interface Props {
  markdown: string;
  className?: string;
}

export function RenderedMarkdown({ markdown, className }: Props): JSX.Element {
  const lines = (markdown ?? '').split(/\r?\n/);
  const nodes: JSX.Element[] = [];
  let listBuffer: string[] = [];
  let i = 0;

  function flushList() {
    if (listBuffer.length === 0) return;
    const items = listBuffer.slice();
    listBuffer = [];
    nodes.push(
      <ul key={`ul-${nodes.length}`} className="ml-5 list-disc space-y-1 py-1">
        {items.map((it, idx) => (
          <li
            key={idx}
            className="text-[13px] leading-relaxed text-fg"
            dangerouslySetInnerHTML={{ __html: renderInline(it) }}
          />
        ))}
      </ul>,
    );
  }

  for (const rawLine of lines) {
    const line = rawLine.trim();
    if (HTML_COMMENT.test(line)) continue;

    if (line === '') {
      flushList();
      continue;
    }

    let m: RegExpMatchArray | null;
    if ((m = line.match(H1))) {
      flushList();
      nodes.push(
        <h1
          key={`h1-${i++}`}
          className="mt-4 mb-3 text-center text-lg font-bold uppercase tracking-wider text-fg"
          dangerouslySetInnerHTML={{ __html: renderInline(m[1]) }}
        />,
      );
    } else if ((m = line.match(H2))) {
      flushList();
      nodes.push(
        <h2
          key={`h2-${i++}`}
          className="mt-4 mb-2 border-b border-accent/40 pb-1 text-[15px] font-bold uppercase text-accent"
          dangerouslySetInnerHTML={{ __html: renderInline(m[1]) }}
        />,
      );
    } else if ((m = line.match(H3))) {
      flushList();
      nodes.push(
        <h3
          key={`h3-${i++}`}
          className="mt-3 mb-1 text-[13px] font-bold italic text-accent/90"
          dangerouslySetInnerHTML={{ __html: renderInline(m[1]) }}
        />,
      );
    } else if ((m = line.match(BULLET)) || (m = line.match(NUMBERED))) {
      listBuffer.push(m[1]);
    } else if ((m = line.match(BLOCKQUOTE))) {
      flushList();
      nodes.push(
        <blockquote
          key={`bq-${i++}`}
          className="my-2 border-l-2 border-border pl-3 text-[12px] italic text-fg-subtle"
          dangerouslySetInnerHTML={{ __html: renderInline(m[1]) }}
        />,
      );
    } else if (HR.test(line)) {
      flushList();
      nodes.push(<hr key={`hr-${i++}`} className="my-3 border-border" />);
    } else {
      flushList();
      nodes.push(
        <p
          key={`p-${i++}`}
          className="mb-2 text-justify text-[13px] leading-relaxed text-fg"
          dangerouslySetInnerHTML={{ __html: renderInline(line) }}
        />,
      );
    }
  }
  flushList();

  return (
    <div className={className ?? 'prose prose-sm max-w-none'}>{nodes}</div>
  );
}
