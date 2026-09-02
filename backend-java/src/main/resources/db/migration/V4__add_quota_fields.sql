-- Ajouter une colonne pour la taille des fichiers afin de calculer le quota
ALTER TABLE documents
ADD COLUMN file_size_bytes BIGINT DEFAULT 0;

-- Mettre à jour les anciennes entrées pour éviter des nulls gênants (s'il y en a)
UPDATE documents SET file_size_bytes = 0 WHERE file_size_bytes IS NULL;
