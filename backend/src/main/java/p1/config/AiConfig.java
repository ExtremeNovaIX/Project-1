package p1.config;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.service.AiServices;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import p1.benchmark.halumem.HaluMemMemoryJudgeAiService;
import p1.benchmark.halumem.HaluMemQaAnswerAiService;
import p1.benchmark.halumem.HaluMemQaJudgeAiService;
import p1.component.agent.factory.ChatModelFactory;
import p1.component.agent.factory.EmbeddingModelFactory;
import p1.component.agent.gamer.memory.GamerMemoryCompressorAiService;
import p1.component.agent.memory.*;
import p1.component.agent.rp.CallSolverTool;
import p1.component.agent.rp.context.RpRequestTimeAppender;
import p1.component.agent.rp.core.RpAgent;
import p1.component.agent.task.checker.TaskCheckerAiService;
import p1.component.log.AiServiceLoggingListener;
import p1.component.log.AssistantLoggingListener;
import p1.config.prop.AssistantProperties;
import p1.config.prop.LockProperties;
import p1.service.ChatLogRepository;
import p1.service.markdown.RawMdService;
import p1.utils.SessionUtil;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@AllArgsConstructor
@Slf4j
public class AiConfig {

    private final AssistantProperties props;
    private final Map<String, ArchivableChatMemory> memoryCache = new ConcurrentHashMap<>();
    private final AiServiceLoggingListener aiServiceLoggingListener;
    private final AssistantLoggingListener assistantLoggingListener;
    private final ChatModelFactory chatModelFactory;
    private final EmbeddingModelFactory embeddingModelFactory;

    @Bean
    public ChatModel chatLanguageModel() {
        AssistantProperties.ChatModelConfig chatModelConfig = props.activeChatModel();
        return chatModelFactory.buildChatModel(chatModelConfig, assistantLoggingListener, null);
    }

    @Bean(name = "rpChatModel")
    public ChatModel rpChatModel() {
        AssistantProperties.ChatModelConfig chatModelConfig = props.activeChatModel();
        return chatModelFactory.buildChatModel(chatModelConfig, assistantLoggingListener, 0.8);
    }

    @Bean(name = "backendChatModel")
    public ChatModel backendChatModel() {
        AssistantProperties.ChatModelConfig config = props.activeChatModel();
        return chatModelFactory.buildChatModel(config, aiServiceLoggingListener, 0.0);
    }

    @Bean(name = "supervisorChatModel")
    public ChatModel supervisorChatModel() {
        AssistantProperties.ChatModelConfig config = props.activeChatModel();
        return chatModelFactory.buildChatModel(config, aiServiceLoggingListener, 0.0, true);
    }

    @Bean(name = "gamerChatModel")
    public ChatModel gamerChatModel() {
        AssistantProperties.ChatModelConfig config = gamerChatModelConfig();
        return chatModelFactory.buildChatModel(config, aiServiceLoggingListener, 0.3);
    }

    @Bean(name = "gamerStreamingChatModel")
    public StreamingChatModel gamerStreamingChatModel() {
        AssistantProperties.ChatModelConfig config = gamerChatModelConfig();
        return chatModelFactory.buildStreamingChatModel(config, aiServiceLoggingListener, 0.3);
    }

    /**
     * 构建 gamer 专用模型配置。
     * <p>
     * gamer 决策要求低延迟，且操作 JSON 会从普通 response 流中解析，
     * 因此这里关闭 reasoning_content / thinking，避免模型把大量推理写入隐藏通道。
     *
     * @return 禁用 thinking 的 gamer 模型配置副本
     */
    private AssistantProperties.ChatModelConfig gamerChatModelConfig() {
        AssistantProperties.ChatModelConfig source = props.activeChatModel();
        AssistantProperties.ChatModelConfig copy = copyChatModelConfig(source);
        copy.setReturnThinking(false);
        copy.setSendThinking(false);
        copy.setReasoningEffort(null);
        copy.setThinkingType("disabled");
        return copy;
    }

    /**
     * 复制聊天模型配置，避免修改全局 activeChatModel 影响 RP 或后台任务。
     *
     * @param source 当前激活的全局模型配置
     * @return 可安全修改的配置副本
     */
    private AssistantProperties.ChatModelConfig copyChatModelConfig(AssistantProperties.ChatModelConfig source) {
        AssistantProperties.ChatModelConfig copy = new AssistantProperties.ChatModelConfig();
        copy.setApiKey(source.getApiKey());
        copy.setBaseUrl(source.getBaseUrl());
        copy.setModelName(source.getModelName());
        copy.setTimeoutSeconds(source.getTimeoutSeconds());
        copy.setLogEnabled(source.isLogEnabled());
        copy.setPrompt(source.getPrompt());
        copy.setReturnThinking(source.isReturnThinking());
        copy.setSendThinking(source.isSendThinking());
        copy.setReasoningEffort(source.getReasoningEffort());
        copy.setThinkingType(source.getThinkingType());
        return copy;
    }

    @Bean
    public RpAgent rpAgent(@Qualifier("rpChatModel") ChatModel chatModel,
                           ChatMemoryProvider chatMemoryProvider,
                           RpRequestTimeAppender rpRequestTimeAppender,
                           CallSolverTool callSolverTool) {
        return AiServices.builder(RpAgent.class)
                .chatModel(chatModel)
                .chatMemoryProvider(chatMemoryProvider)
                .chatRequestTransformer(rpRequestTimeAppender::augment)
                .tools(callSolverTool)
                .build();
    }

    @Bean
    public TaskCheckerAiService taskSupervisorCheckerAiService(@Qualifier("supervisorChatModel") ChatModel supervisorChatModel) {
        return AiServices.builder(TaskCheckerAiService.class)
                .chatModel(supervisorChatModel)
                .build();
    }

    @Bean
    public FactExtractionAiService factExtractionAiService(@Qualifier("backendChatModel") ChatModel backendChatModel) {
        return AiServices.builder(FactExtractionAiService.class)
                .chatModel(backendChatModel)
                .build();
    }

    @Bean
    public FactEvaluatorAiService factScoringAiService(@Qualifier("backendChatModel") ChatModel backendChatModel) {
        return AiServices.builder(FactEvaluatorAiService.class)
                .chatModel(backendChatModel)
                .build();
    }

    @Bean
    public GamerMemoryCompressorAiService gamerMemoryCompressorAiService(@Qualifier("backendChatModel") ChatModel backendChatModel) {
        return AiServices.builder(GamerMemoryCompressorAiService.class)
                .chatModel(backendChatModel)
                .build();
    }

    @Bean
    public HaluMemQaAnswerAiService haluMemQaAnswerAiService(@Qualifier("supervisorChatModel") ChatModel supervisorChatModel) {
        return AiServices.builder(HaluMemQaAnswerAiService.class)
                .chatModel(supervisorChatModel)
                .build();
    }

    @Bean
    public HaluMemMemoryJudgeAiService haluMemMemoryJudgeAiService(@Qualifier("supervisorChatModel") ChatModel supervisorChatModel) {
        return AiServices.builder(HaluMemMemoryJudgeAiService.class)
                .chatModel(supervisorChatModel)
                .build();
    }

    @Bean
    public HaluMemQaJudgeAiService haluMemQaJudgeAiService(@Qualifier("supervisorChatModel") ChatModel supervisorChatModel) {
        return AiServices.builder(HaluMemQaJudgeAiService.class)
                .chatModel(supervisorChatModel)
                .build();
    }

    @Bean
    public ChatMemoryProvider chatMemoryProvider(MemoryAsyncCompressor compressor,
                                                 ChatMemoryAppender dbAppender,
                                                 RawMdService rawMdService,
                                                 LockProperties lockProperties,
                                                 ChatLogRepository chatLogRepository) {
        return memoryId -> {
            String sessionId = SessionUtil.normalizeSessionId(memoryId.toString());
            return memoryCache.computeIfAbsent(sessionId,
                    id -> new ArchivableChatMemory(id, compressor, dbAppender, rawMdService, props, lockProperties, chatLogRepository)
            );
        };
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        return embeddingModelFactory.buildEmbeddingModel();
    }

    @Bean
    public ApplicationRunner aiModeStartupLogger() {
        return args -> {
            AssistantProperties.ChatModelConfig chatModel = props.activeChatModel();
            AssistantProperties.EmbeddingModelConfig embeddingModel = props.activeEmbeddingModel();
            log.info("LLM mode: {} | chat-model: {} @ {} | embedding-model: {} @ {}",
                    props.getMode(),
                    chatModel.getModelName(),
                    chatModel.getBaseUrl(),
                    embeddingModel.getModelName(),
                    embeddingModel.getBaseUrl());
        };
    }
}
