package tn.bricocomptoir.sales.adapter.in.web;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@org.springframework.core.annotation.Order(0)
@RestControllerAdvice(assignableTypes = CartController.class)
public class CartErrors {
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class})
    ResponseEntity<ProblemDetail> invalid() { return problem(HttpStatus.BAD_REQUEST, "Invalid cart lines"); }
    @ExceptionHandler({IllegalStateException.class, DataIntegrityViolationException.class})
    ResponseEntity<ProblemDetail> conflict() { return problem(HttpStatus.CONFLICT, "Cart changed; refresh and retry"); }
    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
