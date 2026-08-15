---
name: i18n-sync
description: Synchronisiert neue Nachrichtenschlüssel automatisch über alle Sprachdateien im EndLock-Projekt
---

# i18n-sync Skill

Synchronisiert Nachrichtenschlüssel automatisch über alle Sprachdateien (`messages_*.yml`) im EndLock-Projekt.

## Wann verwenden

Verwenden Sie diesen Skill, wenn:
- Eine neue Nachrichtenschlüssel zu einer Sprachdatei hinzugefügt wurde
- Sie sicherstellen möchten, dass alle Übersetzungen konsistent sind
- Sie ein neues Feature implementiert haben, das neue UI-Strings erfordert
- Sie die Sprachdateien auf fehlende oder zusätzliche Schlüssel überprüfen möchten

## Wie es funktioniert

Dieser Skill:
1. Liest die Basis-Sprachdatei (normalerweise `messages_en.yml`)
2. Extrahiert alle Nachrichtenschlüssel
3. Prüft jede andere Sprachdatei (`messages_de.yml`, `messages_es.yml`, etc.)
4. Fügt fehlende Schlüssel mit englischen Standardwerten hinzu
5. Erstellt einen Bericht über hinzugefügte, fehlende oder überflüssige Schlüssel

## Schritt-für-Schritt-Prozess

### 1. Vorbereitung
Stellen Sie sicher, dass die Basis-Sprachdatei aktuell ist:
```bash
# Normalerweise messages_en.yml als Quelle der Wahrheit
```

### 2. Schlüssel extrahieren
Der Skill liest alle Schlüssel aus der Basisdatei:
```yaml
# Beispielstruktur in messages_en.yml
locked: "The End is locked!"
locked-reason: "The End is locked! Reason: %reason%"
toggle: "The End is now %status%."
```

### 3. Andere Sprachdateien abgleichen
Für jede `messages_XX.yml`:
- Vergleiche Schlüssel mit der Basis
- Füge fehlende Schlüssel mit `KEY: "ENGLISH_DEFAULT_VALUE"` hinzu
- Markiere überflüssige Schlüssel (optional zur Entfernung)

### 4. Bericht generieren
Ausgabe einer Zusammenfassung wie:
```
✅ Synchronisierung abgeschlossen
📝 Hinzugefügt zu messages_de.yml: 3 Schlüssel
   - preview-lock
   - preview-unlock
   - schedule-paused
📝 Hinzugefügt zu messages_fr.yml: 3 Schlüssel
⚠️  Überflüssig in messages_ja.yml: 1 Schlüssel (altes_feature)
```

## Beispiel-Ausführung

Nachdem Sie `msg("neuer-schlüssel")` in Ihrem Code hinzugefügt haben:

1. Führen Sie den Skill aus: `/sync i18n`
2. Der Skill fügt automatisch hinzu zu allen messages_*.yml:
   ```yaml
   # In messages_de.yml
   neuer-schlüssel: "Neuer Schlüssel Standardwert"

   # In messages_en.yml (falls nicht vorhanden)
   neuer-schlüssel: "Neuer Schlüssel Standardwert"
   ```
3. Überprüfen und übersetzen Sie die neuen Werte in jeder Sprache

## Best Practices

1. **Quelle der Wahrheit**: Behandeln Sie eine Sprache (normalerweise `messages_en.yml`) als Quelle der Wahrheit für neue Schlüssel
2. **Konsistente Formatierung**: Verwenden Sie denselben YAML-Stil und Einrückungen wie in den bestehenden Dateien
3. **Übersetzung nach Synchronisierung**: Nach dem Ausführen des Skills gehen Sie durch und übersetzen die neuen Schlüssel in jeder Sprache
4. **Regelmäßige Ausführung**: Führen Sie dies vor jedem Commit aus, der neue Nachrichtenschlüssel hinzufügt
5. **Überprüfen Sie Diffs**: Überprüfen Sie immer die generierten Änderungen bevor Sie sie committen

## Integration mit Workflow

Dieser Skill funktioniert gut mit:
- `/changelog-updater` - für die Aktualisierung der CHANGELOG.md nach Feature-Implementierungen
- `/release-workflow` - für die Vorbereitung von Releases
- Lokalen Entwicklungszyklus - führen Sie vor dem Testen neuer Nachrichtenschlüssel aus

## Fehlerbehebung

**Problem**: Skill fügt keine Schlüssel hinzu
**Lösung**: Stellen Sie sicher, dass die Basis-Sprachdatei vorhanden und lesbar ist

**Problem**: Falsche YAML-Formatierung
**Lösung**: Der Skill bewahrt die existente Einrückung und Stil bei

**Problem**: Zu viele überflüssige Schlüssel gemeldet
**Lösung**: Einige Sprachen können bewusst zusätzliche Schlüssel für regionale Varianten haben – überprüfen Sie bevor Sie entfernen
