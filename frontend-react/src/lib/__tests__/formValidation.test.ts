import { describe, it, expect } from 'vitest';
import {
  isEmail,
  isFilled,
  requiredMsg,
  emailMsg,
  minLengthMsg,
  maxLengthMsg,
  patternMsg,
  matchMsg,
  firstError,
  hasErrors,
  validationMessages,
} from '../formValidation';

describe('formValidation', () => {
  describe('isFilled', () => {
    it('rejette vide / espaces / null', () => {
      expect(isFilled('')).toBe(false);
      expect(isFilled('   ')).toBe(false);
      expect(isFilled(null)).toBe(false);
      expect(isFilled(undefined)).toBe(false);
    });
    it('accepte une valeur', () => {
      expect(isFilled('x')).toBe(true);
    });
  });

  describe('isEmail', () => {
    it('accepte une adresse plausible', () => {
      expect(isEmail('a.b@jurika.ma')).toBe(true);
    });
    it('rejette une adresse malformée', () => {
      expect(isEmail('nope')).toBe(false);
      expect(isEmail('a@b')).toBe(false);
      expect(isEmail('a b@c.d')).toBe(false);
    });
  });

  describe('requiredMsg', () => {
    it('renvoie un message si vide', () => {
      expect(requiredMsg('')).toBe(validationMessages.required);
    });
    it('renvoie null si rempli', () => {
      expect(requiredMsg('ok')).toBeNull();
    });
    it('accepte un message custom', () => {
      expect(requiredMsg('', 'Obligatoire !')).toBe('Obligatoire !');
    });
  });

  describe('emailMsg', () => {
    it('vide + requis → message requis', () => {
      expect(emailMsg('')).toBe(validationMessages.required);
    });
    it('vide + optionnel → null', () => {
      expect(emailMsg('', { required: false })).toBeNull();
    });
    it('malformé → message email', () => {
      expect(emailMsg('bad')).toBe(validationMessages.email);
    });
    it('valide → null', () => {
      expect(emailMsg('a@b.co')).toBeNull();
    });
  });

  describe('minLengthMsg / maxLengthMsg', () => {
    it('min : trop court → message', () => {
      expect(minLengthMsg('ab', 3)).toBe(validationMessages.minLength(3));
    });
    it('min : vide → null (délégué à requiredMsg)', () => {
      expect(minLengthMsg('', 3)).toBeNull();
    });
    it('max : trop long → message', () => {
      expect(maxLengthMsg('abcd', 3)).toBe(validationMessages.maxLength(3));
    });
    it('max : ok → null', () => {
      expect(maxLengthMsg('abc', 3)).toBeNull();
    });
  });

  describe('patternMsg', () => {
    it('non conforme → message', () => {
      expect(patternMsg('12', /^\d{6}$/, 'Six chiffres.')).toBe('Six chiffres.');
    });
    it('conforme → null', () => {
      expect(patternMsg('123456', /^\d{6}$/)).toBeNull();
    });
  });

  describe('matchMsg', () => {
    it('différents → message', () => {
      expect(matchMsg('a', 'b')).toBe(validationMessages.mismatch);
    });
    it('égaux → null', () => {
      expect(matchMsg('a', 'a')).toBeNull();
    });
  });

  describe('firstError', () => {
    it('renvoie le premier message non nul', () => {
      expect(firstError(() => null, () => 'deux', () => 'trois')).toBe('deux');
    });
    it('renvoie null si tout est valide', () => {
      expect(firstError(() => null, () => null)).toBeNull();
    });
  });

  describe('hasErrors', () => {
    it('vrai si au moins une erreur', () => {
      expect(hasErrors({ a: 'x', b: undefined })).toBe(true);
    });
    it('faux si aucune', () => {
      expect(hasErrors({ a: undefined })).toBe(false);
    });
  });
});
