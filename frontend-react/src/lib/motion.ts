import { gsap } from 'gsap';
import { ScrollTrigger } from 'gsap/ScrollTrigger';
import { useEffect, useRef } from 'react';

gsap.registerPlugin(ScrollTrigger);

export { gsap, ScrollTrigger };

/**
 * useGsap — context isole, cleanup automatique.
 * Pattern : pinned-scrub, sticky-stack, horizontal-scrub.
 */
export function useGsap<T extends HTMLElement = HTMLDivElement>(
  callback: (ctx: gsap.Context, scope: T) => void,
  deps: React.DependencyList = [],
) {
  const ref = useRef<T | null>(null);

  useEffect(() => {
    if (!ref.current) return;
    const ctx = gsap.context((self) => callback(self, ref.current!), ref.current);
    return () => ctx.revert();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  return ref;
}

/** Easing tokens — calibrees sur Linear/Stripe */
export const EASE = {
  outExpo: 'expo.out',
  outQuad: 'power2.out',
  inOutQuad: 'power2.inOut',
  inOutCubic: 'power3.inOut',
} as const;

/** Spring config Framer Motion */
export const SPRING = {
  default: { type: 'spring', stiffness: 400, damping: 32 } as const,
  soft: { type: 'spring', stiffness: 200, damping: 24 } as const,
};

/** Variants reutilisables */
import type { Variants } from 'framer-motion';

export const fadeUp: Variants = {
  hidden: { opacity: 0, y: 24 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.6, ease: [0.16, 1, 0.3, 1] as const } },
};

export const fadeIn: Variants = {
  hidden: { opacity: 0 },
  visible: { opacity: 1, transition: { duration: 0.6, ease: 'easeOut' } },
};

export const stagger = (delay = 0.08): Variants => ({
  hidden: {},
  visible: { transition: { staggerChildren: delay } },
});
