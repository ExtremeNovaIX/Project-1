package p1.component.agent.task.factory;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import p1.component.agent.task.supervisor.TaskSupervisorAiService;
import p1.component.agent.task.supervisor.TaskSupervisorToolbox;

@Component
@RequiredArgsConstructor
public class DefaultTaskSupervisorAiServiceFactory implements TaskSupervisorAiServiceFactory {

    @Qualifier("supervisorChatModel")
    private final ChatModel supervisorChatModel;

    @Override
    public TaskSupervisorAiService create(TaskSupervisorToolbox toolbox) {
        return AiServices.builder(TaskSupervisorAiService.class)
                .chatModel(supervisorChatModel)
                .tools(toolbox)
                .build();
    }
}
