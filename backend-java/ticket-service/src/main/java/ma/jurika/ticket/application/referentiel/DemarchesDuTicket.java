package ma.jurika.ticket.application.referentiel;

import ma.jurika.common.exception.ConflictException;
import ma.jurika.ticket.domain.model.DelaiUnite;
import ma.jurika.ticket.domain.model.Demarche;
import ma.jurika.ticket.domain.model.EntrepriseDossier;
import ma.jurika.ticket.domain.model.FormaliteVolet;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketStatut;
import ma.jurika.ticket.domain.port.DemarcheReferentielRepository;
import ma.jurika.ticket.domain.port.DossierRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Lot L4b (E4, RG-DEM-01, 05, 08) : les demarches d'un ticket.
 *
 * <p>Creation : les lignes en service (inchange). Tout autre ticket : les lignes du
 * referentiel du corpus des <b>decisions du ticket</b>, dans la matrice de sa forme ; pour
 * une decision renvoyee (onglet "Renvois"), la matrice de la famille fait foi.
 *
 * <p>Chaque ligne est evaluee sur les donnees du ticket (magasin des variables) :
 * <ul>
 *   <li>condition remplie, ou absente : APPLICABLE ;</li>
 *   <li>condition contraire a la donnee : SANS_OBJET (la ligne ne s'affiche pas) ;</li>
 *   <li>donnee non saisie, condition en phrase, reste de sous-condition : A_APPRECIER
 *       (traitee comme conditionnelle : cochee, ou ecartee avec motif, RG-DEM-08).</li>
 * </ul>
 * Rien n'est suppose : une donnee absente ne rend jamais une condition vraie ou fausse.
 */
@Service
public class DemarchesDuTicket implements LignesDuTicket {

    public enum Applicabilite { APPLICABLE, A_APPRECIER, SANS_OBJET }

    /** Une ligne du corpus retenue pour le ticket, avec son evaluation. */
    public record LigneCorpus(Demarche demarche, String matrice, String decision, Applicabilite applicabilite,
                              String raison, String echeanceDonnee) {}

    /** Une decision proposee au choix de l'employe. */
    public record DecisionProposee(String matrice, int numero, String section, String code, String libelle,
                                   String typeDecision, String matriceRenvoi, Integer numeroRenvoi,
                                   boolean choisie) {}

    public record Choix(String matrice, String code) {}

    /** Ce que l'ecran des decisions doit savoir. */
    public record Decisions(boolean corpus, String versionReferentiel, String forme, List<String> matrices,
                            List<DecisionProposee> proposees, List<Choix> choisies, List<String> manques) {}

    private final JdbcTemplate db;
    private final DemarcheReferentielRepository referentiel;
    private final DossierRepository dossiers;

    public DemarchesDuTicket(JdbcTemplate db, DemarcheReferentielRepository referentiel, DossierRepository dossiers) {
        this.db = db;
        this.referentiel = referentiel;
        this.dossiers = dossiers;
    }

    // ------------------------------------------------------------------
    //  Lignes du ticket (vue, cochage, controles de passage)
    // ------------------------------------------------------------------

    @Override
    public List<Demarche> toutes(Ticket t) {
        if (!MatricesDuTicket.servieParLeCorpus(t.type())) return referentiel.findByWorkflow(t.type().name());
        return lignesCorpus(t).stream().filter(l -> l.applicabilite() != Applicabilite.SANS_OBJET)
                .map(LigneCorpus::demarche).toList();
    }

    @Override
    public List<Demarche> duStatut(Ticket t, TicketStatut statut) {
        if (!MatricesDuTicket.servieParLeCorpus(t.type())) return referentiel.findByWorkflowAndStatut(t.type().name(), statut);
        return toutes(t).stream().filter(d -> d.statutTicket() == statut).toList();
    }

    @Override
    public Optional<Demarche> parOrdre(Ticket t, int ordre) {
        if (!MatricesDuTicket.servieParLeCorpus(t.type())) return referentiel.findByWorkflowAndOrdre(t.type().name(), ordre);
        return toutes(t).stream().filter(d -> d.ordre() == ordre).findFirst();
    }

    /** Evaluation des lignes du corpus du ticket (vide pour la creation). */
    public List<LigneCorpus> lignesCorpus(Ticket t) {
        if (!MatricesDuTicket.servieParLeCorpus(t.type())) return List.of();
        Map<String, String> variables = variables(t);
        Map<UUID, LigneCorpus> parId = new LinkedHashMap<>();
        for (Choix c : choisies(t)) {
            List<Map<String, Object>> rangs = renvoi(c);
            List<Map<String, Object>> lignes = rangs.isEmpty()
                    ? db.queryForList("""
                        SELECT * FROM demarches_referentiel
                        WHERE corpus_version IS NOT NULL AND actif AND matrice = ? AND decision_code = ?
                          AND statut_ticket <> 'ANNULE'
                        """, c.matrice(), c.code())
                    : db.queryForList("""
                        SELECT * FROM demarches_referentiel
                        WHERE corpus_version IS NOT NULL AND actif AND matrice = ? AND numero_decision = ?
                          AND statut_ticket <> 'ANNULE'
                        """, rangs.get(0).get("matrice_renvoi"), rangs.get(0).get("numero_renvoi"));
            for (Map<String, Object> l : lignes) {
                UUID id = (UUID) l.get("id");
                if (parId.containsKey(id)) continue;
                parId.put(id, evaluer(l, variables));
            }
        }
        List<LigneCorpus> out = new ArrayList<>(parId.values());
        out.sort(Comparator.comparingInt((LigneCorpus l) -> l.demarche().statutTicket().position())
                .thenComparingInt(l -> l.demarche().ordre()));
        return out;
    }

    // ------------------------------------------------------------------
    //  Decisions du ticket
    // ------------------------------------------------------------------

    public Decisions decisions(Ticket t) {
        if (!MatricesDuTicket.servieParLeCorpus(t.type())) {
            return new Decisions(false, null, null, List.of(), List.of(), List.of(), List.of());
        }
        String forme = forme(t);
        List<String> matrices = MatricesDuTicket.matrices(t.type(), forme);
        List<String> manques = new ArrayList<>();
        String version = versionActive();
        if (version == null) {
            manques.add("R\u00e9f\u00e9rentiel des d\u00e9marches non charg\u00e9 : un super-administrateur doit importer le corpus.");
        }
        if (forme == null && t.type() != ma.jurika.ticket.domain.model.TicketType.SUCCURSALE_ETR
                && t.type() != ma.jurika.ticket.domain.model.TicketType.IMPORT) {
            manques.add("Forme juridique du dossier inconnue (SARL ou SARL AU) : renseignez-la sur la fiche du dossier.");
        }
        List<Choix> choisies = choisies(t);
        Set<String> cles = new LinkedHashSet<>();
        choisies.forEach(c -> cles.add(c.matrice() + "|" + c.code()));
        List<DecisionProposee> proposees = new ArrayList<>();
        for (String m : matrices) {
            Set<String> vues = new LinkedHashSet<>();
            for (Map<String, Object> d : db.queryForList("""
                    SELECT d.numero, d.section, d.code, d.libelle, d.type_decision, r.matrice_renvoi, r.numero_renvoi
                    FROM referentiel_decisions d
                    LEFT JOIN referentiel_renvois r ON r.actif AND r.matrice = d.matrice AND r.code = d.code
                    WHERE d.actif AND d.matrice = ? ORDER BY d.numero
                    """, m)) {
                String code = (String) d.get("code");
                if (!vues.add(code)) continue;
                proposees.add(new DecisionProposee(m, ((Number) d.get("numero")).intValue(), (String) d.get("section"),
                        code, (String) d.get("libelle"), (String) d.get("type_decision"),
                        (String) d.get("matrice_renvoi"),
                        d.get("numero_renvoi") == null ? null : ((Number) d.get("numero_renvoi")).intValue(),
                        cles.contains(m + "|" + code)));
            }
            // Decisions presentes aux demarches sans ligne a l'onglet d'origine (import de
            // dossier) : proposees sous leur code, sans libelle invente.
            for (String code : db.queryForList("""
                    SELECT DISTINCT decision_code FROM demarches_referentiel
                    WHERE corpus_version IS NOT NULL AND actif AND matrice = ? AND statut_ticket <> 'ANNULE'
                    ORDER BY decision_code
                    """, String.class, m)) {
                if (vues.add(code)) {
                    proposees.add(new DecisionProposee(m, 0, null, code, null, null, null, null,
                            cles.contains(m + "|" + code)));
                }
            }
        }
        if (version != null && choisies.isEmpty()) {
            manques.add("Aucune d\u00e9cision choisie : choisissez la ou les d\u00e9cisions du ticket pour obtenir ses d\u00e9marches.");
        }
        return new Decisions(true, version, forme, matrices, proposees, choisies, manques);
    }

    /** Remplace les decisions du ticket (employe ; ticket ni clos ni annule). */
    public void choisir(Ticket t, List<Choix> choix, UUID acteur) {
        if (!MatricesDuTicket.servieParLeCorpus(t.type())) {
            throw new ConflictException("La cr\u00e9ation suit son parcours : elle n'a pas de d\u00e9cisions \u00e0 choisir.");
        }
        if (t.statut() == TicketStatut.CLOTURE_DOSSIER || t.statut() == TicketStatut.ANNULE) {
            throw new ConflictException("Les d\u00e9cisions d'un ticket clos ou annul\u00e9 ne se modifient plus.");
        }
        Set<String> permises = new LinkedHashSet<>();
        decisions(t).proposees().forEach(p -> permises.add(p.matrice() + "|" + p.code()));
        for (Choix c : choix) {
            if (!permises.contains(c.matrice() + "|" + c.code())) {
                throw new ConflictException("D\u00e9cision inconnue pour ce ticket : " + c.code() + " (" + c.matrice() + ").");
            }
        }
        db.update("DELETE FROM ticket_decisions WHERE workspace_id = ? AND ticket_id = ?", t.workspaceId(), t.id());
        for (Choix c : choix) {
            db.update("""
                    INSERT INTO ticket_decisions (workspace_id, ticket_id, matrice, decision_code, ajoute_par)
                    VALUES (?,?,?,?,?) ON CONFLICT (ticket_id, matrice, decision_code) DO NOTHING
                    """, t.workspaceId(), t.id(), c.matrice(), c.code(), acteur);
        }
    }

    public List<Choix> choisies(Ticket t) {
        return db.query("""
                SELECT matrice, decision_code FROM ticket_decisions
                WHERE workspace_id = ? AND ticket_id = ? ORDER BY ajoute_le, decision_code
                """, (rs, i) -> new Choix(rs.getString(1), rs.getString(2)), t.workspaceId(), t.id());
    }

    public String versionActive() {
        List<String> v = db.queryForList("""
                SELECT corpus_version FROM referentiel_imports WHERE statut = 'APPLIQUE'
                ORDER BY importe_le DESC LIMIT 1
                """, String.class);
        return v.isEmpty() ? null : v.get(0);
    }

    // ------------------------------------------------------------------
    //  Delais (E5, RG-DEM-06)
    // ------------------------------------------------------------------

    /**
     * Delai d'une ligne du corpus sur ce ticket.
     *
     * @param depart       point de depart retenu (donnee du ticket, sinon saisi sur la ligne)
     * @param echeance     date limite calculee ou lue
     * @param attendu      DEPART ou ECHEANCE quand la ligne attend une saisie, sinon null
     * @param donnee       variable du corpus qui porte la date attendue, si la matrice la nomme
     */
    public record DelaiLigne(java.time.LocalDate depart, java.time.LocalDate echeance, String attendu, String donnee) {}

    public Map<UUID, DelaiLigne> delais(Ticket t, List<LigneCorpus> lignes) {
        Map<String, String> vars = variables(t);
        Map<UUID, Map<String, Object>> saisis = new LinkedHashMap<>();
        for (Map<String, Object> m : db.queryForList("""
                SELECT demarche_id, depart, echeance FROM ticket_demarche_delais WHERE workspace_id = ? AND ticket_id = ?
                """, t.workspaceId(), t.id())) {
            saisis.put((UUID) m.get("demarche_id"), m);
        }
        Map<UUID, DelaiLigne> out = new LinkedHashMap<>();
        for (LigneCorpus l : lignes) {
            Demarche d = l.demarche();
            Map<String, Object> s = saisis.getOrDefault(d.id(), Map.of());
            if (d.delaiValeur() != null && d.delaiUnite() != null) {
                String donnee = d.delaiReferenceDonnee();
                java.time.LocalDate depart = donnee == null ? null : date(vars.get(donnee));
                if (depart == null) depart = local(s.get("depart"));
                out.put(d.id(), depart == null
                        ? new DelaiLigne(null, null, "DEPART", donnee)
                        : new DelaiLigne(depart, ma.jurika.ticket.domain.service.EcheanceCalculator.echeance(
                                depart, d.delaiValeur(), d.delaiUnite()), null, donnee));
            } else if (l.echeanceDonnee() != null) {
                java.time.LocalDate echeance = date(vars.get(l.echeanceDonnee()));
                if (echeance == null) echeance = local(s.get("echeance"));
                out.put(d.id(), new DelaiLigne(null, echeance, echeance == null ? "ECHEANCE" : null, l.echeanceDonnee()));
            }
        }
        return out;
    }

    /** Saisie, sur la ligne, du point de depart ou de la date limite attendus (RG-DEM-06). */
    public void saisirDelai(Ticket t, int ordre, java.time.LocalDate depart, java.time.LocalDate echeance, UUID acteur) {
        if (t.statut() == TicketStatut.CLOTURE_DOSSIER || t.statut() == TicketStatut.ANNULE) {
            throw new ConflictException("Les d\u00e9lais d'un ticket clos ou annul\u00e9 ne se modifient plus.");
        }
        LigneCorpus ligne = lignesCorpus(t).stream().filter(l -> l.demarche().ordre() == ordre).findFirst()
                .orElseThrow(() -> new ConflictException("Aucune d\u00e9marche " + ordre + " sur ce ticket."));
        DelaiLigne d = delais(t, List.of(ligne)).get(ligne.demarche().id());
        boolean attendDepart = ligne.demarche().delaiValeur() != null && ligne.demarche().delaiUnite() != null;
        boolean attendEcheance = !attendDepart && ligne.echeanceDonnee() != null;
        if (d == null || (!attendDepart && !attendEcheance)) {
            throw new ConflictException("Cette d\u00e9marche n'a pas de d\u00e9lai \u00e0 calculer.");
        }
        if ((attendDepart && depart == null) || (attendEcheance && echeance == null)) {
            throw new ConflictException(attendDepart ? "Indiquez le point de d\u00e9part du d\u00e9lai."
                    : "Indiquez la date limite.");
        }
        db.update("""
                INSERT INTO ticket_demarche_delais (workspace_id, ticket_id, demarche_id, depart, echeance, saisi_par)
                VALUES (?,?,?,?,?,?)
                ON CONFLICT (ticket_id, demarche_id) DO UPDATE SET depart = EXCLUDED.depart,
                    echeance = EXCLUDED.echeance, saisi_par = EXCLUDED.saisi_par, saisi_le = now()
                """, t.workspaceId(), t.id(), ligne.demarche().id(), attendDepart ? depart : null,
                attendEcheance ? echeance : null, acteur);
    }

    /** Date du magasin : ISO ou jj/mm/aaaa ; illisible = absente (jamais devinee). */
    static java.time.LocalDate date(String brut) {
        if (brut == null || brut.isBlank()) return null;
        String b = brut.trim();
        try {
            if (b.matches("\\d{4}-\\d{2}-\\d{2}.*")) return java.time.LocalDate.parse(b.substring(0, 10));
            if (b.matches("\\d{2}/\\d{2}/\\d{4}")) {
                return java.time.LocalDate.parse(b, java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            }
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
        return null;
    }

    private static java.time.LocalDate local(Object o) {
        if (o instanceof java.sql.Date d) return d.toLocalDate();
        if (o instanceof java.time.LocalDate d) return d;
        return null;
    }

    // ------------------------------------------------------------------
    //  Evaluation
    // ------------------------------------------------------------------

    private List<Map<String, Object>> renvoi(Choix c) {
        return db.queryForList("""
                SELECT matrice_renvoi, numero_renvoi FROM referentiel_renvois
                WHERE actif AND matrice = ? AND code = ? AND numero_renvoi IS NOT NULL
                """, c.matrice(), c.code());
    }

    public Map<String, String> donnees(Ticket t) {
        return variables(t);
    }

    private Map<String, String> variables(Ticket t) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map<String, Object> v : db.queryForList("""
                SELECT variable, valeur FROM dossier_variables
                WHERE workspace_id = ? AND ticket_id = ? AND boucle IS NULL
                """, t.workspaceId(), t.id())) {
            Object valeur = v.get("valeur");
            if (valeur != null && !String.valueOf(valeur).isBlank()) out.put((String) v.get("variable"), String.valueOf(valeur));
        }
        return out;
    }

    static LigneCorpus evaluer(Map<String, Object> l, Map<String, String> variables) {
        List<String> raisons = new ArrayList<>();
        Applicabilite a = Applicabilite.APPLICABLE;
        a = pire(a, condition((String) l.get("sous_condition_variable"), "EGAL", (String) l.get("sous_condition_valeur"),
                variables, raisons, "d\u00e9cision"));
        if (l.get("sous_condition_variable") == null && l.get("sous_condition_texte") != null) {
            a = pire(a, Applicabilite.A_APPRECIER);
            raisons.add("d\u00e9cision : " + l.get("sous_condition_texte") + " (\u00e0 appr\u00e9cier)");
        } else if (l.get("sous_condition_texte") != null && a != Applicabilite.SANS_OBJET) {
            a = pire(a, Applicabilite.A_APPRECIER);
            raisons.add("d\u00e9cision : " + l.get("sous_condition_texte") + " (\u00e0 appr\u00e9cier)");
        }
        String op = (String) l.get("condition_operateur");
        if ("PHRASE".equals(op)) {
            a = pire(a, Applicabilite.A_APPRECIER);
            raisons.add("condition : " + l.get("condition_application") + " (\u00e0 appr\u00e9cier)");
        } else if (op != null) {
            a = pire(a, condition((String) l.get("condition_variable"), op, (String) l.get("condition_valeur"),
                    variables, raisons, "condition"));
        }
        String matrice = (String) l.get("matrice");
        int ordre = MatricesDuTicket.ordreTicket(matrice, ((Number) l.get("ordre")).intValue());
        boolean obligatoire = "O".equals(String.valueOf(l.get("obligatoire")).trim()) && a == Applicabilite.APPLICABLE;
        Number valeur = (Number) l.get("delai_valeur");
        String unite = (String) l.get("delai_unite");
        String volet = (String) l.get("formalite_volet");
        Demarche d = new Demarche((UUID) l.get("id"), "CORPUS", ordre, (String) l.get("phase_code"),
                (String) l.get("phase_libelle"), (String) l.get("libelle"),
                TicketStatut.valueOf((String) l.get("statut_ticket")), null, (String) l.get("organisme"), obligatoire,
                (String) l.get("condition_application"), null, null, (String) l.get("justificatifs_texte"),
                (String) l.get("modele_jurika"), (String) l.get("delai"), null, null,
                valeur == null ? null : valeur.intValue(), unite == null ? null : DelaiUnite.valueOf(unite),
                null, (String) l.get("delai_reference_donnee"),
                (String) l.get("formalite_code"), volet == null ? null : FormaliteVolet.valueOf(volet), List.of());
        return new LigneCorpus(d, matrice, (String) l.get("decision_code"), a,
                raisons.isEmpty() ? null : String.join(" ; ", raisons), (String) l.get("delai_echeance_donnee"));
    }

    private static Applicabilite condition(String variable, String operateur, String attendu,
                                           Map<String, String> variables, List<String> raisons, String quoi) {
        if (variable == null) return Applicabilite.APPLICABLE;
        String v = variables.get(variable);
        if ("EXISTE".equals(operateur)) {
            if (v != null) return Applicabilite.APPLICABLE;
            raisons.add(quoi + " : $" + variable + " non renseign\u00e9e (\u00e0 appr\u00e9cier)");
            return Applicabilite.A_APPRECIER;
        }
        if (v == null) {
            raisons.add(quoi + " : $" + variable + " non renseign\u00e9e (\u00e0 appr\u00e9cier)");
            return Applicabilite.A_APPRECIER;
        }
        if (normaliser(v).equals(normaliser(attendu))) return Applicabilite.APPLICABLE;
        raisons.add(quoi + " : $" + variable + " vaut \u00ab " + v + " \u00bb (attendu \u00ab " + attendu + " \u00bb)");
        return Applicabilite.SANS_OBJET;
    }

    private static String normaliser(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private static Applicabilite pire(Applicabilite a, Applicabilite b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }

    private String forme(Ticket t) {
        if (t.dossierId() == null) return null;
        return dossiers.findById(t.workspaceId(), t.dossierId()).map(EntrepriseDossier::formeJuridique)
                .map(Enum::name).filter(f -> f.equals("SARL") || f.equals("SARL_AU")).orElse(null);
    }
}
