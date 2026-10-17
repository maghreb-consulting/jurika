// Lot L6c : images fixes de la vitrine, en AVIF et en WebP, a partir d'une capture PNG prise en
// haute definition (deviceScaleFactor 2, par exemple 2560 x 1600 pour une fenetre de 1280 x 800).
//
//   node scripts/vitrine-images.mjs <capture.png> <nom> [dossier de sortie]
//
// Produit <nom>-1600.avif, <nom>-800.avif, <nom>-1600.webp, <nom>-800.webp (ratio 16:10,
// recadrage depuis le haut) dans public/vitrine par defaut. Redimensionnement et WebP : canvas
// de chromium (Playwright, deja installe) ; AVIF : libheif (vitrine-avif.py). Aucun telechargement.
// La capture elle-meme se prend sur des donnees fictives (cabinet de demonstration ou harnais
// visuel) ; aucun identifiant n'est lu ni ecrit par ce script.
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright';

const ICI = path.dirname(fileURLToPath(import.meta.url));
const [, , SRC, NOM, SORTIE = path.resolve(ICI, '../public/vitrine')] = process.argv;
if (!SRC || !NOM) {
  console.error('usage : node scripts/vitrine-images.mjs <capture.png> <nom> [dossier]');
  process.exit(2);
}
const LARGEURS = [1600, 800];
const QUALITE_WEBP = 0.84;
const QUALITE_AVIF = 60;

const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'vitrine-'));
const b = await chromium.launch();
const p = await b.newPage();
await p.setContent('<canvas id="c"></canvas>');
const data = 'data:image/png;base64,' + fs.readFileSync(SRC).toString('base64');
for (const w of LARGEURS) {
  const r = await p.evaluate(
    async ({ data, w, q }) => {
      const img = new Image();
      img.src = data;
      await img.decode();
      const h = Math.round((w * 10) / 16);
      const c = document.getElementById('c');
      c.width = w;
      c.height = h;
      const x = c.getContext('2d');
      x.imageSmoothingQuality = 'high';
      const sh = (img.width * 10) / 16;
      x.drawImage(img, 0, 0, img.width, Math.min(sh, img.height), 0, 0, w, h);
      const px = x.getImageData(0, 0, w, h).data;
      let bin = '';
      for (let i = 0; i < px.length; i += 0x8000) bin += String.fromCharCode.apply(null, px.subarray(i, i + 0x8000));
      return { webp: c.toDataURL('image/webp', q), rgba: btoa(bin), h };
    },
    { data, w, q: QUALITE_WEBP },
  );
  const webp = path.join(SORTIE, `${NOM}-${w}.webp`);
  fs.writeFileSync(webp, Buffer.from(r.webp.split(',')[1], 'base64'));
  const brut = path.join(tmp, `${w}.rgba`);
  fs.writeFileSync(brut, Buffer.from(r.rgba, 'base64'));
  const avif = path.join(SORTIE, `${NOM}-${w}.avif`);
  execFileSync('python3', ['-I', path.join(ICI, 'vitrine-avif.py'), brut, String(w), String(r.h), avif, String(QUALITE_AVIF)], { stdio: 'inherit' });
  for (const f of [avif, webp]) console.log(path.basename(f), fs.statSync(f).size, 'octets');
}
await b.close();
fs.rmSync(tmp, { recursive: true, force: true });
