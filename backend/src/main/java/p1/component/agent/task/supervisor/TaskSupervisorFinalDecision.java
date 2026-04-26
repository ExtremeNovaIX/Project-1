package p1.component.agent.task.supervisor;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

import java.util.List;

@Data
public class TaskSupervisorFinalDecision {

    @Description("在这里输入你的思考过程，包括对任务的最终判断和建议。")
    private String thinking;

    @Description("任务完成状态，必须是COMPLETED或 CANNOT_FINISH或 WORKING")
    private String completionClaim;

    @Description("认为相关的证据ID列表，注意id必须是黑板中存在的id")
    private List<String> candidateEvidenceIds;

    @Description("未解决的风险列表，如果没有未解决的风险，列表为空")
    private List<String> openRisks;

    public List<String> payloadCandidateEvidenceIds() {
        return candidateEvidenceIds == null ? List.of() : candidateEvidenceIds;
    }

    public List<String> payloadOpenRisks() {
        return openRisks == null ? List.of() : openRisks;
    }
}
