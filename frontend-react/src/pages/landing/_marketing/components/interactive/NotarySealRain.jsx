import { useEffect, useMemo, useState } from 'react';
import { useReducedMotion } from '../../hooks/useReducedMotion.js';

/**
 * v2 Flagship — Pluie de 24 sceaux de notaire (or, étoile 5 branches Maroc)
 * tombent du top pendant 3s avec rotation aléatoire + gravité légère.
 *
 * Trigger : prop `active` toggle true par useEasterEgg (séquence JURIKA).
 * Si reduced-motion : ne rend rien (toast conservé hors composant).
 *
 * pointer-events: none, z-index: 9999.
 */
const SEAL_COUNT = 24;
const DURATION_MS = 3000;

function StylizedSeal({ size = 32, seed = 0 }) {
  // Rond or avec étoile 5 branches du Maroc en relief + microtexte simulé.
  // Identifiants SVG uniques par sceau pour éviter collisions defs.
  const gradId = `sealG-${seed}`;
  return (
    <svg width={size} height={size} viewBox="0 0 64 64" aria-hidden="true">
      <defs>
        <radialGradient id={gradId} cx="50%" cy="38%" r="60%">
          <stop offset="0" stopColor="#e3c688" />
          <stop offset="0.55" stopColor="#c8a45c" />
          <stop offset="1" stopColor="#8a6a30" />
        </radialGradient>
      </defs>
      <circle cx="32" cy="32" r="30" fill={`url(#${gradId})`} stroke="#6b521f" strokeWidth="1.5" />
      <circle cx="32" cy="32" r="26" fill="none" stroke="#6b521f" strokeWidth="0.5" opacity="0.5" />
      <circle cx="32" cy="32" r="22" fill="none" stroke="#6b521f" strokeWidth="0.4" opacity="0.35" />
      {/* Étoile 5 branches du Maroc (relief) */}
      <path
        d="M32 14 L36.6 27.4 L50.6 27.4 L39.3 35.6 L43.9 49 L32 40.8 L20.1 49 L24.7 35.6 L13.4 27.4 L27.4 27.4 Z"
        fill="#a47e35"
        fillOpacity="0.85"
        stroke="#6b521f"
        strokeWidth="0.9"
        strokeLinejoin="round"
      />
      {/* Microtexte circulaire (simulé par points qui suggèrent JURIKA·MAGHREB) */}
      <g opacity="0.4" fill="#6b521f">
        {Array.from({ length: 14 }).map((_, i) => {
          const a = (i / 14) * Math.PI * 2 - Math.PI / 2;
          const r = 28;
          const cx = 32 + Math.cos(a) * r;
          const cy = 32 + Math.sin(a) * r;
          return <circle key={i} cx={cx} cy={cy} r="0.5" />;
        })}
      </g>
    </svg>
  );
}

export function NotarySealRain({ active }) {
  const reduced = useReducedMotion();
  const [mounted, setMounted] = useState(false);

  // Position random par seal (memo pour stabilité)
  const seals = useMemo(
    () =>
      Array.from({ length: SEAL_COUNT }, (_, i) => ({
        id: i,
        left: Math.random() * 100,
        delayMs: Math.random() * 600,
        rotation: Math.random() * 360,
        rotationSpeed: 360 + Math.random() * 540,
        size: 28 + Math.random() * 18,
        duration: 1800 + Math.random() * 1200,
      })),
    []
  );

  useEffect(() => {
    if (!active || reduced) return undefined;
    setMounted(true);
    const t = setTimeout(() => setMounted(false), DURATION_MS);
    return () => clearTimeout(t);
  }, [active, reduced]);

  if (!mounted || reduced) return null;

  return (
    <div className="seal-rain" aria-hidden="true">
      {seals.map((s) => (
        <span
          key={s.id}
          className="seal-rain__item"
          style={{
            left: `${s.left}%`,
            animationDelay: `${s.delayMs}ms`,
            animationDuration: `${s.duration}ms`,
            '--seal-rot': `${s.rotation}deg`,
            '--seal-rot-end': `${s.rotation + s.rotationSpeed}deg`,
          }}
        >
          <StylizedSeal size={s.size} seed={s.id} />
        </span>
      ))}
    </div>
  );
}
