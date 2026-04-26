package p1.component.agent.tools;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BackendAssistantGatewayToolTest {

    @Test
    void shouldRenderOnlyVisibleToolResultsInOriginalOrder() {
        ToolCallingPlannerService toolCallingPlannerService = mock(ToolCallingPlannerService.class);
        ToolCallResultStore toolCallResultStore = new ToolCallResultStore();
        BackendAssistantGatewayTool gatewayTool =
                new BackendAssistantGatewayTool(toolCallingPlannerService, toolCallResultStore);

        ToolRoutingResult routingResult = new ToolRoutingResult(
                true,
                "enough",
                List.of(
                        new ToolCallRecord("tool-call-1", "searchLongTermMemory", "{\"query\":\"旅行计划-1\"}", "原始结果一", false),
                        new ToolCallRecord("tool-call-2", "searchLongTermMemory", "{\"query\":\"旅行计划-2\"}", "原始结果二", false)
                ),
                List.of("tool-call-2", "tool-call-1")
        );
        when(toolCallingPlannerService.handleRequest("查一下我之前提过的旅行计划"))
                .thenReturn(routingResult);

        String result = gatewayTool.askBackendAssistant("查一下我之前提过的旅行计划");

        assertEquals("原始结果一\n\n原始结果二", result);
    }
}
