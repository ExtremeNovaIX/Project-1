package p1.component.agent.tools;

import dev.langchain4j.agent.tool.Tool;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import static p1.utils.SessionUtil.normalizeSessionId;

@Component
@RequiredArgsConstructor
@Slf4j
public class BackendAssistantGatewayTool {

    private final ToolCallingPlannerService toolCallingPlannerService;
    private final ToolCallResultStore toolCallResultStore;

    @Tool(name = "askBackendAssistant", value = {
            """
                    使用自然语言把需要查事实或调用工具的任务委托给后端助手。
                    请输入一个具体、明确、可执行的后端任务。
                    当前可用的后端能力包括记忆检索，以及未来需要 agent 参与的工具路由。
                    """
    })
    public String askBackendAssistant(@NonNull String request) {
        if (!StringUtils.hasText(request)) {
            return "后端助手请求不能为空。";
        }

        String sessionId = normalizeSessionId(MDC.get("sessionId"));
        log.info("[工具路由] 转发请求给后端助手 sessionId={} request={}", sessionId, request);

        try (ToolCallResultStore.SessionScope ignored = toolCallResultStore.openSession()) {
            ToolRoutingResult routingResult = toolCallingPlannerService.handleRequest(request.trim());
            String visibleResult = routingResult.renderVisibleResults();

            log.info("[工具路由] 后端助手执行完成 sessionId={} taskCompleted={} visibleCount={}",
                    sessionId,
                    routingResult.taskCompleted(),
                    routingResult.visibleToolCalls().size());
            return visibleResult;
        }
    }
}
