package p1.service.test;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

@Service
public class StoryMarkdownChunker {

    private static final int TARGET_CHUNK_LENGTH = 300;
    private static final int MIN_CHUNK_LENGTH = 200;
    private static final int MAX_CHUNK_LENGTH = 400;

    private static final String PRIMARY_SENTENCE_ENDINGS = "。！？；!?;";
    private static final String SECONDARY_BREAKS = "，、,:：";
    private static final String TRAILING_CLOSERS = "\"'”’）】》〉」』]";

    private static final Pattern HEADING_PREFIX = Pattern.compile("^#{1,6}\\s*");
    private static final Pattern BLOCKQUOTE_PREFIX = Pattern.compile("^>\\s*");
    private static final Pattern LIST_PREFIX = Pattern.compile("^[-*+]\\s+");
    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[([^\\]]+)]\\(([^)]+)\\)");
    private static final Pattern BACKTICKS = Pattern.compile("`{1,3}");
    private static final Pattern ZERO_WIDTH = Pattern.compile("[\\u200B-\\u200D\\uFEFF]");
    private static final Pattern MULTI_SPACE = Pattern.compile("\\s{2,}");
    private static final Pattern REPEATED_EXCLAMATION_OR_QUESTION = Pattern.compile("([!！?？])\\1{2,}");
    private static final Pattern REPEATED_WAVE = Pattern.compile("([~～])\\1+");

    public List<String> chunkStory(Path storyPath) {
        return chunkStory(storyPath, null);
    }

    public List<String> chunkStory(Path storyPath, Integer targetLength) {
        List<String> chunks = new ArrayList<>();
        processStory(storyPath, targetLength, chunks::add);
        return chunks;
    }

    /**
     * 按流式方式读取超长文本，边清洗边切块，避免把整本小说一次性读进内存。
     */
    public ChunkingReport processStory(Path storyPath, Integer targetLength, Consumer<String> chunkConsumer) {
        if (storyPath == null) {
            throw new IllegalArgumentException("storyPath 不能为空");
        }
        if (Files.notExists(storyPath)) {
            throw new IllegalStateException("故事文件不存在: " + storyPath.toAbsolutePath());
        }

        ChunkCollector collector = new ChunkCollector(chunkConsumer);
        StringBuilder pending = new StringBuilder();
        FrontmatterState frontmatterState = new FrontmatterState();
        NormalizationState normalizationState = new NormalizationState(targetLength);

        try (BufferedReader reader = Files.newBufferedReader(storyPath)) {
            String rawLine;
            while ((rawLine = reader.readLine()) != null) {
                if (normalizationState.isExhausted()) {
                    break;
                }

                String normalizedLine = normalizeLine(rawLine, frontmatterState);
                appendNormalizedLine(pending, normalizedLine, normalizationState);
                emitReadyChunks(pending, false, collector);
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取故事文件失败: " + storyPath.toAbsolutePath(), e);
        }

        emitReadyChunks(pending, true, collector);
        return collector.toReport(normalizationState.effectiveLength());
    }

    public List<String> chunkText(String markdownText) {
        return chunkText(markdownText, null);
    }

    public List<String> chunkText(String markdownText, Integer targetLength) {
        String normalizedText = truncateNormalizedText(markdownText, targetLength);
        if (normalizedText.isBlank()) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();
        ChunkCollector collector = new ChunkCollector(chunks::add);
        StringBuilder pending = new StringBuilder(normalizedText);
        emitReadyChunks(pending, true, collector);
        return chunks;
    }

    /**
     * 提供一个稳定的“清洗后正文前缀”入口，方便手工检查 targetLength 的真实效果。
     */
    public String truncateNormalizedText(String markdownText, Integer targetLength) {
        String normalized = normalizeMarkdown(markdownText);
        return applyTargetLength(normalized, targetLength);
    }

    private String normalizeMarkdown(String markdownText) {
        if (markdownText == null || markdownText.isBlank()) {
            return "";
        }

        String normalizedNewLines = markdownText.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder result = new StringBuilder();
        FrontmatterState frontmatterState = new FrontmatterState();
        boolean hasText = false;

        for (String rawLine : normalizedNewLines.split("\n", -1)) {
            String normalizedLine = normalizeLine(rawLine, frontmatterState);
            if (normalizedLine.isBlank()) {
                continue;
            }

            if (hasText) {
                result.append(' ');
            }
            result.append(normalizedLine);
            hasText = true;
        }

        return result.toString();
    }

    private String normalizeLine(String rawLine, FrontmatterState frontmatterState) {
        if (rawLine == null) {
            return "";
        }

        String line = rawLine.replace('\u00A0', ' ');
        line = ZERO_WIDTH.matcher(line).replaceAll("");

        if (!frontmatterState.started()) {
            if (line.isBlank()) {
                return "";
            }
            frontmatterState.markStarted();
            if ("---".equals(line.strip())) {
                frontmatterState.enterFrontmatter();
                return "";
            }
        }

        if (frontmatterState.inFrontmatter()) {
            if ("---".equals(line.strip())) {
                frontmatterState.exitFrontmatter();
            }
            return "";
        }

        line = HEADING_PREFIX.matcher(line).replaceFirst("");
        line = BLOCKQUOTE_PREFIX.matcher(line).replaceFirst("");
        line = LIST_PREFIX.matcher(line).replaceFirst("");
        line = MARKDOWN_LINK.matcher(line).replaceAll("$1");
        line = BACKTICKS.matcher(line).replaceAll("");
        line = REPEATED_EXCLAMATION_OR_QUESTION.matcher(line).replaceAll("$1$1");
        line = REPEATED_WAVE.matcher(line).replaceAll("$1");
        line = MULTI_SPACE.matcher(line).replaceAll(" ");
        return line.trim();
    }

    private String applyTargetLength(String text, Integer targetLength) {
        if (text == null || text.isBlank()) {
            return "";
        }
        if (targetLength == null || targetLength <= 0 || text.length() <= targetLength) {
            return text;
        }
        return text.substring(0, targetLength).trim();
    }

    private void appendNormalizedLine(StringBuilder pending,
                                      String normalizedLine,
                                      NormalizationState normalizationState) {
        if (normalizedLine == null || normalizedLine.isBlank() || normalizationState.isExhausted()) {
            return;
        }

        String fragment = normalizationState.hasContent()
                ? " " + normalizedLine
                : normalizedLine;

        int remaining = normalizationState.remaining();
        if (remaining <= 0) {
            return;
        }
        if (normalizationState.hasContent() && remaining == 1) {
            normalizationState.forceStop();
            return;
        }
        if (fragment.length() > remaining) {
            fragment = fragment.substring(0, remaining);
        }
        if (fragment.isBlank()) {
            normalizationState.forceStop();
            return;
        }

        pending.append(fragment);
        normalizationState.record(fragment.length());
    }

    /**
     * 流式场景下，只有攒到“本块最大长度 + 下一块最小长度”才会提前吐块，
     * 这样可以避免还没看到足够上下文就把句子切得过碎。
     */
    private void emitReadyChunks(StringBuilder pending, boolean flushAll, ChunkCollector collector) {
        while (pending.length() > 0) {
            if (!flushAll && pending.length() < MAX_CHUNK_LENGTH + MIN_CHUNK_LENGTH) {
                return;
            }

            if (flushAll && pending.length() <= MAX_CHUNK_LENGTH) {
                collector.accept(pending.toString());
                pending.setLength(0);
                return;
            }

            int splitIndex = determineSplitIndex(pending, flushAll);
            collector.accept(pending.substring(0, splitIndex + 1));
            int nextStart = skipWhitespace(pending, splitIndex + 1);
            pending.delete(0, nextStart);
        }
    }

    private int determineSplitIndex(CharSequence text, boolean flushAll) {
        int searchStart = Math.min(MIN_CHUNK_LENGTH - 1, text.length() - 1);
        int searchEnd = Math.min(MAX_CHUNK_LENGTH - 1, text.length() - 1);
        int preferredIndex = Math.min(TARGET_CHUNK_LENGTH - 1, searchEnd);

        int splitIndex = findNearestBreak(text, searchStart, searchEnd, preferredIndex, PRIMARY_SENTENCE_ENDINGS);
        if (splitIndex < 0) {
            splitIndex = findNearestBreak(text, searchStart, searchEnd, preferredIndex, SECONDARY_BREAKS);
        }
        if (splitIndex < 0) {
            splitIndex = searchEnd;
        }

        if (!flushAll) {
            splitIndex = Math.min(splitIndex, MAX_CHUNK_LENGTH - 1);
        }
        return consumeTrailingClosers(text, splitIndex);
    }

    private int findNearestBreak(CharSequence text,
                                 int searchStart,
                                 int searchEnd,
                                 int preferredIndex,
                                 String breakChars) {
        if (searchStart > searchEnd) {
            return -1;
        }

        return IntStream.rangeClosed(searchStart, searchEnd)
                .filter(index -> breakChars.indexOf(text.charAt(index)) >= 0)
                .boxed()
                .min(Comparator.comparingInt(index -> Math.abs(index - preferredIndex)))
                .orElse(-1);
    }

    private int consumeTrailingClosers(CharSequence text, int splitIndex) {
        int cursor = splitIndex + 1;
        while (cursor < text.length() && TRAILING_CLOSERS.indexOf(text.charAt(cursor)) >= 0) {
            cursor++;
        }
        return cursor - 1;
    }

    private int skipWhitespace(CharSequence text, int start) {
        int cursor = start;
        while (cursor < text.length() && Character.isWhitespace(text.charAt(cursor))) {
            cursor++;
        }
        return cursor;
    }

    public record ChunkingReport(
            int effectiveSourceLength,
            int chunkCount,
            int minChunkLength,
            int maxChunkLength,
            int averageChunkLength,
            List<String> previewChunks
    ) {
    }

    private static final class FrontmatterState {

        private boolean started;
        private boolean inFrontmatter;

        boolean started() {
            return started;
        }

        void markStarted() {
            this.started = true;
        }

        boolean inFrontmatter() {
            return inFrontmatter;
        }

        void enterFrontmatter() {
            this.inFrontmatter = true;
        }

        void exitFrontmatter() {
            this.inFrontmatter = false;
        }
    }

    private static final class NormalizationState {

        private final Integer targetLength;
        private int effectiveLength;
        private boolean forceStopped;

        private NormalizationState(Integer targetLength) {
            this.targetLength = targetLength;
        }

        boolean hasContent() {
            return effectiveLength > 0;
        }

        int remaining() {
            if (targetLength == null || targetLength <= 0) {
                return Integer.MAX_VALUE;
            }
            return Math.max(targetLength - effectiveLength, 0);
        }

        boolean isExhausted() {
            return forceStopped || (targetLength != null && targetLength > 0 && effectiveLength >= targetLength);
        }

        void record(int appendedLength) {
            effectiveLength += appendedLength;
        }

        void forceStop() {
            this.forceStopped = true;
        }

        int effectiveLength() {
            return effectiveLength;
        }
    }

    private static final class ChunkCollector {

        private final Consumer<String> downstream;
        private final List<String> previewChunks = new ArrayList<>();
        private int chunkCount;
        private int minChunkLength = Integer.MAX_VALUE;
        private int maxChunkLength;
        private long totalChunkLength;

        private ChunkCollector(Consumer<String> downstream) {
            this.downstream = downstream == null ? chunk -> {
            } : downstream;
        }

        private void accept(String rawChunk) {
            String chunk = rawChunk == null ? "" : rawChunk.trim();
            if (chunk.isBlank()) {
                return;
            }

            downstream.accept(chunk);
            chunkCount++;
            minChunkLength = Math.min(minChunkLength, chunk.length());
            maxChunkLength = Math.max(maxChunkLength, chunk.length());
            totalChunkLength += chunk.length();
            if (previewChunks.size() < 3) {
                previewChunks.add(chunk);
            }
        }

        private ChunkingReport toReport(int effectiveSourceLength) {
            return new ChunkingReport(
                    effectiveSourceLength,
                    chunkCount,
                    chunkCount == 0 ? 0 : minChunkLength,
                    maxChunkLength,
                    chunkCount == 0 ? 0 : (int) Math.round((double) totalChunkLength / chunkCount),
                    List.copyOf(previewChunks)
            );
        }
    }
}
