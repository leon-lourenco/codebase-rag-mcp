package com.coderag.eval;

import java.util.List;

/**
 * One fixed evaluation question. {@code expectedKeywords} are matched case-insensitively as
 * substrings of the model's answer - a deliberately cheap check, not an LLM-judge, since the
 * goal is a fast regression signal ("did retrieval + the model still get this right"), not a
 * publishable benchmark.
 */
public record EvalQuestion(String id, String question, String repo, List<String> expectedKeywords) {

    public EvalQuestion(String id, String question, String repo, String... expectedKeywords) {
        this(id, question, repo, List.of(expectedKeywords));
    }
}
