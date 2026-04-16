package p1.json;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.type.TypeFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class TolerantJsonFieldRepairer {

    private final ObjectMapper objectMapper;

    public TolerantJsonFieldRepairer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 通用字段修复入口，处理字段名轻微漂移的情况
     */
    public RepairReport repair(JsonNode root, JavaType targetType) {
        List<RepairChange> changes = new ArrayList<>();
        JsonNode repairedTree = repairNode(root, targetType, "$", changes);
        return new RepairReport(repairedTree, changes);
    }

    private JsonNode repairNode(JsonNode root, JavaType targetType, String path, List<RepairChange> changes) {
        if (root == null || root.isNull() || targetType == null) {
            return root;
        }
        if (root.isArray()) {
            return repairArray((ArrayNode) root, targetType, path, changes);
        }
        if (root.isObject()) {
            return repairObject((ObjectNode) root, targetType, path, changes);
        }
        return root;
    }

    private JsonNode repairArray(ArrayNode arrayNode, JavaType targetType, String path, List<RepairChange> changes) {
        JavaType contentType = targetType.hasContentType() ? targetType.getContentType() : TypeFactory.unknownType();
        ArrayNode repaired = JsonNodeFactory.instance.arrayNode();
        int index = 0;
        for (JsonNode item : arrayNode) {
            repaired.add(repairNode(item, contentType, path + "[" + index + "]", changes));
            index++;
        }
        return repaired;
    }

    private JsonNode repairObject(ObjectNode objectNode, JavaType targetType, String path, List<RepairChange> changes) {
        if (!isBeanLike(targetType)) {
            return objectNode.deepCopy();
        }

        Map<String, BeanProperty> properties = inspectProperties(targetType);
        if (properties.isEmpty()) {
            return objectNode.deepCopy();
        }

        ObjectNode repaired = JsonNodeFactory.instance.objectNode();
        Map<String, JsonNode> unknownFields = new LinkedHashMap<>();
        Map<String, String> claimedTargets = new HashMap<>();

        objectNode.fields().forEachRemaining(entry -> {
            String sourceName = entry.getKey();
            JsonNode sourceValue = entry.getValue();
            BeanProperty exactProperty = properties.get(sourceName);
            if (exactProperty != null) {
                repaired.set(sourceName, repairNode(sourceValue, exactProperty.type(), path + "." + sourceName, changes));
                claimedTargets.put(sourceName, sourceName);
            } else {
                unknownFields.put(sourceName, sourceValue);
            }
        });

        List<FieldMatch> matches = unknownFields.entrySet().stream()
                .map(entry -> bestMatch(entry.getKey(), properties, claimedTargets))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(FieldMatch::score))
                .toList();

        for (FieldMatch match : matches) {
            if (claimedTargets.containsKey(match.targetName())) {
                continue;
            }
            JsonNode sourceValue = unknownFields.get(match.sourceName());
            BeanProperty targetProperty = properties.get(match.targetName());
            repaired.set(match.targetName(), repairNode(sourceValue, targetProperty.type(), path + "." + match.targetName(), changes));
            claimedTargets.put(match.targetName(), match.sourceName());
            changes.add(new RepairChange(path, match.sourceName(), match.targetName(), match.score()));
        }

        return repaired;
    }

    private Map<String, BeanProperty> inspectProperties(JavaType targetType) {
        Map<String, BeanProperty> properties = new LinkedHashMap<>();
        Class<?> current = targetType.getRawClass();
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                String name = field.getName();
                if (properties.containsKey(name)) {
                    continue;
                }
                JavaType propertyType = objectMapper.getTypeFactory().constructType(field.getGenericType());
                properties.put(name, new BeanProperty(name, propertyType));
            }
            current = current.getSuperclass();
        }
        return properties;
    }

    private FieldMatch bestMatch(String sourceName,
                                 Map<String, BeanProperty> properties,
                                 Map<String, String> claimedTargets) {
        String normalizedSource = normalize(sourceName);
        if (normalizedSource.isEmpty()) {
            return null;
        }

        List<FieldMatch> candidates = new ArrayList<>();
        for (BeanProperty property : properties.values()) {
            if (claimedTargets.containsKey(property.name())) {
                continue;
            }
            String normalizedTarget = normalize(property.name());
            if (normalizedTarget.isEmpty()) {
                continue;
            }

            int score = similarityScore(normalizedSource, normalizedTarget);
            if (score <= allowedDistance(normalizedSource, normalizedTarget)) {
                candidates.add(new FieldMatch(sourceName, property.name(), score));
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        candidates.sort(Comparator.comparingInt(FieldMatch::score));
        FieldMatch best = candidates.getFirst();
        if (candidates.size() > 1 && candidates.get(1).score() == best.score()) {
            return null;
        }
        return best;
    }

    private boolean isBeanLike(JavaType targetType) {
        Class<?> rawClass = targetType.getRawClass();
        if (rawClass == null) {
            return false;
        }
        return !targetType.isPrimitive()
                && !targetType.isEnumType()
                && !targetType.isMapLikeType()
                && !targetType.isCollectionLikeType()
                && !rawClass.isArray()
                && !CharSequence.class.isAssignableFrom(rawClass)
                && !Number.class.isAssignableFrom(rawClass)
                && !Boolean.class.equals(rawClass)
                && !rawClass.getPackageName().startsWith("java.time");
    }

    private int allowedDistance(String source, String target) {
        int maxLength = Math.max(source.length(), target.length());
        if (maxLength <= 6) {
            return 1;
        }
        return Math.min(3, Math.max(1, maxLength / 5));
    }

    private int similarityScore(String source, String target) {
        if (source.equals(target)) {
            return 0;
        }
        return levenshteinDistance(source, target);
    }

    private int levenshteinDistance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];

        for (int j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }

        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(
                        Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + cost
                );
            }

            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }

    private String normalize(String name) {
        StringBuilder builder = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                builder.append(Character.toLowerCase(c));
            }
        }
        return builder.toString();
    }

    private record BeanProperty(String name, JavaType type) {
    }

    private record FieldMatch(String sourceName, String targetName, int score) {
    }

    public record RepairReport(JsonNode repairedTree, List<RepairChange> changes) {
        public boolean changed() {
            return !changes.isEmpty();
        }

        public String describeChanges() {
            return changes.stream()
                    .map(change -> change.path() + ": " + change.sourceField() + " -> " + change.targetField() + " (distance=" + change.distance() + ")")
                    .reduce((left, right) -> left + "; " + right)
                    .orElse("无");
        }
    }

    public record RepairChange(String path, String sourceField, String targetField, int distance) {
    }
}
