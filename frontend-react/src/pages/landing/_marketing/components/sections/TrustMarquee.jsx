/**
 * Bandeau defilant (lot L7 E3) : les operations couvertes (CDC 16.1) et les textes de
 * droit marocain auxquels la plateforme s'applique (CDC 1). Aucune interoperabilite avec
 * une administration n'est annoncee : la transmission electronique au greffe est une
 * extension ulterieure (CDC 16.2).
 */
const OPERATIONS = [
  'Création de SARL',
  'SARL à associé unique',
  'Modifications et vie sociale',
  'Assemblée générale ordinaire',
  'Succursales',
  'Dissolution et liquidation',
  'Import de dossier',
];
const TEXTES = [
  'Loi n° 5-96',
  'Code de commerce',
  'Code général des impôts',
  'Loi n° 09-08',
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
      aria-label="Opérations couvertes et textes applicables"
    >
      <div className="container">
        <p className="cloud-title">
          Conçu pour les cabinets qui gèrent la vie juridique des sociétés, en droit marocain.
        </p>
      </div>
      <MarqueeRow items={OPERATIONS} />
      <MarqueeRow items={TEXTES} reverse />
    </section>
  );
}
