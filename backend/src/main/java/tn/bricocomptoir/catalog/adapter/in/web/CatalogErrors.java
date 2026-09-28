package tn.bricocomptoir.catalog.adapter.in.web;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;

@org.springframework.core.annotation.Order(0)
@RestControllerAdvice(assignableTypes = {CatalogController.class, CatalogImportController.class})
public class CatalogErrors {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> invalid(IllegalArgumentException error) {
        return problem(error.getMessage().endsWith("not found") ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST,
                error.getMessage());
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    ResponseEntity<ProblemDetail> invalidInput() { return problem(HttpStatus.BAD_REQUEST, "Invalid input"); }
    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ProblemDetail> conflict(IllegalStateException error) {
        return problem(HttpStatus.CONFLICT, error.getMessage());
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> databaseConflict() {
        return problem(HttpStatus.CONFLICT, "Catalog change conflicts with existing data");
    }
    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
