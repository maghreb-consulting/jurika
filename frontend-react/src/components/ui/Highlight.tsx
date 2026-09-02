import { useMemo } from 'react';

/**
 * Sprint 7 / TASK 1.5 -- Surligne les occurrences d'une query dans un texte.
 *
 * Strategie cote client : split sur regex insensitive case + wrap des matches
 * dans <mark>. La query est split en mots (separes par espaces) pour matcher
 * chaque token independamment, similar a websearch_to_tsquery cote serveur.
 *
 * - query vide -> render texte brut, zero overhead
 * - escape regex chars pour eviter ReDoS sur input utilisateur
 * - <mark> styles via Tailwind directement (pas de CSS externe)
 */
interface HighlightProps {
  text: string;
  query?: string | null;
  className?: string;
}

function escapeRegex(s: string): string {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

export function Highlight({ text, query, className }: HighlightProps) {
  const parts = useMemo(() => {
    if (!query || !query.trim()) return [{ text, match: false }];
    const tokens = query
      .trim()
      .split(/\s+/)
      .filter((t) => t.length > 0)
      .map(escapeRegex);
    if (tokens.length === 0) return [{ text, match: false }];
    const re = new RegExp(`(${tokens.join('|')})`, 'ig');
    const out: { text: string; match: boolean }[] = [];
    let lastIdx = 0;
    let m: RegExpExecArray | null;
    while ((m = re.exec(text)) !== null) {
      if (m.index > lastIdx) {
        out.push({ text: text.slice(lastIdx, m.index), match: false });
      }
      out.push({ text: m[0], match: true });
      lastIdx = m.index + m[0].length;
      // Prevent zero-length match infinite loop
      if (m.index === re.lastIndex) re.lastIndex++;
    }
    if (lastIdx < text.length) {
      out.push({ text: text.slice(lastIdx), match: false });
    }
    return out;
  }, [text, query]);

  return (
    <span className={className}>
      {parts.map((p, i) =>
        p.match ? (
          <mark
            key={i}
            className="rounded bg-amber-100 px-0.5 text-amber-900"
          >
            {p.text}
          </mark>
        ) : (
          <span key={i}>{p.text}</span>
        ),
      )}
    </span>
  );
}
