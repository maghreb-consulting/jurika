import type { Role } from '../../types/auth';

/**
 * Lot L6 (E6, CDC 3.1) : "Le compte d'administration de la plateforme est
 * presente sous le nom JURIKA dans toute l'interface et dans toutes les
 * communications."
 */
export const NOM_PLATEFORME = 'JURIKA';

/** Libelle d'un role, tel qu'il s'affiche a l'utilisateur. */
export function libelleRole(role: Role): string {
  switch (role) {
    case 'SUPER_ADMIN':
      return 'Administration de la plateforme';
    case 'SUPERVISEUR':
      return 'Superviseur';
    case 'EMPLOYE':
      return 'Employé';
    case 'CLIENT':
      return 'Client';
  }
}
