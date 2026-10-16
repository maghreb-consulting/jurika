package ma.jurika.billing.application.contrat;

import ma.jurika.billing.application.contrat.ContratDepot.Acceptation;
import ma.jurika.billing.application.contrat.ContratDepot.Conditions;
import ma.jurika.billing.application.contrat.ContratDepot.Version;
import ma.jurika.billing.application.editeur.EditeurParametresService;
import ma.jurika.billing.infrastructure.persistence.EditeurParametresEntity;
import ma.jurika.billing.infrastructure.config.BillingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lot L7 (E6) : contrat d'abonnement (CDC 4.8, RG-CTR-01 a RG-CTR-09).
 *
 * <ul>
 *   <li>RG-CTR-03 / 05 : conditions particulieres ouvertes EN ATTENTE (super-admin ou partie
 *       abonnement, par {@link ContratAbonnementPort}) ; tant qu'elles le sont, le cabinet
 *       n'accede qu'a l'ecran du contrat (controle de la passerelle).</li>
 *   <li>RG-CTR-01 / 02 : acceptation par le superviseur (case et confirmation exigees par le
 *       serveur), preuve inalterable : texte exact, empreinte SHA-256, cabinet, compte,
 *       qualite, date et heure de Casablanca, adresse IP.</li>
 *   <li>RG-CTR-04 : exemplaire PDF regenere a l'identique depuis la preuve, copie par
 *       courriel (envoi journalise).</li>
 *   <li>RG-CTR-06 : renouvellement annuel, avis 30 jours avant l'echeance.</li>
 *   <li>RG-CTR-07 : versions du modele conservees, nouvelle version publiee au moins 30 jours
 *       avant son entree en vigueur, notifiee, acceptee a la connexion suivante.</li>
 *   <li>RG-CTR-08 : resiliation (date d'effet, motif), arret du forfait a la date d'effet,
 *       archivage sans suppression.</li>
 *   <li>RG-CTR-09 : historique consultable, rien n'est supprime.</li>
 * </ul>
 */
@Service
public class ContratService implements ContratAbonnementPort {

    private static final Logger log = LoggerFactory.getLogger(ContratService.class);

    public static final String OBJET_CP = "CONDITIONS_PARTICULIERES";
    public static final String OBJET_VERSION = "NOUVELLE_VERSION";
    public static final String REPONSE_ACCEPTEE = "ACCEPTEE";
    public static final String REPONSE_REFUSEE = "REFUSEE";
    public static final int PREAVIS_JOURS = 30;
    private static final Set<String> RYTHMES = Set.of("MENSUEL", "ANNUEL");
    private static final Set<String> MODES = Set.of("CARTE", "VIREMENT", "CHEQUE", "ESPECES");
    private static final Set<String> CIVILITES = Set.of("M.", "Mme");
    private static final DateTimeFormatter CASA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern MANQUANT = Pattern.compile("\\[non renseign\u00e9 : ([^\\]]+)\\]");

    // -- Vues ---------------------------------------------------------------------------

    public record ConditionsVue(UUID id, String reference, String forfait, String forfaitLibelle,
                                String rythmePaiement, BigDecimal prixHt, LocalDate dateDebut, LocalDate dateFin,
                                LocalDate echeanceCourante, String modePaiement, String statut, String origine,
                                java.time.Instant creeLe) {
        static ConditionsVue de(Conditions c) {
            return new ConditionsVue(c.id(), c.reference(), c.forfait(), ForfaitContrat.depuis(c.forfait()).libelle(),
                    c.rythmePaiement(), c.prixHt(), c.dateDebut(), c.dateFin(), c.echeanceCourante(), c.modePaiement(),
                    c.statut(), c.origine(), c.creeLe());
        }
    }

    public record AcceptationVue(UUID id, UUID conditionsId, String objet, String versionCode, String empreinteContrat,
                                 String signataire, String signataireQualite, String signataireCompte,
                                 String accepteLeCasablanca, String adresseIp, String cabinetEmail) {
        static AcceptationVue de(Acceptation a) {
            return new AcceptationVue(a.id(), a.conditionsId(), a.objet(), a.versionCode(), a.empreinteContrat(),
                    a.signataireCivilite() + " " + a.signataireNom() + " " + a.signatairePrenom(), a.signataireQualite(),
                    a.signataireCompte(), a.accepteLeCasablanca(), a.adresseIp(), a.cabinetEmail());
        }
    }

    public record VersionVue(UUID id, String code, LocalDate entreeEnVigueur, String empreinteModele,
                             String empreinteCg, String empreinteDpa, String origine, java.time.Instant publieeLe,
                             boolean retiree) {
        static VersionVue de(Version v) {
            return new VersionVue(v.id(), v.code(), v.entreeEnVigueur(), v.empreinteModele(), v.empreinteCg(),
                    v.empreinteDpa(), v.origine(), v.publieeLe(), v.retiree());
        }
    }

    /** Reponse du cabinet a une nouvelle version des conditions generales (article 16). */
    public record ReponseVue(UUID id, UUID conditionsId, String versionCode, String reponse, UUID acceptationId,
                             String signataireCompte, String reponduLeCasablanca, String adresseIp) {
        static ReponseVue de(ContratDepot.Reponse r) {
            return new ReponseVue(r.id(), r.conditionsId(), r.versionCode(), r.reponse(), r.acceptationId(),
                    r.signataireCompte(), r.reponduLeCasablanca(), r.adresseIp());
        }
    }

    /** Refus d'une nouvelle version : version lue et confirmation exigees par le serveur. */
    public record CommandeRefus(String versionCode, boolean confirmation) {}

    public record ResiliationVue(UUID conditionsId, String demandeurRole, LocalDate dateEffet, String motif,
                                 java.time.Instant enregistreeLe) {}

    /** Valeurs reprises de la derniere acceptation (saisie unique, P2). */
    public record Reprise(String civilite, String qualite, String formeCabinet, String rcVilleCabinet) {}

    public record Etat(String statut, boolean acceptationRequise, String objetAAccepter, ConditionsVue enAttente,
                       ConditionsVue courant, AcceptationVue derniereAcceptation, ResiliationVue resiliation,
                       VersionVue versionEnVigueur, VersionVue versionAVenir, Long joursAvantEcheance,
                       boolean avertissementEcheance, Reprise reprise) {}

    public record Apercu(String objet, ConditionsVue conditions, String versionCode, String texte, String empreinte,
                         List<String> manquants) {}

    public record Historique(List<ConditionsVue> conditions, List<AcceptationVue> acceptations,
                             List<ResiliationVue> resiliations, List<ContratDepot.Renouvellement> renouvellements,
                             List<ContratDepot.Avis> avis, List<ReponseVue> reponses) {}

    /**
     * {@code versionSansReponse} : code de la nouvelle version en vigueur a laquelle le cabinet n'a
     * pas encore repondu (null sinon) ; {@code derniereReponse} : sa derniere reponse.
     */
    public record LigneAdmin(UUID workspaceId, String statut, ConditionsVue enAttente, ConditionsVue courant,
                             AcceptationVue derniereAcceptation, ResiliationVue resiliation, Long joursAvantEcheance,
                             String versionSansReponse, ReponseVue derniereReponse) {}

    public record Exemplaire(String nomFichier, byte[] pdf) {}

    /** Commande d'acceptation (RG-CTR-01 : case cochee ET confirmation, verifiees ici). */
    public record CommandeAcceptation(String civilite, String qualite, String formeCabinet, String rcVilleCabinet,
                                      String empreinteLue, boolean caseCochee, boolean confirmation) {}

    public record Requerant(UUID userId, String role, String jeton, String adresseIp, String chaineIp) {}

    // -- Dependances --------------------------------------------------------------------

    private final ContratDepot depot;
    private final EditeurParametresService editeur;
    private final IdentiteCabinetSource identites;
    private final EssaiCabinetSource essais;
    private final ContratCourriel courriel;
    private final ContratHorloge horloge;
    private final ApplicationEventPublisher evenements;
    private final BillingProperties props;
    private final TransactionTemplate tx;

    public ContratService(ContratDepot depot, EditeurParametresService editeur, IdentiteCabinetSource identites,
                          EssaiCabinetSource essais,
                          ContratCourriel courriel, ContratHorloge horloge, ApplicationEventPublisher evenements,
                          BillingProperties props,
                          PlatformTransactionManager transactionManager) {
        this.depot = depot;
        this.editeur = editeur;
        this.identites = identites;
        this.essais = essais;
        this.courriel = courriel;
        this.horloge = horloge;
        this.evenements = evenements;
        this.props = props;
        this.tx = new TransactionTemplate(transactionManager);
    }

    // -- Port (partie abonnement) -------------------------------------------------------

    @Override
    public UUID ouvrirConditionsParticulieres(ConditionsParticulieres c, String modePaiement) {
        return ouvrir(c, modePaiement, "ABONNEMENT");
    }

    @Override
    public boolean acceptationEnAttente(UUID workspaceId) {
        if (workspaceId == null) return false;
        Boolean b = tx.execute(s -> {
            depot.porteeGlobale();
            return depot.enAttente(workspaceId);
        });
        return Boolean.TRUE.equals(b);
    }

    /**
     * Ouvre des conditions particulieres EN ATTENTE (RG-CTR-03, RG-CTR-05). Des conditions deja en
     * attente sont annulees (remplacees avant d'avoir ete acceptees) ; des conditions acceptees le
     * restent jusqu'a l'acceptation des nouvelles.
     */
    public UUID ouvrir(ConditionsParticulieres c, String modePaiement, String origine) {
        if (c == null || c.workspaceId() == null) {
            throw ContratException.invalide("CABINET_ABSENT", "Le cabinet des conditions particuli\u00e8res est obligatoire.");
        }
        ForfaitContrat forfait = ForfaitContrat.depuis(c.forfait());
        List<String> manquants = new ArrayList<>();
        if (c.rythmePaiement() == null || !RYTHMES.contains(c.rythmePaiement())) manquants.add("rythme de paiement (mensuel ou annuel)");
        if (c.prixHt() == null || c.prixHt().signum() <= 0) manquants.add("prix hors taxes");
        if (c.dateDebut() == null) manquants.add("date de d\u00e9but");
        if (modePaiement != null && !MODES.contains(modePaiement)) manquants.add("mode de paiement (carte, virement, ch\u00e8que ou esp\u00e8ces)");
        if (!manquants.isEmpty()) {
            throw new ContratException(HttpStatus.UNPROCESSABLE_ENTITY, "CONDITIONS_INCOMPLETES",
                    "Conditions particuli\u00e8res incompl\u00e8tes : " + String.join(", ", manquants) + ".", manquants);
        }
        if (c.prixHt().stripTrailingZeros().scale() > 2) {
            throw ContratException.invalide("PRIX_INVALIDE", "Le prix s\u2019\u00e9crit avec deux d\u00e9cimales au plus.");
        }
        // Article 5.1 des conditions generales : un an a compter de la date de debut.
        LocalDate fin = c.dateFin() != null ? c.dateFin() : c.dateDebut().plusYears(1);
        if (!fin.isAfter(c.dateDebut())) {
            throw ContratException.invalide("DATES_INVALIDES", "La date d\u2019\u00e9ch\u00e9ance doit suivre la date de d\u00e9but.");
        }
        return tx.execute(s -> {
            depot.porteeGlobale();
            depot.conditionsParStatut(c.workspaceId(), "EN_ATTENTE").ifPresent(ancienne ->
                    depot.changerStatut(c.workspaceId(), ancienne.id(), "EN_ATTENTE", "ANNULE", horloge.maintenant()));
            String reference = depot.prochaineReference(horloge.aujourdhui().getYear());
            UUID id = depot.insererConditions(c.workspaceId(), reference, forfait, c.rythmePaiement(), c.prixHt(),
                    c.dateDebut(), fin, modePaiement, origine, c.creePar());
            log.info("Conditions particulieres ouvertes en attente workspace={} reference={} origine={}",
                    c.workspaceId(), reference, origine);
            return id;
        });
    }

    // -- Etat et apercu (cabinet) -------------------------------------------------------

    public Etat etat(UUID workspaceId) {
        return tx.execute(s -> {
            LocalDate jour = horloge.aujourdhui();
            Optional<Conditions> enAttente = depot.conditionsParStatut(workspaceId, "EN_ATTENTE");
            Optional<Conditions> courant = depot.conditionsParStatut(workspaceId, "ACCEPTE");
            Optional<Acceptation> derniere = depot.derniereAcceptation(workspaceId);
            Optional<Version> vigueur = depot.versionEnVigueur(jour);
            Optional<Version> avenir = depot.versionAVenir(jour);
            List<ContratDepot.Resiliation> resiliations = depot.resiliationsDuCabinet(workspaceId);
            String objet = objetAAccepter(workspaceId, enAttente, courant, vigueur).orElse(null);
            String statut;
            if (enAttente.isPresent()) statut = "EN_ATTENTE";
            else if (courant.isPresent()) statut = "ACCEPTE";
            else if (!resiliations.isEmpty()) statut = "RESILIE";
            else statut = "AUCUN";
            Long jours = courant.map(c -> ChronoUnit.DAYS.between(jour, c.echeanceCourante())).orElse(null);
            ResiliationVue res = resiliations.isEmpty() ? null : vue(resiliations.get(0));
            Reprise reprise = derniere.map(a -> new Reprise(a.signataireCivilite(), a.signataireQualite(),
                    a.cabinetForme(), a.cabinetRcVille())).orElse(null);
            return new Etat(statut, objet != null, objet, enAttente.map(ConditionsVue::de).orElse(null),
                    courant.map(ConditionsVue::de).orElse(null), derniere.map(AcceptationVue::de).orElse(null), res,
                    vigueur.map(VersionVue::de).orElse(null), avenir.map(VersionVue::de).orElse(null), jours,
                    jours != null && jours >= 0 && jours <= PREAVIS_JOURS, reprise);
        });
    }

    /** Texte a lire avant l'acceptation, avec son empreinte et les mentions manquantes. */
    public Apercu apercu(UUID workspaceId, Requerant r, CommandeAcceptation c) {
        IdentiteCabinetSource.Identite identite = identites.lire(r.jeton());
        // L7 E13 : conditions de l essai du cabinet (mention du contrat) ; auth injoignable = refus nomme.
        Optional<EssaiCabinetSource.AcceptationEssai> essai = essais.lire(r.jeton());
        return tx.execute(s -> {
            AAccepter a = aAccepter(workspaceId);
            ContratRedaction.Redaction red = ContratRedaction.rediger(a.version().texteModele(), a.version().code(),
                    a.conditions(), editeur.identite(), identite, signataire(c), essai);
            return new Apercu(a.objet(), ConditionsVue.de(a.conditions()), a.version().code(), red.texte(),
                    red.empreinte(), red.manquants());
        });
    }

    // -- Acceptation (RG-CTR-01, RG-CTR-02) ---------------------------------------------

    public AcceptationVue accepter(UUID workspaceId, Requerant r, CommandeAcceptation c) {
        if (!"SUPERVISEUR".equals(r.role())) {
            throw new ContratException(HttpStatus.FORBIDDEN, "SUPERVISEUR_REQUIS",
                    "Seul le superviseur du cabinet peut accepter le contrat.", List.of());
        }
        if (!c.caseCochee()) {
            throw ContratException.invalide("CASE_NON_COCHEE",
                    "Cochez la case \u00ab J\u2019ai lu et j\u2019accepte le contrat d\u2019abonnement JURIKA \u00bb.");
        }
        if (!c.confirmation()) {
            throw ContratException.invalide("CONFIRMATION_ABSENTE", "Confirmez votre acceptation.");
        }
        List<String> manquants = new ArrayList<>();
        if (c.civilite() == null || !CIVILITES.contains(c.civilite())) manquants.add("civilit\u00e9 (M. ou Mme)");
        if (blanc(c.qualite())) manquants.add("qualit\u00e9 du signataire");
        if (!manquants.isEmpty()) {
            throw new ContratException(HttpStatus.UNPROCESSABLE_ENTITY, "SIGNATAIRE_INCOMPLET",
                    "Renseignez : " + String.join(", ", manquants) + ".", manquants);
        }
        if (blanc(r.adresseIp())) {
            throw new IllegalStateException("Adresse IP de l'acceptation absente");
        }
        IdentiteCabinetSource.Identite identite = identites.lire(r.jeton());
        // L7 E13 : lue AVANT toute ecriture ; auth injoignable = refus nomme (jamais une mention omise).
        Optional<EssaiCabinetSource.AcceptationEssai> essai = essais.lire(r.jeton());
        List<String> signataireManquant = new ArrayList<>();
        if (blanc(identite.signataireNom())) signataireManquant.add("nom du signataire");
        if (blanc(identite.signatairePrenom())) signataireManquant.add("pr\u00e9nom du signataire");
        if (blanc(identite.signataireCompte())) signataireManquant.add("compte du signataire");
        if (!signataireManquant.isEmpty()) {
            throw new ContratException(HttpStatus.UNPROCESSABLE_ENTITY, "SIGNATAIRE_INCOMPLET",
                    "Compte du signataire incomplet : " + String.join(", ", signataireManquant) + ".", signataireManquant);
        }
        Acceptation enregistree = sansDoublon(() -> tx.execute(s -> {
            AAccepter a = aAccepter(workspaceId);
            ContratRedaction.Redaction red = ContratRedaction.rediger(a.version().texteModele(), a.version().code(),
                    a.conditions(), editeur.identite(), identite, signataire(c), essai);
            if (c.empreinteLue() == null || !c.empreinteLue().equals(red.empreinte())) {
                throw ContratException.conflit("TEXTE_MODIFIE",
                        "Le texte du contrat a chang\u00e9 depuis votre lecture : relisez-le avant de l\u2019accepter.");
            }
            java.time.Instant le = horloge.maintenant();
            String casablanca = LocalDateTime.ofInstant(le, ContratHorloge.CASABLANCA).format(CASA);
            Acceptation brouillon = new Acceptation(null, workspaceId, a.conditions().id(), a.version().id(),
                    a.version().code(), a.objet(), red.texte(), red.empreinte(), a.version().empreinteCg(),
                    a.version().empreinteDpa(), r.userId(), identite.signataireCompte(), c.civilite(),
                    identite.signataireNom(), identite.signatairePrenom(), c.qualite().trim(), r.role(),
                    identite.denomination() != null ? identite.denomination()
                            : String.format(ContratRedaction.NON_RENSEIGNE, "d\u00e9nomination du Cabinet"),
                    identite.email(), propre(c.formeCabinet()), propre(c.rcVilleCabinet()), le, casablanca,
                    r.adresseIp(), r.chaineIp(),
                    // L7 E13 : figees dans la preuve, comme la mention dans le texte accepte.
                    essai.map(EssaiCabinetSource.AcceptationEssai::version).orElse(null),
                    essai.map(EssaiCabinetSource.AcceptationEssai::accepteesLe).orElse(null));
            if (OBJET_CP.equals(a.objet())) {
                depot.conditionsParStatut(workspaceId, "ACCEPTE").ifPresent(ancienne ->
                        depot.changerStatut(workspaceId, ancienne.id(), "ACCEPTE", "REMPLACE", le));
                depot.changerStatut(workspaceId, a.conditions().id(), "EN_ATTENTE", "ACCEPTE", le);
            } else {
                refuserSiDejaRepondu(workspaceId, a);
            }
            UUID id = depot.insererAcceptation(brouillon);
            if (OBJET_VERSION.equals(a.objet())) {
                // Article 16 : la reponse "Accepter" est enregistree avec la preuve de l'acceptation.
                depot.insererReponse(new ContratDepot.Reponse(null, workspaceId, a.conditions().id(), a.version().id(),
                        a.version().code(), REPONSE_ACCEPTEE, id, r.userId(), identite.signataireCompte(), r.role(), le,
                        casablanca, r.adresseIp(), r.chaineIp()));
            }
            return depot.acceptation(workspaceId, id).orElseThrow();
        }));
        log.info("Contrat accepte workspace={} conditions={} objet={} version={}", workspaceId,
                enregistree.conditionsId(), enregistree.objet(), enregistree.versionCode());
        envoyerCopie(workspaceId, enregistree);
        Conditions cp = tx.execute(s -> depot.conditions(workspaceId, enregistree.conditionsId()).orElseThrow());
        evenements.publishEvent(new ContratEvenements.ContratAccepte(workspaceId, cp.id(), enregistree.id(),
                enregistree.objet(), cp.forfait(), cp.rythmePaiement(), cp.prixHt(), cp.dateDebut(), cp.dateFin()));
        return AcceptationVue.de(enregistree);
    }

    // -- Refus d'une nouvelle version (RG-CTR-07, article 16) ---------------------------

    /**
     * "Refuser" la nouvelle version en vigueur : la reponse est enregistree (inalterable) et vaut
     * resiliation sans frais a l'echeance courante ; jusque-la, la version anterieure continue de
     * s'appliquer. Une seule reponse par contrat et par version.
     */
    public ReponseVue refuserNouvelleVersion(UUID workspaceId, Requerant r, CommandeRefus c) {
        if (!"SUPERVISEUR".equals(r.role())) {
            throw new ContratException(HttpStatus.FORBIDDEN, "SUPERVISEUR_REQUIS",
                    "Seul le superviseur du cabinet peut r\u00e9pondre aux nouvelles conditions g\u00e9n\u00e9rales.", List.of());
        }
        if (c == null || !c.confirmation()) {
            throw ContratException.invalide("CONFIRMATION_ABSENTE", "Confirmez votre refus.");
        }
        if (blanc(r.adresseIp())) {
            throw new IllegalStateException("Adresse IP du refus absente");
        }
        IdentiteCabinetSource.Identite identite = identites.lire(r.jeton());
        if (blanc(identite.signataireCompte())) {
            throw new ContratException(HttpStatus.UNPROCESSABLE_ENTITY, "SIGNATAIRE_INCOMPLET",
                    "Compte du signataire incomplet : compte du signataire.", List.of("compte du signataire"));
        }
        record Refus(ReponseVue reponse, ResiliationVue resiliation) {}
        Refus refus = sansDoublon(() -> tx.execute(s -> {
            AAccepter a = aAccepter(workspaceId);
            if (!OBJET_VERSION.equals(a.objet())) {
                throw ContratException.conflit("RIEN_A_REFUSER",
                        "Aucune nouvelle version des conditions g\u00e9n\u00e9rales n\u2019attend votre r\u00e9ponse.");
            }
            if (c.versionCode() == null || !c.versionCode().equals(a.version().code())) {
                throw ContratException.conflit("VERSION_CHANGEE",
                        "La version \u00e0 laquelle vous r\u00e9pondez n\u2019est plus celle en vigueur : rechargez la page.");
            }
            refuserSiDejaRepondu(workspaceId, a);
            java.time.Instant le = horloge.maintenant();
            String casablanca = LocalDateTime.ofInstant(le, ContratHorloge.CASABLANCA).format(CASA);
            UUID id = depot.insererReponse(new ContratDepot.Reponse(null, workspaceId, a.conditions().id(),
                    a.version().id(), a.version().code(), REPONSE_REFUSEE, null, r.userId(),
                    identite.signataireCompte(), r.role(), le, casablanca, r.adresseIp(), r.chaineIp()));
            ResiliationVue res = enregistrerResiliation(workspaceId, a.conditions(), r.userId(), "SUPERVISEUR", null,
                    "Refus des conditions g\u00e9n\u00e9rales version " + a.version().code(), le);
            ContratDepot.Reponse rep = depot.reponsesDuCabinet(workspaceId).stream()
                    .filter(x -> x.id().equals(id)).findFirst().orElseThrow();
            return new Refus(ReponseVue.de(rep), res);
        }));
        publierResiliation(workspaceId, refus.resiliation());
        log.info("Nouvelle version refusee workspace={} version={} resiliation effet={}", workspaceId,
                refus.reponse().versionCode(), refus.resiliation().dateEffet());
        return refus.reponse();
    }

    private void refuserSiDejaRepondu(UUID workspaceId, AAccepter a) {
        if (depot.reponse(workspaceId, a.conditions().id(), a.version().id()).isPresent()) {
            throw ContratException.conflit("REPONSE_DEJA_DONNEE",
                    "Vous avez d\u00e9j\u00e0 r\u00e9pondu \u00e0 la version " + a.version().code()
                            + " des conditions g\u00e9n\u00e9rales.");
        }
    }

    /** Deux reponses simultanees : la contrainte d'unicite tranche, la seconde est refusee (409). */
    private static <T> T sansDoublon(java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            throw ContratException.conflit("REPONSE_DEJA_DONNEE",
                    "Une r\u00e9ponse \u00e0 ces conditions g\u00e9n\u00e9rales est d\u00e9j\u00e0 enregistr\u00e9e.");
        }
    }

    // -- Exemplaire (RG-CTR-04) ---------------------------------------------------------

    public Exemplaire exemplaire(UUID workspaceId, UUID acceptationId, boolean porteeGlobale) {
        return tx.execute(s -> {
            if (porteeGlobale) depot.porteeGlobale();
            Acceptation a = depot.acceptation(workspaceId, acceptationId).orElseThrow(() ->
                    ContratException.introuvable("ACCEPTATION_INCONNUE", "Acceptation introuvable pour ce cabinet."));
            Version v = depot.version(a.versionId()).orElseThrow();
            Conditions cp = depot.conditions(workspaceId, a.conditionsId()).orElseThrow();
            String texte = ContratRedaction.exemplaire(v.texteModele(), a);
            LocalDate jour = LocalDate.parse(a.accepteLeCasablanca().substring(0, 10));
            byte[] pdf = ContratPdf.generer(texte, new ContratPdf.Entete(a.cabinetDenomination(), cp.reference(),
                    a.versionCode(), jour, manquantsDe(a.texteAccepte())));
            return new Exemplaire("contrat-abonnement-jurika-" + cp.reference() + "-" + a.versionCode() + ".pdf", pdf);
        });
    }

    public void renvoyerCopie(UUID workspaceId, UUID acceptationId) {
        Acceptation a = tx.execute(s -> depot.acceptation(workspaceId, acceptationId).orElseThrow(() ->
                ContratException.introuvable("ACCEPTATION_INCONNUE", "Acceptation introuvable pour ce cabinet.")));
        ContratCourriel.Resultat r = envoyerCopie(workspaceId, a);
        if (!r.envoye()) {
            throw new ContratException(HttpStatus.BAD_GATEWAY, "COPIE_NON_ENVOYEE",
                    "La copie n\u2019a pas pu \u00eatre envoy\u00e9e : " + r.erreur(), List.of());
        }
    }

    private ContratCourriel.Resultat envoyerCopie(UUID workspaceId, Acceptation a) {
        ContratCourriel.Resultat r;
        String destinataire = a.cabinetEmail();
        try {
            Exemplaire ex = exemplaire(workspaceId, a.id(), false);
            Map<String, Object> vars = new LinkedHashMap<>();
            vars.put("cabinet", a.cabinetDenomination());
            vars.put("signataire", a.signataireCivilite() + " " + a.signatairePrenom() + " " + a.signataireNom());
            vars.put("dateAcceptation", ContratRedaction.date(LocalDate.parse(a.accepteLeCasablanca().substring(0, 10)))
                    + " \u00e0 " + a.accepteLeCasablanca().substring(11));
            vars.put("version", a.versionCode());
            vars.put("empreinte", a.empreinteContrat());
            r = courriel.envoyer(destinataire, "Votre contrat d\u2019abonnement JURIKA", "email/contrat-copie", vars,
                    new ContratCourriel.PieceJointe(ex.nomFichier(), ex.pdf()));
        } catch (RuntimeException ex) {
            r = new ContratCourriel.Resultat(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
        journaliser(workspaceId, "COPIE_CONTRAT", a.id().toString(), destinataire, r);
        return r;
    }

    // -- Historique (RG-CTR-09) ---------------------------------------------------------

    public Historique historique(UUID workspaceId, boolean porteeGlobale) {
        return tx.execute(s -> {
            if (porteeGlobale) depot.porteeGlobale();
            return new Historique(
                    depot.conditionsDuCabinet(workspaceId).stream().map(ConditionsVue::de).toList(),
                    depot.acceptationsDuCabinet(workspaceId).stream().map(AcceptationVue::de).toList(),
                    depot.resiliationsDuCabinet(workspaceId).stream().map(ContratService::vue).toList(),
                    depot.renouvellementsDuCabinet(workspaceId),
                    depot.avisDuCabinet(workspaceId),
                    depot.reponsesDuCabinet(workspaceId).stream().map(ReponseVue::de).toList());
        });
    }

    // -- Resiliation (RG-CTR-08) --------------------------------------------------------

    /** Resiliation en ligne par le superviseur : effet a l'echeance courante (article 12.1). */
    public ResiliationVue resilierParCabinet(UUID workspaceId, UUID par, String motif) {
        return resilier(workspaceId, par, "SUPERVISEUR", null, motif, false);
    }

    /** Resiliation enregistree par le super-admin, date d'effet et motif saisis. */
    public ResiliationVue resilierParAdmin(UUID workspaceId, UUID par, LocalDate dateEffet, String motif) {
        if (dateEffet == null) {
            throw new ContratException(HttpStatus.UNPROCESSABLE_ENTITY, "DATE_EFFET_ABSENTE",
                    "La date d\u2019effet de la r\u00e9siliation est obligatoire.", List.of("date d\u2019effet"));
        }
        if (dateEffet.isBefore(horloge.aujourdhui())) {
            throw ContratException.invalide("DATE_EFFET_PASSEE", "La date d\u2019effet ne peut pas \u00eatre ant\u00e9rieure \u00e0 aujourd\u2019hui.");
        }
        return resilier(workspaceId, par, "SUPER_ADMIN", dateEffet, motif, true);
    }

    private ResiliationVue resilier(UUID workspaceId, UUID par, String role, LocalDate dateEffetDemandee, String motif,
                                    boolean global) {
        if (blanc(motif)) {
            throw new ContratException(HttpStatus.UNPROCESSABLE_ENTITY, "MOTIF_ABSENT",
                    "Le motif de la r\u00e9siliation est obligatoire.", List.of("motif"));
        }
        ResiliationVue vue = tx.execute(s -> {
            if (global) depot.porteeGlobale();
            Conditions cp = depot.conditionsParStatut(workspaceId, "ACCEPTE").orElseThrow(() ->
                    ContratException.conflit("AUCUN_CONTRAT_ACCEPTE", "Aucun contrat accept\u00e9 \u00e0 r\u00e9silier pour ce cabinet."));
            return enregistrerResiliation(workspaceId, cp, par, role, dateEffetDemandee, motif, horloge.maintenant());
        });
        publierResiliation(workspaceId, vue);
        return vue;
    }

    /** Resiliation du contrat accepte {@code cp}, dans la transaction courante (effet : echeance par defaut). */
    private ResiliationVue enregistrerResiliation(UUID workspaceId, Conditions cp, UUID par, String role,
                                                  LocalDate dateEffetDemandee, String motif, java.time.Instant le) {
        LocalDate effet = dateEffetDemandee != null ? dateEffetDemandee : cp.echeanceCourante();
        depot.insererResiliation(workspaceId, cp.id(), par, role, effet, motif.trim());
        depot.changerStatut(workspaceId, cp.id(), "ACCEPTE", "RESILIE", le);
        depot.conditionsParStatut(workspaceId, "EN_ATTENTE").ifPresent(p ->
                depot.changerStatut(workspaceId, p.id(), "EN_ATTENTE", "ANNULE", le));
        return new ResiliationVue(cp.id(), role, effet, motif.trim(), le);
    }

    /**
     * Arret du forfait a la date d'effet : l'acces est conserve jusque-la, puis le workspace est
     * archive (RG-TAR-07), jamais supprime. L'abonnement le fait sur cet evenement
     * (ResiliationAbonnementListener : periode payee bornee, archivage planifie).
     */
    private void publierResiliation(UUID workspaceId, ResiliationVue vue) {
        evenements.publishEvent(new ContratEvenements.ResiliationEnregistree(workspaceId, vue.conditionsId(),
                vue.dateEffet(), vue.motif(), vue.demandeurRole()));
        log.info("Resiliation enregistree workspace={} par={} effet={}", workspaceId, vue.demandeurRole(), vue.dateEffet());
    }

    // -- Versions (RG-CTR-07) -----------------------------------------------------------

    /** Versions : toutes pour le super-admin ; sans les versions retirees pour un cabinet. */
    public List<VersionVue> versions(boolean superAdmin) {
        return tx.execute(s -> (superAdmin ? depot.versions() : depot.versionsPresentees()).stream()
                .map(VersionVue::de).toList());
    }

    /** Texte d'une version ; une version retiree n'est jamais servie a un cabinet. */
    public Version version(String code, boolean superAdmin) {
        return tx.execute(s -> depot.versionParCode(code).filter(v -> superAdmin || !v.retiree()).orElseThrow(() ->
                ContratException.introuvable("VERSION_INCONNUE", "Version du contrat inconnue : " + code)));
    }

    /** Publication d'une nouvelle version, au moins 30 jours avant son entree en vigueur. */
    public VersionVue publierVersion(String code, String texte, LocalDate entree, UUID par) {
        if (blanc(code) || !code.trim().matches("^[0-9A-Za-z][0-9A-Za-z._-]{0,39}$")) {
            throw ContratException.invalide("CODE_INVALIDE",
                    "Code de version invalide (lettres, chiffres, point, tiret ; 40 caract\u00e8res au plus).");
        }
        if (entree == null) {
            throw ContratException.invalide("ENTREE_EN_VIGUEUR_ABSENTE", "La date d\u2019entr\u00e9e en vigueur est obligatoire.");
        }
        LocalDate auPlusTot = horloge.aujourdhui().plusDays(PREAVIS_JOURS);
        if (entree.isBefore(auPlusTot)) {
            throw ContratException.invalide("PREAVIS_INSUFFISANT", "Une nouvelle version entre en vigueur au moins "
                    + PREAVIS_JOURS + " jours apr\u00e8s sa publication (au plus t\u00f4t le " + ContratRedaction.date(auPlusTot) + ").");
        }
        // Le document complet peut etre colle : ligne de publication et piece D (essai) retirees.
        String normalise = ContratTexte.modeleDepuisSource(texte);
        ContratTexte.Sections sections = ContratTexte.sections(normalise);
        Version v = tx.execute(s -> {
            if (depot.versionParCode(code.trim()).isPresent()) {
                throw ContratException.conflit("VERSION_EXISTANTE", "Une version porte d\u00e9j\u00e0 le code " + code.trim() + ".");
            }
            UUID id = depot.insererVersion(code.trim(), normalise, sections, entree, "SUPER_ADMIN", par);
            return depot.version(id).orElseThrow();
        });
        notifierNouvelleVersion(v);
        return VersionVue.de(v);
    }

    /**
     * Version initiale (version 1, texte valide) chargee du classpath si elle manque, en vigueur des
     * son chargement. Sans preavis de 30 jours (RG-CTR-07) : c'est la premiere version presentee,
     * aucun cabinet n'a accepte de version anterieure (le projet du 12 octobre 2026 est retire par
     * billing V16). Si une acceptation existait deja, la charger sans preavis changerait le contrat
     * d'un cabinet : le demarrage est refuse (jamais en silence), la version se publie alors par
     * l'ecran du super-admin, 30 jours a l'avance.
     */
    public void initialiserVersionInitiale(String code, String source) {
        String modele = ContratTexte.modeleDepuisSource(source);
        ContratTexte.Sections sections = ContratTexte.sections(modele);
        tx.executeWithoutResult(s -> {
            depot.porteeGlobale();
            Optional<Version> existante = depot.versionParCode(code);
            if (existante.isPresent()) {
                if (!existante.get().empreinteModele().equals(sections.empreinteModele())) {
                    log.warn("Version {} du contrat deja chargee avec un autre texte (empreinte {}) : elle est conservee "
                            + "telle quelle, le fichier du classpath (empreinte {}) est ignore", code,
                            existante.get().empreinteModele(), sections.empreinteModele());
                }
                return;
            }
            int acceptations = depot.nombreAcceptations();
            if (acceptations > 0) {
                throw new IllegalStateException("Version initiale " + code + " du contrat absente alors que " + acceptations
                        + " acceptation(s) existent : chargement sans preavis refuse (RG-CTR-07)");
            }
            depot.insererVersion(code, modele, sections, horloge.aujourdhui(), "INITIALE", null);
            log.info("Version initiale du contrat d'abonnement chargee : {} (en vigueur le {})", code, horloge.aujourdhui());
        });
    }

    private void notifierNouvelleVersion(Version v) {
        List<Acceptation> destinataires = tx.execute(s -> {
            depot.porteeGlobale();
            List<Acceptation> l = new ArrayList<>();
            for (Conditions c : depot.acceptees()) {
                depot.derniereAcceptationDes(c.workspaceId(), c.id()).ifPresent(l::add);
            }
            return l;
        });
        for (Acceptation a : destinataires) {
            Map<String, Object> vars = Map.of("cabinet", a.cabinetDenomination(), "version", v.code(),
                    "entreeEnVigueur", ContratRedaction.date(v.entreeEnVigueur()));
            ContratCourriel.Resultat r = courriel.envoyer(a.cabinetEmail(),
                    "Nouvelle version des conditions g\u00e9n\u00e9rales JURIKA", "email/contrat-nouvelle-version", vars, null);
            journaliserGlobal(a.workspaceId(), "NOUVELLE_VERSION", v.code(), a.cabinetEmail(), r);
        }
    }

    // -- Echeances (RG-CTR-06), lancees chaque jour --------------------------------------

    public record BilanEcheances(int renouvellements, int avis) {}

    public BilanEcheances executerEcheances() {
        LocalDate jour = horloge.aujourdhui();
        record Avis(UUID workspaceId, String cle, String destinataire, Map<String, Object> vars) {}
        List<Avis> aEnvoyer = new ArrayList<>();
        int[] renouveles = {0};
        tx.executeWithoutResult(s -> {
            depot.porteeGlobale();
            for (Conditions c : depot.acceptees()) {
                LocalDate echeance = c.echeanceCourante();
                // Renouvellement tacite par periodes d'un an (article 5.2), aux conditions en vigueur.
                while (!echeance.isAfter(jour)) {
                    LocalDate suivante = echeance.plusYears(1);
                    depot.insererRenouvellement(c.workspaceId(), c.id(), echeance, suivante);
                    depot.changerEcheance(c.workspaceId(), c.id(), echeance, suivante);
                    echeance = suivante;
                    renouveles[0]++;
                }
                long jours = ChronoUnit.DAYS.between(jour, echeance);
                if (jours <= PREAVIS_JOURS) {
                    Optional<Acceptation> a = depot.derniereAcceptationDes(c.workspaceId(), c.id());
                    String cabinet = a.map(Acceptation::cabinetDenomination).orElse(c.reference());
                    Map<String, Object> vars = Map.of("cabinet", cabinet, "reference", c.reference(),
                            "echeance", ContratRedaction.date(echeance),
                            "forfait", ForfaitContrat.depuis(c.forfait()).libelle());
                    String cle = c.id() + ":" + echeance;
                    for (String dest : destinatairesEcheance(a.map(Acceptation::cabinetEmail).orElse(null))) {
                        if (!depot.avisEnvoye(c.workspaceId(), "ECHEANCE_30_JOURS", cle, dest)) {
                            aEnvoyer.add(new Avis(c.workspaceId(), cle, dest, vars));
                        }
                    }
                }
            }
        });
        for (Avis a : aEnvoyer) {
            ContratCourriel.Resultat r = courriel.envoyer(a.destinataire(),
                    "\u00c9ch\u00e9ance de votre abonnement JURIKA dans 30 jours au plus", "email/contrat-echeance", a.vars(), null);
            journaliserGlobal(a.workspaceId(), "ECHEANCE_30_JOURS", a.cle(), a.destinataire(), r);
        }
        return new BilanEcheances(renouveles[0], aEnvoyer.size());
    }

    private List<String> destinatairesEcheance(String cabinetEmail) {
        List<String> l = new ArrayList<>();
        if (!blanc(cabinetEmail)) l.add(cabinetEmail.trim());
        // Super-admin : adresse commerciale (jurika.billing.sales-email), a defaut l'expediteur verifie.
        String admin = props.adresseCommerciale();
        if (admin != null && !l.contains(admin)) l.add(admin);
        return l;
    }

    // -- Super-admin --------------------------------------------------------------------

    public List<LigneAdmin> listeAdmin() {
        return tx.execute(s -> {
            depot.porteeGlobale();
            LocalDate jour = horloge.aujourdhui();
            Optional<Version> vigueur = depot.versionEnVigueur(jour);
            Map<UUID, List<Conditions>> parCabinet = new LinkedHashMap<>();
            for (Conditions c : depot.toutesConditions()) {
                parCabinet.computeIfAbsent(c.workspaceId(), k -> new ArrayList<>()).add(c);
            }
            List<LigneAdmin> lignes = new ArrayList<>();
            for (Map.Entry<UUID, List<Conditions>> e : parCabinet.entrySet()) {
                UUID ws = e.getKey();
                Optional<Conditions> attente = e.getValue().stream().filter(c -> "EN_ATTENTE".equals(c.statut())).findFirst();
                Optional<Conditions> courant = e.getValue().stream().filter(c -> "ACCEPTE".equals(c.statut())).findFirst();
                List<ContratDepot.Resiliation> res = depot.resiliationsDuCabinet(ws);
                String statut = attente.isPresent() ? "EN_ATTENTE" : courant.isPresent() ? "ACCEPTE"
                        : !res.isEmpty() ? "RESILIE" : "AUCUN";
                Long jours = courant.map(c -> ChronoUnit.DAYS.between(jour, c.echeanceCourante())).orElse(null);
                String sansReponse = OBJET_VERSION.equals(objetAAccepter(ws, attente, courant, vigueur).orElse(null))
                        ? vigueur.get().code() : null;
                List<ContratDepot.Reponse> reponses = depot.reponsesDuCabinet(ws);
                lignes.add(new LigneAdmin(ws, statut, attente.map(ConditionsVue::de).orElse(null),
                        courant.map(ConditionsVue::de).orElse(null),
                        depot.derniereAcceptation(ws).map(AcceptationVue::de).orElse(null),
                        res.isEmpty() ? null : vue(res.get(0)), jours, sansReponse,
                        reponses.isEmpty() ? null : ReponseVue.de(reponses.get(0))));
            }
            return lignes;
        });
    }

    // -- Interne ------------------------------------------------------------------------

    private record AAccepter(String objet, Conditions conditions, Version version) {}

    private AAccepter aAccepter(UUID workspaceId) {
        LocalDate jour = horloge.aujourdhui();
        Optional<Conditions> enAttente = depot.conditionsParStatut(workspaceId, "EN_ATTENTE");
        Optional<Conditions> courant = depot.conditionsParStatut(workspaceId, "ACCEPTE");
        Optional<Version> vigueur = depot.versionEnVigueur(jour);
        String objet = objetAAccepter(workspaceId, enAttente, courant, vigueur).orElseThrow(() ->
                ContratException.conflit("RIEN_A_ACCEPTER", "Aucun contrat en attente d\u2019acceptation pour ce cabinet."));
        Version version = vigueur.orElseThrow(() -> new ContratException(HttpStatus.SERVICE_UNAVAILABLE,
                "AUCUNE_VERSION", "Aucune version du contrat n\u2019est en vigueur.", List.of()));
        return new AAccepter(objet, OBJET_CP.equals(objet) ? enAttente.get() : courant.get(), version);
    }

    private Optional<String> objetAAccepter(UUID workspaceId, Optional<Conditions> enAttente, Optional<Conditions> courant,
                                            Optional<Version> vigueur) {
        if (enAttente.isPresent()) return Optional.of(OBJET_CP);
        if (courant.isPresent() && vigueur.isPresent()) {
            Optional<Acceptation> derniere = depot.derniereAcceptationDes(workspaceId, courant.get().id());
            if (derniere.isPresent() && !derniere.get().versionId().equals(vigueur.get().id())) {
                return Optional.of(OBJET_VERSION);
            }
        }
        return Optional.empty();
    }

    private static ContratRedaction.Signataire signataire(CommandeAcceptation c) {
        return new ContratRedaction.Signataire(c.civilite(), c.qualite(), c.formeCabinet(), c.rcVilleCabinet());
    }

    private void journaliser(UUID workspaceId, String type, String cle, String destinataire, ContratCourriel.Resultat r) {
        tx.executeWithoutResult(s -> depot.insererAvis(workspaceId, type, cle,
                blanc(destinataire) ? "(adresse absente)" : destinataire, r.envoye() ? "ENVOYE" : "ECHEC", r.erreur()));
    }

    private void journaliserGlobal(UUID workspaceId, String type, String cle, String destinataire, ContratCourriel.Resultat r) {
        tx.executeWithoutResult(s -> {
            depot.porteeGlobale();
            depot.insererAvis(workspaceId, type, cle, blanc(destinataire) ? "(adresse absente)" : destinataire,
                    r.envoye() ? "ENVOYE" : "ECHEC", r.erreur());
        });
    }

    private static ResiliationVue vue(ContratDepot.Resiliation r) {
        return new ResiliationVue(r.conditionsId(), r.demandeurRole(), r.dateEffet(), r.motif(), r.enregistreeLe());
    }

    /** Mentions "[non renseigne : ...]" d'un texte accepte, dans l'ordre, sans doublon. */
    static List<String> manquantsDe(String texte) {
        List<String> l = new ArrayList<>();
        Matcher m = MANQUANT.matcher(texte);
        while (m.find()) {
            if (!l.contains(m.group(1))) l.add(m.group(1));
        }
        return l;
    }

    private static String propre(String s) {
        return blanc(s) ? null : s.trim();
    }

    private static boolean blanc(String s) {
        return s == null || s.isBlank();
    }
}
