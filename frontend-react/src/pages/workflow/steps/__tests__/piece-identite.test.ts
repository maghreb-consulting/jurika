import { describe, expect, it } from 'vitest';
import { libelleNumeroPiece, pieceInvalidMessage, pieceSansExtraction } from '../pieceIdentite';

/**
 * Lot L4a (E3) : associe ou gerant etranger. La creation n'acceptait qu'une CIN
 * marocaine (format controle, extraction exigee) : un passeport ou une carte de sejour
 * ne pouvait pas etre saisi.
 */
describe('Pièce d’identité', () => {
  it('contrôle le format marocain pour une CIN (type absent = CIN)', () => {
    expect(pieceInvalidMessage('CIN', 'BK188421')).toBeNull();
    expect(pieceInvalidMessage('CIN', '123')).not.toBeNull();
    expect(pieceInvalidMessage(undefined, '123')).not.toBeNull();
  });

  it('accepte un passeport ou une carte de séjour, sans format marocain', () => {
    expect(pieceInvalidMessage('passeport', '20FV12345')).toBeNull();
    expect(pieceInvalidMessage('carte de séjour', 'E1234567')).toBeNull();
    expect(pieceInvalidMessage('passeport', '!!')).not.toBeNull();
  });

  it('n’exige l’extraction que pour la CIN, et nomme le numéro selon la pièce', () => {
    expect(pieceSansExtraction('CIN')).toBe(false);
    expect(pieceSansExtraction('passeport')).toBe(true);
    expect(libelleNumeroPiece('passeport')).toBe('N° de passeport');
    expect(libelleNumeroPiece(undefined)).toBe('N° CIN');
  });
});
