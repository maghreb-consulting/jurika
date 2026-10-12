/**
 * Lot L5 (E9) -- RG-COM-06 / RG-COM-08 : roles, cabinet, refus par defaut.
 * Lancement : `npm test` (node --test, aucune dependance).
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const {
  controlerEnvoi,
  controlerCorrespondant,
  controlerDestinataireNotification,
  identiteDuJeton,
  peutUtiliserMessagerie,
} = require('../src/messagerie');

const CABINET_A = '11111111-1111-1111-1111-111111111111';
const CABINET_B = '22222222-2222-2222-2222-222222222222';
const SUPERVISEUR_A = 'aaaaaaaa-0000-0000-0000-000000000001';
const EMPLOYE_A = 'aaaaaaaa-0000-0000-0000-000000000002';
const CLIENT_A = 'aaaaaaaa-0000-0000-0000-000000000003';
const INACTIF_A = 'aaaaaaaa-0000-0000-0000-000000000004';
const EMPLOYE_B = 'bbbbbbbb-0000-0000-0000-000000000001';
const INCONNU = 'cccccccc-0000-0000-0000-000000000001';

const USERS = [
  { id: SUPERVISEUR_A, workspace_id: CABINET_A, role: 'SUPERVISEUR', status: 'ACTIVE' },
  { id: EMPLOYE_A, workspace_id: CABINET_A, role: 'EMPLOYE', status: 'ACTIVE' },
  { id: CLIENT_A, workspace_id: CABINET_A, role: 'CLIENT', status: 'ACTIVE' },
  { id: INACTIF_A, workspace_id: CABINET_A, role: 'EMPLOYE', status: 'INACTIVE' },
  { id: EMPLOYE_B, workspace_id: CABINET_B, role: 'EMPLOYE', status: 'ACTIVE' },
];

/**
 * Faux pool pg : applique le filtre (id, workspace_id) comme la requete reelle
 * et memorise les requetes (dont le workspace pose pour la RLS).
 */
function fauxPool() {
  const journal = [];
  return {
    journal,
    async connect() {
      return {
        async query(sql, params) {
          journal.push({ sql, params });
          if (sql.includes('FROM users')) {
            const [id, ws] = params;
            return { rows: USERS.filter((u) => u.id === id && u.workspace_id === ws) };
          }
          return { rows: [] };
        },
        release() {},
      };
    },
  };
}

const employeA = { userId: EMPLOYE_A, workspaceId: CABINET_A, role: 'EMPLOYE' };
const superviseurA = { userId: SUPERVISEUR_A, workspaceId: CABINET_A, role: 'SUPERVISEUR' };
const clientA = { userId: CLIENT_A, workspaceId: CABINET_A, role: 'CLIENT' };

test('un employe ecrit au superviseur de son cabinet', async () => {
  assert.deepEqual(await controlerEnvoi(fauxPool(), employeA, SUPERVISEUR_A, 'Bonjour'), { ok: true });
});

test('le superviseur ecrit a un employe de son cabinet', async () => {
  assert.deepEqual(await controlerEnvoi(fauxPool(), superviseurA, EMPLOYE_A, 'Bonjour'), { ok: true });
});

test('le client n a pas acces a la messagerie', async () => {
  const r = await controlerEnvoi(fauxPool(), clientA, EMPLOYE_A, 'Bonjour');
  assert.equal(r.ok, false);
  assert.equal(r.code, 'ROLE_REFUSE');
  assert.equal(peutUtiliserMessagerie(clientA), false);
});

test('un role inconnu ou absent est refuse', async () => {
  for (const role of [undefined, null, 'SUPER_ADMIN', 'ADMIN', 'employe']) {
    const r = await controlerEnvoi(fauxPool(), { ...employeA, role }, SUPERVISEUR_A, 'x');
    assert.equal(r.code, 'ROLE_REFUSE', `role ${role}`);
  }
});

test('on ne peut pas ecrire a un client du cabinet', async () => {
  const r = await controlerEnvoi(fauxPool(), employeA, CLIENT_A, 'Bonjour');
  assert.equal(r.code, 'DESTINATAIRE_NON_AUTORISE');
});

test('aucun message ne traverse deux cabinets', async () => {
  const r = await controlerEnvoi(fauxPool(), employeA, EMPLOYE_B, 'Bonjour');
  assert.equal(r.code, 'DESTINATAIRE_INCONNU');
});

test('un destinataire inconnu est refuse (refus par defaut)', async () => {
  const r = await controlerEnvoi(fauxPool(), employeA, INCONNU, 'Bonjour');
  assert.equal(r.code, 'DESTINATAIRE_INCONNU');
});

test('un identifiant mal forme est refuse sans requete', async () => {
  const pool = fauxPool();
  const r = await controlerEnvoi(pool, employeA, "x' OR 1=1 --", 'Bonjour');
  assert.equal(r.code, 'DESTINATAIRE_INVALIDE');
  assert.equal(pool.journal.length, 0);
});

test('un compte desactive ne recoit plus de message', async () => {
  const r = await controlerEnvoi(fauxPool(), employeA, INACTIF_A, 'Bonjour');
  assert.equal(r.code, 'DESTINATAIRE_INACTIF');
});

test('on ne s ecrit pas a soi-meme', async () => {
  const r = await controlerEnvoi(fauxPool(), employeA, EMPLOYE_A, 'Bonjour');
  assert.equal(r.code, 'DESTINATAIRE_SOI_MEME');
});

test('message vide ou trop long refuse', async () => {
  assert.equal((await controlerEnvoi(fauxPool(), employeA, SUPERVISEUR_A, '   ')).code, 'MESSAGE_INVALIDE');
  assert.equal((await controlerEnvoi(fauxPool(), employeA, SUPERVISEUR_A, 'a'.repeat(4001))).code, 'MESSAGE_INVALIDE');
  assert.equal((await controlerEnvoi(fauxPool(), employeA, SUPERVISEUR_A, 42)).code, 'MESSAGE_INVALIDE');
});

test('la lecture du membre pose le cabinet pour la RLS et filtre sur lui', async () => {
  const pool = fauxPool();
  await controlerCorrespondant(pool, employeA, SUPERVISEUR_A);
  const pose = pool.journal.find((q) => q.sql.includes('set_config'));
  assert.deepEqual(pose.params, [CABINET_A]);
  const lecture = pool.journal.find((q) => q.sql.includes('FROM users'));
  assert.match(lecture.sql, /workspace_id = \$2/);
  assert.deepEqual(lecture.params, [SUPERVISEUR_A, CABINET_A]);
});

test('notification : employe et superviseur du cabinet acceptes', async () => {
  assert.deepEqual(await controlerDestinataireNotification(fauxPool(), CABINET_A, EMPLOYE_A), { ok: true });
  assert.deepEqual(await controlerDestinataireNotification(fauxPool(), CABINET_A, SUPERVISEUR_A), { ok: true });
});

test('notification : le client n est pas notifie dans l application', async () => {
  const r = await controlerDestinataireNotification(fauxPool(), CABINET_A, CLIENT_A);
  assert.equal(r.code, 'DESTINATAIRE_NON_AUTORISE');
});

test('notification : aucune notification vers un autre cabinet', async () => {
  const r = await controlerDestinataireNotification(fauxPool(), CABINET_A, EMPLOYE_B);
  assert.equal(r.code, 'DESTINATAIRE_INCONNU');
});

test('identite lue dans les claims du jeton', () => {
  assert.deepEqual(identiteDuJeton({ uid: EMPLOYE_A, wsid: CABINET_A, role: 'EMPLOYE' }), employeA);
  assert.deepEqual(identiteDuJeton({ sub: EMPLOYE_A, workspace_id: CABINET_A, role: 'EMPLOYE' }), employeA);
  assert.equal(identiteDuJeton(null), null);
});
