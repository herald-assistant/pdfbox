package pl.demo.pdfbox;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.io.IOException;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> invalid(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }
    @ExceptionHandler(IOException.class)
    ResponseEntity<Map<String, String>> unreadable(IOException ex) {
        return ResponseEntity.unprocessableEntity().body(Map.of("message", "Nie udało się odczytać PDF. Plik może być uszkodzony lub używać nieobsługiwanego formatu."));
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Map<String, String>> tooLarge() {
        return ResponseEntity.status(413).body(Map.of("message", "Maksymalny rozmiar PDF to 20 MB."));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, MethodArgumentTypeMismatchException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class})
    ResponseEntity<Map<String, String>> validation(Exception ex) {
        return ResponseEntity.badRequest().body(Map.of("message", "Nieprawidłowe parametry żądania. Sprawdź pytanie, fragmenty i ustawienia."));
    }
}
