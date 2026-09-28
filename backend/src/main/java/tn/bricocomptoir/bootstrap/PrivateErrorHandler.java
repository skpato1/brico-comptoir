package tn.bricocomptoir.bootstrap;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.LOWEST_PRECEDENCE)
public class PrivateErrorHandler {
    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    public ResponseEntity<?> database() {
        return ResponseEntity.status(500).cacheControl(CacheControl.noStore()).body(Map.of("code","DATA_OPERATION_FAILED"));
    }
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<?> unreadable() { return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(Map.of("code","INVALID_INPUT")); }
}
