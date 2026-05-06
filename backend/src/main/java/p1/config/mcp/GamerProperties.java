package p1.config.mcp;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Gamer agent 通用配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "gamer")
public class GamerProperties {
    /**
     * 请求未提供 gameName 时使用的默认游戏。
     * 为空时，如果当前只有一个已启用 MCP 游戏，会自动选择该游戏。
     */
    private String defaultGameName;
    /**
     * 请求未提供 sessionId 时使用的默认会话。
     */
    private String defaultSessionId = "default";
    /**
     * MCP 客户端上报给 MCP server 的客户端名称。
     */
    private String clientName = "ArcLight-GameAgent";
    /**
     * MCP 客户端版本。
     */
    private String clientVersion = "1.0";
}
