/**
 * v2 Flagship — Grain SVG noise full-viewport fixed.
 *
 * Détail texture papier "vélin" : SVG inline 4% opacity overlay blend.
 * z-index: 1, pointer-events: none. Activé TASK 3.
 *
 * Pourquoi inline : 0 round-trip HTTP, gzip < 200 bytes.
 */
export function GrainOverlay() {
  // turbulence SVG : baseFrequency 0.9 = grain papier de notaire,
  // numOctaves 2 = pas trop bruyant
  const svg = `<svg xmlns='http://www.w3.org/2000/svg' width='240' height='240'><filter id='n'><feTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='2' stitchTiles='stitch'/><feColorMatrix values='0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0.6 0'/></filter><rect width='100%' height='100%' filter='url(%23n)'/></svg>`;
  const dataUri = `url("data:image/svg+xml;utf8,${svg}")`;
  return (
    <div
      className="grain-overlay"
      aria-hidden="true"
      style={{ backgroundImage: dataUri }}
    />
  );
}
