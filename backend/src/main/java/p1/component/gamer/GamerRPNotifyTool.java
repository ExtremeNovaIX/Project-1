package p1.component.gamer;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import p1.component.gamer.bridge.GamerRPBridge;

/**
 * 暴露给游戏智能体的通知工具。
 * <p>
 * 这是游戏智能体将可叙述行动传递给面向用户的角色扮演智能体的通道。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GamerRPNotifyTool {

    private final GamerRPBridge bridge;

    /**
     * 通知角色扮演智能体一个关键游戏事件。
     *
     * @param memoryId    LangChain4j 的会话记忆 id
     * @param description 关键游戏事件的事实描述
     * @return 工具调用结果文本
     */
    @Tool(name = "notifyRP", value = {"""
            当游戏发生值得向用户讲述的关键事件时调用此工具。
            例如：关键胜负、获得重要奖励、触发特殊事件、发生重大失误或出现剧情推进等。

            参数 description: 用中文描述发生了什么，这段描述会传递给角色扮演 AI 用于演绎。
            尽量生动地描述发生了什么，但保持事实准确。
            """})
    public String notifyRP(@ToolMemoryId String memoryId,
                           @P("关键游戏事件的事实描述") String description) {
        if (description == null || description.isBlank()) {
            return "事件描述不能为空。";
        }
        // notifyRP 只负责传递事实事件，具体演绎由后续角色扮演智能体完成。
        log.info("[游戏通知] 通知 RP 代理: {}", description.substring(0, Math.min(200, description.length())));
        bridge.enqueueGameEvent(memoryId, description);
        return "事件已传递给角色扮演 AI。";
    }
}
