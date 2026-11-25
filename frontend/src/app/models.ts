export interface PdfPage {
  number: number;
  width: number;
  height: number;
  rotation: number;
  imageResources: number;
  rawText: string;
  text: string;
  needsOcr: boolean;
}
export interface Chunk {
  id: string;
  page: number;
  start: number;
  end: number;
  text: string;
  estimatedTokens: number;
}
export interface Analysis {
  documentId: string;
  filename: string;
  sizeBytes: number;
  pdfVersion: string;
  encrypted: boolean;
  metadata: Record<string, string>;
  pages: PdfPage[];
  fields: { name: string; type: string; value: string }[];
  chunks: Chunk[];
  warnings: string[];
  chunkSize: number;
  overlap: number;
  processingMs: number;
}
export interface ContextResult {
  method: string;
  matches: { chunk: Chunk; score: number }[];
  prompt: string;
  estimatedTokens: number;
  note: string;
}
