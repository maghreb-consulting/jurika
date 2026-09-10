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
 */
final class ZipHorodatage {

    private ZipHorodatage() {}

    /** Signature d'un en-tête local de fichier. */
    private static final int SIG_LOCAL = 0x04034b50;
    /** Signature d'une entrée du répertoire central. */
    private static final int SIG_CENTRAL = 0x02014b50;
    /** Signature de fin de répertoire central. */
    private static final int SIG_EOCD = 0x06054b50;

    /** 1980-01-01 00:00:00 en heure MS-DOS : heure 0x0000, date 0x0021. */
    private static final int HEURE_FIGEE = 0x0000;
    private static final int DATE_FIGEE = 0x0021;

    /**
     * Réécrit l'horodatage de toutes les entrées, sur place, et rend le même
     * tableau. En cas de ZIP illisible, rend le tableau inchangé : figer une date
     * ne vaut pas de faire échouer une génération.
     */
    static byte[] figer(byte[] zip) {
        if (zip == null || zip.length < 22) return zip;
        try {
            int eocd = chercherEocd(zip);
            if (eocd < 0) return zip;
            int nb = lireU16(zip, eocd + 10);
            int offset = lireU32(zip, eocd + 16);
            for (int i = 0; i < nb; i++) {
                if (offset + 46 > zip.length || lireU32(zip, offset) != SIG_CENTRAL) return zip;
                ecrireU16(zip, offset + 12, HEURE_FIGEE);
                ecrireU16(zip, offset + 14, DATE_FIGEE);

                int local = lireU32(zip, offset + 42);
                if (local + 30 <= zip.length && lireU32(zip, local) == SIG_LOCAL) {
                    ecrireU16(zip, local + 10, HEURE_FIGEE);
                    ecrireU16(zip, local + 12, DATE_FIGEE);
                }
                int lgNom = lireU16(zip, offset + 28);
                int lgExtra = lireU16(zip, offset + 30);
                int lgComm = lireU16(zip, offset + 32);
                offset += 46 + lgNom + lgExtra + lgComm;
            }
            return zip;
        } catch (RuntimeException ex) {
            return zip;
        }
    }

    /** Dernière occurrence de la signature de fin de répertoire central. */
    private static int chercherEocd(byte[] z) {
        for (int i = z.length - 22; i >= 0; i--) {
            if (lireU32(z, i) == SIG_EOCD) return i;
        }
        return -1;
    }

    private static int lireU16(byte[] z, int i) {
        return (z[i] & 0xff) | ((z[i + 1] & 0xff) << 8);
    }

    private static int lireU32(byte[] z, int i) {
        return (z[i] & 0xff) | ((z[i + 1] & 0xff) << 8)
                | ((z[i + 2] & 0xff) << 16) | ((z[i + 3] & 0xff) << 24);
    }

    private static void ecrireU16(byte[] z, int i, int v) {
        z[i] = (byte) (v & 0xff);
        z[i + 1] = (byte) ((v >>> 8) & 0xff);
    }
}
