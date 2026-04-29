package p1.config.prop;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "assistant.lock")
public class LockProperties {
    private long sessionLockWaitTimeoutMs = 10000;
    private int sessionLockRetryCount = 3;
    private long sessionLockRetryDelayMs = 100;
    private long compressionLeaseTimeoutMs = 300000;
}
