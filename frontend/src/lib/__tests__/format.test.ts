import { describe, expect, it } from 'vitest';

import { date, humanise, maskAccount, money, moneyCompact, percent, riskClasses } from '../format';

describe('money formatting', () => {
  it('formats a number as Indian rupees', () => {
    expect(money(184500)).toBe('₹1,84,500.00');
  });

  it('accepts a string from the API', () => {
    expect(money('2500.50')).toBe('₹2,500.50');
  });

  it('always shows two decimal places, so columns align', () => {
    expect(money(100)).toBe('₹100.00');
  });

  it('formats zero rather than treating it as missing', () => {
    expect(money(0)).toBe('₹0.00');
  });

  it('shows a dash for a missing value instead of NaN', () => {
    expect(money(null)).toBe('—');
    expect(money(undefined)).toBe('—');
    expect(money('')).toBe('—');
    expect(money('not a number')).toBe('—');
  });

  it('handles a negative amount', () => {
    expect(money(-500)).toContain('500.00');
  });

  it('formats compactly for tight spaces', () => {
    expect(moneyCompact(184500)).toMatch(/1\.8/);
  });
});

describe('percent formatting', () => {
  it('shows one decimal place', () => {
    expect(percent(42.37)).toBe('42.4%');
  });

  it('handles zero', () => {
    expect(percent(0)).toBe('0.0%');
  });

  it('shows a dash for a missing value', () => {
    expect(percent(null)).toBe('—');
  });
});

describe('account masking', () => {
  it('shows only the last four digits', () => {
    expect(maskAccount('10024857391026')).toBe('•••• 1026');
  });

  it('does not expose the leading digits', () => {
    expect(maskAccount('10024857391026')).not.toContain('100248');
  });

  it('leaves a very short value alone rather than mangling it', () => {
    expect(maskAccount('1234')).toBe('1234');
  });

  it('shows a dash when there is no account number', () => {
    expect(maskAccount(null)).toBe('—');
  });
});

describe('humanise', () => {
  it('turns an enum constant into a readable label', () => {
    expect(humanise('NEEDS_ATTENTION')).toBe('Needs attention');
    expect(humanise('PENDING_VERIFICATION')).toBe('Pending verification');
    expect(humanise('FOOD')).toBe('Food');
  });

  it('shows a dash for a missing value', () => {
    expect(humanise(null)).toBe('—');
  });
});

describe('date formatting', () => {
  it('formats an ISO date', () => {
    expect(date('2026-11-02T10:30:00Z')).toMatch(/2026/);
  });

  it('shows a dash for a missing or invalid date', () => {
    expect(date(null)).toBe('—');
    expect(date('not a date')).toBe('—');
  });
});

describe('risk classes', () => {
  it('gives each band its own colour', () => {
    const bands = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].map(riskClasses);
    expect(new Set(bands).size).toBe(4);
  });

  it('marks critical risk in red', () => {
    expect(riskClasses('CRITICAL')).toContain('critical');
  });

  it('falls back to a neutral style for an unknown band', () => {
    expect(riskClasses('SOMETHING_ELSE')).toContain('navy');
  });
});
