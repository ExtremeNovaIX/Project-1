package p1.benchmark.halumem;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface HaluMemQaJudgeAiService {

    @SystemMessage("""
            You are evaluating answers for a memory benchmark.

            Decide whether the system answer correctly answers the question relative to the gold answer.
            Use these verdicts:
            - CORRECT: materially equivalent to the gold answer
            - PARTIAL: partly correct but incomplete
            - WRONG: incorrect, contradicted by the gold answer, or misses the key fact
            - HALLUCINATED: contains claims that are not supported by the system-retrieved memory context
            - UNKNOWN: explicitly says the information is unavailable or uncertain

            Score guidance:
            - CORRECT: 1.0
            - PARTIAL: around 0.5
            - WRONG: around 0.0
            - HALLUCINATED: 0.0
            - UNKNOWN: 0.0 unless the gold answer is also unknown

            Evaluation policy:
            - Use the gold answer as the primary correctness anchor.
            - Use the system-retrieved memory context to decide whether the system introduced unsupported details.
            - Treat the benchmark reference context as auxiliary background only; it may be empty or less specific than the retrieved memory.
            - If the answer is supported by the retrieved memory but still disagrees with the gold answer, prefer WRONG over HALLUCINATED.

            The output must be valid JSON matching the schema.
            """)
    @UserMessage("""
            <question>
            {{question}}
            </question>

            <gold_answer>
            {{groundTruth}}
            </gold_answer>

            <reference_context>
            {{referenceContext}}
            </reference_context>

            <retrieved_memory_context>
            {{retrievedContext}}
            </retrieved_memory_context>

            <system_answer>
            {{systemAnswer}}
            </system_answer>
            """)
    HaluMemQaJudgeAiVerdict judge(@V("question") String question,
                                  @V("groundTruth") String groundTruth,
                                  @V("referenceContext") String referenceContext,
                                  @V("retrievedContext") String retrievedContext,
                                  @V("systemAnswer") String systemAnswer);
}
