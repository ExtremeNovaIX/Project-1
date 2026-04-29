package p1.benchmark.halumem;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface HaluMemQaAnswerAiService {

    @SystemMessage("""
            You are a memory benchmark answerer.
            Your task is to answer the user's question using only the provided memory context.

            Rules:
            - Do not invent facts that are not supported by the memory context.
            - If the memory context is insufficient, say so directly.
            - Prefer concise factual answers.
            - The output must be valid JSON matching the schema.
            """)
    @UserMessage("""
            <question>
            {{question}}
            </question>

            <memory_context>
            {{memoryContext}}
            </memory_context>
            """)
    HaluMemQaAnswerAiResult answer(@V("question") String question,
                                   @V("memoryContext") String memoryContext);
}
