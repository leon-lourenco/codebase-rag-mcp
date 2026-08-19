package com.coderag.indexing;

import java.nio.file.Path;
import java.util.List;
import org.springframework.ai.document.Document;

/**
 * Splits one file into indexable chunks. Chunking is file-type based, not tied to any
 * particular repo's README/module conventions — that's what makes the indexer work unmodified
 * across differently-shaped repos.
 */
public interface FileChunker {

    boolean supports(Path file);

    List<Document> chunk(Path file, String repoName, Path repoRoot);
}
