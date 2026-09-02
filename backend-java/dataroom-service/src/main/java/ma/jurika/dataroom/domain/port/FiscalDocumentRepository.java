package ma.jurika.dataroom.domain.port;

/**
 * Sprint 7 / TASK 6.1 -- Port domain (vide) pour le futur module Fiscal.
 *
 * Sprint 8 ajoutera :
 *   - DataroomFiscalDocumentEntity (table dataroom_fiscal_documents)
 *   - FiscalDocumentSummary + Filter + statut OUVERT/VERROUILLE
 *   - 7 categories CGI : TVA, IS, IR, TP_TSC, RAS, ATTESTATIONS, CONTENTIEUX
 *   - Sous-classification declaration / paiement / remboursement / attestation / notification
 *   - Alertes echeances (TVA mensuelle J+20, IS annuelle 31/03, acomptes 31/03 30/06 30/09 31/12)
 *
 * Cf. docs/v2/Regles_de_Gestion_V2.md RG-DF01..28 et CGI Art. 211.
 */
public interface FiscalDocumentRepository {
    // Sprint 8 -- a implementer
}
