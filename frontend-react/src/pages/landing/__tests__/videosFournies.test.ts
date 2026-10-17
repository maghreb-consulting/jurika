import { describe, it, expect } from 'vitest';
import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { DIMENSIONS_VIDEO, LIMITES_VIDEO, NOMS_VIDEOS, fichiersVideos } from '../videos';

/**
 * Lot L6b (E10, V4) : les videos de la vitrine sont livrees dans public/vitrine/videos, en
 * MP4 (H.264) et WebM (VP9), sans son, de 6 a 12 secondes, moins de 1,5 Mo chacune, avec
 * leur image fixe en WebP. Durees et pistes lues dans les fichiers eux-memes (en-tetes du
 * conteneur), jamais dans une liste recopiee.
 */

const DOSSIER = resolve(__dirname, '../../../../public/vitrine/videos');

/** Boites MP4 (ISO BMFF) : type et contenu, a un niveau donne. */
function boites(b: Buffer, debut = 0, fin = b.length): { type: string; corps: Buffer }[] {
  const out: { type: string; corps: Buffer }[] = [];
  let o = debut;
  while (o + 8 <= fin) {
    let taille = b.readUInt32BE(o);
    const type = b.toString('latin1', o + 4, o + 8);
    let entete = 8;
    if (taille === 1) {
      taille = Number(b.readBigUInt64BE(o + 8));
      entete = 16;
    }
    if (taille < entete || o + taille > fin) break;
    out.push({ type, corps: b.subarray(o + entete, o + taille) });
    o += taille;
  }
  return out;
}

function enfant(b: Buffer, chemin: string[]): Buffer[] {
  let niveau = [b];
  for (const t of chemin) niveau = niveau.flatMap((x) => boites(x).filter((y) => y.type === t).map((y) => y.corps));
  return niveau;
}

/** Duree d'un MP4 (mvhd : duree / echelle de temps), en secondes. */
function dureeMp4(b: Buffer): number {
  const [mvhd] = enfant(b, ['moov', 'mvhd']);
  if (!mvhd) throw new Error('mvhd absent');
  const v = mvhd[0];
  const echelle = v === 1 ? mvhd.readUInt32BE(20) : mvhd.readUInt32BE(12);
  const duree = v === 1 ? Number(mvhd.readBigUInt64BE(24)) : mvhd.readUInt32BE(16);
  return duree / echelle;
}

/** Types de pistes d'un MP4 (hdlr de chaque trak : vide, soun...). */
function pistesMp4(b: Buffer): string[] {
  return enfant(b, ['moov', 'trak', 'mdia', 'hdlr']).map((h) => h.toString('latin1', 8, 12));
}

/** Element EBML (WebM) : identifiant connu, valeur lue apres la taille (VINT). */
function elementEbml(b: Buffer, id: number[]): Buffer | null {
  const motif = Buffer.from(id);
  const i = b.indexOf(motif);
  if (i < 0) return null;
  const premier = b[i + motif.length];
  let longueur = 1;
  while (longueur <= 8 && !(premier & (0x80 >> (longueur - 1)))) longueur++;
  let taille = premier & (0xff >> longueur);
  for (let k = 1; k < longueur; k++) taille = taille * 256 + b[i + motif.length + k];
  const debut = i + motif.length + longueur;
  return b.subarray(debut, debut + taille);
}

/** Duree d'un WebM (Info : Duration x TimecodeScale), en secondes. */
function dureeWebm(b: Buffer): number {
  const echelle = elementEbml(b, [0x2a, 0xd7, 0xb1]);
  const ns = echelle ? echelle.readUIntBE(0, echelle.length) : 1_000_000;
  const d = elementEbml(b, [0x44, 0x89]);
  if (!d) throw new Error('Duration absente');
  const valeur = d.length === 4 ? d.readFloatBE(0) : d.readDoubleBE(0);
  return (valeur * ns) / 1e9;
}

/** Codecs des pistes d'un WebM (CodecID de chaque TrackEntry : V_VP9, A_OPUS...). */
function codecsWebm(b: Buffer): string[] {
  return [...b.toString('latin1').matchAll(/\x86[\x80-\xff]([VA]_[A-Z0-9]+)/g)].map((m) => m[1]);
}

/** Dimensions d'une image WebP (VP8 avec ou sans perte, VP8X). */
function dimensionsWebp(b: Buffer): { largeur: number; hauteur: number } {
  expect(b.toString('latin1', 0, 4)).toBe('RIFF');
  expect(b.toString('latin1', 8, 12)).toBe('WEBP');
  const morceau = b.toString('latin1', 12, 16);
  if (morceau === 'VP8 ') return { largeur: b.readUInt16LE(26) & 0x3fff, hauteur: b.readUInt16LE(28) & 0x3fff };
  if (morceau === 'VP8L') {
    const bits = b.readUInt32LE(21);
    return { largeur: (bits & 0x3fff) + 1, hauteur: ((bits >> 14) & 0x3fff) + 1 };
  }
  return { largeur: b.readUIntLE(24, 3) + 1, hauteur: b.readUIntLE(27, 3) + 1 };
}

describe('Vitrine : videos fournies dans public/vitrine/videos', () => {
  it('trouve les quinze fichiers attendus (MP4, WebM, image fixe WebP)', () => {
    const fichiers = fichiersVideos();
    expect(fichiers).toHaveLength(15);
    const manquants = fichiers.filter((f) => !existsSync(resolve(DOSSIER, f)));
    expect(manquants, `videos absentes de public/vitrine/videos : ${manquants.join(', ')}`).toEqual([]);
  });

  for (const nom of NOMS_VIDEOS) {
    describe(nom, () => {
      it('MP4 : H.264, une seule piste video, sans son, 6 a 12 s, moins de 1,5 Mo', () => {
        const b = readFileSync(resolve(DOSSIER, `${nom}.mp4`));
        expect(b.length).toBeLessThan(LIMITES_VIDEO.poidsMaxOctets);
        expect(b.toString('latin1', 4, 8)).toBe('ftyp');
        expect(pistesMp4(b)).toEqual(['vide']);
        expect(b.includes(Buffer.from('avc1'))).toBe(true);
        const d = dureeMp4(b);
        expect(d).toBeGreaterThanOrEqual(LIMITES_VIDEO.dureeMinS);
        expect(d).toBeLessThanOrEqual(LIMITES_VIDEO.dureeMaxS);
      });

      it('WebM : VP9, une seule piste video, sans son, 6 a 12 s, moins de 1,5 Mo', () => {
        const b = readFileSync(resolve(DOSSIER, `${nom}.webm`));
        expect(b.length).toBeLessThan(LIMITES_VIDEO.poidsMaxOctets);
        expect(b.readUInt32BE(0)).toBe(0x1a45dfa3);
        expect(codecsWebm(b)).toEqual(['V_VP9']);
        const d = dureeWebm(b);
        expect(d).toBeGreaterThanOrEqual(LIMITES_VIDEO.dureeMinS);
        expect(d).toBeLessThanOrEqual(LIMITES_VIDEO.dureeMaxS);
      });

      it('image fixe : WebP aux dimensions de la video, legere', () => {
        const b = readFileSync(resolve(DOSSIER, `${nom}.webp`));
        expect(dimensionsWebp(b)).toEqual({ largeur: DIMENSIONS_VIDEO.largeur, hauteur: DIMENSIONS_VIDEO.hauteur });
        expect(b.length).toBeLessThan(150_000);
      });

      it('MP4 et WebM ont la meme duree (meme coupe), a 0,3 s pres', () => {
        const mp4 = dureeMp4(readFileSync(resolve(DOSSIER, `${nom}.mp4`)));
        const webm = dureeWebm(readFileSync(resolve(DOSSIER, `${nom}.webm`)));
        expect(Math.abs(mp4 - webm)).toBeLessThan(0.3);
      });
    });
  }
});
