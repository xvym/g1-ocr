package io.github.g1ocr.text;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Local, source-traceable resources only. No screenshots, labels or upstream checkout. */
public final class TextCorpus {
    public record Entry(String id, List<String> games, String kind, String japanese, String chinese,
                        String source, JsonNode revision, JsonNode variables, JsonNode evidence) {}
    public record Fragment(Entry entry, int ordinal, int previous, int first, String japanese,
                           String status, int chineseStart, int chineseEnd, int contextStart, int waiting,
                           String displayChinese, String displaySource, String displayPolicy,
                           List<String> literals, List<String> slots, Set<String> numbers) {}
    public record Hit(Fragment fragment, Map<String, String> variables, boolean complete) {}
    private record Binding(Map<String, String> variables, boolean complete) {}
    private static final Pattern SLOT = Pattern.compile("\\{\\{(\\w+)}}");
    // Pinned Japanese data/text/kana.asm plus dakutens.asm; marks modify kana, not names themselves.
    private static final Pattern NAME_CHARACTERS = Pattern.compile(
            "[ あいうえおかがきぎくぐけげこごさざしじすずせぜそぞただちぢっつづてでとどなにぬねのはばぱひびぴふぶぷへべぺほぼぽまみむめもゃやゅゆょよらりるれろわん"
            + "アイウエオカガキギクグケゲコゴサザシジスズセゼソゾタダチヂッツヅテデトドナニヌネノハバパヒビピフブプヘベペホボポマミムメモャヤュユョヨラリルレロワンー]+");
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Map<String, List<Entry>> words = new LinkedHashMap<>();
    private final Map<String, List<Fragment>> exact = new LinkedHashMap<>();
    private final List<Fragment> templates = new ArrayList<>();
    private final Map<String, String> slotKinds = new LinkedHashMap<>();

    public TextCorpus() throws IOException {
        JsonMapper json = JsonMapper.builder().build();
        for (var row : csv("slot_rules.csv")) slotKinds.put(row.get("label") + ":" + row.get("slot"), row.get("kind"));
        for (var row : csv("records.csv")) {
            Entry entry = new Entry(row.get("id"), Arrays.asList(row.get("games").split(";")), row.get("kind"),
                    row.get("japanese_ocr"), row.get("chinese"), row.get("chinese_repo") + ":" + row.get("chinese_source"),
                    json.readTree(row.get("chinese_revision").isEmpty() ? "null" : row.get("chinese_revision")),
                    json.readTree(row.get("variables")), json.readTree(row.get("evidence").startsWith("{")
                            || row.get("evidence").startsWith("[") ? row.get("evidence") : "null"));
            if (!row.get("mapping_status").equals("aligned") || !row.get("render_status").equals("parsed")) continue;
            entries.put(entry.id(), entry);
            if (!entry.kind().startsWith("dialogue"))
                words.computeIfAbsent(canonical(entry.japanese()), key -> new ArrayList<>()).add(entry);
        }
        for (var row : csv("fragments.csv")) {
            Entry entry = entries.get(row.get("record_id"));
            if (entry == null || !entry.kind().startsWith("dialogue")) continue;
            String japanese = canonical(row.get("japanese_ocr"));
            var slots = new ArrayList<String>();
            var literals = new ArrayList<String>();
            var numbers = new java.util.HashSet<String>();
            var matcher = SLOT.matcher(japanese);
            int end = 0;
            while (matcher.find()) {
                literals.add(japanese.substring(end, matcher.start()));
                String slot = matcher.group(1);
                slots.add(slot);
                for (var variable : entry.variables())
                    if (variable.path("slot").asText().equals(slot) && variable.path("kind").asText().equals("NUMBER"))
                        numbers.add(slot);
                end = matcher.end();
            }
            literals.add(japanese.substring(end));
            int ordinal = integer(row, "ordinal");
            Fragment fragment = new Fragment(entry, ordinal, integer(row, "previous_ordinal"),
                    ordinal - japanese.split("\n").length + 1, row.get("japanese_ocr"), row.get("status"),
                    integer(row, "chinese_start"), integer(row, "chinese_end"),
                    integer(row, "context_start_ordinal"), integer(row, "waiting_for_ordinal"),
                    row.get("display_chinese"), row.get("display_source"), row.get("display_policy"),
                    List.copyOf(literals), List.copyOf(slots), Set.copyOf(numbers));
            if (slots.isEmpty()) exact.computeIfAbsent(japanese, key -> new ArrayList<>()).add(fragment);
            else templates.add(fragment);
        }
    }

    public List<String> presetNames() {
        return entries.values().stream().filter(entry -> entry.id().contains(":fixed:DefaultName."))
                .map(Entry::japanese).distinct().toList();
    }

    /** An empty kind list searches every standalone word, for names and nicknames. */
    public List<Entry> words(String text, List<String> kinds) {
        return words.getOrDefault(canonical(text), List.of()).stream()
                .filter(e -> kinds.isEmpty() || kinds.contains(e.kind())).toList();
    }

    public List<String> nameTranslations(String name) {
        return words(name, List.of()).stream().map(entry -> displayChinese(entry, entry.chinese()))
                .distinct().toList();
    }

    public List<Hit> dialogue(String text) {
        String key = canonical(text);
        List<Hit> hits = new ArrayList<>();
        for (var fragment : exact.getOrDefault(key, List.of())) hits.add(new Hit(fragment, Map.of(), true));
        for (var item : exact.entrySet()) if (!item.getKey().equals(key) && item.getKey().startsWith(key))
            for (var fragment : item.getValue()) hits.add(new Hit(fragment, Map.of(), false));
        for (var fragment : templates) {
            List<Binding> bindings = new ArrayList<>();
            bind(fragment, text, key, 0, 0, false, new LinkedHashMap<>(), bindings);
            for (var binding : bindings)
                if (validComposition(fragment.entry(), binding.variables()))
                    hits.add(new Hit(fragment, binding.variables(), binding.complete()));
        }
        Set<String> completeRecords = new java.util.HashSet<>();
        for (var hit : hits) if (hit.complete()) completeRecords.add(hit.fragment().entry().id());
        // A later window from the same source is not another source candidate for its completed earlier window.
        return hits.stream().filter(h -> h.complete() || !completeRecords.contains(h.fragment().entry().id())).distinct().toList();
    }

    private void bind(Fragment fragment, String raw, String text, int part, int offset, boolean anchored,
                      Map<String, String> variables, List<Binding> matches) {
        String literal = fragment.literals().get(part);
        if (literal.startsWith(text.substring(offset)) && literal.length() > text.length() - offset) {
            if (anchored || offset < text.length()) matches.add(new Binding(Map.copyOf(variables), false));
            return;
        }
        if (!text.startsWith(literal, offset)) return;
        offset += literal.length();
        anchored |= !literal.isEmpty();
        if (part == fragment.slots().size()) {
            if (offset == text.length()) matches.add(new Binding(Map.copyOf(variables), true));
            return;
        }
        if (offset == text.length()) {
            if (anchored) matches.add(new Binding(Map.copyOf(variables), false));
            return;
        }
        String slot = fragment.slots().get(part), previous = variables.get(slot);
        for (int end = offset + 1; end <= text.length() && text.charAt(end - 1) != '\n'; end++) {
            String value = raw.substring(offset, end);
            if (!value.equals(value.strip()) || value.isBlank()) continue;
            String kind = variableKind(fragment.entry(), slot);
            String name = kind.equals("battle_name") && value.startsWith("てきの ") ? value.substring(4) : value;
            if (List.of("name", "nickname", "battle_name", "trainer_name").contains(kind)
                    && !validName(name, kind, false)) continue;
            // NAME_LENGTH=6 includes the terminator in the pinned Japanese source.
            if (List.of("name", "nickname", "battle_name").contains(kind) && name.length() > 5) continue;
            if (kind.equals("trainer_name") && name.length() > 5 && variableWords(value, "trainer").isEmpty()) continue;
            if (List.of("item", "move", "trainer", "pokemon", "stat", "city", "gym_leader").contains(kind)
                    && variableWords(value, kind).stream().noneMatch(e -> e.games().equals(fragment.entry().games()))) continue;
            if (previous != null && !canonical(previous).equals(text.substring(offset, end))) continue;
            if ((fragment.numbers().contains(slot) || kind.equals("number")) && !value.matches("[0-9]+")) continue;
            if (kind.equals("literal") && end == text.length() && !fragment.literals().get(part + 1).isEmpty()) continue;
            variables.put(slot, value);
            bind(fragment, raw, text, part + 1, end, anchored, variables, matches);
        }
        if (previous == null) variables.remove(slot); else variables.put(slot, previous);
        // A partially drawn value is evidence for a possible source, never a bound display variable.
        String remaining = raw.substring(offset), kind = variableKind(fragment.entry(), slot);
        String name = kind.equals("battle_name") && remaining.startsWith("てきの ") ? remaining.substring(4) : remaining;
        if (anchored && !remaining.contains("\n") && !remaining.isBlank()) {
            boolean possible = switch (kind) {
                case "name", "nickname", "trainer_name", "battle_name" ->
                        name.length() < 5 && validName(name, kind, true);
                case "item", "move", "trainer", "pokemon", "stat", "city", "gym_leader" -> words.entrySet().stream()
                        .anyMatch(e -> e.getKey().startsWith(canonical(remaining)) &&
                                variableWords(e.getKey(), kind).stream()
                                .anyMatch(v -> v.games().equals(fragment.entry().games())));
                default -> (fragment.numbers().contains(slot) || kind.equals("number")) && remaining.matches("[0-9]+");
            };
            if (possible) matches.add(new Binding(Map.copyOf(variables), false));
        }
    }

    private boolean validName(String value, String kind, boolean prefix) {
        if (NAME_CHARACTERS.matcher(value).matches()) return true;
        // Default species (e.g. ニドラン♀/ケーシィ) and fixed trainers may use non-input characters.
        if (kind.equals("name")) return false;
        String wordKind = kind.equals("trainer_name") ? "trainer" : "pokemon";
        String key = canonical(value);
        if (!prefix) return !words(value, List.of(wordKind)).isEmpty();
        return words.entrySet().stream().anyMatch(e -> e.getKey().startsWith(key)
                && e.getValue().stream().anyMatch(v -> v.kind().equals(wordKind)));
    }

    String variableKind(Entry entry, String slot) {
        if (slot.equals("PLAYER") || slot.equals("RIVAL")) return "name";
        if (slot.equals("USER") || slot.equals("TARGET")) return "battle_name";
        if (slot.equals("SPEAKER")) return "trainer_name";
        if (entry.id().contains(":composed:UsedMove.") && slot.equals("RAM1")) return "move";
        if (entry.id().contains(":composed:Experience.") && slot.equals("RAM1")) return "nickname";
        if (entry.id().contains(":composed:StatChange.") && slot.equals("RAM1")) return "stat";
        String label = entry.id().substring(entry.id().lastIndexOf(':') + 1);
        String declared = slotKinds.get(label + ":" + slot);
        if (declared != null) return declared;
        for (var variable : entry.variables()) if (variable.path("slot").asText().equals(slot)) {
            String address = variable.path("address").asText();
            if (address.equals("wInGameTradeGiveMonName") || address.equals("wInGameTradeReceiveMonName")
                    || address.equals("wNameOfPlayerMonToBeTraded")) return "pokemon";
            if (address.contains("Nick") || address.equals("wDayCareMonName") || address.equals("wLearnMoveMonName")) return "nickname";
            if (address.equals("wGymCityName")) return "city";
            if (address.equals("wGymLeaderName")) return "gym_leader";
            if (address.equals("wLinkEnemyTrainerName")) return "name";
            if (address.equals("wBoxNumString")) return "number";
            if (address.equals("wTrainerName")) return "trainer_name";
            if (address.equals("wOaksAideRewardItemName")) return "item";
        }
        return "literal";
    }

    String enemyPrefix(Entry entry) {
        Entry source = entries.get(entry.id().substring(0, entry.id().indexOf(':'))
                + ":engine/battle/core.asm:EnemyMonFaintedText");
        return source.chinese().substring(0, source.chinese().indexOf("{{"));
    }

    String displayChinese(Entry entry, String text) {
        if (!text.contains("#")) return text;
        Entry pokemon = words("ポケモン", List.of("fixed")).stream()
                .filter(e -> e.games().equals(entry.games()) && e.id().contains(":fixed:BattleMenu.")).findFirst().orElseThrow();
        return text.replace("#", pokemon.chinese());
    }

    List<Entry> variableWords(String text, String kind) {
        if (kind.equals("city") || kind.equals("gym_leader")) return words(text, List.of("fixed")).stream()
                .filter(e -> e.id().contains(":fixed:" + (kind.equals("city") ? "GymCity." : "GymLeader."))).toList();
        if (kind.equals("stat")) return words(text, List.of("fixed")).stream()
                .filter(e -> e.id().contains(":fixed:StatMod.")).toList();
        if (!kind.equals("trainer")) return words(text, List.of(kind));
        return words(text, List.of("trainer", "fixed")).stream()
                .filter(e -> e.kind().equals("trainer") || e.id().contains(":fixed:TrainerNamePointers.")).toList();
    }

    private boolean validComposition(Entry entry, Map<String, String> variables) {
        if (!entry.id().contains(":composed:UsedMove.")) return true;
        String move = variables.get("RAM1");
        if (move == null) return true;
        var matches = words(move, List.of("move")).stream().filter(e -> e.games().equals(entry.games())).toList();
        if (matches.size() != 1) return false;
        String symbol = matches.getFirst().id().substring(matches.getFirst().id().lastIndexOf(':') + 1);
        var detail = entry.evidence().path("detail");
        if (!detail.path("default_set").asBoolean())
            return java.util.stream.StreamSupport.stream(detail.path("move_symbols").spliterator(), false)
                    .anyMatch(s -> s.asText().equals(symbol));
        return entries.values().stream().filter(e -> e.games().equals(entry.games()) && e.id().contains(":composed:UsedMove."))
                .flatMap(e -> java.util.stream.StreamSupport.stream(e.evidence().path("detail").path("move_symbols").spliterator(), false))
                .noneMatch(s -> s.asText().equals(symbol));
    }

    static String clean(String text) {
        return String.join("\n", text.lines().map(line -> line.strip().replaceAll(" +", " ")).toList());
    }
    static String canonical(String text) {
        return Normalizer.normalize(clean(text), Normalizer.Form.NFC)
                .replace('リ', 'り').replace('ヘ', 'へ').replace('ベ', 'べ').replace('ペ', 'ぺ');
    }
    static String slice(String text, int start, int end) {
        return text.substring(text.offsetByCodePoints(0, start), text.offsetByCodePoints(0, end));
    }
    private static int integer(Map<String, String> row, String column) {
        return row.get(column).isEmpty() ? 0 : Integer.parseInt(row.get(column));
    }

    /** RFC 4180 quoted fields, including real newlines; our checked resources use UTF-8 BOM/LF. */
    private static List<Map<String, String>> csv(String name) throws IOException {
        try (var reader = new PushbackReader(new InputStreamReader(
                TextCorpus.class.getResourceAsStream("/corpus/" + name), StandardCharsets.UTF_8), 1)) {
            reader.read(); // Checked resources begin with a UTF-8 BOM.
            List<String> header = null;
            List<Map<String, String>> rows = new ArrayList<>();
            List<String> values = new ArrayList<>();
            StringBuilder field = new StringBuilder();
            boolean quoted = false;
            int c;
            while ((c = reader.read()) != -1) {
                if (c == '"') {
                    if (quoted) {
                        int next = reader.read();
                        if (next == '"') { field.append('"'); continue; }
                        quoted = false;
                        if (next != -1) reader.unread(next);
                    } else quoted = true;
                } else if (!quoted && (c == ',' || c == '\n')) {
                    values.add(field.toString()); field.setLength(0);
                    if (c == '\n') {
                        if (header == null) header = List.copyOf(values);
                        else {
                            Map<String, String> row = new LinkedHashMap<>();
                            for (int i = 0; i < header.size(); i++) row.put(header.get(i), values.get(i));
                            rows.add(row);
                        }
                        values.clear();
                    }
                } else field.append((char) c);
            }
            return rows;
        }
    }
}
