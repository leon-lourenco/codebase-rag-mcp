package com.coderag.indexing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

/** One chunk per markdown heading section (any level, ATX-style `#`..`######`). */
@Component
public class MarkdownSectionChunker implements FileChunker {

    private static final Pattern HEADING = Pattern.compile("(?m)^#{1,6}\\s+.*$");

    @Override
    public boolean supports(Path file) {
        return file.toString().endsWith(".md");
    }

    @Override
    public List<Document> chunk(Path file, String repoName, Path repoRoot) {
        String content;
        try {
            content = Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + file, e);
        }

        String relativePath = repoRoot.relativize(file).toString().replace('\\', '/');
        List<Integer> starts = new ArrayList<>();
        List<String> headings = new ArrayList<>();
        Matcher matcher = HEADING.matcher(content);
        while (matcher.find()) {
            starts.add(matcher.start());
            headings.add(matcher.group().replaceFirst("^#{1,6}\\s+", "").trim());
        }
        starts.add(content.length());

        List<Document> documents = new ArrayList<>();
        for (int i = 0; i < starts.size() - 1; i++) {
            String section = content.substring(starts.get(i), starts.get(i + 1)).trim();
            if (section.isBlank()) {
                continue;
            }
            documents.add(new Document(section, Map.of(
                    "repo", repoName,
                    "path", relativePath,
                    "type", "markdown-section",
                    "symbol", headings.get(i))));
        }

        // No headings at all (rare, e.g. a short notes file) - index the whole file as one
        // chunk instead of silently dropping it.
        if (documents.isEmpty() && !content.isBlank()) {
            documents.add(new Document(content, Map.of(
                    "repo", repoName,
                    "path", relativePath,
                    "type", "markdown-section",
                    "symbol", relativePath)));
        }

        return documents;
    }
}
