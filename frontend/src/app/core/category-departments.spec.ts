import { Category } from './catalog-api';
import { buildCategoryMenu } from './category-menu';
import { buildCategoryDepartments } from './category-departments';

const category = (id: string, name: string): Category =>
  ({ id, name, slug: id, parentId: null, active: true, version: 0 });

describe('Customer-facing category departments', () => {
  it('keeps each real category reachable exactly once and places clear product families together', () => {
    const categories = [
      category('drill', 'Perceuses et Visseuses'),
      category('tap', 'Robinet Mitigeur Cuisine'),
      category('lamp', 'Éclairage Intérieur'),
      category('paint', 'Peinture et finition Mural'),
      category('garden', 'Tuyaux d’Arrosage'),
      category('other', 'Collection sans famille'),
    ];
    const menu = buildCategoryMenu(categories);
    const departments = buildCategoryDepartments(menu.searchable);
    const ids = departments.flatMap(department => department.groups.flatMap(group => group.nodes.map(node => node.category.id)));
    expect(ids.sort()).toEqual(categories.map(item => item.id).sort());
    const departmentOf = (id: string) => departments.find(department =>
      department.groups.some(group => group.nodes.some(node => node.category.id === id)))?.id;
    expect(departmentOf('drill')).toBe('outillage');
    expect(departmentOf('tap')).toBe('plomberie');
    expect(departmentOf('lamp')).toBe('electricite');
    expect(departmentOf('paint')).toBe('peinture');
    expect(departmentOf('garden')).toBe('jardin');
    expect(departmentOf('other')).toBe('autres');
  });
});
