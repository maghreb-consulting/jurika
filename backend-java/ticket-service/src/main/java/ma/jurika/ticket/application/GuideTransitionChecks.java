package ma.jurika.ticket.application;

import ma.jurika.ticket.domain.model.Demarche;
import ma.jurika.ticket.domain.model.DemarcheEtat;
import ma.jurika.ticket.domain.model.EntrepriseDossier;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketDemarche;
import ma.jurika.ticket.domain.model.TicketStatut;
import ma.jurika.ticket.domain.port.DemarcheReferentielRepository;
import ma.jurika.ticket.domain.port.DossierRepository;
import ma.jurika.ticket.domain.port.TicketDemarcheRepository;
import ma.jurika.ticket.domain.port.TransitionChecks;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Les « points de controle avant passage au statut suivant » de l'onglet 2 du
 * guide, rendus MECANIQUES.
 *
 * <p>Ce qui est verifie, et pourquoi :
 *
 * <ul>
 *   <li><b>Demarches du statut quitte</b> — toute demarche obligatoire doit etre
 *       cochee, toute demarche conditionnelle cochee ou explicitement ecartee.
 *       C'est la traduction fidele et complete des points de controle des
 *       statuts 1, 2 et 3, qui enumerent precisement les etapes du parcours.</li>
 *   <li><b>Jeu de variables</b> (controle 2 -> 3 du guide : « aucun champ non
 *       renseigne dans les modeles ») — verifie sur les variables que JURIKA
 *       stocke comme colonnes de premier rang du dossier. Les variables du guide
 *       sans contrepartie en base ne sont PAS inventees : elles sont listees
 *       telles quelles par {@link #variablesNonVerifiables(Ticket)} pour que le
 *       cabinet sache ce qui echappe au controle.</li>
 * </ul>
 *
 * <p>Ce qui n'est PAS verifiable et reste a la charge de l'employe : « honoraires
 * acceptes » (statut 1) et « facture soldee » (statut 4) — la facturation n'est
 * pas rattachee au ticket dans le modele actuel.
 *
 * <p>Un workflow dont le referentiel est vide ne produit aucun obstacle : les
 * huit autres workflows conservent exactement leur comportement.
 */
@Service
public class GuideTransitionChecks implements TransitionChecks {

    /**
     * Variables du guide effectivement resolvables sur le dossier. Toute autre
     * variable est signalee comme non verifiable plutot que d'etre ignoree en
     * silence ou, pire, consideree comme satisfaite.
     */
    private static final Map<String, Function<EntrepriseDossier, Object>> RESOLVEURS =
            Map.of(
                    "$DENOMINATION", EntrepriseDossier::raisonSociale,
                    "$FORME_JURIDIQUE", EntrepriseDossier::formeJuridique,
                    "$SIEGE_ADRESSE", EntrepriseDossier::adresseSiege,
                    "$SIEGE_VILLE", EntrepriseDossier::ville,
                    "$CAPITAL_CHIFFRES", EntrepriseDossier::capitalSocialMad,
                    "$ICE", EntrepriseDossier::ice,
                    "$RC_NUMERO", EntrepriseDossier::rcNumero,
                    "$IDENTIFIANT_FISCAL", EntrepriseDossier::identifiantFiscal,
                    "$IDENTIFIANT_TP", EntrepriseDossier::taxeProfessionnelle,
                    "$CNSS_NUMERO", EntrepriseDossier::cnss);

    private final DemarcheReferentielRepository referentiel;
    private final TicketDemarcheRepository etats;
    private final DossierRepository dossiers;

    public GuideTransitionChecks(DemarcheReferentielRepository referentiel,
                                  TicketDemarcheRepository etats,
                                  DossierRepository dossiers) {
        this.referentiel = referentiel;
        this.etats = etats;
        this.dossiers = dossiers;
    }

    @Override
    public List<String> obstacles(Ticket ticket, TicketStatut cible) {
        // Sortie laterale : l'annulation n'attend aucune demarche. Le motif est
        // exige par AnnuleHandler.
        if (cible == TicketStatut.ANNULE) return List.of();
        // Reprise d'un ticket annule : on ne rejoue pas les controles du statut
        // vise, sans quoi un ticket annule au statut 3 ne pourrait plus etre
        // repris. Le motif est exige par le handler.
        if (ticket.statut() == TicketStatut.ANNULE) return List.of();

        List<Demarche> duStatut = referentiel.findByWorkflowAndStatut(
                ticket.type().name(), ticket.statut());
        if (duStatut.isEmpty()) return List.of();

        Map<UUID, TicketDemarche> parDemarche = etats.findByTicket(ticket.workspaceId(), ticket.id());
        List<String> obstacles = new ArrayList<>();

        for (Demarche d : duStatut) {
            TicketDemarche etat = parDemarche.get(d.id());
            DemarcheEtat e = etat == null ? DemarcheEtat.A_FAIRE : etat.etat();
            if (e != DemarcheEtat.A_FAIRE) continue;
            obstacles.add(d.obligatoire()
                    ? "Demarche " + d.ordre() + " (obligatoire) non cochee : " + d.libelle()
                    : "Demarche " + d.ordre() + " (conditionnelle) ni cochee ni ecartee : " + d.libelle());
        }

        // Controle propre au passage « Generation des documents » -> « Deroulement
        // de la demarche » : le guide exige un jeu de variables complet.
        if (ticket.statut() == TicketStatut.GENERATION_DOCUMENTS) {
            obstacles.addAll(variablesManquantes(ticket, duStatut, parDemarche));
        }
        return obstacles;
    }

    /**
     * Variables alimentees par les etapes REELLEMENT accomplies et restees vides
     * sur le dossier. On n'exige rien d'une etape ecartee ou non faite : sa
     * variable n'avait pas a etre produite.
     */
    private List<String> variablesManquantes(Ticket ticket, List<Demarche> duStatut,
                                              Map<UUID, TicketDemarche> parDemarche) {
        if (ticket.dossierId() == null) return List.of();
        Optional<EntrepriseDossier> dossier = dossiers.findById(ticket.workspaceId(), ticket.dossierId());
        if (dossier.isEmpty()) return List.of();

        Map<String, Boolean> aVerifier = new LinkedHashMap<>();
        for (Demarche d : duStatut) {
            TicketDemarche etat = parDemarche.get(d.id());
            if (etat == null || etat.etat() != DemarcheEtat.COCHEE) continue;
            for (String variable : variablesDe(d)) {
                if (RESOLVEURS.containsKey(variable)) aVerifier.put(variable, true);
            }
        }
        List<String> manquantes = new ArrayList<>();
        for (String variable : aVerifier.keySet()) {
            Object valeur = RESOLVEURS.get(variable).apply(dossier.get());
            if (valeur == null || String.valueOf(valeur).isBlank()) {
                manquantes.add("Variable " + variable + " non renseignee sur le dossier "
                        + "(point de controle du guide : « jeu de variables complet »).");
            }
        }
        return manquantes;
    }

    /**
     * Variables du guide sans contrepartie verifiable en base, pour le statut
     * courant du ticket. Informatif : ces champs ne bloquent pas, mais l'employe
     * doit savoir qu'ils ne sont pas controles.
     */
    public List<String> variablesNonVerifiables(Ticket ticket) {
        List<String> out = new ArrayList<>();
        for (Demarche d : referentiel.findByWorkflowAndStatut(ticket.type().name(), ticket.statut())) {
            for (String variable : variablesDe(d)) {
                if (!RESOLVEURS.containsKey(variable) && !out.contains(variable)) out.add(variable);
            }
        }
        return out;
    }

    /**
     * Extrait les variables concretes de la colonne « Donnees alimentees ». Les
     * familles a joker ({@code $ASSOCIE_*}, {@code $GERANT_*}) sont ecartees :
     * le guide ne dit pas combien d'occurrences sont attendues, et bloquer sur
     * une famille reviendrait a inventer une regle.
     */
    private static List<String> variablesDe(Demarche d) {
        if (d.variablesAlimentees() == null || d.variablesAlimentees().isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\\$[A-Z0-9_]+\\*?")
                .matcher(d.variablesAlimentees());
        while (m.find()) {
            String v = m.group();
            if (v.endsWith("*") || v.endsWith("_")) continue;
            if (!out.contains(v)) out.add(v);
        }
        return out;
    }
}
