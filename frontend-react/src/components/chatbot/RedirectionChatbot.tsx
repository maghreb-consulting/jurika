import { useEffect } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuthStore } from '../../store/authStore';
import { useAssistantStore } from '../../store/assistantStore';

/**
 * Lot L5 E8 (RG-COM-01) : la page pleine /chatbot est supprimee ; l'adresse est conservee
 * pour les anciens liens et favoris. Le cabinet arrive sur son tableau de bord avec le
 * panneau du chatbot ouvert ; le super-admin arrive sur l'ecran des sources communes.
 */
export function RedirectionChatbot() {
  const role = useAuthStore((s) => s.user?.role);
  const ouvrir = useAssistantStore((s) => s.ouvrir);
  const superAdmin = role === 'SUPER_ADMIN';

  useEffect(() => {
    if (!superAdmin) ouvrir();
  }, [superAdmin, ouvrir]);

  return <Navigate to={superAdmin ? '/admin/chatbot-sources' : '/dashboard'} replace />;
}
