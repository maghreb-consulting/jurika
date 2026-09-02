#!/usr/bin/env node
/**
 * ═══════════════════════════════════════════════════════════════════════
 *  JURIKA — Captation video de demonstration sur la PLATEFORME REELLE
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Pilote un vrai navigateur sur le front-end lance en local et enregistre la
 * session. Aucune reconstitution, aucune capture collee : ce qui est filme est
 * l'application telle qu'elle tourne, sur les donnees du workspace de
 * demonstration JUR-DEMO2 (societes et personnes fictives).
 *
 * ── Pre-requis ────────────────────────────────────────────────────────
 *   1. Stack UP (front 5173 + gateway 8080 + microservices).
 *   2. node scripts/seed-demo.mjs                    (workspace, comptes, dossiers, tickets)
 *   3. node scripts/demo-video/enroll-totp.mjs       (2FA du compte de demo)
 *   4. python scripts/demo-video/build-demo-pdfs.py  (documents fictifs)
 *   5. node scripts/demo-video/seed-demo-content.mjs (Data Room + corpus chatbot)
 *
 * ── Usage ─────────────────────────────────────────────────────────────
 *   node scripts/demo-video/capture-demo.mjs
 *   node scripts/demo-video/capture-demo.mjs --vitesse=1.3   (plus lent : toutes
 *                                                             les pauses x1.3)
 *   node scripts/demo-video/capture-demo.mjs --url=http://localhost:5173
 *                                            --workspace=JUR-DEMO2
 *                                            --email=... --password=...
 *                                            --dossier="SARL Atlas Conseil"
 *                                            --sortie=C:\chemin\vers\dossier
 *
 * ── Choix techniques (a savoir avant de relire le code) ───────────────
 *
 *  • **Chromium « new headless » (channel: 'chromium')** et non le
 *    headless_shell par defaut : ce dernier n'embarque PAS la visionneuse PDF,
 *    et l'apercu de document (etape 4) comme la Fiche client (etape 7)
 *    filmaient une page blanche. Le mode headless est neanmoins indispensable :
 *    l'ecran de la machine fait 1536x864, une fenetre reelle de 1920x1080 ne
 *    tiendrait pas — seul le viewport emule garantit un vrai 1080p.
 *
 *  • **Curseur dessine par nous.** Playwright ne rend pas le pointeur systeme
 *    dans la video. On injecte donc (addInitScript, rejoue a chaque document)
 *    un petit disque qui suit les evenements `mousemove` reels emis par
 *    `page.mouse`, plus une onde de choc au clic. C'est un calque de 2 elements,
 *    `pointer-events: none`, qui ne modifie ni le style ni le comportement de
 *    l'application.
 *
 *  • **Rythme.** Toutes les temporisations passent par `attendre()` et sont
 *    multipliees par `--vitesse`. La frappe est faite caractere par caractere
 *    (`pressSequentially`), les defilements par petits crans de molette.
 *
 *  • **2FA reelle.** Le compte de demo a une 2FA TOTP reellement activee
 *    (RG-AU30). Le code a 6 chiffres saisi a l'ecran est calcule au moment de
 *    la frappe a partir du secret enrole — ce n'est pas une valeur figee.
 *
 * ── Sortie ────────────────────────────────────────────────────────────
 *   output/demo-video/JURIKA_demo_<date>.mp4   (1920x1080, H.264, sans son)
 *   output/demo-video/minutage.json            (reperes de chaque section)
 *   output/demo-video/brut/*.webm              (enregistrement Playwright brut)
 */

import { createRequire } from 'node:module';
import { readFile, writeFile, mkdir, readdir, rename } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { join, dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';
import { homedir } from 'node:os';
import { totp, msLeftInWindow } from './totp.mjs';

// Playwright est installe dans frontend-react (c'est la que vivent les e2e).
// On le resout explicitement pour pouvoir garder ce script dans scripts/.
const require = createRequire(
    resolve(dirname(fileURLToPath(import.meta.url)), '..', '..', 'frontend-react', 'package.json'));
const { chromium } = require('playwright');

const HERE = dirname(fileURLToPath(import.meta.url));
const PROJET = resolve(HERE, '..', '..');
const RACINE = resolve(PROJET, '..');

// ─── Parametres ─────────────────────────────────────────────────────────
const opt = (n, d) => {
    const m = process.argv.find(a => a.startsWith(`--${n}=`));
    return m ? m.split('=').slice(1).join('=') : d;
};

const CFG = {
    url: opt('url', 'http://localhost:5173'),
    workspace: opt('workspace', 'JUR-DEMO2'),
    email: opt('email', 'employe1@demo.jurika.ma'),
    password: opt('password', 'Demo@2026'),
    dossier: opt('dossier', 'SARL Atlas Conseil'),
    sortie: opt('sortie', join(RACINE, 'output', 'demo-video')),
    vitesse: Number(opt('vitesse', '1')),
    largeur: 1920,
    hauteur: 1080,
};

const BRUT = join(CFG.sortie, 'brut');
const TELECHARGEMENTS = join(CFG.sortie, 'telechargements');

// ─── Rythme ─────────────────────────────────────────────────────────────
const attendre = (ms) => new Promise(r => setTimeout(r, Math.round(ms * CFG.vitesse)));
/** Temps de lecture d'un ecran cle par le jury. */
const LIRE = 1250;
/** Petit temps mort entre deux gestes. */
const GESTE = 420;

const t0 = { value: 0 };
const debutVideo = { value: 0 };
const minutage = [];
const anomalies = [];

function chapitre(titre) {
    const t = Date.now() - t0.value;
    minutage.push({ titre, ms: t, horodatage: mmss(t) });
    console.log(`  [${mmss(t)}]  ${titre}`);
}

function mmss(ms) {
    const s = Math.round(ms / 1000);
    return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
}

function signaler(quoi, pourquoi) {
    anomalies.push({ quoi, pourquoi });
    console.log(`  ⚠  ${quoi} — ${pourquoi}`);
}

// ─── Curseur visible + neutralisation des artefacts de dev ──────────────
const SCRIPT_CURSEUR = `
(() => {
  if (window.__jurikaCurseur) return;
  window.__jurikaCurseur = true;
  const poser = () => {
    if (!document.body || document.getElementById('jurika-curseur')) return;
    const style = document.createElement('style');
    style.textContent = \`
      #jurika-curseur {
        position: fixed; left: 0; top: 0; z-index: 2147483647; pointer-events: none;
        width: 22px; height: 22px; margin: -11px 0 0 -11px; border-radius: 50%;
        background: rgba(168,133,61,.30);
        border: 2px solid rgba(168,133,61,.95);
        box-shadow: 0 0 0 1px rgba(255,255,255,.65), 0 2px 6px rgba(0,0,0,.35);
        transition: transform .08s linear; will-change: transform;
      }
      #jurika-onde {
        position: fixed; left: 0; top: 0; z-index: 2147483646; pointer-events: none;
        width: 22px; height: 22px; margin: -11px 0 0 -11px; border-radius: 50%;
        border: 2px solid rgba(168,133,61,.9); opacity: 0; transform: scale(1);
      }
      @keyframes jurika-clic { from { opacity:.9; transform: scale(1); }
                               to   { opacity:0;  transform: scale(2.6); } }
      /* Superpositions de developpement : hors sujet dans une demo. */
      vite-error-overlay, #vite-error-overlay { display: none !important; }
    \`;
    document.head.appendChild(style);
    const c = document.createElement('div'); c.id = 'jurika-curseur';
    const o = document.createElement('div'); o.id = 'jurika-onde';
    document.body.append(c, o);
    let x = -50, y = -50;
    addEventListener('mousemove', (e) => {
      x = e.clientX; y = e.clientY;
      c.style.transform = \`translate(\${x}px, \${y}px)\`;
      o.style.transform = \`translate(\${x}px, \${y}px)\`;
    }, true);
    addEventListener('mousedown', () => {
      o.style.animation = 'none';
      void o.offsetWidth;
      o.style.transformOrigin = 'center';
      o.style.left = x + 'px'; o.style.top = y + 'px';
      o.style.transform = 'translate(0,0)';
      o.style.animation = 'jurika-clic .45s ease-out';
    }, true);
  };
  if (document.readyState === 'loading') addEventListener('DOMContentLoaded', poser);
  else poser();
})();
`;

// ─── Gestes ─────────────────────────────────────────────────────────────
let souris = { x: CFG.largeur / 2, y: CFG.hauteur / 2 };

async function versPoint(page, x, y) {
    // Deplacement en plusieurs pas : le curseur injecte glisse au lieu de sauter.
    const distance = Math.hypot(x - souris.x, y - souris.y);
    const pas = Math.max(8, Math.min(40, Math.round(distance / 22)));
    await page.mouse.move(x, y, { steps: pas });
    souris = { x, y };
}

async function viser(page, cible) {
    const loc = typeof cible === 'string' ? page.locator(cible) : cible;
    await loc.first().scrollIntoViewIfNeeded({ timeout: 15000 });
    await attendre(180);
    const box = await loc.first().boundingBox();
    if (!box) throw new Error('element sans boite englobante');
    await versPoint(page, Math.round(box.x + box.width / 2), Math.round(box.y + box.height / 2));
    return loc.first();
}

async function cliquer(page, cible, apres = GESTE) {
    const loc = await viser(page, cible);
    await attendre(260);
    await loc.click({ timeout: 15000 });
    await attendre(apres);
}

async function survoler(page, cible, duree = 900) {
    await viser(page, cible);
    await attendre(duree);
}

async function saisir(page, cible, texte, delai = 60, apres = GESTE) {
    const loc = await viser(page, cible);
    await attendre(200);
    await loc.click();
    await attendre(180);
    await loc.pressSequentially(texte, { delay: delai });
    await attendre(apres);
}

/**
 * Attend que la visionneuse PDF integree ait reellement peint sa page.
 *
 * Le rendu du PDF se fait hors DOM (plugin PDFium) : aucun selecteur ne dit
 * « c'est affiche ». On echantillonne donc une vignette du centre de la page :
 * tant que la zone est unie (fond gris de la visionneuse), le PNG compresse
 * pese quelques centaines d'octets ; des que le document est peint, il passe a
 * plusieurs dizaines de milliers.
 */
async function attendrePeintureDuPdf(page, plafondMs = 10000) {
    const debut = Date.now();
    while (Date.now() - debut < plafondMs) {
        // Garde-fou indispensable : sans elle, une navigation ratee laissait la
        // page de l'application a l'ecran, sa vignette (riche) passait le seuil,
        // et la fonction annonçait « peint » alors que le PDF n'etait jamais
        // apparu. Le plan final filmait la Data Room, sans la moindre alerte.
        if (!page.url().startsWith('file:')) return false;
        const vignette = await page
            .screenshot({ clip: { x: 760, y: 200, width: 700, height: 420 } })
            .catch(() => null);
        if (vignette && vignette.length > 12000) return true;
        await new Promise(r => setTimeout(r, 350));
    }
    return false;
}

/**
 * Ouvre le PDF produit dans la visionneuse integree.
 *
 * <p>Le passage par `about:blank` n'est pas cosmetique : naviguer directement
 * d'une page `http://` vers une URL `file://` est refuse par Chromium selon le
 * contexte, silencieusement. On coupe donc l'origine d'abord.
 */
async function ouvrirPdf(page, chemin) {
    const url = 'file:///' + chemin.replace(/\\/g, '/');
    for (let essai = 1; essai <= 2; essai++) {
        try {
            await page.goto('about:blank', { waitUntil: 'domcontentloaded' });
            await page.goto(url, { waitUntil: 'load' });
        } catch (e) {
            if (essai === 2) return { ok: false, raison: e.message.split('\n')[0] };
            continue;
        }
        if (page.url().startsWith('file:')) return { ok: true };
    }
    return { ok: false, raison: `la page est restee sur ${page.url()}` };
}

/** Defilement progressif : `crans` coups de molette de `pas` pixels. */
async function defiler(page, crans, pas = 120, pause = 90) {
    for (let i = 0; i < crans; i++) {
        await page.mouse.wheel(0, pas);
        await attendre(pause);
    }
}

// ─── Reperage hors camera ───────────────────────────────────────────────
/** Retourne l'id du dossier de demonstration via l'API (login + 2FA reels). */
/** Id du ticket portant le workflow prepare — repere hors camera. */
async function identifiantTicket(cleTotp, reference) {
    const token = await jetonApi(cleTotp);
    const r = await fetch(`${opt('api', 'http://localhost:8080')}/api/v1/tickets?limit=200`,
        { headers: { Authorization: `Bearer ${token}` } });
    if (!r.ok) throw new Error(`/tickets -> ${r.status}`);
    const corps = await r.json();
    const t = (corps.items ?? corps).find(x => x.reference === reference);
    if (!t) throw new Error(`${reference} absent`);
    return t.id;
}

/** Jeton d'API (login + 2FA reels), mutualise par les reperages hors camera. */
async function jetonApi(cleTotp) {
    const api = opt('api', 'http://localhost:8080');
    const post = async (chemin, corps, token) => {
        const r = await fetch(api + chemin, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
            body: JSON.stringify(corps),
        });
        if (!r.ok) throw new Error(`${chemin} -> ${r.status}`);
        return r.json();
    };
    const l = await post('/api/v1/auth/login',
        { workspaceCode: CFG.workspace, email: CFG.email, password: CFG.password });
    if (!l.requires2fa) return l.accessToken;
    if (msLeftInWindow() < 4000) await new Promise(r => setTimeout(r, msLeftInWindow() + 400));
    const v = await post('/api/v1/auth/verify-2fa',
        { userId: l.userId, workspaceId: l.workspaceId, code: totp(cleTotp) });
    return v.accessToken;
}

async function identifiantDossier(cleTotp, raisonSociale = CFG.dossier) {
    const api = opt('api', 'http://localhost:8080');
    const post = async (chemin, corps, token) => {
        const r = await fetch(api + chemin, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
            body: JSON.stringify(corps),
        });
        if (!r.ok) throw new Error(`${chemin} -> ${r.status}`);
        return r.json();
    };
    const l = await post('/api/v1/auth/login',
        { workspaceCode: CFG.workspace, email: CFG.email, password: CFG.password });
    let token = l.accessToken;
    if (l.requires2fa) {
        if (msLeftInWindow() < 4000) await new Promise(r => setTimeout(r, msLeftInWindow() + 400));
        const v = await post('/api/v1/auth/verify-2fa',
            // Le code part en CHAINE : serialise en nombre, un code commencant par
            // zero perd son premier chiffre et le serveur le refuse.
            { userId: l.userId, workspaceId: l.workspaceId, code: totp(cleTotp) });
        token = v.accessToken;
    }
    const r = await fetch(api + '/api/v1/dataroom/dossiers', { headers: { Authorization: `Bearer ${token}` } });
    if (!r.ok) throw new Error(`/dataroom/dossiers -> ${r.status}`);
    const trouve = (await r.json()).find(d => d.raisonSociale === raisonSociale);
    if (!trouve) throw new Error(`dossier « ${CFG.dossier} » introuvable`);
    return trouve.id;
}

// ─── Programme ──────────────────────────────────────────────────────────
async function main() {
    await mkdir(BRUT, { recursive: true });
    await mkdir(TELECHARGEMENTS, { recursive: true });

    const secrets = JSON.parse(await readFile(join(HERE, 'demo-secrets.json'), 'utf8'));
    const cleTotp = secrets[`${CFG.workspace}:${CFG.email}`]?.secret;
    if (!cleTotp) throw new Error(`Secret TOTP absent pour ${CFG.workspace}:${CFG.email} — lancer enroll-totp.mjs`);

    // Remise en etat du workspace de demonstration AVANT de filmer. Indispensable
    // pour rejouer la captation : l'etape 7 saisit les identifiants post-
    // immatriculation, donc au tour suivant le pre-vol de la Fiche client n'aurait
    // plus rien a signaler. seed-demo-content les remet a blanc et complete ce
    // qui manquerait dans la Data Room (idempotent).
    if (opt('reinitialiser', 'oui') !== 'non') {
        console.log('▶ Remise en etat du contenu de demonstration…');
        await lancer(process.execPath, [join(HERE, 'seed-demo-workflow.mjs')])
            .then(() => console.log('  ✓ workflow de creation pret'))
            .catch(e => signaler('Preparation workflow', e.message.split(String.fromCharCode(10))[0]));
        await lancer(process.execPath, [join(HERE, 'seed-demo-content.mjs')])
            .then(() => console.log('  ✓ contenu pret'))
            .catch(e => signaler('Remise en etat', e.message.split('\n')[0]));
    }

    // Identifiant du dossier, recupere hors camera : il sert au lien profond de
    // l'etape 7 (retour direct a la Data Room, sans refilmer la recherche).
    const ticketWorkflow = await identifiantTicket(cleTotp, 'T-DEMO-003').catch(e => {
        signaler('Acte genere', `ticket du workflow introuvable (${e.message})`);
        return null;
    });

    const dossierWorkflow = await identifiantDossier(cleTotp, 'Maroc Telecom Services SARL').catch(() => null);

    const dossierId = await identifiantDossier(cleTotp).catch(e => {
        signaler('Lien profond Data Room', e.message);
        return null;
    });

    console.log(`\n▶ Captation — ${CFG.url}  (${CFG.workspace} / ${CFG.email})`);
    console.log(`  vitesse x${CFG.vitesse}  •  ${CFG.largeur}x${CFG.hauteur}\n`);

    const navigateur = await chromium.launch({
        headless: true,
        channel: 'chromium',   // « new headless » : embarque la visionneuse PDF
        args: ['--hide-scrollbars=false', '--force-color-profile=srgb'],
    });

    const contexte = await navigateur.newContext({
        viewport: { width: CFG.largeur, height: CFG.hauteur },
        recordVideo: { dir: BRUT, size: { width: CFG.largeur, height: CFG.hauteur } },
        deviceScaleFactor: 1,
        locale: 'fr-FR',
        timezoneId: 'Africa/Casablanca',
        acceptDownloads: true,
        reducedMotion: 'no-preference',
    });
    await contexte.addInitScript(SCRIPT_CURSEUR);

    const page = await contexte.newPage();
    page.setDefaultTimeout(20000);
    // L'enregistrement demarre a la creation de la page : tout ce qui precede le
    // premier chapitre (chargement de /login) sera coupe au montage.
    debutVideo.value = Date.now();

    let ficheTelechargee = null;

    try {
        // ── Amorce : on charge la page de connexion avant de lancer le chrono
        await page.goto(`${CFG.url}/login`, { waitUntil: 'networkidle' });
        await page.waitForTimeout(1200);
        t0.value = Date.now();
        chapitre('Ouverture — page de connexion');
        await versPoint(page, CFG.largeur / 2, CFG.hauteur / 2 + 120);
        await attendre(700);

        // ══════════════════════════════════════════════════════════════
        // 1. Connexion
        // ══════════════════════════════════════════════════════════════
        chapitre('1. Connexion — creation de compte possible');
        // On montre d'abord que la plateforme est ouverte a l'inscription.
        await survoler(page, page.getByRole('link', { name: /Creer un nouveau workspace/i }), 1000);
        await cliquer(page, page.getByRole('link', { name: /Creer un nouveau workspace/i }), 400);
        await page.waitForURL(/\/signup/, { timeout: 15000 }).catch(() => {});
        await attendre(LIRE);
        await defiler(page, 2, 150);
        await page.goBack({ waitUntil: 'domcontentloaded' });
        await attendre(1100);

        chapitre('1. Connexion — code workspace');
        // Le prefixe « JUR- » est fixe dans le champ : on ne saisit que la suite.
        await saisir(page, 'input[name="workspaceCode"]', CFG.workspace.replace(/^JUR-/, ''), 110);
        await cliquer(page, page.getByRole('button', { name: /Continuer/i }), 900);

        chapitre('1. Connexion — identifiant et mot de passe');
        await saisir(page, 'input[name="email"]', CFG.email, 45);
        await saisir(page, 'input[name="password"]', CFG.password, 70);
        await attendre(400);
        await cliquer(page, page.getByRole('button', { name: /Se connecter/i }), 1200);

        // 2FA reelle : code calcule a l'instant.
        const champ2fa = page.locator('input[name="twofa"]');
        if (await champ2fa.count()) {
            chapitre('1. Connexion — verification en 2 etapes (TOTP reel)');
            if (msLeftInWindow() < 6000) await attendre(msLeftInWindow() + 400);
            await saisir(page, champ2fa, totp(cleTotp), 130);
            await cliquer(page, page.getByRole('button', { name: /Verifier|Valider|Continuer/i }), 800);
        }
        await page.waitForURL(/\/dashboard/, { timeout: 25000 });
        await page.waitForLoadState('networkidle').catch(() => {});
        await attendre(LIRE);

        // ══════════════════════════════════════════════════════════════
        // 2. Tableau de bord
        // ══════════════════════════════════════════════════════════════
        chapitre('2. Tableau de bord — indicateurs');
        await versPoint(page, 640, 360);
        await attendre(LIRE);
        await defiler(page, 7, 130);
        await attendre(LIRE);
        chapitre('2. Tableau de bord — Copilote et echeances');
        await defiler(page, 7, 130);
        await attendre(LIRE + 400);
        await defiler(page, 10, -170, 70);   // remontee
        await attendre(800);

        // ══════════════════════════════════════════════════════════════
        // 3. Tickets
        // ══════════════════════════════════════════════════════════════
        chapitre('3. Tickets — vue Kanban');
        await cliquer(page, page.getByRole('link', { name: /Mes Tickets/i }), 1200);
        await page.waitForLoadState('networkidle').catch(() => {});
        await attendre(LIRE);
        await defiler(page, 3, 130);
        await attendre(600);

        chapitre('3. Tickets — vue Tableau (types d\'operation)');
        await cliquer(page, page.getByRole('button', { name: /^Tableau$/ }), 1000);
        await attendre(LIRE + 200);
        await defiler(page, 3, 130);
        await attendre(900);

        chapitre('3. Tickets — catalogue des types');
        await cliquer(page, page.getByRole('button', { name: /Nouveau ticket|^Nouveau$/ }), 1100);
        // <select> natif : la liste deroulante est rendue par l'OS et n'apparait
        // pas dans la capture. On fait donc defiler les valeurs une a une, ce qui
        // affiche successivement chaque type dans le champ.
        const selType = page.locator('select').first();
        if (await selType.count()) {
            await viser(page, selType);
            for (const t of ['CREATION', 'MODIFICATION', 'DISSOLUTION']) {
                await selType.selectOption(t).catch(() => {});
                await attendre(520);
            }
        } else {
            signaler('Types de ticket', 'aucun <select> trouve dans le tiroir de creation');
        }
        await attendre(450);
        // Le tiroir se ferme par sa croix : `Escape` n'y est pas cable, et son
        // voile (`bg-fg/40`) intercepte tous les clics tant qu'il reste ouvert.
        await cliquer(page, page.getByRole('button', { name: 'Fermer' }).first(), 900);

        chapitre('3. Tickets — detail d\'un ticket');
        const ligne = page.locator('table tbody tr').first();
        if (await ligne.count()) {
            await cliquer(page, ligne, 1300);
            await attendre(LIRE);
            await cliquer(page, page.getByRole('button', { name: 'Fermer' }).first(), 900);
        } else {
            signaler('Detail ticket', 'aucune ligne dans la vue Tableau');
        }

        // ══════════════════════════════════════════════════════════════
        // 4. Data Room
        // ══════════════════════════════════════════════════════════════
        chapitre('4. Data Room — liste des dossiers');
        await cliquer(page, page.getByRole('link', { name: /Data Room/i }), 1200);
        await page.waitForLoadState('networkidle').catch(() => {});
        await attendre(LIRE);
        await saisir(page, page.getByPlaceholder('Rechercher...'), 'Atlas', 70);
        await attendre(900);

        chapitre('4. Data Room — dossier ' + CFG.dossier);
        await cliquer(page, page.getByText(CFG.dossier, { exact: false }).first(), 1500);
        await page.waitForLoadState('networkidle').catch(() => {});
        await attendre(LIRE);

        chapitre('4. Data Room — documents classes par type');
        await defiler(page, 4, 130);
        await attendre(LIRE);

        chapitre('4. Data Room — versions d\'un document');
        const bascVersions = page.locator('[data-testid="versions-toggle"]').first();
        if (await bascVersions.count()) {
            await cliquer(page, bascVersions, 1200);
            await attendre(LIRE);
        } else {
            signaler('Anciennes versions', 'bascule « Anciennes versions » absente');
        }

        chapitre('4. Data Room — apercu d\'un document');
        const apercu = page.locator('button[title="Apercu"], button[title="Aperçu"]').first();
        if (await apercu.count()) {
            await cliquer(page, apercu, 1500);
            await attendre(2200);          // rendu de la visionneuse PDF
            await cliquer(page, page.getByRole('button', { name: /Fermer l'apercu/i }).first(), 900);
        } else {
            signaler('Apercu document', 'bouton « Apercu » introuvable');
        }

        chapitre('4. Data Room — onglets fiscal et comptable');
        await defiler(page, 5, -190, 60);
        await cliquer(page, page.getByRole('button', { name: /Dossier Fiscal/i }), 900);
        await attendre(LIRE);
        await cliquer(page, page.getByRole('button', { name: /Dossier Comptable/i }), 900);
        await attendre(LIRE - 300);

        chapitre('4. Data Room — demandes du client et requetes au cabinet');
        // Les deux sens de l'echange : ce que le client reclame, et ce que le
        // cabinet lui demande de fournir.
        await cliquer(page, page.getByRole('button', { name: /^Demandes$/ }), 1100);
        await attendre(LIRE + 500);
        await defiler(page, 4, 140, 110);
        await attendre(LIRE + 300);
        await cliquer(page, page.getByRole('button', { name: /Dossier Juridique/i }), 800);

        // ══════════════════════════════════════════════════════════════
        // 5. Chatbot RAG
        // ══════════════════════════════════════════════════════════════
        chapitre('5. Chatbot — question metier');
        await cliquer(page, page.getByRole('link', { name: /ChatBot IA/i }), 1300);
        await page.waitForLoadState('networkidle').catch(() => {});
        await attendre(1100);
        // Le corpus interroge est celui du cabinet : les statuts refondus du
        // dossier ont ete verses dans les « Sources fiables » du workspace.
        await cliquer(page, page.getByRole('button', { name: /Sources fiables/i }), 800);
        await attendre(LIRE - 400);
        await cliquer(page, page.getByRole('button', { name: /Sources fiables/i }), 500);

        // Question posee en francais naturel : depuis que la recherche du corpus
        // ignore les interrogatifs et sait retomber sur un OU pondere, une vraie
        // question retrouve les passages (elle rendait 0 resultat auparavant, ce
        // qui imposait de formuler en groupe nominal).
        const QUESTION = 'Que prevoient les statuts en cas de cession de parts a un tiers ?';
        await saisir(page, '[data-testid="chatbot-message-input"]', QUESTION, 42);
        await page.keyboard.press('Enter');
        chapitre('5. Chatbot — reponse ancree sur les documents');
        await attendre(2100);
        await defiler(page, 5, 130, 120);
        await attendre(LIRE + 400);

        // ══════════════════════════════════════════════════════════════
        // 6. Un acte genere, valide, puis depose automatiquement
        // ══════════════════════════════════════════════════════════════
        // C'est la boucle que le cabinet attend : le document est produit par la
        // plateforme, relu (et corrige si besoin via « Editer »), puis valide —
        // et c'est la validation qui declenche son depot en Data Room. Rien
        // n'est range a la main.
        if (ticketWorkflow) {
            chapitre("6. Generation d'un acte depuis le workflow");
            await page.goto(`${CFG.url}/workflows/${ticketWorkflow}`, { waitUntil: 'domcontentloaded' });
            await page.waitForLoadState('networkidle').catch(() => {});
            await attendre(1500);
            const generer = page.getByRole('button', { name: /^Generer$|^Générer$/ }).first();
            if (await generer.count()) {
                await cliquer(page, generer, 800);
                await attendre(4500);
                chapitre("6. Relecture — le document reste modifiable");
                await defiler(page, 5, 150, 130);
                await attendre(LIRE);
                // On survole « Editer » sans l'ouvrir : la possibilite de corriger
                // avant validation doit se voir, sans couter 20 s de camera.
                const editer = page.getByRole('button', { name: /^Éditer|^Editer/ }).first();
                if (await editer.count()) await survoler(page, editer, 1100);

                chapitre('6. Validation — depot automatique en Data Room');
                const valider = page.getByRole('button', { name: /Valider et deposer|Valider et déposer/i }).first();
                if (await valider.count()) {
                    await cliquer(page, valider, 1200);
                    await attendre(3000);
                    await attendre(LIRE);
                    // Et on va le CONSTATER : le document valide apparait dans la
                    // Data Room du dossier, classe par type et versionne. C'est la
                    // demonstration du rangement automatique, pas sa promesse.
                    if (dossierWorkflow) {
                        chapitre('6. Le document valide est arrive en Data Room');
                        await page.goto(`${CFG.url}/data-rooms?dossier=${dossierWorkflow}`,
                            { waitUntil: 'domcontentloaded' });
                        await page.waitForLoadState('networkidle').catch(() => {});
                        await attendre(1800);
                        await defiler(page, 4, 140, 120);
                        await attendre(LIRE + 400);
                    }
                } else {
                    signaler('Depot automatique', 'bouton « Valider et deposer en Dataroom » introuvable');
                }
            } else {
                signaler("Generation d'acte", "aucun bouton « Generer » a l'ecran 7");
            }
        }

        // ══════════════════════════════════════════════════════════════
        // 7. Identifiants post-immatriculation → Fiche client
        // ══════════════════════════════════════════════════════════════
        chapitre('7. Retour Data Room — dossier ' + CFG.dossier);
        // Lien profond supporte par l'application (`/data-rooms?dossier={id}`,
        // celui qu'emploie deja le tableau de bord) : inutile de refilmer la
        // recherche montree a l'etape 4.
        if (dossierId) {
            await page.goto(`${CFG.url}/data-rooms?dossier=${dossierId}`, { waitUntil: 'domcontentloaded' });
        } else {
            await cliquer(page, page.getByRole('link', { name: /Data Room/i }), 1000);
            await saisir(page, page.getByPlaceholder('Rechercher...'), 'Atlas', 70);
            await cliquer(page, page.getByText(CFG.dossier, { exact: false }).first(), 1200);
        }
        await page.waitForLoadState('networkidle').catch(() => {});
        await attendre(1400);

        chapitre('7. Fiche client — pre-vol des identifiants manquants');
        await cliquer(page, page.getByRole('button', { name: /Fiche client/i }), 1300);
        // Le libelle reel est accentue (« Compléter maintenant ») : la classe
        // [ée] evite de dependre de l'accent dans le selecteur.
        const preflight = page.getByRole('button', { name: /Compl[ée]ter maintenant/i });
        if (await preflight.count()) {
            await attendre(LIRE + 400);          // le jury lit la liste des manquants
            await cliquer(page, preflight, 1200);
        } else {
            signaler('Pre-vol Fiche client',
                'la modale n\'est pas apparue — les identifiants du dossier etaient deja complets');
            await cliquer(page, page.locator('[data-testid="open-identifiants"]').first(), 1200);
        }

        chapitre('7. Saisie des identifiants (RC, IF, patente, CNSS)');
        const champs = [
            ['Numéro RC', '512447'],
            ['Tribunal du RC', 'Tribunal de commerce de Casablanca'],
            ['Identifiant fiscal (IF)', '40218773'],
            ['Taxe professionnelle (patente)', '30721104'],
            ['CNSS', '7742019'],
        ];
        for (const [label, valeur] of champs) {
            const champ = page.getByLabel(label, { exact: false }).first();
            if (await champ.count()) await saisir(page, champ, valeur, 26, 200);
            else signaler(`Champ « ${label} »`, 'introuvable dans le tiroir Identifiants');
        }
        const adresse = page.getByLabel('Adresse du siège', { exact: false }).first();
        if (await adresse.count()) {
            await saisir(page, adresse, '45, boulevard Zerktouni, Casablanca', 18, 200);
        }
        await attendre(300);
        await cliquer(page, page.getByRole('button', { name: /^Enregistrer$/ }), 1400);
        await page.waitForLoadState('networkidle').catch(() => {});
        await attendre(LIRE - 300);

        chapitre('7. Generation de la Fiche client');
        const attenteFichier = page.waitForEvent('download', { timeout: 30000 }).catch(() => null);
        await cliquer(page, page.getByRole('button', { name: /Fiche client/i }), 900);
        const encore = page.getByRole('button', { name: /Generer quand meme|Générer quand même/i });
        if (await encore.count()) await cliquer(page, encore, 600);
        const dl = await attenteFichier;
        if (dl) {
            ficheTelechargee = join(TELECHARGEMENTS, dl.suggestedFilename());
            await dl.saveAs(ficheTelechargee);
            console.log(`     fiche client : ${ficheTelechargee}`);
        } else {
            signaler('Fiche client', 'aucun telechargement recu dans les 30 s');
        }
        await attendre(1800);

        // ══════════════════════════════════════════════════════════════
        // Fin — plan fixe sur le document produit
        // ══════════════════════════════════════════════════════════════
        if (ficheTelechargee && existsSync(ficheTelechargee)) {
            chapitre('Fin — la Fiche client produite');
            const ouverture = await ouvrirPdf(page, ficheTelechargee);
            if (!ouverture.ok) {
                signaler('Plan final', `le PDF produit n'a pas pu etre ouvert (${ouverture.raison})`);
            }
            // La visionneuse PDF integree peint sa premiere page en 1 a 5 s selon
            // la charge : une attente fixe filmait tantot le document, tantot un
            // cadre vide. On attend donc la peinture pour de bon, puis on tient
            // le plan 2,4 s.
            const peint = await attendrePeintureDuPdf(page);
            if (!peint) {
                signaler('Plan final',
                    'la visionneuse PDF n\'a pas affiche le document — le plan final est perdu');
            }
            // 6 s et non 2,4 : l'enregistrement video est ecrit de facon
            // asynchrone, et les toutes dernieres images produites avant
            // `context.close()` peuvent ne jamais etre encodees. Un maintien
            // court faisait disparaitre le plan final du fichier alors qu'il
            // avait bien ete joue a l'ecran — le montage se terminait sur la
            // Data Room. La marge excedentaire est recoupee par le `-t` ffmpeg.
            await attendre(6000);
        } else {
            signaler('Plan final',
                'aucune Fiche client telechargee : la video se termine sur le tableau de bord');
            chapitre('Fin — retour au tableau de bord');
            await cliquer(page, page.getByRole('link', { name: /Tableau de bord/i }), 1200);
            await attendre(3200);
        }
        chapitre('FIN');
    } catch (err) {
        signaler('Interruption du parcours', err.message);
        console.error('\n❌ Le parcours s\'est arrete :', err.message);
        console.error('   La video de ce qui a ete filme jusque-la est conservee.\n');
    } finally {
        const video = page.video();
        await contexte.close();      // ferme et finalise le fichier .webm
        await navigateur.close();

        const webm = video ? await video.path() : null;
        await finaliser(webm);
    }
}

// ─── Assemblage MP4 ─────────────────────────────────────────────────────
/**
 * Cherche un ffmpeg capable d'encoder en H.264.
 *
 * Le ffmpeg livre avec Playwright est volontairement ampute (VP8 + WebM
 * uniquement, `--disable-everything`) : il enregistre mais ne sait pas produire
 * de MP4. On cherche donc un binaire complet, sans rien installer.
 */
async function trouverFfmpeg() {
    const candidats = [
        process.env.FFMPEG_PATH,
        'ffmpeg',
        join(homedir(), 'AppData', 'Local', 'Programs', 'LNV', 'Stremio-4', 'ffmpeg.exe'),
        'C:\\ffmpeg\\bin\\ffmpeg.exe',
        'C:\\Program Files\\ffmpeg\\bin\\ffmpeg.exe',
    ].filter(Boolean);

    for (const bin of candidats) {
        const sortie = await lancer(bin, ['-hide_banner', '-encoders']).catch(() => null);
        if (sortie && /\slibx264\s/.test(sortie)) return bin;
    }
    return null;
}

function lancer(bin, args) {
    return new Promise((res, rej) => {
        const p = spawn(bin, args, { windowsHide: true });
        let out = '';
        p.stdout.on('data', d => { out += d; });
        p.stderr.on('data', d => { out += d; });
        p.on('error', rej);
        p.on('close', code => (code === 0 ? res(out) : rej(new Error(`${bin} -> ${code}\n${out.slice(-800)}`))));
    });
}

async function finaliser(webm) {
    if (!webm) { console.log('\n❌ Aucun enregistrement produit.'); return; }

    const jour = new Date().toISOString().slice(0, 10);
    const nomBrut = join(BRUT, `JURIKA_demo_${jour}.webm`);
    await rename(webm, nomBrut).catch(() => {});
    const source = existsSync(nomBrut) ? nomBrut : webm;

    const mp4 = join(CFG.sortie, `JURIKA_demo_${jour}.mp4`);
    const ffmpeg = await trouverFfmpeg();

    // Amorce a couper : le temps de chargement de /login, enregistre avant le
    // premier chapitre. On garde 0,4 s pour ne pas demarrer sur une coupe seche.
    const amorce = Math.max(0, (t0.value - debutVideo.value) / 1000 - 0.4);
    // Duree a conserver : le deroule des chapitres, plus 0,4 s d'amorce avant le
    // premier et 0,8 s de plan fixe apres le dernier.
    //
    // Attention au referentiel : `-ss amorce` place deja le debut de sortie sur
    // le premier chapitre. `-t` compte donc a partir de LA, pas depuis le debut
    // de l'enregistrement. Retrancher `amorce` ici (ce que faisait la version
    // precedente) amputait la sortie du temps de chargement initial — soit 3 a
    // 5 secondes selon la charge de la machine, exactement la duree du plan
    // final. La Fiche client etait bien filmee, puis coupee au montage.
    const duree = minutage.length
        ? (minutage[minutage.length - 1].ms / 1000 + 1.2).toFixed(2)
        : null;

    if (!ffmpeg) {
        signaler('Conversion MP4',
            'aucun ffmpeg avec libx264 trouve — la video reste au format WebM');
        console.log(`\n⚠  WebM conserve : ${source}`);
    } else {
        console.log(`\n▶ Conversion MP4 (${ffmpeg})`);
        await lancer(ffmpeg, [
            '-y', ...(amorce > 0.3 ? ['-ss', amorce.toFixed(2)] : []), '-i', source,
            ...(duree ? ['-t', duree] : []),
            '-c:v', 'libx264', '-preset', 'slow', '-crf', '20',
            '-pix_fmt', 'yuv420p', '-r', '30',
            '-vf', `scale=${CFG.largeur}:${CFG.hauteur}:flags=lanczos`,
            '-movflags', '+faststart', '-an',
            mp4,
        ]);
        console.log(`  ✓ ${mp4}`);
    }

    const recap = {
        genere: new Date().toISOString(),
        source: { url: CFG.url, workspace: CFG.workspace, compte: CFG.email, dossier: CFG.dossier },
        vitesse: CFG.vitesse,
        duree: minutage.length ? minutage[minutage.length - 1].horodatage : '00:00',
        sections: minutage,
        anomalies,
        fichiers: { mp4: ffmpeg ? mp4 : null, webm: source },
    };
    await writeFile(join(CFG.sortie, 'minutage.json'), JSON.stringify(recap, null, 2) + '\n', 'utf8');

    console.log('\n─── Minutage ───');
    for (const s of minutage) console.log(`  ${s.horodatage}  ${s.titre}`);
    if (anomalies.length) {
        console.log('\n─── A signaler ───');
        for (const a of anomalies) console.log(`  • ${a.quoi} : ${a.pourquoi}`);
    }
    console.log(`\n✓ Recapitulatif : ${join(CFG.sortie, 'minutage.json')}\n`);
}

main().catch(e => { console.error('\n❌', e); process.exit(1); });
