package p1.component.log;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.ansi.AnsiColor;
import org.springframework.boot.ansi.AnsiOutput;
import org.springframework.boot.ansi.AnsiStyle;
import org.springframework.stereotype.Component;
import p1.mdc.ChatSessionMetrics;

import static p1.utils.SessionUtil.normalizeSessionId;

@Component
@Slf4j
@RequiredArgsConstructor
public class AssistantLoggingListener implements ChatModelListener {

    private final ChatSessionMetrics chatSessionMetrics;

    @Override
    public void onResponse(ChatModelResponseContext context) {
        ChatResponse response = context.chatResponse();
        ChatRequest request = context.chatRequest();
        TokenUsage usage = response.tokenUsage();

        String id = normalizeSessionId(MDC.get("sessionId"));
        int currentRound = resolveCurrentRound(id);
        ChatSessionMetrics.TokenSnapshot tokenTotals = chatSessionMetrics.addAndGetTokenTotals(id, usage);

        String aiOutput = response.aiMessage().text() == null ? "N/A" : response.aiMessage().text();
        long inputTokens = usage == null ? 0L : usage.inputTokenCount();
        long outputTokens = usage == null ? 0L : usage.outputTokenCount();
        long totalTokens = usage == null ? 0L : usage.totalTokenCount();

        String input = "N/A";
        if (!request.messages().isEmpty()) {
            ChatMessage lastMsg = request.messages().getLast();
            if (lastMsg instanceof UserMessage userMsg) {
                input = userMsg.singleText();
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n")
                .append(AnsiOutput.toString(AnsiColor.BRIGHT_CYAN, AnsiStyle.BOLD, ">>> [前端AI交互流程]", AnsiStyle.NORMAL)).append("\n")
                .append(AnsiOutput.toString(AnsiColor.WHITE, "[SessionId]: ", AnsiColor.DEFAULT, id)).append("\n")
                .append(AnsiOutput.toString(AnsiColor.WHITE, "[当前对话轮数]: ", AnsiColor.DEFAULT, currentRound)).append("\n")
                .append(AnsiOutput.toString(AnsiColor.CYAN, "[请求]: " + input)).append("\n")
                .append(AnsiOutput.toString(AnsiColor.BRIGHT_WHITE, "[输出]: " + aiOutput.trim())).append("\n")
                .append(AnsiOutput.toString(AnsiColor.WHITE, "[本次调用 Tokens]: ",
                        AnsiColor.BRIGHT_YELLOW, "[I:", inputTokens, " O:", outputTokens, " T:", totalTokens, "]",
                        AnsiColor.DEFAULT)).append("\n")
                .append(AnsiOutput.toString(AnsiColor.WHITE, "[Session Tokens]: ",
                        AnsiColor.BRIGHT_YELLOW, "[I:", tokenTotals.input(), " O:", tokenTotals.output(), " T:", tokenTotals.total(), "]",
                        AnsiColor.DEFAULT)).append("\n")
                .append(AnsiOutput.toString(AnsiColor.BRIGHT_CYAN, "=================================================================================================="));
        log.info(sb.toString());
    }

    @Override
    public void onError(ChatModelErrorContext context) {
        log.error("调用失败 | Error: {}", context.error().toString());
    }

    private int resolveCurrentRound(String sessionId) {
        String roundFromMdc = MDC.get("chatRound");
        if (roundFromMdc != null && !roundFromMdc.isBlank()) {
            try {
                return Integer.parseInt(roundFromMdc);
            } catch (NumberFormatException ignored) {
            }
        }
        return chatSessionMetrics.getCurrentRound(sessionId);
    }
}
