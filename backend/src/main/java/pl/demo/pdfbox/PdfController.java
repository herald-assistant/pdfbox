package pl.demo.pdfbox;

import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import static pl.demo.pdfbox.Models.*;

@RestController
@RequestMapping("/api")
public class PdfController {
    private final PdfService pdf;
    private final ContextService context;
    private final DemoPdfService demo;
    public PdfController(PdfService pdf, ContextService context, DemoPdfService demo) {
        this.pdf = pdf; this.context = context; this.demo = demo;
    }
    @GetMapping("/health") public java.util.Map<String, String> health() { return java.util.Map.of("status", "UP"); }
    @PostMapping(value="/pdf/analyze", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Analysis analyze(@RequestParam MultipartFile file, @RequestParam(defaultValue="") String password,
                            @RequestParam(defaultValue="1000") int chunkSize, @RequestParam(defaultValue="150") int overlap,
                            @RequestParam(defaultValue="true") boolean sortByPosition) throws IOException {
        return pdf.analyze(file, password, chunkSize, overlap, sortByPosition);
    }
    @PostMapping(value="/pdf/preview", consumes=MediaType.MULTIPART_FORM_DATA_VALUE, produces=MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> preview(@RequestParam MultipartFile file, @RequestParam(defaultValue="") String password,
                                         @RequestParam(defaultValue="1") int page) throws IOException {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(pdf.preview(file, password, page));
    }
    @GetMapping(value="/pdf/demo", produces=MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> demo() throws IOException {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=atlas-demo.pdf").body(demo.generate());
    }
    @PostMapping("/context") public ContextResult context(@Valid @RequestBody ContextRequest request) { return context.retrieve(request); }
}
