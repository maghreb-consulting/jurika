#!/usr/bin/env node
// Recette R3 : logo des courriels en PNG haute definition, depuis le SVG source du logo
// Moucharabieh (front : src/assets/brand/moucharabieh/embleme-ivoire.svg, variante ivoire posee
// sur le bandeau marine des courriels). Les messageries n'affichent pas le SVG : on le rend en
// PNG 3x (affiche a 56 x 56 pixels dans le courriel, fichier de 168 x 168 pixels), fond
// transparent (coins arrondis de la tuile).
//
// Outil : chromium de Playwright, deja installe pour les tests du front (aucun telechargement).
// Usage (depuis la racine du depot) :
//   node scripts/courriels/generer-logo-courriel.mjs
// Variable : JURIKA_FRONT_NODE_MODULES = dossier node_modules qui contient playwright
// (defaut : frontend-react/node_modules du depot).
// Cible : backend-java/jurika-common/src/main/resources/branding/courriel/logo-jurika-courriel.png
// (CourrielJurika.LOGO_RESSOURCE ; taille controlee par LogoCourrielHauteDefinitionTest).
import { createRequire } from 'node:module';
import { readFileSync, writeFileSync, statSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const racine = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const modules = process.env.JURIKA_FRONT_NODE_MODULES || path.join(racine, 'frontend-react', 'node_modules');
const require = createRequire(path.join(modules, 'noop.js'));
const { chromium } = require('playwright');

const SOURCE = path.join(racine, 'frontend-react', 'src', 'assets', 'brand', 'moucharabieh', 'embleme-ivoire.svg');
const CIBLE = path.join(racine, 'backend-java', 'jurika-common', 'src', 'main', 'resources',
  'branding', 'courriel/logo-jurika-courriel.png');
const COTE = 56; // taille d'affichage dans le courriel (pixels CSS)
const ECHELLE = 3; // densite : 3 x 56 = 168 pixels

const svg = readFileSync(SOURCE, 'utf8');
const html = `<!doctype html><html><head><style>html,body{margin:0;padding:0;background:transparent}
#l{width:${COTE}px;height:${COTE}px}#l svg{width:100%;height:100%;display:block}</style></head>
<body><div id="l">${svg}</div></body></html>`;

const navigateur = await chromium.launch();
try {
  const page = await navigateur.newPage({ deviceScaleFactor: ECHELLE, viewport: { width: 200, height: 200 } });
  await page.setContent(html);
  const png = await page.locator('#l').screenshot({ omitBackground: true, type: 'png' });
  writeFileSync(CIBLE, png);
} finally {
  await navigateur.close();
}
console.log(`${path.relative(racine, CIBLE)} : ${statSync(CIBLE).size} octets (${COTE * ECHELLE} x ${COTE * ECHELLE})`);
