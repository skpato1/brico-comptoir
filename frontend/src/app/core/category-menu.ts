import { Category } from './catalog-api';

export interface CategoryMenuNode {
  category: Category;
  children: CategoryMenuNode[];
  path: string;
}

export interface CategoryMenuGroup {
  letter: string;
  nodes: CategoryMenuNode[];
}

export interface CategoryMenu {
  branches: CategoryMenuNode[];
  alphabet: CategoryMenuGroup[];
  searchable: CategoryMenuNode[];
}

const byName = (a: CategoryMenuNode, b: CategoryMenuNode) =>
  a.category.name.localeCompare(b.category.name, 'fr');

export function buildCategoryMenu(categories: readonly Category[]): CategoryMenu {
  const nodes = new Map<string, CategoryMenuNode>();
  for (const category of categories) {
    if (category.active) nodes.set(category.id, { category, children: [], path: '' });
  }
  const roots: CategoryMenuNode[] = [];
  for (const node of nodes.values()) {
    const parent = node.category.parentId ? nodes.get(node.category.parentId) : undefined;
    if (parent && parent !== node) parent.children.push(node);
    else roots.push(node);
  }
  const searchable: CategoryMenuNode[] = [];
  const visit = (node: CategoryMenuNode, parents: string[], visited: Set<string>) => {
    if (visited.has(node.category.id)) return;
    const next = new Set(visited).add(node.category.id);
    node.path = [...parents, node.category.name].join(' / ');
    searchable.push(node);
    node.children.sort(byName);
    for (const child of node.children) visit(child, [...parents, node.category.name], next);
  };
  roots.sort(byName);
  for (const root of roots) visit(root, [], new Set());

  const letters = new Map<string, CategoryMenuNode[]>();
  for (const root of roots.filter(node => node.children.length === 0)) {
    const first = normalizeCategoryText(root.category.name).charAt(0).toUpperCase();
    const letter = /^[A-Z]$/.test(first) ? first : '#';
    letters.set(letter, [...(letters.get(letter) ?? []), root]);
  }
  return {
    branches: roots.filter(node => node.children.length > 0),
    alphabet: [...letters].sort(([a], [b]) => a.localeCompare(b, 'fr'))
      .map(([letter, group]) => ({ letter, nodes: group })),
    searchable,
  };
}

export function normalizeCategoryText(value: string): string {
  return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase('fr').trim();
}

export function searchCategoryMenu(menu: CategoryMenu, query: string): CategoryMenuNode[] {
  const normalized = normalizeCategoryText(query);
  return normalized ? menu.searchable.filter(node =>
    normalizeCategoryText(node.path).includes(normalized)) : [];
}
