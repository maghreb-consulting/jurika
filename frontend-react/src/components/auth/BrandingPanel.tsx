import { BrandLogo } from '../ui/BrandLogo';

/**
 * Left panel for auth/signup screens.
 * Maquette: 42% width, dark slate gradient, marketing testimonials.
 */
export function BrandingPanel() {
  return (
    <div className="relative hidden min-h-screen w-[42%] flex-col justify-between overflow-hidden bg-fg p-12 lg:flex">
      {/* Logo — surface toujours navy => embleme sur fond navy. */}
      <div className="z-10 flex items-center gap-3">
        <BrandLogo size={48} tone="navy" />
        <div>
          <p className="text-2xl font-bold tracking-tight text-bg-raised">JURIKA</p>
          <p className="text-xs uppercase tracking-widest text-fg-subtle">
            Gestion juridique SaaS
          </p>
        </div>
      </div>

      {/* Abstract geometric illustration */}
      <div className="pointer-events-none absolute inset-0 flex items-center justify-center opacity-20">
        <svg width="500" height="600" viewBox="0 0 500 600" fill="none">
          <path d="M100 150 L250 100 L200 250 Z" fill="#2563EB" opacity="0.3" />
          <path d="M300 200 L450 150 L400 350 Z" fill="#10B981" opacity="0.25" />
          <path d="M150 350 L300 300 L250 450 Z" fill="#2563EB" opacity="0.2" />
          <path d="M200 450 L350 400 L300 550 Z" fill="#10B981" opacity="0.3" />
          <circle cx="380" cy="280" r="60" fill="#2563EB" opacity="0.15" />
          <circle cx="120" cy="480" r="40" fill="#10B981" opacity="0.2" />
          <polygon points="320,480 380,520 340,580" fill="#2563EB" opacity="0.25" />
        </svg>
      </div>

      {/* Marketing copy */}
      <div className="z-10 space-y-4">
        <div className="rounded-lg border border-border-hi bg-bg-overlay p-5">
          <p className="mb-3 text-sm leading-relaxed text-bg-raised">
            « La creation SARL qui prenait 3 jours se fait maintenant en 2h grace a JURIKA. »
          </p>
          <p className="text-xs text-fg-subtle">— Cabinet Al-Amin, Casablanca</p>
        </div>

        <div className="rounded-lg border border-border-hi bg-bg-overlay p-5">
          <p className="mb-3 text-sm leading-relaxed text-bg-raised">
            « L'espace client Data Room est exactement ce que nos clients attendaient. »
          </p>
          <p className="text-xs text-fg-subtle">— Cabinet Benali & Associes</p>
        </div>
      </div>
    </div>
  );
}
