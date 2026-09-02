/**
 * AUDIT — variables non résolues dans les documents générés (2026-08-14).
 *
 * Le moteur `DocxTemplateEngine` remplace toute variable absente du payload par
 * un marqueur rouge « ‹ VALEUR MANQUANTE : $VAR › ». Ce script génère TOUS les
 * modèles de chaque workflow — y compris les optionnels — à partir du payload
 * réellement construit par le front, puis extrait le texte du .docx et relève
 * ces marqueurs.
 *
 * Chaque marqueur est un champ que l'employé ne peut renseigner nulle part, ou
 * qu'on aurait dû lire en base. C'est la liste de travail.
 *
 * Usage : node scripts/audit-documents-variables-manquantes.mjs
 * Pré-requis : stack démarrée (gateway 8080), endpoint de seed actif.
 */
import { writeFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';

const BASE = process.env.JURIKA_BASE ?? 'http://localhost:8080';
const MARKER = /‹\s*VALEUR MANQUANTE\s*:\s*([^›]+)›/g;

async function http(method, path, { token, body, raw } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  const res = await fetch(`${BASE}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (raw) return { ok: res.ok, status: res.status, buf: Buffer.from(await res.arrayBuffer()) };
  let parsed = null;
  try { parsed = await res.json(); } catch { /* corps vide */ }
  return { ok: res.ok, status: res.status, body: parsed };
}

/**
 * Texte brut d'un .docx. On évite toute dépendance : un .docx est un ZIP, et
 * `word/document.xml` porte le texte dans des <w:t>. PowerShell fournit
 * l'extraction ZIP sans installer quoi que ce soit.
 */
function docxText(buf) {
  const tmp = `${process.env.TEMP ?? '.'}\\jurika-audit-${Date.now()}.docx`;
  writeFileSync(tmp, buf);
  const ps = `
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $z = [System.IO.Compression.ZipFile]::OpenRead('${tmp.replace(/\\/g, '\\\\')}')
    $e = $z.Entries | Where-Object { $_.FullName -eq 'word/document.xml' }
    $r = New-Object System.IO.StreamReader($e.Open())
    $r.ReadToEnd()
    $z.Dispose()
  `;
  try {
    const xml = execFileSync('powershell', ['-NoProfile', '-Command', ps], {
      encoding: 'utf8', maxBuffer: 64 * 1024 * 1024,
    });
    return xml.replace(/<[^>]+>/g, '');
  } catch {
    return '';
  }
}

const WORKFLOWS = [
  'CREATION_SARL', 'MODIFICATION', 'DISSOLUTION', 'LIQUIDATION',
  'SUCCURSALE_MA', 'SUCCURSALE_ETR', 'FERMETURE_SUCCURSALE',
  'PV_AGO', 'SEANCE_AG', 'INCIDENT_SEANCE',
];

/**
 * Payload « complet » représentatif.
 *
 * ATTENTION — un payload incomplet produit des FAUX POSITIFS. Chaque mapper lit
 * une forme précise, et une variable non résolue peut n'être qu'une clé absente
 * de ce jeu d'essai, pas un défaut du produit. Deux cas rencontrés :
 *
 *   * `STATUTS_REFONDUS_*` lit les associés dans `ficheStructuree`, PAS à la
 *     racine — sans elle, les 11 variables d'associé sortent « manquantes » ;
 *   * `RAPPORT_GESTION` lit `exercice` / `gerant` / `signature`, pas `approbation`.
 *
 * D'où les blocs ci-dessous. Toute variable signalée doit être reconfirmée avec
 * la forme réellement envoyée par le front avant d'être qualifiée de défaut.
 */
function payloadComplet() {
  const associes = [
    { typePersonne: 'PHYSIQUE', civilite: 'M.', prenom: 'Ahmed', nom: 'ALAOUI',
      adresse: '12 rue des Foules, Casablanca', nombreParts: '600', nombreVoix: '600',
      presence: 'présent', mandataireNom: '', cin: 'BK123456' },
    { typePersonne: 'PHYSIQUE', civilite: 'Mme', prenom: 'Fatima', nom: 'BENNANI',
      adresse: '8 avenue Hassan II, Rabat', nombreParts: '400', nombreVoix: '400',
      presence: 'présent', mandataireNom: '', cin: 'BK654321' },
  ];
  const gerants = [{ civilite: 'M.', prenom: 'Ahmed', nom: 'ALAOUI' }];
  return {
    societe: {
      denomination: 'PARACOSME', formeJuridique: 'SARL',
      capitalChiffres: '100000', capitalLettres: 'cent mille',
      siegeSocial: '25 BD ANFA, CASABLANCA', nombreParts: '1000',
      rcNumero: '123456', villeGreffe: 'CASABLANCA', ice: '001234567000001',
      identifiantFiscal: '12345678', patente: '87654321', cnss: '9876543',
    },
    seance: {
      type: 'extraordinaire', date: '2026-08-20', heure: '10:00',
      lieu: '25 BD ANFA, CASABLANCA', heureCloture: '12:00',
      presidentNom: 'Ahmed ALAOUI', presidentQualite: 'Gérant',
      secretairePresent: 'non', secretaireNom: '',
    },
    convocation: {
      auteur: 'la gérance', date: '2026-08-01', heure: '09:00',
      mode: 'lettre recommandée avec accusé de réception', rang: 'première',
      dateAgPremiere: '', lieuSignature: 'Casablanca', irregulariteNature: '',
    },
    ordreDuJour: ['Premier point', 'Second point'],
    documentsJoints: ['Texte des résolutions proposées'],
    resolutions: [],
    associes, gerants,
    liquidateur: {
      civilite: 'M.', prenom: 'Ahmed', nom: 'ALAOUI', cin: 'BK123456',
      adresse: '12 rue des Foules, Casablanca', siege: '45 BD ZERKTOUNI, CASABLANCA',
      remuneration: 'exercées à titre gratuit',
    },
    succursale: {
      enseigne: 'PARACOSME — Agence Marrakech', adresse: '5 Avenue Mohammed VI',
      ville: 'Marrakech', villeGreffe: 'MARRAKECH', activite: 'Conseil',
      rcNumero: '78901', dateOuverture: '2026-09-01', dateFermeture: '2026-10-31',
      motif: 'la reorganisation du reseau commercial',
    },
    dissolution: { motif: 'Cessation volontaire de l\'activite', dateEffet: '2026-08-20' },
    comptesFinaux: { totalActif: 500000, totalPassif: 300000, devise: 'MAD' },
    approbation: {
      exerciceClosDate: '2026-12-31', resultatType: 'bénéfice', resultatNet: 150000,
      affectations: [
        { libelle: 'Réserve légale', montant: 7500 },
        { libelle: 'Report à nouveau', montant: 142500 },
      ],
      dividendeDistribue: 'non', quitusGerance: 'oui', conventionsReglementees: 'non',
    },
    // Rapport de gestion — forme lue par AnnualReportMapper.
    exercice: {
      dateCloture: '2026-12-31', resultatNet: 150000, chiffreAffaires: 2400000,
      reserveLegale: 7500, reportANouveau: 142500, dividendes: 0,
      commentaireActivite: 'Activite en croissance sur l\'exercice.',
      evenementsPerspectives: 'Ouverture prevue a Marrakech.',
    },
    gerant: { nom: 'Ahmed ALAOUI' },
    signature: { lieuDateEmission: 'Casablanca, le 20 aout 2026' },
    // Statuts refondus — la source est la fiche structuree du dossier.
    ficheStructuree: {
      denomination: 'PARACOSME', formeJuridique: 'SARL', adresseSiege: '25 BD ANFA, CASABLANCA',
      ville: 'CASABLANCA', capitalSocial: 100000, nombreParts: 1000, valeurNominale: 100,
      dureeAnnees: 99, objetSocial: 'Conseil aux entreprises', rcNumero: '123456',
      ice: '001234567000001', associes, gerants,
    },
  };
}

async function main() {
  console.log(`Audit des variables manquantes — ${BASE}\n`);

  const seed = await http('POST', '/api/v1/test/seed/workspace?role=EMPLOYE', { body: {} });
  if (!seed.ok) { console.error('Seed impossible :', seed.status, seed.body); process.exit(1); }
  const login = await http('POST', '/api/v1/auth/login', {
    body: {
      workspaceCode: seed.body.workspaceCode,
      email: seed.body.adminEmail,
      password: seed.body.adminPassword,
    },
  });
  if (!login.ok) { console.error('Login impossible :', login.status); process.exit(1); }
  const token = login.body.accessToken;

  const payload = payloadComplet();
  const rapport = [];

  for (const wf of WORKFLOWS) {
    const tpls = await http('GET', `/api/v1/ai/workflows/${wf}/templates`, { token });
    if (!tpls.ok || !Array.isArray(tpls.body)) {
      console.log(`  ! ${wf} : liste de modeles indisponible (${tpls.status})`);
      continue;
    }
    console.log(`\n=== ${wf} — ${tpls.body.length} modele(s) ===`);
    for (const tpl of tpls.body) {
      const gen = await http('POST', `/api/v1/ai/workflows/${wf}/documents/${tpl.code}`, {
        token, body: payload, raw: true,
      });
      if (!gen.ok) {
        console.log(`  [ERR ${gen.status}] ${tpl.code}`);
        rapport.push({ workflow: wf, template: tpl.code, erreur: gen.status });
        continue;
      }
      const texte = docxText(gen.buf);
      const manquantes = [...new Set([...texte.matchAll(MARKER)].map((m) => m[1].trim()))]
        // Les modèles directeur portent un formulaire de saisie qui EXPLIQUE le
        // marqueur (« Chaque variable ‹ VALEUR MANQUANTE : ... › est remplacée… »).
        // Ce n'est pas une variable non résolue : on l'écarte.
        .filter((v) => v !== '...' && v.length > 0);
      if (manquantes.length === 0) {
        console.log(`  [OK ] ${tpl.code}`);
      } else {
        console.log(`  [${String(manquantes.length).padStart(2)} ] ${tpl.code} -> ${manquantes.join(', ')}`);
      }
      rapport.push({ workflow: wf, template: tpl.code, manquantes });
    }
  }

  writeFileSync('.tmp/audit-variables-manquantes.json', JSON.stringify(rapport, null, 2), 'utf8');

  const total = rapport.reduce((n, r) => n + (r.manquantes?.length ?? 0), 0);
  const parVariable = {};
  rapport.forEach((r) => (r.manquantes ?? []).forEach((v) => {
    parVariable[v] = (parVariable[v] ?? 0) + 1;
  }));
  console.log('\n=== Variables manquantes, par frequence ===');
  Object.entries(parVariable).sort((a, b) => b[1] - a[1])
    .forEach(([v, n]) => console.log(`  ${String(n).padStart(3)} x  ${v}`));
  console.log(`\nTotal : ${total} occurrence(s) sur ${rapport.length} document(s).`);
  console.log('Detail : .tmp/audit-variables-manquantes.json');
}

main().catch((e) => { console.error(e); process.exit(1); });
