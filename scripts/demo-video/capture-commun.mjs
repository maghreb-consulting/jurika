/**
 * Gestes de camera partages par les scripts de captation video.
 *
 * Deux parcours sont filmes sur la meme plateforme — le tour d'ensemble
 * (`capture-demo.mjs`) et le parcours complet de constitution
 * (`capture-parcours.mjs`) — et ils ont exactement les memes besoins :
 * un curseur visible, une frappe caractere par caractere, des defilements
 * progressifs, un journal de minutage, et un montage MP4 en fin de course.
 * Tout cela vit ici plutot qu'en double.
 *
 * `creerCamera(CFG)` rend un objet de gestes lie a la configuration passee
 * (vitesse, dimensions, dossier de sortie).
 */
import { writeFile, rename } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { join } from 'node:path';
import { spawn } from 'node:child_process';
import { homedir } from 'node:os';

/**
 * Calque de curseur injecte dans chaque document.
 *
 * Playwright ne rend pas le pointeur systeme dans la video. On dessine donc un
 * disque qui suit les vrais evenements `mousemove` emis par `page.mouse`, plus
 * une onde au clic. Deux elements en `pointer-events: none` : ni les styles ni
 * le comportement de l'application ne sont touches.
 */
export const SCRIPT_CURSEUR = `
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

function mmss(ms) {
    const s = Math.round(ms / 1000);
    return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
}

export function creerCamera(CFG) {
    const attendre = (ms) => new Promise(r => setTimeout(r, Math.round(ms * CFG.vitesse)));
    /** Temps de lecture d'un ecran cle par le jury. */
    const LIRE = CFG.lire ?? 1250;
    /** Petit temps mort entre deux gestes. */
    const GESTE = CFG.geste ?? 420;

    const horloge = { t0: 0, debutVideo: 0 };
    const minutage = [];
    const anomalies = [];
    let souris = { x: CFG.largeur / 2, y: CFG.hauteur / 2 };

    function chapitre(titre) {
        const t = Date.now() - horloge.t0;
        minutage.push({ titre, ms: t, horodatage: mmss(t) });
        console.log(`  [${mmss(t)}]  ${titre}`);
    }

    function signaler(quoi, pourquoi) {
        anomalies.push({ quoi, pourquoi });
        console.log(`  ⚠  ${quoi} — ${pourquoi}`);
    }

    // ─── Gestes ─────────────────────────────────────────────────────────
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

    /** Defilement progressif : `crans` coups de molette de `pas` pixels. */
    async function defiler(page, crans, pas = 120, pause = 90) {
        for (let i = 0; i < crans; i++) {
            await page.mouse.wheel(0, pas);
            await attendre(pause);
        }
    }

    /**
     * Attend que la visionneuse PDF integree ait reellement peint sa page.
     *
     * Le rendu se fait hors DOM (plugin PDFium) : aucun selecteur ne dit « c'est
     * affiche ». On echantillonne donc une vignette du centre de la page. La
     * verification de l'URL n'est pas decorative : sans elle, une navigation
     * ratee laissait la page de l'application a l'ecran, sa vignette (riche)
     * passait le seuil, et la fonction annoncait « peint » a tort.
     */
    async function attendrePeintureDuPdf(page, plafondMs = 10000) {
        const debut = Date.now();
        while (Date.now() - debut < plafondMs) {
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
     * Ouvre un PDF local dans la visionneuse integree.
     *
     * Le passage par `about:blank` n'est pas cosmetique : naviguer directement
     * d'une page `http://` vers une URL `file://` est refuse par Chromium selon
     * le contexte, silencieusement.
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

    // ─── Assemblage MP4 ─────────────────────────────────────────────────
    /**
     * Cherche un ffmpeg capable d'encoder en H.264.
     *
     * Le ffmpeg livre avec Playwright est volontairement ampute (VP8 + WebM
     * uniquement, `--disable-everything`) : il enregistre mais ne sait pas
     * produire de MP4. On cherche donc un binaire complet, sans rien installer.
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

    async function finaliser(webm, nomBase) {
        if (!webm) { console.log('\n❌ Aucun enregistrement produit.'); return; }

        const nomBrut = join(CFG.brut, `${nomBase}.webm`);
        await rename(webm, nomBrut).catch(() => {});
        const source = existsSync(nomBrut) ? nomBrut : webm;

        const mp4 = join(CFG.sortie, `${nomBase}.mp4`);
        const ffmpeg = await trouverFfmpeg();

        // Amorce a couper : le chargement de la premiere page, enregistre avant
        // le premier chapitre. On garde 0,4 s pour ne pas ouvrir sur une coupe.
        const amorce = Math.max(0, (horloge.t0 - horloge.debutVideo) / 1000 - 0.4);
        // Duree a conserver, comptee A PARTIR de `-ss` : le deroule des
        // chapitres + 0,4 s d'amorce + 0,8 s de plan final. Retrancher `amorce`
        // ici amputerait la sortie du temps de chargement initial.
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
            source: { url: CFG.url, workspace: CFG.workspace, compte: CFG.email },
            vitesse: CFG.vitesse,
            duree: minutage.length ? minutage[minutage.length - 1].horodatage : '00:00',
            sections: minutage,
            anomalies,
            fichiers: { mp4: ffmpeg ? mp4 : null, webm: source },
        };
        await writeFile(join(CFG.sortie, `${nomBase}.minutage.json`),
            JSON.stringify(recap, null, 2) + '\n', 'utf8');

        console.log('\n─── Minutage ───');
        for (const s of minutage) console.log(`  ${s.horodatage}  ${s.titre}`);
        if (anomalies.length) {
            console.log('\n─── A signaler ───');
            for (const a of anomalies) console.log(`  • ${a.quoi} : ${a.pourquoi}`);
        }
        console.log(`\n✓ Recapitulatif : ${join(CFG.sortie, `${nomBase}.minutage.json`)}\n`);
    }

    return {
        attendre, LIRE, GESTE, horloge, minutage, anomalies,
        chapitre, signaler,
        versPoint, viser, cliquer, survoler, saisir, defiler,
        attendrePeintureDuPdf, ouvrirPdf, finaliser,
    };
}
