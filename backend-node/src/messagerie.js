/**
 * Lot L5 (E9) -- controles de la messagerie interne et des notifications
 * (cahier des charges, RG-COM-06 et RG-COM-08).
 *
 * RG-COM-06 : la messagerie relie le superviseur et les employes d'un MEME
 * cabinet. Le client n'y a pas acces ; aucun message ne traverse deux cabinets.
 * RG-COM-08 : seuls les employes et le superviseur recoivent des notifications
 * dans l'application ; le client est averti par courriel (RG-COM-09).
 *
 * Regle : REFUS PAR DEFAUT. Un destinataire qu'on ne retrouve pas dans le
 * cabinet de l'expediteur est refuse (l'ancien code laissait passer un inconnu).
 *
 * Module sans dependance (ni pg, ni socket.io) : il recoit l'acces a la base en
 * parametre, ce qui le rend testable par `node --test` sans infrastructure.
 */

const ROLES_INTERNES = Object.freeze(['EMPLOYE', 'SUPERVISEUR']);
const TAILLE_MAX_MESSAGE = 4000;
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** Messages affiches a l'utilisateur, par code de refus. */
const MESSAGES_REFUS = Object.freeze({
  ROLE_REFUSE: 'La messagerie interne est réservée au superviseur et aux employés du cabinet.',
  MESSAGE_INVALIDE: 'Le message est vide ou dépasse 4 000 caractères.',
  DESTINATAIRE_INVALIDE: 'Destinataire invalide.',
  DESTINATAIRE_SOI_MEME: 'Vous ne pouvez pas vous écrire à vous-même.',
  DESTINATAIRE_INCONNU: 'Ce destinataire n’appartient pas à votre cabinet.',
  DESTINATAIRE_NON_AUTORISE: 'Ce destinataire n’a pas accès à la messagerie interne.',
  DESTINATAIRE_INACTIF: 'Ce compte est désactivé : il ne peut plus recevoir de message.',
});

/** Identite lue dans un jeton verifie (claims d'auth-service). */
function identiteDuJeton(decoded) {
  if (!decoded || typeof decoded !== 'object') return null;
  return {
    userId: decoded.uid || decoded.sub || null,
    workspaceId: decoded.wsid || decoded.workspace_id || null,
    role: decoded.role || null,
  };
}

function estRoleInterne(role) {
  return ROLES_INTERNES.includes(role);
}

/** L'utilisateur du jeton peut-il utiliser la messagerie ? */
function peutUtiliserMessagerie(identite) {
  return Boolean(identite && identite.userId && identite.workspaceId && estRoleInterne(identite.role));
}

/**
 * Lit un membre DANS le cabinet donne. Le filtre workspace_id est explicite, et
 * le workspace est aussi pose pour la RLS (transaction locale) : les deux, comme
 * dans les services Java. Absent du cabinet = null.
 *
 * @param db objet expose `connect()` (pool pg) : client avec `query` et `release`.
 */
async function lireMembre(db, workspaceId, userId) {
  if (!UUID_RE.test(String(workspaceId || '')) || !UUID_RE.test(String(userId || ''))) return null;
  const client = await db.connect();
  try {
    await client.query('BEGIN');
    await client.query("SELECT set_config('app.current_workspace_id', $1, true)", [workspaceId]);
    const { rows } = await client.query(
      'SELECT id, workspace_id, role, status FROM users WHERE id = $1 AND workspace_id = $2',
      [userId, workspaceId]
    );
    await client.query('COMMIT');
    if (!rows.length) return null;
    const r = rows[0];
    // Defense en profondeur : la ligne rendue doit bien etre du cabinet demande.
    if (String(r.workspace_id) !== String(workspaceId)) return null;
    return { id: r.id, workspaceId: r.workspace_id, role: r.role, status: r.status };
  } catch (err) {
    try { await client.query('ROLLBACK'); } catch { /* connexion deja perdue */ }
    throw err;
  } finally {
    client.release();
  }
}

/**
 * Controle d'un correspondant de la messagerie (envoi, indicateur de frappe).
 * Renvoie `{ ok: true }` ou `{ ok: false, code, message }`.
 */
async function controlerCorrespondant(db, identite, destinataireId) {
  if (!peutUtiliserMessagerie(identite)) return refus('ROLE_REFUSE');
  if (!UUID_RE.test(String(destinataireId || ''))) return refus('DESTINATAIRE_INVALIDE');
  if (String(destinataireId).toLowerCase() === String(identite.userId).toLowerCase()) {
    return refus('DESTINATAIRE_SOI_MEME');
  }
  const membre = await lireMembre(db, identite.workspaceId, destinataireId);
  if (!membre) return refus('DESTINATAIRE_INCONNU');
  if (!estRoleInterne(membre.role)) return refus('DESTINATAIRE_NON_AUTORISE');
  if (membre.status === 'INACTIVE') return refus('DESTINATAIRE_INACTIF');
  return { ok: true };
}

/** Controle complet d'un envoi : contenu du message puis correspondant. */
async function controlerEnvoi(db, identite, to, body) {
  if (!peutUtiliserMessagerie(identite)) return refus('ROLE_REFUSE');
  if (typeof body !== 'string' || !body.trim() || body.length > TAILLE_MAX_MESSAGE) {
    return refus('MESSAGE_INVALIDE');
  }
  return controlerCorrespondant(db, identite, to);
}

/**
 * Destinataire d'une notification dans l'application (RG-COM-08) : un employe
 * ou le superviseur DU cabinet de la notification. Un client (averti par
 * courriel, RG-COM-09), un inconnu ou un membre d'un autre cabinet : refus.
 */
async function controlerDestinataireNotification(db, workspaceId, userId) {
  const membre = await lireMembre(db, workspaceId, userId);
  if (!membre) return refus('DESTINATAIRE_INCONNU');
  if (!estRoleInterne(membre.role)) return refus('DESTINATAIRE_NON_AUTORISE');
  return { ok: true };
}

function refus(code) {
  return { ok: false, code, message: MESSAGES_REFUS[code] || 'Refusé.' };
}

module.exports = {
  ROLES_INTERNES,
  TAILLE_MAX_MESSAGE,
  MESSAGES_REFUS,
  identiteDuJeton,
  estRoleInterne,
  peutUtiliserMessagerie,
  lireMembre,
  controlerCorrespondant,
  controlerEnvoi,
  controlerDestinataireNotification,
};
