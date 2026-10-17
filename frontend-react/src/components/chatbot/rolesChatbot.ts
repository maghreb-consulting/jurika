import type { Role } from '../../types/auth';

/**
 * Lot L5 E8 (RG-COM-01) : roles qui utilisent le chatbot juridique : le cabinet (employe,
 * superviseur) ; le super-admin l'utilise pour eprouver les sources qu'il depose. Pas le
 * client. Lot L6b (E11) : liste partagee entre le panneau du chatbot, JURI et la palette.
 */
export const ROLES_ASSISTANT: Role[] = ['EMPLOYE', 'SUPERVISEUR', 'SUPER_ADMIN'];

export function peutUtiliserChatbot(role: Role | undefined | null): boolean {
  return !!role && ROLES_ASSISTANT.includes(role);
}
