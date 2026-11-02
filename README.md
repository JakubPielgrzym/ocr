# Kancelaria OCR

Aplikacja na Androida do pracy z odręcznymi zapisami (księgi, wykazy, ewidencje):
zdjęcie kartki → rozpoznanie tekstu → poprawka → szyfrowany zapis → analiza (zliczanie, sumowanie).

**Wszystko dzieje się na telefonie. Aplikacja nie ma uprawnienia do internetu**,
więc nie jest w stanie wysłać żadnych danych.

## Jak to działa

1. **Skanuj dokument** – skaner Google ML Kit (wykrywa krawędzie kartki, prostuje, poprawia kontrast; można też wczytać zdjęcie z galerii, do 10 stron naraz).
2. **OCR na urządzeniu** – ML Kit z modelem dołączonym do aplikacji.
3. **Ekran poprawek** – zdjęcie (z powiększaniem) obok tekstu. Słowa, których OCR nie był pewien, są podświetlone na żółto. Jeśli słowo przypomina coś ze słownika (imiona, nazwiska, miejscowości), pojawia się podpowiedź do zatwierdzenia jednym dotknięciem.
4. **Zapis** – tekst trafia do szyfrowanego pliku (AES-256, klucz w Android Keystore). Zdjęcie jest usuwane.
5. **Analiza** – zliczanie wystąpień (kilka wyrażeń naraz, z podziałem na dokumenty), sumowanie liczb (opcjonalnie tylko z linii zawierających dane słowo, np. „ofiara”), najczęstsze słowa. Wynik można wyeksportować do .txt.

Aplikacja uczy się: wielkie-literowe słowa z zatwierdzonych tekstów trafiają do szyfrowanego słownika użytkownika i poprawiają podpowiedzi przy kolejnych skanach. Słownik startowy możesz rozszerzyć w `app/src/main/assets/slownik.txt`.

## Zabezpieczenia (RODO / poufność)

- brak uprawnienia INTERNET (jawnie usunięte w manifeście),
- blokada biometrią albo PIN-em/wzorem telefonu; aplikacja blokuje się po każdym wyjściu z niej,
- dane zaszyfrowane w prywatnym katalogu aplikacji, wyłączona kopia zapasowa Androida (`allowBackup=false`),
- zrzuty ekranu i podgląd w liście ostatnich aplikacji zablokowane (`FLAG_SECURE`),
- zdjęcia stron usuwane po zapisie lub odrzuceniu,
- **eksport do .txt tworzy zwykły, niezaszyfrowany plik** – aplikacja o tym ostrzega; dbaj o to, gdzie go zapisujesz.

Uwaga: skaner dokumentów działa w usługach Google Play i przy pierwszym użyciu może sam pobrać swój moduł (wymaga wtedy internetu w telefonie). Skanowane obrazy przetwarzane są lokalnie.

## Budowanie w GitHub Actions

1. Utwórz **prywatne** repozytorium i wgraj do niego całą zawartość tego folderu.
2. Po pushu na gałąź `main` workflow `Build APK` zbuduje aplikację. Gotowy plik APK znajdziesz w zakładce **Actions → (ostatni przebieg) → Artifacts → kancelaria-ocr-apk**.
3. Pobierz APK na telefon i zainstaluj (Android poprosi o zgodę na instalację z nieznanego źródła dla przeglądarki/menedżera plików).

### Stały podpis (zalecane)

Bez własnego klucza każdy build jest podpisany innym kluczem i **nie da się go zainstalować „na wierzch” starej wersji** – trzeba by odinstalować aplikację, co usuwa dane. Żeby tego uniknąć, utwórz klucz raz:

```bash
keytool -genkeypair -v -keystore parafia.jks -keyalg RSA -keysize 2048 -validity 10000 -alias parafia
base64 -w0 parafia.jks      # na macOS: base64 -i parafia.jks
```

W repozytorium: **Settings → Secrets and variables → Actions → New repository secret**:

| Nazwa | Wartość |
|---|---|
| `KEYSTORE_BASE64` | wynik polecenia `base64` |
| `KEYSTORE_PASSWORD` | hasło do magazynu kluczy |
| `KEY_ALIAS` | `parafia` |
| `KEY_PASSWORD` | hasło do klucza |

Plik `parafia.jks` zachowaj w bezpiecznym miejscu (nie wrzucaj do repozytorium).

## Ograniczenia pierwszej wersji

- ML Kit jest zoptymalizowany pod tekst drukowany; pismo odręczne rozpoznaje z różną skutecznością – zależy od czytelności. Dlatego jest ekran poprawek i podpowiedzi.
- Sumowanie liczb: spacja lub kropka przed dokładnie trzema cyframi jest traktowana jako separator tysięcy (`1 200`, `3.500` = 1200, 3500). Daty (`12.03.1920`) są pomijane. Wynik zawiera listę użytych wartości do sprawdzenia.
- Brak rozpoznawania kolumn/tabel o stałym układzie – to naturalny następny krok, gdy będzie wiadomo, jak wyglądają Wasze księgi.

## Struktura

```
app/src/main/java/pl/parafia/kancelaria/
  MainActivity.kt        blokada, skaner, eksport
  AppViewModel.kt        stan aplikacji
  data/SecureStore.kt    szyfrowany zapis
  ocr/OcrEngine.kt       rozpoznawanie tekstu
  ocr/Suggestions.kt     podpowiedzi poprawek ze słownika
  analysis/Analyzer.kt   zliczanie, sumowanie, najczęstsze słowa
  ui/Screens.kt          ekrany (Jetpack Compose)
```
