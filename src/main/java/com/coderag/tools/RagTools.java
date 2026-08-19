package com.coderag.tools;

import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * The five tools an MCP client (Claude Code/Desktop) can call against the codebase RAG store.
 * Every method here is pure retrieval - none of them call an LLM. The calling client is the
 * LLM; these tools only fetch and return raw evidence for it to reason over and synthesize an
 * answer from.
 */
@Component
public class RagTools {

    private final VectorStore vectorStore;
    private final JdbcClient jdbcClient;

    public RagTools(VectorStore vectorStore, JdbcClient jdbcClient) {
        this.vectorStore = vectorStore;
        this.jdbcClient = jdbcClient;
    }

    @McpTool(
            name = "list_indexed_repos",
            description = "Lists every repository currently indexed in the codebase RAG store, "
                    + "with file and chunk counts. Call this first to discover what's available "
                    + "before searching or fetching a specific file.")
    public List<RepoSummary> listIndexedRepos() {
        return jdbcClient.sql("""
                SELECT metadata->>'repo' AS repo,
                       count(DISTINCT metadata->>'path') AS files,
                       count(*) AS chunks
                FROM vector_store
                GROUP BY metadata->>'repo'
                ORDER BY repo
                """)
                .query((rs, rowNum) -> new RepoSummary(
                        rs.getString("repo"), rs.getLong("files"), rs.getLong("chunks")))
                .list();
    }

    @McpTool(
            name = "search_code",
            description = "Semantic search over indexed code and docs. Returns the most "
                    + "relevant chunks (Java classes/interfaces/enums/records, or markdown "
                    + "sections) across one or all indexed repos, ranked by similarity to the "
                    + "query.")
    public List<SearchResult> searchCode(
            @McpToolParam(description = "Natural-language or code-related search query", required = true)
            String query,
            @McpToolParam(description = "Restrict the search to one repo name, as returned by "
                    + "list_indexed_repos. Omit to search across all indexed repos.", required = false)
            String repo) {
        SearchRequest.Builder request = SearchRequest.builder().query(query).topK(8);
        if (StringUtils.hasText(repo)) {
            request.filterExpression(new FilterExpressionBuilder().eq("repo", repo).build());
        }
        return vectorStore.similaritySearch(request.build()).stream()
                .map(doc -> new SearchResult(
                        (String) doc.getMetadata().get("repo"),
                        (String) doc.getMetadata().get("path"),
                        (String) doc.getMetadata().get("symbol"),
                        (String) doc.getMetadata().get("type"),
                        doc.getScore() == null ? 0.0 : doc.getScore(),
                        doc.getText()))
                .toList();
    }

    @McpTool(
            name = "get_file",
            description = "Returns every indexed chunk for one file (its classes, or its "
                    + "markdown sections), identified by repo name and path. Use search_code or "
                    + "list_structures first to find the right repo/path pair.")
    public List<FileChunk> getFile(
            @McpToolParam(description = "Repo name, as returned by list_indexed_repos", required = true)
            String repo,
            @McpToolParam(description = "File path relative to the repo root, e.g. "
                    + "src/main/java/com/example/Foo.java", required = true)
            String path) {
        return jdbcClient.sql("""
                SELECT metadata->>'symbol' AS symbol, metadata->>'type' AS type, content
                FROM vector_store
                WHERE metadata->>'repo' = :repo AND metadata->>'path' = :path
                """)
                .param("repo", repo)
                .param("path", path)
                .query((rs, rowNum) -> new FileChunk(
                        rs.getString("symbol"), rs.getString("type"), rs.getString("content")))
                .list();
    }

    @McpTool(
            name = "list_structures",
            description = "Lists every indexed symbol (class/interface/enum/record, or markdown "
                    + "section heading) with its repo and file path, optionally scoped to one "
                    + "repo. Useful for browsing what exists before searching or fetching a "
                    + "specific file.")
    public List<StructureEntry> listStructures(
            @McpToolParam(description = "Restrict to one repo name. Omit to list structures "
                    + "across all indexed repos.", required = false)
            String repo) {
        boolean scoped = StringUtils.hasText(repo);
        String sql = """
                SELECT metadata->>'repo' AS repo, metadata->>'path' AS path,
                       metadata->>'symbol' AS symbol, metadata->>'type' AS type
                FROM vector_store
                %s
                ORDER BY repo, path
                """.formatted(scoped ? "WHERE metadata->>'repo' = :repo" : "");
        JdbcClient.StatementSpec statement = jdbcClient.sql(sql);
        if (scoped) {
            statement = statement.param("repo", repo);
        }
        return statement
                .query((rs, rowNum) -> new StructureEntry(
                        rs.getString("repo"), rs.getString("path"), rs.getString("symbol"), rs.getString("type")))
                .list();
    }

    @McpTool(
            name = "explain_concept",
            description = "Looks up a named concept (a class, interface, design pattern, or "
                    + "algorithm name) across indexed repos: returns its own definition chunk "
                    + "if a matching symbol exists, plus the most relevant related chunks found "
                    + "via semantic search. This is raw evidence for the caller to build an "
                    + "explanation from - it does not synthesize any explanation itself.")
    public List<SearchResult> explainConcept(
            @McpToolParam(description = "Name of the concept to explain, e.g. 'Observer "
                    + "Pattern', 'OutboxDispatcher', 'quicksort'", required = true)
            String name,
            @McpToolParam(description = "Restrict to one repo name. Omit to search across all "
                    + "indexed repos.", required = false)
            String repo) {
        boolean scoped = StringUtils.hasText(repo);
        String sql = """
                SELECT metadata->>'repo' AS repo, metadata->>'path' AS path,
                       metadata->>'symbol' AS symbol, metadata->>'type' AS type, content
                FROM vector_store
                WHERE metadata->>'symbol' ILIKE :pattern
                %s
                LIMIT 5
                """.formatted(scoped ? "AND metadata->>'repo' = :repo" : "");
        JdbcClient.StatementSpec statement = jdbcClient.sql(sql).param("pattern", "%" + name + "%");
        if (scoped) {
            statement = statement.param("repo", repo);
        }
        List<SearchResult> results = new ArrayList<>(statement
                .query((rs, rowNum) -> new SearchResult(
                        rs.getString("repo"), rs.getString("path"), rs.getString("symbol"),
                        rs.getString("type"), 1.0, rs.getString("content")))
                .list());

        // Semantic search as a supplement: catches concepts that don't map to one exact symbol
        // (e.g. "double-entry bookkeeping") and surfaces related context around an exact match.
        results.addAll(searchCode(name, repo));
        return results;
    }

    public record RepoSummary(String repo, long files, long chunks) {
    }

    public record SearchResult(String repo, String path, String symbol, String type, double score, String content) {
    }

    public record FileChunk(String symbol, String type, String content) {
    }

    public record StructureEntry(String repo, String path, String symbol, String type) {
    }
}
