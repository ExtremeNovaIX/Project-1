package p1.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import p1.component.ai.assistant.TestAssistant;
import p1.config.prop.AssistantProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatTestSelfSummaryService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TestAssistant testAssistant;
    private final AssistantProperties assistantProperties;

    public synchronized void appendRoundSummary(String sessionId,
                                                int startRound,
                                                int endRound,
                                                List<String> ownMessages) {
        if (ownMessages == null || ownMessages.isEmpty()) {
            return;
        }

        String summary = normalize(testAssistant.summarizeOwnMessages(buildOwnMessagesPrompt(ownMessages)));
        if (summary.isBlank()) {
            return;
        }

        Path summaryPath = resolveSummaryPath(sessionId);
        try {
            Files.createDirectories(summaryPath.getParent());
            if (Files.notExists(summaryPath)) {
                Files.writeString(summaryPath, "# Chat Test Self Summary\n\n");
            }

            StringBuilder block = new StringBuilder();
            block.append("## ")
                    .append(LocalDateTime.now().format(TIME_FORMATTER))
                    .append(" | rounds ")
                    .append(startRound)
                    .append("-")
                    .append(endRound)
                    .append("\n\n")
                    .append("- session_id: `")
                    .append(sessionId)
                    .append("`\n")
                    .append("- rounds: `")
                    .append(startRound)
                    .append("-")
                    .append(endRound)
                    .append("`\n\n")
                    .append(summary)
                    .append("\n\n");

            Files.writeString(
                    summaryPath,
                    block.toString(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
            log.info("[Chat Test Self Summary] sessionId={} 已追加轮次 {} 到 {} 的总结：{}", sessionId, startRound, endRound, summary);
        } catch (IOException e) {
            throw new IllegalStateException("failed to append chat test self summary, sessionId=" + sessionId, e);
        }
    }

    Path resolveSummaryPath(String sessionId) {
        return Path.of(assistantProperties.getTestChat().getSelfSummaryPath(), sessionId, "self-summary.md");
    }

    private String buildOwnMessagesPrompt(List<String> ownMessages) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < ownMessages.size(); i++) {
            builder.append(i + 1)
                    .append(". ")
                    .append(normalize(ownMessages.get(i)))
                    .append("\n");
        }
        return builder.toString().strip();
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.strip()
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }
}
