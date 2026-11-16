package com.celpip.practice.imports;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import java.util.Map;

@RestControllerAdvice
class ApiErrors {
    record ErrorBody(String code, String message, Map<String,String> fieldErrors) {}
    @ExceptionHandler(ImportProblem.class)
    ResponseEntity<ErrorBody> problem(ImportProblem ex) {
        return ResponseEntity.status(ex.status).body(new ErrorBody(ex.code, ex.getMessage(), Map.of(ex.field, ex.getMessage())));
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ErrorBody> oversized() { return problem(ImportProblem.limit()); }
    @ExceptionHandler({org.springframework.web.multipart.MultipartException.class, org.springframework.web.bind.ServletRequestBindingException.class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    ResponseEntity<ErrorBody> badRequest(Exception ex) { return problem(new ImportProblem(400,"INVALID_REQUEST","request","Check the multipart request fields and content type.")); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorBody> unexpected(Exception ex) {
        if (ex instanceof org.springframework.web.ErrorResponse error) {
            return problem(new ImportProblem(error.getStatusCode().value(), "INVALID_REQUEST", "request", "Check the request route, method and content type."));
        }
        return problem(new ImportProblem(503,"LOCAL_SERVICE_ERROR","request","Local operation failed. Check storage availability and retry."));
    }
}
