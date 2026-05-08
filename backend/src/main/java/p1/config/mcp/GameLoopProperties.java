package p1.config.mcp;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 游戏循环配置。
 * <p>
 * 慢轮询负责兜底唤醒；桥接层发现状态变化导致队列中断时，循环层会在同一轮内立即重规划。
 */
@Data
@Component
@ConfigurationProperties(prefix = "gamer.game-loop")
public class GameLoopProperties {
    /**
     * 慢轮询间隔（毫秒），检查是否轮到 AI 行动
     */
    private long pollIntervalMs = 3000;
    /**
     * 单次慢轮询 tick 内最多允许的即时重规划次数，防止状态持续抖动导致死循环。
     */
    private int maxImmediateReplans = 5;
}
