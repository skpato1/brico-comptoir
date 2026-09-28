package tn.bricocomptoir.sales.adapter.in.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import tn.bricocomptoir.sales.domain.CheckoutFailure;

@org.springframework.core.annotation.Order(0)
@RestControllerAdvice(assignableTypes = OrderController.class)
public class OrderErrors {
    @ExceptionHandler(CheckoutFailure.class)
    ResponseEntity<ProblemDetail> failure(CheckoutFailure failure) {
        HttpStatus status = switch (failure.code()) {
            case "ORDER_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "CHECKOUT_UNAVAILABLE" -> HttpStatus.SERVICE_UNAVAILABLE;
            case "INVALID_CHECKOUT", "DELIVERY_ZONE_UNAVAILABLE" -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.CONFLICT;
        };
        return error(status, failure.code());
    }
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class,
            MethodArgumentTypeMismatchException.class, MissingRequestHeaderException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ProblemDetail> invalid() { return error(HttpStatus.BAD_REQUEST, "INVALID_CHECKOUT"); }
    private ResponseEntity<ProblemDetail> error(HttpStatus status, String code) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, code);
        detail.setProperty("code", code);
        return ResponseEntity.status(status).cacheControl(org.springframework.http.CacheControl.noStore()).body(detail);
    }
}
