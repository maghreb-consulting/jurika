package ma.jurika.dataroom.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.jurika.common.audit.Auditable;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.security.Role;
import ma.jurika.common.security.TenantContext;
import ma.jurika.dataroom.api.dto.DataroomDtos.DocumentSummary;
import ma.jurika.dataroom.api.dto.FicheClientDtos.FicheClientView;
import ma.jurika.dataroom.api.dto.FicheClientDtos.FicheDossierView;
import ma.jurika.dataroom.api.dto.FicheClientDtos.Identifiant;
import ma.jurika.dataroom.api.dto.FicheClientDtos.Modification;
import ma.jurika.dataroom.api.dto.FicheClientDtos.Operation;
import ma.jurika.dataroom.api.dto.FicheClientDtos.VersionTp;
import ma.jurika.dataroom.infrastructure.persistence.DossierViewEntity;
import ma.jurika.dataroom.infrastructure.persistence.DossierViewJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.FicheSocieteLecture;
import ma.jurika.dataroom.infrastructure.persistence.FicheSocieteLecture.TicketLigne;
import ma.jurika.dataroom.infrastructure.persistence.FicheSocieteLecture.TraceLigne;
import ma.jurika.dataroom.infrastructure.persistence.FicheSocieteLecture.VersionTpLigne;
import ma.jurika.dataroom.infrastructure.persistence.UserViewEntity;
import ma.jurika.dataroom.infrastructure.persistence.UserViewJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.WorkflowProgressRow;
import ma.jurika.dataroom.infrastructure.persistence.WorkflowProgressViewJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.WorkspaceViewJpaRepository;
import ma.jurika.dataroom.infrastructure.pdf.FichesPdf;
import ma.jurika.dataroom.infrastructure.persistence.WorkspaceViewEntity;
import ma.jurika.common.pdf.CabinetIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Lot L5 (E7) : fiche client et fiche dossier (cahier des charges 10.2 et 10.3).
 *
 * <ul>
 *   <li>RG-FIC-01 : la fiche client ne porte QUE les identifiants de la societe ;</li>
 *   <li>RG-FIC-02 : la taxe professionnelle avec toutes ses versions, la version en vigueur
 *       signalee, une date de prise d'effet inconnue affichee "non renseignee" ;</li>
 *   <li>RG-FIC-03 : chaque information porte sa provenance, lue dans la trace des
 *       identifiants (ticket V41) ; une valeur anterieure a la trace est dite "non tracee",
 *       jamais attribuee a une source supposee ;</li>
 *   <li>RG-FIC-04 : la trace des modifications (auteur, date, ancienne et nouvelle valeur) se
 *       consulte a l'ecran ; la modification elle-meme passe par ticket-service
 *       ({@code PATCH /api/v1/dossiers/{id}/identifiants}) ;</li>
 *   <li>RG-FIC-05 a 08 : deux documents distincts generes a la demande au format PDF a la
 *       charte JURIKA (lot L5b E3 ; plus aucune sortie Word), deterministes ; chaque
 *       generation par l'employe responsable est conservee dans la Data Room du dossier, en
 *       versions successives (RG-DR-04). La fiche dossier est relue a chaque demande : elle
 *       est a jour de la derniere cloture (RG-CLO-04).</li>
 * </ul>
 * Acces : employe responsable du dossier (RG-DOS-01), superviseur et super-admin en lecture ;
 * jamais le client.
 */
@Service
public class FicheClientService {

    private static final Logger log = LoggerFactory.getLogger(FicheClientService.class);
    private static final ZoneId CASABLANCA = ZoneId.of("Africa/Casablanca");
    private static final DateTimeFormatter JJ_MM_AAAA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    static final String TYPE_FICHE_CLIENT = "FICHE_CLIENT";
    static final String TYPE_FICHE_DOSSIER = "FICHE_DOSSIER";
    static final String TITRE_FICHE_CLIENT = "Fiche client";
    static final String TITRE_FICHE_DOSSIER = "Fiche dossier";
    public static final String PDF_MIME = FichesPdf.MIME;

    static final String NON_RENSEIGNE = "Non renseigné";
    static final String NON_RENSEIGNEE = "Non renseignée";
    static final String DONNEE_MANQUANTE = "Donnée manquante";
    static final String NON_TRACEE = "Provenance non tracée (valeur antérieure au suivi)";

    /** Types de tickets -> libelle metier. */
    static final Map<String, String> TYPE_LABELS = Map.ofEntries(
            Map.entry("CREATION", "Création de société"),
            Map.entry("IMPORT", "Import de dossier existant"),
            Map.entry("MODIFICATION", "Modification statutaire"),
            Map.entry("DISSOLUTION", "Dissolution"),
            Map.entry("LIQUIDATION", "Liquidation"),
            Map.entry("SUCCURSALE_MA", "Ouverture de succursale (Maroc)"),
            Map.entry("SUCCURSALE_ETR", "Ouverture de succursale (société étrangère)"),
            Map.entry("FERMETURE_SUCCURSALE", "Fermeture de succursale"),
            Map.entry("PV_AGO", "Assemblée générale ordinaire"),
            Map.entry("ANNULATION", "Annulation"));

    /** Statuts de ticket -> libelle (guide du cabinet v2 ; anciens statuts V3 conserves). */
    static final Map<String, String> STATUT_LABELS = Map.ofEntries(
            Map.entry("CREATION_TICKET", "Création du ticket"),
            Map.entry("GENERATION_DOCUMENTS", "Génération des documents"),
            Map.entry("DEROULEMENT_DEMARCHE", "Déroulement des démarches"),
            Map.entry("CLOTURE_DOSSIER", "Clôturé"),
            Map.entry("ANNULE", "Annulé"),
            Map.entry("NOUVEAU", "Nouveau"),
            Map.entry("EN_COURS", "En cours"),
            Map.entry("CLOTURE", "Clôturé"));

    private static final Set<String> STATUTS_CLOTURES = Set.of("CLOTURE_DOSSIER", "CLOTURE");

    /** Colonne tracee (ticket V41) -> libelle. */
    static final Map<String, String> CHAMP_LABELS = Map.ofEntries(
            Map.entry("raison_sociale", "Dénomination"),
            Map.entry("forme_juridique", "Forme juridique"),
            Map.entry("capital_social_mad", "Capital social"),
            Map.entry("adresse_siege", "Adresse du siège"),
            Map.entry("ville", "Ville du siège"),
            Map.entry("rc_numero", "Numéro du registre du commerce"),
            Map.entry("rc_tribunal", "Tribunal du registre du commerce"),
            Map.entry("ice", "ICE"),
            Map.entry("identifiant_fiscal", "Identifiant fiscal"),
            Map.entry("taxe_professionnelle", "Taxe professionnelle (patente)"),
            Map.entry("cnss", "CNSS"),
            Map.entry("date_constitution", "Date de constitution"));

    private static final Map<String, String> FORMES = Map.of(
            "SARL", "Société à responsabilité limitée (SARL)",
            "SARL_AU", "Société à responsabilité limitée à associé unique (SARL AU)",
            "ETRANGERE", "Société étrangère");

    /** Document PDF produit, et sa conservation dans la Data Room. */
    public record FichePdf(byte[] contenu, String nomFichier, boolean conservee, Short version) {}

    private final DossierViewJpaRepository dossiers;
    private final WorkflowProgressViewJpaRepository workflows;
    private final UserViewJpaRepository users;
    private final WorkspaceViewJpaRepository workspaces;
    private final FicheSocieteLecture lecture;
    private final DataroomJuridiqueService juridique;
    private final DossierArchiveGuard archiveGuard;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public FicheClientService(DossierViewJpaRepository dossiers,
                              WorkflowProgressViewJpaRepository workflows,
                              UserViewJpaRepository users,
                              WorkspaceViewJpaRepository workspaces,
                              FicheSocieteLecture lecture,
                              DataroomJuridiqueService juridique,
                              DossierArchiveGuard archiveGuard,
                              ObjectMapper objectMapper,
                              Clock clock) {
        this.dossiers = dossiers;
        this.workflows = workflows;
        this.users = users;
        this.workspaces = workspaces;
        this.lecture = lecture;
        this.juridique = juridique;
        this.archiveGuard = archiveGuard;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ lecture

    @Transactional(readOnly = true)
    public FicheClientView ficheClient(UUID dossierId, Role role, UUID userId) {
        UUID ws = workspace();
        DossierViewEntity d = dossierAccessible(ws, dossierId, role, userId);
        return assembler(ws, d, role, userId, new Noms(ws));
    }

    @Transactional(readOnly = true)
    public FicheDossierView ficheDossier(UUID dossierId, Role role, UUID userId) {
        UUID ws = workspace();
        DossierViewEntity d = dossierAccessible(ws, dossierId, role, userId);
        Noms noms = new Noms(ws);
        FicheClientView fiche = assembler(ws, d, role, userId, noms);
        List<TicketLigne> tickets = lecture.tickets(ws, dossierId);
        noms.charger(tickets.stream().map(TicketLigne::assigneId).toList());
        List<Operation> operations = operations(ws, tickets, noms);
        Instant derniere = tickets.stream()
                .filter(t -> STATUTS_CLOTURES.contains(t.statut()) && t.clotureLe() != null)
                .map(TicketLigne::clotureLe).max(Comparator.naturalOrder()).orElse(null);
        return new FicheDossierView(fiche, operations, derniere);
    }

    // ------------------------------------------------------------------ PDF

    @Transactional
    @Auditable(action = "FICHE_CLIENT_GENERATED", resourceType = "dossier", resourceIdExpr = "#dossierId")
    public FichePdf genererFicheClient(UUID dossierId, Role role, UUID userId) {
        FicheClientView fiche = ficheClient(dossierId, role, userId);
        byte[] pdf = FichesPdf.ficheClient(identiteCabinet(), fiche, aujourdhui());
        return conserver(dossierId, role, userId, TYPE_FICHE_CLIENT, TITRE_FICHE_CLIENT,
                "Fiche_client_" + nomSur(fiche.denomination()) + ".pdf", pdf);
    }

    @Transactional
    @Auditable(action = "FICHE_DOSSIER_GENERATED", resourceType = "dossier", resourceIdExpr = "#dossierId")
    public FichePdf genererFicheDossier(UUID dossierId, Role role, UUID userId) {
        FicheDossierView vue = ficheDossier(dossierId, role, userId);
        byte[] pdf = FichesPdf.ficheDossier(identiteCabinet(), vue, aujourdhui());
        return conserver(dossierId, role, userId, TYPE_FICHE_DOSSIER, TITRE_FICHE_DOSSIER,
                "Fiche_dossier_" + nomSur(vue.fiche().denomination()) + ".pdf", pdf);
    }

    /** Date d'edition imprimee : jour courant a Casablanca (horloge injectee). */
    private LocalDate aujourdhui() {
        return LocalDate.now(clock.withZone(CASABLANCA));
    }

    /** En-tete du cabinet : donnees du workspace (nom affiche sur les documents, sinon denomination). */
    private CabinetIdentity identiteCabinet() {
        UUID ws = workspace();
        return workspaces.findById(ws).map(FicheClientService::identite)
                .orElseGet(() -> CabinetIdentity.ofName(null, null));
    }

    private static CabinetIdentity identite(WorkspaceViewEntity w) {
        return CabinetIdentity.resolve(w.getNomAfficheDocuments(), w.getName(), w.getLogoBytes(),
                w.getLogoContentType(), w.getAdresse(), w.getTelephone(), w.getContactEmail(), w.getSiteWeb(),
                w.getIce(), w.getRcNumber(), w.getIfFiscal());
    }

    /**
     * L'employe responsable conserve chaque generation dans la Data Room (nouvelle version du
     * meme emplacement). Le superviseur et le super-admin, en lecture seule (RG-U02-04),
     * telechargent sans rien ecrire ; une societe archivee (Data Room en lecture seule) aussi.
     */
    private FichePdf conserver(UUID dossierId, Role role, UUID userId, String type, String titre,
                               String nomFichier, byte[] pdf) {
        if (role != Role.EMPLOYE || archiveGuard.isReadOnly(dossierId)) {
            return new FichePdf(pdf, nomFichier, false, null);
        }
        DocumentSummary doc = juridique.uploadVersion(dossierId, type, titre, null,
                new OctetsMultipartFile(nomFichier, PDF_MIME, pdf), userId);
        return new FichePdf(pdf, nomFichier, true, doc.version());
    }

    // ------------------------------------------------------------------ assemblage

    private FicheClientView assembler(UUID ws, DossierViewEntity d, Role role, UUID userId, Noms noms) {
        List<TraceLigne> traces = lecture.traces(ws, d.getId());
        List<VersionTpLigne> versions = lecture.versionsTp(ws, d.getId());
        String typeOrigine = lecture.typeDuTicketOrigine(ws, d.getId());
        Set<UUID> ticketIds = new HashSet<>();
        Set<UUID> personnes = new HashSet<>();
        for (TraceLigne t : traces) {
            if (t.ticketId() != null) ticketIds.add(t.ticketId());
            if (t.auteurId() != null) personnes.add(t.auteurId());
        }
        for (VersionTpLigne v : versions) if (v.saisiPar() != null) personnes.add(v.saisiPar());
        noms.charger(personnes);
        Map<UUID, TicketLigne> ticketsCites = lecture.ticketsParId(ws, ticketIds);
        Provenances prov = new Provenances(traces, ticketsCites, noms, typeOrigine);

        List<Identifiant> ids = new ArrayList<>();
        ids.add(prov.identifiant("DENOMINATION", "Dénomination", "raison_sociale",
                d.getRaisonSociale(), d.getRaisonSociale()));
        ids.add(prov.identifiant("FORME", "Forme juridique", "forme_juridique",
                d.getFormeJuridique(), d.getFormeJuridique() == null ? null
                        : FORMES.getOrDefault(d.getFormeJuridique(), d.getFormeJuridique())));
        ids.add(prov.identifiant("CAPITAL", "Capital social", "capital_social_mad",
                d.getCapitalSocialMad() == null ? null : d.getCapitalSocialMad().toPlainString(),
                capital(d.getCapitalSocialMad())));
        String siege = joindre(d.getAdresseSiege(), d.getVille());
        ids.add(prov.identifiant("SIEGE", "Siège social", d.getAdresseSiege() != null ? "adresse_siege" : "ville",
                d.getAdresseSiege() != null ? d.getAdresseSiege() : d.getVille(), siege));
        String rc = vide(d.getRcNumero()) ? null
                : d.getRcNumero() + (vide(d.getRcTribunal()) ? "" : " (tribunal : " + d.getRcTribunal().trim() + ")");
        ids.add(prov.identifiant("RC", "Registre du commerce", "rc_numero", d.getRcNumero(), rc));
        ids.add(prov.identifiant("ICE", "ICE", "ice", d.getIce(), d.getIce()));
        ids.add(prov.identifiant("IF", "Identifiant fiscal", "identifiant_fiscal",
                d.getIdentifiantFiscal(), d.getIdentifiantFiscal()));
        ids.add(prov.identifiant("TP", "Taxe professionnelle (patente) en vigueur", "taxe_professionnelle",
                d.getTaxeProfessionnelle(), d.getTaxeProfessionnelle()));
        ids.add(prov.identifiant("CNSS", "CNSS", "cnss", d.getCnss(), d.getCnss()));

        List<VersionTp> tp = new ArrayList<>();
        for (int i = 0; i < versions.size(); i++) {
            VersionTpLigne v = versions.get(i);
            tp.add(new VersionTp(v.numero(), v.dateEffet(), i == versions.size() - 1, v.saisiLe(),
                    noms.nom(v.saisiPar())));
        }

        List<Modification> modifs = new ArrayList<>();
        for (int i = traces.size() - 1; i >= 0; i--) {
            TraceLigne t = traces.get(i);
            modifs.add(new Modification(t.champ(), CHAMP_LABELS.getOrDefault(t.champ(), t.champ()),
                    t.ancienneValeur(), t.nouvelleValeur(), prov.libelle(t), noms.nom(t.auteurId()), t.date()));
        }

        boolean modifiable = role == Role.EMPLOYE && userId != null && userId.equals(d.getResponsableId());
        return new FicheClientView(d.getId(), cabinet(ws), d.getRaisonSociale(), ids, tp, modifs, modifiable);
    }

    private List<Operation> operations(UUID ws, List<TicketLigne> tickets, Noms noms) {
        if (tickets.isEmpty()) return List.of();
        Map<UUID, WorkflowProgressRow> wf = new HashMap<>();
        for (WorkflowProgressRow row : workflows.findRowsByWorkspaceAndTickets(ws,
                tickets.stream().map(TicketLigne::id).toList())) {
            wf.put(row.getTicketId(), row);
        }
        List<Operation> out = new ArrayList<>(tickets.size());
        for (TicketLigne t : tickets) {
            String sousType = null;
            if ("MODIFICATION".equals(t.type()) && wf.containsKey(t.id())) {
                sousType = ModificationSousTypes.enPhrase(parseJson(wf.get(t.id()).getDataJson()));
            }
            Instant fin = null;
            String nature = null;
            if ("ANNULE".equals(t.statut())) {
                fin = t.annuleLe();
                nature = "ANNULATION";
            } else if (STATUTS_CLOTURES.contains(t.statut())) {
                fin = t.clotureLe();
                nature = "CLOTURE";
            }
            out.add(new Operation(t.id(), t.type(), TYPE_LABELS.getOrDefault(t.type(), t.type()),
                    vide(sousType) ? null : sousType, t.reference(), t.creeLe(), fin, nature, t.statut(),
                    STATUT_LABELS.getOrDefault(t.statut(), t.statut()),
                    t.assigneId() == null ? NON_RENSEIGNE : noms.nom(t.assigneId())));
        }
        return out;
    }

    /** Provenance d'une valeur en vigueur : la derniere trace qui l'a posee (RG-FIC-03). */
    private static final class Provenances {
        private final List<TraceLigne> traces;
        private final Map<UUID, TicketLigne> tickets;
        private final Noms noms;
        private final String typeOrigine;

        Provenances(List<TraceLigne> traces, Map<UUID, TicketLigne> tickets, Noms noms, String typeOrigine) {
            this.traces = traces;
            this.tickets = tickets;
            this.noms = noms;
            this.typeOrigine = typeOrigine;
        }

        Identifiant identifiant(String code, String libelle, String champ, String brute, String affichee) {
            if (vide(brute)) return new Identifiant(code, libelle, null, DONNEE_MANQUANTE, null);
            for (int i = traces.size() - 1; i >= 0; i--) {
                TraceLigne t = traces.get(i);
                if (champ.equals(t.champ()) && memeValeur(t.nouvelleValeur(), brute)) {
                    return new Identifiant(code, libelle, affichee, libelle(t), t.origine());
                }
            }
            return new Identifiant(code, libelle, affichee, NON_TRACEE, "NON_TRACEE");
        }

        String libelle(TraceLigne t) {
            TicketLigne ticket = t.ticketId() == null ? null : tickets.get(t.ticketId());
            String ref = ticket == null || ticket.reference() == null ? "" : " (ticket " + ticket.reference() + ")";
            return switch (t.origine()) {
                case "SAISIE" -> "Saisie manuelle par " + noms.nom(t.auteurId()) + ", le " + date(t.date());
                case "FIN_PARCOURS" -> "Obtenu en fin de parcours" + ref;
                case "EXTRAITE" -> "Lu sur une pièce et confirmé" + ref;
                case "OUVERTURE" -> "IMPORT".equals(typeOrigine) ? "Reprise à l’import du dossier"
                        : "CREATION".equals(typeOrigine) ? "Parcours de création"
                        : "Ouverture du dossier";
                case "PARCOURS" -> ticket == null ? "Mise à jour par un parcours"
                        : "IMPORT".equals(ticket.type()) ? "Reprise à l’import du dossier" + ref
                        : "Parcours « " + TYPE_LABELS.getOrDefault(ticket.type(), ticket.type()) + " »" + ref;
                default -> t.origine();
            };
        }

        private static boolean memeValeur(String tracee, String courante) {
            if (tracee == null || courante == null) return false;
            String a = tracee.trim();
            String b = courante.trim();
            if (a.equals(b)) return true;
            try {
                return new BigDecimal(a).compareTo(new BigDecimal(b)) == 0;
            } catch (NumberFormatException ex) {
                return false;
            }
        }
    }

    /** Noms affiches des employes, charges par lots. */
    private final class Noms {
        private final UUID ws;
        private final Map<UUID, String> parId = new HashMap<>();

        Noms(UUID ws) {
            this.ws = ws;
        }

        void charger(java.util.Collection<UUID> ids) {
            Set<UUID> manquants = new HashSet<>();
            for (UUID id : ids) if (id != null && !parId.containsKey(id)) manquants.add(id);
            if (manquants.isEmpty()) return;
            for (UserViewEntity u : users.findAllByWorkspaceIdAndIdIn(ws, manquants)) {
                parId.put(u.getId(), u.displayName());
            }
        }

        String nom(UUID id) {
            if (id == null) return NON_RENSEIGNE;
            return parId.getOrDefault(id, "Compte inconnu");
        }
    }

    // ------------------------------------------------------------------ acces

    private static UUID workspace() {
        UUID ws = TenantContext.get();
        if (ws == null) throw new NotFoundException("Dossier inconnu");
        return ws;
    }

    private DossierViewEntity dossierAccessible(UUID ws, UUID dossierId, Role role, UUID userId) {
        DossierViewEntity d = dossiers.findByWorkspaceIdAndId(ws, dossierId)
                .orElseThrow(() -> new NotFoundException("Dossier inconnu"));
        // Defense en profondeur : le CLIENT est deja exclu au controleur.
        if (role == Role.CLIENT) {
            throw new AccessDeniedException("La fiche client n'est pas accessible au client.");
        }
        if (role == Role.EMPLOYE && (d.getResponsableId() == null || !d.getResponsableId().equals(userId))) {
            throw new AccessDeniedException("Seul l'employé responsable du dossier consulte ses fiches.");
        }
        return d;
    }

    private String cabinet(UUID ws) {
        return workspaces.findById(ws)
                .map(w -> !vide(w.getNomAfficheDocuments()) ? w.getNomAfficheDocuments().trim() : w.getName())
                .orElse("");
    }

    // ------------------------------------------------------------------ mise en forme

    static String capital(BigDecimal c) {
        if (c == null) return null;
        DecimalFormatSymbols s = new DecimalFormatSymbols(Locale.FRANCE);
        s.setGroupingSeparator(' ');
        s.setDecimalSeparator(',');
        return new DecimalFormat("#,##0.00", s).format(c) + " dirhams";
    }

    static String date(Instant i) {
        return i == null ? NON_RENSEIGNEE : LocalDate.ofInstant(i, CASABLANCA).format(JJ_MM_AAAA);
    }

    private static String joindre(String adresse, String ville) {
        if (vide(adresse)) return vide(ville) ? null : ville.trim();
        if (vide(ville) || adresse.toLowerCase(Locale.ROOT).contains(ville.trim().toLowerCase(Locale.ROOT))) {
            return adresse.trim();
        }
        return adresse.trim() + ", " + ville.trim();
    }

    private static boolean vide(String s) {
        return s == null || s.isBlank();
    }

    private static String nomSur(String denomination) {
        if (vide(denomination)) return "dossier";
        String s = denomination.replaceAll("[^a-zA-Z0-9_-]", "_");
        return s.length() > 40 ? s.substring(0, 40) : s;
    }

    private JsonNode parseJson(String json) {
        if (json == null || json.isBlank()) return objectMapper.nullNode();
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            log.warn("Fiche dossier : donnees de workflow illisibles ({})", ex.getMessage());
            return objectMapper.nullNode();
        }
    }
}
