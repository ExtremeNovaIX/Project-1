package p1.infrastructure.markdown;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import p1.config.prop.AssistantProperties;
import p1.infrastructure.markdown.assembler.RecentEventGroupMdAssembler;
import p1.infrastructure.markdown.model.MarkdownDocument;
import p1.model.document.MemoryArchiveDocument;
import p1.model.document.RecentEventGroupDocument;
import p1.service.markdown.MemoryArchiveStore;
import p1.service.markdown.RecentEventGroupStore;
import p1.utils.SessionUtil;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class MarkdownRecentEventGroupStore implements RecentEventGroupStore {

    private final AssistantProperties props;
    private final MarkdownFileAccess fileAccess;
    private final RecentEventGroupMdAssembler mapper;
    private final MemoryArchiveStore archiveStore;

    @Override
    public RecentEventGroupDocument save(RecentEventGroupDocument group) {
        MarkdownDocument document = mapper.toMarkdown(group, buildRenderedArchiveRefs(group));
        fileAccess.write(resolvePath(group.getSessionId(), group.getId()), document, "recent event group markdown");
        return group;
    }

    @Override
    public Optional<RecentEventGroupDocument> findById(String sessionId, String groupId) {
        String normalizedSessionId = SessionUtil.normalizeSessionId(sessionId);
        return fileAccess.readOptional(resolvePath(normalizedSessionId, groupId), "recent event group markdown")
                .map(mapper::fromMarkdown);
    }

    @Override
    public List<RecentEventGroupDocument> findAllBySessionId(String sessionId) {
        Path root = baseDirectory(SessionUtil.normalizeSessionId(sessionId));
        return listAllPaths(root).stream()
                .map(path -> fileAccess.readOptional(path, "recent event group markdown"))
                .flatMap(Optional::stream)
                .map(mapper::fromMarkdown)
                .toList();
    }

    @Override
    public List<RecentEventGroupDocument> findAll() {
        Path root = Paths.get(props.getMdRepository().getPath(), "_system", "sessions");
        return listAllPaths(root).stream()
                .filter(path -> path.toString().replace('\\', '/').contains("/recent-event-groups/"))
                .map(path -> fileAccess.readOptional(path, "recent event group markdown"))
                .flatMap(Optional::stream)
                .map(mapper::fromMarkdown)
                .toList();
    }

    @Override
    public void delete(String sessionId, String groupId) {
        fileAccess.deleteIfExists(resolvePath(SessionUtil.normalizeSessionId(sessionId), groupId), "recent event group markdown");
    }

    private List<Path> listAllPaths(Path rootDirectory) {
        return fileAccess.listRegularFiles(rootDirectory, "recent event group markdown files");
    }

    private Path resolvePath(String sessionId, String groupId) {
        return baseDirectory(sessionId).resolve(groupId + ".md");
    }

    private Path baseDirectory(String sessionId) {
        return Paths.get(props.getMdRepository().getPath(),
                "_system",
                "sessions",
                sessionId,
                "recent-event-groups");
    }

    private List<RecentEventGroupMdAssembler.RenderedArchiveRef> buildRenderedArchiveRefs(RecentEventGroupDocument group) {
        if (group == null || group.getArchiveIds() == null || group.getArchiveIds().isEmpty()) {
            return List.of();
        }

        return group.getArchiveIds().stream()
                .map(this::toRenderedArchiveRef)
                .toList();
    }

    private RecentEventGroupMdAssembler.RenderedArchiveRef toRenderedArchiveRef(Long archiveId) {
        if (archiveId == null) {
            return new RecentEventGroupMdAssembler.RenderedArchiveRef("");
        }
        return archiveStore.findById(archiveId)
                .map(this::toRenderedArchiveRef)
                .orElseGet(() -> {
                    String noteId = archiveStore.noteId(archiveId);
                    return new RecentEventGroupMdAssembler.RenderedArchiveRef("[[" + noteId + "|" + noteId + "]]");
                });
    }

    private RecentEventGroupMdAssembler.RenderedArchiveRef toRenderedArchiveRef(MemoryArchiveDocument archive) {
        String path = archiveStore.relativeNotePath(archive);
        String label = archiveStore.displayTitle(archive);
        String wikilink;
        if (path.isBlank()) {
            String noteId = archiveStore.noteId(archive.getId());
            wikilink = "[[" + noteId + "|" + noteId + "]]";
        } else {
            wikilink = "[[" + path + "|" + label + "]]";
        }
        return new RecentEventGroupMdAssembler.RenderedArchiveRef(wikilink);
    }
}
