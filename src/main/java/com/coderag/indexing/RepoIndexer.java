package com.coderag.indexing;

import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * CLI entry point for indexing a repo: {@code java -jar app.jar --index <path> <repoName>}.
 * A no-op (returns immediately) when {@code --index} isn't present, so the same jar falls
 * through to normal MCP-server startup otherwise.
 */
@Component
public class RepoIndexer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RepoIndexer.class);

    private final RepoWalker repoWalker;
    private final List<FileChunker> chunkers;
    private final VectorStore vectorStore;
    private final ConfigurableApplicationContext context;

    public RepoIndexer(RepoWalker repoWalker, List<FileChunker> chunkers, VectorStore vectorStore,
            ConfigurableApplicationContext context) {
        this.repoWalker = repoWalker;
        this.chunkers = chunkers;
        this.vectorStore = vectorStore;
        this.context = context;
    }

    @Override
    public void run(String... args) {
        int flagIndex = indexOf(args, "--index");
        if (flagIndex == -1) {
            return;
        }
        if (flagIndex + 2 >= args.length) {
            log.error("Usage: --index <path> <repoName>");
            System.exit(SpringApplication.exit(context, () -> 1));
            return;
        }

        Path repoRoot = Path.of(args[flagIndex + 1]).toAbsolutePath().normalize();
        String repoName = args[flagIndex + 2];

        int fileCount = 0;
        int chunkCount = 0;
        for (Path file : repoWalker.walk(repoRoot)) {
            FileChunker chunker = chunkers.stream()
                    .filter(c -> c.supports(file))
                    .findFirst()
                    .orElse(null);
            if (chunker == null) {
                continue;
            }
            List<Document> documents = chunker.chunk(file, repoName, repoRoot);
            if (documents.isEmpty()) {
                continue;
            }
            vectorStore.add(documents);
            fileCount++;
            chunkCount += documents.size();
            log.info("Indexed {} ({} chunks)", repoRoot.relativize(file), documents.size());
        }

        log.info("Done: {} files, {} chunks indexed for repo '{}'", fileCount, chunkCount, repoName);
        System.exit(SpringApplication.exit(context, () -> 0));
    }

    private static int indexOf(String[] args, String value) {
        for (int i = 0; i < args.length; i++) {
            if (value.equals(args[i])) {
                return i;
            }
        }
        return -1;
    }
}
