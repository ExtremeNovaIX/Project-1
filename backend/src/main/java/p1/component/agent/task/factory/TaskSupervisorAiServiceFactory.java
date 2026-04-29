package p1.component.agent.task.factory;

import p1.component.agent.task.supervisor.TaskSupervisorAiService;
import p1.component.agent.task.supervisor.TaskSupervisorToolbox;

@FunctionalInterface
public interface TaskSupervisorAiServiceFactory {

    TaskSupervisorAiService create(TaskSupervisorToolbox toolbox);
}
