package p1.json;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.internal.Json;
import lombok.extern.slf4j.Slf4j;
import p1.json.TolerantJsonFieldRepairer.RepairReport;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;

@Slf4j
public class TolerantJsonCodec implements Json.JsonCodec {

    private static final List<String> REPAIR_PACKAGE_PREFIXES = List.of(
            "p1.model",
            "p1.component.ai.service"
    );

    private final Object delegate;
    private final Method toJsonMethod;
    private final Method fromJsonClassMethod;
    private final Method fromJsonTypeMethod;
    private final ObjectMapper objectMapper;
    private final TolerantJsonFieldRepairer fieldRepairer;

    /**
     * 这里并不重写 LangChain4j 默认的 JSON codec，
     * 而是通过反射包一层默认的 JacksonJsonCodec。
     * 正常情况下完全沿用默认行为，只有默认反序列化失败时才进入修复流程。
     */
    public TolerantJsonCodec() {
        try {
            Class<?> codecClass = Class.forName("dev.langchain4j.internal.JacksonJsonCodec");
            Constructor<?> constructor = codecClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            this.delegate = constructor.newInstance();

            this.toJsonMethod = codecClass.getDeclaredMethod("toJson", Object.class);
            this.fromJsonClassMethod = codecClass.getDeclaredMethod("fromJson", String.class, Class.class);
            this.fromJsonTypeMethod = codecClass.getDeclaredMethod("fromJson", String.class, Type.class);
            Method objectMapperMethod = codecClass.getDeclaredMethod("getObjectMapper");

            this.toJsonMethod.setAccessible(true);
            this.fromJsonClassMethod.setAccessible(true);
            this.fromJsonTypeMethod.setAccessible(true);
            objectMapperMethod.setAccessible(true);

            this.objectMapper = (ObjectMapper) objectMapperMethod.invoke(delegate);
            this.fieldRepairer = new TolerantJsonFieldRepairer(objectMapper);
            log.info("[JSON修复] 已装配宽容 JsonCodec，将在默认反序列化失败时自动尝试字段修复");
        } catch (Exception e) {
            throw new IllegalStateException("failed to initialize tolerant json codec", e);
        }
    }

    @Override
    public String toJson(Object object) {
        try {
            return (String) toJsonMethod.invoke(delegate, object);
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialize json", e);
        }
    }

    @Override
    public <T> T fromJson(String json, Class<T> type) {
        JavaType targetType = objectMapper.constructType(type);
        try {
            @SuppressWarnings("unchecked")
            T result = (T) fromJsonClassMethod.invoke(delegate, json, type);
            return result;
        } catch (Exception e) {
            if (!shouldRepair(targetType)) {
                throw propagateOriginal(e);
            }
            return repairAndRead(json, targetType, e);
        }
    }

    @Override
    public <T> T fromJson(String json, Type type) {
        JavaType targetType = objectMapper.constructType(type);
        try {
            @SuppressWarnings("unchecked")
            T result = (T) fromJsonTypeMethod.invoke(delegate, json, type);
            return result;
        } catch (Exception e) {
            if (!shouldRepair(targetType)) {
                throw propagateOriginal(e);
            }
            return repairAndRead(json, targetType, e);
        }
    }

    private <T> T repairAndRead(String json, JavaType targetType, Exception originalException) {
        try {
            log.warn("[JSON修复] 默认反序列化失败，进入修复流程，targetType={}，reason={}",
                    targetType, rootMessage(originalException));
            JsonNode originalTree = objectMapper.readTree(json);
            RepairReport report = fieldRepairer.repair(originalTree, targetType);
            JsonNode repairedTree = report.repairedTree();
            T result = objectMapper.readerFor(targetType).readValue(repairedTree);

            if (report.changed()) {
                log.info("[JSON修复] 修复成功，targetType={}，修复项={}", targetType, report.describeChanges());
            } else {
                log.info("[JSON修复] 进入修复流程但没有可修复字段，targetType={}，将按原结构继续解析", targetType);
            }
            return result;
        } catch (Exception repairException) {
            log.error("[JSON修复] 修复失败，targetType={}，原始失败原因={}，修复失败原因={}",
                    targetType,
                    rootMessage(originalException),
                    rootMessage(repairException));
            originalException.addSuppressed(repairException);
            throw propagateOriginal(originalException);
        }
    }

    /**
     * 只对白名单包下的结构化 DTO 启用字段修复。
     * 这样可以避免全局 JSON codec 误影响普通字符串服务或其他不希望放宽约束的类型。
     */
    private boolean shouldRepair(JavaType targetType) {
        if (targetType == null) {
            return false;
        }
        if (isWhitelisted(targetType.getRawClass())) {
            return true;
        }
        if (targetType.hasContentType() && shouldRepair(targetType.getContentType())) {
            return true;
        }
        if (targetType.getKeyType() != null && shouldRepair(targetType.getKeyType())) {
            return true;
        }
        return false;
    }

    private boolean isWhitelisted(Class<?> rawClass) {
        if (rawClass == null) {
            return false;
        }
        String className = rawClass.getName();
        return REPAIR_PACKAGE_PREFIXES.stream().anyMatch(className::startsWith);
    }

    private RuntimeException propagateOriginal(Exception exception) {
        if (exception instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalStateException("failed to deserialize json", exception);
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getClass().getSimpleName() + ": " + String.valueOf(current.getMessage());
    }
}
