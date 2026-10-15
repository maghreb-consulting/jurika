import { useState } from 'react';
import { Plus, Trash2, Upload, CheckCircle2 } from 'lucide-react';
import { TexteAide } from '../ui/Aide';
import { Button } from '../ui/Button';
import { classesBouton } from '../ui/boutonClasses';
import { Select } from '../ui/Select';
import { TextField } from '../ui/TextField';
import { dataroomService } from '../../services/dataroom.service';
import { extractError } from '../../lib/api';
import { manquePublicite, publiciteVide, type Publicite } from '../../lib/publicites';

/**
 * Lot L4d (E8), décision du directeur du 11 octobre 2026 : à l'import d'un dossier, les
 * publicités de la société rejoignent les pièces — annonces au journal d'annonces légales et
 * publications au Bulletin officiel, pour la constitution et chaque modification : nature,
 * journal, date et numéro de parution, pièce versée en Data Room.
 */
function titrePiece(p: Publicite, societe: string): string {
  const support = p.support === 'BO' ? 'Publication au Bulletin officiel' : `Annonce légale — ${p.journal ?? ''}`.trim();
  const nature = p.nature === 'MODIFICATION' ? `Modification (${p.objet ?? ''})` : 'Constitution';
  return `${support} — ${nature} — ${societe} — n° ${p.numeroParution} du ${p.dateParution}`;
}

interface Props {
  value: Publicite[];
  onChange: (next: Publicite[]) => void;
  dossierId?: string | null;
  societe: string;
}

export function PublicitesSociete({ value, onChange, dossierId, societe }: Props) {
  const [erreurs, setErreurs] = useState<Record<number, string>>({});
  const [envoi, setEnvoi] = useState<number | null>(null);
  const maj = (i: number, patch: Partial<Publicite>) =>
    onChange(value.map((p, k) => (k === i ? { ...p, ...patch } : p)));

  async function deposer(i: number, fichier: File | undefined) {
    if (!fichier || !dossierId) return;
    const p = value[i];
    const manque = manquePublicite(p);
    if (manque) {
      setErreurs((e) => ({ ...e, [i]: `Avant de verser la pièce, ${manque}` }));
      return;
    }
    setEnvoi(i);
    try {
      const doc = await dataroomService.uploadJuridique(dossierId, {
        file: fichier,
        documentType: p.support === 'BO' ? 'PUBLICATION_BO' : 'JOURNAL_ANNONCE',
        title: titrePiece(p, societe),
      });
      maj(i, { documentId: doc.id, fichier: fichier.name });
      setErreurs((e) => ({ ...e, [i]: '' }));
    } catch (err) {
      setErreurs((e) => ({ ...e, [i]: extractError(err).message }));
    } finally {
      setEnvoi(null);
    }
  }

  return (
    <section className="space-y-3 rounded-xl border border-border bg-bg-raised p-4" data-testid="publicites-societe">
      <h3 className="text-sm font-semibold text-fg">Publicités de la société</h3>
      <TexteAide cle="import-publicites" titre="Quelles publicités saisir ?">
        <p>
          Indiquez chaque publicité légale de la société : l’annonce au journal d’annonces légales et
          la publication au Bulletin officiel, pour la constitution puis pour chaque modification.
          Pour chacune : la nature, le journal, la date et le numéro de parution, puis versez
          l’exemplaire ou le justificatif : il rejoint la Data Room du dossier. Cette liste est
          facultative, mais une publicité commencée doit être complète.
        </p>
      </TexteAide>

      {value.map((p, i) => (
        <div key={i} className="space-y-2 rounded-lg border border-border p-3" data-testid={`publicite-${i}`}>
          <div className="grid gap-2 md:grid-cols-3">
            <Select label="Nature" aria-label={`Nature de la publicité ${i + 1}`} value={p.nature}
              onChange={(e) => maj(i, { nature: e.target.value as Publicite['nature'] })}
              options={[
                { value: '', label: 'Choisir…' },
                { value: 'CONSTITUTION', label: 'Constitution' },
                { value: 'MODIFICATION', label: 'Modification' },
              ]} />
            {p.nature === 'MODIFICATION' && (
              <TextField label="Objet de la modification" containerClassName="md:col-span-2"
                aria-label={`Objet de la modification ${i + 1}`} value={p.objet ?? ''}
                placeholder="Ex. : transfert du siège social" onChange={(e) => maj(i, { objet: e.target.value })} />
            )}
            <Select label="Support" aria-label={`Support de la publicité ${i + 1}`} value={p.support}
              onChange={(e) => maj(i, { support: e.target.value as Publicite['support'] })}
              options={[
                { value: '', label: 'Choisir…' },
                { value: 'JAL', label: 'Journal d’annonces légales' },
                { value: 'BO', label: 'Bulletin officiel' },
              ]} />
            {p.support === 'JAL' && (
              <TextField label="Journal" aria-label={`Journal ${i + 1}`} value={p.journal ?? ''}
                onChange={(e) => maj(i, { journal: e.target.value })} />
            )}
            <TextField label="Date de parution" type="date" aria-label={`Date de parution ${i + 1}`} value={p.dateParution}
              onChange={(e) => maj(i, { dateParution: e.target.value })} />
            <TextField label="Numéro de parution" aria-label={`Numéro de parution ${i + 1}`} value={p.numeroParution}
              onChange={(e) => maj(i, { numeroParution: e.target.value })} />
          </div>
          <div className="flex flex-wrap items-center gap-3 text-xs">
            {p.documentId ? (
              <span className="inline-flex items-center gap-1 text-success">
                <CheckCircle2 className="h-3.5 w-3.5" /> Pièce versée en Data Room{p.fichier ? ` : ${p.fichier}` : ''}
              </span>
            ) : (
              <label className={classesBouton('secondary', 'sm', `cursor-pointer has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-focus ${!dossierId ? 'pointer-events-none opacity-55' : ''}`)}>
                <Upload className="h-4 w-4" aria-hidden="true" />
                {envoi === i ? 'Envoi…' : 'Verser la pièce (exemplaire du journal ou du Bulletin officiel)'}
                <input type="file" className="sr-only" aria-label={`Pièce de la publicité ${i + 1}`}
                  onChange={(e) => { void deposer(i, e.target.files?.[0]); e.target.value = ''; }} />
              </label>
            )}
            <Button type="button" variant="ghost" size="sm" icon={<Trash2 />} className="text-fg-muted hover:text-danger"
              onClick={() => onChange(value.filter((_, k) => k !== i))}>
              Retirer
            </Button>
          </div>
          {erreurs[i] && <p role="alert" className="text-xs text-danger">{erreurs[i]}</p>}
        </div>
      ))}

      <Button type="button" variant="secondary" size="sm" icon={<Plus />}
        onClick={() => onChange([...value, publiciteVide()])}>
        Ajouter une publicité
      </Button>
    </section>
  );
}
