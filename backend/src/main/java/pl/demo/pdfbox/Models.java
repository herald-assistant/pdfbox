package pl.demo.pdfbox;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Map;

public final class Models {
    private Models() {}
    public record Page(int number, float width, float height, int rotation, int imageResources,
                       String rawText, String text, boolean needsOcr) {}
    public record Chunk(@NotBlank @Size(max=160) String id,
                        @Min(1) int page, @Min(0) int start, @Min(1) int end,
                        @NotBlank @Size(max=4000) String text, int estimatedTokens) {}
    public record FormField(String name, String type, String value) {}
    public record Analysis(String documentId, String filename, long sizeBytes, String pdfVersion,
                           boolean encrypted, Map<String, String> metadata, List<Page> pages,
                           List<FormField> fields, List<Chunk> chunks, List<String> warnings,
                           int chunkSize, int overlap, long processingMs) {}
    public record ContextRequest(@NotBlank @Size(max=2000) String question,
                                 @NotEmpty @Size(max=3000) List<@Valid Chunk> chunks,
                                 @Min(1) @Max(10) int topK) {}
    public record Match(Chunk chunk, double score) {}
    public record ContextResult(String method, List<Match> matches, String prompt,
                                int estimatedTokens, String note) {}
}
