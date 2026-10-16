import '@testing-library/jest-dom/vitest';
import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { SignupSuccess } from '../SignupSuccess';

const origine = Object.getOwnPropertyDescriptor(window.navigator, 'clipboard');
afterEach(() => {
  if (origine) Object.defineProperty(window.navigator, 'clipboard', origine);
  else Reflect.deleteProperty(window.navigator, 'clipboard');
});

// Lot L6b (E13) : en HTTP (Z440), navigator.clipboard n'existe pas. Le bouton levait une
// TypeError (erreur de console) et la copie echouait sans rien dire.
describe('SignupSuccess : copie du code du cabinet hors contexte securise', () => {
  it('copie par le repli, sans erreur, et le dit', async () => {
    Object.defineProperty(window.navigator, 'clipboard', { value: undefined, configurable: true });
    const exec = vi.fn(() => true);
    Object.defineProperty(document, 'execCommand', { value: exec, configurable: true });
    render(<MemoryRouter><SignupSuccess workspaceCode="JUR-ABCDE" email="a@jurika.ma" /></MemoryRouter>);
    fireEvent.click(screen.getByRole('button', { name: 'Copier le code' }));
    await waitFor(() => expect(screen.getByRole('button', { name: 'Code copié' })).toBeInTheDocument());
    expect(exec).toHaveBeenCalledWith('copy');
  });
});
