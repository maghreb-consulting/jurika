package ma.jurika.dataroom.infrastructure.word;

/**
 * Lot L5 (E7) : fige l'horodatage des entrees d'un ZIP (un .docx), pour que deux
 * generations des memes donnees donnent les memes octets.
 *
 * <p>Copie de {@code ma.jurika.ai.document.ZipHorodatage} (ai-service, lot A), classe
 * interne a son paquet et a son service : dataroom ne depend pas d'ai-service. Meme
 * regle : seuls les champs d'horodatage de l'en-tete local et de l'entree centrale sont
 * reecrits, en suivant le repertoire central ; valeur figee : 1er janvier 1980, 00:00:00
 * (plancher du format MS-DOS).
 */
final class ZipHorodatage {

    private ZipHorodatage() {}

    private static final int SIG_LOCAL = 0x04034b50;
    private static final int SIG_CENTRAL = 0x02014b50;
    private static final int SIG_EOCD = 0x06054b50;
    private static final int HEURE_FIGEE = 0x0000;
    private static final int DATE_FIGEE = 0x0021;

    /**
     * Reecrit l'horodatage de toutes les entrees, sur place. Contrairement a la version
     * d'ai-service, un ZIP illisible est une erreur : la fiche est produite ici meme, un
     * document qui ne se fige pas est un defaut a signaler, pas a avaler.
     */
    static byte[] figer(byte[] zip) {
        if (zip == null || zip.length < 22) {
            throw new IllegalStateException("Document Word illisible : archive trop courte.");
        }
        int eocd = chercherEocd(zip);
        if (eocd < 0) throw new IllegalStateException("Document Word illisible : fin d'archive absente.");
        int nb = lireU16(zip, eocd + 10);
        int offset = lireU32(zip, eocd + 16);
        for (int i = 0; i < nb; i++) {
            if (offset + 46 > zip.length || lireU32(zip, offset) != SIG_CENTRAL) {
                throw new IllegalStateException("Document Word illisible : repertoire central rompu.");
            }
            ecrireU16(zip, offset + 12, HEURE_FIGEE);
            ecrireU16(zip, offset + 14, DATE_FIGEE);
            int local = lireU32(zip, offset + 42);
            if (local + 30 <= zip.length && lireU32(zip, local) == SIG_LOCAL) {
                ecrireU16(zip, local + 10, HEURE_FIGEE);
                ecrireU16(zip, local + 12, DATE_FIGEE);
            }
            offset += 46 + lireU16(zip, offset + 28) + lireU16(zip, offset + 30) + lireU16(zip, offset + 32);
        }
        return zip;
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
        return (z[i] & 0xff) | ((z[i + 1] & 0xff) << 8) | ((z[i + 2] & 0xff) << 16) | ((z[i + 3] & 0xff) << 24);
    }

    private static void ecrireU16(byte[] z, int i, int v) {
        z[i] = (byte) (v & 0xff);
        z[i + 1] = (byte) ((v >>> 8) & 0xff);
    }
}
