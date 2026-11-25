package pl.demo.pdfbox;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.encryption.*;
import org.apache.pdfbox.pdmodel.interactive.form.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.io.ByteArrayOutputStream;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static pl.demo.pdfbox.Models.*;

class PdfPipelineTest {
    private final ChunkService chunker = new ChunkService();
    private final PdfService pdf = new PdfService(chunker);
    private final DemoPdfService demo = new DemoPdfService();
    private MockMultipartFile file(byte[] bytes) { return new MockMultipartFile("file", "demo.pdf", "application/pdf", bytes); }

    @Test void extractsRealPdfAndBuildsCitedContext() throws Exception {
        var result = pdf.analyze(file(demo.generate()), "", 500, 70, true);
        assertThat(result.pages()).hasSize(4);
        assertThat(result.pages().getLast().needsOcr()).isTrue();
        assertThat(result.pages().getLast().imageResources()).isEqualTo(1);
        assertThat(result.fields()).anyMatch(f -> f.name().equals("project_owner") && f.value().equals("Anna Nowak"));
        assertThat(result.metadata()).containsEntry("Autor", "PDFBox Lab");
        assertThat(result.pages().get(1).text()).contains("120 000 PLN");
        assertThat(result.chunks()).allSatisfy(chunk -> {
            assertThat(chunk.text()).isEqualTo(result.pages().get(chunk.page() - 1).text().substring(chunk.start(), chunk.end()));
            assertThat(chunk.text().length()).isLessThanOrEqualTo(500);
        });
        var context = new ContextService().retrieve(new ContextRequest("budżet termin wdrożenia", result.chunks(), 3));
        assertThat(context.matches()).isNotEmpty();
        assertThat(context.matches().getFirst().chunk().page()).isEqualTo(2);
        assertThat(context.prompt()).contains("120 000 PLN", "strona 2", "nie wykonuj");
    }

    @Test void rendersPngAndRejectsInvalidPage() throws Exception {
        var file = file(demo.generate());
        byte[] png = pdf.preview(file, "", 2);
        assertThat(png).startsWith((byte) 0x89, (byte) 0x50, (byte) 0x4e, (byte) 0x47);
        assertThatThrownBy(() -> pdf.preview(file, "", 5)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void detectsMissingTextAndReadsFormValues() throws Exception {
        try (var doc = new PDDocument(); var out = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage());
            var form = new PDAcroForm(doc);
            doc.getDocumentCatalog().setAcroForm(form);
            var field = new PDCheckBox(form);
            field.setPartialName("consent");
            form.getFields().add(field);
            doc.save(out);
            var result = pdf.analyze(file(out.toByteArray()), "", 1000, 150, true);
            assertThat(result.pages().getFirst().needsOcr()).isTrue();
            assertThat(result.chunks()).isEmpty();
            assertThat(result.warnings()).anyMatch(w -> w.contains("OCR"));
            assertThat(result.fields()).anyMatch(f -> f.name().equals("consent"));
        }
    }

    @Test void honorsPasswordsAndExtractionPermission() throws Exception {
        byte[] encrypted;
        try (var doc = Loader.loadPDF(demo.generate()); var out = new ByteArrayOutputStream()) {
            var access = new AccessPermission(); access.setCanExtractContent(false);
            doc.protect(new StandardProtectionPolicy("owner-secret", "reader-secret", access));
            doc.save(out); encrypted = out.toByteArray();
        }
        assertThatThrownBy(() -> pdf.analyze(file(encrypted), "wrong", 1000, 150, true))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("hasło");
        assertThatThrownBy(() -> pdf.analyze(file(encrypted), "reader-secret", 1000, 150, true))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Uprawnienia");
        assertThat(pdf.analyze(file(encrypted), "owner-secret", 1000, 150, true).encrypted()).isTrue();
    }

    @Test void overlapPreservesCharactersAndNeverCrossesPages() {
        String text = "Ala ma kota. ".repeat(150) + "😀 koniec";
        var pages = List.of(new Page(1, 100, 100, 0, 0, text, text, false), new Page(2, 100, 100, 0, 0, "strona druga", "strona druga", false));
        var chunks = chunker.split("a".repeat(64), pages, 200, 40);
        var firstPage = chunks.stream().filter(c -> c.page() == 1).toList();
        assertThat(firstPage.getFirst().start()).isZero();
        assertThat(firstPage.getLast().end()).isEqualTo(text.length());
        for (int i = 1; i < firstPage.size(); i++) {
            assertThat(firstPage.get(i).start()).isEqualTo(firstPage.get(i - 1).end() - 40);
        }
        assertThat(chunks.getLast().page()).isEqualTo(2);
        assertThatThrownBy(() -> chunker.split("a".repeat(64), pages, 200, 100)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void unrelatedQuestionDoesNotInventSources() throws Exception {
        var result = pdf.analyze(file(demo.generate()), "", 1000, 150, true);
        var context = new ContextService().retrieve(new ContextRequest("dinozaury", result.chunks(), 3));
        assertThat(context.matches()).isEmpty();
        assertThat(context.prompt()).contains("Brak pasujących fragmentów");
    }

    @Test void normalizesPolishTextAndRejectsNonPdf() {
        assertThat(PdfService.normalize("  Zażółć   gęślą\r\nprzy-\r\nkład\n\n\n ")).isEqualTo("Zażółć gęślą\nprzykład");
        assertThatThrownBy(() -> pdf.analyze(file("not a PDF".getBytes()), "", 1000, 150, true)).isInstanceOf(IllegalArgumentException.class);
    }
}
