import { describe, it, expect, vi, afterEach } from 'vitest';
import { copierTexte } from '../pressePapiers';

const origine = Object.getOwnPropertyDescriptor(window.navigator, 'clipboard');

afterEach(() => {
  if (origine) Object.defineProperty(window.navigator, 'clipboard', origine);
  else Reflect.deleteProperty(window.navigator, 'clipboard');
  vi.restoreAllMocks();
});

describe('copierTexte (lot L6b E13, contexte non securise)', () => {
  it('sans navigator.clipboard (HTTP) : repli execCommand, aucune erreur levee', async () => {
    Object.defineProperty(window.navigator, 'clipboard', { value: undefined, configurable: true });
    const exec = vi.fn(() => true);
    Object.defineProperty(document, 'execCommand', { value: exec, configurable: true });
    await expect(copierTexte('JUR-ABCDE')).resolves.toBe(true);
    expect(exec).toHaveBeenCalledWith('copy');
    expect(document.querySelector('textarea')).toBeNull();
  });

  it('avec navigator.clipboard : l API du navigateur est utilisee', async () => {
    const writeText = vi.fn(() => Promise.resolve());
    Object.defineProperty(window.navigator, 'clipboard', { value: { writeText }, configurable: true });
    await expect(copierTexte('code')).resolves.toBe(true);
    expect(writeText).toHaveBeenCalledWith('code');
  });

  it('copie refusee partout : rend faux, ne leve pas', async () => {
    Object.defineProperty(window.navigator, 'clipboard', {
      value: { writeText: () => Promise.reject(new Error('refus')) }, configurable: true,
    });
    Object.defineProperty(document, 'execCommand', { value: () => false, configurable: true });
    await expect(copierTexte('x')).resolves.toBe(false);
  });
});
