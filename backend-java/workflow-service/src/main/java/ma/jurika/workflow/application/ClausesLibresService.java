package ma.jurika.workflow.application;

import ma.jurika.common.audit.Auditable;
import ma.jurika.common.exception.ValidationException;
import ma.jurika.common.security.TenantContext;
import ma.jurika.workflow.domain.model.VariableDuDossier.Origine;
import ma.jurika.workflow.infrastructure.persistence.DossierVariableEntity;
import ma.jurika.workflow.infrastructure.persistence.DossierVariableJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Lot L3 (RG-GEN-05, RG-GEN-06) : clauses libres d'un ticket.
 *
 * <p>Une clause libre est une DONNEE DU TICKET : elle vit au magasin de variables
 * (boucle {@code CLAUSES_LIBRES}, une occurrence par clause), avec son auteur et sa date,
 * et elle est reutilisee a chaque regeneration (le determinisme est preserve : meme
 * magasin, meme acte). Seuls les champs modifies sont reecrits : l'auteur et la date
 * d'une clause inchangee sont conserves.
 */
@Service
public class ClausesLibresService {

    public static final String BOUCLE = "CLAUSES_LIBRES";

    /** Champ du formulaire -> variable de la boucle. */
    static final Map<String, String> CHAMPS = new LinkedHashMap<>();
    static {
        CHAMPS.put("document", "CLAUSE_DOCUMENT");
        CHAMPS.put("emplacement", "CLAUSE_EMPLACEMENT");
        CHAMPS.put("titre", "CLAUSE_TITRE");
        CHAMPS.put("texte", "CLAUSE_TEXTE");
        CHAMPS.put("resultat", "CLAUSE_RESULTAT");
        CHAMPS.put("voixPour", "CLAUSE_VOIX_POUR");
        CHAMPS.put("voixContre", "CLAUSE_VOIX_CONTRE");
        CHAMPS.put("abstentions", "CLAUSE_ABSTENTIONS");
    }

    /** Une clause libre, telle que l'ecran la saisit et que la generation l'imprime. */
    public record Clause(String document, String emplacement, String titre, String texte, String resultat,
                         String voixPour, String voixContre, String abstentions,
                         UUID saisiePar, Instant saisieLe) {}

    private final DossierVariableJpaRepository repository;

    public ClausesLibresService(DossierVariableJpaRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Clause> lister(UUID workspaceId, UUID ticketId) {
        TenantContext.set(workspaceId);
        Map<Short, Map<String, DossierVariableEntity>> parRang = new TreeMap<>();
        for (DossierVariableEntity e : repository.findByWorkspaceIdAndTicketId(workspaceId, ticketId)) {
            if (BOUCLE.equals(e.getBoucle())) {
                parRang.computeIfAbsent(e.getRang(), r -> new LinkedHashMap<>()).put(e.getVariable(), e);
            }
        }
        List<Clause> out = new ArrayList<>();
        for (Map<String, DossierVariableEntity> champs : parRang.values()) {
            DossierVariableEntity recent = champs.values().stream()
                    .max(Comparator.comparing(DossierVariableEntity::getSaisieLe)).orElseThrow();
            out.add(new Clause(v(champs, "CLAUSE_DOCUMENT"), v(champs, "CLAUSE_EMPLACEMENT"), v(champs, "CLAUSE_TITRE"),
                    v(champs, "CLAUSE_TEXTE"), v(champs, "CLAUSE_RESULTAT"), v(champs, "CLAUSE_VOIX_POUR"),
                    v(champs, "CLAUSE_VOIX_CONTRE"), v(champs, "CLAUSE_ABSTENTIONS"),
                    recent.getSaisieParId(), recent.getSaisieLe()));
        }
        return out;
    }

    /** Remplace les clauses du ticket ; titre, texte et document sont obligatoires. */
    @Transactional
    @Auditable(action = "CLAUSES_LIBRES_MODIFIEES", resourceType = "ticket", resourceIdExpr = "#ticketId")
    public List<Clause> remplacer(UUID workspaceId, UUID ticketId, UUID auteur, List<Clause> clauses) {
        TenantContext.set(workspaceId);
        List<Clause> liste = clauses == null ? List.of() : clauses;
        for (Clause c : liste) {
            if (blanc(c.document()) || blanc(c.titre()) || blanc(c.texte())) {
                throw new ValidationException("Chaque clause libre doit avoir un document, un titre et un texte.");
            }
        }
        short rang = 0;
        for (Clause c : liste) {
            Map<String, String> valeurs = new LinkedHashMap<>();
            valeurs.put("CLAUSE_DOCUMENT", c.document());
            valeurs.put("CLAUSE_EMPLACEMENT", c.emplacement());
            valeurs.put("CLAUSE_TITRE", c.titre());
            valeurs.put("CLAUSE_TEXTE", c.texte());
            valeurs.put("CLAUSE_RESULTAT", c.resultat());
            valeurs.put("CLAUSE_VOIX_POUR", c.voixPour());
            valeurs.put("CLAUSE_VOIX_CONTRE", c.voixContre());
            valeurs.put("CLAUSE_ABSTENTIONS", c.abstentions());
            for (Map.Entry<String, String> champ : valeurs.entrySet()) {
                var deja = repository.findEnBoucle(workspaceId, ticketId, BOUCLE, rang, champ.getKey());
                String valeur = champ.getValue();
                if (blanc(valeur)) {
                    deja.ifPresent(repository::delete);
                    continue;
                }
                if (deja.isPresent() && Objects.equals(deja.get().getValeur(), valeur)) {
                    continue; // inchange : l'auteur et la date d'origine restent
                }
                DossierVariableEntity e = deja.orElseGet(DossierVariableEntity::new);
                e.setWorkspaceId(workspaceId);
                e.setTicketId(ticketId);
                e.setVariable(champ.getKey());
                e.setBoucle(BOUCLE);
                e.setRang(rang);
                e.setValeur(valeur);
                e.setOrigine(Origine.SAISIE.name());
                e.setSaisieParId(auteur);
                e.setSaisieLe(Instant.now());
                e.setOccasion("clauses-libres");
                repository.save(e);
            }
            rang++;
        }
        repository.purgerBoucleAuDela(workspaceId, ticketId, BOUCLE, rang);
        return lister(workspaceId, ticketId);
    }

    private static String v(Map<String, DossierVariableEntity> champs, String variable) {
        DossierVariableEntity e = champs.get(variable);
        return e == null ? null : e.getValeur();
    }

    private static boolean blanc(String s) {
        return s == null || s.isBlank();
    }
}
