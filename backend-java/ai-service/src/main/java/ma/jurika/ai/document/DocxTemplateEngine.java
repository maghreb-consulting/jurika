package ma.jurika.ai.document;

import ma.jurika.ai.document.corpus.CorpusCharge;
import ma.jurika.ai.document.corpus.DictionnaireUnique;
import ma.jurika.ai.document.format.CasseEnTete;
import ma.jurika.ai.document.format.FrenchContraction;
import ma.jurika.ai.document.manifest.TemplateDefaultsApplier;
import ma.jurika.ai.document.manifest.TemplateManifest;
import ma.jurika.ai.document.manifest.TemplateManifestLoader;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Moteur de generation de documents DOCX a partir de templates avec placeholders.
 *
 * <p>Templates dans {@code src/main/resources/templates/docx/<CODE>.docx}.
 *
 * <p>Resolution intelligente : si la cle demandee est {@code STATUTS_CONSTITUTIFS}
 * et que les variables contiennent {@code formeJuridique=SARL_AU}, le moteur cherche
 * d'abord {@code STATUTS_CONSTITUTIFS_SARL_AU.docx} puis fallback {@code STATUTS_CONSTITUTIFS.docx}.
 *
 * <p><b>Syntaxe placeholders V2</b> (les deux co-existent — backward compatible) :
 * <ul>
 *     <li>{@code ${UPPERCASE}} : nouvelle syntaxe directeur (cle MAJUSCULE_STRICT [A-Z0-9_])</li>
 *     <li>{@code {{denomination}}} : syntaxe legacy (insensible casse/espaces)</li>
 *     <li>{@code {{societe.capital}}} : nested map (legacy)</li>
 *     <li>Runs Word fragmentes : recompose runs adjacents avant remplacement</li>
 * </ul>
 *
 * <p><b>Blocs repetables V2</b> : un paragraphe ou une ligne de tableau marquee par
 * {@code ▶ NOM_BLOC} est cloned N fois (N = taille de la liste {@code variables.get("NOM_BLOC")}).
 * Marqueur {@code ▶} (U+25B6) supprime de chaque clone. Pour le bloc nomme
 * {@code RESOLUTIONS}, la cle {@code RESOLUTION_RANG} est auto-injectee (PREMIERE,
 * DEUXIEME, ...) si absente.
 *
 * <p><b>Limitation L1 acceptee</b> : la recomposition de runs ecrase le formatage
 * mixte sur le 1er run du paragraphe (heritee du moteur V1).
 */
@Component
public class DocxTemplateEngine {

    private static final Logger log = LoggerFactory.getLogger(DocxTemplateEngine.class);

    /**
     * Pattern unifie V2 : groupe 1 = ${...} (uppercase, accepte espaces/ponctuation —
     * normalise en interne), groupe 2 = {{lower}} legacy.
     *
     * <p>Avant 2026-06-09 : groupe 1 etait strict {@code [A-Z][A-Z0-9_]*}. Les modeles
     * JAL fournis par le directeur contiennent des placeholders avec espaces
     * (ex. {@code ${NOM ET PRENOM ASSOCIE}}, {@code ${ADRESSE DE SIEGE SOCIAL}}).
     * On accepte tout sauf {@code }} et on normalise via {@link #normalizeUpperKey(String)}.
     */
    static final Pattern PLACEHOLDER_PATTERN = Pattern.compile(
            "(?:\\$\\{\\s*([^}\\n]*?)\\s*\\}|\\{\\{\\s*([^}]+?)\\s*\\}\\}|\\$([A-Z][A-Z0-9_]{2,}))");

    /**
     * A.1 (2026-08 — modeles directeur) — Variables NUES {@code $NOM_EN_MAJUSCULES}
     * (prefixe {@code $}, MAJUSCULES/underscore, >= 3 caracteres, SANS accolades).
     * C'est le 3e groupe de {@link #PLACEHOLDER_PATTERN} : place APRES {@code ${...}}
     * pour ne jamais capturer un {@code ${...}} deja gere (le {@code $} y est suivi de
     * {@code '{'}, jamais d'une lettre). La recomposition de runs de
     * {@link #replaceInParagraph} gere la fragmentation Word (le {@code $} et le nom
     * peuvent etre dans des runs separes).
     */

    /** Marqueur de debut de bloc repetable : ▶ NOM_BLOC. */
    static final Pattern BLOCK_START_PATTERN = Pattern.compile("\\u25B6\\s*([A-Z][A-Z0-9_]*)\\b");

    /** Marqueur de fin de bloc (optionnel — Option B) : ◀ NOM_BLOC. */
    static final Pattern BLOCK_END_PATTERN = Pattern.compile("\\u25C0\\s*([A-Z][A-Z0-9_]*)\\b");

    /**
     * A.2 (2026-08 — modeles directeur) — Ouverture de boucle au format directeur :
     * {@code ▼ DÉBUT BOUCLE — NOM} (U+25BC). Le nom (ASSOCIES, APPORTS_PAR_ASSOCIE,
     * GERANTS, SIGNATAIRES) est le token MAJUSCULE en fin de marqueur. Tiret cadratin
     * (—, U+2014), demi-cadratin (–, U+2013) ou trait d'union accepte.
     */
    static final Pattern DIR_LOOP_START_PATTERN = Pattern.compile(
            "\\u25BC\\s*D[E\\u00C9]BUT\\s+BOUCLE\\s*[\\u2014\\u2013-]\\s*([A-Z][A-Z0-9_]*)");

    /** A.2 — Fermeture de boucle au format directeur : {@code ▲ FIN BOUCLE — NOM} (U+25B2). */
    static final Pattern DIR_LOOP_END_PATTERN = Pattern.compile(
            "\\u25B2\\s*FIN\\s+BOUCLE\\s*[\\u2014\\u2013-]\\s*([A-Z][A-Z0-9_]*)");

    /**
     * Marqueur d'ouverture de bloc CONDITIONNEL : ◇ NOM_FLAG (U+25C7). Le contenu
     * encadré entre {@code ◇ NOM_FLAG} et {@code ◆ NOM_FLAG} est inclus si la variable
     * {@code NOM_FLAG} (ou son alias lowercase) est <em>truthy</em>, sinon supprimé.
     */
    static final Pattern COND_START_PATTERN = Pattern.compile("\\u25C7\\s*([A-Z][A-Z0-9_]*)\\b");

    /** Marqueur de fermeture de bloc conditionnel : ◆ NOM_FLAG (U+25C6). */
    static final Pattern COND_END_PATTERN = Pattern.compile("\\u25C6\\s*([A-Z][A-Z0-9_]*)\\b");

    /**
     * Pattern de strip GÉNÉRIQUE pour les marqueurs ▶ NOM_BLOC, ◀ NOM_BLOC, ◇/◆ NOM_FLAG :
     * retire le marqueur + le nom + les espaces qui suivent (le nom doit débuter par une
     * lettre MAJUSCULE et peut contenir chiffres + underscore). Conçu pour s'appliquer
     * sur le TEXTE RECOMPOSÉ d'un paragraphe (jamais run-par-run) afin de résister à la
     * fragmentation de runs Word.
     */
    static final Pattern BLOCK_MARKER_STRIP_PATTERN = Pattern.compile(
            "[\\u25B6\\u25C0\\u25C7\\u25C6]\\s*[A-Z][A-Z0-9_]*\\s*");

    /** Valeurs string FALSY pour la résolution des flags conditionnels. */
    private static final java.util.Set<String> FALSY_VALUES = java.util.Set.of(
            "", "non", "no", "false", "0", "off", "null"
    );

    /** Mapping bloc -> cle de rang ordinal auto-injectee si absente. */
    private static final Map<String, String> AUTO_RANG_KEYS = Map.of(
            "RESOLUTIONS", "RESOLUTION_RANG"
    );

    private final OrdinalFrenchFormatter ordinalFormatter = new OrdinalFrenchFormatter();

    /** Loader manifest L3 (optionnel — peut être null en test pur sans Spring). */
    private final TemplateManifestLoader manifestLoader;

    /**
     * Sprint P2 2026-06-21 (Cowork) — Applique des placeholders neutres pour
     * toutes les variables declarees au manifest qui n'ont pas de valeur :
     * "............" pour les variables post-immatriculation (fillLater),
     * "[à compléter]" sinon. Reserve le rouge "VALEUR MANQUANTE" aux variables
     * hors-manifest (= vrai bug template). Optionnel : null en mode test non-Spring.
     */
    private final TemplateDefaultsApplier defaultsApplier;

    /**
     * Lot L2 : corpus date charge en lecture seule, resolu AVANT le classpath
     * (empreinte verifiee a chaque rendu). Null en test pur sans corpus.
     */
    private final CorpusCharge corpus;

    /**
     * Lot L2 : codes servis depuis le classpath faute d'equivalent dans le corpus
     * (signales au rapport de chargement ; sort a decider par le directeur).
     */
    private final java.util.Set<String> codesServisHorsCorpus =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Lot L3 : classement interne / externe des variables (regle des variables). Liste
     * versionnee du classpath ; son controle contre le dictionnaire du corpus est fait
     * au demarrage (CorpusConfiguration#classementVariables).
     */
    private final ClassementVariables classement = ClassementVariables.charger();

    /** Constructeur Spring : manifest L3, defaultsApplier et corpus (lot L2). */
    @Autowired
    public DocxTemplateEngine(
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            TemplateManifestLoader manifestLoader,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            TemplateDefaultsApplier defaultsApplier,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            CorpusCharge corpus) {
        this.manifestLoader = manifestLoader;
        this.defaultsApplier = defaultsApplier;
        this.corpus = corpus;
    }

    /** Sans corpus (tests anterieurs au lot L2). */
    public DocxTemplateEngine(TemplateManifestLoader manifestLoader, TemplateDefaultsApplier defaultsApplier) {
        this(manifestLoader, defaultsApplier, null);
    }

    /** Lot L3 (RG-GEN-05/07) : le gabarit prevoit-il un emplacement de clause libre ? */
    public boolean emplacementClausesPrevu(String templateCode) {
        byte[] gabarit = resolveTemplate(templateCode, Map.of());
        if (gabarit == null) throw new GabaritIntrouvableException(templateCode);
        return ClausesLibres.emplacementPrevu(gabarit);
    }

    /** Lot L3 : dictionnaire unique du corpus charge (libelles des donnees manquantes), ou null. */
    public ma.jurika.ai.document.corpus.DictionnaireUnique dictionnaire() {
        return corpus == null ? null : corpus.dictionnaire();
    }

    /** Lot L3 : nom en clair (libelle du dictionnaire) des donnees externes manquantes du document. */
    public static String enteteDonneesAObtenir(DocumentResult result, ma.jurika.ai.document.corpus.DictionnaireUnique dico) {
        java.util.List<java.util.Map<String, String>> l = new ArrayList<>();
        for (MissingVariableMarker.Manquante m : result.manquantes()) {
            if (m.externe()) {
                String lib = dico == null ? null : dico.libelle(m.nom());
                l.add(java.util.Map.of("variable", m.nom(), "libelle", lib == null ? m.nom() : lib));
            }
        }
        try {
            return java.net.URLEncoder.encode(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(l),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Lot L2 : codes rendus depuis le classpath alors qu'un corpus est charge. */
    public java.util.Set<String> codesServisHorsCorpus() {
        return java.util.Set.copyOf(codesServisHorsCorpus);
    }

    /** Constructeur sans manifest (legacy, réservé aux tests L1 non-Spring). */
    public DocxTemplateEngine() {
        this(null, null);
    }

    /** Variante : avec manifest mais sans defaults (tests V2 historiques). */
    public DocxTemplateEngine(TemplateManifestLoader manifestLoader) {
        this(manifestLoader, null);
    }

    public DocumentResult generate(String templateCode, Map<String, Object> variables) {
        // Sprint P2 2026-06-21 — Injecte les placeholders neutres pour toutes
        // les variables declarees au manifest qui n'ont pas de valeur (le
        // mapper a deja produit son payload). Effet net : aucun rouge sur
        // une variable declaree mais non saisie -- seulement "[à compléter]"
        // ou "............" selon le flag fillLater.
        Map<String, Object> withDefaults = defaultsApplier == null
                ? variables
                : defaultsApplier.withDefaults(templateCode, variables);
        if (corpus != null) {
            withDefaults = avecAlias(withDefaults, corpus.dictionnaire());
        }

        // Lot L2 : plus de document de remplacement. Gabarit introuvable = erreur
        // explicite ; gabarit du corpus modifie ou non rendable = CorpusException.
        byte[] gabarit = resolveTemplate(templateCode, withDefaults);
        if (gabarit == null) {
            throw new GabaritIntrouvableException(templateCode);
        }

        try (InputStream in = new ByteArrayInputStream(gabarit)) {
            RenderOutcome outcome = renderInternal(in, withDefaults,
                    isStatutsTemplate(templateCode), estImprimeAdministratif(templateCode));
            return new DocumentResult(
                    outcome.bytes(),
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    templateCode + ".docx",
                    true,
                    outcome.missingVariables(),
                    outcome.detail());
        } catch (Exception ex) {
            throw new RuntimeException(
                    "Echec generation document " + templateCode + " : " + ex.getMessage(), ex);
        }
    }

    /**
     * Methode utilitaire package-private reservee aux tests : prend les bytes
     * d'un .docx directement (sans passer par classpath), applique le moteur, retourne
     * le DocumentResult. Permet de generer des fixtures via POI a la volee.
     */
    DocumentResult render(byte[] docxBytes, String filename, Map<String, Object> variables) {
        try (InputStream in = new ByteArrayInputStream(docxBytes)) {
            RenderOutcome outcome = renderInternal(in, variables,
                    isStatutsTemplate(filename), estImprimeAdministratif(filename));
            return new DocumentResult(
                    outcome.bytes(),
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    filename,
                    true,
                    outcome.missingVariables(),
                    outcome.detail());
        } catch (Exception ex) {
            throw new RuntimeException("Echec render in-memory : " + ex.getMessage(), ex);
        }
    }

    /**
     * Lot 5 (2026-09-07) — les trois IMPRIMES de l'administration : demande de taxe
     * professionnelle, declaration d'existence, declaration d'immatriculation au
     * registre du commerce.
     *
     * <p>Ce ne sont pas des actes mais des formulaires officiels, faits de cases.
     * Une case laissee blanche y est recevable — c'est deja ce que dit le controle
     * de completude, qui ne bloque que sur un blanc AU MILIEU D'UNE PHRASE. Le rendu
     * doit dire la meme chose : sur ces trois modeles, une case non renseignee sort
     * BLANCHE, au lieu du « ‹ VALEUR MANQUANTE : … › » rouge qui serait imprime sur
     * un document remis a la DGI ou au greffe.
     *
     * <p>Defaut trouve en conditions reelles, sur le document produit et non sur un
     * test : la declaration d'existence est sortie avec
     * « ‹ VALEUR MANQUANTE : ASSOCIE_PRINCIPAL_FAX › » imprime dessus.
     *
     * <p>La liste est explicite plutot que deduite : c'est une decision de rendu, et
     * elle doit se relire. Les huit autres workflows ne sont pas concernes.
     */
    private static final java.util.Set<String> IMPRIMES_ADMINISTRATIFS = java.util.Set.of(
            // Lot 5 (2026-09-07) — les trois imprimes de l'administration fiscale
            // et du greffe.
            "DEMANDE_TAXE_PROFESSIONNELLE",
            "DECLARATION_EXISTENCE",
            "DECLARATION_IMMATRICULATION_RC",
            // Lot B (2026-09-11) — LA LISTE PASSE DE TROIS A ONZE.
            //
            // Le lot A l'avait signalee : figee a trois entrees au lot 5, alors que
            // « le corpus en compte au moins onze ». Le temoin du lot B l'a
            // confirme en lisant les documents produits — la demande d'affiliation
            // CNSS sortait avec sept « ‹ VALEUR MANQUANTE : … › » ROUGES imprimes
            // dessus, sur un formulaire destine a la Caisse.
            //
            // Le critere n'est pas la provenance du modele mais sa NATURE : un
            // formulaire fait de cases, ou une case vide reste recevable, contre un
            // acte ou le blanc se lit au milieu d'une phrase. Restent donc des
            // ACTES, et gardent le rouge : statuts, contrats de bail et de
            // domiciliation, acte de nomination, pouvoir, annonce legale,
            // declaration de souscription, demande de deblocage, note d'annulation,
            // lettre de retrait, rapport du commissaire aux apports.
            //
            // A CONFIRMER PAR LE CABINET — porte au rapport.
            "DEMANDE_AFFILIATION_CNSS",              // formulaire CNSS
            "DECLARATION_BENEFICIAIRES_EFFECTIFS",   // formulaire RBE
            "DECLARATION_CNDP",                      // formulaire CNDP
            "DEMANDE_ADHESION_SIMPL",                // formulaire DGI
            "FICHE_RENSEIGNEMENTS_CREATION",         // fiche interne, faite de rubriques
            "BORDEREAU_REMISE_DOSSIER",              // tableau de pieces
            "ETAT_ACTES_SOCIETE_EN_FORMATION",       // tableau annexe aux statuts
            "NOTE_CONFORMITE_MENTIONS_LEGALES");     // liste de controle

    private boolean estImprimeAdministratif(String codeOrFilename) {
        if (codeOrFilename == null) return false;
        String base = codeOrFilename.toUpperCase(Locale.ROOT).replace(".DOCX", "");
        return IMPRIMES_ADMINISTRATIFS.contains(base);
    }

    /**
     * Heuristique : les codes / noms de fichier commencant par {@code STATUTS_}
     * beneficient de la passe de hierarchie typographique
     * ({@link StatutsHierarchyApplier#apply(XWPFDocument)}). Les autres documents
     * (JAL, PV, actes) ne subissent que la passe universelle de marquage rouge
     * des variables manquantes.
     */
    private boolean isStatutsTemplate(String codeOrFilename) {
        if (codeOrFilename == null) return false;
        String upper = codeOrFilename.toUpperCase(Locale.ROOT);
        return upper.startsWith("STATUTS_");
    }

    private RenderOutcome renderInternal(InputStream in,
                                          Map<String, Object> variables,
                                          boolean applyStatutsHierarchy,
                                          boolean imprimeAdministratif) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(in);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // PRE-PASS V5 (2026-09) : retrait des lignes d'annotation « ↳ ».
            // Doit tourner EN PREMIER : ces lignes sont des reperes de lecture, elles
            // ne participent ni aux conditions ni aux boucles.
            retirerAnnotations(doc);

            // Lot L2 : section de documentation finale « DICTIONNAIRE DES VARIABLES —
            // <CODE> » (92 gabarits du corpus 2026-10-03) : retiree, du titre a la fin
            // du corps. Le gabarit n'est pas modifie (corpus en lecture seule).
            retirerDictionnaireDesVariables(doc);

            // PRE-PASS V5 (2026-09 — formulaires DGI / greffe) : cases a cocher
            // ◈ CASE À COCHER … / ☐ option. Doit tourner AVANT l'evaluateur
            // conditionnel : une option cochee peut se trouver dans une branche
            // conservee, et surtout le marqueur ◈ ne doit jamais survivre.
            List<String> casesNonRenseignees = resolveDirectorCheckboxes(doc, variables);

            // PRE-PASS V4 (2026-08 — modeles directeur) : evaluateur conditionnel
            // ◇ SI / ◇ SINON SI / ◇ SINON / ◆ FIN SI au niveau DOCUMENT (scope global).
            // Doit tourner AVANT l'expansion des boucles directeur : une branche fausse
            // qui contient une boucle ▼…▲ est supprimee sans etre expansee. Les blocs
            // conditionnels situes DANS une boucle sont evalues par item a l'expansion
            // (scope de l'item), pas ici — cf. expandDirectorLoops.
            resolveDirectorConditionalsDocLevel(doc, variables);

            // PRE-PASS V4 : expansion des boucles directeur ▼ DÉBUT BOUCLE … ▲ FIN BOUCLE.
            // Clone le corps par element de liste, resout les conditions par item, puis
            // substitue les variables scopees. Independant du mecanisme legacy ▶/◀.
            expandDirectorLoops(doc, variables);

            // PRE-PASS V3 (2026-06-12) : élagage des blocs conditionnels LEGACY ◇ FLAG / ◆ FLAG
            // AVANT expansion des blocs répétables (un ▶ peut être contenu dans un ◇).
            // No-op sur les modeles directeur (leurs ◇ SI/◆ FIN SI sont deja consommes).
            pruneConditionalBlocks(doc, variables);

            // PRE-PASS V2 : expansion des blocs repetables LEGACY (clonage XML AVANT substitution).
            expandRepeatableBlocks(doc, variables);

            // Pass scalaire : substitution des placeholders sur le document expanded.
            Map<String, String> flat = flatten(variables, "");

            for (XWPFParagraph p : doc.getParagraphs()) {
                replaceInParagraph(p, flat, Collections.emptyMap());
            }

            for (XWPFTable table : doc.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        for (XWPFParagraph p : cell.getParagraphs()) {
                            replaceInParagraph(p, flat, Collections.emptyMap());
                        }
                    }
                }
            }

            for (XWPFHeader header : doc.getHeaderList()) {
                for (XWPFParagraph p : header.getParagraphs()) {
                    replaceInParagraph(p, flat, Collections.emptyMap());
                }
            }
            for (XWPFFooter footer : doc.getFooterList()) {
                for (XWPFParagraph p : footer.getParagraphs()) {
                    replaceInParagraph(p, flat, Collections.emptyMap());
                }
            }

            // POST-PASS 2026-06-19 (PARTIE B) : hierarchie de styles Word pour
            // les Statuts uniquement. Le gabarit decide de l'apparence (police,
            // tailles, gras) via styles JurikaTitreArticle / JurikaSousTitre ;
            // cette passe se contente d'assigner le bon style a chaque paragraphe.
            if (applyStatutsHierarchy) {
                StatutsHierarchyApplier.apply(doc);
            }

            // POST-PASS 2026-06-19 (PARTIE A) : marquage rouge UNIVERSEL des
            // variables non resolues. Doit s'executer APRES la hierarchie : si la
            // passe B scinde un paragraphe, le sentinel doit etre rendu rouge dans
            // le morceau approprie.
            // Sprint Cowork 2026-06-21 (C3) : on transmet le set des variables
            // "fill later" (post-immatriculation) au marker -> rendu "[a completer]"
            // rouge au lieu du "VALEUR MANQUANTE" generique. Les autres variables
            // manquantes restent en rouge "VALEUR MANQUANTE" (vrai bug / oubli).
            // Lot L3 : la liste fill_later est remplacee par le classement des variables :
            // une externe manquante est marquee « A OBTENIR » (jamais bloquante), une
            // interne manquante est bloquante (sauf case d'imprime administratif).
            ma.jurika.ai.document.corpus.DictionnaireUnique dico = corpus == null ? null : corpus.dictionnaire();
            List<MissingVariableMarker.Manquante> detail =
                    MissingVariableMarker.applyDetailed(doc, n -> classement.estExterne(n, dico), imprimeAdministratif);
            // Lot 5 — une case a cocher restee vide est une variable non renseignee,
            // meme si son nom ne figure nulle part dans le texte rendu (le marqueur ◈
            // a ete consomme). Elle n'est jamais bloquante : c'est une case.
            for (String v : casesNonRenseignees) {
                boolean deja = detail.stream().anyMatch(m -> m.nom().equals(v));
                if (!deja) {
                    detail = new ArrayList<>(detail);
                    detail.add(new MissingVariableMarker.Manquante(
                            v, "case a cocher non renseignee", false));
                }
            }
            List<String> missing = new ArrayList<>(detail.size());
            for (MissingVariableMarker.Manquante m : detail) missing.add(m.nom());
            if (!missing.isEmpty()) {
                log.info("Variables manquantes dans le document genere ({}) : {}",
                        missing.size(), missing);
            }

            doc.write(out);
            // Lot A — l'horodatage ZIP fige, sinon deux rendus du meme dossier
            // different sur les octets de date sans qu'une ligne ait bouge.
            return new RenderOutcome(ZipHorodatage.figer(out.toByteArray()), missing, detail);
        }
    }

    /**
     * Resultat interne d'un rendu : bytes du .docx + liste ordonnee / dedupliquee
     * des noms de variables manquantes, et leur detail (endroit + caractere bloquant).
     */
    private record RenderOutcome(byte[] bytes, List<String> missingVariables,
                                  List<MissingVariableMarker.Manquante> detail) {}

    // ============================================================
    // EXPANSION DES BLOCS REPETABLES (▶ NOM_BLOC)
    // ============================================================

    // ============================================================
    // ELAGAGE DES BLOCS CONDITIONNELS (◇ FLAG / ◆ FLAG) — Sprint 2026-06-12
    // ============================================================
    //
    // Convention :
    //  - ◇ NOM_FLAG (U+25C7) ouvre un bloc conditionnel
    //  - ◆ NOM_FLAG (U+25C6) ferme le bloc
    //  - Si la valeur lookupée pour NOM_FLAG est truthy → on garde le contenu et on
    //    supprime les 2 marqueurs (BLOCK_MARKER_STRIP_PATTERN s'en charge plus tard
    //    via stripBlockMarker quand le paragraphe sera substitué)
    //  - Si falsy → on supprime tous les paragraphes [start..end] (ou la row entière)
    //
    // Truthy = String non vide ET valeur normalisée ∉ {"non","no","false","0","off","null"}.
    // Boolean false / null sont falsy. Tout le reste est truthy.

    private void pruneConditionalBlocks(XWPFDocument doc, Map<String, Object> variables) {
        // 1. Lignes de tableau (1 row = 1 condition complète).
        for (XWPFTable table : new ArrayList<>(doc.getTables())) {
            pruneTableRowsByCondition(table, variables);
        }
        // 2. Paragraphes top-level du body : suppression du range [start..end].
        pruneBodyParagraphsByCondition(doc, variables);
        // 3. Headers / footers : limitation V1, on log + on enlève juste les marqueurs.
        for (XWPFHeader header : doc.getHeaderList()) {
            stripCondMarkersInBody(header, variables);
        }
        for (XWPFFooter footer : doc.getFooterList()) {
            stripCondMarkersInBody(footer, variables);
        }
    }

    private void pruneTableRowsByCondition(XWPFTable table, Map<String, Object> variables) {
        int i = 0;
        while (i < table.getNumberOfRows()) {
            XWPFTableRow row = table.getRow(i);
            String rowText = concatRowText(row);
            Matcher mStart = COND_START_PATTERN.matcher(rowText);
            if (!mStart.find()) {
                i++;
                continue;
            }
            String flag = mStart.group(1);
            boolean truthy = isFlagTruthy(flag, variables);
            if (!truthy) {
                table.removeRow(i);
                continue; // ne pas incrémenter — la row suivante a glissé en i
            }
            // truthy : on supprime les marqueurs ◇/◆ de chaque cellule pour ne pas
            // laisser de résidu visible dans le rendu final.
            for (XWPFTableCell cell : row.getTableCells()) {
                for (XWPFParagraph p : cell.getParagraphs()) {
                    stripCondMarkers(p);
                }
            }
            i++;
        }
    }

    private void pruneBodyParagraphsByCondition(XWPFDocument doc, Map<String, Object> variables) {
        boolean modified = true;
        while (modified) {
            modified = false;
            List<XWPFParagraph> paragraphs = new ArrayList<>(doc.getParagraphs());
            for (int i = 0; i < paragraphs.size(); i++) {
                XWPFParagraph p = paragraphs.get(i);
                String text = paragraphText(p);
                Matcher mStart = COND_START_PATTERN.matcher(text);
                if (!mStart.find()) continue;
                String flag = mStart.group(1);

                // Trouver l'index de fin : 1er paragraphe contenant ◆ FLAG.
                int endIdx = i;
                Matcher mSameEnd = COND_END_PATTERN.matcher(text);
                boolean foundOnSame = false;
                while (mSameEnd.find()) {
                    if (mSameEnd.group(1).equals(flag)) {
                        foundOnSame = true;
                        break;
                    }
                }
                if (!foundOnSame) {
                    for (int j = i + 1; j < paragraphs.size(); j++) {
                        String tj = paragraphText(paragraphs.get(j));
                        Matcher me = COND_END_PATTERN.matcher(tj);
                        boolean done = false;
                        while (me.find()) {
                            if (me.group(1).equals(flag)) {
                                endIdx = j;
                                done = true;
                                break;
                            }
                        }
                        if (done) break;
                        // Aucune fin trouvée — on bornera au même paragraphe en fallback.
                        endIdx = j;
                    }
                }

                boolean truthy = isFlagTruthy(flag, variables);
                if (!truthy) {
                    // Supprimer tous les body elements (paragraphes ET tables) entre
                    // les positions du paragraphe i et du paragraphe endIdx (inclus).
                    // Sans inclure les tables entre les 2 bornes, un bloc ◇/◆ qui
                    // englobe un titre + une grille + une clause-tableau ne supprimerait
                    // que les paragraphes — les tables resteraient orphelines.
                    int posStart = doc.getPosOfParagraph(paragraphs.get(i));
                    int posEnd = doc.getPosOfParagraph(paragraphs.get(endIdx));
                    if (posStart >= 0 && posEnd >= posStart) {
                        for (int posK = posEnd; posK >= posStart; posK--) {
                            doc.removeBodyElement(posK);
                        }
                    } else {
                        // Fallback : ne retirer que les paragraphes (ancien comportement).
                        for (int k = endIdx; k >= i; k--) {
                            int posK = doc.getPosOfParagraph(paragraphs.get(k));
                            if (posK >= 0) doc.removeBodyElement(posK);
                        }
                    }
                } else {
                    // Garder, mais retirer les marqueurs ◇/◆ pour ne pas polluer le rendu.
                    for (int k = i; k <= endIdx; k++) {
                        stripCondMarkers(paragraphs.get(k));
                    }
                }
                modified = true;
                break; // re-iterate sur snapshot frais
            }
        }
    }

    private void stripCondMarkersInBody(IBody body, Map<String, Object> variables) {
        for (XWPFParagraph p : new ArrayList<>(body.getParagraphs())) {
            String text = paragraphText(p);
            if (COND_START_PATTERN.matcher(text).find()
                    || COND_END_PATTERN.matcher(text).find()) {
                log.warn("Bloc conditionnel ◇/◆ dans header/footer : non supporté, marqueurs retirés");
                stripCondMarkers(p);
            }
        }
    }

    /**
     * Retire UNIQUEMENT les marqueurs ◇/◆ d'un paragraphe. Utilise BLOCK_MARKER_STRIP_PATTERN
     * (qui couvre aussi ▶/◀) — mais comme les ▶ ne devraient pas apparaître ici (ils
     * sont déjà strippés par stripBlockMarker au moment de l'expansion), c'est sans effet
     * pervers.
     */
    private void stripCondMarkers(XWPFParagraph p) {
        rewriteParagraphText(p, txt -> {
            String s = txt;
            s = COND_START_PATTERN.matcher(s).replaceAll("");
            s = COND_END_PATTERN.matcher(s).replaceAll("");
            // Compacter UNIQUEMENT les espaces multiples créés par la suppression
            // (préserve l'espace fin français avant : ; ! ? imposé par la typographie).
            s = s.replaceAll(" {2,}", " ").trim();
            return s;
        });
    }

    /** Résolution truthy/falsy d'un flag par lookup case-aware sur les variables. */
    private boolean isFlagTruthy(String flag, Map<String, Object> variables) {
        Object raw = variables.get(flag);
        if (raw == null) raw = variables.get(flag.toLowerCase(Locale.ROOT));
        if (raw == null) {
            // Recherche case-insensitive en 1er niveau.
            for (Map.Entry<String, Object> e : variables.entrySet()) {
                if (e.getKey().equalsIgnoreCase(flag)) {
                    raw = e.getValue();
                    break;
                }
            }
        }
        if (raw == null) return false;
        if (raw instanceof Boolean b) return b;
        if (raw instanceof Number n) return n.doubleValue() != 0d;
        if (raw instanceof List<?> l) return !l.isEmpty();
        String s = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
        return !FALSY_VALUES.contains(s);
    }

    private void expandRepeatableBlocks(XWPFDocument doc, Map<String, Object> variables) {
        // 1. Tables : cloner les rows porteuses.
        for (XWPFTable table : new ArrayList<>(doc.getTables())) {
            expandTableRows(table, variables);
        }

        // 2. Paragraphes top-level du body.
        expandBodyParagraphs(doc, variables);

        // 3. Headers / footers.
        for (XWPFHeader header : doc.getHeaderList()) {
            expandHeaderFooterParagraphs(header, variables);
        }
        for (XWPFFooter footer : doc.getFooterList()) {
            expandHeaderFooterParagraphs(footer, variables);
        }
    }

    private void expandTableRows(XWPFTable table, Map<String, Object> variables) {
        // On itere par index car la liste sera mutee.
        int i = 0;
        while (i < table.getNumberOfRows()) {
            XWPFTableRow row = table.getRow(i);
            String rowText = concatRowText(row);
            Matcher m = BLOCK_START_PATTERN.matcher(rowText);
            if (!m.find()) {
                i++;
                continue;
            }
            String blockName = m.group(1);
            List<Map<String, Object>> items = resolveListVariable(variables, blockName);

            if (items == null) {
                // Cle inconnue : on laisse le marqueur intact, log WARN, on avance.
                log.warn("Bloc ▶ {} : aucune liste correspondante dans les variables, marqueur laisse intact", blockName);
                i++;
                continue;
            }

            CTRow templateCtRow = (CTRow) row.getCtRow().copy();

            if (items.isEmpty()) {
                table.removeRow(i);
                // Pas d'incrementation : la row suivante a glisse en i.
                continue;
            }

            // Cloner items.size() fois et inserer en position i, i+1, i+2 ...
            // Strategie : on construit les nouvelles rows, on les insere, puis on supprime l'originale.
            List<XWPFTableRow> newRows = new ArrayList<>();
            for (int idx = 0; idx < items.size(); idx++) {
                CTRow clonedCt = (CTRow) templateCtRow.copy();
                XWPFTableRow clonedRow = new XWPFTableRow(clonedCt, table);
                Map<String, String> scoped = buildScopedItemVars(
                        items.get(idx), idx, blockName);
                // ORDRE FIX L7b : 1) substituer ${VAR} sur le clone, 2) strip ▶ NOM
                // sur le texte recomposé. Strip après substitution évite qu'un ${VAR}
                // précédé du marqueur passe inaperçu.
                for (XWPFTableCell cell : clonedRow.getTableCells()) {
                    for (XWPFParagraph p : cell.getParagraphs()) {
                        replaceInParagraph(p, flatten(variables, ""), scoped);
                    }
                }
                stripBlockMarker(clonedRow, blockName);
                newRows.add(clonedRow);
            }

            // Insertion brute via CTRow array : on remplace en supprimant l'originale
            // puis en re-inserant les clones a la meme position.
            table.removeRow(i);
            for (int k = 0; k < newRows.size(); k++) {
                // table.addRow(row, pos) insere a la position pos.
                table.addRow(newRows.get(k), i + k);
            }

            i += items.size();
        }
    }

    private void expandBodyParagraphs(XWPFDocument doc, Map<String, Object> variables) {
        boolean modified = true;
        while (modified) {
            modified = false;
            List<XWPFParagraph> paragraphs = new ArrayList<>(doc.getParagraphs());
            for (int i = 0; i < paragraphs.size(); i++) {
                XWPFParagraph p = paragraphs.get(i);
                String text = paragraphText(p);
                Matcher m = BLOCK_START_PATTERN.matcher(text);
                if (!m.find()) continue;

                String blockName = m.group(1);
                List<Map<String, Object>> items = resolveListVariable(variables, blockName);
                if (items == null) {
                    // Cle inconnue : on laisse passer (marqueur preserve).
                    continue;
                }

                // Detection range Option A : paragraphes successifs identiques.
                int endIdx = i;
                while (endIdx + 1 < paragraphs.size()) {
                    String nextText = paragraphText(paragraphs.get(endIdx + 1));
                    Matcher mn = BLOCK_START_PATTERN.matcher(nextText);
                    if (mn.find() && mn.group(1).equals(blockName)) {
                        endIdx++;
                    } else {
                        break;
                    }
                }

                // Snapshot XML des paragraphes a cloner.
                List<CTP> unitTemplate = new ArrayList<>();
                for (int k = i; k <= endIdx; k++) {
                    unitTemplate.add((CTP) paragraphs.get(k).getCTP().copy());
                }

                // Trouver la position de body element du 1er paragraphe porteur.
                int firstPos = doc.getPosOfParagraph(paragraphs.get(i));

                // Supprimer les paragraphes porteurs (du dernier au premier pour ne pas decaler les indices).
                for (int k = endIdx; k >= i; k--) {
                    int posK = doc.getPosOfParagraph(paragraphs.get(k));
                    if (posK >= 0) doc.removeBodyElement(posK);
                }

                if (items.isEmpty()) {
                    modified = true;
                    break; // re-iterate
                }

                // Inserer les clones a partir de firstPos. On insere les paragraphes
                // dans l'ordre INVERSE pour pouvoir utiliser createParagraphAt(firstPos)
                // OU on utilise un curseur sur le body element a la position firstPos.
                // Approche : utiliser le body element actuellement a firstPos comme ancre,
                // ou si on a depasse la fin, append au document.
                int totalInserted = 0;
                Map<String, String> globalFlat = flatten(variables, "");

                for (int idx = 0; idx < items.size(); idx++) {
                    Map<String, String> scoped = buildScopedItemVars(items.get(idx), idx, blockName);
                    for (CTP ctpTemplate : unitTemplate) {
                        CTP clonedCt = (CTP) ctpTemplate.copy();
                        XWPFParagraph newPara = insertParagraphAtPosition(doc, firstPos + totalInserted, clonedCt);
                        // ORDRE FIX L7b : substituer AVANT de stripper le marqueur ▶ NOM
                        // (sur texte recomposé) pour ne pas laisser de résidu fragmenté.
                        replaceInParagraph(newPara, globalFlat, scoped);
                        stripBlockMarker(newPara, blockName);
                        totalInserted++;
                    }
                }

                modified = true;
                break; // re-iterate with fresh snapshot
            }
        }
    }

    /**
     * Insere un nouveau paragraphe a la position donnee dans le body. Si pos depasse
     * la taille actuelle, le paragraphe est ajoute en fin de document. Le CTP fourni
     * remplace le CTP du paragraphe cree, puis le paragraphe est re-fetched pour
     * disposer des runs frais.
     */
    private XWPFParagraph insertParagraphAtPosition(XWPFDocument doc, int pos, CTP ctpTemplate) {
        XWPFParagraph created;
        List<IBodyElement> bodyElements = doc.getBodyElements();
        if (pos >= bodyElements.size()) {
            // Append a la fin.
            created = doc.createParagraph();
        } else {
            IBodyElement anchor = bodyElements.get(pos);
            org.apache.xmlbeans.XmlCursor cursor;
            if (anchor instanceof XWPFParagraph ap) {
                cursor = ap.getCTP().newCursor();
            } else if (anchor instanceof XWPFTable at) {
                cursor = at.getCTTbl().newCursor();
            } else {
                // Fallback : append.
                created = doc.createParagraph();
                created.getCTP().set(ctpTemplate);
                // Re-fetch a fresh XWPFParagraph pour avoir les bons runs.
                return refetchParagraph(doc, created);
            }
            created = doc.insertNewParagraph(cursor);
            cursor.dispose();
        }
        // Remplacer le CTP par notre template clone.
        created.getCTP().set(ctpTemplate);
        // POI cache les runs dans XWPFParagraph ; apres `set()`, le cache est obsolete.
        // On reconstruit un XWPFParagraph autour du nouveau CTP via la position du body.
        return refetchParagraph(doc, created);
    }

    /**
     * Apres modification du CTP via XmlBeans, on re-instancie un XWPFParagraph
     * pour rafraichir la liste des runs. Recherche par identite du CTP dans le body.
     */
    private XWPFParagraph refetchParagraph(XWPFDocument doc, XWPFParagraph stale) {
        CTP target = stale.getCTP();
        for (XWPFParagraph p : doc.getParagraphs()) {
            if (p.getCTP() == target) {
                // Construire un nouveau XWPFParagraph qui parsera fraichement les runs.
                return new XWPFParagraph(target, p.getBody());
            }
        }
        return stale;
    }

    private void expandHeaderFooterParagraphs(IBody body, Map<String, Object> variables) {
        // Implementation simplifiee : on cherche les paragraphes porteurs mais
        // on n'execute pas le clonage par IBody (limitation V1 — headers/footers
        // sont rarement utilises pour des blocs repetables).
        for (XWPFParagraph p : new ArrayList<>(body.getParagraphs())) {
            String text = paragraphText(p);
            Matcher m = BLOCK_START_PATTERN.matcher(text);
            if (m.find()) {
                log.warn("Bloc ▶ {} dans header/footer : non supporte en V1, marqueur laisse intact",
                        m.group(1));
            }
        }
    }

    private void removeParagraphFromBody(XWPFDocument doc, XWPFParagraph p) {
        int pos = doc.getPosOfParagraph(p);
        if (pos >= 0) {
            doc.removeBodyElement(pos);
        }
    }

    // ============================================================
    // FORMAT DIRECTEUR (2026-08) — Partie A
    //   Conditions : ◇ SI / ◇ SINON SI / ◇ SINON / ◆ FIN SI (evaluateur a valeur)
    //   Boucles    : ▼ DÉBUT BOUCLE — NOM … ▲ FIN BOUCLE — NOM (delimiteurs debut/fin)
    //
    // Ordre : conditions doc-level (scope global) AVANT expansion des boucles ; les
    // conditions situees DANS une boucle sont evaluees PAR ITEM a l'expansion (scope
    // item + global). Retrocompatibilite : les marqueurs LEGACY ◇FLAG/◆FLAG et ▶/◀
    // sont ignores par cet evaluateur (classes CONTENT) et traites par les passes V2/V3.
    // ============================================================

    /** Type de marqueur conditionnel directeur porte par un paragraphe. */
    private enum CondKind { CONTENT, IF, ELSEIF, ELSE, ENDIF }

    /** Classification d'un paragraphe : type de marqueur + expression de condition. */
    private record CondLine(CondKind kind, String expr) {}

    /** Cadre de pile pour l'evaluation if/elseif/else (gere l'imbrication). */
    private static final class CondFrame {
        /** Contexte englobant actif au moment du ◇ SI (fige a la creation). */
        final boolean parentActive;
        /** Une branche du bloc a-t-elle deja ete retenue ? */
        boolean anyMatched;
        /** La branche courante est-elle retenue (contenu conserve) ? */
        boolean currentActive;
        CondFrame(boolean parentActive, boolean firstBranchActive) {
            this.parentActive = parentActive;
            this.anyMatched = firstBranchActive;
            this.currentActive = firstBranchActive;
        }
    }

    /** Predicat d'existence : {@code $VAR existe}. */
    private static final Pattern ATOM_EXISTS = Pattern.compile(
            "\\$([A-Z][A-Z0-9_]*)\\s+existe", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Egalite : {@code $VAR = « valeur »} (guillemets optionnels). */
    private static final Pattern ATOM_EQUALS = Pattern.compile(
            "\\$([A-Z][A-Z0-9_]*)\\s*=\\s*(.+)", Pattern.DOTALL);

    /**
     * Classe le texte recompose d'un paragraphe. Ne reconnait le format directeur que
     * si le token apres ◇ est SI/SINON ou apres ◆ est « FIN SI » ; sinon CONTENT
     * (les ◇FLAG/◆FLAG legacy tombent donc en CONTENT et restent pour la passe V3).
     */
    private CondLine classifyCond(String rawText) {
        String t = rawText == null ? "" : rawText.trim();
        if (t.isEmpty()) return new CondLine(CondKind.CONTENT, null);
        char c0 = t.charAt(0);
        if (c0 == '◆') { // ◆ (U+25C6)
            String rem = t.substring(1).trim();
            if (rem.matches("(?siu)FIN\\s+SI\\b.*")) return new CondLine(CondKind.ENDIF, null);
            return new CondLine(CondKind.CONTENT, null); // ◆FLAG legacy
        }
        if (c0 == '◇') { // ◇ (U+25C7)
            String rem = t.substring(1).trim();
            if (rem.matches("(?siu)SINON\\s+SI\\b.*")) return new CondLine(CondKind.ELSEIF, exprAfterColon(rem));
            if (rem.matches("(?siu)SINON\\b.*"))       return new CondLine(CondKind.ELSE, null);
            if (rem.matches("(?siu)SI\\s*:.*"))         return new CondLine(CondKind.IF, exprAfterColon(rem));
            return new CondLine(CondKind.CONTENT, null); // ◇FLAG legacy
        }
        return new CondLine(CondKind.CONTENT, null);
    }

    private String exprAfterColon(String rem) {
        int idx = rem.indexOf(':');
        return idx >= 0 ? rem.substring(idx + 1).trim() : "";
    }

    /** Evalue une condition directeur (atomique ou composee ET/OU) dans un scope donne. */
    private boolean evalCondition(String expr, Map<String, Object> scope) {
        if (expr == null || expr.isBlank()) return false;
        String e = expr.trim();
        List<String> ou = decouperHorsLitteral(e, "OU");
        if (ou.size() > 1) {
            for (String part : ou) {
                if (evalAtomic(part.trim(), scope)) return true;
            }
            return false;
        }
        boolean all = true;
        for (String part : decouperHorsLitteral(e, "ET")) {
            all &= evalAtomic(part.trim(), scope);
        }
        return all;
    }

    /**
     * Decoupe une condition sur le mot-cle ET / OU, MAIS JAMAIS A L'INTERIEUR
     * D'UN LITTERAL {@code « … »}.
     *
     * <p>Lot A (2026-09-10), defaut trouve sur le document PRODUIT. Le corpus
     * creation du 9 septembre porte, dans la demande d'affiliation CNSS :
     *
     * <pre>    ◇ SI : $CNSS_MODE_DECLARATION = « Teledeclaration et telepaiement (DAMANCOM) »</pre>
     *
     * Le decoupage precedent, un simple {@code split("\\s+ET\\s+")}, coupait la
     * condition sur le « et » DU LIBELLE. La premiere moitie comparait
     * « Teledeclaration » a la valeur entiere : fausse. La seconde,
     * « telepaiement (DAMANCOM) » », n'etait reconnue par aucun patron et
     * partait en {@code log.warn} — donc a false, elle aussi. La ligne
     * « Personne habilitee pour la teledeclaration » disparaissait du formulaire
     * alors que le dossier portait exactement la valeur attendue : une demande
     * d'affiliation deposee a la CNSS sans son correspondant DAMANCOM.
     *
     * <p>Le defaut etait dormant tant qu'aucun libelle ne contenait « et » ou
     * « ou ». Il ne l'est plus, et il pouvait frapper les neuf autres workflows
     * a la premiere valeur mal choisie.
     */
    static List<String> decouperHorsLitteral(String expr, String motCle) {
        List<String> parts = new ArrayList<>();
        if (expr == null) return parts;
        int profondeur = 0;
        int debut = 0;
        int i = 0;
        while (i < expr.length()) {
            char c = expr.charAt(i);
            if (c == '«') { profondeur++; i++; continue; }
            if (c == '»') { if (profondeur > 0) profondeur--; i++; continue; }
            if (profondeur == 0 && Character.isWhitespace(c)) {
                int j = i;
                while (j < expr.length() && Character.isWhitespace(expr.charAt(j))) j++;
                int fin = j + motCle.length();
                boolean motSuivi = fin < expr.length() && Character.isWhitespace(expr.charAt(fin));
                if (motSuivi && expr.regionMatches(true, j, motCle, 0, motCle.length())) {
                    parts.add(expr.substring(debut, i));
                    while (fin < expr.length() && Character.isWhitespace(expr.charAt(fin))) fin++;
                    debut = fin;
                    i = fin;
                    continue;
                }
                i = j;
                continue;
            }
            i++;
        }
        parts.add(expr.substring(debut));
        return parts;
    }

    /** Evalue une condition atomique : {@code $VAR existe} ou {@code $VAR = « valeur »}. */
    private boolean evalAtomic(String atom, Map<String, Object> scope) {
        if (atom == null) return false;
        String a = atom.trim();
        Matcher ex = ATOM_EXISTS.matcher(a);
        if (ex.matches()) {
            return isFlagTruthy(ex.group(1), scope);
        }
        Matcher eq = ATOM_EQUALS.matcher(a);
        if (eq.matches()) {
            String var = eq.group(1);
            String expected = stripGuillemets(eq.group(2));
            String actual = scopeString(scope, var);
            return normalizeCompare(actual).equals(normalizeCompare(expected));
        }
        // Conditions en langage naturel des modèles directeur (texte INTOUCHABLE) :
        // on les évalue contre un drapeau dérivé produit en amont par le builder.
        String nlFlag = NL_CONDITION_FLAGS.get(normalizeCompare(a));
        if (nlFlag != null) {
            return isFlagTruthy(nlFlag, scope);
        }
        log.warn("Condition directeur non reconnue, evaluee a false : {}", a);
        return false;
    }

    /**
     * Conditions rédigées en langage naturel dans les modèles du directeur (dont le
     * contenu est intouchable) → drapeau dérivé fourni par le builder. Clé = condition
     * normalisée (sans accents, minuscules, espaces compactés).
     */
    private static final Map<String, String> NL_CONDITION_FLAGS = Map.of(
            "au moins un apport est realise en nature", "HAS_APPORT_NATURE",
            // Lot 5 (2026-09-07) — formulaire DECLARATION_IMMATRICULATION_RC. Les cinq
            // conditions du modele 2 sont redigees en francais et non en $VAR = « … » ;
            // le fichier du directeur reste INTOUCHE, la resolution se fait ici.
            "le siege etait precedemment exploite par un tiers", "HAS_SIEGE_PRECEDENT",
            "la societe comporte des succursales", "HAS_SUCCURSALES",
            "capital variable", "HAS_CAPITAL_VARIABLE",
            "brevets ou marques deposes", "HAS_BREVETS_MARQUES",
            "un dirigeant est une personne morale", "HAS_DIRIGEANT_PM");

    /** Retire guillemets francais « », droits, apostrophes et espaces (fins inclus). */
    private String stripGuillemets(String v) {
        if (v == null) return "";
        String s = v.trim();
        s = s.replaceAll("^[\\u00AB\"'\\s\\u00A0\\u202F]+", "");
        s = s.replaceAll("[\\u00BB\"'\\s\\u00A0\\u202F]+$", "");
        return s.trim();
    }

    /**
     * Normalisation de comparaison : sans accents, minuscules, espaces compactes —
     * ET APOSTROPHES REPLIEES.
     *
     * <p>Lot 5 (2026-09-07), defaut trouve sur le document PRODUIT. Les modeles du
     * directeur portent l'apostrophe typographique (U+2019) la ou les mappers
     * produisent l'apostrophe droite. La demande de taxe professionnelle porte la
     * condition
     *
     * <pre>    ◇ SI : $TP_OBJET = « Creation d’une personne morale »</pre>
     *
     * qui comparait « d’une » a « d'une » : FAUSSE. Le formulaire sortait sans le
     * moindre marqueur residuel, toutes ses valeurs en place, la bonne case cochee —
     * et la MAUVAISE liste de pieces a joindre, celle de la branche SINON. Un
     * document recevable a l'oeil, faux devant la DGI.
     *
     * <p>Le repliement vit ici, dans la normalisation PARTAGEE, et non au seul
     * endroit ou le defaut s'est manifeste : conditions, cases a cocher et
     * conditions en langage naturel comparent desormais de la meme facon.
     */
    private String normalizeCompare(String s) {
        if (s == null) return "";
        String n = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD);
        n = n.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        n = n.replaceAll("[\\u2018\\u2019\\u201B\\u02BC\\u0060\\u00B4]", "'");
        return n.toLowerCase(Locale.ROOT).replaceAll("[\\s\\u00A0\\u202F]+", " ").trim();
    }

    /** Lookup case-insensitive d'une cle dans un scope (comme {@link #isFlagTruthy}). */
    private Object scopeLookup(Map<String, Object> scope, String key) {
        Object raw = scope.get(key);
        if (raw == null) raw = scope.get(key.toLowerCase(Locale.ROOT));
        if (raw == null) {
            for (Map.Entry<String, Object> e : scope.entrySet()) {
                if (e.getKey().equalsIgnoreCase(key)) { raw = e.getValue(); break; }
            }
        }
        return raw;
    }

    private String scopeString(Map<String, Object> scope, String key) {
        Object raw = scopeLookup(scope, key);
        return raw == null ? "" : String.valueOf(raw);
    }

    /** Vrai si tous les cadres de la pile ont une branche courante active. */
    private boolean allActive(java.util.Deque<CondFrame> stack) {
        for (CondFrame f : stack) if (!f.currentActive) return false;
        return true;
    }

    /**
     * Selection if/elseif/else sur une liste de textes (une seule branche retenue).
     * Retourne les index des paragraphes de CONTENU a conserver (les marqueurs sont
     * exclus). Utilise pour le corps clone d'une boucle (scope par item).
     */
    private java.util.List<Integer> selectConditionalSurvivors(java.util.List<String> texts,
                                                               Map<String, Object> scope) {
        java.util.List<Integer> survivors = new ArrayList<>();
        java.util.Deque<CondFrame> stack = new java.util.ArrayDeque<>();
        for (int i = 0; i < texts.size(); i++) {
            CondLine cl = classifyCond(texts.get(i));
            boolean parentActive = allActive(stack);
            switch (cl.kind()) {
                case IF -> stack.push(new CondFrame(parentActive,
                        parentActive && evalCondition(cl.expr(), scope)));
                case ELSEIF -> {
                    CondFrame f = stack.peek();
                    if (f != null) {
                        boolean t = f.parentActive && !f.anyMatched && evalCondition(cl.expr(), scope);
                        f.currentActive = t;
                        if (t) f.anyMatched = true;
                    }
                }
                case ELSE -> {
                    CondFrame f = stack.peek();
                    if (f != null) {
                        boolean t = f.parentActive && !f.anyMatched;
                        f.currentActive = t;
                        if (t) f.anyMatched = true;
                    }
                }
                case ENDIF -> { if (!stack.isEmpty()) stack.pop(); }
                case CONTENT -> { if (parentActive) survivors.add(i); }
            }
        }
        return survivors;
    }

    /**
     * Passe conditionnelle directeur au niveau DOCUMENT (scope global). Traite chaque
     * region de boucle ▼…▲ comme une unite opaque (conservee si active — expansion
     * ulterieure —, supprimee entierement sinon) et n'evalue donc PAS les conditions
     * internes aux boucles (elles seront evaluees par item a l'expansion).
     */
    private void resolveDirectorConditionalsDocLevel(XWPFDocument doc, Map<String, Object> scope) {
        java.util.List<XWPFParagraph> paras = new ArrayList<>(doc.getParagraphs());
        java.util.Map<Integer, Integer> loopRegions = computeTopLevelLoopRegions(paras);
        java.util.Set<XWPFParagraph> toRemove = new java.util.LinkedHashSet<>();
        java.util.Deque<CondFrame> stack = new java.util.ArrayDeque<>();
        int idx = 0;
        while (idx < paras.size()) {
            Integer end = loopRegions.get(idx);
            if (end != null) {
                if (!allActive(stack)) {
                    for (int k = idx; k <= end && k < paras.size(); k++) toRemove.add(paras.get(k));
                }
                idx = end + 1;
                continue;
            }
            XWPFParagraph p = paras.get(idx);
            CondLine cl = classifyCond(paragraphText(p));
            boolean parentActive = allActive(stack);
            switch (cl.kind()) {
                case IF -> {
                    stack.push(new CondFrame(parentActive, parentActive && evalCondition(cl.expr(), scope)));
                    toRemove.add(p);
                }
                case ELSEIF -> {
                    CondFrame f = stack.peek();
                    if (f != null) {
                        boolean t = f.parentActive && !f.anyMatched && evalCondition(cl.expr(), scope);
                        f.currentActive = t;
                        if (t) f.anyMatched = true;
                    }
                    toRemove.add(p);
                }
                case ELSE -> {
                    CondFrame f = stack.peek();
                    if (f != null) {
                        boolean t = f.parentActive && !f.anyMatched;
                        f.currentActive = t;
                        if (t) f.anyMatched = true;
                    }
                    toRemove.add(p);
                }
                case ENDIF -> { if (!stack.isEmpty()) stack.pop(); toRemove.add(p); }
                case CONTENT -> { if (!parentActive) toRemove.add(p); }
            }
            idx++;
        }
        removeParagraphsByPosition(doc, toRemove);
    }

    // ============================================================
    // FORMAT DIRECTEUR (2026-09) — LIGNES D'ANNOTATION « ↳ »
    // ============================================================
    //
    // Le directeur documente lui-meme la convention, en tete de chacun des trois
    // formulaires :
    //
    //   « ↳ Les bandeaux bleus fonces sont les intitules de sections du formulaire
    //     officiel. Les bandeaux bleu clair / oranges sont des reperes de
    //     generation, NON IMPRIMES DANS LE DOCUMENT FINAL. »
    //
    // Et cette phrase est elle-meme une ligne « ↳ ». Le caractere U+21B3 est donc
    // un marqueur de balisage au meme titre que ▼▲, ◇◆ et ◈ : ce qu'il ouvre est
    // une note de lecture destinee au cabinet, pas au greffe ni a la DGI.
    //
    // La convention vit ICI, dans le moteur — le fichier du directeur reste intact.
    // C'est la meme regle que pour les conditions en langage naturel : on ne
    // reecrit pas le modele, on apprend a le lire.
    //
    // ⚠ UN CAS EN ATTENTE D'ARBITRAGE. Le « ↳ NOTA — La presente declaration doit
    // etre redigee en triple exemplaire… » du modele 2 porte l'article 64 du Code
    // de commerce : il pourrait appartenir au formulaire OFFICIEL du greffe plutot
    // qu'aux notes de preparation. La question est posee au cabinet
    // (`rapport-cabinet.md` § 11) ; en attendant, la regle s'applique
    // uniformement — un traitement au cas par cas serait une decision prise a la
    // place du cabinet.

    /** Ligne d'annotation du directeur : commence par « ↳ » (U+21B3). */
    static final Pattern ANNOTATION_PATTERN = Pattern.compile("^\\s*\\u21B3");

    /**
     * Retire les paragraphes d'annotation. Retourne leur nombre (journalise : ces
     * lignes disparaissent du rendu, l'employe doit pouvoir le constater).
     */
    private void retirerDictionnaireDesVariables(XWPFDocument doc) {
        List<IBodyElement> elements = doc.getBodyElements();
        int debut = -1;
        for (int i = 0; i < elements.size(); i++) {
            if (elements.get(i) instanceof XWPFParagraph p
                    && ma.jurika.ai.document.corpus.ControlesIntegration.DICTIONNAIRE_DES_VARIABLES
                    .matcher(p.getText()).find()) {
                debut = i;
                break;
            }
        }
        if (debut < 0) return;
        for (int i = doc.getBodyElements().size() - 1; i >= debut; i--) {
            doc.removeBodyElement(i);
        }
        log.debug("Section DICTIONNAIRE DES VARIABLES retiree du rendu ({} elements)", elements.size() - debut);
    }

    private int retirerAnnotations(XWPFDocument doc) {
        java.util.Set<XWPFParagraph> toRemove = new java.util.LinkedHashSet<>();
        for (XWPFParagraph p : new ArrayList<>(doc.getParagraphs())) {
            if (ANNOTATION_PATTERN.matcher(paragraphText(p)).find()) toRemove.add(p);
        }
        // Tableaux : une annotation peut vivre dans une cellule de formulaire.
        for (XWPFTable table : doc.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    for (XWPFParagraph p : new ArrayList<>(cell.getParagraphs())) {
                        if (!ANNOTATION_PATTERN.matcher(paragraphText(p)).find()) continue;
                        // On VIDE le paragraphe au lieu de le supprimer : retirer une
                        // ligne d'une cellule deplacerait la mise en page du tableau.
                        for (int i = p.getRuns().size() - 1; i >= 0; i--) p.removeRun(i);
                    }
                }
            }
        }
        removeParagraphsByPosition(doc, toRemove);
        if (!toRemove.isEmpty()) {
            log.debug("Lignes d'annotation « ↳ » retirees du rendu : {}", toRemove.size());
        }
        return toRemove.size();
    }

    // ============================================================
    // FORMAT DIRECTEUR (2026-09) — CASES A COCHER
    //   ◈ CASE À COCHER (choix unique) pilotée par $VAR
    //   ☐  Libelle A
    //   ☐  Libelle B
    // ============================================================
    //
    // Les trois formulaires DGI / greffe du lot 5 sont des CASES, pas de la prose :
    // l'administration attend une croix dans l'une des cases pre-imprimees, jamais
    // une valeur recopiee. Sans cette passe, la ligne « ◈ CASE À COCHER … » et ses
    // options s'imprimaient telles quelles — un marqueur de moteur sur un document
    // remis a la DGI.
    //
    // La passe coche l'option dont le libelle egale (comparaison sans accents ni
    // casse) la valeur de la variable pilote, puis supprime la ligne de marqueur.
    // Aucune option ne correspond (variable vide, ou valeur hors liste) : toutes les
    // cases restent vides et la variable est REMONTEE comme non renseignee — non
    // bloquante, puisqu'une case administrative vide reste un formulaire recevable
    // (cf. classification obligatoire / optionnel du lot 5).

    /** Ligne de marqueur : {@code ◈ CASE À COCHER (choix unique) pilotée par $VAR} (U+25C8). */
    /**
     * Lot B — le seul PREFIXE du marqueur, sans son nom de variable. Sert au
     * balayage d'apres expansion, ou la variable a deja ete substituee.
     */
    static final Pattern CHECKBOX_PREFIX_PATTERN = Pattern.compile(
            "\\u25C8\\s*CASE\\s+[A\\u00C0]\\s+COCHER",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    static final Pattern CHECKBOX_MARKER_PATTERN = Pattern.compile(
            "\\u25C8\\s*CASE\\s+[A\\u00C0]\\s+COCHER.*?\\$([A-Z][A-Z0-9_]*)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);

    /** Case vide (U+2610) ouvrant une ligne d'option. */
    private static final char CHECKBOX_EMPTY = '☐';

    /** Case cochee (U+2612) substituee a l'option retenue. */
    private static final char CHECKBOX_TICKED = '☒';

    /**
     * Coche les options des blocs {@code ◈ CASE À COCHER} et retire les lignes de
     * marqueur.
     *
     * @return noms des variables pilotes dont AUCUNE option n'a pu etre cochee
     *         (valeur absente ou hors liste), dans l'ordre du document.
     */
    private List<String> resolveDirectorCheckboxes(XWPFDocument doc, Map<String, Object> scope) {
        return resolveDirectorCheckboxes(doc, scope, horsBoucles(doc.getParagraphs()))
                .nonRenseignees();
    }

    /**
     * Lot B (2026-09-11) — LA PASSE DOCUMENT NE TOUCHE PAS AUX CASES D'UNE BOUCLE.
     *
     * <p>Elle tourne AVANT l'expansion des boucles — elle le doit, un bloc
     * conditionnel peut contenir une boucle. Mais une case dont la valeur est une
     * variable d'item ({@code $BE_GENRE}) n'a, à ce moment-là, aucun scope où se
     * lire : la passe ne cochait rien et <b>consommait le marqueur</b>. La case
     * sortait alors inerte — « ☐ Masculin ☐ Féminin » sur un bénéficiaire dont le
     * genre était renseigné — sans marqueur résiduel, donc sans alarme.
     *
     * <p>Défaut trouvé au lot B en LISANT le document produit, pas par un
     * compteur. C'est la même famille que le défaut ① du lot A.
     *
     * <p>On écarte donc les paragraphes situés entre {@code ▼ DÉBUT BOUCLE} et
     * {@code ▲ FIN BOUCLE} : l'expansion les traitera, occurrence par occurrence,
     * avec le scope de l'item.
     */
    private List<XWPFParagraph> horsBoucles(List<XWPFParagraph> paras) {
        List<XWPFParagraph> out = new ArrayList<>(paras.size());
        int profondeur = 0;
        for (XWPFParagraph p : paras) {
            String texte = paragraphText(p);
            boolean debut = DIR_LOOP_START_PATTERN.matcher(texte).find();
            boolean fin = DIR_LOOP_END_PATTERN.matcher(texte).find();
            if (debut) {
                profondeur++;
                out.add(p);          // le délimiteur lui-même reste visible
                continue;
            }
            if (fin) {
                profondeur = Math.max(0, profondeur - 1);
                out.add(p);
                continue;
            }
            if (profondeur == 0) out.add(p);
        }
        return out;
    }

    /**
     * Lot B (2026-09-11) — le résultat d'une passe de cases : ce qui n'a pas pu
     * être coché, et combien de paragraphes-marqueurs ont été retirés.
     *
     * <p>Le compte de retraits n'est pas un détail : dans une boucle, il décale la
     * position d'insertion de l'occurrence suivante. Sans lui, la deuxième
     * occurrence s'insérerait au milieu de la première.
     */
    private record ResultatCases(List<String> nonRenseignees,
                                 java.util.Set<XWPFParagraph> retires) {}

    /**
     * Lot B — LA PASSE DE CASES, BORNÉE À UN ENSEMBLE DE PARAGRAPHES.
     *
     * <p>Appelée deux fois, et pour deux raisons distinctes :
     * <ul>
     *   <li>une fois au niveau DOCUMENT, avec les variables globales — c'est le
     *       comportement historique, inchangé ;</li>
     *   <li>une fois par OCCURRENCE de boucle, avec le scope de l'item. Une case
     *       dont la valeur est une variable d'item ({@code $BE_GENRE}) n'a de
     *       sens que là : au niveau document, elle n'a aucun scope où se lire, et
     *       elle restait inerte — sans marqueur résiduel, donc sans alarme.</li>
     * </ul>
     */
    private ResultatCases resolveDirectorCheckboxes(XWPFDocument doc, Map<String, Object> scope,
                                                     List<XWPFParagraph> paras) {
        java.util.Set<XWPFParagraph> toRemove = new java.util.LinkedHashSet<>();
        List<String> unresolved = new ArrayList<>();

        for (int i = 0; i < paras.size(); i++) {
            Matcher m = CHECKBOX_MARKER_PATTERN.matcher(paragraphText(paras.get(i)));
            if (!m.find()) continue;
            String variable = m.group(1);
            String attendu = normalizeCompare(scopeString(scope, variable));
            toRemove.add(paras.get(i));

            boolean coche = false;
            boolean casesEcrites = i + 1 < paras.size()
                    && paragraphText(paras.get(i + 1)).indexOf(CHECKBOX_EMPTY) >= 0;

            if (casesEcrites) {
                // CONVENTION DU 4 SEPTEMBRE : chaque option porte sa case ☐.
                for (int j = i + 1; j < paras.size(); j++) {
                    String texte = paragraphText(paras.get(j));
                    int box = texte.indexOf(CHECKBOX_EMPTY);
                    if (box < 0) break; // fin du bloc d'options
                    String libelle = texte.substring(box + 1).trim();
                    if (!attendu.isEmpty() && !coche
                            && normalizeCompare(libelle).equals(attendu)) {
                        cocherCase(paras.get(j));
                        coche = true;
                    }
                }
            } else {
                for (XWPFParagraph option : optionsParStyle(paras, i)) {
                    boolean retenue = !attendu.isEmpty() && !coche
                            && normalizeCompare(paragraphText(option)).equals(attendu);
                    prefixerCase(option, retenue ? CHECKBOX_TICKED : CHECKBOX_EMPTY);
                    coche |= retenue;
                }
            }

            if (!coche) {
                if (!unresolved.contains(variable)) unresolved.add(variable);
                log.info("Case a cocher non renseignee : ${} (valeur « {} » absente des options)",
                        variable, scopeString(scope, variable));
            }
        }
        removeParagraphsByPosition(doc, toRemove);
        return new ResultatCases(unresolved, toRemove);
    }


    /**
     * CONVENTION DU 9 SEPTEMBRE : les options ne portent plus de case ☐. Ce sont
     * les paragraphes qui suivent le marqueur et qui partagent tous un meme style
     * de liste — le gabarit dit « ceci est une option » par le style, plus par un
     * caractere.
     *
     * <p>Le corpus livre le 9 septembre declare 27 blocs {@code ◈ CASE À COCHER}
     * et ne contient que 14 caracteres ☐, sur 4 blocs. Les 23 autres sortaient
     * donc en liste nue : rien de coche, aucun marqueur residuel, aucune alarme —
     * la declaration d'existence partait a la DGI sans forme juridique declaree.
     *
     * <p>La borne du bloc est le style : on prend celui du PREMIER paragraphe qui
     * suit le marqueur et on s'arrete des qu'il change. On refuse un style vide,
     * et le style du marqueur lui-meme ({@code JurikaBalise} porte aussi les
     * conditions et les boucles) : sans cela, un {@code ◇ SI} placé juste apres
     * les options serait avale comme une option de plus.
     *
     * @return les paragraphes d'option, dans l'ordre ; vide s'il n'y en a pas.
     */
    private List<XWPFParagraph> optionsParStyle(List<XWPFParagraph> paras, int marqueur) {
        List<XWPFParagraph> options = new ArrayList<>();
        if (marqueur + 1 >= paras.size()) return options;
        String styleMarqueur = paras.get(marqueur).getStyle();
        String styleOption = paras.get(marqueur + 1).getStyle();
        if (styleOption == null || styleOption.isBlank()
                || styleOption.equals(styleMarqueur)) {
            return options;
        }
        for (int j = marqueur + 1; j < paras.size(); j++) {
            if (!styleOption.equals(paras.get(j).getStyle())) break;
            options.add(paras.get(j));
        }
        return options;
    }

    /**
     * Ecrit la case devant le libelle d'une option qui n'en portait pas. La case
     * est ajoutee au rendu, jamais au gabarit : le fichier du cabinet reste
     * intouche.
     */
    private void prefixerCase(XWPFParagraph p, char casePrefixe) {
        String prefixe = casePrefixe + " ";
        List<XWPFRun> runs = p.getRuns();
        if (runs.isEmpty()) {
            XWPFRun r = p.createRun();
            r.setText(prefixe.trim(), 0);
            return;
        }
        XWPFRun premier = runs.get(0);
        String t = premier.text();
        premier.setText(prefixe + (t == null ? "" : t), 0);
    }

    /** Remplace la 1re case vide du paragraphe par une case cochee, run par run. */
    private void cocherCase(XWPFParagraph p) {
        for (XWPFRun r : p.getRuns()) {
            String t = r.text();
            if (t == null) continue;
            int idx = t.indexOf(CHECKBOX_EMPTY);
            if (idx < 0) continue;
            r.setText(t.substring(0, idx) + CHECKBOX_TICKED + t.substring(idx + 1), 0);
            return;
        }
    }

    /** Regions de boucle directeur de plus haut niveau : index_start -> index_end. */
    private java.util.Map<Integer, Integer> computeTopLevelLoopRegions(java.util.List<XWPFParagraph> paras) {
        java.util.Map<Integer, Integer> regions = new java.util.HashMap<>();
        java.util.Deque<Integer> starts = new java.util.ArrayDeque<>();
        for (int i = 0; i < paras.size(); i++) {
            String t = paragraphText(paras.get(i));
            if (DIR_LOOP_START_PATTERN.matcher(t).find()) {
                starts.push(i);
            } else if (DIR_LOOP_END_PATTERN.matcher(t).find()) {
                if (!starts.isEmpty()) {
                    int s = starts.pop();
                    if (starts.isEmpty()) regions.put(s, i);
                }
            }
        }
        return regions;
    }

    private void removeParagraphsByPosition(XWPFDocument doc, java.util.Set<XWPFParagraph> toRemove) {
        if (toRemove.isEmpty()) return;
        java.util.List<Integer> positions = new ArrayList<>();
        java.util.List<XWPFParagraph> introuvables = new ArrayList<>();
        for (XWPFParagraph p : toRemove) {
            int pos = doc.getPosOfParagraph(p);
            if (pos >= 0) positions.add(pos);
            else introuvables.add(p);
        }
        positions.sort(Collections.reverseOrder());
        for (int pos : positions) doc.removeBodyElement(pos);

        // Lot B (2026-09-11) — `getPosOfParagraph` compare par IDENTITE. Un
        // paragraphe que l'appelant n'a pas obtenu de `doc.getParagraphs()` n'y
        // est pas retrouve, et la suppression echoue EN SILENCE.
        //
        // On ne tente pas de rattraper au curseur XML : retirer le noeud sans
        // retirer le XWPFParagraph des listes internes de POI laisse un objet
        // orphelin, et la passe suivante leve XmlValueDisconnectedException.
        // L'appelant doit passer des paragraphes VIVANTS — cf.
        // `paragraphesDepuis`, employe par l'expansion des boucles.
        if (!introuvables.isEmpty()) {
            log.warn("removeParagraphsByPosition : {} paragraphe(s) non retrouve(s) par identite "
                    + "— l'appelant doit fournir des paragraphes issus de doc.getParagraphs()",
                    introuvables.size());
        }
    }

    /**
     * Expansion des boucles directeur ▼ DÉBUT BOUCLE — NOM … ▲ FIN BOUCLE — NOM.
     * Pour chaque element de la liste NOM : clone le corps, resout les conditions par
     * item (scope item + global), substitue les variables scopees, insere.
     */
    private void expandDirectorLoops(XWPFDocument doc, Map<String, Object> variables) {
        boolean modified = true;
        while (modified) {
            modified = false;
            java.util.List<XWPFParagraph> paras = new ArrayList<>(doc.getParagraphs());
            for (int i = 0; i < paras.size(); i++) {
                Matcher ms = DIR_LOOP_START_PATTERN.matcher(paragraphText(paras.get(i)));
                if (!ms.find()) continue;
                String blockName = ms.group(1);

                // Fin correspondante (meme nom), imbrication de meme nom geree par depth.
                int endIdx = -1;
                int depth = 0;
                for (int j = i + 1; j < paras.size(); j++) {
                    String tj = paragraphText(paras.get(j));
                    Matcher mStart = DIR_LOOP_START_PATTERN.matcher(tj);
                    if (mStart.find() && mStart.group(1).equals(blockName)) { depth++; continue; }
                    Matcher me = DIR_LOOP_END_PATTERN.matcher(tj);
                    if (me.find() && me.group(1).equals(blockName)) {
                        if (depth == 0) { endIdx = j; break; }
                        depth--;
                    }
                }
                if (endIdx < 0) {
                    log.warn("Boucle directeur ▼ {} sans ▲ FIN BOUCLE, laissee intacte", blockName);
                    continue;
                }

                // Boucles imbriquees (ex. Convocation : POINTS_ODJ / DOCUMENTS_JOINTS dans
                // ASSOCIES) : on traite d'abord la boucle la plus INTERNE (leaf). Sinon le
                // clonage de la boucle externe substituerait prematurement les variables
                // par-item de la boucle interne (non presentes dans le scope externe -> sentinel).
                boolean hasNested = false;
                for (int k = i + 1; k < endIdx; k++) {
                    if (DIR_LOOP_START_PATTERN.matcher(paragraphText(paras.get(k))).find()) {
                        hasNested = true;
                        break;
                    }
                }
                if (hasNested) continue; // pas une leaf : on cherchera une boucle interne d'abord

                java.util.List<Map<String, Object>> items = resolveListVariable(variables, blockName);

                // Snapshot des CTP du corps (entre delimiteurs, exclus).
                java.util.List<CTP> bodyCtps = new ArrayList<>();
                for (int k = i + 1; k < endIdx; k++) bodyCtps.add((CTP) paras.get(k).getCTP().copy());

                int firstPos = doc.getPosOfParagraph(paras.get(i));

                if (items == null) {
                    log.warn("Boucle directeur ▼ {} : aucune liste dans les variables, delimiteurs retires", blockName);
                    // Lot B — LE CORPS EST CONSERVE ICI : il faut donc y resoudre les
                    // cases a cocher, que la passe document-level a laissees de cote
                    // parce qu'elles etaient entre ▼ et ▲. Sans cela, le marqueur
                    // « ◈ CASE À COCHER » survivrait au rendu et s'imprimerait.
                    // Le scope est le scope GLOBAL : il n'y a pas d'item.
                    resolveDirectorCheckboxes(doc, variables,
                            new ArrayList<>(paras.subList(i + 1, endIdx)));
                    removeParagraphFromBody(doc, paras.get(endIdx));
                    removeParagraphFromBody(doc, paras.get(i));
                    modified = true;
                    break;
                }

                // Supprimer delimiteur debut + corps + delimiteur fin (haut -> bas).
                for (int k = endIdx; k >= i; k--) {
                    int pos = doc.getPosOfParagraph(paras.get(k));
                    if (pos >= 0) doc.removeBodyElement(pos);
                }

                if (items.isEmpty()) { modified = true; break; }

                Map<String, String> globalFlat = flatten(variables, "");
                java.util.List<String> bodyTexts = new ArrayList<>();
                for (CTP ctp : bodyCtps) bodyTexts.add(ctpText(ctp));

                int totalInserted = 0;
                for (int idx = 0; idx < items.size(); idx++) {
                    Map<String, Object> itemScope = mergedScope(variables, items.get(idx));
                    Map<String, String> scopedStr = buildScopedItemVars(items.get(idx), idx, blockName);
                    java.util.List<Integer> survivors = selectConditionalSurvivors(bodyTexts, itemScope);

                    // Lot B — LES CASES A COCHER DE CETTE OCCURRENCE.
                    //
                    // Resolues AVANT l'insertion, sur le texte du corps de boucle :
                    // une case dont la valeur est une variable d'ITEM
                    // (« ◈ CASE À COCHER pilotée par $BE_GENRE ») n'a de scope que
                    // la. La passe document-level, qui tourne avant l'expansion,
                    // n'avait nulle part ou la lire — elle ne cochait rien et
                    // consommait le marqueur : « ☐ Masculin ☐ Féminin » sortait
                    // inerte sur un beneficiaire dont le genre etait renseigne.
                    CasesDeBoucle cases = resoudreCasesDeBoucle(bodyCtps, bodyTexts,
                            survivors, itemScope);

                    java.util.List<XWPFParagraph> inserees = new ArrayList<>(survivors.size());
                    for (int k = 0; k < survivors.size(); k++) {
                        // Le paragraphe du marqueur n'est PAS insere. C'est ce qui
                        // dispense de le retrouver ensuite pour le retirer — et un
                        // clone insere par curseur n'apparait pas dans les listes
                        // internes de POI, donc on ne le retrouverait pas.
                        if (cases.marqueurs().contains(k)) continue;
                        CTP cloned = (CTP) bodyCtps.get(survivors.get(k)).copy();
                        XWPFParagraph newPara = insertParagraphAtPosition(
                                doc, firstPos + totalInserted + inserees.size(), cloned);
                        Boolean cochee = cases.options().get(k);
                        if (cochee != null) {
                            prefixerCase(newPara, cochee ? CHECKBOX_TICKED : CHECKBOX_EMPTY);
                        }
                        replaceInParagraph(newPara, globalFlat, scopedStr);
                        inserees.add(newPara);
                    }
                    totalInserted += inserees.size();
                }
                modified = true;
                break; // re-scan sur snapshot frais
            }
        }
    }

    /**
     * Lot B — le resultat d'une resolution de cases dans un corps de boucle.
     *
     * @param marqueurs index (dans {@code survivors}) des paragraphes
     *                  {@code ◈ CASE À COCHER} : ils ne sont pas inseres
     * @param options   index -> la case doit-elle etre cochee
     */
    private record CasesDeBoucle(java.util.Set<Integer> marqueurs,
                                 Map<Integer, Boolean> options) {}

    /**
     * Lot B — RESOUT LES CASES A COCHER D'UN CORPS DE BOUCLE, POUR UNE OCCURRENCE.
     *
     * <p>Meme regle qu'au niveau document, et pour la meme raison : le gabarit dit
     * « ceci est une option » par le STYLE des paragraphes qui suivent le
     * marqueur, plus par un caractere ☐ — convention du 9 septembre. On prend le
     * style du premier paragraphe suivant et on s'arrete des qu'il change.
     *
     * <p>On refuse le style du marqueur lui-meme : {@code JurikaBalise} porte
     * aussi les conditions et les boucles, et un {@code ◇ SI} place juste apres
     * les options serait avale comme une option de plus.
     */
    private CasesDeBoucle resoudreCasesDeBoucle(java.util.List<CTP> bodyCtps,
                                                 java.util.List<String> bodyTexts,
                                                 java.util.List<Integer> survivors,
                                                 Map<String, Object> itemScope) {
        java.util.Set<Integer> marqueurs = new java.util.LinkedHashSet<>();
        Map<Integer, Boolean> options = new java.util.LinkedHashMap<>();

        for (int k = 0; k < survivors.size(); k++) {
            String texte = bodyTexts.get(survivors.get(k));
            Matcher m = CHECKBOX_MARKER_PATTERN.matcher(texte);
            if (!m.find()) continue;
            marqueurs.add(k);

            String attendu = normalizeCompare(scopeString(itemScope, m.group(1)));
            String styleMarqueur = styleDe(bodyCtps.get(survivors.get(k)));
            String styleOption = k + 1 < survivors.size()
                    ? styleDe(bodyCtps.get(survivors.get(k + 1))) : null;
            if (styleOption == null || styleOption.isBlank()
                    || styleOption.equals(styleMarqueur)) {
                // Aucun bloc d'options reconnaissable : on retire le marqueur et on
                // le signale, plutot que de l'imprimer sur le document.
                log.info("Case a cocher ${} : aucun bloc d'options identifiable dans la boucle",
                        m.group(1));
                continue;
            }

            boolean deja = false;
            for (int j = k + 1; j < survivors.size(); j++) {
                if (!styleOption.equals(styleDe(bodyCtps.get(survivors.get(j))))) break;
                String libelle = bodyTexts.get(survivors.get(j)).trim();
                boolean retenue = !attendu.isEmpty() && !deja
                        && normalizeCompare(libelle).equals(attendu);
                options.put(j, retenue);
                deja |= retenue;
            }
            if (!deja) {
                log.info("Case a cocher non renseignee dans une boucle : ${} (valeur « {} »)",
                        m.group(1), scopeString(itemScope, m.group(1)));
            }
        }
        return new CasesDeBoucle(marqueurs, options);
    }

    /** Nom du style d'un paragraphe, ou {@code ""} s'il n'en porte pas. */
    private static String styleDe(CTP ctp) {
        if (ctp.getPPr() == null || ctp.getPPr().getPStyle() == null) return "";
        String val = ctp.getPPr().getPStyle().getVal();
        return val == null ? "" : val;
    }

    /** Fusionne globals + item (l'item ecrase) en un scope pour l'evaluation conditionnelle. */
    private Map<String, Object> mergedScope(Map<String, Object> globals, Map<String, Object> item) {
        Map<String, Object> scope = new HashMap<>(globals);
        if (item != null) {
            for (Map.Entry<String, Object> e : item.entrySet()) {
                scope.put(e.getKey(), e.getValue());
                scope.put(e.getKey().toUpperCase(Locale.ROOT), e.getValue());
            }
        }
        return scope;
    }

    /** Texte concatene des runs d'un CTP (pour classification conditionnelle). */
    private String ctpText(CTP ctp) {
        StringBuilder sb = new StringBuilder();
        for (CTR r : ctp.getRArray()) {
            for (CTText t : r.getTArray()) {
                String v = t.getStringValue();
                if (v != null) sb.append(v);
            }
        }
        return sb.toString();
    }

    private String concatRowText(XWPFTableRow row) {
        StringBuilder sb = new StringBuilder();
        for (XWPFTableCell cell : row.getTableCells()) {
            for (XWPFParagraph p : cell.getParagraphs()) {
                sb.append(paragraphText(p)).append('\n');
            }
        }
        return sb.toString();
    }

    private String paragraphText(XWPFParagraph p) {
        StringBuilder sb = new StringBuilder();
        for (XWPFRun r : p.getRuns()) {
            String t = r.text();
            if (t != null) sb.append(t);
        }
        return sb.toString();
    }

    /**
     * Supprime le marqueur ▶ NOM (et ◀ NOM) de TOUTES les occurrences du paragraphe.
     *
     * <p>IMPORTANT : opère sur le texte RECOMPOSÉ (concat des runs) puis réécrit dans
     * le 1er run + vide les autres. Cela garantit que la regex matche même quand Word
     * a fragmenté '▶ ASSOCIES' sur plusieurs runs (ex. run1='▶ A' / run2='SSOCIES').
     *
     * <p>Doit être appelé APRÈS la substitution scalaire sur le clone pour ne pas
     * interférer avec un éventuel ${VAR} adjacent.
     */
    private void stripBlockMarker(XWPFParagraph p, String blockName) {
        rewriteParagraphText(p, txt -> BLOCK_MARKER_STRIP_PATTERN.matcher(txt).replaceAll(""));
    }

    private void stripBlockMarker(XWPFTableRow row, String blockName) {
        for (XWPFTableCell cell : row.getTableCells()) {
            for (XWPFParagraph p : cell.getParagraphs()) {
                stripBlockMarker(p, blockName);
            }
        }
    }

    /**
     * Reecrit le texte d'un paragraphe en recomposant ses runs puis appliquant un
     * mapping. Ecrase tous les runs sauf le premier (limitation L1 documentee).
     */
    private void rewriteParagraphText(XWPFParagraph p, java.util.function.Function<String, String> mapper) {
        List<XWPFRun> runs = p.getRuns();
        if (runs.isEmpty()) return;
        String text = paragraphText(p);
        String rewritten = mapper.apply(text);
        if (rewritten.equals(text)) return;
        for (int i = runs.size() - 1; i > 0; i--) {
            p.removeRun(i);
        }
        runs.get(0).setText(rewritten, 0);
    }

    /**
     * Construit la map des variables scopees pour un item d'une liste. Les cles MAJUSCULES
     * de l'item ont priorite sur le scope global. Pour les blocs RESOLUTIONS, auto-injection
     * de RESOLUTION_RANG via OrdinalFrenchFormatter si absent.
     */
    private Map<String, String> buildScopedItemVars(Map<String, Object> item, int index, String blockName) {
        Map<String, String> scoped = new HashMap<>();
        if (item != null) {
            for (Map.Entry<String, Object> e : item.entrySet()) {
                String key = e.getKey();
                String val = e.getValue() == null ? "" : String.valueOf(e.getValue());
                scoped.put(key, val);
                scoped.put(key.toUpperCase(Locale.ROOT), val);
            }
        }
        // Auto-injection du rang ordinal pour RESOLUTIONS.
        String autoKey = AUTO_RANG_KEYS.get(blockName.toUpperCase(Locale.ROOT));
        if (autoKey != null && !scoped.containsKey(autoKey)) {
            scoped.put(autoKey, ordinalFormatter.ordinal(index + 1));
        }
        return scoped;
    }

    /**
     * Recherche une cle de liste dans la map (case-insensitive sur les cles de 1er niveau).
     * Retourne null si la cle n'existe pas ; retourne une liste vide si la cle existe mais
     * la valeur n'est pas une liste exploitable.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> resolveListVariable(Map<String, Object> variables, String blockName) {
        Object raw = null;
        // 1. Recherche exacte.
        if (variables.containsKey(blockName)) {
            raw = variables.get(blockName);
        } else {
            // 2. Case-insensitive sur les cles top-level.
            for (Map.Entry<String, Object> e : variables.entrySet()) {
                if (e.getKey().equalsIgnoreCase(blockName)) {
                    raw = e.getValue();
                    break;
                }
            }
        }
        if (raw == null) return null;
        if (!(raw instanceof List<?>)) return Collections.emptyList();
        List<?> list = (List<?>) raw;
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Map<?, ?>) {
                result.add((Map<String, Object>) o);
            } else {
                Map<String, Object> wrap = new HashMap<>();
                wrap.put("VALUE", o);
                result.add(wrap);
            }
        }
        return result;
    }

    // ============================================================
    // RESOLUTION DU TEMPLATE
    // ============================================================

    /**
     * Resout le template avec fallback variante par forme juridique.
     *
     * <p>Ordre de résolution L3 (Sprint manifest) :
     * <ol>
     *   <li>Si le manifest est dispo et contient le code (alias résolu inclus),
     *       on charge {@code templates/docx/{entry.file()}}.</li>
     *   <li>Sinon, comportement legacy : variante {@code _SARL/_SARL_AU} si applicable,
     *       puis classpath direct {@code templates/docx/{code}.docx}.</li>
     * </ol>
     */
    @SuppressWarnings("unchecked")
    private byte[] resolveTemplate(String templateCode, Map<String, Object> variables) {
        if (corpus != null) {
            for (String code : candidatsCorpus(templateCode, variables)) {
                if (corpus.gabarit(code).isPresent()) {
                    return corpus.lireVerifie(code);
                }
            }
        }
        Resource res = resolveClasspath(templateCode, variables);
        if (res == null) {
            return null;
        }
        if (corpus != null) {
            codesServisHorsCorpus.add(templateCode);
            log.warn("Gabarit {} absent du corpus {} : servi depuis le classpath", templateCode, corpus.version());
        }
        try (InputStream in = res.getInputStream()) {
            return in.readAllBytes();
        } catch (IOException ex) {
            throw new IllegalStateException("Gabarit classpath illisible : " + templateCode, ex);
        }
    }

    /**
     * Lot L2 : codes a chercher dans le corpus, dans l'ordre : variante de forme
     * ({@code <code>_<forme>}), code demande, puis code canonique du manifest L3
     * (alias de code) et sa variante.
     */
    private List<String> candidatsCorpus(String templateCode, Map<String, Object> variables) {
        List<String> codes = new ArrayList<>();
        Object forme = formeJuridique(variables);
        java.util.function.Consumer<String> ajouter = code -> {
            if (forme != null && !code.endsWith("_SARL") && !code.endsWith("_SARL_AU")) {
                codes.add(code + "_" + forme);
            }
            codes.add(code);
        };
        ajouter.accept(templateCode);
        if (manifestLoader != null) {
            manifestLoader.resolve(templateCode).map(TemplateManifest.TemplateEntry::code)
                    .filter(c -> !c.equals(templateCode)).ifPresent(ajouter);
        }
        return codes;
    }

    @SuppressWarnings("unchecked")
    private static Object formeJuridique(Map<String, Object> variables) {
        Object forme = variables.get("formeJuridique");
        if (forme == null && variables.get("societe") instanceof Map<?, ?> m) {
            forme = ((Map<String, Object>) m).get("formeJuridique");
        }
        return forme;
    }

    /**
     * Lot L2 : un alias du dictionnaire unique recoit la meme valeur (renseignee)
     * que son nom canonique, dans les deux sens (00_LISEZ_MOI du corpus, Conventions), au
     * niveau racine et dans chaque element de liste (boucles).
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> avecAlias(Map<String, Object> variables, DictionnaireUnique dictionnaire) {
        if (dictionnaire.alias().isEmpty()) {
            return variables;
        }
        Map<String, Object> out = new java.util.LinkedHashMap<>(variables);
        for (Map.Entry<String, Object> e : out.entrySet()) {
            if (e.getValue() instanceof List<?> l && l.stream().allMatch(i -> i instanceof Map)) {
                List<Object> items = new ArrayList<>();
                for (Object i : l) items.add(avecAlias((Map<String, Object>) i, dictionnaire));
                e.setValue(items);
            }
        }
        for (Map.Entry<String, String> a : dictionnaire.alias().entrySet()) {
            String alias = a.getKey().substring(1);
            String canonique = a.getValue().substring(1);
            // Seule une valeur RENSEIGNEE se recopie : une valeur vide recopiee masquerait
            // le marqueur de valeur manquante (vu au temoin L2 : $VILLE_GREFFE -> $RC_VILLE).
            if (renseignee(out.get(canonique)) && !renseignee(out.get(alias))) {
                out.put(alias, out.get(canonique));
            } else if (renseignee(out.get(alias)) && !renseignee(out.get(canonique))) {
                out.put(canonique, out.get(alias));
            }
        }
        return out;
    }

    private static boolean renseignee(Object valeur) {
        return valeur != null && !(valeur instanceof String s && s.isBlank());
    }

    @SuppressWarnings("unchecked")
    private Resource resolveClasspath(String templateCode, Map<String, Object> variables) {
        // 1. Manifest L3 (priorité — gère les aliases).
        if (manifestLoader != null) {
            var resolved = manifestLoader.resolve(templateCode);
            if (resolved.isPresent()) {
                TemplateManifest.TemplateEntry entry = resolved.get();
                if (entry.file() != null && !entry.file().isBlank()) {
                    if (!templateCode.equals(entry.code())) {
                        log.debug("Manifest alias : {} → {} (file={})",
                                templateCode, entry.code(), entry.file());
                    }
                    Resource manifestRes = new ClassPathResource("templates/docx/" + entry.file());
                    if (manifestRes.exists()) {
                        return manifestRes;
                    }
                }
            }
        }

        // 2. Fallback legacy : variante par forme juridique.
        Object forme = formeJuridique(variables);
        if (forme != null && !templateCode.endsWith("_SARL") && !templateCode.endsWith("_SARL_AU")) {
            String variantCode = templateCode + "_" + String.valueOf(forme);
            Resource variant = new ClassPathResource("templates/docx/" + variantCode + ".docx");
            if (variant.exists()) return variant;
        }
        Resource direct = new ClassPathResource("templates/docx/" + templateCode + ".docx");
        return direct.exists() ? direct : null;
    }

    // ============================================================
    // SUBSTITUTION SCALAIRE DES PLACEHOLDERS
    // ============================================================

    /**
     * Remplace placeholders en recomposant les runs (Word fragmente souvent ${var} / {{var}}).
     * scopedVars (eventuellement vide) ont priorite sur la map globale flatten.
     */
    private void replaceInParagraph(XWPFParagraph paragraph,
                                     Map<String, String> variables,
                                     Map<String, String> scopedVars) {
        List<XWPFRun> runs = paragraph.getRuns();
        if (runs.isEmpty()) return;

        StringBuilder fullText = new StringBuilder();
        for (XWPFRun r : runs) {
            String t = r.text();
            if (t != null) fullText.append(t);
        }
        String original = fullText.toString();
        // A.1 : la variable NUE $NOM commence par '$' sans accolade — on elargit la
        // garde rapide a tout '$' (couvre ${...} ET $NU) en plus de {{...}}.
        if (original.indexOf('$') < 0 && !original.contains("{{")) return;

        // Casse d'en-tete (2026-08-17) — les titres des modeles sont ecrits ENTIEREMENT
        // en majuscules, litteralement (aucun style w:caps) ; les valeurs injectees, elles,
        // arrivent en minuscules car elles servent AUSSI le corps du texte. D'ou
        // « GmbH DE DROIT allemand » et « DECISIONS DU gerant unique ». On determine donc
        // UNE FOIS par paragraphe si sa portion litterale (placeholders retires) est un
        // en-tete ; cf. CasseEnTete pour la garde qui protege sigles et noms propres.
        boolean enTete = CasseEnTete.estEnTete(
                PLACEHOLDER_PATTERN.matcher(original).replaceAll(""));

        Matcher m = PLACEHOLDER_PATTERN.matcher(original);
        StringBuilder sb = new StringBuilder();
        boolean changed = false;
        while (m.find()) {
            String resolved;
            if (m.group(1) != null) {
                // Groupe 1 : ${UPPERCASE}
                String name = m.group(1).trim();
                resolved = resolveUpper(name, variables, scopedVars,
                        "${" + name + "}");
            } else if (m.group(2) != null) {
                // Groupe 2 : {{lower}} (legacy)
                String key = m.group(2).trim();
                resolved = resolveLower(key, variables, scopedVars,
                        "{{" + key + "}}");
            } else {
                // Groupe 3 : $NU nue (format directeur, ex. $ASSOCIE_NOM). Deja en
                // snake-case strict MAJUSCULE — meme resolution que ${UPPERCASE}.
                String name = m.group(3);
                resolved = resolveUpper(name, variables, scopedVars, "$" + name);
            }
            // Grammaire d'assemblage (2026-08-17) — le modèle porte la préposition EN
            // DUR juste avant le placeholder (« Fait à $ASSEMBLEE_LIEU », « DÉCISIONS
            // DE $ORGANE_COMPETENT ») tandis que la valeur porte son propre article,
            // parce qu'elle sert aussi de SUJET ailleurs dans le même acte. La simple
            // concaténation donnait « Fait à au siège social » / « DE le gérant unique ».
            // On fusionne donc préposition + article ici : c'est la seule couche qui
            // connaisse les deux. Le contenu des modèles reste intouché.
            m.appendReplacement(sb, "");
            if (resolved.isEmpty()) {
                // Variable VIDE entre deux mots : le modèle écrit « $ASSOCIE_NOM
                // $ASSOCIE_PRENOM …… », or un associé personne morale n'a pas de prénom.
                // La substitution laissait alors une DOUBLE ESPACE (« HOLDING ATLAS
                //  ……… 700 parts ») — une cicatrice visible de la valeur absente.
                // On absorbe l'espace qui précédait la variable ; celui qui la suit
                // suffit à séparer les deux mots restants.
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) == ' ') {
                    sb.setLength(sb.length() - 1);
                }
                changed = true;
                continue;
            }
            // L'ORDRE COMPTE : la casse d'abord, la contraction ensuite. « le gerant
            // unique » devient « LE GERANT UNIQUE », puis la contraction avec le « DE »
            // du modele donne « DU GERANT UNIQUE ». Dans l'ordre inverse, la valeur
            // porterait deja un « DU » majuscule et ne serait plus vue comme
            // integralement minuscule : la mise en majuscules serait sautee.
            resolved = CasseEnTete.harmoniser(resolved, enTete);
            FrenchContraction.Fusion fusion = FrenchContraction.fusionner(sb, resolved);
            if (fusion.caracteresARetirer() > 0) {
                sb.setLength(sb.length() - fusion.caracteresARetirer());
            }
            sb.append(fusion.valeur());
            changed = true;
        }
        m.appendTail(sb);

        if (!changed) return;

        for (int i = runs.size() - 1; i > 0; i--) {
            paragraph.removeRun(i);
        }
        setRunTextMultiline(runs.get(0), sb.toString());
    }

    /**
     * 2026-08 — Écrit le texte dans le 1er run en convertissant les sauts de ligne
     * {@code '\n'} d'une valeur injectée en vrais retours à la ligne Word
     * ({@code <w:br/>}) AU SEIN du paragraphe. Permet à une variable multi-lignes
     * (ex. {@code $OBJET_SOCIAL} à plusieurs activités, rendu en liste à tirets)
     * d'apparaître en plusieurs lignes sans toucher au texte du modèle directeur.
     * Pour une valeur mono-ligne, comportement identique à {@code setText(text, 0)}.
     */
    private void setRunTextMultiline(XWPFRun run, String text) {
        if (text.indexOf('\n') < 0) {
            run.setText(text, 0);
            return;
        }
        String[] lines = text.split("\n", -1);
        run.setText(lines[0], 0);           // remplace le <w:t> à l'index 0
        for (int i = 1; i < lines.length; i++) {
            run.addBreak();                 // <w:br/>
            run.setText(lines[i]);          // append un nouveau <w:t>
        }
    }

    private String resolveUpper(String name, Map<String, String> variables,
                                 Map<String, String> scopedVars, String literal) {
        String trouvee = trouverUpper(name, variables, scopedVars);
        // Lot L3 (motif 9) : une valeur VIDE est une donnee manquante. Elle s'imprimait
        // en silence (« registre du commerce de , numero ») : elle est desormais marquee
        // et classee comme une variable absente.
        if (trouvee != null && !trouvee.isBlank()) return trouvee;
        String upper = normalizeUpperKey(name);
        if (log.isTraceEnabled()) {
            log.trace("Placeholder UPPERCASE non resolu ou vide, sentinel injecte : {}", literal);
        }
        return MissingVariableMarker.sentinel(upper.isEmpty() ? name : upper);
    }

    private String trouverUpper(String name, Map<String, String> variables, Map<String, String> scopedVars) {
        // 1. Resolution directe (placeholder deja en snake-case strict, ex. ${ASSOCIE_NOM}).
        if (scopedVars.containsKey(name)) return scopedVars.get(name);
        if (variables.containsKey(name)) return variables.get(name);
        // 2. Normalisation : espaces -> '_', UPPERCASE, accents/ponctuation strippes
        //    (necessaire pour les JAL : ${NOM ET PRENOM ASSOCIE} -> NOM_ET_PRENOM_ASSOCIE).
        String upper = normalizeUpperKey(name);
        if (!upper.equals(name)) {
            if (scopedVars.containsKey(upper)) return scopedVars.get(upper);
            if (variables.containsKey(upper)) return variables.get(upper);
        }
        // 3. Fallback : tester lowercase / normalize au cas ou flatten ait ajoute la variante.
        String lc = name.toLowerCase(Locale.ROOT);
        if (variables.containsKey(lc)) return variables.get(lc);
        String norm = normalize(name);
        if (variables.containsKey(norm)) return variables.get(norm);
        String normUpper = normalize(upper);
        if (variables.containsKey(normUpper)) return variables.get(normUpper);
        // 2026-06-19 (PARTIE A) — Variable non fournie : l'appelant insere un sentinel
        // {@link MissingVariableMarker#SENTINEL_OPEN}NOM_NORMALISE
        // {@link MissingVariableMarker#SENTINEL_CLOSE}, converti en run gras+rouge
        // par la passe universelle {@link MissingVariableMarker}. Le nom utilise est
        // la forme normalisee (UPPERCASE_SNAKE) pour rester lisible cote employe.
        return null;
    }

    /**
     * Normalise une cle ${...} : UPPERCASE + espaces/ponctuation -> '_'. Permet de
     * supporter les placeholders directeur avec espaces et accents (cas JAL).
     */
    static String normalizeUpperKey(String raw) {
        if (raw == null) return "";
        String n = java.text.Normalizer.normalize(raw, java.text.Normalizer.Form.NFD);
        n = n.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        n = n.toUpperCase(Locale.ROOT);
        // Tout caractere autre que [A-Z0-9_] devient '_'.
        n = n.replaceAll("[^A-Z0-9_]+", "_");
        // Compactage des '_' multiples + trim.
        n = n.replaceAll("_+", "_").replaceAll("^_+|_+$", "");
        return n;
    }

    private String resolveLower(String key, Map<String, String> variables,
                                 Map<String, String> scopedVars, String literal) {
        // scoped d'abord (rare pour legacy mais coherent). Lot L3 : une valeur vide est
        // une donnee manquante, comme dans resolveUpper.
        String trouvee = null;
        String lc = key.toLowerCase(Locale.ROOT);
        String norm = normalize(key);
        if (scopedVars.containsKey(key)) trouvee = scopedVars.get(key);
        else if (variables.containsKey(key)) trouvee = variables.get(key);
        else if (scopedVars.containsKey(lc)) trouvee = scopedVars.get(lc);
        else if (variables.containsKey(lc)) trouvee = variables.get(lc);
        else if (variables.containsKey(norm)) trouvee = variables.get(norm);
        if (trouvee != null && !trouvee.isBlank()) return trouvee;
        // 2026-06-19 (PARTIE A) — Coherence avec resolveUpper : sentinel pour
        // materialiser la variable absente, normalisee en UPPERCASE_SNAKE pour
        // la lisibilite cote employe.
        if (log.isTraceEnabled()) {
            log.trace("Placeholder legacy non resolu, sentinel injecte : {}", literal);
        }
        return MissingVariableMarker.sentinel(normalizeUpperKey(key));
    }

    private String normalize(String key) {
        return key.toLowerCase(Locale.ROOT).replaceAll("[\\s_]+", "");
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> flatten(Map<String, Object> source, String prefix) {
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, Object> e : source.entrySet()) {
            String key = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
            Object v = e.getValue();
            if (v == null) {
                put(result, key, "");
            } else if (v instanceof Map<?, ?> nested) {
                result.putAll(flatten((Map<String, Object>) nested, key));
            } else if (v instanceof List<?> list) {
                // Pour les listes : on garde le comportement legacy (count + indexed access)
                // mais ces cles ne sont PAS utilisees par les blocs ▶ (lesquels lisent variables.get(NAME) directement).
                put(result, key + ".count", String.valueOf(list.size()));
                for (int i = 0; i < list.size(); i++) {
                    Object item = list.get(i);
                    if (item instanceof Map<?, ?> mapItem) {
                        result.putAll(flatten((Map<String, Object>) mapItem, key + "[" + i + "]"));
                    } else {
                        put(result, key + "[" + i + "]", String.valueOf(item));
                    }
                }
            } else {
                put(result, key, String.valueOf(v));
            }
        }
        return result;
    }

    private void put(Map<String, String> map, String key, String value) {
        map.put(key, value);
        map.put(key.toLowerCase(Locale.ROOT), value);
        map.put(normalize(key), value);
    }

    /**
     * Resultat d'une generation de document.
     *
     * @param missingVariables liste ordonnee, dedupliquee, des noms de variables
     *                         non renseignees (et donc rendues en marqueur rouge
     *                         "‹ VALEUR MANQUANTE : ... ›" dans le .docx).
     *                         Vide si toutes les variables ont ete resolues.
     * @param templateFound    toujours vrai depuis le lot L2 (un gabarit introuvable
     *                         leve GabaritIntrouvableException) ; conserve pour
     *                         l'en-tete X-Template-Found lu par le front.
     * @param manquantes       Lot 5 (2026-09-07) — meme liste, mais qualifiee : pour
     *                         chaque variable, l'endroit du document ou elle apparait
     *                         et si son vide se lit DANS UNE PHRASE. C'est ce qui
     *                         permet a l'appelant de refuser une generation qui
     *                         produirait « ne le  a , demeurant a  », sans refuser
     *                         une case administrative laissee blanche.
     */
    public record DocumentResult(byte[] bytes,
                                  String contentType,
                                  String filename,
                                  boolean templateFound,
                                  List<String> missingVariables,
                                  List<MissingVariableMarker.Manquante> manquantes) {
        public DocumentResult {
            missingVariables = missingVariables == null
                    ? Collections.emptyList()
                    : List.copyOf(missingVariables);
            manquantes = manquantes == null
                    ? Collections.emptyList()
                    : List.copyOf(manquantes);
        }

        /** Compat : appelants anterieurs au lot 5, qui n'ont pas le detail. */
        public DocumentResult(byte[] bytes, String contentType, String filename,
                              boolean templateFound, List<String> missingVariables) {
            this(bytes, contentType, filename, templateFound, missingVariables, List.of());
        }

        /**
         * Variables dont le vide s'imprime dans une phrase. Non vide = la generation
         * doit etre refusee : le document sortirait avec un trou grammatical.
         */
        public List<MissingVariableMarker.Manquante> manquantesBloquantes() {
            return manquantes.stream().filter(MissingVariableMarker.Manquante::bloquante).toList();
        }
    }

    // Marker (unused but referenced by ai-service code historically).
    @SuppressWarnings("unused")
    private BigInteger __unused = BigInteger.ZERO;
}
