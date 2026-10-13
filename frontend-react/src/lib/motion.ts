import { gsap } from 'gsap';
import { ScrollTrigger } from 'gsap/ScrollTrigger';

gsap.registerPlugin(ScrollTrigger);

export { gsap, ScrollTrigger };


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
