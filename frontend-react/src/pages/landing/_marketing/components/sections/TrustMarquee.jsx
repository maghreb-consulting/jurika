/**
 * v2 Flagship — TASK 8 Trust marquee dual line (deux rangs opposés).
 *
 * Rang 1 : entités cabinet (Maghreb Consulting, avocats, etc.) — scroll →
 * Rang 2 : institutions officielles (OMPIC, RC, CNSS, DGI, CNDP) — scroll ←
 * Vitesses légèrement décalées pour effet "constellation" papier juridique.
 */
const CABINETS = [
  'Maghreb Consulting',
  "Cabinets d'avocats",
  'Experts-comptables',
  "Centres d'affaires",
  'Notaires',
  'Conseils juridiques',
  'Sociétés de domiciliation',
];
const INSTITUTIONS = [
  'OMPIC',
  'Registre de Commerce',
  'CNSS',
  'DGI',
  'CNDP — Loi 09-08',
  'CGI Article 211',
  'Loi 5-96 (SARL)',
  'Code de commerce MA',
];

function MarqueeRow({ items, reverse = false }) {
  const doubled = [...items, ...items];
  return (
    <div className={`marquee-track ${reverse ? 'marquee-track--reverse' : ''}`} aria-hidden="true">
      <div className="marquee-row">
        {doubled.map((item, i) => (
          <span key={`${item}-${i}`} className="marquee-item">{item}</span>
        ))}
      </div>
    </div>
  );
}

export function TrustMarquee() {
  return (
    <section
      className="cloud trust-marquee trust-marquee--dual"
      aria-label="Conçu pour les cabinets et compatible avec les institutions du Royaume"
    >
      <div className="container">
        <p className="cloud-title">
          Conçu pour les cabinets de consulting, comptables agréés, avocats et notaires du Maghreb.
          Interopérable avec les institutions du Royaume.
        </p>
      </div>
      <MarqueeRow items={CABINETS} />
      <MarqueeRow items={INSTITUTIONS} reverse />
    </section>
  );
}
