package svb.leaseacar.interest;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import svb.leaseacar.interest.rate.InterestRateDateConflictException;

/** Converts API failures into stable ProblemDetail responses. */
@RestControllerAdvice
class ApiExceptionHandler {

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<ProblemDetail> responseStatus(ResponseStatusException exception) {
    String detail = exception.getReason() == null ? "Request failed" : exception.getReason();
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.getStatusCode(), detail);
    return ResponseEntity.status(exception.getStatusCode()).body(problem);
  }

  @ExceptionHandler(InterestRateDateConflictException.class)
  ProblemDetail duplicateStartDate(InterestRateDateConflictException exception) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
  }

  @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
  ProblemDetail invalidParameter() {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request parameter");
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ProblemDetail malformedBody() {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Malformed request body");
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ProblemDetail invalidInterestRate(MethodArgumentNotValidException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Interest rate input is invalid");
    problem.setTitle("Invalid request");
    Map<String, String> errors = new LinkedHashMap<>();
    for (FieldError error : exception.getBindingResult().getFieldErrors()) {
      errors.putIfAbsent(
          error.getField(), error.getDefaultMessage() == null ? "invalid" : error.getDefaultMessage());
    }
    problem.setProperty("errors", errors);
    return problem;
  }
}
