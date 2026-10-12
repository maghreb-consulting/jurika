package ma.jurika.ai.document;

/**
 * Lot A (2026-09-10) — FIGE L'HORODATAGE DES ENTRÉES D'UN ZIP.
 *
 * <h2>Pourquoi</h2>
 * Un {@code .docx} est un ZIP, et POI horodate chaque entrée à l'heure d'écriture.
 * Deux générations du même dossier, à deux secondes d'intervalle, produisaient donc
 * des octets différents — sans qu'une seule ligne du document ait bougé.
 *
 * <p>Le défaut s'est manifesté par un test rouge, une fois :
 * {@code MissingVariableMarkerTest.determinisme_bit_pour_bit_meme_payload} a échoué
 * sur l'octet d'index 10, qui est précisément le champ <i>heure de dernière
 * modification</i> de l'en-tête local ZIP. Les deux rendus comparés avaient enjambé
 * la frontière de deux secondes de l'horodatage MS-DOS. Un test de déterminisme qui
 * dépend de l'heure ne teste pas le déterminisme.
 *
 * <p>Ce n'est pas qu'une affaire de test. « Le même dossier donne le même document »
 * est l'argument du projet devant un cabinet : autant qu'il soit démontrable octet
 * pour octet, y compris pour un contrôle d'intégrité ou une empreinte archivée.
 *
 * <h2>Comment</h2>
 * On ne recompresse rien : recompresser changerait les octets utiles au gré du
 * niveau de déflation. On réécrit UNIQUEMENT les deux champs d'horodatage, dans
 * l'en-tête local et dans l'entrée centrale de chaque fichier, en suivant le
 * répertoire central plutôt qu'en cherchant des signatures à l'aveugle — une
 * signature peut apparaître par hasard dans des données compressées.
 *
 * <p>Valeur retenue : le 1<sup>er</sup> janvier 1980 à 00:00:00, plancher du format
 * MS-DOS et convention des chaînes de construction reproductibles.
 *
 * <p>Lot L5 (E6) : l'implementation est remontee dans
 * {@link ma.jurika.common.docx.ZipHorodatage} (jurika-common), qui sert aussi l'etat des
 * debours du ticket-service ; cette classe y delegue.
 */
final class ZipHorodatage {

    private ZipHorodatage() {}

    /**
     * Réécrit l'horodatage de toutes les entrées, sur place, et rend le même
     * tableau. En cas de ZIP illisible, rend le tableau inchangé : figer une date
     * ne vaut pas de faire échouer une génération.
     */
    static byte[] figer(byte[] zip) {
        return ma.jurika.common.docx.ZipHorodatage.figer(zip);
    }
}
