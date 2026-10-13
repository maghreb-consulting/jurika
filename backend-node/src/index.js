/**
 * JURIKA Realtime Service (Node.js + Express + Socket.io)
 * Phase 5 V2 :
 *   - Messagerie interne superviseur <-> employes d'un meme cabinet (RG-COM-06,
 *     controles dans messagerie.js ; le client n'y a pas acces)
 *   - Notifications push in-app
 *   - Transfert dossier entre employes (avec acceptation)
 *   - Bridge RabbitMQ/Redis Pub-Sub <-> WebSocket
 */
const express = require('express');
const cors = require('cors');
const { createServer } = require('http');
const { Server } = require('socket.io');
const redis = require('redis');
const jwt = require('jsonwebtoken');
const { Pool } = require('pg');
const { randomUUID } = require('crypto');
const fs = require('fs');
const messagerie = require('./messagerie');

// ----------------------------------------------------------------------
// BUG 10 (2026-06-08) — verification JWT alignee avec auth-service.
// L'auth Java signe en RS256 quand JWT_ALGORITHM=RS256 et JWT_PUBLIC_KEY_PATH
// pointe sur la PEM publique. On supporte les deux schemas pour ne casser
// ni le dev (HS256 + JWT_SECRET) ni la pre-prod (RS256 + clef PEM).
// ----------------------------------------------------------------------
const JWT_ALGO = (process.env.JWT_ALGORITHM || 'HS256').toUpperCase();
const VERIFICATION_ALGOS = JWT_ALGO === 'RS256' ? ['RS256'] : ['HS256'];
// Chantier secrets-z440 : aucun repli de secret publie. Le service refuse de
// demarrer si sa cle manque. En RS256, une cle publique absente ou illisible
// arretait le service en HS256 avec un secret de developpement PUBLIE (jetons
// forgeables) : c'est desormais un arret.
function arret(message) {
  console.error(`[realtime] ${message}`);
  process.exit(1);
}
let verificationKey;
if (JWT_ALGO === 'RS256') {
  const pubPath = process.env.JWT_PUBLIC_KEY_PATH;
  if (!pubPath) {
    arret('JWT_ALGORITHM=RS256 mais JWT_PUBLIC_KEY_PATH absent : demarrage refuse');
  }
  try {
    verificationKey = fs.readFileSync(pubPath, 'utf8');
    console.log(`[realtime] JWT verification RS256 (public key ${pubPath})`);
  } catch (err) {
    arret(`echec lecture cle publique ${pubPath} : ${err.message} : demarrage refuse`);
  }
} else {
  verificationKey = process.env.JWT_SECRET;
  if (!verificationKey || verificationKey.length < 32) {
    arret('JWT_ALGORITHM=HS256 mais JWT_SECRET absent ou trop court (32 caracteres minimum) : demarrage refuse');
  }
  console.log('[realtime] JWT verification HS256');
}

// ----------------------------------------------------------------------
// CORS (lot 2, 2026-09-07) — le frontend est servi par nginx sur le PORT 80,
// donc depuis l'origine `http://localhost` (sans port). Les deux valeurs par
// defaut ne citaient que `http://localhost:5173`, l'ancien serveur de dev Vite :
// en pile Docker, `/notifications/unread-count` et `/chat/conversations`
// echouaient au preflight, en boucle et en silence cote utilisateur —
// notifications et chat simplement hors service. Un defaut d'origine ne doit
// pas dependre d'un port qui n'existe plus.
//
// Les deux listes (HTTP et WebSocket) partagent desormais la meme source : les
// voir diverger etait la moitie du probleme.
// ----------------------------------------------------------------------
const DEFAULT_CORS_ORIGINS = 'http://localhost,http://localhost:80,http://localhost:5173';
const CORS_ORIGINS = (process.env.CORS_ORIGINS || DEFAULT_CORS_ORIGINS)
  .split(',')
  .map((o) => o.trim())
  .filter(Boolean);

const app = express();
app.use(express.json({ limit: '1mb' }));
app.use(cors({ origin: CORS_ORIGINS }));

const httpServer = createServer(app);
const io = new Server(httpServer, {
  cors: { origin: CORS_ORIGINS, methods: ['GET', 'POST'] },
});

// ----------------------------------------------------------------------
// PostgreSQL pool — persistance des messages chat
// ----------------------------------------------------------------------
if (!process.env.POSTGRES_PASSWORD) {
  arret('POSTGRES_PASSWORD absent : demarrage refuse');
}
const pgPool = new Pool({
  host: process.env.POSTGRES_HOST || 'localhost',
  port: parseInt(process.env.POSTGRES_PORT || '5432', 10),
  database: process.env.POSTGRES_DB || 'jurika_db',
  user: process.env.POSTGRES_USER || 'jurika_user',
  // Chantier secrets-z440 : obligatoire (verifie au demarrage, plus bas).
  password: process.env.POSTGRES_PASSWORD,
  max: 5,
});

async function ensureChatSchema() {
  try {
    await pgPool.query(`
      CREATE TABLE IF NOT EXISTS chat_conversations (
          id UUID PRIMARY KEY,
          workspace_id UUID NOT NULL,
          participant_a UUID NOT NULL,
          participant_b UUID NOT NULL,
          last_message_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
          created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
          UNIQUE (workspace_id, participant_a, participant_b)
      );
      CREATE TABLE IF NOT EXISTS chat_messages (
          id UUID PRIMARY KEY,
          workspace_id UUID NOT NULL,
          conversation_id UUID NOT NULL REFERENCES chat_conversations(id) ON DELETE CASCADE,
          sender_id UUID NOT NULL,
          recipient_id UUID NOT NULL,
          body TEXT NOT NULL,
          read_at TIMESTAMPTZ,
          created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
      );
      CREATE INDEX IF NOT EXISTS idx_chat_msgs_conv
          ON chat_messages (conversation_id, created_at DESC);
      -- NOTE V9 (2026-06-25) : la table dossier_transfers + ses endpoints REST
      -- ont ete RETIRES du realtime-service. Le transfert de dossier est
      -- desormais authoritatif dans ticket-service (responsable_id durable +
      -- reassignation des tickets OUVERTS + audit DOSSIER_TRANSFERE), seul
      -- proprietaire transactionnel de entreprise_dossiers + tickets.

      -- BUG 10 (2026-06-08) — notifications in-app (persistantes + push WS).
      -- Pas de FK vers users (mono-source = auth-service) : on stocke
      -- workspace_id + user_id en plat pour eviter le couplage cross-service.
      CREATE TABLE IF NOT EXISTS notifications (
          id UUID PRIMARY KEY,
          workspace_id UUID NOT NULL,
          user_id UUID NOT NULL,
          type VARCHAR(64) NOT NULL,
          title TEXT NOT NULL,
          message TEXT NOT NULL,
          action_url TEXT,
          metadata JSONB,
          read_at TIMESTAMPTZ,
          created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
      );
      CREATE INDEX IF NOT EXISTS idx_notifications_user_unread
          ON notifications (user_id, created_at DESC) WHERE read_at IS NULL;
      CREATE INDEX IF NOT EXISTS idx_notifications_user_all
          ON notifications (user_id, created_at DESC);

      -- BUG 11 (2026-06-08) — preferences notifications par type (opt-in/opt-out).
      -- Absence = enabled true par defaut (fail-open). Cle composite user+type.
      CREATE TABLE IF NOT EXISTS notification_preferences (
          user_id UUID NOT NULL,
          type VARCHAR(64) NOT NULL,
          enabled BOOLEAN NOT NULL DEFAULT TRUE,
          updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
          PRIMARY KEY (user_id, type)
      );
    `);
    console.log('Chat / notifications schema ensured');
  } catch (err) {
    console.error('Chat schema bootstrap failed:', err.message);
  }
}
ensureChatSchema().catch(() => {});

// ----------------------------------------------------------------------
// BUG 10 (2026-06-08) — helpers notifications
// ----------------------------------------------------------------------
const NOTIFICATION_TYPES = [
  'TICKET_ASSIGNED',
  'DOSSIER_TRANSFER',
  'DEADLINE_DUE',
  'CHAT_MESSAGE',
  'PAYMENT_VALIDATED',
  'WORKFLOW_COMPLETED',
  'WORKSPACE_EVENT',
  // Lot L5 (E9, RG-COM-08) : nouvelle demande du client, requete repondue,
  // quota du forfait approchant.
  'DEMANDE_CLIENT',
  'QUOTA_APPROCHANT',
];

async function isTypeEnabled(userId, type) {
  try {
    const { rows } = await pgPool.query(
      `SELECT enabled FROM notification_preferences WHERE user_id = $1 AND type = $2`,
      [userId, type]
    );
    if (!rows.length) return true; // default opt-in
    return rows[0].enabled === true;
  } catch {
    return true; // fail-open : un crash de pref ne doit pas bloquer une notif
  }
}

async function createNotification({ workspaceId, userId, type, title, message, actionUrl, metadata }) {
  if (!workspaceId || !userId || !type || !title || !message) {
    throw new Error('createNotification : workspaceId/userId/type/title/message requis');
  }
  // Lot L5 (E9, RG-COM-08) : seuls un employe ou le superviseur DU cabinet de
  // la notification la recoivent dans l'application. Client (averti par
  // courriel, RG-COM-09), inconnu ou membre d'un autre cabinet : refus.
  const controle = await messagerie.controlerDestinataireNotification(pgPool, workspaceId, userId);
  if (!controle.ok) {
    const err = new Error(`Destinataire refusé (${controle.code})`);
    err.code = controle.code;
    err.status = 403;
    throw err;
  }
  if (!(await isTypeEnabled(userId, type))) {
    return null; // user a opt-out
  }
  const id = randomUUID();
  const createdAt = new Date().toISOString();
  await pgPool.query(
    `INSERT INTO notifications(id, workspace_id, user_id, type, title, message,
                                 action_url, metadata, created_at)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)`,
    [id, workspaceId, userId, type, title, message, actionUrl || null,
      metadata ? JSON.stringify(metadata) : null, createdAt]
  );
  const payload = {
    id, workspaceId, userId, type, title, message,
    actionUrl: actionUrl || null,
    metadata: metadata || null,
    readAt: null,
    createdAt,
  };
  io.to(`user:${userId}`).emit('notification:received', payload);
  return payload;
}

// ----------------------------------------------------------------------
// Lot L5 (E9, RG-COM-06) : l'ancienne verification cross-workspace (cache des
// workspaces observes, inconnu = laisse passer) est remplacee par le module
// messagerie.js : destinataire lu dans `users` DU cabinet de l'expediteur,
// role EMPLOYE ou SUPERVISEUR, refus par defaut.
// ----------------------------------------------------------------------


// ----------------------------------------------------------------------
// Redis pub/sub bridge (Spring Boot -> Node -> WS)
// ----------------------------------------------------------------------
const redisSubscriber = redis.createClient({ url: process.env.REDIS_URL || 'redis://localhost:6379' });
const redisPublisher = redis.createClient({ url: process.env.REDIS_URL || 'redis://localhost:6379' });

async function connectRedis() {
  await redisSubscriber.connect();
  await redisPublisher.connect();
  console.log('Redis connected');
  await redisSubscriber.subscribe('jurika:notifications', (message) => {
    try {
      const data = JSON.parse(message);
      if (data.userId) io.to(`user:${data.userId}`).emit('notification', data);
      if (data.workspaceId) io.to(`workspace:${data.workspaceId}`).emit('workspace_event', data);
    } catch (err) {
      console.error('Redis msg error:', err);
    }
  });
}
connectRedis().catch((err) => console.error('Redis bootstrap failed:', err.message));

// ----------------------------------------------------------------------
// JWT auth middleware
// ----------------------------------------------------------------------
io.use((socket, next) => {
  const token = socket.handshake.auth.token;
  if (!token) return next(new Error('Token manquant'));
  try {
    const decoded = jwt.verify(token, verificationKey, { algorithms: VERIFICATION_ALGOS });
    socket.userId = decoded.uid || decoded.sub;
    socket.workspaceId = decoded.wsid || decoded.workspace_id;
    socket.role = decoded.role;
    socket.email = decoded.email;
    socket.identite = messagerie.identiteDuJeton(decoded);
    next();
  } catch {
    next(new Error('Token invalide'));
  }
});

// ----------------------------------------------------------------------
// Socket.io handlers
// ----------------------------------------------------------------------
io.on('connection', (socket) => {
  console.log(`User connected: ${socket.email} (${socket.userId})`);
  socket.join(`user:${socket.userId}`);
  socket.join(`workspace:${socket.workspaceId}`);

  socket.on('ping', () => socket.emit('pong', { timestamp: Date.now() }));

  socket.on('chat:typing', async (payload) => {
    const to = payload && payload.to;
    try {
      const controle = await messagerie.controlerCorrespondant(pgPool, socket.identite, to);
      if (!controle.ok) return; // refus silencieux : simple indicateur de frappe
      io.to(`user:${to}`).emit('chat:typing', { from: socket.userId });
    } catch (err) {
      console.error('chat:typing failed', err.message);
    }
  });

  socket.on('chat:send', async (payload, ack) => {
    const { to, body } = payload || {};
    try {
      // Lot L5 (E9, RG-COM-06) : expediteur EMPLOYE ou SUPERVISEUR, destinataire
      // EMPLOYE ou SUPERVISEUR actif DU MEME cabinet ; tout le reste est refuse.
      // Lot L5b (E1) : meme chemin d'ecriture que la route REST.
      const r = await messagerie.enregistrerMessage(pgPool, socket.identite, to, body);
      if (!r.ok) return ack && ack({ ok: false, error: r.message, code: r.code });
      diffuserMessage(r.message);
      ack && ack({ ok: true, id: r.message.id, conversationId: r.message.conversationId,
        createdAt: r.message.createdAt, message: r.message });
    } catch (err) {
      console.error('chat:send failed', err.message);
      ack && ack({ ok: false, error: 'Le message n’a pas pu être envoyé. Réessayez.' });
    }
  });

  socket.on('chat:read', async ({ conversationId }) => {
    if (!conversationId) return;
    try {
      await pgPool.query(
        `UPDATE chat_messages SET read_at = NOW()
           WHERE conversation_id = $1 AND recipient_id = $2 AND read_at IS NULL`,
        [conversationId, socket.userId]
      );
    } catch (err) {
      console.error('chat:read failed', err.message);
    }
  });

  socket.on('disconnect', () => {
    console.log(`User disconnected: ${socket.email}`);
  });
});

// ----------------------------------------------------------------------
// REST endpoints (chat history, notifications, dossier transfer)
// ----------------------------------------------------------------------
function userFromToken(req) {
  const auth = req.headers.authorization || '';
  const token = auth.startsWith('Bearer ') ? auth.slice(7) : null;
  if (!token) return null;
  try {
    return jwt.verify(token, verificationKey, { algorithms: VERIFICATION_ALGOS });
  } catch {
    return null;
  }
}

/**
 * Lot L5b (E1) : diffusion d'un message DEJA enregistre. Best-effort : le temps reel
 * et la notification ne conditionnent jamais l'envoi (RG-COM-06).
 */
function diffuserMessage(msg) {
  io.to(`user:${msg.to}`).emit('chat:message', msg);
  io.to(`user:${msg.from}`).emit('chat:message', msg);
  // BUG 10 — notification CHAT_MESSAGE pour le destinataire (best-effort).
  createNotification({
    workspaceId: msg.workspaceId,
    userId: msg.to,
    type: 'CHAT_MESSAGE',
    title: `Nouveau message`,
    message: msg.body.length > 120 ? `${msg.body.slice(0, 117)}...` : msg.body,
    actionUrl: '/chat',
    metadata: { from: msg.from, conversationId: msg.conversationId },
  }).catch((err) => console.error('notification CHAT_MESSAGE failed:', err.message));
}

/**
 * Lot L5b (E1, RG-COM-06) : envoi par REST. L'ecran envoie toujours par cette route :
 * le message est enregistre et rendu meme quand la connexion temps reel est coupee ;
 * le temps reel ne sert qu'a la remise immediate.
 */
app.post('/api/v1/chat/messages', async (req, res) => {
  const user = userFromToken(req);
  if (!user) return res.status(401).json({ error: 'Non authentifié' });
  const identite = messagerie.identiteDuJeton(user);
  if (!messagerie.peutUtiliserMessagerie(identite)) {
    return res.status(403).json({ error: messagerie.MESSAGES_REFUS.ROLE_REFUSE, code: 'ROLE_REFUSE' });
  }
  const { to, body } = req.body || {};
  try {
    const r = await messagerie.enregistrerMessage(pgPool, identite, to, body);
    if (!r.ok) {
      const statut = r.code === 'MESSAGE_INVALIDE' || r.code === 'DESTINATAIRE_INVALIDE'
        || r.code === 'DESTINATAIRE_SOI_MEME' ? 400 : 403;
      return res.status(statut).json({ error: r.message, code: r.code });
    }
    diffuserMessage(r.message);
    return res.status(201).json(r.message);
  } catch (err) {
    console.error('POST /api/v1/chat/messages failed', err.message);
    return res.status(500).json({ error: 'Le message n’a pas pu être envoyé. Réessayez.' });
  }
});

app.get('/api/v1/chat/conversations', async (req, res) => {
  const user = userFromToken(req);
  if (!user) return res.status(401).json({ error: 'Non authentifié' });
  // Lot L5 (E9, RG-COM-06) : messagerie reservee au superviseur et aux employes.
  if (!messagerie.peutUtiliserMessagerie(messagerie.identiteDuJeton(user))) {
    return res.status(403).json({ error: messagerie.MESSAGES_REFUS.ROLE_REFUSE, code: 'ROLE_REFUSE' });
  }
  const wsId = user.wsid || user.workspace_id;
  const userId = user.uid || user.sub;
  try {
    const { rows } = await pgPool.query(
      `SELECT c.id, c.workspace_id, c.participant_a, c.participant_b, c.last_message_at,
              (SELECT body FROM chat_messages m
                 WHERE m.conversation_id = c.id ORDER BY m.created_at DESC LIMIT 1) AS last_body,
              (SELECT COUNT(*) FROM chat_messages m
                 WHERE m.conversation_id = c.id AND m.recipient_id = $2 AND m.read_at IS NULL) AS unread
         FROM chat_conversations c
         WHERE c.workspace_id = $1
           AND (c.participant_a = $2 OR c.participant_b = $2)
         ORDER BY c.last_message_at DESC`,
      [wsId, userId]
    );
    // BUG 12 (fix 2026-06-09) — shape attendu par le frontend (Conversation
    // type) : peerId calcule (l'autre participant), lastMessage/lastMessageAt
    // camelCase, unreadCount integer. L'ancien renvoi rows brut renvoyait
    // participant_a/_b sans peerId -> ChatPage.peerInitials(undefined.slice)
    // crashait des qu'une vraie conversation existait en base.
    const conversations = rows.map((r) => ({
      id: r.id,
      workspaceId: r.workspace_id,
      peerId: r.participant_a === userId ? r.participant_b : r.participant_a,
      lastMessage: r.last_body ?? null,
      lastMessageAt: r.last_message_at,
      unreadCount: typeof r.unread === 'number' ? r.unread : parseInt(r.unread, 10) || 0,
    }));
    res.json(conversations);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

app.get('/api/v1/chat/conversations/:id/messages', async (req, res) => {
  const user = userFromToken(req);
  if (!user) return res.status(401).json({ error: 'Non authentifié' });
  // Lot L5 (E9, RG-COM-06) : messagerie reservee au superviseur et aux employes.
  if (!messagerie.peutUtiliserMessagerie(messagerie.identiteDuJeton(user))) {
    return res.status(403).json({ error: messagerie.MESSAGES_REFUS.ROLE_REFUSE, code: 'ROLE_REFUSE' });
  }
  const userId = user.uid || user.sub;
  try {
    const { rows } = await pgPool.query(
      `SELECT m.id, m.conversation_id, m.workspace_id,
              m.sender_id, m.recipient_id, m.body, m.created_at, m.read_at
         FROM chat_messages m
         JOIN chat_conversations c ON c.id = m.conversation_id
         WHERE c.id = $1 AND (c.participant_a = $2 OR c.participant_b = $2)
         ORDER BY m.created_at ASC
         LIMIT 200`,
      [req.params.id, userId]
    );
    // BUG 12 (fix 2026-06-09) — meme shape que l'event chat:message Socket.io
    // (from/to/createdAt camelCase) au lieu de sender_id/recipient_id snake.
    const messages = rows.map((r) => ({
      id: r.id,
      conversationId: r.conversation_id,
      workspaceId: r.workspace_id,
      from: r.sender_id,
      to: r.recipient_id,
      body: r.body,
      createdAt: r.created_at,
      readAt: r.read_at,
    }));
    res.json(messages);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ----------------------------------------------------------------------
// NOTE V9 (2026-06-25) — les endpoints /api/v1/transfers* ont ete RETIRES.
// Le transfert de dossier est desormais authoritatif dans ticket-service
// (POST /api/v1/dossiers/{id}/transfer-requests, /accept, /reject, /cancel,
// /transfer + GET /api/v1/dossier-transfer-requests), seul service capable
// d'appliquer responsable_id + reassignation des tickets + audit.
// Le realtime-service ne sert plus qu'a relayer les events socket
// transfer:received / transfer:accepted / transfer:rejected (via /emit).
// ----------------------------------------------------------------------

// ----------------------------------------------------------------------
// BUG 10 (2026-06-08) — REST notifications (vue cloche + drawer)
// ----------------------------------------------------------------------
app.get('/api/v1/notifications', async (req, res) => {
  const user = userFromToken(req);
  if (!user) return res.status(401).json({ error: 'Non authentifié' });
  const userId = user.uid || user.sub;
  const wsId = user.wsid || user.workspace_id;
  const limit = Math.min(parseInt(req.query.limit, 10) || 50, 200);
  const onlyUnread = req.query.unread === 'true' || req.query.unread === '1';
  try {
    const { rows } = await pgPool.query(
      `SELECT id, workspace_id, user_id, type, title, message,
              action_url, metadata, read_at, created_at
         FROM notifications
        WHERE user_id = $1 AND workspace_id = $2
          ${onlyUnread ? 'AND read_at IS NULL' : ''}
        ORDER BY created_at DESC
        LIMIT $3`,
      [userId, wsId, limit]
    );
    res.json({
      items: rows.map((r) => ({
        id: r.id,
        workspaceId: r.workspace_id,
        userId: r.user_id,
        type: r.type,
        title: r.title,
        message: r.message,
        actionUrl: r.action_url,
        metadata: r.metadata,
        readAt: r.read_at,
        createdAt: r.created_at,
      })),
      total: rows.length,
    });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

app.get('/api/v1/notifications/unread-count', async (req, res) => {
  const user = userFromToken(req);
  if (!user) return res.status(401).json({ error: 'Non authentifié' });
  const userId = user.uid || user.sub;
  const wsId = user.wsid || user.workspace_id;
  try {
    const { rows } = await pgPool.query(
      `SELECT COUNT(*)::int AS c FROM notifications
        WHERE user_id = $1 AND workspace_id = $2 AND read_at IS NULL`,
      [userId, wsId]
    );
    res.json({ count: rows[0].c });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

app.patch('/api/v1/notifications/:id/read', async (req, res) => {
  const user = userFromToken(req);
  if (!user) return res.status(401).json({ error: 'Non authentifié' });
  const userId = user.uid || user.sub;
  try {
    const { rows } = await pgPool.query(
      `UPDATE notifications SET read_at = NOW()
        WHERE id = $1 AND user_id = $2 AND read_at IS NULL
        RETURNING id, read_at`,
      [req.params.id, userId]
    );
    if (!rows.length) return res.json({ id: req.params.id, alreadyRead: true });
    res.json({ id: rows[0].id, readAt: rows[0].read_at });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

app.patch('/api/v1/notifications/read-all', async (req, res) => {
  const user = userFromToken(req);
  if (!user) return res.status(401).json({ error: 'Non authentifié' });
  const userId = user.uid || user.sub;
  const wsId = user.wsid || user.workspace_id;
  try {
    const { rowCount } = await pgPool.query(
      `UPDATE notifications SET read_at = NOW()
        WHERE user_id = $1 AND workspace_id = $2 AND read_at IS NULL`,
      [userId, wsId]
    );
    res.json({ updated: rowCount });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

app.get('/api/v1/notifications/preferences', async (req, res) => {
  const user = userFromToken(req);
  if (!user) return res.status(401).json({ error: 'Non authentifié' });
  const userId = user.uid || user.sub;
  try {
    const { rows } = await pgPool.query(
      `SELECT type, enabled FROM notification_preferences WHERE user_id = $1`,
      [userId]
    );
    const map = Object.fromEntries(rows.map((r) => [r.type, r.enabled]));
    // Defaut opt-in pour les types non encore positionnes
    const out = {};
    for (const t of NOTIFICATION_TYPES) {
      out[t] = t in map ? map[t] : true;
    }
    res.json({ preferences: out, knownTypes: NOTIFICATION_TYPES });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

app.patch('/api/v1/notifications/preferences', async (req, res) => {
  const user = userFromToken(req);
  if (!user) return res.status(401).json({ error: 'Non authentifié' });
  const userId = user.uid || user.sub;
  const updates = req.body && req.body.preferences;
  if (!updates || typeof updates !== 'object') {
    return res.status(400).json({ error: 'Préférences requises (objet associant chaque type à vrai ou faux).' });
  }
  try {
    for (const [type, enabled] of Object.entries(updates)) {
      if (!NOTIFICATION_TYPES.includes(type)) continue;
      await pgPool.query(
        `INSERT INTO notification_preferences(user_id, type, enabled, updated_at)
           VALUES ($1, $2, $3, NOW())
         ON CONFLICT (user_id, type)
           DO UPDATE SET enabled = EXCLUDED.enabled, updated_at = NOW()`,
        [userId, type, Boolean(enabled)]
      );
    }
    res.json({ updated: Object.keys(updates).length });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

/**
 * BUG 10 — endpoint INTERNE pour les microservices Java (publier une notif).
 * Garde-fou : on accepte un X-Internal-Token optionnel s'il est configure.
 * Sinon, n'importe quel caller peut publier — sufficant en MVP local mais
 * a serrer en prod (cf TODO infra). Le frontend N'utilise PAS cet endpoint.
 */
app.post('/internal/notifications', async (req, res) => {
  const internalToken = process.env.INTERNAL_TOKEN;
  if (internalToken && req.headers['x-internal-token'] !== internalToken) {
    return res.status(403).json({ error: 'X-Internal-Token invalide' });
  }
  const { workspaceId, userId, type, title, message, actionUrl, metadata } = req.body || {};
  try {
    const payload = await createNotification({
      workspaceId, userId, type, title, message, actionUrl, metadata,
    });
    if (!payload) return res.status(202).json({ skipped: true, reason: 'OPTED_OUT' });
    res.status(201).json(payload);
  } catch (err) {
    // Lot L5 (E9) : un destinataire refuse est journalise (jamais en silence).
    if (err.status === 403) {
      console.warn(`[realtime] notification ${type} refusee pour ${userId} (ws ${workspaceId}) : ${err.code}`);
      return res.status(403).json({ error: err.message, code: err.code });
    }
    res.status(400).json({ error: err.message });
  }
});

/**
 * BUG 10 — endpoint de dev pour declencher manuellement une notif test :
 * accepte un JWT user normal et publie une notif type WORKSPACE_EVENT sur
 * le user courant. Utile pour les e2e Playwright/node — pas pour la prod.
 */
app.post('/api/v1/notifications/self-test', async (req, res) => {
  const user = userFromToken(req);
  if (!user) return res.status(401).json({ error: 'Non authentifié' });
  const userId = user.uid || user.sub;
  const wsId = user.wsid || user.workspace_id;
  try {
    const payload = await createNotification({
      workspaceId: wsId,
      userId,
      type: req.body?.type || 'WORKSPACE_EVENT',
      title: req.body?.title || 'Notification de test',
      message: req.body?.message || 'Ceci est un push e2e.',
      actionUrl: req.body?.actionUrl || null,
      metadata: req.body?.metadata || null,
    });
    if (!payload) return res.status(202).json({ skipped: true, reason: 'OPTED_OUT' });
    res.status(201).json(payload);
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

// Generic emit endpoint for Spring Boot services
app.post('/emit', (req, res) => {
  const { userId, workspaceId, event, data } = req.body;
  if (userId) io.to(`user:${userId}`).emit(event, data);
  if (workspaceId) io.to(`workspace:${workspaceId}`).emit(event, data);
  res.json({ success: true, event });
});

app.get('/health', (req, res) => {
  res.json({
    status: 'UP',
    connections: io.engine.clientsCount,
    timestamp: new Date().toISOString(),
  });
});

app.get('/rooms', (req, res) => {
  const rooms = {};
  io.sockets.adapter.rooms.forEach((value, key) => {
    rooms[key] = value.size;
  });
  res.json(rooms);
});

const PORT = process.env.PORT || 3000;
httpServer.listen(PORT, () => {
  console.log(`JURIKA Realtime Service listening on port ${PORT}`);
});

module.exports = { app, io };
