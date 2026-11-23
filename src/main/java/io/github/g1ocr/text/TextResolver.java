package io.github.g1ocr.text;

import io.github.g1ocr.ocr.PixelOcr;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Source matching and display readiness are independent. Raw OCR is never edited. */
public final class TextResolver {
    private static final Pattern DISPLAY_SYMBOLS = Pattern.compile("(?:<Lv>|[\\p{P}\\p{S}\\p{N}\\p{Z}\\sー])+");
    public record Candidate(String recordId, int ordinal, String japanese, String segmentStatus,
                            Map<String, String> variables, String source, String revision, String reusedPlayerName) {}
    public record Unit(String rawJapanese, String matchStatus, String displayStatus, String resolvedJapanese,
                       String chinese, List<Candidate> candidates) {
        public String display() { return chinese.isEmpty() ? rawJapanese : chinese; }
    }
    private record Progress(TextCorpus.Hit hit, int start, boolean observedContinuation) {}
    /** Source identity is deliberately excluded; current content and display permissions must agree. */
    private record Rendering(String status, String chinese, Map<String, String> variables,
                             Map<String, String> variableKinds, int waitingLines, String reusedPlayerName) {
        boolean canDisplay() {
            return !chinese.isEmpty() && (status.equals("ready") || status.equals("ready_waiting"));
        }
    }
    private static final Pattern SLOT = Pattern.compile("\\{\\{(\\w+)}}");
    private static final Pattern SPEAKER_LABEL = Pattern.compile("^\\{\\{(PLAYER|RIVAL|SPEAKER)}}『");
    private static final Pattern FIXED_DIALOGUE_LINE = Pattern.compile("[^『\\n]+『[^{}\\n]*\\p{L}[^{}\\n]*");
    private static final Pattern NAME_ONLY_DIALOGUE_LINE = Pattern.compile(
            "[^『\\n]+『\\{\\{(?:PLAYER|RIVAL|SPEAKER)}}[\\p{P}\\p{Z}]*");
    private static final Pattern QUANTITY = Pattern.compile("^(.*?) *(× *[0-9]+)$");
    private static final Pattern PRICE = Pattern.compile("^(.*?)( *[0-9]+円)$");
    private static final Pattern PRIZE_PRICE = Pattern.compile("^(.+?)( +[0-9]+)$");
    private static final String PLAYER_PC_SUFFIX = "の パソコン";
    private final TextCorpus corpus;
    private final Map<String, List<Progress>> histories = new LinkedHashMap<>();
    private String playerName;
    private Map<String, String> selectedNames = Map.of();

    public TextResolver(TextCorpus corpus) { this.corpus = corpus; }
    public void reset() { histories.clear(); playerName = null; }
    public void selectPlayerName(String name) { selectNames(name, selectedNames.get("RIVAL")); }
    public void selectNames(String player, String rival) {
        var names = new LinkedHashMap<String, String>();
        if (player != null && !player.isBlank()) names.put("PLAYER", player);
        if (rival != null && !rival.isBlank()) names.put("RIVAL", rival);
        selectedNames = Map.copyOf(names);
        reset();
    }
    public void retainRegions(Set<String> visible) { histories.keySet().retainAll(visible); }

    public Map<String, Object> diagnostics() {
        var context = new LinkedHashMap<String, Object>();
        context.put("selectedPlayerName", selectedNames.get("PLAYER"));
        context.put("selectedRivalName", selectedNames.get("RIVAL"));
        context.put("confirmedPlayerName", playerName);
        var progress = new LinkedHashMap<String, Object>();
        histories.forEach((id, values) -> progress.put(id, values.stream().map(value -> Map.of(
                "recordId", value.hit().fragment().entry().id(), "ordinal", value.hit().fragment().ordinal(),
                "start", value.start(), "variables", value.hit().variables())).toList()));
        context.put("dialogueProgress", progress);
        return context;
    }

    public List<Unit> resolve(PixelOcr.Result result) {
        return resolve(result, false);
    }

    public List<Unit> resolve(PixelOcr.Result result, boolean waitingForInput) {
        if (result.layout().equals("dialogue"))
            return List.of(dialogue(result.id(), String.join("\n", result.rawOcr()), waitingForInput));
        List<Unit> units = new ArrayList<>();
        for (String row : result.rawOcr()) {
            if (result.layout().equals("battle_action")) {
                for (String name : row.split(" +")) units.add(word(name, "", List.of("fixed"), "BattleMenu."));
            } else if (result.layout().equals("safari_action")) {
                var amount = QUANTITY.matcher(row);
                if (amount.matches()) {
                    String name = amount.group(1);
                    units.add(word(name, row.substring(name.length()), List.of("item"), ""));
                } else {
                    units.add(word(row, "", List.of("fixed"), row.equals("にげる") ? "BattleMenu." : "SafariMenu."));
                }
            } else if (result.layout().equals("pc_action")) {
                units.add(word(row, "", List.of("fixed"), "PCAction."));
            } else if (result.layout().equals("pc_item_action")) {
                units.add(word(row, "", List.of("fixed"), "PCItemAction."));
            } else if (result.layout().equals("pc_select")) {
                if (row.equals(result.diagnostics().get(1).rawOcr()) && row.endsWith(PLAYER_PC_SUFFIX)
                        && !row.contains("�")) {
                    String name = row.substring(0, row.length() - PLAYER_PC_SUFFIX.length());
                    var suffix = word(PLAYER_PC_SUFFIX, "", List.of("fixed"), "PCSelect.PlayersPCText");
                    var translations = corpus.nameTranslations(name);
                    String display = translations.size() == 1 ? translations.getFirst() : name;
                    units.add(new Unit(row, suffix.matchStatus(), suffix.displayStatus(), row,
                            suffix.chinese().isEmpty() ? "" : display + suffix.chinese(), suffix.candidates()));
                } else {
                    units.add(word(row, "", List.of("fixed"), "PCSelect."));
                }
            } else if (result.layout().equals("game_corner_prizes")) {
                var amount = PRIZE_PRICE.matcher(row);
                if (amount.matches()) {
                    String name = amount.group(1);
                    units.add(word(name, amount.group(2),
                            name.startsWith("わざマシン") ? List.of("item") : List.of("pokemon"), ""));
                } else {
                    units.add(word(row, "", List.of("fixed"), "PrizeMenu.Cancel"));
                }
            } else if (result.layout().equals("vending_items")) {
                var amount = PRICE.matcher(row);
                if (amount.matches()) units.add(word(amount.group(1), amount.group(2), List.of("item"), ""));
                else units.add(word(row, "", List.of("fixed"), "VendingMenu.Cancel"));
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
                var kinds = switch (result.layout()) {
                    case "battle_moves" -> List.of("move", "fixed");
                    case "shared_list" -> suffix.isEmpty() ? List.of("item", "fixed", "floor") : List.of("item", "fixed");
                    default -> List.of("item", "fixed");
                };
                units.add(word(name, suffix, kinds, ""));
            }
        }
        for (String footer : result.footerLines()) units.add(word(footer, "", List.of("fixed"), ""));
        return List.copyOf(units);
    }

    private Unit word(String text, String suffix, List<String> kinds, String control) {
        var entries = (kinds.isEmpty() ? corpus.pokemonNameWords(text) : corpus.words(text, kinds)).stream()
                .filter(e -> control.isEmpty() || e.id().contains(":fixed:" + control)).toList();
        var candidates = entries.stream().map(e -> new Candidate(e.id(), 1, e.japanese(), "whole_visible_unit",
                Map.of(), e.source(), e.revision().toString(), "")).toList();
        String raw = text + suffix;
        if (text.contains("�")) return new Unit(raw, "unreliable", "unknown_character", "", "", candidates);
        if (entries.isEmpty()) return unmatched(text, suffix, kinds.isEmpty());
        var chinese = entries.stream().map(e -> corpus.displayChinese(e, e.chinese()) + suffix).distinct().toList();
        if (chinese.size() != 1) return new Unit(raw, "ambiguous", "ambiguous", "", "", candidates);
        var japanese = entries.stream().map(e -> e.japanese() + suffix).distinct().toList();
        return new Unit(raw, entries.size() == 1 ? "unique" : "equivalent", "ready",
                japanese.size() == 1 ? japanese.getFirst() : raw, chinese.getFirst(), candidates);
    }

    public Unit dialogue(String regionId, String raw) {
        return dialogue(regionId, raw, false);
    }

    public Unit dialogue(String regionId, String raw, boolean waitingForInput) {
        String text = TextCorpus.clean(raw);
        if (text.isEmpty() || text.contains("�")) {
            histories.remove(regionId);
            return new Unit(raw, "unreliable", "unknown_character", "", "", List.of());
        }
        var hits = corpus.dialogue(text, waitingForInput, selectedNames);
        List<Progress> previous = histories.getOrDefault(regionId, List.of());
        List<Progress> progress = new ArrayList<>();
        List<Progress> continued = new ArrayList<>();
        Set<TextCorpus.Hit> observedContinuations = new HashSet<>();
        for (var hit : hits) {
            var fragment = hit.fragment();
            int start = fragment.first();
            boolean linked = false;
            boolean observed = false;
            for (var before : previous) {
                var old = before.hit().fragment();
                if (!old.entry().id().equals(fragment.entry().id())) continue;
                // Only adjacent source windows can establish continuous observed context.
                boolean same = hit.complete() && old.ordinal() == fragment.ordinal()
                        && before.hit().variables().equals(hit.variables());
                boolean next = fragment.previous() == old.ordinal() && fragment.first() <= old.ordinal() + 1;
                if (same || next) { start = Math.min(start, before.start()); linked = true; }
                observed |= next || (same && before.observedContinuation());
            }
            if (observed) observedContinuations.add(hit);
            if (!hit.complete()) continue;
            var value = new Progress(hit, start, observed);
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
        if (possible.isEmpty()) return unmatched(raw, "", false);
        // Learn only when every remaining source fully agrees that this visible value is PLAYER.
        // Region disappearance clears dialogue progress, not the session's confirmed identity.
        if (possible.stream().allMatch(h -> h.complete() && !h.tutorialActor() && h.variables().containsKey("PLAYER"))) {
            var names = possible.stream().map(h -> h.variables().get("PLAYER")).distinct().toList();
            if (names.size() == 1) playerName = names.getFirst();
        }
        // Prefer a fixed line with a source pause to reading its entire body as a name before the next line.
        // Apply the preference after recording source progress and any confirmed player identity.
        if (possible.stream().anyMatch(hit -> hit.complete() && hit.fragment().pauseEnd()
                && FIXED_DIALOGUE_LINE.matcher(hit.fragment().japanese()).matches())) {
            possible = possible.stream().filter(hit -> !hit.complete() || !hit.fragment().immediateLineEnd()
                    || !NAME_ONLY_DIALOGUE_LINE.matcher(hit.fragment().japanese()).matches()).toList();
        }
        List<Rendering> renderings = new ArrayList<>();
        List<Integer> starts = new ArrayList<>();
        for (var hit : possible) {
            int start = hit.fragment().first();
            for (var value : progress) if (value.hit().equals(hit)) start = value.start();
            starts.add(start);
            renderings.add(render(hit, start, false));
        }
        // A reviewed shared body can resolve a conflict caused only by an off-screen name.
        if (renderings.stream().distinct().count() != 1 && possible.stream().anyMatch(h ->
                h.fragment().displayPolicy().equals("shared_rolled_name"))) {
            List<Rendering> shared = new ArrayList<>(renderings);
            for (int i = 0; i < possible.size(); i++)
                if (possible.get(i).fragment().displayPolicy().equals("shared_rolled_name"))
                    shared.set(i, render(possible.get(i), starts.get(i), true));
            if (shared.stream().distinct().count() == 1 && !shared.getFirst().chinese().isEmpty()) renderings = shared;
        }
        List<Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < possible.size(); i++)
            candidates.add(candidate(possible.get(i), renderings.get(i).reusedPlayerName()));
        Rendering rendering = renderings.getFirst();
        List<TextCorpus.Hit> displayHits = possible;
        // Select a publishable fixed translation without changing source progress or diagnostic candidates.
        if (renderings.stream().distinct().count() != 1) {
            List<Rendering> fixed = new ArrayList<>();
            for (int i = 0; i < possible.size(); i++) {
                var hit = possible.get(i);
                if (hit.complete() && hit.fragment().slots().isEmpty()) fixed.add(renderings.get(i));
            }
            if (fixed.isEmpty() || !fixed.getFirst().canDisplay() || fixed.stream().distinct().count() != 1)
                return new Unit(raw, "ambiguous", "ambiguous", "", "", candidates);

            rendering = fixed.getFirst();
            displayHits = new ArrayList<>();
            for (int i = 0; i < possible.size(); i++) {
                var hit = possible.get(i);
                var other = renderings.get(i);
                if (other.canDisplay() && other.chinese().equals(rendering.chinese())) {
                    displayHits.add(hit);
                } else if (observedContinuations.contains(hit)
                        || !(hit.weakPrefix() || other.status().equals("variables_missing"))) {
                    return new Unit(raw, "ambiguous", "ambiguous", "", "", candidates);
                }
            }
        }
        var japanese = displayHits.stream().filter(TextCorpus.Hit::complete)
                .map(h -> substitute(h.fragment().japanese(), h.variables())).distinct().toList();
        return new Unit(raw, displayHits.size() == 1 ? "unique" : "equivalent", rendering.status(),
                japanese.isEmpty() ? "" : japanese.size() == 1 ? japanese.getFirst() : raw, rendering.chinese(), candidates);
    }

    private static Unit unmatched(String text, String suffix, boolean nickname) {
        String display = nickname || DISPLAY_SYMBOLS.matcher(text).matches() ? "passthrough" : "missing";
        return new Unit(text + suffix, "missing", display, "", "", List.of());
    }

    private Rendering render(TextCorpus.Hit hit, int start, boolean sharedBody) {
        var fragment = hit.fragment();
        String fallbackPlayerName = selectedNames.getOrDefault("PLAYER", playerName);
        String savedChinese = TextCorpus.slice(fragment.entry().chinese(), fragment.chineseStart(), fragment.chineseEnd());
        boolean useDisplay = !fragment.displayChinese().isEmpty();
        if (useDisplay && !sharedBody && (fragment.displayPolicy().equals("missing_rolled_name")
                || fragment.displayPolicy().equals("shared_rolled_name"))) {
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
        // These source pairs use different slot names for the same visible person:
        // the owner on the sign, or the recipient in the lab. Keep source bindings for identity tracking.
        String source = fragment.entry().id().substring(fragment.entry().id().indexOf(':') + 1);
        boolean sameVisiblePerson = switch (source) {
            case "scripts/PalletTown.asm:PalletTownPlayersHouseSignText",
                    "scripts/PalletTown.asm:PalletTownRivalsHouseSignText",
                    "scripts/OaksLab.asm:OaksLabReceivedMonText",
                    "scripts/OaksLab.asm:OaksLabRivalReceivedMonText.Text",
                    "scripts/OaksLab.asm:OaksLabReceivedText" -> true;
            default -> false;
        };
        // Both aide branches print hOaksAideRequirement in this window.
        // NUMBER1 in UhOhText instead counts the species already caught.
        String requiredPokedexCount = switch (source) {
            case "engine/events/oaks_aide.asm:OaksAideUhOhText" -> fragment.ordinal() == 6 ? "NUMBER2" : "";
            case "engine/events/oaks_aide.asm:OaksAideComeBackText" -> fragment.ordinal() == 3 ? "NUMBER1" : "";
            default -> "";
        };
        Map<String, String> comparedVariables = new LinkedHashMap<>();
        Map<String, String> kinds = new LinkedHashMap<>();
        displayedVariables.forEach((slot, value) -> {
            // In this reviewed prefix USER and TARGET both name the currently visible stat owner.
            String key = fragment.displayPolicy().equals("stat_change_prefix")
                    && (slot.equals("USER") || slot.equals("TARGET")) ? "OWNER" : slot;
            if (sameVisiblePerson && (slot.equals("PLAYER") || slot.equals("RIVAL"))) key = "VISIBLE_PERSON";
            if (slot.equals(requiredPokedexCount)) key = "REQUIRED_POKEDEX_COUNT";
            comparedVariables.put(key, value);
            kinds.put(key, corpus.variableKind(fragment.entry(), slot));
        });
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
        var speaker = SPEAKER_LABEL.matcher(fragment.japanese());
        if (!chinese.isEmpty() && speaker.find()) {
            String slot = speaker.group(1), placeholder = "{{" + slot + "}}";
            // A label-only name is already compared in the rendered Chinese, just like a fixed speaker.
            // Keep its original binding when the current Japanese or Chinese body also uses that name.
            if (savedChinese.startsWith(placeholder + "：")
                    && !savedChinese.substring(placeholder.length() + 1).contains(placeholder)
                    && !fragment.japanese().substring(speaker.end()).contains(placeholder)) {
                comparedVariables.remove(slot);
                kinds.remove(slot);
            }
        }
        return new Rendering(status, chinese, Map.copyOf(comparedVariables), Map.copyOf(kinds),
                Math.max(0, fragment.waiting() - fragment.ordinal()), reusedPlayerName);
    }

    private String translatedVariable(TextCorpus.Entry entry, String slot, String raw) {
        String kind = corpus.variableKind(entry, slot);
        boolean name = List.of("name", "nickname", "battle_name", "trainer_name", "trainer", "gym_leader").contains(kind);
        if (!name && !List.of("item", "move", "pokemon", "stat", "city", "game_corner_prize", "battle_ghost").contains(kind))
            return raw;
        boolean enemy = kind.equals("battle_name") && raw.startsWith("てきの ");
        String value = enemy ? raw.substring(4) : raw;
        boolean pokemonName = kind.equals("nickname") || kind.equals("battle_name");
        List<String> chinese;
        if (kind.equals("trainer_name")) {
            var shared = corpus.words(value, List.of("fixed")).stream()
                    .filter(e -> e.id().contains(":fixed:TrainerClass.") && e.games().equals(entry.games()))
                    .map(e -> corpus.displayChinese(e, e.chinese())).distinct().toList();
            chinese = shared.isEmpty() ? corpus.nameTranslations(value) : shared;
        } else if (name && !pokemonName) chinese = corpus.nameTranslations(value);
        else {
            var entries = pokemonName ? corpus.pokemonNameWords(value) : corpus.variableWords(value, kind);
            chinese = entries.stream().filter(e -> pokemonName || e.games().equals(entry.games()))
                    .map(e -> corpus.displayChinese(e, e.chinese())).distinct().toList();
        }
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
