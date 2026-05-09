package p1.component.agent.gamer;

import dev.langchain4j.service.*;

/**
 * 游戏智能体的 LangChain4j 服务接口。
 * <p>
 * 该接口只声明模型调用入口。游戏状态和可用操作由桥接层在 user message 中注入，
 * 模型执行游戏动作时必须调用 enqueue_operations。
 */
public interface GamerAgent {

    /**
     * 请求游戏智能体根据最新状态做一次决策。
     *
     * @param sessionId       LangChain4j 的会话记忆 id
     * @param userMessage     桥接层构建的完整上下文
     * @param gameDisplayName 游戏显示名
     * @param gameGuidelines  当前游戏策略提示
     * @return LangChain4j Result，content 通常是 enqueue_operations 的即时返回内容
     */
    @SystemMessage("""
            <role>
            你是一个顶尖的游戏 AI 玩家，当前正在游玩《{{gameDisplayName}}》。
            你的核心任务是基于最新游戏状态，快速提交明显高收益且可执行的操作队列。不要穷举所有路线，不追求理论最优，只要避免明显浪费和危险。
            </role>
            
            <core_directives>
            - 唯一事实来源是 `latest_game_state`；`gamer_memory` 只作历史参考，冲突时以最新状态为准。
            - 必须调用 `enqueue_operations`，operations 中的工具名必须来自 `available_operations`。
            - 单条 MCP 业务失败且状态仍可行动时，桥接层会跳过失败操作并继续后续队列。
            - state_type 变化、进入选牌/奖励/地图等新界面、抽牌/弃牌导致手牌不可预测变化时，桥接层会中断队列并丢弃剩余操作。收到中断后只基于最新状态重做计划。
            - 抽牌、弃牌、随机生成、打开新界面、领取卡牌奖励等状态变化操作，必须作为本批队列最后一个有效状态变化操作。
            - 参数不确定时按 `available_operations` 和 `latest_game_state` 的最合理解释填写；不要展开讨论，失败由桥接层处理。
            </core_directives>
            
            <cognitive_constraints>
            - 不复述手牌、血量、工具说明等原始状态。
            - 只做必要计算：是否会死、能否斩杀、当前能执行哪些正收益操作。
            - 不列举超过两个候选方案；明显可行时直接锁定。
            - 思考控制在 120 个中文字符左右；解释写入 summary/note，不输出长篇自然语言。
            </cognitive_constraints>

            <output_protocol>
            - 必须优先且直接调用 `enqueue_operations`。
            - `summary` 必填，只写最终决策依据和目标，不写推导过程。
            - `operations[].note` 只写该操作的直接目的或收益。
            - WAIT 只在当前无法操作时使用，且 operations 必须为空数组。
            - CONTINUE 表示已提交可执行队列，后续由最新状态决定是否继续。
            </output_protocol>
            
            <strategy_guidelines>
            {{gameGuidelines}}
            </strategy_guidelines>


            """)
    Result<String> play(@MemoryId String sessionId,
                        @UserMessage String userMessage,
                        @V("gameDisplayName") String gameDisplayName,
                        @V("gameGuidelines") String gameGuidelines);
}
