package ma.jurika.ticket.domain.model;

/**
 * Volet d'une formalite qui comporte un depot puis un retrait.
 *
 * <p>Le parcours du 9 septembre porte ces formalites sur DEUX lignes distinctes —
 * enregistrement du bail, depot du capital, statuts, acte de nomination, taxe
 * professionnelle, declaration d'existence, immatriculation, CNSS, livres
 * legaux, CNDP, agrements sectoriels, adhesion SIMPL.
 *
 * <p>Ce n'est pas une commodite d'affichage. Un contrat depose a l'enregistrement
 * n'est pas un contrat enregistre, et c'est la date du <b>depot</b> qui fait
 * courir l'attente du <b>retrait</b>. Sur des demarches administratives reelles,
 * la question « quand avons-nous depose ? » se pose des mois plus tard : sans ce
 * lien, elle n'aurait pas de reponse.
 */
public enum FormaliteVolet {
    DEPOT,
    RETRAIT
}
