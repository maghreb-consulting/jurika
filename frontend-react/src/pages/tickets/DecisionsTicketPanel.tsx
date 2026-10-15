import { useEffect, useMemo, useState } from 'react';
import { Save } from 'lucide-react';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { InfoBulle, TexteAide } from '../../components/ui/Aide';
import { Message } from '../../components/ui/Message';
import { TextField } from '../../components/ui/TextField';
import { extractError } from '../../lib/api';
import {
  decisionsService,
  LIBELLES_MATRICES,
  type DecisionsTicket,
  type DecisionProposee,
} from '../../services/decisions.service';

/**
 * Lot L4b (E4, RG-DEM-01) : les décisions d'un ticket hors création.
 *
 * Elles choisissent, dans le référentiel du cabinet (onglets « Démarches » des matrices),
 * les démarches du ticket. Une décision renvoyée vers une autre famille suit la matrice de
 * cette famille (onglet « Renvois »). Ce qui manque pour passer au statut suivant est nommé.
 */
export function DecisionsTicketPanel({
  ticketId,
  canAct,
  onChanged,
}: {
  ticketId: string;
  canAct: boolean;
  onChanged?: () => void;
}) {
  const [etat, setEtat] = useState<DecisionsTicket | null>(null);
  const [choix, setChoix] = useState<Set<string>>(new Set());
  const [erreur, setErreur] = useState<string | null>(null);
  const [enCours, setEnCours] = useState(false);
  const [filtre, setFiltre] = useState('');

  useEffect(() => {
    let actif = true;
    decisionsService
      .lire(ticketId)
      .then((d) => {
        if (!actif) return;
        setEtat(d);
        setChoix(new Set(d.choisies.map((c) => `${c.matrice}|${c.code}`)));
      })
      .catch((err) => actif && setErreur(extractError(err).message));
    return () => {
      actif = false;
    };
  }, [ticketId]);

  const parMatrice = useMemo(() => {
    const m = new Map<string, DecisionProposee[]>();
    const f = filtre.trim().toLowerCase();
    for (const p of etat?.proposees ?? []) {
      if (f && !`${p.libelle ?? ''} ${p.code}`.toLowerCase().includes(f) && !choix.has(`${p.matrice}|${p.code}`)) continue;
      m.set(p.matrice, [...(m.get(p.matrice) ?? []), p]);
    }
    return m;
  }, [etat, filtre, choix]);

  if (!etat || !etat.corpus) return erreur ? <p role="alert" className="text-sm text-danger">{erreur}</p> : null;

  const modifie =
    choix.size !== etat.choisies.length || etat.choisies.some((c) => !choix.has(`${c.matrice}|${c.code}`));

  async function enregistrer() {
    setEnCours(true);
    setErreur(null);
    try {
      const d = await decisionsService.choisir(
        ticketId,
        [...choix].map((k) => {
          const [matrice, code] = k.split('|');
          return { matrice, code };
        }),
      );
      setEtat(d);
      onChanged?.();
    } catch (err) {
      setErreur(extractError(err).message);
    } finally {
      setEnCours(false);
    }
  }

  return (
    <Card>
      <section data-testid="decisions-ticket" className="space-y-3 px-5 py-4">
        <h3 className="flex items-center gap-1 text-sm font-semibold text-fg">
          Décisions du ticket
          <InfoBulle
            libelle="À quoi servent les décisions ?"
            texte="Chaque décision du ticket apporte ses démarches, tirées du référentiel du cabinet : documents à générer, dépôts, retraits et formalités de clôture. Une décision renvoyée vers une autre famille suit la matrice de cette famille."
          />
        </h3>
        <TexteAide cle="decisions-ticket" titre="Comment choisir les décisions">
          Cochez la ou les décisions que la société adopte dans ce dossier, puis enregistrez : les démarches du
          ticket apparaissent en dessous, statut par statut. Une démarche « à apprécier » dépend d’une donnée non
          encore saisie ou d’une condition rédigée en phrase : cochez-la si elle s’applique, sinon écartez-la avec
          un motif.
        </TexteAide>

        {etat.manques.length > 0 && (
          <Message ton="attention">
            <ul data-testid="decisions-manques" className="space-y-1">
              {etat.manques.map((m) => <li key={m}>{m}</li>)}
            </ul>
          </Message>
        )}
        <p className="text-xs text-fg-subtle">
          Forme : <strong>{etat.forme === 'SARL_AU' ? 'SARL AU' : etat.forme ?? 'inconnue'}</strong>
          {etat.versionReferentiel && <> · Référentiel : <span className="font-mono">{etat.versionReferentiel}</span></>}
        </p>

        {etat.proposees.length > 0 && (
          <TextField
            type="search"
            aria-label="Rechercher une décision"
            placeholder="Rechercher une décision…"
            value={filtre}
            onChange={(e) => setFiltre(e.target.value)}
          />
        )}

        {[...parMatrice.entries()].map(([matrice, decisions]) => (
          <fieldset key={matrice} disabled={!canAct} className="space-y-1">
            <legend className="text-xs font-semibold text-fg-muted">
              {LIBELLES_MATRICES[matrice] ?? matrice}
            </legend>
            <ul className="max-h-64 space-y-1 overflow-y-auto overscroll-auto">
              {decisions.map((p) => {
                const cle = `${p.matrice}|${p.code}`;
                return (
                  <li key={cle}>
                    <label className="flex cursor-pointer items-start gap-2 text-sm text-fg">
                      <input
                        type="checkbox"
                        className="mt-1 h-4 w-4 accent-accent"
                        checked={choix.has(cle)}
                        onChange={(e) => {
                          setChoix((prev) => {
                            const s = new Set(prev);
                            if (e.target.checked) s.add(cle);
                            else s.delete(cle);
                            return s;
                          });
                        }}
                      />
                      <span>
                        {p.libelle ?? <span className="font-mono">{p.code}</span>}
                        {p.matriceRenvoi && (
                          <span className="block text-xs text-fg-subtle">
                            Renvoi : la matrice {LIBELLES_MATRICES[p.matriceRenvoi] ?? p.matriceRenvoi} fait foi
                            (décision n° {p.numeroRenvoi}).
                          </span>
                        )}
                      </span>
                    </label>
                  </li>
                );
              })}
            </ul>
          </fieldset>
        ))}

        {erreur && <p role="alert" className="text-sm text-danger">{erreur}</p>}
        {canAct && (
          <Button variant="primary" disabled={!modifie || enCours} onClick={() => void enregistrer()}>
            <Save className="mr-1 h-4 w-4" /> {enCours ? 'Enregistrement…' : 'Enregistrer les décisions'}
          </Button>
        )}
      </section>
    </Card>
  );
}
