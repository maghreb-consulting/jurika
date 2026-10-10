package ma.jurika.workflow.application;

import ma.jurika.common.security.TenantContext;
import ma.jurika.workflow.domain.model.VariableDuDossier;
import ma.jurika.workflow.domain.model.VariableDuDossier.Origine;
import ma.jurika.workflow.infrastructure.persistence.DossierVariableEntity;
import ma.jurika.workflow.infrastructure.persistence.DossierVariableJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * LE MAGASIN DE VARIABLES DU DOSSIER — décision 2 du cabinet.
 *
 * <p>« Une variable se saisit une seule fois. La dénomination est saisie une
 * fois ; aucun document ne la redemande. Chaque valeur occupe sa variable,
 * disponible dès qu'un document en a besoin. »
 *
 * <p><b>Ce service est le seul écrivain et le seul lecteur du magasin.</b> Rien
 * d'autre ne touche {@code dossier_variables} : c'est ce qui permet d'affirmer
 * qu'il n'existe pas deux chemins pour la même valeur.
 *
 * <h2>Les quatre règles qu'il tient</h2>
 *
 * <ol>
 *   <li><b>Une variable, une valeur.</b> {@link #poser} est un <i>upsert</i> :
 *       reposer une variable écrase sa valeur, jamais ne l'ajoute. Les deux
 *       index uniques partiels de {@code V13} rendent le doublon impossible même
 *       si un appelant s'y prenait mal.</li>
 *   <li><b>Une correction se propage.</b> Il n'y a qu'un endroit à corriger ;
 *       tout document régénéré relit la valeur corrigée, sans ressaisie
 *       ailleurs. C'est la conséquence directe de la règle 1, pas un mécanisme
 *       de plus.</li>
 *   <li><b>Les valeurs déjà en base priment.</b> {@link #poserSiAbsente} sert
 *       les valeurs d'origine {@code BASE} : elles alimentent la variable sans
 *       jamais écraser ce qu'un humain a saisi. Une donnée que la plateforme
 *       détient déjà n'est pas redemandée — et n'efface pas davantage une
 *       correction.</li>
 *   <li><b>La provenance est tracée.</b> Qui, quand, à quelle occasion. Une
 *       valeur {@code SAISIE} porte toujours son auteur — la base le refuse
 *       autrement.</li>
 * </ol>
 *
 * <h2>Ce que le magasin ne contient pas</h2>
 *
 * <p>Il ne contient <b>que des variables de document</b>. L'état des formulaires
 * — quelle case est cochée, quelle étape est atteinte, quel brouillon est en
 * cours — reste dans {@code workflow_progress.data}, qui n'est plus jamais lu
 * pour produire un acte. Les deux ne se recouvrent pas : l'un est ce que l'écran
 * réaffiche, l'autre est ce qui s'imprime.
 */
@Service
public class MagasinVariables {

    private static final Logger log = LoggerFactory.getLogger(MagasinVariables.class);

    private final DossierVariableJpaRepository repository;

    public MagasinVariables(DossierVariableJpaRepository repository) {
        this.repository = repository;
    }

    // ------------------------------------------------------------------
    // Écriture
    // ------------------------------------------------------------------

    /**
     * Pose (ou corrige) une variable simple.
     *
     * <p>Une valeur vide ou {@code null} <b>retire</b> la variable du magasin
     * plutôt que d'y laisser une chaîne vide : « pas de valeur » et « valeur
     * vide » ne doivent pas se distinguer à la lecture, sinon un document pourrait
     * sortir avec un blanc là où le contrôle de complétude attendait une absence.
     */
    @Transactional
    public void poser(UUID workspaceId, UUID ticketId, String variable, String valeur,
                       Origine origine, UUID auteur, String occasion) {
        TenantContext.set(workspaceId);
        String nom = normaliser(variable);
        if (valeur == null || valeur.isBlank()) {
            repository.findSimple(workspaceId, ticketId, nom).ifPresent(repository::delete);
            return;
        }
        DossierVariableEntity e = repository.findSimple(workspaceId, ticketId, nom)
                .orElseGet(DossierVariableEntity::new);
        appliquer(e, workspaceId, ticketId, nom, null, null, valeur, origine, auteur, occasion);
        repository.save(e);
    }

    /**
     * Pose une variable <b>seulement si elle n'est pas déjà renseignée</b>.
     *
     * <p>C'est la porte des valeurs d'origine {@code BASE} : la plateforme
     * alimente ce qu'elle détient, sans jamais recouvrir ce qu'un humain a saisi
     * ni annuler une correction.
     *
     * @return {@code true} si la valeur a été posée, {@code false} si la place
     *         était déjà occupée.
     */
    @Transactional
    public boolean poserSiAbsente(UUID workspaceId, UUID ticketId, String variable, String valeur,
                                   Origine origine, UUID auteur, String occasion) {
        TenantContext.set(workspaceId);
        if (valeur == null || valeur.isBlank()) return false;
        String nom = normaliser(variable);
        Optional<DossierVariableEntity> deja = repository.findSimple(workspaceId, ticketId, nom);
        if (deja.isPresent() && deja.get().getValeur() != null && !deja.get().getValeur().isBlank()) {
            return false;
        }
        DossierVariableEntity e = deja.orElseGet(DossierVariableEntity::new);
        appliquer(e, workspaceId, ticketId, nom, null, null, valeur, origine, auteur, occasion);
        repository.save(e);
        return true;
    }

    /**
     * Remplace le contenu d'une boucle par la liste donnée.
     *
     * <p>Chaque occurrence est une carte {@code variable -> valeur}. Les
     * occurrences au-delà de la liste sont <b>purgées</b> : réduire une liste de
     * deux associés à un seul doit retirer le second, faute de quoi le document
     * rendrait un associé fantôme.
     *
     * <p>Une liste vide vide la boucle — et une boucle vide publie une liste
     * vide, jamais une occurrence fantôme (règle posée au lot B).
     */
    @Transactional
    public void poserBoucle(UUID workspaceId, UUID ticketId, String boucle,
                             List<Map<String, String>> occurrences,
                             Origine origine, UUID auteur, String occasion) {
        TenantContext.set(workspaceId);
        List<Map<String, String>> liste = occurrences == null ? List.of() : occurrences;
        short rang = 0;
        for (Map<String, String> occurrence : liste) {
            for (Map.Entry<String, String> champ : occurrence.entrySet()) {
                String nom = normaliser(champ.getKey());
                String valeur = champ.getValue();
                Optional<DossierVariableEntity> deja =
                        repository.findEnBoucle(workspaceId, ticketId, boucle, rang, nom);
                if (valeur == null || valeur.isBlank()) {
                    deja.ifPresent(repository::delete);
                    continue;
                }
                DossierVariableEntity e = deja.orElseGet(DossierVariableEntity::new);
                appliquer(e, workspaceId, ticketId, nom, boucle, rang, valeur, origine, auteur, occasion);
                repository.save(e);
            }
            rang++;
        }
        int purgees = repository.purgerBoucleAuDela(workspaceId, ticketId, boucle, rang);
        if (purgees > 0) {
            log.debug("magasin.boucle.purge ticket={} boucle={} rangs>={} lignes={}",
                    ticketId, boucle, rang, purgees);
        }
    }

    /**
     * Lot L3 (RG-VAR-02/03/05) : pose une donnee de la FICHE SOCIETE. Elle remplit une
     * place vide, et se met a jour quand la fiche change (une correction de la fiche se
     * repercute) ; elle ne recouvre jamais une donnee saisie, extraite ou calculee.
     *
     * @return {@code true} si la valeur a ete posee ou mise a jour.
     */
    @Transactional
    public boolean poserFiche(UUID workspaceId, UUID ticketId, String variable, String valeur) {
        TenantContext.set(workspaceId);
        if (valeur == null || valeur.isBlank()) return false;
        String nom = normaliser(variable);
        Optional<DossierVariableEntity> deja = repository.findSimple(workspaceId, ticketId, nom);
        if (deja.isPresent()) {
            DossierVariableEntity e = deja.get();
            boolean renseignee = e.getValeur() != null && !e.getValeur().isBlank();
            if (renseignee && !Origine.FICHE.name().equals(e.getOrigine())) return false;
            if (valeur.equals(e.getValeur())) return false;
        }
        DossierVariableEntity e = deja.orElseGet(DossierVariableEntity::new);
        appliquer(e, workspaceId, ticketId, nom, null, null, valeur, Origine.FICHE, null, "fiche-societe");
        repository.save(e);
        return true;
    }

    /**
     * Lot L3 (RG-VAR-09, D14 de L1) : marque la provenance d'une valeur deja posee --
     * une valeur lue sur une piece et confirmee par l'employe est EXTRAITE, non SAISIE.
     * Sans valeur a cette place, rien n'est marque.
     */
    @Transactional
    public void marquerOrigine(UUID workspaceId, UUID ticketId, String boucle, Short rang, String variable,
                               Origine origine, UUID auteur) {
        TenantContext.set(workspaceId);
        String nom = normaliser(variable);
        Optional<DossierVariableEntity> e = boucle == null
                ? repository.findSimple(workspaceId, ticketId, nom)
                : repository.findEnBoucle(workspaceId, ticketId, boucle, rang, nom);
        e.filter(x -> x.getValeur() != null && !x.getValeur().isBlank()).ifPresent(x -> {
            x.setOrigine(origine.name());
            x.setSaisieParId(auteur);
            repository.save(x);
        });
    }

    /** Rattache les variables du ticket au dossier, dès que celui-ci existe. */
    @Transactional
    public int rattacherAuDossier(UUID workspaceId, UUID ticketId, UUID dossierId) {
        TenantContext.set(workspaceId);
        if (dossierId == null) return 0;
        return repository.rattacherAuDossier(workspaceId, ticketId, dossierId);
    }

    // ------------------------------------------------------------------
    // Lecture
    // ------------------------------------------------------------------

    /** Toutes les variables du ticket, provenance comprise. */
    @Transactional(readOnly = true)
    public List<VariableDuDossier> lire(UUID workspaceId, UUID ticketId) {
        TenantContext.set(workspaceId);
        return repository.findByWorkspaceIdAndTicketId(workspaceId, ticketId).stream()
                .map(MagasinVariables::versModele)
                .sorted(Comparator.comparing(VariableDuDossier::variable))
                .toList();
    }

    /**
     * Les variables du ticket sous la forme que le moteur attend :
     * {@code NOM -> valeur} pour les variables simples,
     * {@code BOUCLE -> [ {NOM -> valeur}, … ]} pour les boucles, dans l'ordre
     * des rangs.
     *
     * <p>C'est <b>la seule source</b> de la construction de la charge utile.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> lirePourGeneration(UUID workspaceId, UUID ticketId) {
        TenantContext.set(workspaceId);
        Map<String, Object> simples = new LinkedHashMap<>();
        Map<String, TreeMap<Short, Map<String, Object>>> boucles = new LinkedHashMap<>();

        for (DossierVariableEntity e : repository.findByWorkspaceIdAndTicketId(workspaceId, ticketId)) {
            if (e.getValeur() == null || e.getValeur().isBlank()) continue;
            if (e.getBoucle() == null) {
                simples.put(e.getVariable(), e.getValeur());
            } else {
                boucles.computeIfAbsent(e.getBoucle(), k -> new TreeMap<>())
                        .computeIfAbsent(e.getRang(), k -> new LinkedHashMap<>())
                        .put(e.getVariable(), e.getValeur());
            }
        }

        Map<String, Object> sortie = new LinkedHashMap<>(simples);
        boucles.forEach((nom, parRang) -> sortie.put(nom, new ArrayList<>(parRang.values())));
        return sortie;
    }

    /** Vrai si la variable porte déjà une valeur — donc n'a pas à être redemandée. */
    @Transactional(readOnly = true)
    public boolean estRenseignee(UUID workspaceId, UUID ticketId, String variable) {
        TenantContext.set(workspaceId);
        return repository.findSimple(workspaceId, ticketId, normaliser(variable))
                .map(e -> e.getValeur() != null && !e.getValeur().isBlank())
                .orElse(false);
    }

    // ------------------------------------------------------------------

    /** Accepte {@code $DENOMINATION} comme {@code DENOMINATION}. */
    private static String normaliser(String variable) {
        String v = variable == null ? "" : variable.trim();
        return v.startsWith("$") ? v.substring(1) : v;
    }

    private static void appliquer(DossierVariableEntity e, UUID workspaceId, UUID ticketId,
                                   String variable, String boucle, Short rang, String valeur,
                                   Origine origine, UUID auteur, String occasion) {
        e.setWorkspaceId(workspaceId);
        e.setTicketId(ticketId);
        e.setVariable(variable);
        e.setBoucle(boucle);
        e.setRang(rang);
        e.setValeur(valeur);
        e.setOrigine(origine.name());
        // La base refuse une SAISIE sans auteur : on ne dégrade pas l'origine en
        // silence, on laisse la contrainte parler.
        e.setSaisieParId(auteur);
        e.setSaisieLe(java.time.Instant.now());
        e.setOccasion(occasion);
    }

    private static VariableDuDossier versModele(DossierVariableEntity e) {
        return new VariableDuDossier(
                e.getVariable(), e.getBoucle(), e.getRang(), e.getValeur(),
                Origine.of(e.getOrigine()), e.getSaisieParId(), e.getSaisieLe(), e.getOccasion());
    }
}
