package com.namastute.shared;

import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

// Turns exceptions from any controller into our standard {"error": {code, message}} response
@RestControllerAdvice
class ApiExceptionHandler {

    record ErrorBody(Error error) {
        record Error(String code, String message) {}
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorBody> handleApi(ApiException e) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(e.status());
        if (e.retryAfterSeconds() != null) {
            response.header(HttpHeaders.RETRY_AFTER, String.valueOf(e.retryAfterSeconds()));
        }
        return response.body(new ErrorBody(new ErrorBody.Error(e.code(), e.getMessage())));
    }

    // Spring refuses an upload over spring.servlet.multipart.max-file-size before any controller runs (ID photos, 78)
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ErrorBody> handleTooBig(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ErrorBody(new ErrorBody.Error("PHOTO_TOO_BIG", "Each photo must be under 2 MB.")));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorBody> handleValidation(MethodArgumentNotValidException e) {
        String details = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest()
                .body(new ErrorBody(new ErrorBody.Error("VALIDATION_FAILED", details)));
    }
}