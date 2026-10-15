import { useState } from 'react';
import { Download, Copy, Check } from 'lucide-react';
import { Button } from '../ui/Button';
import { Case } from '../ui/Choix';
import { Message } from '../ui/Message';
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
    lines.push('JURIKA — Codes de récupération (double authentification)');
    lines.push('==================================================');
    if (user) {
      lines.push(`Compte     : ${user.email ?? '(non renseigné)'}`);
    }
    lines.push(`Générés le : ${new Date().toLocaleString('fr-MA')}`);
    lines.push(`Contexte   : ${context === 'INITIAL_SETUP' ? 'activation de la double authentification' : 'régénération'}`);
    lines.push('');
    lines.push('⚠  AVERTISSEMENT');
    lines.push('   Conservez ces codes en lieu sûr (impression, coffre-fort, gestionnaire');
    lines.push('   de mots de passe). Chaque code ne sert qu’une seule fois.');
    lines.push('   Si vous perdez votre téléphone, ils sont votre seul moyen de');
    lines.push('   retrouver l’accès à votre compte JURIKA.');
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
    a.download = `jurika-codes-de-recuperation-${Date.now()}.txt`;
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
      <Message ton="attention" titre="Ces codes ne seront plus affichés">
        Téléchargez-les ou recopiez-les maintenant. Chaque code ne sert qu’une fois, et seulement si vous perdez
        l’accès à votre méthode habituelle de double authentification.
      </Message>

      <div className="grid gap-2 rounded-lg border border-border bg-bg-overlay p-3 sm:grid-cols-2">
        {codes.map((c, i) => (
          <code
            key={i}
            className="block rounded bg-bg-raised px-3 py-2 text-center font-mono text-sm tracking-wider text-fg ring-1 ring-border"
          >
            <span className="mr-2 text-[10px] font-semibold uppercase text-fg-subtle">
              {(i + 1).toString().padStart(2, '0')}
            </span>
            {c}
          </code>
        ))}
      </div>

      <div className="flex flex-wrap gap-2">
        <Button type="button" variant="secondary" size="sm" onClick={downloadTxt} icon={<Download />}>
          Télécharger (.txt)
        </Button>
        <Button
          type="button"
          variant="secondary"
          size="sm"
          onClick={copyAll}
          icon={copied ? <Check className="text-success" /> : <Copy />}
        >
          {copied ? 'Codes copiés' : 'Copier les 10 codes'}
        </Button>
      </div>

      <div className="rounded-lg border border-border bg-bg-raised p-3">
        <Case
          checked={checked}
          onChange={(e) => setChecked(e.target.checked)}
          data-testid="recovery-codes-checkbox"
          label={
            <>
              J’ai <strong>mis mes codes de récupération en lieu sûr</strong>. Je comprends qu’ils ne seront{' '}
              <strong>plus jamais affichés</strong>.
            </>
          }
        />
      </div>

      <Button
        type="button"
        size="lg"
        fullWidth
        onClick={onContinue}
        disabled={!checked}
        data-testid="recovery-codes-continue"
      >
        {continueLabel}
      </Button>
    </div>
  );
}
