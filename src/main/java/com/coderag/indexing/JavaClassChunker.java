package com.coderag.indexing;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

/**
 * One chunk per class/interface/enum/record declaration, at any nesting level. Uses a real
 * parser rather than brace-counting, which breaks on nested types, braces inside string
 * literals, and braces inside comments.
 */
@Component
public class JavaClassChunker implements FileChunker {

    private static final Logger log = LoggerFactory.getLogger(JavaClassChunker.class);

    @Override
    public boolean supports(Path file) {
        return file.toString().endsWith(".java");
    }

    @Override
    public List<Document> chunk(Path file, String repoName, Path repoRoot) {
        CompilationUnit unit;
        try {
            unit = StaticJavaParser.parse(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + file, e);
        } catch (ParseProblemException e) {
            // A handful of generated or edge-case files won't parse cleanly - skip that one
            // file rather than fail the whole indexing run over it.
            log.warn("Skipping unparseable file {}: {}", file, e.getMessage());
            return List.of();
        }

        String relativePath = repoRoot.relativize(file).toString().replace('\\', '/');
        String packageName = unit.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("");

        List<TypeDeclaration<?>> types = new ArrayList<>();
        types.addAll(unit.findAll(ClassOrInterfaceDeclaration.class));
        types.addAll(unit.findAll(EnumDeclaration.class));
        types.addAll(unit.findAll(RecordDeclaration.class));

        List<Document> documents = new ArrayList<>();
        for (TypeDeclaration<?> type : types) {
            String qualifiedName = packageName.isEmpty()
                    ? type.getNameAsString()
                    : packageName + "." + type.getNameAsString();
            Map<String, Object> metadata = Map.of(
                    "repo", repoName,
                    "path", relativePath,
                    "type", "java-class",
                    "symbol", qualifiedName);
            documents.add(new Document(type.toString(), metadata));
        }
        return documents;
    }
}
