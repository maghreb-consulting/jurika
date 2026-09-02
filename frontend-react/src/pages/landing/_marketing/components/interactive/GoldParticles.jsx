import { useEffect, useRef } from 'react';
import { useReducedMotion } from '../../hooks/useReducedMotion.js';

/**
 * v2 Flagship — Layer 2 parallax : particules or canvas (-0.3x).
 *
 * 60 particules dorées flottantes, rendues sur un <canvas> plein écran
 * du composant parent. Léger jitter + drift vertical lent. Si
 * `prefers-reduced-motion` : on rend un fond statique (aucune anim).
 *
 * Performance : ~60 particles avec rAF + ctx.globalAlpha, < 1ms par
 * frame en moyenne sur desktop, ~2ms sur mid-range mobile.
 */
const PARTICLE_COUNT = 60;

export function GoldParticles({ density = PARTICLE_COUNT, opacity = 0.5 }) {
  const reduced = useReducedMotion();
  const canvasRef = useRef(null);
  const particlesRef = useRef([]);
  const rafRef = useRef(null);

  useEffect(() => {
    if (reduced) return undefined;
    const canvas = canvasRef.current;
    if (!canvas) return undefined;
    const ctx = canvas.getContext && canvas.getContext('2d');
    if (!ctx) return undefined; // jsdom + envs sans canvas API : no-op silencieux
    const dpr = Math.min(window.devicePixelRatio || 1, 2);

    const resize = () => {
      const rect = canvas.getBoundingClientRect();
      canvas.width = rect.width * dpr;
      canvas.height = rect.height * dpr;
      canvas.style.width = rect.width + 'px';
      canvas.style.height = rect.height + 'px';
      if (ctx.scale) ctx.scale(dpr, dpr);
    };
    resize();
    window.addEventListener('resize', resize);

    const rect = canvas.getBoundingClientRect();
    particlesRef.current = Array.from({ length: density }, () => ({
      x: Math.random() * rect.width,
      y: Math.random() * rect.height,
      r: 0.6 + Math.random() * 1.8,
      vx: (Math.random() - 0.5) * 0.15,
      vy: -0.05 - Math.random() * 0.12,
      a: 0.3 + Math.random() * 0.7,
      twinkle: Math.random() * Math.PI * 2,
    }));

    const tick = () => {
      const r = canvas.getBoundingClientRect();
      ctx.clearRect(0, 0, r.width, r.height);
      const now = performance.now() / 1000;
      for (const p of particlesRef.current) {
        p.x += p.vx;
        p.y += p.vy;
        if (p.y < -5) {
          p.y = r.height + 5;
          p.x = Math.random() * r.width;
        }
        if (p.x < -5) p.x = r.width + 5;
        if (p.x > r.width + 5) p.x = -5;
        const tw = (Math.sin(now + p.twinkle) + 1) / 2; // 0..1
        const alpha = p.a * (0.4 + 0.6 * tw) * opacity;
        ctx.globalAlpha = alpha;
        ctx.fillStyle = '#c8a45c';
        ctx.beginPath();
        ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2);
        ctx.fill();
      }
      rafRef.current = requestAnimationFrame(tick);
    };
    tick();

    return () => {
      window.removeEventListener('resize', resize);
      if (rafRef.current) cancelAnimationFrame(rafRef.current);
    };
  }, [reduced, density, opacity]);

  return <canvas ref={canvasRef} className="gold-particles" aria-hidden="true" />;
}
