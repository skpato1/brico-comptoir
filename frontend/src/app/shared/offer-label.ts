/** Hide a source-catalogue placeholder only when it is a separate variant segment. */
export function offerLabel(label: string): string {
  return label.replace(/\s*[·–-]\s*Default Title(?=\s*[·(]|$)/gi, '');
}

export function variantLabel(label: string, variantCount: number): string {
  return variantCount === 1 && label.trim().toLowerCase() === 'default title'
    ? 'Modèle unique'
    : label;
}
