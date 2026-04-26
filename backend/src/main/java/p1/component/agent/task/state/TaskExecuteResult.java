package p1.component.agent.task.state;

import java.util.List;

public record TaskExecuteResult(Status status,
                                String reasonCode,
                                String taskRunId,
                                String responseText,
                                List<String> visibleEvidenceIds) {

    public enum Status {
        COMPLETED,
        TIMEOUT,
        FAILED
    }

    public static TaskExecuteResult completed(String taskRunId,
                                              String responseText,
                                              List<String> visibleEvidenceIds) {
        return new TaskExecuteResult(
                Status.COMPLETED,
                "completed",
                taskRunId,
                responseText,
                visibleEvidenceIds == null ? List.of() : List.copyOf(visibleEvidenceIds)
        );
    }

    public static TaskExecuteResult timeout(String taskRunId, String reasonCode) {
        return new TaskExecuteResult(Status.TIMEOUT, reasonCode, taskRunId, "工具调用超时", List.of());
    }

    public static TaskExecuteResult failed(String taskRunId, String reasonCode, String responseText) {
        return new TaskExecuteResult(Status.FAILED, reasonCode, taskRunId, responseText, List.of());
    }

    public static TaskExecuteResult failed(String taskRunId, String reasonCode) {
        return new TaskExecuteResult(Status.FAILED, reasonCode, taskRunId, "工具调用失败", List.of());
    }
}
