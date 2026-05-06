package p1.component.gamer.loop;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 活跃游戏会话注册表。
 * <p>
 * 该组件只保存当前进程内的循环会话状态，不负责持久化。
 */
@Component
@Slf4j
public class ActiveGameRegistry {

    /**
     * key = gameName:sessionId。
     */
    private final Map<String, ActiveGameSession> sessions = new ConcurrentHashMap<>();

    /**
     * 构建注册表内部使用的会话 key。
     *
     * @param gameName  游戏名
     * @param sessionId 用户侧会话 id
     * @return 唯一会话 key
     */
    private static String key(String gameName, String sessionId) {
        return gameName + ":" + sessionId;
    }

    /**
     * 注册一个新的运行中会话。
     *
     * @param gameName  游戏名
     * @param sessionId 用户侧会话 id
     * @return 新注册的活跃会话
     */
    public ActiveGameSession register(String gameName, String sessionId) {
        ActiveGameSession session = new ActiveGameSession(gameName, sessionId);
        ActiveGameSession existing = sessions.putIfAbsent(key(gameName, sessionId), session);
        if (existing != null) {
            // start 语义是重新开始循环，因此同名会话存在时用新会话覆盖旧会话。
            log.warn("[游戏循环] 会话已存在，覆盖: game={}, session={}", gameName, sessionId);
            sessions.put(key(gameName, sessionId), session);
        }
        log.info("[游戏循环] 会话已注册: game={}, session={}", gameName, sessionId);
        return session;
    }

    /**
     * 注销一个会话。
     *
     * @param gameName  游戏名
     * @param sessionId 用户侧会话 id
     */
    public void unregister(String gameName, String sessionId) {
        ActiveGameSession removed = sessions.remove(key(gameName, sessionId));
        if (removed != null) {
            removed.setState(ActiveGameSession.State.STOPPED);
            log.info("[游戏循环] 会话已注销: game={}, session={}", gameName, sessionId);
        }
    }

    /**
     * 查询一个会话。
     *
     * @param gameName  游戏名
     * @param sessionId 用户侧会话 id
     * @return 会话对象；不存在时返回 null
     */
    public ActiveGameSession get(String gameName, String sessionId) {
        return sessions.get(key(gameName, sessionId));
    }

    /**
     * 列出所有运行中的会话。
     *
     * @return 当前状态为 RUNNING 的会话集合
     */
    public Collection<ActiveGameSession> listRunning() {
        return sessions.values().stream()
                .filter(session -> session.getState() == ActiveGameSession.State.RUNNING)
                .toList();
    }

    /**
     * 列出所有会话。
     *
     * @return 当前注册表中的全部会话集合
     */
    public Collection<ActiveGameSession> listAll() {
        return sessions.values();
    }
}
