package tn.bricocomptoir.privacy.adapter.in.web;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice(assignableTypes=PrivacyController.class)
@org.springframework.core.annotation.Order(0)
public class PrivacyErrors {
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<?> conflict() { return ResponseEntity.status(409).cacheControl(CacheControl.noStore()).body(Map.of("code","DATA_OPERATION_REFUSED")); }
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<?> forbidden() { return ResponseEntity.status(403).cacheControl(CacheControl.noStore()).body(Map.of("code","FORBIDDEN")); }
    @ExceptionHandler({IllegalArgumentException.class,IllegalStateException.class})
    public ResponseEntity<?> rule(RuntimeException failure) {
        String code=failure.getMessage();
        if(code==null || !code.matches("[A-Z_]{3,60}"))code="DATA_OPERATION_REFUSED";
        return ResponseEntity.status(failure instanceof IllegalStateException?409:400).cacheControl(CacheControl.noStore()).body(Map.of("code",code));
    }
    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<?> validation() { return ResponseEntity.badRequest().body(Map.of("code","INVALID_INPUT")); }
}
