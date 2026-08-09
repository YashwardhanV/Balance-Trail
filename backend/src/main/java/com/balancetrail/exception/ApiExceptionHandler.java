package com.balancetrail.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(ResourceNotFoundException.class)
  public ProblemDetail notFound(ResourceNotFoundException exception, HttpServletRequest request) {
    return problem(HttpStatus.NOT_FOUND, "Resource not found", exception.getMessage(), request);
  }

  @ExceptionHandler({
    InvalidUploadException.class,
    MethodArgumentNotValidException.class,
    ConstraintViolationException.class
  })
  public ProblemDetail badRequest(Exception exception, HttpServletRequest request) {
    String detail =
        exception instanceof MethodArgumentNotValidException validation
            ? validation.getBindingResult().getAllErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("Request validation failed")
            : exception.getMessage();
    return problem(HttpStatus.BAD_REQUEST, "Invalid request", detail, request);
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ProblemDetail fileTooLarge(
      MaxUploadSizeExceededException exception, HttpServletRequest request) {
    return problem(
        HttpStatus.PAYLOAD_TOO_LARGE,
        "File too large",
        "The CSV exceeds the configured upload limit",
        request);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ProblemDetail conflict(
      DataIntegrityViolationException exception, HttpServletRequest request) {
    return problem(
        HttpStatus.CONFLICT,
        "Data conflict",
        "The request conflicts with an existing record or database constraint",
        request);
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail unexpected(Exception exception, HttpServletRequest request) {
    log.error("Unhandled request failure for {}", request.getRequestURI(), exception);
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Internal server error",
        "The request could not be completed",
        request);
  }

  private ProblemDetail problem(
      HttpStatus status, String title, String detail, HttpServletRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(title);
    problem.setInstance(URI.create(request.getRequestURI()));
    problem.setProperty("timestamp", Instant.now());
    return problem;
  }
}
