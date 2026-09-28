package tn.bricocomptoir.sales.adapter.in.web;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
@org.springframework.core.annotation.Order(0)
@RestControllerAdvice(assignableTypes=DeliverySettingsController.class)
public class DeliverySettingsControllerErrors {
    @ExceptionHandler({IllegalArgumentException.class,HttpMessageNotReadableException.class})
    public ResponseEntity<ProblemDetail> invalid() {
        return ResponseEntity.badRequest().body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,"Vérifiez les champs, leurs limites et les valeurs autorisées."));
    }
    @ExceptionHandler({IllegalStateException.class,org.springframework.dao.DataIntegrityViolationException.class})
    public ResponseEntity<ProblemDetail> conflict() {
        return ResponseEntity.status(409).body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,"Le réglage a changé. Rechargez-le avant de réessayer."));
    }
}
