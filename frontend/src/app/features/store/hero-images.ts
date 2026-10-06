export const PROMOTIONAL_HERO_IMAGES = ['brico-projects', 'brico-workshop'] as const;

export function isPromotionalHeroImage(image: string): boolean {
  return PROMOTIONAL_HERO_IMAGES.some(value => value === image);
}

export function heroImageUrl(image: string, width: 720 | 1440): string {
  const format = isPromotionalHeroImage(image) ? 'jpg' : 'webp';
  return `/images/hero/${image}-${width}.${format}`;
}
