import { describe, expect, it } from 'vitest';
import { offerLabel, variantLabel } from './offer-label';

describe('customer-facing offer labels', () => {
  it('removes only the standalone source placeholder', () => {
    expect(offerLabel('Perceuse · Default Title · REF-1')).toBe('Perceuse · REF-1');
    expect(offerLabel('Perceuse - Default Title (REF-1)')).toBe('Perceuse (REF-1)');
    expect(offerLabel('Peinture Default Title bleu')).toBe('Peinture Default Title bleu');
  });
  it('keeps real variant choices and labels a sole default variant', () => {
    expect(variantLabel('Default Title', 1)).toBe('Modèle unique');
    expect(variantLabel('Default Title', 2)).toBe('Default Title');
    expect(variantLabel('Rouge', 1)).toBe('Rouge');
  });
});
