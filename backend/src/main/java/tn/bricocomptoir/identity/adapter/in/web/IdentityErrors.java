package tn.bricocomptoir.identity.adapter.in.web;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = IdentityController.class)
@org.springframework.core.annotation.Order(0)
public class IdentityErrors {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> invalidBody() {
        return problem(HttpStatus.BAD_REQUEST, "Invalid input");
    }

    @ExceptionHandler(SecurityException.class)
    ResponseEntity<ProblemDetail> forbidden() {
        return problem(HttpStatus.FORBIDDEN, "Access denied");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> invalid(IllegalArgumentException exception) {
        return problem(exception.getMessage().equals("Account not found") ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST,
                exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ProblemDetail> conflict(IllegalStateException exception) {
        return problem(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> duplicateOrConflict() {
        return problem(HttpStatus.CONFLICT, "Account change conflicts with existing data");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        return ResponseEntity.status(status).body(problem);
    }
}
