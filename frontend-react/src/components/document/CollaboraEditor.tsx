import { useCallback, useEffect, useRef, useState } from 'react';
import { AlertTriangle, Loader, ShieldCheck } from 'lucide-react';
import { dataroomService } from '../../services/dataroom.service';
import type { SeanceEdition } from '../../types/dataroom';

/**
 * Édition bureautique fidèle — l'éditeur Collabora, dans un cadre.
 *
 * <p>Lot 3 (2026-09-07). Remplace le mode « Édition libre » (TipTap) dont
 * l'aller-retour `.docx → HTML → .docx` détruisait la mise en page du
 * directeur : `styles.xml` disparaissait, les 49 paragraphes portant le style
 * `JurikaTitreArticle` le perdaient, les 11 numérotations automatiques et les
 * 89 alignements avec. Mesuré au lot 2, puis re-mesuré au lot 3 avec Collabora :
 * tout est conservé.
 *
 * <p>Le composant ne reçoit pas le document : il ouvre une SÉANCE côté serveur,
 * et c'est Collabora qui va chercher le fichier et qui le repose (protocole
 * WOPI). Le navigateur ne transporte jamais le binaire.
 */

interface Props {
  documentId: string;
  /** Titre lisible, pour l'en-tête et les messages. */
  titre: string;
  /** Fermeture demandée par l'utilisateur. */
  onClose: () => void;
  /**
   * Appelé après la fermeture de la séance, quand au moins un enregistrement a
   * eu lieu : le document en base a changé, l'appelant doit se rafraîchir.
   */
  onEdited?: () => void;
}

export function CollaboraEditor({ documentId, titre, onClose, onEdited }: Props) {
  const [seance, setSeance] = useState<SeanceEdition | null>(null);
  const [erreur, setErreur] = useState<string | null>(null);
  /**
   * Vrai dès que l'éditeur signale une modification. Sert à ne prévenir
   * l'appelant que si quelque chose a réellement changé.
   */
  const aModifie = useRef(false);
  const seanceRef = useRef<SeanceEdition | null>(null);

  // ─── Ouverture de la séance ────────────────────────────────────────
  useEffect(() => {
    let annule = false;
    void dataroomService
      .ouvrirSeanceEdition(documentId)
      .then((s) => {
        if (annule) return;
        seanceRef.current = s;
        setSeance(s);
      })
      .catch((e: unknown) => {
        if (annule) return;
        setErreur(e instanceof Error ? e.message : "Impossible d'ouvrir l'éditeur");
      });
    return () => {
      annule = true;
    };
  }, [documentId]);

  // ─── Fermeture : révocation côté serveur ───────────────────────────
  //
  // La séance est fermée au démontage. Le serveur coupe la LECTURE aussitôt
  // mais laisse une fenêtre de grâce à l'ÉCRITURE : Collabora enregistre de
  // façon asynchrone et appelle souvent PutFile après la fermeture de l'onglet.
  useEffect(() => {
    return () => {
      const s = seanceRef.current;
      if (!s) return;
      void dataroomService.fermerSeanceEdition(s.sessionId).catch(() => {
        /* best-effort : la séance expirera d'elle-même */
      });
      if (aModifie.current) onEdited?.();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // ─── Messages de l'éditeur ─────────────────────────────────────────
  //
  // Collabora communique par `postMessage`. On n'écoute que ce dont on a
  // besoin — savoir qu'une modification a eu lieu — et on ignore le reste.
  const onMessage = useCallback((e: MessageEvent) => {
    if (!seanceRef.current) return;
    try {
      const data = typeof e.data === 'string' ? JSON.parse(e.data) : e.data;
      if (data?.MessageId === 'Doc_ModifiedStatus' && data?.Values?.Modified) {
        aModifie.current = true;
      }
      if (data?.MessageId === 'Action_Save_Resp' || data?.MessageId === 'Document_BeforeSave') {
        aModifie.current = true;
      }
    } catch {
      /* message non JSON : ce n'est pas pour nous */
    }
  }, []);

  useEffect(() => {
    window.addEventListener('message', onMessage);
    return () => window.removeEventListener('message', onMessage);
  }, [onMessage]);

  if (erreur) {
    return (
      <div className="flex h-full flex-col items-center justify-center gap-3 p-6 text-center">
        <AlertTriangle className="h-6 w-6 text-danger" />
        <p className="text-sm text-fg">L'éditeur n'a pas pu s'ouvrir.</p>
        <p className="max-w-lg text-xs text-fg-subtle">{erreur}</p>
        <button
          type="button"
          onClick={onClose}
          className="rounded-lg border border-border bg-bg-raised px-3 py-1.5 text-xs font-semibold text-fg"
        >
          Fermer
        </button>
      </div>
    );
  }

  if (!seance) {
    return (
      <div className="flex h-full items-center justify-center gap-2 text-sm text-fg-subtle">
        <Loader className="h-4 w-4 animate-spin" />
        Ouverture de l'éditeur…
      </div>
    );
  }

  // `editeurUrl` vient de la découverte Collabora et se termine déjà par « ? »
  // ou « & » : son chemin porte une empreinte de version qui change à chaque
  // publication de l'image. La deviner marcherait aujourd'hui et tomberait à la
  // prochaine mise à jour du conteneur.
  const sep = seance.editeurUrl.endsWith('?') || seance.editeurUrl.endsWith('&') ? '' : '?';
  const url =
    `${seance.editeurUrl}${sep}` +
    `WOPISrc=${encodeURIComponent(seance.wopiSrc)}` +
    `&access_token=${encodeURIComponent(seance.accessToken)}` +
    `&access_token_ttl=${Date.now() + seance.accessTokenTtlMs}` +
    `&lang=fr-FR` +
    (seance.canWrite ? '' : '&permission=readonly');

  return (
    <div className="flex h-full flex-col">
      {/*
        AUCUN MOYEN D'ANNULER UNE ÉDITION.
        Ouvrir un acte validé, taper une lettre par mégarde et fermer crée une
        nouvelle version. Ce n'est pas grave — la précédente reste en historique
        — mais il faut le DIRE, sinon l'employé croira avoir abîmé un acte
        définitif et n'osera plus ouvrir l'éditeur.
      */}
      <div className="flex items-start gap-2 border-b border-border bg-bg-overlay px-4 py-2 text-[11px] leading-snug text-fg-subtle">
        {seance.verrouPar ? (
          <AlertTriangle className="mt-0.5 h-3.5 w-3.5 shrink-0 text-warning" />
        ) : (
          <ShieldCheck className="mt-0.5 h-3.5 w-3.5 shrink-0 text-success" />
        )}
        {seance.verrouPar ? (
          /*
            UN COLLÈGUE ÉDITE DÉJÀ CET ACTE.
            On le dit ici, avant la première frappe. Laisser deux employés
            travailler en parallèle sur le même acte juridique conduit à ce que
            le second enregistrement écrase le premier — c'est précisément ce
            que le verrou empêche, mais l'employé doit savoir POURQUOI il ne
            peut pas taper, sinon il croira à une panne.
          */
          <span>
            <strong className="text-fg">Lecture seule — {seance.verrouPar} édite cet acte</strong>
            {seance.verrouDepuis
              ? ` depuis ${new Date(seance.verrouDepuis).toLocaleTimeString('fr-FR', {
                  hour: '2-digit',
                  minute: '2-digit',
                })}`
              : ''}
            . Vos modifications ne seraient pas enregistrées. Rouvrez l'acte une fois
            qu'il aura refermé son éditeur.
          </span>
        ) : seance.canWrite ? (
          <span>
            Vos modifications sont enregistrées au fil de la frappe.{' '}
            <strong className="text-fg">Il n'y a pas d'annulation après coup</strong> : la
            première modification crée une nouvelle version du document. Rien n'est
            perdu pour autant — la version précédente reste consultable dans
            «&nbsp;Anciennes versions&nbsp;», et vous pouvez la restaurer.
          </span>
        ) : (
          <span>
            <strong className="text-fg">Lecture seule.</strong> Votre rôle ne permet pas de
            modifier cet acte, ou le dossier est archivé.
          </span>
        )}
      </div>

      <iframe
        title={`Édition — ${titre}`}
        src={url}
        className="min-h-0 flex-1 border-0"
        allow="clipboard-read; clipboard-write"
        data-testid="collabora-iframe"
      />
    </div>
  );
}
