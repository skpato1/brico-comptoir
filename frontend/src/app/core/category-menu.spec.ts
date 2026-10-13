import { describe, expect, it } from 'vitest';
import { Category } from './catalog-api';
import { buildCategoryMenu, searchCategoryMenu } from './category-menu';

const category = (id: string, name: string, parentId: string | null = null,
  active = true): Category => ({ id, name, parentId, active, slug: id, version: 0 });

describe('category menu', () => {
  it('builds nested categories from their real parent IDs', () => {
    const menu = buildCategoryMenu([
      category('child', 'Perceuses', 'parent'),
      category('grandchild', 'Sans fil', 'child'),
      category('parent', 'Outillage'),
    ]);
    expect(menu.branches.map(node => node.category.id)).toEqual(['parent']);
    expect(menu.branches[0].children[0].children[0].path)
      .toBe('Outillage / Perceuses / Sans fil');
    expect(menu.alphabet).toEqual([]);
  });

  it('groups flat categories alphabetically without inventing parent categories', () => {
    const menu = buildCategoryMenu([
      category('z', 'Zinc'), category('e', 'Électricité'), category('a', 'Adhésifs'),
    ]);
    expect(menu.branches).toEqual([]);
    expect(menu.alphabet.map(group => group.letter)).toEqual(['A', 'E', 'Z']);
  });

  it('searches without accents and excludes inactive categories', () => {
    const menu = buildCategoryMenu([
      category('parent', 'Électricité'),
      category('child', 'Interrupteurs', 'parent'),
      category('hidden', 'Ancien stock', null, false),
    ]);
    expect(searchCategoryMenu(menu, 'electricite').map(node => node.category.id))
      .toEqual(['parent', 'child']);
    expect(searchCategoryMenu(menu, 'ancien')).toEqual([]);
  });

  it('keeps orphaned public categories accessible', () => {
    const menu = buildCategoryMenu([category('orphan', 'Peinture', 'missing')]);
    expect(menu.alphabet[0].nodes[0].category.id).toBe('orphan');
  });
});
