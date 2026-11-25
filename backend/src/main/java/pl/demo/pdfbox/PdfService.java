package pl.demo.pdfbox;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.io.*;
import java.security.*;
import java.text.Normalizer;
import java.util.*;
import static pl.demo.pdfbox.Models.*;

@Service
public class PdfService {
    private final ChunkService chunks;
    public PdfService(ChunkService chunks) { this.chunks = chunks; }

    public Analysis analyze(MultipartFile file, String password, int size, int overlap, boolean sort) throws IOException {
        long started = System.nanoTime();
        byte[] bytes = read(file);
        String id = hash(bytes);
        try (PDDocument document = open(bytes, password)) {
            check(document);
            List<Page> pages = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(sort);
            int total = 0;
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                stripper.setStartPage(i + 1);
                stripper.setEndPage(i + 1);
                String raw = stripper.getText(document);
                String text = normalize(raw);
                total += raw.length();
                if (total > 1_000_000) throw new IllegalArgumentException("Limit tekstu to 1 000 000 znaków. Podziel dokument na mniejsze pliki.");
                PDPage page = document.getPage(i);
                int images = 0;
                if (page.getResources() != null) {
                    for (var name : page.getResources().getXObjectNames()) {
                        if (page.getResources().getXObject(name) instanceof PDImageXObject) images++;
                    }
                }
                boolean needsOcr = text.isBlank();
                if (needsOcr) warnings.add("Strona " + (i + 1) + ": brak warstwy tekstowej. Jeśli zawiera skan, potrzebny jest OCR (np. Tesseract).");
                pages.add(new Page(i + 1, page.getCropBox().getWidth(), page.getCropBox().getHeight(),
                        page.getRotation(), images, raw, text, needsOcr));
            }
            Map<String, String> metadata = new LinkedHashMap<>();
            var info = document.getDocumentInformation();
            put(metadata, "Tytuł", info.getTitle());
            put(metadata, "Autor", info.getAuthor());
            put(metadata, "Temat", info.getSubject());
            put(metadata, "Słowa kluczowe", info.getKeywords());
            put(metadata, "Generator", info.getProducer());
            put(metadata, "Aplikacja", info.getCreator());
            if (info.getCreationDate() != null) put(metadata, "Data utworzenia", info.getCreationDate().toInstant().toString());
            List<FormField> fields = new ArrayList<>();
            var form = document.getDocumentCatalog().getAcroForm();
            if (form != null) {
                for (var field : form.getFieldTree()) {
                    fields.add(new FormField(field.getFullyQualifiedName(), field.getFieldType(), field.getValueAsString()));
                    if (fields.size() >= 1000) { warnings.add("Pokazano pierwsze 1000 pól formularza."); break; }
                }
                if (!fields.isEmpty()) warnings.add("Pola AcroForm są pokazane oddzielnie. Przed indeksowaniem połącz je z tekstem, jeśli mają być dostępne dla modelu.");
            }
            warnings.add("PDFBox nie wykonuje OCR ani semantycznej rekonstrukcji tabel. Sprawdź kolejność tekstu w dokumentach wielokolumnowych.");
            return new Analysis(id, filename(file), bytes.length, Float.toString(document.getVersion()),
                    document.isEncrypted(), metadata, pages, fields, chunks.split(id, pages, size, overlap),
                    warnings, size, overlap, (System.nanoTime() - started) / 1_000_000);
        }
    }

    public byte[] preview(MultipartFile file, String password, int page) throws IOException {
        try (PDDocument document = open(read(file), password)) {
            check(document);
            if (page < 1 || page > document.getNumberOfPages()) throw new IllegalArgumentException("Nieprawidłowy numer strony.");
            var box = document.getPage(page - 1).getCropBox();
            if (!Float.isFinite(box.getWidth()) || !Float.isFinite(box.getHeight()) || box.getWidth() <= 0 || box.getHeight() <= 0) {
                throw new IllegalArgumentException("Nieprawidłowe wymiary strony.");
            }
            float scale = Math.min(1.5f, 1400f / Math.max(box.getWidth(), box.getHeight()));
            var renderer = new PDFRenderer(document);
            renderer.setSubsamplingAllowed(true);
            var image = renderer.renderImage(page - 1, scale);
            try (var out = new ByteArrayOutputStream()) {
                ImageIO.write(image, "png", out);
                return out.toByteArray();
            } finally { image.flush(); }
        }
    }

    private PDDocument open(byte[] bytes, String password) throws IOException {
        try { return Loader.loadPDF(bytes, password); }
        catch (InvalidPasswordException ex) { throw new IllegalArgumentException("PDF jest zaszyfrowany. Podaj prawidłowe hasło."); }
    }

    private void check(PDDocument document) {
        if (document.getNumberOfPages() < 1 || document.getNumberOfPages() > 150) throw new IllegalArgumentException("Obsługiwane są dokumenty od 1 do 150 stron.");
        if (!document.getCurrentAccessPermission().canExtractContent()) throw new IllegalArgumentException("Uprawnienia PDF nie pozwalają na ekstrakcję treści.");
    }

    private byte[] read(MultipartFile file) throws IOException {
        if (file.isEmpty() || file.getSize() > 20 * 1024 * 1024) throw new IllegalArgumentException("Wybierz niepusty PDF o rozmiarze do 20 MB.");
        byte[] bytes = file.getBytes();
        // The header may occur within the first 1024 bytes (PDF readers allow leading bytes).
        if (!new String(bytes, 0, Math.min(1024, bytes.length), java.nio.charset.StandardCharsets.ISO_8859_1).contains("%PDF-")) {
            throw new IllegalArgumentException("Plik nie ma nagłówka PDF.");
        }
        return bytes;
    }

    static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFC).replace("\u00ad", "")
                .replaceAll("(?<=\\p{L})-\\R(?=\\p{Ll})", "")
                .replaceAll("[\\p{Zs}\\t]+", " ").replaceAll(" *\\R *", "\n")
                .replaceAll("\n{3,}", "\n\n").strip();
    }
    private static void put(Map<String, String> map, String key, String value) { if (value != null && !value.isBlank()) map.put(key, value); }
    private static String filename(MultipartFile file) {
        String name = Objects.requireNonNullElse(file.getOriginalFilename(), "document.pdf").replace('\\', '/');
        return name.substring(name.lastIndexOf('/') + 1);
    }
    private static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
