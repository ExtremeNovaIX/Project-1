package p1.benchmark.memory;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@Profile("benchmark")
@RestControllerAdvice(basePackageClasses = MemoryBenchmarkController.class)
public class MemoryBenchmarkExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handle(Exception exception) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("error", exception.getClass().getSimpleName());
        payload.put("message", trimToEmpty(exception.getMessage()));

        Throwable rootCause = rootCauseOf(exception);
        if (rootCause != null && rootCause != exception) {
            payload.put("rootCause", rootCause.getClass().getSimpleName());
            payload.put("rootMessage", trimToEmpty(rootCause.getMessage()));
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(payload);
    }

    private Throwable rootCauseOf(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private String trimToEmpty(String text) {
        return text == null ? "" : text.trim();
    }
}
