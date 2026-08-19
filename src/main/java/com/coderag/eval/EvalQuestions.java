package com.coderag.eval;

import java.util.List;

/**
 * ~20 fixed questions spanning every repo indexed for this project, grounded in real classes
 * and modules (verified against the actual indexed content, not guessed). Not a golden set in
 * the ML-benchmark sense - just a fixed regression check that retrieval-plus-synthesis still
 * gets the right answer as the indexer or the tools change.
 */
public final class EvalQuestions {

    public static final List<EvalQuestion> ALL = List.of(
            // pix-payment-gateway
            new EvalQuestion("pix-1",
                    "How does the gateway avoid creating a duplicate transaction when the same "
                            + "request is retried with the same idempotency key?",
                    "pix-payment-gateway", "idempot", "unique"),
            new EvalQuestion("pix-2",
                    "What pattern does the gateway use to publish transaction-created events to "
                            + "Kafka reliably, without losing an event if the app crashes right "
                            + "after committing the transaction?",
                    "pix-payment-gateway", "outbox"),
            new EvalQuestion("pix-3",
                    "How does the ledger worker avoid posting a duplicate ledger entry if Kafka "
                            + "redelivers the same transaction-created message?",
                    "pix-payment-gateway", "unique", "constraint"),
            new EvalQuestion("pix-4",
                    "What does the gateway's ledger posting logic guarantee about every "
                            + "transaction - does it post to just one account or two?",
                    "pix-payment-gateway", "double"),

            // design-patterns-project
            new EvalQuestion("dp-1",
                    "Which design pattern routes a Pix transaction through AML, KYC, fraud, "
                            + "compliance and limit checks in sequence, in the applied example?",
                    "design-patterns-project", "chain of responsibility"),
            new EvalQuestion("dp-2",
                    "Which class implements the Observer pattern's applied example to send "
                            + "transaction status updates over a webhook?",
                    "design-patterns-project", "webhooknotifierobserver"),
            new EvalQuestion("dp-3",
                    "In the Strategy pattern's applied example, what three payment methods each "
                            + "get their own fee-calculation strategy?",
                    "design-patterns-project", "pix", "ted", "boleto"),
            new EvalQuestion("dp-4",
                    "Which pattern's applied example models a transaction moving through "
                            + "Pending, Processing, Settled and Failed states?",
                    "design-patterns-project", "state"),
            new EvalQuestion("dp-5",
                    "What real-world problem does the Adapter pattern's applied example solve?",
                    "design-patterns-project", "mainframe"),

            // data-structures-project
            new EvalQuestion("ds-1",
                    "Which module implements Dijkstra's shortest-path algorithm?",
                    "data-structures-project", "dijkstra"),
            new EvalQuestion("ds-2",
                    "What probabilistic data structure does the hashing/bloom-filter module "
                            + "implement, and what is it typically used for?",
                    "data-structures-project", "bloom"),
            new EvalQuestion("ds-3",
                    "Which tree module implements a self-balancing binary search tree that "
                            + "keeps operations at O(log n) by rebalancing on insert/delete?",
                    "data-structures-project", "avl"),
            new EvalQuestion("ds-4",
                    "What does the graphs/union-find module implement?",
                    "data-structures-project", "union", "find"),

            // algorithms-project
            new EvalQuestion("algo-1",
                    "Which sorting algorithm has an average case of O(n log n) but a worst case "
                            + "of O(n^2)?",
                    "algorithms-project", "quick"),
            new EvalQuestion("algo-2",
                    "Which greedy algorithm module builds an optimal prefix-free binary "
                            + "encoding from symbol frequencies?",
                    "algorithms-project", "huffman"),
            new EvalQuestion("algo-3",
                    "Which dynamic programming module solves the 0/1 knapsack problem?",
                    "algorithms-project", "knapsack"),
            new EvalQuestion("algo-4",
                    "What classic problem does the backtracking n-queens module solve?",
                    "algorithms-project", "queens"),
            new EvalQuestion("algo-5",
                    "Which string-matching algorithm avoids re-scanning already-matched "
                            + "characters in the text by precomputing a failure function?",
                    "algorithms-project", "knuth", "morris", "pratt"),

            // codebase-rag-mcp (self-referential)
            new EvalQuestion("rag-1",
                    "How does this codebase RAG indexer split a .java file into indexable "
                            + "chunks?",
                    "codebase-rag-mcp", "javaparser", "class"),
            new EvalQuestion("rag-2",
                    "Do this MCP server's tools call an LLM themselves to answer a question, or "
                            + "do they only retrieve data?",
                    "codebase-rag-mcp", "retriev"));

    private EvalQuestions() {
    }
}
