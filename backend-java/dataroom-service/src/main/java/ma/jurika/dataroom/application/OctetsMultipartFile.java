package ma.jurika.dataroom.application;

import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/**
 * Lot L5 (E7) : un document produit par la Data Room elle-meme (fiche client, fiche
 * dossier), presente au depot versionne comme un fichier televerse.
 */
final class OctetsMultipartFile implements MultipartFile {

    private final String nomFichier;
    private final String typeContenu;
    private final byte[] contenu;

    OctetsMultipartFile(String nomFichier, String typeContenu, byte[] contenu) {
        this.nomFichier = nomFichier;
        this.typeContenu = typeContenu;
        this.contenu = contenu.clone();
    }

    @Override public String getName() { return "file"; }
    @Override public String getOriginalFilename() { return nomFichier; }
    @Override public String getContentType() { return typeContenu; }
    @Override public boolean isEmpty() { return contenu.length == 0; }
    @Override public long getSize() { return contenu.length; }
    @Override public byte[] getBytes() { return contenu.clone(); }
    @Override public InputStream getInputStream() { return new ByteArrayInputStream(contenu); }

    @Override
    public void transferTo(File dest) throws IOException {
        Files.write(dest.toPath(), contenu);
    }
}
