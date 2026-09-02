/**
 * v2 Flagship — Audiences carousel horizontal scroll-snap avec depth shadow.
 *
 * Pour TASK 2 : grille 4 cards. La conversion en carousel scroll-snap
 * mobile vient TASK 7 (perspective).
 */
const AUDIENCES = [
  { title: 'Cabinets de consulting juridique', desc: 'Pilotez créations, modifications et dissolutions de A à Z, sans Excel ni Word dispersés.' },
  { title: 'Experts-comptables agréés', desc: 'Une vue unifiée dossier comptable + dossier juridique, avec exercices archivés par année.' },
  { title: 'Avocats & notaires', desc: "Espace client sécurisé, génération d'actes conformes et chat client-conseil avec traçabilité." },
  { title: "Centres d'affaires & domiciliataires", desc: 'Gestion multi-clients avec workspaces isolés et facturation incluse.' },
];

export function AudiencesCarousel() {
  return (
    <section className="section section-audience" id="cible">
      <div className="container">
        <div className="section-head">
          <span className="eyebrow">À qui s'adresse JURIKA</span>
          <h2 className="section-title">Une plateforme, plusieurs métiers du droit</h2>
        </div>
        <div className="audience-grid">
          {AUDIENCES.map((a, i) => (
            <article className="audience-card" key={a.title}>
              <div className="audience-num">0{i + 1}</div>
              <h3>{a.title}</h3>
              <p>{a.desc}</p>
            </article>
          ))}
        </div>
      </div>
    </section>
  );
}
