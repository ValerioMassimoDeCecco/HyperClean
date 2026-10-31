# HyperClean — Eliminatore Ultra-Veloce di File Duplicati

Un'applicazione Android progettata per individuare ed eliminare i file duplicati su tutta la memoria del dispositivo con la massima velocità possibile grazie a un algoritmo di confronto progressivo a blocchi (`Dimensione → Primi 1 KB → Blocchi successivi da 4 KB`), affiancato da un motore AI avanzato per l'analisi intelligente delle copie e consigli di pulizia profonda.

## User Review & Critical Decisions

> [!IMPORTANT]
> Di seguito sono riepilogate le decisioni confermate e le scelte architetturali chiave adottate per garantire sia la massima velocità di scansione sia la conformità con le policy di sicurezza Android.

- **Ambito di Scansione ("Tutto il dispositivo")**: L'app combina la scansione completa del catalogo multimediale e dei documenti tramite `MediaStore` (foto, video, audio, archivi, download e documenti indicizzati) con l'accesso diretto a qualsiasi cartella o radice di archiviazione tramite `Storage Access Framework` (SAF `OpenDocumentTree`), permettendo inoltre di generare o importare un set di file reali nel workspace dell'app per testare e misurare in tempo reale i benchmark dell'algoritmo `1 KB → 4 KB`.
- **Criterio Automatico "Mantieni Originale"**: Per ogni gruppo di duplicati identificato, il sistema calcola un punteggio di priorità basato sulla data di creazione/modifica più antica, sulla profondità e autorevolezza della cartella (es. privilegiando `DCIM/Camera` o `Documents` rispetto a `Download`, `WhatsApp` o cartelle temporanee) e sulla pulizia del nome file (penalizzando suffissi come `(1)`, `copia`, `backup`).
- **Intelligenza Artificiale ad Alto Ragionamento (`gemini-4-argon` con `ThinkingLevel.HIGH`)**: Integrata tramite Firebase AI Logic per analizzare i gruppi di duplicati complessi, spiegare perché una determinata copia è stata scelta come originale e generare un piano strategico di pulizia profonda per recuperare spazio in sicurezza.

---

## 1. Overview & Core Concept

- **Cosa Fa**: Scansiona l'intero spazio di archiviazione accessibile alla ricerca di file identici al 100% byte per byte, evitando di leggere inutilmente interi file di grandi dimensioni grazie a un filtro multi-stadio progressivo, e consente di eliminare tutte le copie superflue con un solo tocco.
- **Target Audience / Persona**: Utenti Android esigenti, fotografi mobile e power user che accumulano migliaia di foto, video, PDF, backup e download duplicati e desiderano liberare gigabyte di spazio in pochi secondi senza rischiare di cancellare i file originali.
- **Valore Chiave**: Velocità estrema su file di qualsiasi dimensione (video 4K, archivi, RAW, documenti) grazie all'interruzione immediata del confronto non appena un blocco da `1 KB` o `4 KB` differisce, unita a una cache locale degli hash per scansioni successive istantanee.

---

## 2. User Experience & Visual Design

### Flussi Utente Principali
1. **Dashboard Telemetrica & Avvio Scansione**: All'apertura, l'utente visualizza lo stato dello spazio di archiviazione, le statistiche della cache hash e il pulsante principale per avviare la scansione globale (MediaStore + cartelle collegate via SAF + area benchmark).
2. **Radar di Scansione Live (Real-Time Telemetry)**: Durante l'esecuzione, l'interfaccia mostra in tempo reale i contatori di:
   - File raggruppati per dimensione identica (Stadio 0)
   - Confronti rapidi del primo blocco da `1 KB` (Stadio 1)
   - Avanzamento dei blocchi progressivi da `4 KB` (`4 KB → 8 KB → 12 KB...`, Stadio 2+)
   - Velocità effettiva in MB/s e percentuale di I/O risparmiato rispetto a una lettura tradizionale completa.
3. **Revisione Gruppi Duplicati & Auto-Selezione**: Al termine, i gruppi di file identici sono elencati per spazio recuperabile. In ogni gruppo, il file "Originale" è protetto da un badge verde **ORIGINALE PROTETTO**, mentre le copie ridondanti sono pre-selezionate per l'eliminazione. L'utente può filtrare per categoria (Tutti, Immagini, Video, Audio, Documenti/Altro), ispezionare i metadati o invertire la selezione.
4. **Consulente AI Deep Clean (High Thinking)**: Con un tocco su *"Analisi AI Profonda"*, il modello `gemini-4-argon` (`ThinkingLevel.HIGH`) esamina la struttura dei duplicati trovati, individua pattern ricorrenti (es. cartelle che generano copie ridondanti, backup doppi) e risponde a domande complesse sulla strategia ottimale di conservazione.
5. **Eliminazione Sicura & Report Finale**: Un'azione di conferma rimuove fisicamente i file selezionati, aggiorna il database locale e mostra il riepilogo dei megabyte/gigabyte liberati.

### Identità Visiva & Tema
- **Direzione Estetica**: *High-Precision Telemetry & Technical Dark* — ispirata agli strumenti di diagnostica ad alte prestazioni, con superfici scure profonde, indicatori luminosi ad alto contrasto e layout asimmetrici focalizzati sui dati di velocità e spazio.
- **Palette Colori & Atmosfera**:
  - `--background`: `#0B0F17` (Nero Ossidiana Profondo)
  - `--surface`: `#131B2E` (Blu Grafite Tecnico con elevazione tonale stratificata)
  - `--primary`: `#00F5D4` (Ciano Neon Telemetrico per azioni primarie, radar 1KB/4KB e indicatori di velocità)
  - `--secondary`: `#6366F1` (Indaco Elettrico per il motore AI High Thinking e badge analitici)
  - `--accent-safe`: `#10B981` (Verde Smeraldo per il badge "Originale Protetto")
  - `--accent-danger`: `#F43F5E` (Rosso Corallo per le copie selezionate per l'eliminazione)
- **Tipografia & Gerarchia**:
  - *Display & Titoli*: **Space Grotesk** (installato localmente in `res/font/`) per intestazioni tecniche, metriche giganti di spazio recuperabile e contatori di velocità.
  - *Corpo & Percorsi File*: **JetBrains Mono** per hash esadecimali, dimensioni in byte, velocità di lettura (`1 KB` / `4 KB`) e percorsi delle cartelle; **Plus Jakarta Sans** per descrizioni leggibili e consigli AI.
- **Feedback Interattivo & Movimento**: Animazioni fluide a molla (`spring()`) per l'espansione dei gruppi di duplicati, barre di progresso segmentate che evidenziano il risparmio di lettura disco e transizioni immediate tra le schede *Scanner*, *Duplicati*, *AI Advisor* e *Cronologia/Benchmark*.

---

## 3. Key Product Decisions & Trade-Offs

- **Decisione 1: Algoritmo di Hashing Progressivo a Finestre (`Dimensione → 1 KB → 4 KB × N`)**
  - *Approccio Scelto*:
    1. **Filtro 0 (Zero Disk Read)**: Raggruppamento istantaneo per `size` in byte; tutti i file con dimensione unica vengono scartati immediatamente senza alcuna lettura dal disco.
    2. **Stadio 1 (1 KB Header Hash)**: Per i soli file con uguale dimensione, lettura dei primi `1.024 byte` (`1 KB`) e calcolo di un hash veloce a 64-bit / SHA-256. I file con hash `1 KB` unico vengono esclusi subito.
    3. **Stadio 2+ (4 KB Progressive Stepping)**: Per i candidati che superano il primo kilobyte, il motore procede leggendo in parallelo blocchi successivi da `4.096 byte` (`4 KB`) alla volta (`offset 1 KB..5 KB`, poi `5 KB..9 KB`, ecc.). Non appena i blocchi da `4 KB` divergono, i file vengono separati nei rispettivi sotto-bucket senza leggere il resto del file!
  - *Perché*: Su file multimediali o archivi da decine o centinaia di megabyte che condividono la stessa dimensione o un header simile, questo approccio evita fino al 99% delle operazioni di I/O su disco rispetto agli scanner tradizionali che calcolano l'hash dell'intero file fin dall'inizio.
  - *Alternative Scartate*: Hashing completo immediato (troppo lento su video e grandi archivi) o confronto solo su primo/ultimo blocco (rischio di falsi positivi e perdita di dati).

- **Decisione 2: Persistenza Locale della Cache Hash con Room Database**
  - *Approccio Scelto*: Salvataggio locale in SQLite (tramite Room) delle firme calcolate indicizzate per `(uri/path, fileSize, lastModified)` insieme allo storico delle pulizie effettuate.
  - *Perché*: Nelle scansioni successive, i file non modificati riutilizzano gli hash parziali già noti, riducendo ulteriormente il tempo di scansione a frazioni di secondo e mantenendo i dati dei file strettamente privati sul dispositivo.

- **Decisione 3: Intelligenza Artificiale con `gemini-4-argon` e `ThinkingLevel.HIGH`**
  - *Approccio Scelto*: Utilizzo di Firebase AI Logic con configurazione `ThinkingLevel.HIGH` sul modello `gemini-4-argon` senza imporre `maxOutputTokens`.
  - *Perché*: Consente un ragionamento profondo sui metadati dei gruppi di duplicati (percorsi, pattern di denominazione, date di creazione, dimensioni per categoria) per fornire sia verdetti accurati sui gruppi ambigui sia una consulenza interattiva avanzata.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Diagram

```
┌───────────────────────────────────────────────────────────────────────────┐
│                         UI LAYER (Jetpack Compose)                        │
│  ┌─────────────────┐  ┌──────────────────┐  ┌──────────────┐  ┌─────────┐ │
│  │ Radar Scanner   │  │ Duplicate Groups │  │ AI DeepClean │  │ Lab &   │ │
│  │ & Live Telemetry│  │ & Smart Selector │  │ Advisor Tab  │  │ History │ │
│  └────────┬────────┘  └────────┬─────────┘  └──────┬───────┘  └────┬────┘ │
└───────────┼────────────────────┼───────────────────┼───────────────┼──────┘
            │                    │                   │               │
            ▼                    ▼                   ▼               ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                     VIEWMODEL & STATE LAYER (StateFlow)                   │
│             DuplicateCleanerViewModel (ScanState, Selection, AI)          │
└───────────┬────────────────────────────────────────┬───────────────┬──────┘
            │                                        │               │
            ▼                                        ▼               ▼
┌───────────────────────────────────────┐  ┌──────────────────┐  ┌──────────┐
│   PROGRESSIVE HASH ENGINE (Coroutines)│  │ FIREBASE AI LOGIC│  │ ROOM DB  │
│  1. Group by exact byte size          │  │ gemini-4-argon   │  │ HashCache│
│  2. Hash first 1 KB (1024 B)          │  │ ThinkingLevel    │  │ & Clean  │
│  3. Loop 4 KB chunks (4096 B)         │  │ .HIGH            │  │ History  │
│     until divergence or EOF           │  └──────────────────┘  └──────────┘
└───────────┬───────────────────────────┘
            │
            ▼
┌───────────────────────────────────────┐
│   STORAGE ACCESS & MEDIA AGGREGATOR   │
│  MediaStore + SAF DocumentTree +      │
│  Internal Storage / Benchmark Sandbox │
└───────────────────────────────────────┘
```

### Data Model & State
- **`ScannedFileItem`**: Rappresenta un file sul dispositivo (`id`, `uri`, `displayName`, `folderPath`, `sizeBytes`, `lastModified`, `mimeType`, `category`, `originScore`, `isKeepRecommended`, `isSelectedForDeletion`).
- **`DuplicateGroup`**: Insieme di 2 o più `ScannedFileItem` verificati identici fino all'ultimo blocco, con `groupId`, `fileSizeBytes`, `wastedBytes` (`sizeBytes * (count - 1)`), `chunksVerified`, e `aiRecommendation`.
- **`ScanTelemetry`**: Metriche live del motore (`totalFilesDiscovered`, `sizeFilteredCandidates`, `oneKbChecksCount`, `fourKbChunksReadCount`, `bytesActuallyRead`, `bytesSavedByEarlyExit`, `elapsedTimeMs`, `throughputMbPerSec`).
- **`CachedFileSignature` (Room Entity)**: Cache locale (`fileKey`, `sizeBytes`, `lastModified`, `first1KbHash`, `fullProgressiveHash`, `updatedAt`) e **`CleanupHistoryRecord` (Room Entity)** per tracciare le sessioni di pulizia completate.

### Interactive Component & State Mapping
- **Avvio / Pausa / Annulla Scansione**: Il pulsante primario sulla Dashboard avvia la pipeline asincrona su `Dispatchers.IO` con concorrenza strutturata; aggiorna `ScanTelemetry` ad ogni stadio (`Dimensione → 1 KB → 4 KB × N`) e permette l'annullamento cooperativo istantaneo.
- **Selettore Criterio Intelligente & Filtri**: Nella vista *Duplicati*, i chip consentono di filtrare per categoria (Tutti, Foto, Video, Audio, Documenti) e di applicare o modificare la regola di selezione automatica (*Originale per Data e Cartella*, *Più Recente*, *Più Vecchio*, *Percorso Più Breve*), ricalcolando istantaneamente `isSelectedForDeletion` e il totale dei byte recuperabili.
- **Generatore Benchmark On-Device ("Laboratorio Velocità 1KB/4KB")**: Permette di generare con un tocco un set di file reali di test (inclusi file della stessa dimensione che differiscono al primo `1 KB`, al terzo blocco da `4 KB`, o identici al 100%) per verificare visivamente la telemetria del motore anche su un emulatore appena avviato.
- **Analisi AI con ThinkingLevel.HIGH**: Nel pannello *AI DeepClean*, l'utente può richiedere l'analisi completa dei gruppi trovati o inviare domande personalizzate; il ViewModel invoca `gemini-4-argon` con `thinkingConfig { thinkingLevel = ThinkingLevel.HIGH }` in streaming/unary con timeout di 60 secondi e gestione trasparente degli errori.
