package io.github.g1ocr.text;

import io.github.g1ocr.ocr.PixelOcr;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Source matching and display readiness are independent. Raw OCR is never edited. */
public final class TextResolver {
    public record Candidate(String recordId, int ordinal, String japanese, String segmentStatus,
                            Map<String, String> variables, String source, String revision, String reusedPlayerName) {}
    public record Unit(String rawJapanese, String matchStatus, String displayStatus, String resolvedJapanese,
                       String chinese, List<Candidate> candidates) {
        public String display() { return chinese.isEmpty() ? rawJapanese : chinese; }
    }
    private record Progress(TextCorpus.Hit hit, int start) {}
    /** Source identity is deliberately excluded; current content and display permissions must agree. */
    private record Rendering(String status, String chinese, Map<String, String> variables,
                             Map<String, String> variableKinds, int waitingLines, String reusedPlayerName) {}
    private static final Pattern SLOT = Pattern.compile("\\{\\{(\\w+)}}");
    private static final Pattern QUANTITY = Pattern.compile("^(.*?) *(× *[0-9]+)$");
    private static final Pattern PRICE = Pattern.compile("^(.*?)( *[0-9]+円)$");
    private final TextCorpus corpus;
    private final Map<String, List<Progress>> histories = new LinkedHashMap<>();
    private String playerName;
    private String selectedPlayerName;

    public TextResolver(TextCorpus corpus) { this.corpus = corpus; }
    public void reset() { histories.clear(); playerName = null; }
    public void selectPlayerName(String name) { selectedPlayerName = name; reset(); }
    public void retainRegions(Set<String> visible) { histories.keySet().retainAll(visible); }

    public List<Unit> resolve(PixelOcr.Result result) {
        if (result.layout().equals("dialogue"))
            return List.of(dialogue(result.id(), String.join("\n", result.rawOcr())));
        List<Unit> units = new ArrayList<>();
        for (String row : result.rawOcr()) {
            if (result.layout().equals("battle_action")) {
                for (String name : row.split(" +")) units.add(word(name, "", List.of("fixed"), "BattleMenu."));
            } else if (result.semanticType().equals("pc_pokemon")) {
                // Mon entries include the visible level field; the cancel row is a fixed control.
                if (row.contains("<Lv>")) {
                    String name = row.substring(0, row.indexOf("<Lv>")).stripTrailing();
                    units.add(word(name, row.substring(name.length()), List.of(), ""));
                }
                else units.add(word(row, "", List.of("fixed"), "ListMenuCancelText"));
            } else {
                var amount = (result.layout().equals("shop_items") ? PRICE : QUANTITY).matcher(row);
                String name = amount.matches() ? amount.group(1) : row;
                String suffix = amount.matches() ? row.substring(name.length()) : "";
                units.add(word(name, suffix,
                        result.layout().equals("battle_moves") ? List.of("move", "fixed") : List.of("item", "fixed"), ""));
            }
        }
        for (String footer : result.footerLines()) units.add(word(footer, "", List.of("fixed"), ""));
        return List.copyOf(units);
    }

    private Unit word(String text, String suffix, List<String> kinds, String control) {
        var entries = corpus.words(text, kinds).stream()
                .filter(e -> control.isEmpty() || e.id().contains(":fixed:" + control)).toList();
        var candidates = entries.stream().map(e -> new Candidate(e.id(), 1, e.japanese(), "whole_visible_unit",
                Map.of(), e.source(), e.revision().toString(), "")).toList();
        String raw = text + suffix;
        if (text.contains("�")) return new Unit(raw, "unreliable", "unknown_character", "", "", candidates);
        if (entries.isEmpty()) return new Unit(raw, "missing", "missing", "", "", candidates);
        var chinese = entries.stream().map(e -> corpus.displayChinese(e, e.chinese()) + suffix).distinct().toList();
        if (chinese.size() != 1) return new Unit(raw, "ambiguous", "ambiguous", "", "", candidates);
        var japanese = entries.stream().map(e -> e.japanese() + suffix).distinct().toList();
        return new Unit(raw, entries.size() == 1 ? "unique" : "equivalent", "ready",
                japanese.size() == 1 ? japanese.getFirst() : raw, chinese.getFirst(), candidates);
    }

    public Unit dialogue(String regionId, String raw) {
        String text = TextCorpus.clean(raw);
        if (text.isEmpty() || text.contains("�")) {
            histories.remove(regionId);
            return new Unit(raw, "unreliable", "unknown_character", "", "", List.of());
        }
        var hits = corpus.dialogue(text);
        List<Progress> previous = histories.getOrDefault(regionId, List.of());
        List<Progress> progress = new ArrayList<>();
        List<Progress> continued = new ArrayList<>();
        for (var hit : hits) if (hit.complete()) {
            var fragment = hit.fragment();
            int start = fragment.first();
            boolean linked = false;
            for (var before : previous) {
                var old = before.hit().fragment();
                if (!old.entry().id().equals(fragment.entry().id())) continue;
                // Only adjacent source windows can establish continuous observed context.
                boolean same = old.ordinal() == fragment.ordinal()
                        && before.hit().variables().equals(hit.variables());
                boolean next = fragment.previous() == old.ordinal() && fragment.first() <= old.ordinal() + 1;
                if (same || next) { start = Math.min(start, before.start()); linked = true; }
            }
            var value = new Progress(hit, start);
            progress.add(value);
            if (linked) continued.add(value);
        }
        List<TextCorpus.Hit> possible = new ArrayList<>(hits);
        if (!continued.isEmpty()) {
            progress = continued;
            possible = continued.stream().map(Progress::hit).toList();
        }
        List<Progress> remembered = new ArrayList<>(progress);
        for (var before : previous) if (hits.stream().anyMatch(h -> !h.complete()
                && h.fragment().entry().id().equals(before.hit().fragment().entry().id()))) remembered.add(before);
        histories.put(regionId, List.copyOf(remembered));
        if (possible.isEmpty()) return new Unit(raw, "missing", "missing", "", "", List.of());
        // Learn only when every remaining source fully agrees that this visible value is PLAYER.
        // Region disappearance clears dialogue progress, not the session's confirmed identity.
        if (possible.stream().allMatch(h -> h.complete() && h.variables().containsKey("PLAYER"))) {
            var names = possible.stream().map(h -> h.variables().get("PLAYER")).distinct().toList();
            if (names.size() == 1) playerName = names.getFirst();
        }
        List<Candidate> candidates = new ArrayList<>();
        List<Rendering> renderings = new ArrayList<>();
        for (var hit : possible) {
            int start = hit.fragment().first();
            for (var value : progress) if (value.hit().equals(hit)) start = value.start();
            var rendering = render(hit, start);
            renderings.add(rendering);
            candidates.add(candidate(hit, rendering.reusedPlayerName()));
        }
        if (renderings.stream().distinct().count() != 1)
            return new Unit(raw, "ambiguous", "ambiguous", "", "", candidates);
        var rendering = renderings.getFirst();
        var japanese = possible.stream().filter(TextCorpus.Hit::complete)
                .map(h -> substitute(h.fragment().japanese(), h.variables())).distinct().toList();
        return new Unit(raw, possible.size() == 1 ? "unique" : "equivalent", rendering.status(),
                japanese.isEmpty() ? "" : japanese.size() == 1 ? japanese.getFirst() : raw, rendering.chinese(), candidates);
    }

    private Rendering render(TextCorpus.Hit hit, int start) {
        var fragment = hit.fragment();
        String fallbackPlayerName = selectedPlayerName != null ? selectedPlayerName : playerName;
        String savedChinese = TextCorpus.slice(fragment.entry().chinese(), fragment.chineseStart(), fragment.chineseEnd());
        boolean useDisplay = !fragment.displayChinese().isEmpty();
        if (useDisplay && fragment.displayPolicy().equals("missing_rolled_name")) {
            useDisplay = false;
            var required = SLOT.matcher(savedChinese);
            while (required.find()) {
                String slot = required.group(1);
                if (!hit.variables().containsKey(slot) && !(slot.equals("PLAYER") && fallbackPlayerName != null)) {
                    useDisplay = true;
                    break;
                }
            }
        }
        if (useDisplay) savedChinese = fragment.displayChinese();
        Map<String, String> displayedVariables = new LinkedHashMap<>(hit.variables());
        if (useDisplay) {
            // A reviewed common fragment does not publish the other candidate-specific fields.
            var used = new java.util.HashSet<String>();
            var slots = SLOT.matcher(savedChinese);
            while (slots.find()) used.add(slots.group(1));
            displayedVariables.keySet().retainAll(used);
        }
        Map<String, String> kinds = new LinkedHashMap<>();
        displayedVariables.keySet().forEach(slot -> kinds.put(slot, corpus.variableKind(fragment.entry(), slot)));
        String status = fragment.status();
        if (!hit.complete()) status = "incomplete";
        else if (status.equals("semantic_context_required") && start > fragment.contextStart()) status = "context_required";
        else if (List.of("whole_visible_unit", "semantic_visible", "semantic_context_required").contains(status))
            status = fragment.waiting() == 0 ? "ready" : "ready_waiting";
        String chinese = "";
        String reusedPlayerName = "";
        if (status.equals("ready") || status.equals("ready_waiting")) {
            chinese = corpus.displayChinese(fragment.entry(), savedChinese);
            Map<String, String> values = new LinkedHashMap<>();
            var slots = SLOT.matcher(chinese);
            while (slots.find()) {
                String slot = slots.group(1), value = hit.variables().get(slot);
                // Only PLAYER may come from the selected name or session; other slots need current pixels.
                if (value == null && slot.equals("PLAYER") && fallbackPlayerName != null) {
                    value = fallbackPlayerName;
                    reusedPlayerName = fallbackPlayerName;
                }
                if (value == null) { status = "variables_missing"; break; }
                values.put(slot, translatedVariable(fragment.entry(), slot, value));
            }
            chinese = status.equals("variables_missing") ? "" : substitute(chinese, values);
            if (chinese.isEmpty()) reusedPlayerName = "";
        }
        return new Rendering(status, chinese, Map.copyOf(displayedVariables), Map.copyOf(kinds),
                Math.max(0, fragment.waiting() - fragment.ordinal()), reusedPlayerName);
    }

    private String translatedVariable(TextCorpus.Entry entry, String slot, String raw) {
        String kind = corpus.variableKind(entry, slot);
        boolean name = List.of("name", "nickname", "battle_name", "trainer_name", "trainer", "gym_leader").contains(kind);
        if (!name && !List.of("item", "move", "pokemon", "stat", "city").contains(kind)) return raw;
        boolean enemy = kind.equals("battle_name") && raw.startsWith("てきの ");
        String value = enemy ? raw.substring(4) : raw;
        var chinese = name ? corpus.nameTranslations(value) : corpus.variableWords(value, kind).stream()
                .filter(e -> e.games().equals(entry.games()))
                .map(e -> corpus.displayChinese(e, e.chinese())).distinct().toList();
        return (enemy ? corpus.enemyPrefix(entry) : "") + (chinese.size() == 1 ? chinese.getFirst() : value);
    }

    private static Candidate candidate(TextCorpus.Hit hit, String reusedPlayerName) {
        var fragment = hit.fragment();
        return new Candidate(fragment.entry().id(), fragment.ordinal(), fragment.japanese(), fragment.status(),
                hit.variables(), fragment.entry().source()
                        + (fragment.displaySource().isEmpty() ? "" : "; display: " + fragment.displaySource()),
                fragment.entry().revision().toString(), reusedPlayerName);
    }
    private static String substitute(String text, Map<String, String> variables) {
        return SLOT.matcher(text).replaceAll(match -> java.util.regex.Matcher.quoteReplacement(variables.get(match.group(1))));
    }
}
