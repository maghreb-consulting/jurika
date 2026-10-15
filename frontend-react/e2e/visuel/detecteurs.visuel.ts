import { test, expect, type Page } from '@playwright/test';
import { analyser, type OptionsAnalyse, type ResultatAnalyse, type TypeDefaut } from './detecteurs';

/**
 * Lot L6b (E4) : tests unitaires des detecteurs, sur des pages HTML minimales.
 * Chaque defaut a son temoin positif (la page fautive est signalee, l'element nomme) et
 * son temoin negatif (la page saine n'est pas signalee). Aucun serveur : page.setContent.
 */

test.use({ viewport: { width: 1366, height: 900 } });

const BASE = `<style>
  html, body { margin: 0; background: #fff; color: #111; font: 16px/1.4 sans-serif; }
  .abs { position: absolute; }
</style>`;

async function defautsDe(page: Page, corps: string, options: OptionsAnalyse = {}) {
  await page.setContent(`<!doctype html><html lang="fr"><head>${BASE}</head><body>${corps}</body></html>`);
  return analyser(page, options);
}

function deType(r: ResultatAnalyse, type: TypeDefaut) {
  return r.defauts.filter((d) => d.type === type);
}

test.describe('chevauchement', () => {
  test('deux boutons qui se recouvrent sont signales et nommes', async ({ page }) => {
    const r = await defautsDe(
      page,
      `<button class="abs" style="left:10px;top:10px;width:120px;height:40px">Enregistrer</button>
       <button class="abs" style="left:80px;top:20px;width:120px;height:40px">Annuler</button>`,
      { detecteurs: ['chevauchement'] },
    );
    const c = deType(r, 'chevauchement');
    expect(c).toHaveLength(1);
    expect(c[0].message).toContain('"Enregistrer"');
    expect(c[0].message).toContain('"Annuler"');
  });

  test('deux boutons voisins qui se touchent a 1 px pres ne sont pas signales', async ({ page }) => {
    const r = await defautsDe(
      page,
      `<button class="abs" style="left:10px;top:10px;width:100px;height:40px">Un</button>
       <button class="abs" style="left:109px;top:10px;width:100px;height:40px">Deux</button>`,
      { detecteurs: ['chevauchement'] },
    );
    expect(deType(r, 'chevauchement')).toEqual([]);
    expect(r.examines.chevauchement).toBe(2);
  });

  test('un texte pose sur un bouton est signale', async ({ page }) => {
    const r = await defautsDe(
      page,
      `<button class="abs" style="left:10px;top:10px;width:200px;height:40px">Valider le dossier</button>
       <p class="abs" style="margin:0;left:20px;top:20px">Mention qui recouvre</p>`,
      { detecteurs: ['chevauchement'] },
    );
    const c = deType(r, 'chevauchement');
    expect(c).toHaveLength(1);
    expect(c[0].message).toContain('Mention qui recouvre');
  });

  test('deux textes qui se recouvrent sont signales', async ({ page }) => {
    const r = await defautsDe(
      page,
      `<span class="abs" style="left:10px;top:10px">Premier libelle long</span>
       <span class="abs" style="left:30px;top:14px">Second libelle</span>`,
      { detecteurs: ['chevauchement'] },
    );
    expect(deType(r, 'chevauchement')).toHaveLength(1);
  });

  test('le libelle d un bouton (enfant) et une carte (parent) ne sont pas signales', async ({ page }) => {
    const r = await defautsDe(
      page,
      `<div><a href="#x" style="display:block;padding:20px">Ouvrir <span>le dossier</span></a>
       <label>Nom <input value="SARL Atlas"></label></div>`,
      { detecteurs: ['chevauchement'] },
    );
    expect(deType(r, 'chevauchement')).toEqual([]);
  });

  test('des segments en ligne d un meme paragraphe qui passent a la ligne ne sont pas signales', async ({ page }) => {
    const r = await defautsDe(
      page,
      `<p style="width:300px">Le contrat est conclu <strong>par voie electronique</strong>, conformement au dahir formant code des obligations et des contrats, <span>et son utilisation par le Cabinet dans le cadre de son activite</span> <em>professionnelle</em>.</p>`,
      { detecteurs: ['chevauchement'] },
    );
    expect(deType(r, 'chevauchement')).toEqual([]);
  });

  test('des lignes a interligne serre (zone du texte plus haute que la ligne) ne sont pas signalees', async ({ page }) => {
    const r = await defautsDe(
      page,
      `<div style="width:200px;font-size:12px;line-height:1"><p style="margin:0">Augmentation de capital</p><p style="margin:0">Dossier suivi par le cabinet</p><span>Nadia Idrissi</span></div>`,
      { detecteurs: ['chevauchement'] },
    );
    expect(deType(r, 'chevauchement')).toEqual([]);
  });

  test('un texte tronque (truncate, line-clamp) n est compare que sur sa partie visible', async ({ page }) => {
    const r = await defautsDe(
      page,
      `<div style="display:flex;width:300px;gap:8px">
         <span style="flex:1;min-width:0;overflow:hidden;white-space:nowrap;text-overflow:ellipsis">TK-2026-0106 Augmentation de capital de la cooperative</span>
         <span style="flex:none">EN COURS</span></div>
       <p style="width:200px;margin:0;overflow:hidden;display:-webkit-box;-webkit-box-orient:vertical;-webkit-line-clamp:1">Dossier suivi par le cabinet, pieces du client recues</p>
       <span>Nadia Idrissi</span>`,
      { detecteurs: ['chevauchement'] },
    );
    expect(deType(r, 'chevauchement')).toEqual([]);
  });

  test('un menu deroulant (couche positionnee, z-index eleve) sur le contenu n est pas signale', async ({ page }) => {
    const r = await defautsDe(
      page,
      `<header style="position:relative"><button>Notifications</button>
         <div style="position:absolute;top:100%;left:0;z-index:50;background:#fff;width:300px"><button>Echeance depassee</button><p>Depot au greffe</p></div></header>
       <main><p>Tableau de bord du cabinet</p><button>Voir les tickets</button></main>`,
      { detecteurs: ['chevauchement'] },
    );
    expect(deType(r, 'chevauchement')).toEqual([]);
  });

  test('un bouton pose dans un champ (afficher le mot de passe) n est pas signale', async ({ page }) => {
    const r = await defautsDe(
      page,
      `<div style="position:relative;width:300px"><input type="password" style="width:100%;height:40px;box-sizing:border-box">
         <button style="position:absolute;right:6px;top:6px;width:28px;height:28px" aria-label="Afficher le mot de passe"></button></div>`,
      { detecteurs: ['chevauchement'] },
    );
    expect(deType(r, 'chevauchement')).toEqual([]);
  });

  test('une bulle fixe (assistant) qui recouvre un bouton est signalee', async ({ page }) => {
    const r = await defautsDe(
      page,
      `<button style="position:absolute;right:20px;bottom:30px;width:120px;height:40px">Desactiver</button>
       <button data-testid="assistant-bulle" style="position:fixed;right:24px;bottom:24px;width:56px;height:56px;z-index:50" aria-label="Ouvrir le chatbot juridique"></button>`,
      { detecteurs: ['chevauchement'] },
    );
    const c = deType(r, 'chevauchement');
    expect(c).toHaveLength(1);
    expect(c[0].message).toContain('assistant-bulle');
  });

  test('une fenetre modale ouverte au-dessus du contenu n est pas signalee', async ({ page }) => {
    await page.setContent(
      `<!doctype html><html><head>${BASE}</head><body>
       <button style="margin:100px">Derriere</button>
       <dialog id="d"><button>Confirmer</button><p>Texte de la fenetre</p></dialog>
       <script>document.getElementById('d').showModal();</script></body></html>`,
    );
    const r = await analyser(page, { detecteurs: ['chevauchement'] });
    expect(deType(r, 'chevauchement')).toEqual([]);
  });

  test('un element cache ou masque aux yeux n est pas signale', async ({ page }) => {
    const r = await defautsDe(
      page,
      `<button class="abs" style="left:10px;top:10px;width:120px;height:40px">Visible</button>
       <button class="abs" style="left:20px;top:10px;width:120px;height:40px;visibility:hidden">Cache</button>
       <span class="abs" style="left:20px;top:10px;width:1px;height:1px;overflow:hidden;clip-path:inset(50%)">Lecteur</span>`,
      { detecteurs: ['chevauchement'] },
    );
    expect(deType(r, 'chevauchement')).toEqual([]);
  });

  test('une couche prevue (selecteur declare) n est pas signalee', async ({ page }) => {
    const r = await defautsDe(
      page,
      `<button class="abs" style="left:10px;top:10px;width:120px;height:40px">Contenu</button>
       <div data-couche="toasts" style="position:fixed;left:10px;top:10px"><button>Fermer</button></div>`,
      { detecteurs: ['chevauchement'], couches: ['[data-couche="toasts"]'] },
    );
    expect(deType(r, 'chevauchement')).toEqual([]);
  });

  test('un en-tete collant qui recouvre le contenu a defilement nul est signale', async ({ page }) => {
    const r = await defautsDe(
      page,
      `<header style="position:fixed;top:0;left:0;right:0;height:56px;background:#fff"><a href="#">Accueil</a></header>
       <main><a href="#a">Premier lien du contenu</a><div style="height:3000px"></div></main>`,
      { detecteurs: ['chevauchement'] },
    );
    expect(deType(r, 'chevauchement')).toHaveLength(1);
  });

  test('un en-tete collant qui laisse la place au contenu n est pas signale', async ({ page }) => {
    const r = await defautsDe(
      page,
      `<header style="position:sticky;top:0;height:56px;background:#fff"><a href="#">Accueil</a></header>
       <main><a href="#a">Premier lien du contenu</a><div style="height:3000px"></div></main>`,
      { detecteurs: ['chevauchement'] },
    );
    expect(deType(r, 'chevauchement')).toEqual([]);
  });

  test('un element defile hors de son conteneur n est pas signale', async ({ page }) => {
    const r = await defautsDe(
      page,
      `<div style="height:60px;overflow:auto"><div style="height:60px"></div><button>Plus bas</button></div>
       <button class="abs" style="left:0;top:62px">Dessous</button>`,
      { detecteurs: ['chevauchement'] },
    );
    expect(deType(r, 'chevauchement')).toEqual([]);
  });
});

test.describe('debordement', () => {
  test('un defilement horizontal de la page est signale et l element fautif nomme', async ({ page }) => {
    const r = await defautsDe(page, `<div data-testid="tableau-large" style="width:2000px">Tableau trop large</div>`, {
      detecteurs: ['debordement'],
    });
    const d = deType(r, 'debordement-page');
    expect(d).toHaveLength(1);
    expect(d[0].message).toContain('tableau-large');
  });

  test('une page a la largeur de l ecran n est pas signalee', async ({ page }) => {
    const r = await defautsDe(page, `<div style="width:100%">Contenu</div>`, { detecteurs: ['debordement'] });
    expect(r.defauts).toEqual([]);
  });

  test('un texte qui deborde de sa boite est signale', async ({ page }) => {
    const r = await defautsDe(
      page,
      `<div style="width:80px;white-space:nowrap">Societe a responsabilite limitee</div>`,
      { detecteurs: ['debordement'] },
    );
    const d = deType(r, 'debordement-texte');
    expect(d).toHaveLength(1);
    expect(d[0].message).toContain('Societe a responsabilite limitee');
  });

  test('un texte coupe sans ellipsis est signale', async ({ page }) => {
    const r = await defautsDe(
      page,
      `<div style="width:80px;white-space:nowrap;overflow:hidden">Societe a responsabilite limitee</div>`,
      { detecteurs: ['debordement'] },
    );
    expect(deType(r, 'texte-coupe')).toHaveLength(1);
  });

  test('un texte coupe par son conteneur en hauteur est signale', async ({ page }) => {
    const r = await defautsDe(
      page,
      `<div style="width:100px;height:20px;overflow:hidden"><p style="margin:0">Une ligne puis une deuxieme puis une troisieme</p></div>`,
      { detecteurs: ['debordement'] },
    );
    expect(deType(r, 'texte-coupe')).toHaveLength(1);
  });

  test('une ellipsis voulue, un line-clamp ou un conteneur qui defile ne sont pas signales', async ({ page }) => {
    const r = await defautsDe(
      page,
      `<div style="width:80px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">Societe a responsabilite limitee</div>
       <div style="width:100px;overflow:hidden;display:-webkit-box;-webkit-box-orient:vertical;-webkit-line-clamp:2">Une ligne puis une deuxieme puis une troisieme puis une quatrieme</div>
       <div style="width:80px;overflow-x:auto;white-space:nowrap">Societe a responsabilite limitee</div>`,
      { detecteurs: ['debordement'] },
    );
    expect(r.defauts).toEqual([]);
    expect(r.examines.debordement).toBe(3);
  });
});

test.describe('contraste', () => {
  test('un texte gris #777 sur blanc (4,48:1) est signale, #767676 (4,54:1) ne l est pas', async ({ page }) => {
    const r = await defautsDe(
      page,
      `<p style="color:#777777">Texte trop pale</p><p style="color:#767676">Texte juste conforme</p>`,
      { detecteurs: ['contraste'] },
    );
    const c = deType(r, 'contraste');
    expect(c).toHaveLength(1);
    expect(c[0].message).toContain('Texte trop pale');
    expect(r.examines.contraste).toBe(2);
  });

  test('le seuil du grand texte est 3:1', async ({ page }) => {
    const r = await defautsDe(
      page,
      `<p style="color:#949494;font-size:24px">Grand titre (3,03:1)</p>
       <p style="color:#949494;font-size:14px">Petit texte (3,03:1)</p>`,
      { detecteurs: ['contraste'] },
    );
    const c = deType(r, 'contraste');
    expect(c).toHaveLength(1);
    expect(c[0].message).toContain('Petit texte');
  });

  test('le fond effectif est celui des ancetres, couleurs oklch comprises (theme sombre)', async ({ page }) => {
    const r = await defautsDe(
      page,
      `<div style="background:oklch(0.2 0.03 260)"><div style="background:transparent">
         <span style="color:oklch(0.95 0 0)">Lisible en sombre</span>
         <span style="color:oklch(0.35 0.03 260)">Illisible en sombre</span></div></div>`,
      { detecteurs: ['contraste'] },
    );
    const c = deType(r, 'contraste');
    expect(c).toHaveLength(1);
    expect(c[0].message).toContain('Illisible en sombre');
  });

  test('l opacite d un ancetre compte', async ({ page }) => {
    const r = await defautsDe(page, `<div style="opacity:0.4"><p style="color:#333">Texte estompe</p></div>`, {
      detecteurs: ['contraste'],
    });
    expect(deType(r, 'contraste')).toHaveLength(1);
  });

  test('un bouton desactive est exempte et un fond en degrade est compte comme non evalue', async ({ page }) => {
    const r = await defautsDe(
      page,
      `<button disabled style="color:#ccc;background:#fff">Desactive</button>
       <div style="background:linear-gradient(#000,#fff)"><span style="color:#888">Sur degrade</span></div>`,
      { detecteurs: ['contraste'] },
    );
    expect(r.defauts).toEqual([]);
    expect(r.examines.contrasteNonEvalue).toBe(1);
  });
});
