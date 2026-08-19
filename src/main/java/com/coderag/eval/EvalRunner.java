package com.coderag.eval;

import com.coderag.tools.RagTools;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.template.NoOpTemplateRenderer;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * CLI entry point for the evaluation harness: {@code java -jar app.jar --eval}. Runs every
 * question in {@link EvalQuestions}, retrieving context the same way search_code would and
 * asking Claude to answer from that context alone, then scores the answer by keyword presence.
 * A no-op when {@code --eval} isn't present, so the same jar still falls through to normal
 * MCP-server startup otherwise.
 *
 * <p>This is the one place in the whole app that calls an LLM - by design, everywhere else
 * (the MCP tools) is pure retrieval, and the calling MCP client supplies the LLM. Here, the
 * harness stands in for that client so the pipeline can be checked end to end.
 */
@Component
public class EvalRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(EvalRunner.class);

    private static final String SYSTEM_PROMPT = """
            You are answering a question about a codebase using ONLY the context chunks given
            below. Do not use outside knowledge. Be concise - a few sentences is enough.""";

    private final RagTools ragTools;
    private final AnthropicChatModel chatModel;
    private final ConfigurableApplicationContext context;

    public EvalRunner(RagTools ragTools, AnthropicChatModel chatModel, ConfigurableApplicationContext context) {
        this.ragTools = ragTools;
        this.chatModel = chatModel;
        this.context = context;
    }

    @Override
    public void run(String... args) {
        if (indexOf(args, "--eval") == -1) {
            return;
        }

        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultTemplateRenderer(new NoOpTemplateRenderer())
                .build();

        int passed = 0;
        double totalScore = 0.0;
        for (EvalQuestion q : EvalQuestions.ALL) {
            List<RagTools.SearchResult> retrieved = ragTools.searchCode(q.question(), q.repo());
            String contextBlock = retrieved.stream()
                    .map(r -> "### %s (%s)\n%s".formatted(r.path(), r.symbol(), r.content()))
                    .reduce((a, b) -> a + "\n\n" + b)
                    .orElse("(no context retrieved)");

            String answer = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user("Context:\n" + contextBlock + "\n\nQuestion: " + q.question())
                    .call()
                    .content();

            String answerLower = answer.toLowerCase(Locale.ROOT);
            long hits = q.expectedKeywords().stream()
                    .filter(k -> answerLower.contains(k.toLowerCase(Locale.ROOT)))
                    .count();
            double score = (double) hits / q.expectedKeywords().size();
            boolean pass = score == 1.0;
            if (pass) {
                passed++;
            }
            totalScore += score;

            log.info("[{}] {} (score {}/{}) - {}",
                    q.id(), pass ? "PASS" : "FAIL", hits, q.expectedKeywords().size(), q.question());
            if (!pass) {
                log.info("  expected keywords: {}", q.expectedKeywords());
                log.info("  answer: {}", answer.replace("\n", " "));
            }
        }

        int total = EvalQuestions.ALL.size();
        log.info("Done: {}/{} passed, average score {}", passed, total,
                "%.2f".formatted(totalScore / total));
        int exitCode = passed == total ? 0 : 1;
        System.exit(SpringApplication.exit(context, () -> exitCode));
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
