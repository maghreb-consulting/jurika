/**
 * Liste de reference des provinces / prefectures / communes du Maroc.
 *
 * F1 2026-06-09 — Genere pour le combobox Step2Siege. Volontairement
 * exhaustif sur les 12 regions / 75 prefectures-provinces officielles
 * (decoupage 2015) + arrondissements urbains des grandes villes
 * (Casablanca, Rabat-Sale-Temara, Marrakech, Fes, Tanger, Agadir,
 * Meknes, Oujda).
 *
 * Source : Decret n° 2-15-40 du 20 fevrier 2015 fixant le nombre de
 * regions, leur denomination, leur chef-lieu ainsi que les prefectures
 * et provinces qui les composent + Code geographique HCP 2024.
 *
 * Le combobox accepte aussi du free-text : un cabinet peut entrer une
 * commune rurale ou une nouvelle commune non listee, la valeur est
 * persistee telle qu'elle.
 */

/** Liste plate des 75 prefectures / provinces (ordre alphabetique). */
export const MOROCCO_PROVINCES: string[] = [
  // Region Tanger-Tetouan-Al Hoceima
  'Al Hoceima', 'Chefchaouen', 'Fahs-Anjra', 'Larache', 'Mdiq-Fnideq',
  'Ouezzane', 'Tanger-Assilah', 'Tetouan',
  // Region Oriental
  'Berkane', 'Driouch', 'Figuig', 'Guercif', 'Jerada', 'Nador', 'Oujda-Angad', 'Taourirt',
  // Region Fes-Meknes
  'Boulemane', 'El Hajeb', 'Fes', 'Ifrane', 'Meknes', 'Moulay Yacoub',
  'Sefrou', 'Taounate', 'Taza',
  // Region Rabat-Sale-Kenitra
  'Kenitra', 'Khemisset', 'Rabat', 'Sale', 'Sidi Kacem', 'Sidi Slimane', 'Skhirate-Temara',
  // Region Beni Mellal-Khenifra
  'Azilal', 'Beni Mellal', 'Fquih Ben Salah', 'Khenifra', 'Khouribga',
  // Region Casablanca-Settat
  'Benslimane', 'Berrechid', 'Casablanca', 'El Jadida', 'Mediouna',
  'Mohammedia', 'Nouaceur', 'Settat', 'Sidi Bennour',
  // Region Marrakech-Safi
  'Al Haouz', 'Chichaoua', 'El Kelaa des Sraghna', 'Essaouira', 'Marrakech',
  'Rehamna', 'Safi', 'Youssoufia',
  // Region Draa-Tafilalet
  'Errachidia', 'Midelt', 'Ouarzazate', 'Tinghir', 'Zagora',
  // Region Souss-Massa
  'Agadir-Ida Ou Tanane', 'Chtouka-Ait Baha', 'Inezgane-Ait Melloul',
  'Taroudant', 'Tata', 'Tiznit',
  // Region Guelmim-Oued Noun
  'Assa-Zag', 'Guelmim', 'Sidi Ifni', 'Tan-Tan',
  // Region Laayoune-Sakia El Hamra
  'Boujdour', 'Es-Smara', 'Laayoune', 'Tarfaya',
  // Region Dakhla-Oued Ed-Dahab
  'Aousserd', 'Oued Ed-Dahab',
].sort((a, b) => a.localeCompare(b, 'fr'));

/**
 * Communes / arrondissements indicatifs par province. La liste n'est
 * pas exhaustive (chaque province compte 5 a 50 communes rurales) ;
 * on liste ici les communes urbaines et les chefs-lieux. Le combobox
 * accepte de la free-text pour les autres.
 */
export const MOROCCO_COMMUNES: Record<string, string[]> = {
  // ── Casablanca ───────────────────────────────────────────────
  'Casablanca': [
    'Anfa', 'Ain Chock', 'Ain Sebaa', 'Hay Hassani', 'Maarif',
    'Sidi Belyout', 'Sidi Bernoussi', 'Sidi Moumen', 'Sidi Maarouf',
    'Sidi Othmane', 'Moulay Rachid', 'Roches Noires', 'Bourgogne',
    'Ben Msik', 'Sbata', 'Hay Mohammadi', 'Mers Sultan',
  ],
  'Mohammedia': ['Mohammedia', 'Beni Yakhlef', 'Ain Harrouda'],
  'Nouaceur': ['Nouaceur', 'Bouskoura', 'Dar Bouazza', 'Ouled Saleh', 'Lahsasna'],
  'Mediouna': ['Mediouna', 'Tit Mellil', 'Sidi Hajjaj'],
  'Berrechid': ['Berrechid', 'Deroua', 'Sidi Rahal Chatai', 'Had Soualem'],
  'Settat': ['Settat', 'Ben Ahmed', 'El Brouj'],
  'El Jadida': ['El Jadida', 'Azemmour', 'Bir Jdid', 'Sidi Bouzid'],
  'Sidi Bennour': ['Sidi Bennour', 'Zemamra', 'Khemis Zemamra'],
  'Benslimane': ['Benslimane', 'Bouznika', 'Sidi Yahya Zaer'],

  // ── Rabat-Sale-Kenitra ──────────────────────────────────────
  'Rabat': [
    'Agdal-Ryad', 'Hassan', 'Yacoub El Mansour', 'Souissi',
    'Touarga', 'El Youssoufia', 'Riad', 'Akkari', 'Hay Ryad',
  ],
  'Sale': [
    'Bab Lamrissa', 'Bettana', 'Hssaine', 'Layayda', 'Sale Al Jadida',
    'Tabriquet', 'Sidi Bouknadel', 'Sidi Yahya Zaer',
  ],
  'Skhirate-Temara': ['Temara', 'Skhirate', 'Harhoura', 'Ain Aouda', 'Sidi Yahya Zaer'],
  'Kenitra': ['Kenitra', 'Sidi Taibi', 'Mehdya', 'Mograne', 'Souk El Arbaa'],
  'Khemisset': ['Khemisset', 'Tiflet', 'Rommani', 'Oulmes'],
  'Sidi Kacem': ['Sidi Kacem', 'Mechra Bel Ksiri', 'Jorf El Melha'],
  'Sidi Slimane': ['Sidi Slimane', 'Sidi Yahya El Gharb'],

  // ── Tanger-Tetouan-Al Hoceima ───────────────────────────────
  'Tanger-Assilah': [
    'Tanger-Medina', 'Charf-Mghogha', 'Charf-Souani', 'Beni Makada',
    'Assilah', 'Boukhalef', 'Gzennaya',
  ],
  'Tetouan': ['Tetouan', 'Martil', 'Mdiq', 'Cabo Negro'],
  'Mdiq-Fnideq': ['Mdiq', 'Fnideq', 'Allyeenne'],
  'Larache': ['Larache', 'Ksar El Kebir', 'Lixus'],
  'Chefchaouen': ['Chefchaouen', 'Bab Berred', 'Bni Bouzra'],
  'Al Hoceima': ['Al Hoceima', 'Imzouren', 'Beni Bouayach', 'Targuist'],
  'Ouezzane': ['Ouezzane', 'Brikcha', 'Sidi Redouane'],
  'Fahs-Anjra': ['Ksar El Majaz', 'Anjra'],

  // ── Fes-Meknes ──────────────────────────────────────────────
  'Fes': [
    'Fes-Medina', 'Agdal', 'Saiss', 'Zouagha', 'El Mariniyine',
    'Jnan El Ward', 'Fes-Ville Nouvelle',
  ],
  'Meknes': [
    'Hamria', 'Ismailia', 'Toulal', 'Mechouar Stinia', 'Al Mansour',
    'Ouislane',
  ],
  'El Hajeb': ['El Hajeb', 'Agourai', 'Ait Boubidmane'],
  'Ifrane': ['Ifrane', 'Azrou', 'Ain Leuh'],
  'Sefrou': ['Sefrou', 'Bhalil', 'Imouzzer Kandar'],
  'Taza': ['Taza', 'Tahla', 'Aknoul', 'Oued Amlil'],
  'Taounate': ['Taounate', 'Tissa', 'Karia Ba Mohamed'],
  'Boulemane': ['Boulemane', 'Missour', 'Outat El Haj'],
  'Moulay Yacoub': ['Moulay Yacoub', 'Sebt Loudaya', 'Ain Bouali'],

  // ── Marrakech-Safi ──────────────────────────────────────────
  'Marrakech': [
    'Gueliz', 'Medina', 'Mechouar-Kasbah', 'Sidi Youssef Ben Ali',
    'Annakhil', 'Menara', 'Hivernage', 'Daoudiate', 'Massira',
  ],
  'Al Haouz': ['Tahanaout', 'Amizmiz', 'Asni', 'Ourika'],
  'Chichaoua': ['Chichaoua', 'Imintanout', 'Mzouda'],
  'El Kelaa des Sraghna': ['El Kelaa des Sraghna', 'Tamellalt', 'Sidi Rahal'],
  'Essaouira': ['Essaouira', 'Talmest', 'Tamanar'],
  'Rehamna': ['Ben Guerir', 'Sidi Bou Othmane', 'Skhour Rehamna'],
  'Safi': ['Safi', 'Sebt Gzoula', 'Jamaat Shaim'],
  'Youssoufia': ['Youssoufia', 'Jemaa Sahim', 'Chemaia'],

  // ── Souss-Massa ─────────────────────────────────────────────
  'Agadir-Ida Ou Tanane': [
    'Agadir', 'Bensergao', 'Anza', 'Tikiouine', 'Drarga',
    'Aourir', 'Taghazout', 'Imouzzer Ida Ou Tanane',
  ],
  'Inezgane-Ait Melloul': ['Inezgane', 'Ait Melloul', 'Lqliaa', 'Dcheira El Jihadia'],
  'Chtouka-Ait Baha': ['Biougra', 'Ait Baha', 'Ait Amira', 'Massa'],
  'Taroudant': ['Taroudant', 'Oulad Teima', 'Ouled Berhil', 'Aoulouz'],
  'Tata': ['Tata', 'Foum Zguid', 'Akka'],
  'Tiznit': ['Tiznit', 'Tafraout', 'Mirleft', 'Anezi'],

  // ── Oriental ────────────────────────────────────────────────
  'Oujda-Angad': ['Oujda', 'Sidi Yahya', 'Bni Drar', 'Ahl Angad'],
  'Berkane': ['Berkane', 'Saidia', 'Aklim', 'Ahfir'],
  'Nador': ['Nador', 'Beni Ensar', 'Selouane', 'Zaio', 'Al Aaroui'],
  'Driouch': ['Driouch', 'Midar', 'Ben Taieb'],
  'Jerada': ['Jerada', 'Touissit', 'Ain Bni Mathar'],
  'Taourirt': ['Taourirt', 'El Aioun Sidi Mellouk', 'Debdou'],
  'Guercif': ['Guercif', 'Lamrija', 'Houara Oulad Raho'],
  'Figuig': ['Figuig', 'Bouarfa', 'Tendrara'],

  // ── Beni Mellal-Khenifra ────────────────────────────────────
  'Beni Mellal': ['Beni Mellal', 'Kasba Tadla', 'El Ksiba', 'Zaouiat Cheikh'],
  'Azilal': ['Azilal', 'Demnate', 'Ait Mhamed'],
  'Fquih Ben Salah': ['Fquih Ben Salah', 'Souk Sebt Oulad Nemma'],
  'Khenifra': ['Khenifra', 'Mrirt', 'El Borj', 'Aguelmous'],
  'Khouribga': ['Khouribga', 'Oued Zem', 'Boujniba', 'Hattane'],

  // ── Draa-Tafilalet ──────────────────────────────────────────
  'Errachidia': ['Errachidia', 'Erfoud', 'Rissani', 'Goulmima'],
  'Midelt': ['Midelt', 'Itzer', 'Boumia'],
  'Ouarzazate': ['Ouarzazate', 'Tarmigte', 'Skoura'],
  'Tinghir': ['Tinghir', 'Boumalne Dades', 'Kelaat Mgouna'],
  'Zagora': ['Zagora', 'Agdz', 'Mhamid El Ghizlane'],

  // ── Guelmim-Oued Noun ───────────────────────────────────────
  'Guelmim': ['Guelmim', 'Bouizakarne', 'Abaynou'],
  'Assa-Zag': ['Assa', 'Zag'],
  'Sidi Ifni': ['Sidi Ifni', 'Mirleft'],
  'Tan-Tan': ['Tan-Tan', 'El Ouatia'],

  // ── Laayoune-Sakia El Hamra ────────────────────────────────
  'Laayoune': ['Laayoune', 'El Marsa', 'Foum El Oued'],
  'Boujdour': ['Boujdour', 'Jraifia'],
  'Es-Smara': ['Es-Smara', 'Tifariti'],
  'Tarfaya': ['Tarfaya', 'Akhfennir'],

  // ── Dakhla-Oued Ed-Dahab ───────────────────────────────────
  'Oued Ed-Dahab': ['Dakhla', 'El Argoub', 'Imlili'],
  'Aousserd': ['Aousserd', 'Bir Gandouz', 'Zoug'],
};

/** Mapping province -> tribunal de commerce competent (incomplet, complete par defaut). */
export const MOROCCO_TRIBUNAUX: Record<string, string> = {
  'Casablanca': 'Tribunal de Commerce - Casablanca',
  'Mohammedia': 'Tribunal de Commerce - Casablanca',
  'Nouaceur': 'Tribunal de Commerce - Casablanca',
  'Mediouna': 'Tribunal de Commerce - Casablanca',
  'Berrechid': 'Tribunal de Commerce - Casablanca',
  'Rabat': 'Tribunal de Commerce - Rabat',
  'Sale': 'Tribunal de Commerce - Rabat',
  'Skhirate-Temara': 'Tribunal de Commerce - Rabat',
  'Kenitra': 'Tribunal de Commerce - Kenitra',
  'Marrakech': 'Tribunal de Commerce - Marrakech',
  'Tanger-Assilah': 'Tribunal de Commerce - Tanger',
  'Tetouan': 'Tribunal de Commerce - Tanger',
  'Fes': 'Tribunal de Commerce - Fes',
  'Meknes': 'Tribunal de Commerce - Fes',
  'Agadir-Ida Ou Tanane': 'Tribunal de Commerce - Agadir',
  'Inezgane-Ait Melloul': 'Tribunal de Commerce - Agadir',
  'Oujda-Angad': 'Tribunal de Commerce - Oujda',
  'Nador': 'Tribunal de Commerce - Oujda',
};

/** Retourne la liste des communes d'une province, ou liste vide si inconnue. */
export function communesForProvince(province: string): string[] {
  return MOROCCO_COMMUNES[province] ?? [];
}

/** Retourne le tribunal de commerce competent (fallback generique si inconnu). */
export function tribunalForProvince(province: string): string {
  return MOROCCO_TRIBUNAUX[province] ?? `Tribunal de Commerce - ${province}`;
}
