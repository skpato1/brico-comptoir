package tn.bricocomptoir.packs.adapter.in.web;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@org.springframework.core.annotation.Order(0)
@RestControllerAdvice(assignableTypes = PackController.class)
public class PackErrors {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> invalid(IllegalArgumentException error) {
        HttpStatus status = error.getMessage().endsWith("not found") ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
        return problem(status, error.getMessage());
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    ResponseEntity<ProblemDetail> invalidInput() { return problem(HttpStatus.BAD_REQUEST, "Invalid input"); }
    @ExceptionHandler({IllegalStateException.class, DataIntegrityViolationException.class})
    ResponseEntity<ProblemDetail> conflict() { return problem(HttpStatus.CONFLICT, "Pack change conflicts"); }
    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
