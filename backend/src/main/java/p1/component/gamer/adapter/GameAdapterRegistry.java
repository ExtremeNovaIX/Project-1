package p1.component.gamer.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import p1.config.mcp.MCPProperties;

import java.util.List;

/**
 * 游戏适配器注册表。
 * <p>
 * Spring 会注入所有 GameAdapter 实现，本类负责按游戏配置选择具体适配器。
 */
@Component
@RequiredArgsConstructor
public class GameAdapterRegistry {

    private final List<GameAdapter> adapters;

    /**
     * 根据游戏配置选择适配器。
     *
     * @param gameName 游戏名
     * @param config   游戏 MCP 配置
     * @return 匹配的游戏适配器；没有专用适配器时回退到 default
     */
    public GameAdapter getAdapter(String gameName, MCPProperties.GameMCPConfig config) {
        // 先按配置中的 adapter id 查找专用适配器。
        return adapters.stream()
                .filter(adapter -> adapter.supports(gameName, config))
                .findFirst()
                .orElseGet(() -> adapters.stream()
                        // 没有专用适配器时使用默认适配器，保证普通 MCP 游戏仍可运行。
                        .filter(adapter -> "default".equals(adapter.id()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("未找到 default GameAdapter")));
    }
}
