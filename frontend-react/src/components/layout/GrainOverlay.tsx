/**
 * Sprint 12.5 T13 — Grain overlay global (port subset marketing).
 *
 * Pose une texture noise 3% en layer fixe par-dessus toute l'app. Donne
 * le "feel premium editorial" (rappel marketing-site Flagship) sans gener
 * la lecture.
 *
 * - position: fixed inset: 0 -> couvre tout l'ecran
 * - pointer-events: none -> ne capture jamais les clics
 * - z-index élevé mais sous les modals Radix (z-50 = floor des modals)
 * - mix-blend-mode: overlay -> s'integre proprement aux fonds clairs/sombres
 * - respecte prefers-reduced-motion via media query CSS dans index.css T1
 *
 * SVG noise inline (data: URI) -> pas de requete reseau, pas de cache miss.
 */
export function GrainOverlay() {
  return (
    <div
      aria-hidden="true"
      className="pointer-events-none fixed inset-0 z-40 motion-reduce:hidden"
      style={{
        opacity: 0.03,
        backgroundImage:
          "url(\"data:image/svg+xml;utf8,<svg viewBox='0 0 200 200' xmlns='http://www.w3.org/2000/svg'><filter id='n'><feTurbulence type='fractalNoise' baseFrequency='0.85' numOctaves='2' stitchTiles='stitch'/></filter><rect width='100%25' height='100%25' filter='url(%23n)' opacity='0.5'/></svg>\")",
        mixBlendMode: 'overlay',
      }}
    />
  );
}
