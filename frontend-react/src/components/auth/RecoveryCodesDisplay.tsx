import { useState } from 'react';
import { Download, Copy, Check, ShieldAlert } from 'lucide-react';
import { Button } from '../ui/Button';
import { useCurrentUser } from '../../store/authStore';

interface Props {
  codes: string[];
  /**
   * Callback declenche quand l'utilisateur a coche "J'ai bien sauvegarde" et
   * clique sur "Continuer". Bloque tant que la case n'est pas cochee.
   */
  onContinue: () => void;
  /** Libelle du bouton de validation final. Defaut : "Continuer". */
  continueLabel?: string;
  /**
   * Contexte affiche en haut (ex: "Activation 2FA" / "Regeneration").
   * Sert aussi de prefixe dans le fichier .txt telecharge.
   */
  context: 'INITIAL_SETUP' | 'REGENERATION';
}

/**
 * Sprint 3 / TASK 3 (RG-AU33) — Affichage ONE-SHOT des 10 codes de recuperation
 * 2FA generes par le backend.
 *
 * Garanties UX :
 * <ul>
 *     <li>Les codes ne sont jamais affiches a nouveau (le backend stocke uniquement les hashes).</li>
 *     <li>Le bouton "Continuer" est desactive tant que la case "J'ai sauvegarde" n'est pas cochee.</li>
 *     <li>Telechargement {@code .txt} formate avec horodatage, cabinet, email + warning.</li>
 *     <li>Copie integrale dans le presse-papiers (fallback : fichier).</li>
 * </ul>
 */
export function RecoveryCodesDisplay({ codes, onContinue, continueLabel = 'Continuer', context }: Props) {
  const user = useCurrentUser();
  const [checked, setChecked] = useState(false);
  const [copied, setCopied] = useState(false);

  function buildFileContent(): string {
    const lines: string[] = [];
    lines.push('JURIKA — Codes de récupération 2FA');
    lines.push('==================================================');
    if (user) {
      lines.push(`Email      : ${user.email ?? '(non renseigne)'}`);
    }
    lines.push(`Genere le  : ${new Date().toLocaleString('fr-MA')}`);
    lines.push(`Contexte   : ${context === 'INITIAL_SETUP' ? 'Activation initiale 2FA' : 'Regeneration'}`);
    lines.push('');
    lines.push('⚠  AVERTISSEMENT');
    lines.push('   Conservez ces codes en lieu sur (impression, coffre-fort, gestionnaire');
    lines.push('   de mots de passe). Chaque code est utilisable UNE SEULE FOIS.');
    lines.push('   En cas de perte de votre telephone, ils sont votre seul moyen de');
    lines.push('   recuperer votre compte JURIKA.');
    lines.push('');
    lines.push('Codes :');
    lines.push('-------');
    codes.forEach((c, i) => {
      lines.push(`  ${(i + 1).toString().padStart(2, '0')}.  ${c}`);
    });
    lines.push('');
    lines.push('JURIKA — Maghreb Consulting — support@jurika.ma');
    return lines.join('\n');
  }

  function downloadTxt() {
    const blob = new Blob([buildFileContent()], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `jurika-recovery-codes-${Date.now()}.txt`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  }

  async function copyAll() {
    try {
      await navigator.clipboard.writeText(codes.join('\n'));
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // Fallback : on force le telechargement si le clipboard n'est pas dispo.
      downloadTxt();
    }
  }

  return (
    <div className="space-y-5" data-testid="recovery-codes-display">
      <header className="flex items-start gap-3 rounded-lg border border-amber-200 bg-warning/10 p-4">
        <ShieldAlert className="mt-0.5 h-5 w-5 shrink-0 text-warning" />
        <div>
          <h3 className="text-sm font-semibold text-amber-900">
            Ces codes ne seront plus affiches
          </h3>
          <p className="mt-1 text-xs text-warning">
            Telechargez-les ou recopiez-les immediatement. Chaque code n'est utilisable qu'une seule fois,
            uniquement si vous perdez l'acces a votre methode 2FA principale.
          </p>
        </div>
      </header>

      <div className="grid grid-cols-2 gap-2 rounded-lg border border-border bg-bg-overlay p-3">
        {codes.map((c, i) => (
          <code
            key={i}
            className="block rounded bg-bg-raised px-3 py-2 text-center font-mono text-sm tracking-wider text-fg ring-1 ring-slate-200"
          >
            <span className="mr-2 text-[10px] font-semibold uppercase text-fg-subtle">
              {(i + 1).toString().padStart(2, '0')}
            </span>
            {c}
          </code>
        ))}
      </div>

      <div className="flex flex-wrap gap-2">
        <Button type="button" variant="secondary" size="sm" onClick={downloadTxt}>
          <Download className="mr-1 h-4 w-4" />
          Telecharger (.txt)
        </Button>
        <Button type="button" variant="ghost" size="sm" onClick={copyAll}>
          {copied ? (
            <>
              <Check className="mr-1 h-4 w-4 text-success" /> Copies !
            </>
          ) : (
            <>
              <Copy className="mr-1 h-4 w-4" /> Copier les 10 codes
            </>
          )}
        </Button>
      </div>

      <label className="flex items-start gap-2 rounded-lg border border-border bg-bg-raised p-3 text-sm text-fg-muted">
        <input
          type="checkbox"
          checked={checked}
          onChange={(e) => setChecked(e.target.checked)}
          className="mt-0.5 h-4 w-4 rounded border-border-hi text-accent focus:ring-indigo-500"
          data-testid="recovery-codes-checkbox"
        />
        <span>
          J'ai <strong>sauvegarde mes codes</strong> de recuperation en lieu sur. Je comprends qu'ils
          ne seront <strong>plus jamais affiches</strong>.
        </span>
      </label>

      <Button
        type="button"
        className="w-full"
        onClick={onContinue}
        disabled={!checked}
        data-testid="recovery-codes-continue"
      >
        {continueLabel}
      </Button>
    </div>
  );
}
