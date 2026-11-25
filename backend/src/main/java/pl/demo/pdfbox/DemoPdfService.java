package pl.demo.pdfbox;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.interactive.form.*;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import org.springframework.stereotype.Service;
import java.io.*;

@Service
public class DemoPdfService {
    public byte[] generate() throws IOException {
        try (var document = new PDDocument(); var out = new ByteArrayOutputStream()) {
            document.getDocumentInformation().setTitle("Atlas - knowledge base for RAG");
            document.getDocumentInformation().setAuthor("PDFBox Lab");
            document.getDocumentInformation().setSubject("Text extraction, chunking and source attribution");
            page(document, "Projekt Atlas / baza wiedzy", new String[]{
                    "Atlas to aplikacja do analizy dokumentow PDF w firmie.",
                    "Zespol korzysta z Apache PDFBox do odczytu tekstu i metadanych.",
                    "Kazdy fragment zachowuje numer strony i identyfikator dokumentu.",
                    "Dzieki temu odpowiedz modelu moze wskazywac konkretne zrodlo.", "",
                    "Architektura", "Backend: Java 21 i Spring Boot. Frontend: Angular.",
                    "PDFBox wydobywa dane. Model je interpretuje na podstawie kontekstu.",
                    "Samo dodanie PDF do aplikacji nie trenuje modelu.", "",
                    "Przeplyw danych", "PDF -> tekst stron -> normalizacja -> fragmenty -> wyszukiwanie -> prompt.",
                    "W produkcji fragmenty mozna zamienic na embeddingi i zapisac w bazie wektorowej."
            });
            page(document, "Wdrozenie i koszty", new String[]{
                    "Budzet projektu Atlas wynosi 120 000 PLN.",
                    "Termin wdrozenia to 15 grudnia 2026.",
                    "Za wdrozenie odpowiada Anna Nowak.",
                    "Pilotaz obejmuje 30 uzytkownikow z dzialu obslugi klienta.", "",
                    "Zasady dostepu", "Dokumenty sa przetwarzane lokalnie, bez wysylania do zewnetrznego modelu.",
                    "Wersja demonstracyjna nie przechowuje dokumentow po zakonczeniu zadania.",
                    "Przed integracja z modelem nalezy ustalic zasady ochrony danych.", "",
                    "Jakosc odpowiedzi", "Odpowiedz powinna zawierac cytowania numerow stron.",
                    "Jesli w zrodlach nie ma odpowiedzi, model powinien o tym poinformowac."
            });
            page(document, "Ograniczenia ekstrakcji", new String[]{
                    "PDFBox odczytuje istniejaca warstwe tekstowa. Nie wykonuje OCR.",
                    "Skan dokumentu wymaga OCR, na przyklad z uzyciem Tesseract.",
                    "PDFRenderer moze zamienic strone PDF na obraz do przetworzenia OCR.", "",
                    "Tabele i kolumny", "PDF opisuje polozenie znakow, a nie logiczna strukture tabel.",
                    "Sortowanie po polozeniu pomaga, lecz nie gwarantuje poprawnej kolejnosci.",
                    "Pola formularzy AcroForm mozna odczytac jako pary nazwa-wartosc.", "",
                    "Fragmenty", "Nakladanie fragmentow pozwala zachowac kontekst na ich granicach.",
                    "W tym demo fragment nie przekracza granicy strony.",
                    "Liczba tokenow jest przyblizona i zalezy od tokenizera wybranego modelu."
            });
            addForm(document);
            addScan(document);
            document.save(out);
            return out.toByteArray();
        }
    }

    private void addForm(PDDocument document) throws IOException {
        var form = new PDAcroForm(document);
        var resources = new PDResources();
        resources.put(COSName.getPDFName("Helv"), new PDType1Font(Standard14Fonts.FontName.HELVETICA));
        form.setDefaultResources(resources);
        form.setDefaultAppearance("/Helv 11 Tf 0 g");
        document.getDocumentCatalog().setAcroForm(form);
        var field = new PDTextField(form);
        field.setPartialName("project_owner");
        form.getFields().add(field);
        var widget = field.getWidgets().getFirst();
        widget.setRectangle(new PDRectangle(42, 140, 250, 25));
        widget.setPage(document.getPage(2));
        document.getPage(2).getAnnotations().add(widget);
        field.setValue("Anna Nowak");
    }

    private void addScan(PDDocument document) throws IOException {
        var image = new BufferedImage(1000, 1400, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, 1000, 1400);
            graphics.setColor(new Color(25, 55, 80));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 38));
            graphics.drawString("Przykład skanu / brak warstwy tekstowej", 55, 120);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 25));
            graphics.drawString("Ten tekst jest obrazem, a nie znakami w PDF.", 55, 200);
            graphics.drawString("PDFRenderer wyświetla stronę poprawnie.", 55, 250);
            graphics.drawString("PDFTextStripper nie wydobędzie z niej treści.", 55, 300);
            graphics.drawString("Następny krok: obraz strony -> OCR -> fragmenty.", 55, 390);
        } finally { graphics.dispose(); }
        var page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        try (var stream = new PDPageContentStream(document, page)) {
            stream.drawImage(LosslessFactory.createFromImage(document, image), 0, 0, PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight());
        } finally { image.flush(); }
    }

    private void page(PDDocument document, String title, String[] lines) throws IOException {
        var page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        try (var stream = new PDPageContentStream(document, page)) {
            stream.setNonStrokingColor(0.10f, 0.22f, 0.34f);
            stream.addRect(0, 720, PDRectangle.A4.getWidth(), 122);
            stream.fill();
            stream.beginText();
            stream.setNonStrokingColor(1f, 1f, 1f);
            stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 22);
            stream.newLineAtOffset(42, 768);
            stream.showText(title);
            stream.endText();
            stream.beginText();
            stream.setNonStrokingColor(0.15f, 0.20f, 0.26f);
            stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
            stream.setLeading(25);
            stream.newLineAtOffset(42, 680);
            for (String line : lines) { stream.showText(line); stream.newLine(); }
            stream.endText();
        }
    }
}
