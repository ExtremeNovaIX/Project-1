package p1.component.agent.task.state;

public record TaskSupervisorBlackboardEntry(String evidenceId,
                                            String sourceType,
                                            String sourceName,
                                            String status,
                                            String request,
                                            String promptSummary,
                                            String response) {
}
