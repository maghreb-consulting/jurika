package ma.jurika.common.docx;

/**
 * Fige l'horodatage des entrees d'un ZIP (donc d'un .docx) : deux generations des memes
 * donnees donnent les memes octets (le coeur metier est deterministe).
 *
 * <p>Lot L5 (E6) : remonte de l'ai-service ({@code ma.jurika.ai.document.ZipHorodatage}, lot A
 * du 2026-09-10, qui y delegue desormais) pour servir aussi l'etat des debours du
 * ticket-service. Rien n'est recompresse : seuls les deux champs d'horodatage MS-DOS de
 * l'en-tete local et de l'entree centrale de chaque fichier sont reecrits, en suivant le
 * repertoire central. Valeur retenue : 1er janvier 1980 a 00:00:00, plancher du format.
 */
public final class ZipHorodatage {

    private ZipHorodatage() {}

    private static final int SIG_LOCAL = 0x04034b50;
    private static final int SIG_CENTRAL = 0x02014b50;
    private static final int SIG_EOCD = 0x06054b50;

    /** 1980-01-01 00:00:00 en heure MS-DOS : heure 0x0000, date 0x0021. */
    private static final int HEURE_FIGEE = 0x0000;
    private static final int DATE_FIGEE = 0x0021;

    /**
     * Reecrit l'horodatage de toutes les entrees, sur place, et rend le meme tableau.
     * ZIP illisible : tableau rendu inchange.
     */
    public static byte[] figer(byte[] zip) {
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
