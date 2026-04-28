package p1.component.agent.task.state;

public record TaskSupervisorBlackboardAppendResult(TaskSupervisorBlackboardEntry entry,
                                                   boolean appendedNewEvidence) {
}
