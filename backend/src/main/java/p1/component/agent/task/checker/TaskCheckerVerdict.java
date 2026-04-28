package p1.component.agent.task.checker;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

/**
 * 任务监督器检查器判定结果
 * <p>
 * 该类表示 checker（检查器）对任务执行结果的最终判定，包含决策类型、证据管理、
 * 摘要信息以及重试指令等关键信息。用于在任务监督流程中传递检查器的评估结果，
 * 控制任务的后续执行流程。
 * </p>
 * <p>
 * 主要功能：
 * <ul>
 *   <li>定义两种决策类型：APPROVED（通过）、RETRY（重试）、REJECTED（拒绝）</li>
 *   <li>管理黑板证据的可见性，控制 rpAgent 能感知哪些证据</li>
 *   <li>提供最终回复摘要和判定理由</li>
 *   <li>支持重试场景下的指令传递</li>
 * </ul>
 * </p>
 */
@Data
public class TaskCheckerVerdict {

    @Description("对任务的思考过程，包括对任务的最终判断和建议。")
    private String thinking;

    @Description("判定理由")
    private String reason;

    @Description("对此任务的最终判定，必须是 APPROVED、RETRY 或 REJECTED")
    private String decision;

    @Description("当判定为 RETRY 时，提供的重试指令")
    private String retryInstruction;
}
