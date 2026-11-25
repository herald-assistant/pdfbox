package pl.demo.pdfbox;

import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import static pl.demo.pdfbox.Models.*;

@Service
public class ChunkService {
    public List<Chunk> split(String documentId, List<Page> pages, int size, int overlap) {
        if (size < 200 || size > 4000 || overlap < 0 || overlap >= size / 2) {
            throw new IllegalArgumentException("Rozmiar fragmentu: 200–4000 znaków. Nakładanie: od 0 do mniej niż połowy rozmiaru.");
        }
        List<Chunk> result = new ArrayList<>();
        for (Page page : pages) {
            String text = page.text();
            int start = 0;
            while (start < text.length()) {
                int end = Math.min(start + size, text.length());
                if (end < text.length()) {
                    int space = text.lastIndexOf(' ', end);
                    if (space > start + size / 2) end = space;
                    if (Character.isLowSurrogate(text.charAt(end))) end--;
                }
                String value = text.substring(start, end);
                if (!value.isBlank()) {
                    result.add(new Chunk(documentId.substring(0, 12) + "-p" + page.number() + "-" + start,
                            page.number(), start, end, value, tokens(value)));
                    if (result.size() > 3000) throw new IllegalArgumentException("Za dużo fragmentów. Zwiększ rozmiar fragmentu lub użyj mniejszego PDF.");
                }
                if (end == text.length()) break;
                int next = Math.max(start + 1, end - overlap);
                if (Character.isLowSurrogate(text.charAt(next))) next++;
                start = next;
            }
        }
        return result;
    }

    public static int tokens(String text) { return (text.length() + 3) / 4; }
}
