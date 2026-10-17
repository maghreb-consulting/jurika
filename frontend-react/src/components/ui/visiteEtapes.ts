import type { Role } from '../../types/auth';

/**
 * Lot L6 (E6, CDC 15.4) : etapes de la premiere visite guidee, par role.
 * `cible` designe un attribut `data-visite` de la mise en page commune
 * (AppTopNav, AppShell) ; une etape sans cible s'affiche au centre de l'ecran.
 */
export interface EtapeVisite {
  cible?: string;
  titre: string;
  texte: string;
}

/** Cle de l'aide "visite guidee" d'un role, memorisee comme une aide masquee. */
export function cleVisite(role: Role): string {
  return `visite-${role.toLowerCase().replace(/_/g, '-')}`;
}

const COMPTE: EtapeVisite = {
  cible: 'compte',
  titre: 'Votre compte',
  texte:
    'Ce menu ouvre « Mon compte », la sécurité de connexion et les paramètres. Vous pouvez y revoir cette visite et réafficher les aides que vous avez masquées.',
};

const AIDES: EtapeVisite = {
  titre: 'Les aides de chaque écran',
  texte:
    'Chaque écran porte des encadrés d’aide et des icônes « i » : ce qu’il faut faire, ce qui manque, ce qui vient ensuite. Une aide déjà lue se masque d’un clic ; elle se réaffiche depuis votre compte.',
};

/** Lot L6b (E11) : JURI remplace la bulle du chatbot (meme cible "chatbot"). */
const JURI_CABINET: EtapeVisite = {
  cible: 'chatbot',
  titre: 'JURI, votre assistant',
  texte:
    'JURI attend sur le bord droit de l’écran : un clic ouvre l’aide de l’écran en cours, les raccourcis clavier et le chatbot juridique, dont chaque réponse cite ses sources. Ctrl + K ouvre la palette de commandes pour retrouver un dossier ou un ticket.',
};

const ETAPES: Record<Role, EtapeVisite[]> = {
  EMPLOYE: [
    {
      titre: 'Bienvenue sur JURIKA',
      texte:
        'Cette courte visite présente l’essentiel de votre espace de travail. Vous pouvez la quitter à tout moment avec la touche Échap.',
    },
    {
      cible: 'menu',
      titre: 'Le menu',
      texte:
        'Commencez par « Mes tickets » : chaque ticket suit une demande de votre client, de la saisie des informations jusqu’au dépôt. « Mes Data Rooms » rassemble les pièces de vos dossiers.',
    },
    {
      cible: 'echeances',
      titre: 'Vos échéances',
      texte: 'Ce compteur signale les délais qui approchent ; un clic ouvre le calendrier de vos tickets.',
    },
    {
      cible: 'cloche',
      titre: 'Les notifications',
      texte: 'La cloche vous avertit d’une nouvelle affectation, d’une remarque du superviseur ou d’une pièce déposée par un client.',
    },
    JURI_CABINET,
    COMPTE,
    AIDES,
  ],
  SUPERVISEUR: [
    {
      titre: 'Bienvenue sur JURIKA',
      texte:
        'Cette courte visite présente l’essentiel de votre espace de supervision. Vous pouvez la quitter à tout moment avec la touche Échap.',
    },
    {
      cible: 'menu',
      titre: 'Le menu',
      texte:
        'Commencez par « Tous les Tickets » pour suivre le travail du cabinet, puis par « Dossiers à vérifier » : ce sont les dossiers dont le responsable a été désigné d’office. « Traçabilité » retrace les actions de chacun.',
    },
    {
      cible: 'echeances',
      titre: 'Les échéances',
      texte: 'Ce compteur signale les délais qui approchent pour l’ensemble du cabinet.',
    },
    {
      cible: 'cloche',
      titre: 'Les notifications',
      texte: 'La cloche vous avertit des événements du cabinet, dont les quotas de votre abonnement qui approchent de leur limite.',
    },
    JURI_CABINET,
    {
      ...COMPTE,
      texte:
        'Ce menu ouvre « Mon compte » et les paramètres du cabinet : la fiche du cabinet, l’équipe, la facturation. Vous pouvez y revoir cette visite et réafficher les aides masquées.',
    },
    AIDES,
  ],
  CLIENT: [
    {
      titre: 'Bienvenue dans votre espace JURIKA',
      texte:
        'Votre cabinet suit vos démarches juridiques dans cet espace. Cette courte visite en présente l’essentiel ; la touche Échap la ferme.',
    },
    {
      cible: 'menu',
      titre: 'Le menu',
      texte:
        '« Mon espace » rassemble les documents de vos dossiers. « Mes demandes » vous permet d’adresser une demande au cabinet et d’en suivre l’avancement. « Demandes de mon conseiller » liste ce que votre conseiller attend de vous : commencez par là.',
    },
    {
      ...JURI_CABINET,
      texte:
        'JURI attend sur le bord droit de l’écran : un clic ouvre l’aide de l’écran en cours et la liste des raccourcis clavier. Vous pouvez le masquer, puis le réafficher depuis le menu de votre compte.',
    },
    COMPTE,
    AIDES,
  ],
  SUPER_ADMIN: [
    {
      titre: 'Bienvenue dans l’administration de JURIKA',
      texte:
        'Cette courte visite présente les outils d’administration de la plateforme. Vous pouvez la quitter à tout moment avec la touche Échap.',
    },
    {
      cible: 'menu',
      titre: 'Le menu',
      texte:
        'Commencez par « Workspaces » : la liste des cabinets abonnés et leur état. « Utilisateurs » et « Journal d’audit » servent au support ; « Référentiel des démarches » et « Sources du chatbot » sont communs à tous les cabinets.',
    },
    {
      cible: 'cloche',
      titre: 'Les notifications',
      texte: 'La cloche vous avertit des événements de la plateforme.',
    },
    {
      ...JURI_CABINET,
      texte:
        'JURI attend sur le bord droit de l’écran : un clic ouvre l’aide de l’écran en cours, les raccourcis clavier et le chatbot, pour vérifier ses réponses à partir des sources communes que vous gérez.',
    },
    COMPTE,
    AIDES,
  ],
};

export function etapesVisite(role: Role): EtapeVisite[] {
  return ETAPES[role] ?? [];
}
