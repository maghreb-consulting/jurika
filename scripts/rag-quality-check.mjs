#!/usr/bin/env node
/**
 * Banc d'essai du chatbot RAG : pose une batterie de questions et rapporte,
 * pour chacune, le mode de reponse, le nombre de passages retrouves et les
 * sources citees.
 *
 * Sert a verifier qu'une question posee en francais naturel — et en particulier
 * les quatre suggestions affichees par la page ChatBot — retrouve bien quelque
 * chose dans le corpus du workspace.
 *
 * Usage :
 *   node scripts/rag-quality-check.mjs
 *   node scripts/rag-quality-check.mjs --workspace=JUR-DEMO2 --email=...
 *   node scripts/rag-quality-check.mjs --markdown        (tableau colle-able)
 */
import { readFile } from 'node:fs/promises';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { totp, msLeftInWindow } from './demo-video/totp.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));

const opt = (n, d) => {
    const m = process.argv.find(a => a.startsWith(`--${n}=`));
    return m ? m.split('=').slice(1).join('=') : d;
};
const MARKDOWN = process.argv.includes('--markdown');

const API = opt('api', 'http://localhost:8080');
const WORKSPACE = opt('workspace', 'JUR-DEMO2');
const EMAIL = opt('email', 'employe1@demo.jurika.ma');
const PASSWORD = opt('password', 'Demo@2026');

/**
 * Les 4 premieres sont EXACTEMENT les suggestions de `ChatbotPage.tsx` : une
 * question proposee par le produit doit fonctionner. Les suivantes couvrent des
 * formulations naturelles variees (Comment / Que prevoit / Quelles sont /
 * Est-ce que / Pourquoi / Combien), plus quelques questions portant sur le
 * contenu reel du corpus de demonstration (les statuts d'Atlas Conseil).
 */
const QUESTIONS = [
    ['suggestion', 'Comment creer une SARL ?'],
    ['suggestion', 'Capital minimum SARL ?'],
    ['suggestion', 'Procedure de dissolution'],
    ['suggestion', 'Succursale etrangere au Maroc ?'],

    ['naturelle', 'Que prevoient les statuts en cas de cession de parts a un tiers ?'],
    ['naturelle', 'Quelle majorite faut-il pour agreer un nouvel associe ?'],
    ['naturelle', 'Quelles sont les conditions de cession des parts sociales ?'],
    ['naturelle', 'Est-ce que le gerant peut vendre seul un bien de la societe ?'],
    ['naturelle', 'Combien de temps la societe a-t-elle pour repondre a un projet de cession ?'],
    ['naturelle', 'Pourquoi les statuts ont-ils ete refondus ?'],
    ['naturelle', 'Quel est le capital social de la societe et comment est-il reparti ?'],
    ['naturelle', 'Ou se trouve le siege social et peut-on le transferer ?'],
    ['naturelle', 'Comment est affecte le resultat de l exercice ?'],
    ['naturelle', 'Qui dirige la societe ?'],
];

async function appel(chemin, corps, token) {
    const r = await fetch(API + chemin, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
        body: JSON.stringify(corps),
    });
    const texte = await r.text();
    let body; try { body = texte ? JSON.parse(texte) : null; } catch { body = texte; }
    if (!r.ok) throw new Error(`${chemin} -> ${r.status} ${JSON.stringify(body).slice(0, 200)}`);
    return body;
}

async function connexion() {
    const secrets = JSON.parse(
        await readFile(join(HERE, 'demo-video', 'demo-secrets.json'), 'utf8').catch(() => '{}'));
    const l = await appel('/api/v1/auth/login',
        { workspaceCode: WORKSPACE, email: EMAIL, password: PASSWORD });
    if (!l.requires2fa) return l.accessToken;
    const cle = secrets[`${WORKSPACE}:${EMAIL}`]?.secret;
    if (!cle) throw new Error(`2FA active mais pas de secret TOTP pour ${WORKSPACE}:${EMAIL}`);
    if (msLeftInWindow() < 4000) await new Promise(r => setTimeout(r, msLeftInWindow() + 400));
    const v = await appel('/api/v1/auth/verify-2fa',
        // Code en CHAINE : un code commencant par zero serait tronque en nombre.
        { userId: l.userId, workspaceId: l.workspaceId, code: totp(cle) });
    return v.accessToken;
}

function sourcesLisibles(reponse) {
    const s = reponse.sources ?? [];
    if (!s.length) return '—';
    const vues = new Set();
    for (const c of s) {
        const ref = c.reference ?? '?';
        const art = c.article ? ` (${c.article})` : '';
        vues.add(ref + art);
    }
    return [...vues].slice(0, 3).join(' · ');
}

async function main() {
    console.log(`\n▶ Banc d'essai RAG — ${API} · ${WORKSPACE} · ${EMAIL}\n`);
    const token = await connexion();

    const lignes = [];
    for (const [famille, question] of QUESTIONS) {
        const t0 = Date.now();
        let r;
        try {
            r = await appel('/api/v1/chatbot/ask', { question }, token);
        } catch (e) {
            lignes.push({ famille, question, mode: 'ERREUR', passages: 0, ms: Date.now() - t0,
                          sources: e.message.slice(0, 80), extrait: '' });
            continue;
        }
        lignes.push({
            famille,
            question,
            mode: r.ragMode ?? '?',
            passages: r.chunksRetrieved ?? 0,
            ms: Date.now() - t0,
            sources: sourcesLisibles(r),
            extrait: (r.reponse ?? '').replace(/\s+/g, ' ').slice(0, 110),
        });
    }

    if (MARKDOWN) {
        console.log('| Question | Passages | Mode | Source citée |');
        console.log('|---|---:|---|---|');
        for (const l of lignes) {
            console.log(`| ${l.question} | ${l.passages} | ${l.mode} | ${l.sources} |`);
        }
    } else {
        for (const l of lignes) {
            const drapeau = l.passages > 0 ? '✓' : '✗';
            console.log(`${drapeau} [${l.famille.padEnd(10)}] ${l.question}`);
            console.log(`   passages=${String(l.passages).padStart(2)}  mode=${l.mode.padEnd(4)}  ${l.ms} ms  sources: ${l.sources}`);
            console.log(`   « ${l.extrait}… »\n`);
        }
    }

    const vides = lignes.filter(l => l.passages === 0);
    const suggestionsVides = vides.filter(l => l.famille === 'suggestion');
    console.log('─'.repeat(72));
    console.log(`  ${lignes.length - vides.length}/${lignes.length} questions retrouvent au moins un passage.`);
    if (suggestionsVides.length) {
        console.log(`  ⚠  ${suggestionsVides.length}/4 suggestions affichees par le produit ne retrouvent RIEN :`);
        for (const s of suggestionsVides) console.log(`       — ${s.question}`);
    }
    if (vides.length) {
        console.log(`  ⚠  questions sans passage :`);
        for (const s of vides) console.log(`       — ${s.question}`);
    }
    console.log('');
    process.exitCode = suggestionsVides.length ? 1 : 0;
}

main().catch(e => { console.error('\n❌', e.message); process.exit(1); });
