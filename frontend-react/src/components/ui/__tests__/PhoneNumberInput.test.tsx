/// <reference types="vitest" />
import '@testing-library/jest-dom/vitest';
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, within } from '@testing-library/react';
import {
  PhoneNumberInput,
  composeE164,
  isValidPhone,
} from '../PhoneNumberInput';
import { countryByIso2 } from '../../../data/countries';

describe('composeE164 / isValidPhone', () => {
  const MA = countryByIso2('ma')!;

  it('composes E.164 from a national number, stripping the trunk 0', () => {
    expect(composeE164(MA, '0612345678')).toBe('+212612345678');
    expect(composeE164(MA, '612345678')).toBe('+212612345678');
  });

  it('returns empty string when no national digits are entered', () => {
    expect(composeE164(MA, '')).toBe('');
    expect(composeE164(MA, '0')).toBe('');
  });

  it('validates a real Moroccan mobile and rejects a too-short one', () => {
    expect(isValidPhone('+212612345678')).toBe(true);
    expect(isValidPhone('+21261')).toBe(false);
    expect(isValidPhone('')).toBe(false);
  });
});

describe('<PhoneNumberInput>', () => {
  it('defaults to Morocco (+212)', () => {
    render(
      <PhoneNumberInput value="" onChange={vi.fn()} data-testid="phone" />,
    );
    expect(screen.getByTestId('phone-country')).toHaveTextContent('+212');
  });

  it('emits an E.164 value as the national number is typed', () => {
    const onChange = vi.fn();
    render(<PhoneNumberInput value="" onChange={onChange} data-testid="phone" />);
    fireEvent.change(screen.getByTestId('phone'), { target: { value: '612345678' } });
    expect(onChange).toHaveBeenLastCalledWith('+212612345678');
  });

  it('changing the country updates the dial code and recomposes the E.164', () => {
    const onChange = vi.fn();
    render(<PhoneNumberInput value="+212612345678" onChange={onChange} data-testid="phone" />);
    // Ouvre le selecteur, recherche la France, la selectionne.
    fireEvent.click(screen.getByTestId('phone-country'));
    const search = screen.getByLabelText('Rechercher un pays');
    fireEvent.change(search, { target: { value: 'France' } });
    const listbox = screen.getByRole('listbox');
    fireEvent.click(within(listbox).getByText('France'));
    expect(screen.getByTestId('phone-country')).toHaveTextContent('+33');
    // Le numero national est conserve, l'indicatif change.
    expect(onChange).toHaveBeenLastCalledWith('+33612345678');
  });

  it('decomposes an incoming E.164 into the right country', () => {
    render(<PhoneNumberInput value="+33612345678" onChange={vi.fn()} data-testid="phone" />);
    expect(screen.getByTestId('phone-country')).toHaveTextContent('+33');
  });

  it('filters the country list by dial code', () => {
    render(<PhoneNumberInput value="" onChange={vi.fn()} data-testid="phone" />);
    fireEvent.click(screen.getByTestId('phone-country'));
    fireEvent.change(screen.getByLabelText('Rechercher un pays'), { target: { value: '213' } });
    const listbox = screen.getByRole('listbox');
    expect(within(listbox).getByText('Algerie')).toBeInTheDocument();
  });
});
