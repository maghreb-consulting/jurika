import { describe, expect, it } from 'vitest';
import { adresseTempsReel } from '../realtimeAdresse';

/**
 * Lot L5b (E1) : le service temps reel est joint par la MEME origine que le front
 * (nginx relaie /temps-reel/), sauf adresse explicite VITE_REALTIME_URL. Defaut
 * constate sur le Z440 : la variable lue au build etait absente (.env.local portait
 * VITE_WS_URL), le front visait http://localhost:3000, donc la machine de chaque
 * utilisateur : messagerie « Hors-ligne ».
 */
describe('adresseTempsReel', () => {
  it('sans adresse explicite : meme origine, chemin /temps-reel', () => {
    expect(adresseTempsReel(undefined, 'http://192.168.1.251')).toEqual({
      origine: 'http://192.168.1.251',
      cheminSocket: '/temps-reel/socket.io',
      baseRest: 'http://192.168.1.251/temps-reel/api/v1',
    });
    expect(adresseTempsReel('  ', 'https://app.jurika.ma').baseRest).toBe('https://app.jurika.ma/temps-reel/api/v1');
  });

  it('adresse explicite : utilisee telle quelle (developpement avec Vite)', () => {
    expect(adresseTempsReel('http://localhost:3000/', 'http://localhost:5173')).toEqual({
      origine: 'http://localhost:3000',
      cheminSocket: '/socket.io',
      baseRest: 'http://localhost:3000/api/v1',
    });
  });

  it("n'utilise jamais localhost par defaut", () => {
    expect(adresseTempsReel(undefined, 'http://10.0.0.5').origine).not.toContain('localhost');
  });
});
