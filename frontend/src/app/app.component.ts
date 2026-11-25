import { ChangeDetectorRef, Component, OnDestroy, inject } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { HttpClient, HttpErrorResponse } from "@angular/common/http";
import { firstValueFrom } from "rxjs";
import { Analysis, ContextResult, PdfPage } from "./models";

@Component({
  selector: "app-root",
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: "./app.component.html",
})
export class AppComponent implements OnDestroy {
  private http = inject(HttpClient);
  private changeDetector = inject(ChangeDetectorRef);
  file: File | null = null;
  analysis: Analysis | null = null;
  context: ContextResult | null = null;
  tab: "text" | "chunks" | "metadata" | "context" = "text";
  chunkSize = 1000;
  overlap = 150;
  sortByPosition = true;
  password = "";
  page = 1;
  raw = false;
  busy = false;
  searching = false;
  previewBusy = false;
  previewUrl = "";
  previewError = "";
  error = "";
  notice = "";
  question = "Jaki jest budżet projektu Atlas i termin wdrożenia?";
  topK = 3;
  dragOver = false;
  private previewRequest = 0;
  get currentPage(): PdfPage | undefined {
    return this.analysis?.pages[this.page - 1];
  }
  get totalChars(): number {
    return this.analysis?.pages.reduce((sum, p) => sum + p.text.length, 0) ?? 0;
  }
  get ocrPages(): number {
    return this.analysis?.pages.filter((p) => p.needsOcr).length ?? 0;
  }
  get settingsChanged(): boolean {
    return (
      !!this.analysis &&
      (this.chunkSize !== this.analysis.chunkSize ||
        this.overlap !== this.analysis.overlap)
    );
  }

  selectFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (file) this.loadFile(file);
    input.value = "";
  }
  drop(event: DragEvent): void {
    event.preventDefault();
    this.dragOver = false;
    const file = event.dataTransfer?.files[0];
    if (file && !this.busy) this.loadFile(file);
  }
  loadFile(file: File): void {
    if (this.busy) return;
    if (
      file.size > 20 * 1024 * 1024 ||
      !file.name.toLowerCase().endsWith(".pdf")
    ) {
      this.error = "Wybierz plik PDF o rozmiarze do 20 MB.";
      return;
    }
    this.file = file;
    this.password = "";
    this.analysis = null;
    this.context = null;
    this.page = 1;
    this.tab = "text";
    this.error = "";
    this.clearPreview();
    void this.analyze();
  }
  async demo(): Promise<void> {
    this.error = "";
    this.busy = true;
    try {
      const blob = await firstValueFrom(
        this.http.get("/api/pdf/demo", { responseType: "blob" }),
      );
      this.busy = false;
      this.loadFile(
        new File([blob], "atlas-demo.pdf", { type: "application/pdf" }),
      );
    } catch (e) {
      this.error = await this.message(e);
      this.busy = false;
    } finally {
      this.changeDetector.markForCheck();
    }
  }
  private form(): FormData {
    const data = new FormData();
    if (this.file) data.append("file", this.file);
    data.append("password", this.password);
    return data;
  }
  async analyze(): Promise<void> {
    if (!this.file || this.busy) return;
    if (
      this.chunkSize < 200 ||
      this.chunkSize > 4000 ||
      this.overlap < 0 ||
      this.overlap >= this.chunkSize / 2
    ) {
      this.error =
        "Rozmiar: 200–4000 znaków. Nakładanie musi być mniejsze niż połowa rozmiaru.";
      return;
    }
    this.busy = true;
    this.error = "";
    this.context = null;
    this.analysis = null;
    this.clearPreview();
    const data = this.form();
    data.append("chunkSize", String(this.chunkSize));
    data.append("overlap", String(this.overlap));
    data.append("sortByPosition", String(this.sortByPosition));
    try {
      this.analysis = await firstValueFrom(
        this.http.post<Analysis>("/api/pdf/analyze", data),
      );
      this.page = 1;
      void this.renderPage();
    } catch (e) {
      this.error = await this.message(e);
    } finally {
      this.busy = false;
      this.changeDetector.markForCheck();
    }
  }
  selectPage(value: number): void {
    this.page = value;
    void this.renderPage();
  }
  async renderPage(): Promise<void> {
    const request = ++this.previewRequest;
    this.previewBusy = true;
    this.previewError = "";
    const data = this.form();
    data.append("page", String(this.page));
    try {
      const blob = await firstValueFrom(
        this.http.post("/api/pdf/preview", data, { responseType: "blob" }),
      );
      if (request !== this.previewRequest) return;
      if (this.previewUrl) URL.revokeObjectURL(this.previewUrl);
      this.previewUrl = URL.createObjectURL(blob);
    } catch (e) {
      if (request === this.previewRequest)
        this.previewError = await this.message(e);
    } finally {
      if (request === this.previewRequest) {
        this.previewBusy = false;
        this.changeDetector.markForCheck();
      }
    }
  }
  async retrieve(): Promise<void> {
    if (
      !this.analysis?.chunks.length ||
      !this.question.trim() ||
      this.searching
    )
      return;
    const source = this.analysis;
    this.searching = true;
    this.error = "";
    this.context = null;
    try {
      const result = await firstValueFrom(
        this.http.post<ContextResult>("/api/context", {
          question: this.question,
          chunks: source.chunks,
          topK: this.topK,
        }),
      );
      if (source === this.analysis) this.context = result;
    } catch (e) {
      this.error = await this.message(e);
    } finally {
      this.searching = false;
      this.changeDetector.markForCheck();
    }
  }
  exportJson(): void {
    if (this.analysis)
      this.download(
        JSON.stringify({ schemaVersion: 1, ...this.analysis }, null, 2),
        "pdfbox-data.json",
        "application/json",
      );
  }
  exportPrompt(): void {
    if (this.context)
      this.download(
        this.context.prompt,
        "prompt.txt",
        "text/plain;charset=utf-8",
      );
  }
  downloadPdf(): void {
    if (!this.file) return;
    const url = URL.createObjectURL(this.file);
    this.saveUrl(url, this.file.name);
  }
  async copyPrompt(): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.context?.prompt ?? "");
      this.notice = "Prompt skopiowany.";
    } catch {
      this.notice = "Schowek jest niedostępny. Użyj przycisku Pobierz prompt.";
    } finally {
      this.changeDetector.markForCheck();
    }
  }
  private download(text: string, filename: string, type: string): void {
    this.saveUrl(URL.createObjectURL(new Blob([text], { type })), filename);
  }
  private saveUrl(url: string, filename: string): void {
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = filename;
    anchor.click();
    window.setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
  private async message(e: unknown): Promise<string> {
    if (e instanceof HttpErrorResponse) {
      if (e.error instanceof Blob) {
        try {
          return JSON.parse(await e.error.text()).message;
        } catch {
          /* use generic error below */
        }
      }
      if (e.error?.message) return e.error.message;
      if (!e.status || e.status >= 500)
        return "Nie można połączyć się z backendem. Sprawdź, czy Spring Boot działa na porcie 8087.";
    }
    return "Operacja nie powiodła się. Sprawdź plik i spróbuj ponownie.";
  }
  private clearPreview(): void {
    this.previewRequest++;
    if (this.previewUrl) URL.revokeObjectURL(this.previewUrl);
    this.previewUrl = "";
    this.previewError = "";
    this.previewBusy = false;
  }
  ngOnDestroy(): void {
    this.clearPreview();
  }
}
