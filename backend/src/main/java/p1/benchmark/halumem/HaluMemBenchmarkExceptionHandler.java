package p1.benchmark.halumem;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
@Profile("benchmark")
public class HaluMemBenchmarkExceptionHandler {

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, String> handle(Exception exception) {
        return Map.of(
                "error", exception.getClass().getSimpleName(),
                "message", exception.getMessage() == null ? "" : exception.getMessage()
        );
    }
}
