# PDFBox Lab

Lokalny projekt demonstracyjny **Java 21 + Spring Boot 3.5.7 + Apache PDFBox 3.0.8** oraz **Angular 21**. Interfejs jest po polsku. Pokazuje pełną drogę od PDF do kontekstu dla modelu: ekstrakcję, normalizację, fragmenty ze źródłami, wyszukiwanie BM25 i gotowy prompt. Nie wymaga konta ani klucza API.

## Uruchomienie

Wymagania: JDK 21, Maven 3.6.3+ oraz Node.js 20.19+, 22.12+ lub 24.x (zgodne z wymaganiami Angulara 21). Pierwsze uruchomienie pobiera zależności z Maven Central i npm.

Na Windows uruchom w dwóch terminalach z katalogu projektu:

```powershell
.\start-backend.cmd
```

```powershell
.\start-frontend.cmd
```

Otwórz **[http://127.0.0.1:4207](http://127.0.0.1:4207)**. Backend: [http://127.0.0.1:8087/api/health](http://127.0.0.1:8087/api/health). Zatrzymanie: `Ctrl+C` w każdym terminalu. Porty 4207/8087 pozwalają uruchomić demo obok typowych aplikacji na 4200/8080.

Ręcznie, również na Linux/macOS:

```sh
cd backend
mvn -Dmaven.repo.local=../.tools/m2 spring-boot:run
```

```sh
cd frontend
npm ci
npm start
```

`JAVA_HOME` musi wskazywać JDK 21 lub nowszy. Skrypt Windows korzysta z zainstalowanego JDK 21 w standardowej lokalizacji, jeśli `JAVA_HOME` nie jest ustawione. Lokalny cache Mavena znajduje się w ignorowanym `.tools/m2`. Żaden globalny Angular CLI nie jest potrzebny.

## Scenariusz demonstracyjny

1. Kliknij **Wypróbuj przykładowy dokument**. PDF jest tworzony przez PDFBox na backendzie: trzy strony tekstu, pole formularza `project_owner` i czwarta strona będąca obrazem skanu.
2. W **Tekst i podgląd** porównaj `PDFRenderer` z `PDFTextStripper`. Przełącz „Surowy”, aby obejrzeć rezultat przed normalizacją. Na stronie 4 obraz jest widoczny, a tekst pusty — to miejsce na OCR.
3. W **Fragmenty** sprawdź tekst, numer strony, offsety i identyfikator źródła. Zmień rozmiar i nakładanie, a następnie kliknij **Przetwórz dokument**.
4. W **Metadane** znajdziesz informacje o dokumencie, SHA-256 i pola AcroForm. Pola są odczytywane oddzielnie i nie są automatycznie dołączane do fragmentów.
5. W **Kontekst dla modelu** użyj pytania „Jaki jest budżet projektu Atlas i termin wdrożenia?”. Zbudowany prompt zawiera fragment ze strony 2 z kwotą `120 000 PLN` i terminem `15 grudnia 2026`.
6. Zapytaj o `dinozaury`, aby zobaczyć brak źródeł. Wyszukiwanie nie tworzy fikcyjnej odpowiedzi.
7. Użyj **Eksport JSON**, aby otrzymać wynik analizy z fragmentami, lub pobierz sam prompt. Wgraj własny PDF, żeby sprawdzić rzeczywiste dokumenty.

Przykładowe strony tekstowe używają standardowej czcionki PDF Helvetica, dlatego ich treść jest zapisana bez polskich znaków. Ekstrakcja własnych PDF obsługuje Unicode, jeżeli dokument ma prawidłowe mapowanie znaków. Obraz skanu zawiera polskie znaki.

## Jak zasilać model danymi z PDF

```text
PDF
  -> PDFBox: tekst stron + metadane + pola formularzy
  -> kontrola jakości / OCR dla stron bez tekstu
  -> normalizacja i fragmenty z numerami stron
  -> embeddingi + indeks wektorowy (etap do dodania)
  -> wyszukanie fragmentów dla pytania
  -> prompt ze źródłami -> LLM (etap do dodania)
```

W tym demo BM25 zapewnia lokalne wyszukiwanie słów bez dodatkowej infrastruktury. To rzeczywisty retrieval, ale nie wyszukiwanie semantyczne: normalizuje wielkość liter i diakrytykę, nie rozumie synonimów ani odmiany wyrazów. Wynik BM25 jest oceną trafności, nie prawdopodobieństwem poprawnej odpowiedzi. Fragmenty mogą się częściowo powtarzać przez overlap.

**RAG nie zmienia wag modelu.** Zamiast wysyłać cały PDF, wybierasz odpowiednie fragmenty i dodajesz je do konkretnego zapytania. Następnym krokiem może być implementacja adaptera do wybranego LLM w backendzie oraz zastąpienie `ContextService` wyszukiwaniem embeddingów (np. pgvector). Klucz modelu powinien pozostawać na backendzie. API modelu nie jest tutaj wywoływane, a UI nie symuluje odpowiedzi LLM.

Przykład danych do indeksowania (skrót; rzeczywisty eksport zawiera także strony i metadane):

```json
{
  "documentId": "sha256-całego-pliku",
  "filename": "atlas-demo.pdf",
  "chunks": [{
    "id": "identyfikator-p2-0",
    "page": 2,
    "start": 0,
    "end": 60,
    "text": "Budzet projektu Atlas wynosi 120 000 PLN...",
    "estimatedTokens": 15
  }]
}
```

Do rekordu wektorowego dodaj tekst, wektor embeddingu oraz metadane: identyfikator dokumentu, nazwę pliku, numer strony i identyfikator fragmentu. Zachowaj te metadane do cytowania źródeł. Identyfikator fragmentu w demo zawiera skrót SHA, stronę i offset początku; przy zmianie parametrów podziału usuń poprzednie fragmenty danego dokumentu i zindeksuj go ponownie.

## API

Frontend wywołuje `/api` przez proxy Angulara, więc backend nie potrzebuje szerokiego CORS.

| Metoda | Endpoint | Dane / wynik |
| --- | --- | --- |
| GET | `/api/health` | Stan backendu |
| GET | `/api/pdf/demo` | Wygenerowany przykładowy PDF |
| POST | `/api/pdf/analyze` | Multipart: `file`, `password` (opcjonalnie), `chunkSize` (1000), `overlap` (150), `sortByPosition` (true). Wynik: strony, tekst, metadane, pola, fragmenty, uwagi |
| POST | `/api/pdf/preview` | Multipart: `file`, `page` (od 1), `password`. Wynik PNG z `PDFRenderer` |
| POST | `/api/context` | JSON: `question`, `chunks` z analizy, `topK` (1–10). Wynik: ranking BM25 i prompt |

Przykład (Windows: `curl.exe`):

```sh
curl -o atlas-demo.pdf http://127.0.0.1:8087/api/pdf/demo
curl -F "file=@atlas-demo.pdf" -F "chunkSize=1000" -F "overlap=150" http://127.0.0.1:8087/api/pdf/analyze
```

Backend jest bezstanowy. Pliki i tekst nie są zapisywane w bazie ani przechowywane między żądaniami; framework może korzystać z tymczasowych plików multipart na czas żądania. Przeglądarka przechowuje bieżący dokument i wynik w pamięci do odświeżenia/zamknięcia karty. Eksport następuje wyłącznie na żądanie. Podgląd ponownie przesyła PDF do lokalnego backendu. Oba serwery nasłuchują tylko na loopback.

## Ograniczenia

- PDFBox odczytuje istniejącą warstwę tekstową, nie wykonuje OCR. Pusta strona jest oznaczona jako potencjalnie wymagająca OCR; może być też rzeczywiście pusta. Częściowy OCR lub błędne mapowania znaków wymagają ręcznej oceny.
- Tabele, nagłówki, stopki i wielokolumnowy skład wymagają dalszej obróbki. Sortowanie po pozycji nie gwarantuje poprawnej kolejności czytania. Normalizacja jest heurystyczna; tekst surowy pozostaje dostępny.
- Fragmenty nie przekraczają stron; rozmiar i overlap liczone są w jednostkach UTF-16 (Java/JS), nie tokenach. Offsety `[start, end)` odnoszą się do znormalizowanego tekstu strony. Tokeny to przybliżenie `znaki / 4`, a nie tokenizer modelu.
- Licznik obrazów pokazuje bezpośrednie zasoby `PDImageXObject` strony; nie obejmuje wszystkich obrazów zagnieżdżonych i nie jest liczbą ich wystąpień na stronie.
- Limity demonstracyjne: 20 MB, 150 stron, milion znaków surowego tekstu, 3000 fragmentów, 1000 pól formularza. Fragment: 200–4000 znaków; overlap od 0 do mniej niż połowy rozmiaru. Podgląd skaluje stronę do maks. 1400 pikseli na dłuższym boku.
- Hasła są używane tylko do bieżących żądań. Ograniczenia ekstrakcji zapisane w PDF są respektowane. Demo nie ma logowania, trwałego magazynu ani izolacji przetwarzania niezaufanych PDF wymaganej dla publicznej usługi.

## Weryfikacja

```sh
cd backend
mvn -Dmaven.repo.local=../.tools/m2 test
```

```sh
cd frontend
npm ci
npm run build
```

Testy backendu obejmują rzeczywisty PDF, cytowania źródeł, generowanie PNG, formularze, brak tekstu, hasła i uprawnienia, overlap, normalizację, nieprawidłowe pliki i walidację API. Build Angulara sprawdza także typy i szablony.

Najważniejsze klasy: `PdfService` (ekstrakcja/renderowanie), `ChunkService` (fragmenty), `ContextService` (BM25/prompt), `DemoPdfService` (generator przykładu). Backend możesz otworzyć w IntelliJ jako projekt Maven z `backend/pom.xml`.

Dokumentacja źródłowa: [PDFBox 3 — API i migracja](https://pdfbox.apache.org/3.0/migration.html), [wydania PDFBox](https://pdfbox.apache.org/download.html), [zgodność wersji Angular](https://angular.dev/reference/versions).
