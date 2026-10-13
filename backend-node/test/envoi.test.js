/**
 * Lot L5b (E1) -- RG-COM-06 : un message est ENREGISTRE des son envoi, par le meme
 * chemin que l'envoi passe par le temps reel ou par la route REST ; un envoi refuse
 * n'ecrit rien. Lancement : `npm test`.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const { enregistrerMessage } = require('../src/messagerie');

const CABINET_A = '11111111-1111-1111-1111-111111111111';
const CABINET_B = '22222222-2222-2222-2222-222222222222';
const EMPLOYE_A = 'aaaaaaaa-0000-0000-0000-000000000002';
const SUPERVISEUR_A = 'aaaaaaaa-0000-0000-0000-000000000001';
const EMPLOYE_B = 'bbbbbbbb-0000-0000-0000-000000000001';

const USERS = [
  { id: SUPERVISEUR_A, workspace_id: CABINET_A, role: 'SUPERVISEUR', status: 'ACTIVE' },
  { id: EMPLOYE_A, workspace_id: CABINET_A, role: 'EMPLOYE', status: 'ACTIVE' },
  { id: EMPLOYE_B, workspace_id: CABINET_B, role: 'EMPLOYE', status: 'ACTIVE' },
];

/** Faux pool : tables en memoire chat_conversations et chat_messages. */
function fauxPool() {
  const conversations = [];
  const messages = [];
  async function query(sql, params) {
    if (sql.includes('FROM users')) {
      const [id, ws] = params;
      return { rows: USERS.filter((u) => u.id === id && u.workspace_id === ws) };
    }
    if (sql.includes('SELECT id FROM chat_conversations')) {
      const [ws, a, b] = params;
      return { rows: conversations.filter((c) => c.ws === ws && c.a === a && c.b === b) };
    }
    if (sql.includes('INSERT INTO chat_conversations')) {
      const [id, ws, a, b] = params;
      conversations.push({ id, ws, a, b });
      return { rows: [] };
    }
    if (sql.includes('INSERT INTO chat_messages')) {
      const [id, ws, conv, from, to, body, createdAt] = params;
      messages.push({ id, ws, conv, from, to, body, createdAt });
      return { rows: [] };
    }
    return { rows: [] };
  }
  return {
    conversations,
    messages,
    query,
    async connect() {
      return { query, release() {} };
    },
  };
}

const employeA = { userId: EMPLOYE_A, workspaceId: CABINET_A, role: 'EMPLOYE' };

test('un envoi accepte est enregistre et rendu, conversation creee une seule fois', async () => {
  const db = fauxPool();
  const r1 = await enregistrerMessage(db, employeA, SUPERVISEUR_A, 'Bonjour');
  const r2 = await enregistrerMessage(db, employeA, SUPERVISEUR_A, 'Le dossier est pret.');
  assert.equal(r1.ok, true);
  assert.equal(r2.ok, true);
  assert.equal(db.messages.length, 2);
  assert.equal(db.conversations.length, 1);
  assert.equal(r1.message.conversationId, r2.message.conversationId);
  assert.deepEqual(
    { from: r1.message.from, to: r1.message.to, body: r1.message.body, ws: r1.message.workspaceId },
    { from: EMPLOYE_A, to: SUPERVISEUR_A, body: 'Bonjour', ws: CABINET_A }
  );
  assert.equal(db.messages[0].id, r1.message.id);
});

test('un envoi refuse n ecrit rien', async () => {
  const db = fauxPool();
  const r = await enregistrerMessage(db, employeA, EMPLOYE_B, 'Bonjour');
  assert.equal(r.ok, false);
  assert.equal(r.code, 'DESTINATAIRE_INCONNU');
  assert.equal(db.messages.length, 0);
  assert.equal(db.conversations.length, 0);
});

test('un message vide est refuse sans ecriture', async () => {
  const db = fauxPool();
  const r = await enregistrerMessage(db, employeA, SUPERVISEUR_A, '   ');
  assert.equal(r.code, 'MESSAGE_INVALIDE');
  assert.equal(db.messages.length, 0);
});
