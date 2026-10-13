import { CategoryMenuNode, normalizeCategoryText } from './category-menu';

export interface CategoryDepartment {
  id: string;
  name: string;
  icon: string;
  groups: { name: string; nodes: CategoryMenuNode[] }[];
  count: number;
}

interface GroupRule {
  name: string;
  test: RegExp;
}

interface DepartmentRule {
  id: string;
  name: string;
  icon: string;
  groups: GroupRule[];
}

// These are browsing labels only. Every link still uses a real category ID from the API.
// The first matching group wins, so specific product families precede broad labels.
const DEPARTMENTS: DepartmentRule[] = [
  { id: 'outillage', name: 'Outillage', icon: 'tools', groups: [
    { name: 'Électroportatif', test: /perceus|visseus|meuleus|ponceus|scie|rabot|defonceus|perforat|tronconn|electroportat|electro-jardinage|batterie et chargeur/ },
    { name: 'Outils à main', test: /outillage|outil|marteau|tournevis|pince|tenaille|cle[s ]|douille|cutters|ciseau|lime|serre-joint|etau|agrafeus|cloueus/ },
    { name: 'Atelier & mesure', test: /mesure|tracage|compresseur|soud|aspirateur de chantier|caisse[s ]? a outil|servante|rangement des outils|machine[s ]? de tolerie/ },
  ] },
  { id: 'plomberie', name: 'Plomberie & bain', icon: 'water', groups: [
    { name: 'Robinetterie & sanitaire', test: /robinet|mitigeur|lavabo|wc|bidet|evier|baignoire|douche|salle de bain|siphon|bonde|chasse|sanitaire/ },
    { name: 'Tuyaux & raccords', test: /plomberie|tuyau|tube|raccord|laiton|joint|eau\/gaz|gaz -|flexible/ },
    { name: 'Pompes & traitement de l’eau', test: /pompe|pompage|surpresseur|filtration|osmoseur|adoucisseur|evacuer l.eau/ },
    { name: 'Chauffage', test: /chauffage|chauffe-eau|chaudiere|radiateur|seche.serviette|vase[s ]? d.expansion/ },
  ] },
  { id: 'electricite', name: 'Électricité & lumière', icon: 'light', groups: [
    { name: 'Éclairage', test: /eclairage|luminaire|ampoule|lampe|spot|led|lustre|suspension|plafonnier|guirlande/ },
    { name: 'Câbles & prises', test: /cable|gaine|prise|rallonge|enrouleur|pile|batterie[s ]? varta/ },
    { name: 'Installation électrique', test: /electric|tableau|coffret|minuterie|contacteur|telerupteur|alimentation electrique|moteur rideau/ },
  ] },
  { id: 'peinture', name: 'Peinture & finitions', icon: 'paint', groups: [
    { name: 'Peinture & décoration', test: /peinture|peintre|droguerie|chimi-couleur|rev[eê]tement|decoration mur|tableau[x]? de peinture|preparation et entretien du bois/ },
    { name: 'Colles & abrasifs', test: /\bcolles?\b|adhesif|abrasif|papier[s ]? de verre|brosse et abrasif|ruban[s ]? & bande/ },
    { name: 'Étanchéité & isolation', test: /etancheite|isolation|isolant|reparation et etancheite|protection des portes/ },
  ] },
  { id: 'jardin', name: 'Jardin & extérieur', icon: 'leaf', groups: [
    { name: 'Outils de jardin', test: /jardin|motoculteur|motobineuse|tondeuse|taille.haie|debroussailleus|coupe.bordure|secateur|r[aâ]teau|fourche|pelle|beche|binette|houe|pioche|souffleur/ },
    { name: 'Arrosage & piscine', test: /arrosage|irrigation|pulverisateur|piscine/ },
    { name: 'Aménagement extérieur', test: /exterieur|terrasse|barbecue|gazon|ombrage|cloture|grillage|occultation|mur[s ]? vegetaux|loisirs en plein air/ },
  ] },
  { id: 'quincaillerie', name: 'Quincaillerie', icon: 'fixing', groups: [
    { name: 'Visserie & fixation', test: /vis |vis et|boulon|cheville|fixation|clou[s ]|rivet|crochet|assemblage|ressort/ },
    { name: 'Portes & serrures', test: /porte|serrure|verrou|cylindre|poignee|cremone|anti.panique|cadenas|antivol|charniere|paumelle/ },
    { name: 'Meuble & accessoires', test: /meuble|tiroir|etagere|tablette|quincaillerie|roulette|patin antiderapant|glissiere/ },
    { name: 'Sécurité', test: /securite|alarme|detecteur|controle d.acces|signal[eé]tique/ },
  ] },
  { id: 'maison', name: 'Maison & rangement', icon: 'home', groups: [
    { name: 'Cuisine & maison', test: /maison|cuisine|electromenager|linge|buanderie|enfants|bebe/ },
    { name: 'Rangement & entretien', test: /rangement|nettoyage|entretien|poubelle|dechet|hygiene|balai|accessoire[s ]? de decoration/ },
    { name: 'Décoration', test: /decoration|fenetre|rideau|tringle|revetement de sol|brique[s ]? de verre/ },
    { name: 'Protection contre les nuisibles', test: /insect|moustiquaire|anti.nuisible|animalerie/ },
  ] },
  { id: 'chantier', name: 'Chantier & matériaux', icon: 'building', groups: [
    { name: 'Construction', test: /construction|chantier|macon|materiau|planche|madrier|charpente|travaux/ },
    { name: 'Équipement & manutention', test: /manutention|levage|gerbeur|transpalette|chariot|brouette|diable|palan|echelle|escabeau|marchepied|location|dem[eé]nagement/ },
    { name: 'Protection individuelle', test: /protection|casque|gant[s ]? de travail|vetement[s ]? de travail|impermeable/ },
    { name: 'Auto & énergie', test: /auto|moto|velo|cric|groupe[s ]? electrogene/ },
  ] },
];

export function buildCategoryDepartments(nodes: readonly CategoryMenuNode[]): CategoryDepartment[] {
  const departments = DEPARTMENTS.map(rule => ({
    id: rule.id, name: rule.name, icon: rule.icon,
    groups: rule.groups.map(group => ({ name: group.name, nodes: [] as CategoryMenuNode[] })),
    count: 0,
  }));
  const other: CategoryDepartment = {
    id: 'autres', name: 'Autres catégories', icon: 'grid',
    groups: [{ name: 'Toutes les références', nodes: [] }], count: 0,
  };
  for (const node of nodes) {
    const name = normalizeCategoryText(node.category.name);
    if (/arrosage|irrigation/.test(name)) {
      departments[4].groups[1].nodes.push(node);
      departments[4].count++;
      continue;
    }
    let matched = false;
    for (let i = 0; i < DEPARTMENTS.length && !matched; i++) {
      const group = DEPARTMENTS[i].groups.findIndex(rule => rule.test.test(name));
      if (group < 0) continue;
      departments[i].groups[group].nodes.push(node);
      departments[i].count++;
      matched = true;
    }
    if (!matched) {
      other.groups[0].nodes.push(node);
      other.count++;
    }
  }
  return [...departments, other]
    .filter(department => department.count)
    .map(department => ({
      ...department,
      groups: department.groups.filter(group => group.nodes.length),
    }));
}
