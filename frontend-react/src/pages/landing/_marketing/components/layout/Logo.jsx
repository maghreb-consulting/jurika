// Nouveau logo JURIKA (2026-07-27) : embleme officiel. Deux fonds — navy (footer
// sombre, prop `light`) et clair (navbar clair). Meme source que BrandLogo.
import emblemNavy from '../../../../../assets/brand/jurika-emblem-navy.png';
import emblemClair from '../../../../../assets/brand/jurika-emblem-clair.png';

/**
 * v2 Flagship — Logo JURIKA, ré-utilisable Navbar + Footer.
 * Pulse animation (Easter egg JURIKA) ciblée via data-attribute.
 */
export function Logo({ light = false, pulsing = false }) {
  return (
    <a href="#top" className="logo" data-pulse={pulsing ? 'true' : undefined} aria-label="JURIKA — accueil">
      <img
        src={light ? emblemNavy : emblemClair}
        alt=""
        width={36}
        height={36}
        draggable={false}
        style={{ height: 36, width: 'auto' }}
        aria-hidden="true"
      />
      <span className={light ? 'logo-text light' : 'logo-text'}>
        <span className="logo-juri">JURI</span><span className="logo-ka">KA</span>
      </span>
    </a>
  );
}
