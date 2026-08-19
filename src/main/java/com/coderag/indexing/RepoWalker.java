package com.coderag.indexing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/** Walks a repo root and returns every file worth chunking, skipping build/VCS noise. */
@Component
public class RepoWalker {

    private static final Set<String> SKIP_DIRS = Set.of(
            ".git", "target", "build", "node_modules", ".gradle", ".mvn", ".idea", "out");

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(".java", ".md");

    public List<Path> walk(Path repoRoot) {
        try (Stream<Path> paths = Files.walk(repoRoot)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(this::isNotInSkippedDir)
                    .filter(this::hasSupportedExtension)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to walk " + repoRoot, e);
        }
    }

    private boolean isNotInSkippedDir(Path path) {
        for (Path segment : path) {
            if (SKIP_DIRS.contains(segment.toString())) {
                return false;
            }
        }
        return true;
    }

    private boolean hasSupportedExtension(Path path) {
        String name = path.toString();
        return SUPPORTED_EXTENSIONS.stream().anyMatch(name::endsWith);
    }
}
