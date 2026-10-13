package svb.leaseacar.lease;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

/** Converts API failures into stable ProblemDetail responses. */
@RestControllerAdvice
class ApiExceptionHandler {

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<ProblemDetail> responseStatus(ResponseStatusException exception) {
    String detail = exception.getReason() == null ? "Request failed" : exception.getReason();
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.getStatusCode(), detail);
    return ResponseEntity.status(exception.getStatusCode()).body(problem);
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ProblemDetail invalidParameter() {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.BAD_REQUEST, "Invalid request parameter");
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ProblemDetail malformedBody() {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Malformed request body");
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ProblemDetail invalidVehicle(MethodArgumentNotValidException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Vehicle input is invalid");
    problem.setTitle("Invalid request");
    Map<String, String> errors = new LinkedHashMap<>();
    exception
        .getBindingResult()
        .getFieldErrors()
        .forEach(
            error ->
                errors.putIfAbsent(
                    error.getField(),
                    error.getDefaultMessage() == null ? "invalid" : error.getDefaultMessage()));
    problem.setProperty("errors", errors);
    return problem;
  }
}
