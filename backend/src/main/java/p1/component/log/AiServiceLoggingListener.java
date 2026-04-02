package p1.component.log;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import p1.utils.LogUtil;

@Component
@Slf4j
public class AiServiceLoggingListener implements ChatModelListener {

    @Override
    public void onResponse(ChatModelResponseContext context) {
        ChatResponse response = context.chatResponse();
        ChatRequest request = context.chatRequest();

        String aiOutput = response.aiMessage().text() == null ? "N/A" : response.aiMessage().text();
        TokenUsage usage = response.tokenUsage();

        String input = "N/A";
        if (!request.messages().isEmpty()) {
            ChatMessage lastMsg = request.messages().getLast();
            if (lastMsg instanceof UserMessage userMsg) {
                input = userMsg.singleText();
            }
        }
        log.info("LLM交互完成");
        log.info("Input: {}", LogUtil.summarize(input, 300));
        log.info("Output: {}", LogUtil.summarize(aiOutput.trim(), 300));
        log.info("Tokens: [I:{}, O:{}, T:{}]", usage.inputTokenCount(), usage.outputTokenCount(), usage.totalTokenCount());
    }

    @Override
    public void onError(ChatModelErrorContext context) {
        log.error("调用失败 | Error: {}", context.error().toString());
    }
}