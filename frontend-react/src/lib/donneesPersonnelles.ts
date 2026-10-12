/**
 * Lot L5 E8 (RG-COM-03, RG-COM-04) : filtre des donnees personnelles du chatbot, cote ecran.
 *
 * Avant tout envoi, la question est analysee ; si elle contient une donnee personnelle
 * reconnaissable, l'envoi est bloque et l'utilisateur est invite a reformuler. Le serveur
 * (ai-service, FiltreDonneesPersonnelles.java) rejoue le meme controle et refuse en 422.
 *
 * Les motifs sont RECOPIES de FiltreDonneesPersonnelles.java ; les deux cotes sont eprouves
 * par le meme jeu de cas : tests/chatbot/cas-donnees-personnelles.json. Toute modification
 * se fait des deux cotes.
 */

export type NatureDonneePersonnelle =
  | 'CIN'
  | 'ICE'
  | 'RC'
  | 'IDENTIFIANT_FISCAL'
  | 'TELEPHONE'
  | 'EMAIL'
  | 'RIB'
  | 'DATE_NAISSANCE';

/** Ordre et libelles identiques a l'enumeration du serveur. */
export const LIBELLES_DONNEES_PERSONNELLES: Record<NatureDonneePersonnelle, string> = {
  CIN: 'numéro de carte nationale (CIN)',
  ICE: 'ICE',
  RC: 'numéro de registre du commerce (RC)',
  IDENTIFIANT_FISCAL: 'identifiant fiscal (IF)',
  TELEPHONE: 'numéro de téléphone',
  EMAIL: 'adresse électronique',
  RIB: 'RIB',
  DATE_NAISSANCE: 'date de naissance',
};

const NATURES = Object.keys(LIBELLES_DONNEES_PERSONNELLES) as NatureDonneePersonnelle[];

const MARQUE_NUMERO = '(?:n\\u00b0|no\\.?|num[e\\u00e9]ro)?\\s*:?\\s*';
const MOIS =
  '(?:janvier|f[e\\u00e9]vrier|mars|avril|mai|juin|juillet|ao[u\\u00fb]t|septembre|octobre|novembre|d[e\\u00e9]cembre)';

/** Drapeaux : 'u' partout (\p{L}) ; 'i' quand le motif Java porte CASE_INSENSITIVE. */
const MOTIFS: Array<{ nature: NatureDonneePersonnelle; motif: RegExp }> = [
  { nature: 'CIN', motif: new RegExp('(?<![\\p{L}\\d])[A-Z]{1,2}\\d{5,6}(?!\\d)', 'u') },
  {
    nature: 'CIN',
    motif: new RegExp(
      "(?<!\\p{L})(?:CIN|C\\.I\\.N\\.?|carte\\s+nationale(?:\\s+d'identit[e\\u00e9])?)\\s*" +
        MARQUE_NUMERO +
        '[A-Z]{1,2}\\s?\\d{3,6}(?!\\d)',
      'iu',
    ),
  },
  { nature: 'ICE', motif: new RegExp('(?<!\\d)\\d{15}(?!\\d)', 'u') },
  {
    nature: 'ICE',
    motif: new RegExp('(?<!\\p{L})ICE\\s*' + MARQUE_NUMERO + '\\d(?:[\\s.]?\\d){7,14}(?!\\d)', 'iu'),
  },
  {
    nature: 'RC',
    motif: new RegExp(
      '(?<!\\p{L})R\\.?C\\.?\\s*(?:de\\s+)?(?:\\p{Lu}[\\p{L}\\-]+\\s*)?' + MARQUE_NUMERO + '\\d{3,}(?!\\d)',
      'u',
    ),
  },
  {
    nature: 'RC',
    motif: new RegExp(
      'registre\\s+(?:du|de)\\s+commerce\\s*(?:de\\s+\\p{L}[\\p{L}\\-]*\\s*)?' +
        '(?:n\\u00b0|no\\.?|num[e\\u00e9]ro)\\s*:?\\s*\\d{3,}(?!\\d)',
      'iu',
    ),
  },
  {
    nature: 'IDENTIFIANT_FISCAL',
    motif: new RegExp('(?<!\\p{L})I\\.?F\\.?\\s*' + MARQUE_NUMERO + '\\d{6,9}(?!\\d)', 'u'),
  },
  {
    nature: 'IDENTIFIANT_FISCAL',
    motif: new RegExp('identifiant\\s+fiscal\\s*' + MARQUE_NUMERO + '\\d{6,9}(?!\\d)', 'iu'),
  },
  { nature: 'RIB', motif: new RegExp('(?<!\\d)\\d(?:[\\s.\\-]?\\d){23}(?!\\d)', 'u') },
  { nature: 'RIB', motif: new RegExp('(?<!\\p{L})MA\\d{2}(?:\\s?\\d){24}(?!\\d)', 'iu') },
  {
    nature: 'TELEPHONE',
    motif: new RegExp('(?<![\\d+])(?:\\+212|00212|0)\\s?(?:\\(0\\)\\s?)?[5-8](?:[\\s.\\-]?\\d){8}(?!\\d)', 'u'),
  },
  {
    nature: 'TELEPHONE',
    motif: new RegExp('(?<![\\d\\p{L}])(?:\\+|00)[1-9](?:[\\s.\\-]?\\d){7,14}(?!\\d)', 'u'),
  },
  {
    nature: 'EMAIL',
    motif: new RegExp('[\\p{L}0-9._%+\\-]+@[\\p{L}0-9.\\-]+\\.\\p{L}{2,}', 'u'),
  },
  {
    nature: 'DATE_NAISSANCE',
    motif: new RegExp(
      '(?<!\\p{L})(?:n[e\\u00e9]e?(?:\\(e\\))?\\s+le|date\\s+de\\s+naissance\\s*:?|naissance\\s*:)\\s*(?:le\\s+)?' +
        '(?:\\d{1,2}[/.\\-]\\d{1,2}[/.\\-]\\d{2,4}|\\d{1,2}(?:er)?\\s+' +
        MOIS +
        '\\s+\\d{4})',
      'iu',
    ),
  },
];

/** Natures detectees dans le texte, dans l'ordre du serveur, sans doublon ni valeur. */
export function detecterDonneesPersonnelles(texte: string | null | undefined): NatureDonneePersonnelle[] {
  if (!texte || !texte.trim()) return [];
  return NATURES.filter((nature) =>
    MOTIFS.some((m) => m.nature === nature && m.motif.test(texte)),
  );
}

/** Message d'invitation a reformuler, nommant la nature des donnees detectees. */
export function messageReformulation(natures: NatureDonneePersonnelle[]): string {
  const liste = natures.map((n) => LIBELLES_DONNEES_PERSONNELLES[n]).join(', ');
  return (
    `Votre question contient une donnée personnelle (${liste}) : elle n’a pas été envoyée. ` +
    'Reformulez-la sans cette donnée : le chatbot n’a besoin d’aucune donnée de client pour répondre.'
  );
}
