package ma.jurika.auth.application.essai;

import ma.jurika.auth.infrastructure.persistence.WorkspaceEntity;
import ma.jurika.common.trial.EssaiRegles;

import java.time.Duration;
import java.time.Instant;

/**
 * Lot L7 (E4) : etat de l'essai d'un cabinet, tel que l'ecran l'affiche
 * (bandeau, rappels J-3 et J-1, lecture seule, date de suppression).
 *
 * @param enEssai          essai en cours ou en lecture seule (filigrane, aucun telechargement)
 * @param statut           TRIAL_ACTIVE, TRIAL_EXPIRED, CONVERTED, ou null (cabinet sans essai)
 * @param joursRestants    jours entiers restants avant la fin (arrondi superieur), 0 si termine
 * @param rappel           "J-3", "J-1" ou null (RG-ESS-08)
 * @param lectureSeule     RG-ESS-10
 * @param suppressionLe    debut de la lecture seule + 30 jours, null hors lecture seule
 * @param prolongeable     le super-admin peut encore prolonger (une fois, RG-ESS-11)
 */
public record EtatEssai(boolean enEssai, String statut, Instant debut, Instant fin, long joursRestants,
                        String rappel, boolean lectureSeule, Instant lectureSeuleDepuis,
                        Instant suppressionLe, Instant avertissementLe, Instant prolongeLe,
                        boolean prolongeable, Limites limites) {

    public record Limites(int dossiers, int superviseurs, int employes, long stockageOctets,
                          int questionsChatbot, String forfait) {
        static Limites essai() {
            return new Limites(EssaiRegles.MAX_DOSSIERS, EssaiRegles.MAX_SUPERVISEURS, EssaiRegles.MAX_EMPLOYES,
                    EssaiRegles.MAX_STOCKAGE_OCTETS, EssaiRegles.MAX_QUESTIONS_CHATBOT, EssaiRegles.FORFAIT_ESSAI);
        }
    }

    public static EtatEssai de(WorkspaceEntity ws, Instant maintenant) {
        String statut = ws.getTrialStatus();
        boolean actif = "TRIAL_ACTIVE".equals(statut);
        boolean lectureSeule = "TRIAL_EXPIRED".equals(statut);
        boolean enEssai = actif || lectureSeule;
        long jours = 0;
        String rappel = null;
        if (actif && ws.getTrialEndsAt() != null) {
            long secondes = Duration.between(maintenant, ws.getTrialEndsAt()).getSeconds();
            jours = secondes <= 0 ? 0 : (secondes + 86_399L) / 86_400L;
            if (secondes > 0 && jours <= EssaiRegles.RAPPEL_DERNIER_JOURS) {
                rappel = "J-1";
            } else if (secondes > 0 && jours <= EssaiRegles.RAPPEL_PREMIER_JOURS) {
                rappel = "J-3";
            }
        }
        Instant depuis = lectureSeule ? ws.getEssaiExpireLe() : null;
        Instant suppression = depuis == null ? null : depuis.plus(Duration.ofDays(EssaiRegles.LECTURE_SEULE_JOURS));
        boolean prolongeable = enEssai && ws.getEssaiProlongeLe() == null;
        return new EtatEssai(enEssai, statut, ws.getTrialStartedAt(), ws.getTrialEndsAt(), jours, rappel,
                lectureSeule, depuis, suppression, ws.getEssaiAvertissementLe(), ws.getEssaiProlongeLe(),
                prolongeable, enEssai ? Limites.essai() : null);
    }
}
