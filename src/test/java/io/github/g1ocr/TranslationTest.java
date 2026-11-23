package io.github.g1ocr;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.g1ocr.image.GbFrame;
import io.github.g1ocr.ocr.PixelOcr;
import io.github.g1ocr.region.RegionDetector.Rect;
import io.github.g1ocr.text.TextCorpus;
import io.github.g1ocr.text.TextResolver;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.text.Normalizer;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TranslationTest {
    private static TextCorpus corpus;
    @BeforeAll static void load() throws Exception { corpus = new TextCorpus(); }

    @Test void november21MenusKeepVisibleRowsAndIndependentDialogue() throws Exception {
        record Scene(String file, String layout, int cursor, List<String> japanese,
                     List<String> chinese, List<String> dialogue) {}
        var scenes = List.of(
                new Scene("prize-menu", "game_corner_prizes", 0,
                        List.of("ケーシィ 180", "ピッピ 500", "ニドりーナ 1200", "いらない"),
                        List.of("凯西 180", "皮皮 500", "尼多娜 1200", "还是算了"), List.of("なにと こうかんする？")),
                new Scene("pc-select", "pc_select", 0,
                        List.of("マサキの パソコン", "レッドの パソコン", "オーキドの パソコン", "スイッチを きる"),
                        List.of("正辉的电脑", "赤红的电脑", "大木的电脑", "关机"), List.of()),
                new Scene("pc-select-obscured", "pc_select", 0,
                        List.of("マサキの パソコン", "レッドの パソコン", "オーキドの パソコン", "スイッチを きる"),
                        List.of("正辉的电脑", "赤红的电脑", "大木的电脑", "关机"), List.of()),
                new Scene("load-game", "", -1, List.of(), List.of(), List.of()),
                new Scene("safari-action", "safari_action", 0,
                        List.of("サファりボール×30", "エサをなげる", "いしをなげる", "にげる"),
                        List.of("狩猎球×30", "诱饵", "石块", "逃走"), List.of()),
                new Scene("vending-menu", "vending_items", 1,
                        List.of("おいしいみず 200円", "サイコソーダ 300円", "ミックスオレ 350円", "いらない"),
                        List.of("美味之水 200円", "劲爽汽水 300円", "果汁牛奶 350円", "取消"),
                        List.of("じどう はんばいき だ！", "ほしい のみものは…… ……")),
                new Scene("elevator", "shared_list", 0, List.of("1かい", "2かい", "3かい", "4かい"),
                        List.of("1楼", "2楼", "3楼", "4楼"), List.of("なんかいへ いきますか？")));
        var pipeline = new OcrPipeline();
        for (var scene : scenes) {
            var image = ImageIO.read(getClass().getResource("/" + scene.file() + "-20261121.png"));
            var upper = new BufferedImage(400, 240, BufferedImage.TYPE_INT_RGB);
            var graphics = upper.createGraphics();
            graphics.drawImage(image, 120, 48, null);
            graphics.dispose();
            var frame = GbFrame.normalize(upper, new GbFrame.Mapping(0, 0, 1));
            if (scene.layout().isEmpty()) {
                assertTrue(pipeline.regions(frame).isEmpty(), scene.file());
                continue;
            }
            var results = pipeline.recognize(frame);
            assertEquals(scene.dialogue().isEmpty() ? List.of(scene.layout()) : List.of(scene.layout(), "dialogue"),
                    results.stream().map(PixelOcr.Result::layout).toList(), scene.file());
            assertEquals(scene.japanese(), results.getFirst().rawOcr(), scene.file());
            assertEquals(scene.cursor(), results.getFirst().selectedItem(), scene.file());
            if (scene.file().equals("elevator")) assertEquals("elevator_floors", results.getFirst().semanticType());
            assertEquals(scene.chinese(), new TextResolver(corpus).resolve(results.getFirst()).stream()
                    .map(TextResolver.Unit::chinese).toList(), scene.file());
            if (!scene.dialogue().isEmpty()) assertEquals(scene.dialogue(), results.getLast().rawOcr(), scene.file());
        }
    }

    @Test void november21DialogueUsesFixedRewardPauseWindowsAndTrainerNames() {
        var resolver = new TextResolver(corpus);
        resolver.selectPlayerName("レッド");
        var reward = resolver.dialogue("reward", "レッドは おんなのこから\nわざマシン49を もらった！", true);
        assertEquals("赤红从女孩那里\n得到了TM49！", reward.chinese());
        assertTrue(reward.candidates().stream().allMatch(c -> c.recordId().endsWith("ReceivedTM49Text")));
        assertEquals("赤红从女孩那里\n得到了TM48！",
                resolver.dialogue("otherReward", "レッドは おんなのこから\nわざマシン48を もらった！", true).chinese());
        assertEquals("incomplete", resolver.dialogue("guard", "おいしそうな のみもの……").displayStatus());
        assertEquals("啊，那个饮料\n看上去很好喝啊……", resolver.dialogue("guard", "おいしそうな のみもの……", true).chinese());
        assertEquals("咕噜咕噜……", resolver.dialogue("guard", "ゴクゴク……", true).chinese());
        assertEquals("精英训练家\n前来挑战了！",
                resolver.dialogue("trainer", "エりートトレーナーが\nしょうぶを しかけてきた！", true).chinese());
        assertEquals("精英♀：你可真厉害啊！",
                resolver.dialogue("trainer", "エりート♀『やる じゃない！", true).chinese());
    }

    @Test void vendingDeliveryAndBothGameCornerPrizeTypesUseTheDictionary() {
        var resolver = new TextResolver(corpus);
        assertEquals("劲爽汽水\n已滑落至取物口！",
                resolver.dialogue("dialogue", "サイコソーダが でてきた！").chinese());
        assertEquals("选择多边兽\n没问题吧？",
                resolver.dialogue("dialogue", "ポリゴンで\nいいんだね？").chinese());
        assertEquals("选择TM50\n没问题吧？",
                resolver.dialogue("dialogue", "わざマシン50で\nいいんだね？").chinese());
        assertTrue(resolver.dialogue("dialogue", "キズぐすりで\nいいんだね？").candidates().stream()
                .noneMatch(c -> c.recordId().endsWith(":SoYouWantPrizeText")));
    }

    @Test void towerGhostAppearanceUsesTheFixedBattleName() {
        var result = new TextResolver(corpus).dialogue("dialogue", "ゆうれいが\nあらわれた！", true);
        assertEquals("ゆうれいが\nあらわれた！", result.rawJapanese());
        assertEquals("幽灵\n扑过来了！", result.chinese());
        assertTrue(result.candidates().stream().allMatch(candidate ->
                candidate.recordId().endsWith(":EnemyAppearedText")
                        && "ゆうれい".equals(candidate.variables().get("RAM1"))));
        assertTrue(corpus.pokemonNameWords("ゆうれい").isEmpty());
        assertTrue(corpus.nameTranslations("ゆうれい").isEmpty());
    }

    @Test void novemberReportsTranslateTypedSlotsAndSharedParcelBody() {
        var resolver = new TextResolver(corpus);
        assertEquals("碧蓝得到了\n伤药！", resolver.dialogue("dialogue",
                "ブルーは\n「キズぐすり」を もらった！").chinese());
        assertEquals("杰尼龟学会了\n泡沫！", resolver.dialogue("dialogue",
                "ゼニガメは あたらしく\nあわを おぼえた！").chinese());
        var map = resolver.dialogue("dialogue", "ブルーは おねえさんから\nタウンマップを もらった！");
        assertEquals("碧蓝从姐姐那里\n得到了城镇地图！", map.chinese());
        assertTrue(map.candidates().stream().allMatch(c -> c.variables().get("RAM1").equals("タウンマップ")));
        resolver.selectPlayerName("ブルー");
        var parcel = resolver.dialogue("dialogue", "とくせいの モンスターボールじゃ\nどうも ありがとよ！");
        assertEquals("哦哦！这不是\n我预订的特殊精灵球嘛。\n真是谢谢你了！", parcel.chinese());
        assertEquals(3, parcel.candidates().size());
    }

    @Test void oaksAideScrollUsesVisibleVariablesAndKeepsBothEquivalentBranches() {
        var resolver = new TextResolver(corpus);
        resolver.selectNames("レッド", "グリーン");
        var frames = List.of(
                Map.entry("ポケモンずかんが 30しゅるい\nあつまってる ようなら",
                        "大木博士吩咐我，等你\n收集到30种宝可梦时，"),
                Map.entry("あつまってる ようなら\nダウジングマシンを わたす よう", "就把探宝器交给你！"),
                Map.entry("ダウジングマシンを わたす よう\nはかせに いわれ ました", "就把探宝器交给你！"),
                Map.entry("…… それでは さっそく\nレッドくん！", "……那么，赤红！"),
                Map.entry("レッドくん！\nつかまえた ポケモンは", "……那么，赤红！\n你捉到的宝可梦"),
                Map.entry("つかまえた ポケモンは\n30しゅるい より おおい かな？",
                        "……那么，赤红！\n你捉到的宝可梦\n有30种以上吗？"),
                Map.entry("…… …… そう ですか", "……这样啊。"),
                Map.entry("30しゅるいに たりない なら\nダウジングマシンは",
                        "如果还没到30种，\n我只能以后再把\n探宝器交给你了。"));
        for (var frame : frames) {
            var translation = resolver.dialogue("dialogue", frame.getKey(), true);
            assertEquals(frame.getKey(), translation.rawJapanese());
            assertEquals(frame.getValue(), translation.chinese(), frame.getKey());
        }

        var refusal = new TextResolver(corpus);
        assertEquals("……我看看！\n你捕捉到的宝可梦……", refusal.dialogue("dialogue",
                "…… …… えーと！\nつかまえた ポケモンは……", true).chinese());
        assertEquals("你捕捉到的宝可梦……\n啊呀呀？", refusal.dialogue("dialogue",
                "つかまえた ポケモンは……\nあれれー？", true).chinese());

        var isolated = new TextResolver(corpus).dialogue("dialogue",
                "50しゅるいに たりない なら\nがくしゅうそうちは", true);
        assertEquals("equivalent", isolated.matchStatus());
        assertEquals("ready", isolated.displayStatus());
        assertEquals("如果还没到50种，\n我只能以后再把\n学习装置交给你了。", isolated.chinese());
        assertEquals(6, isolated.candidates().size());
        assertEquals(3, isolated.candidates().stream().filter(c -> c.recordId().endsWith(":OaksAideUhOhText")
                && c.variables().equals(Map.of("NUMBER2", "50", "RAM1", "がくしゅうそうち"))).count());
        assertEquals(3, isolated.candidates().stream().filter(c -> c.recordId().endsWith(":OaksAideComeBackText")
                && c.variables().equals(Map.of("NUMBER1", "50", "RAM1", "がくしゅうそうち"))).count());
    }

    @Test void independentScenarios() throws Exception {
        var json = JsonMapper.builder().build();
        var fixture = json.readTree(Path.of("src/test/resources/chinese-cases.json").toFile());
        var results = new ArrayList<Object>();
        var failures = new ArrayList<String>();
        int assertions = 0;
        for (var scenario : fixture.path("scenarios")) {
            var resolver = new TextResolver(corpus);
            for (var event : scenario.path("events")) {
                if (event.path("reset").asBoolean()) { resolver.reset(); continue; }
                if (event.path("disappear").asBoolean()) { resolver.retainRegions(Set.of()); continue; }
                var actual = resolver.dialogue("dialogue", event.path("raw").asText(), event.path("waitingForInput").asBoolean());
                boolean pass = actual.rawJapanese().equals(event.path("raw").asText())
                        && actual.matchStatus().equals(event.path("match").asText())
                        && actual.displayStatus().equals(event.path("status").asText())
                        && actual.chinese().equals(event.path("chinese").asText())
                        && (!event.has("source") || actual.candidates().stream()
                        .allMatch(c -> c.recordId().endsWith(event.path("source").asText())))
                        && (!event.has("includesSource") || actual.candidates().stream()
                        .anyMatch(c -> c.recordId().endsWith(event.path("includesSource").asText())))
                        && (!event.has("candidateCount") || actual.candidates().size() == event.path("candidateCount").asInt())
                        && (!event.has("reusedPlayerName") || actual.candidates().stream()
                        .allMatch(c -> c.reusedPlayerName().equals(event.path("reusedPlayerName").asText())));
                results.add(java.util.Map.of("scenario", scenario.path("name").asText(), "expected", event, "actual", actual, "pass", pass));
                if (!pass) failures.add(scenario.path("name").asText() + ": " + json.writeValueAsString(actual));
                assertions++;
            }
        }
        var report = new LinkedHashMap<String, Object>();
        report.put("implementationVersion", System.getProperty("g1ocr.version"));
        report.put("scope", fixture.path("scope").asText());
        report.put("annotationMethod", fixture.path("method").asText());
        report.put("checks", assertions); report.put("passed", assertions - failures.size());
        report.put("realCaptureTested", false); report.put("finalChineseAcceptance", false);
        var hashes = new LinkedHashMap<String, String>();
        try (var paths = Files.walk(Path.of("src"))) {
            for (var path : paths.filter(Files::isRegularFile).sorted().toList())
                hashes.put(path.toString().replace('\\', '/'), HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))));
        }
        report.put("sha256", hashes); report.put("results", results);
        Files.createDirectories(Path.of("reports"));
        json.writerWithDefaultPrettyPrinter().writeValue(Path.of("reports/chinese-runtime.json").toFile(), report);
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    @Test void selectedPlayerNameWorksWithoutPriorDialogueAndSurvivesReset() {
        var resolver = new TextResolver(corpus);
        String raw = "カンナとの\nしょうぶに かった！";
        resolver.selectPlayerName("サトシ");
        var victory = resolver.dialogue("dialogue", raw);
        assertEquals("ready", victory.displayStatus());
        assertEquals("小智战胜了\n科拿！", victory.chinese());
        assertEquals(raw, victory.rawJapanese());
        assertTrue(victory.candidates().stream().allMatch(c -> c.variables().equals(Map.of("RAM1", "カンナ"))
                && c.reusedPlayerName().equals("サトシ")));
        resolver.reset();
        assertEquals("小智战胜了\n科拿！", resolver.dialogue("dialogue", raw).chinese());
        resolver.selectPlayerName("レッド");
        assertEquals("赤红战胜了\n科拿！", resolver.dialogue("dialogue", raw).chinese());
        resolver.selectPlayerName(null);
        assertEquals("variables_missing", resolver.dialogue("dialogue", raw).displayStatus());
    }

    @Test void selectedPlayerNameConstrainsVisibleBindingsAndCanStillFillAnOffscreenName() {
        var resolver = new TextResolver(corpus);
        String bicycle = "レッドは ひきかえけんで\nじてんしゃを もらった！";
        resolver.selectPlayerName("サトシ");
        var mismatched = resolver.dialogue("dialogue", bicycle);
        assertEquals(bicycle, mismatched.rawJapanese());
        assertTrue(mismatched.candidates().stream().noneMatch(c -> c.variables().containsKey("PLAYER")));
        assertEquals("小智战胜了\n科拿！", resolver.dialogue("dialogue", "カンナとの\nしょうぶに かった！").chinese());
        resolver.selectPlayerName("レッド");
        var visible = resolver.dialogue("dialogue", bicycle);
        assertEquals("赤红用兑换券\n得到了自行车！", visible.chinese());
        assertTrue(visible.candidates().stream().allMatch(c -> c.reusedPlayerName().isEmpty()));
        resolver.selectPlayerName(null);
        assertEquals("variables_missing", resolver.dialogue("dialogue", "カンナとの\nしょうぶに かった！").displayStatus());
    }

    @Test void bothConfiguredNamesSeparateSourcesAndKeepTheVisibleOcrSpelling() {
        var resolver = new TextResolver(corpus);
        resolver.selectNames("ブルー", "グリーン");
        assertEquals("站住！", resolver.dialogue("dialogue", "まちなー！", true).chinese());
        var rival = resolver.dialogue("dialogue", "ここは グりーン のいえ");
        assertEquals("这里是\n青绿的家。", rival.chinese());
        assertTrue(rival.candidates().stream().anyMatch(c -> "グりーン".equals(c.variables().get("RIVAL"))));
        assertTrue(rival.candidates().stream().noneMatch(c -> c.variables().containsKey("PLAYER")));
        resolver.reset();
        var player = resolver.dialogue("dialogue", "ここは ブルー のいえ");
        assertTrue(player.candidates().stream().anyMatch(c -> "ブルー".equals(c.variables().get("PLAYER"))));
        assertTrue(player.candidates().stream().noneMatch(c -> c.variables().containsKey("RIVAL")));
        resolver.selectNames("ブルー", null);
        assertTrue(resolver.dialogue("dialogue", "ここは ブルー のいえ").candidates().stream()
                .anyMatch(c -> "ブルー".equals(c.variables().get("RIVAL"))));
    }

    @Test void scriptedPokeBallActorsDoNotBecomeThePlayer() {
        for (String actor : List.of("おじいさん", "オーキド")) {
            String raw = actor + "は\nモンスターボールを つかった！";
            for (String player : java.util.Arrays.asList(null, "ブルー")) {
                var resolver = new TextResolver(corpus);
                resolver.selectNames(player, "グリーン");
                var caught = resolver.dialogue("dialogue", raw, true);
                assertEquals(raw, caught.rawJapanese());
                assertTrue(caught.chinese().endsWith("使用了精灵球！"));
                assertNull(resolver.diagnostics().get("confirmedPlayerName"));
            }
        }
    }

    @Test void incompleteTextAndOpponentNamesDoNotEstablishPlayerIdentity() {
        for (String raw : List.of("サトシは ひきかえけんで\nじてんしゃを もら",
                "サト�は ひきかえけんで\nじてんしゃを もらった！", "カンナ『…… なんて やつなの！")) {
            var resolver = new TextResolver(corpus);
            resolver.dialogue("dialogue", raw);
            var victory = resolver.dialogue("dialogue", "カンナとの\nしょうぶに かった！");
            assertEquals("variables_missing", victory.displayStatus());
            assertEquals("", victory.chinese());
            assertTrue(victory.candidates().stream().allMatch(c -> c.reusedPlayerName().isEmpty()));
        }
    }

    @Test void fixedAndVariableSpeakerLabelsKeepBothSourcesWhenTheirTranslationsAgree() {
        for (var name : List.of(List.of("グりーン", "青绿"), List.of("あいうえお", "あいうえお"))) {
            String raw = "オーキド『" + name.getFirst() + "！";
            var result = new TextResolver(corpus).dialogue("dialogue", raw);
            assertEquals("equivalent", result.matchStatus());
            assertEquals("ready", result.displayStatus());
            assertEquals("大木：" + name.get(1) + "！", result.chinese());
            assertEquals(raw, result.rawJapanese());
            assertTrue(result.candidates().stream().anyMatch(c -> c.recordId().endsWith(":ChampionsRoomOakText")
                    && !c.variables().containsKey("RIVAL")));
            assertTrue(result.candidates().stream().anyMatch(c -> c.recordId().endsWith(":OaksLabRivalTakesText1")
                    && "オーキド".equals(c.variables().get("RIVAL"))));
            assertTrue(result.candidates().stream().allMatch(c -> name.getFirst().equals(c.variables().get("PLAYER"))
                    && c.reusedPlayerName().isEmpty()));
            var incomplete = new TextResolver(corpus).dialogue("dialogue", raw.substring(0, raw.length() - 1));
            assertEquals("", incomplete.chinese());
        }
    }

    @Test void oakComparisonWindowsDoNotRequireTheStarterNameAfterItScrollsAway() {
        String body = "比起启程收集\n宝可梦图鉴时的你，\n现在的你真的成长了不少！";
        var resolver = new TextResolver(corpus);
        var named = resolver.dialogue("dialogue", "はじめて ピカチュウを もらって\nポケモン ずかん あつめに");
        assertEquals("比起刚刚捕获\nピカチュウ后启程收集\n宝可梦图鉴时的你，\n现在的你真的成长了不少！", named.chinese());
        assertTrue(named.candidates().stream().allMatch(c -> "ピカチュウ".equals(c.variables().get("RAM1"))));
        for (String raw : List.of("ポケモン ずかん あつめに\nでかけた ころと くらべると",
                "でかけた ころと くらべると\nたくましく なったな！")) {
            var current = resolver.dialogue("dialogue", raw);
            assertEquals("equivalent", current.matchStatus());
            assertEquals("ready", current.displayStatus());
            assertEquals(body, current.chinese());
            assertEquals(raw, current.rawJapanese());
            assertEquals(3, current.candidates().size());
            assertTrue(current.candidates().stream().allMatch(c -> c.recordId().endsWith(":ChampionsRoomOakCongratulatesPlayerText.Text")
                    && c.variables().isEmpty() && c.reusedPlayerName().isEmpty()));
            assertEquals(body, new TextResolver(corpus).dialogue("dialogue", raw).chinese());
            assertEquals("", new TextResolver(corpus).dialogue("dialogue", raw.substring(0, raw.length() - 1)).chinese());
        }
    }

    @Test void sentencePunctuationCannotBecomeANameInAnotherTemplate() {
        for (var value : List.of(List.of("ウツボット", "大食花"), List.of("ピカチュウ", "皮卡丘"),
                List.of("あいうえお", "あいうえお"))) {
            String raw = value.getFirst() + "は たおれた！";
            var result = new TextResolver(corpus).dialogue("dialogue", raw);
            assertEquals("equivalent", result.matchStatus());
            assertEquals("ready", result.displayStatus());
            assertEquals(value.get(1) + "\n倒下了！", result.chinese());
            assertEquals(3, result.candidates().size());
            assertTrue(result.candidates().stream().allMatch(c -> c.recordId().endsWith(":PlayerMonFaintedText")));
            assertEquals(raw, result.rawJapanese());
        }
        for (String raw : List.of("カンナは たおれた！", "カンナは あ！", "カンナは ！"))
            assertTrue(corpus.dialogue(raw).stream().noneMatch(h -> h.fragment().entry().id().endsWith(":TrainerAboutToUseText")
                    || h.fragment().entry().id().endsWith(":AIBattleUseItemText")));
    }

    @Test void legalCustomNamesAndDefaultSpeciesStillParticipateInIncompleteMatches() {
        for (String raw : List.of("カンナは たおれたを", "カンナは シェ", "ジェントルマンは シェ"))
            assertTrue(corpus.dialogue(raw).stream().anyMatch(h ->
                    h.fragment().entry().id().endsWith(":TrainerAboutToUseText")));
        for (String name : List.of("ニドラン♀", "ニドラン♂", "ケーシィ", "ファイヤー", "モルフォン")) {
            var hits = corpus.dialogue(name + "は たおれた！").stream()
                    .filter(h -> h.fragment().entry().id().endsWith(":PlayerMonFaintedText")).toList();
            assertEquals(3, hits.size());
            assertTrue(hits.stream().allMatch(TextCorpus.Hit::complete));
        }
        var partial = new TextResolver(corpus).dialogue("dialogue", "ウツボットは たおれた");
        assertEquals("", partial.chinese());
    }

    @Test void presetNamesUseTheDictionaryAcrossSourcesAndKeepConflictsJapanese() {
        // JACK also occurs in the source as an unused trainer alias for Juggler.
        for (var name : List.of(List.of("レッド", "赤红"), List.of("サトシ", "小智"), List.of("ジャック", "ジャック"),
                List.of("グリーン", "青绿"), List.of("シゲル", "小茂"), List.of("ジョン", "约翰"),
                List.of("ブルー", "碧蓝"), List.of("ツネカズ", "恒和"), List.of("ジャン", "基恩"),
                List.of("ヒロシ", "阿溥"), List.of("イエロー", "小黄"))) {
            String raw = name.getFirst() + "は ひきかえけんで\nじてんしゃを もらった！";
            var result = new TextResolver(corpus).dialogue("dialogue", raw);
            assertEquals(name.get(1) + "用兑换券\n得到了自行车！", result.chinese());
            assertEquals(raw, result.rawJapanese());
            assertTrue(result.candidates().stream().allMatch(c -> name.getFirst().equals(c.variables().get("PLAYER"))));
        }
    }

    @Test void signsAndLabGiftsAgreeOnTheVisiblePersonWithoutInferringTheirRole() {
        var resolver = new TextResolver(corpus);
        for (var example : Map.of(
                "ここは グりーン のいえ", "这里是\n青绿的家。",
                "ここは レッド のいえ", "这里是\n赤红的家。",
                "レッドは オーキドから\nヒトカゲを もらった！", "赤红从大木那里\n得到了小火龙！",
                "グりーンは オーキドから\nゼニガメを もらった！", "青绿从大木那里\n得到了杰尼龟！").entrySet()) {
            var result = resolver.dialogue("dialogue", example.getKey());
            assertEquals("equivalent", result.matchStatus());
            assertEquals(example.getValue(), result.chinese());
            assertEquals(example.getKey(), result.rawJapanese());
            assertTrue(result.candidates().stream().anyMatch(c -> c.variables().containsKey("PLAYER")));
            assertTrue(result.candidates().stream().anyMatch(c -> c.variables().containsKey("RIVAL")));
        }
    }

    @Test void pcWithdrawalDialogueTranslatesTheVisibleItemName() {
        String raw = "パソコンつうしんで\nキズぐすりを ひきだした！";
        var result = new TextResolver(corpus).dialogue("dialogue", raw, true);
        assertEquals("ready", result.displayStatus());
        assertEquals("从电脑里取出了\n伤药。", result.chinese());
        assertEquals(raw, result.rawJapanese());
        assertEquals(3, result.candidates().size());
        assertTrue(result.candidates().stream().allMatch(c ->
                c.recordId().endsWith(":WithdrewItemText") && c.variables().equals(Map.of("RAM1", "キズぐすり"))));
    }

    @Test void sourceDefinedMovePaddingKeepsTheVisibleNameAndSentenceEnding() {
        for (var scenario : List.of(
                List.of("ライチュウの\nじごくぐるま ！", "雷丘\n使出了地狱翻滚！", "ライチュウ", "normal"),
                List.of("てきの ライチュウの\nじごくぐるま ！", "敌方雷丘\n使出了地狱翻滚！", "てきの ライチュウ", "normal"),
                List.of("あいうえおの\nじごくぐるま ！", "あいうえお\n使出了地狱翻滚！", "あいうえお", "normal"),
                List.of("ライチュウは めいれいをむしして\nじごくぐるま ！", "雷丘无视命令\n使出了地狱翻滚！", "ライチュウ", "disobedient"))) {
            String raw = scenario.getFirst();
            var result = new TextResolver(corpus).dialogue("dialogue", raw);
            assertEquals("equivalent", result.matchStatus());
            assertEquals("ready", result.displayStatus());
            assertEquals(scenario.get(1), result.chinese());
            assertEquals(raw, result.rawJapanese());
            assertEquals(raw, result.resolvedJapanese());
            assertEquals(3, result.candidates().size());
            assertTrue(result.candidates().stream().allMatch(c ->
                    c.recordId().endsWith(":composed:UsedMove.grammar4." + scenario.get(3))
                    && c.variables().equals(Map.of("USER", scenario.get(2), "RAM1", "じごくぐるま "))));
        }
        assertEquals("雷丘\n使出了地狱翻滚！", new TextResolver(corpus)
                .dialogue("dialogue", "ライチュウの\nじごくぐるま！").chinese());
        for (String raw : List.of("ライチュウの\nじごくぐる", "ライチュウの\nじごくぐるま",
                "ライチュウの\nじごくぐる� ！", "ライチュウの\nかみなり ！",
                "ライチュウは\nじごくぐるま を つかった！")) {
            var result = new TextResolver(corpus).dialogue("dialogue", raw);
            assertEquals("", result.chinese());
            assertEquals(raw, result.rawJapanese());
        }
    }

    @Test void pokemonNamesPreferSpeciesWhileHumanNamesKeepAllStandaloneMeanings() {
        for (var scenario : List.of(
                List.of("キクコは\nゴーストを くりだした！", "菊子派出了\n鬼斯通！"),
                List.of("ゴーストは\nゴーストを くりだした！", "ゴースト派出了\n鬼斯通！"),
                List.of("ゴーストは ひきかえけんで\nじてんしゃを もらった！", "ゴースト用兑换券\n得到了自行车！"),
                List.of("ゴーストは たおれた！", "鬼斯通\n倒下了！"),
                List.of("てきの ゴーストは たおれた！", "敌方鬼斯通\n倒下了！"),
                List.of("ゴーストの\nこうげきりょくが", "鬼斯通的\n攻击"),
                List.of("キクコは\nキズぐすりを くりだした！", "菊子派出了\n伤药！"),
                List.of("キクコは\nあいうえおを くりだした！", "菊子派出了\nあいうえお！"))) {
            var result = new TextResolver(corpus).dialogue("dialogue", scenario.getFirst());
            assertEquals("ready", result.displayStatus());
            assertEquals(scenario.get(1), result.chinese());
            assertEquals(scenario.getFirst(), result.rawJapanese());
        }
        var sent = new TextResolver(corpus).dialogue("dialogue", "キクコは\nゴーストを くりだした！");
        assertTrue(sent.candidates().stream().allMatch(c -> c.variables().get("RAM2").equals("ゴースト")));
        assertEquals(Set.of("鬼斯通", "幽灵"), Set.copyOf(corpus.nameTranslations("ゴースト")));
        assertEquals(List.of("幽灵"), corpus.words("ゴースト", List.of("fixed")).stream()
                .map(TextCorpus.Entry::chinese).distinct().toList());
    }

    @Test void statChangePrefixesAndResultsCoverAllSixStats() {
        for (var stat : List.of(List.of("こうげきりょく", "攻击"), List.of("ぼうぎょりょく", "防御"),
                List.of("すばやさ", "速度"), List.of("とくしゅのうりょく", "特殊能力"),
                List.of("めいちゅうりつ", "命中率"), List.of("かいひりつ", "闪避率"))) {
            for (var ending : List.of(List.of(" あがった！", "提高了！"), List.of(" さがった！", "降低了！"),
                    List.of("\nぐーんと あがった！", "\n大幅提高了！"),
                    List.of("\nがくっと さがった！", "\n大幅降低了！"))) {
                var resolver = new TextResolver(corpus);
                String raw = "ウツボットの\n" + stat.getFirst() + "が";
                var prefix = resolver.dialogue("dialogue", raw);
                assertEquals("equivalent", prefix.matchStatus());
                assertEquals("ready", prefix.displayStatus());
                assertEquals("大食花的\n" + stat.get(1), prefix.chinese());
                assertEquals(raw, prefix.rawJapanese());
                assertEquals(12, prefix.candidates().size());
                assertTrue(prefix.candidates().stream().anyMatch(c -> c.variables().containsKey("USER")));
                assertTrue(prefix.candidates().stream().anyMatch(c -> c.variables().containsKey("TARGET")));
                boolean rolled = ending.getFirst().startsWith("\n");
                String completeRaw = (rolled ? stat.getFirst() + "が" : raw) + ending.getFirst();
                var complete = resolver.dialogue("dialogue", completeRaw);
                assertEquals("ready", complete.displayStatus());
                assertEquals((rolled ? "" : "大食花的\n") + stat.get(1) + ending.get(1), complete.chinese());
                assertEquals(completeRaw, complete.rawJapanese());
                var cold = new TextResolver(corpus).dialogue("dialogue", completeRaw);
                assertEquals(complete.chinese(), cold.chinese());
            }
            assertEquals("", new TextResolver(corpus).dialogue("dialogue", "ウツボットの\n" + stat.getFirst()).chinese());
        }
        var enemy = new TextResolver(corpus).dialogue("dialogue", "てきの ウツボットの\nぼうぎょりょくが");
        assertEquals("敌方大食花的\n防御", enemy.chinese());
        var nickname = new TextResolver(corpus).dialogue("dialogue", "あいうえおの\nすばやさが");
        assertEquals("あいうえお的\n速度", nickname.chinese());
    }

    @Test void listFieldsAndVisualOcrRemainIndependent() throws Exception {
        var resolver = new TextResolver(corpus);
        var frame = GbFrame.normalize(ImageIO.read(Path.of("testpic/Screenshot 2026-10-20 21-22-46.png").toFile()),
                new GbFrame.Mapping(0, 0, 3));
        var original = new OcrPipeline().recognize(frame).getFirst();
        assertEquals(List.of("じてんしゃ", "ポケモンのふえ", "いいつりざお", "ボロのつりざお"), original.rawOcr());
        var translated = resolver.resolve(original);
        assertEquals(List.of("自行车", "宝可梦之笛", "好钓竿", "破旧钓竿"),
                translated.subList(0, 4).stream().map(TextResolver.Unit::chinese).toList());
        assertTrue(translated.subList(0, 4).stream().allMatch(u -> u.matchStatus().equals("equivalent") && u.candidates().size() == 3));
        assertEquals("关闭", translated.get(4).chinese());
        assertEquals(original.rawOcr(), new OcrPipeline().recognize(frame).getFirst().rawOcr());
        var items = new PixelOcr.Result("list", "shared_list", "ambiguous", new Rect(32, 16, 128, 88),
                List.of(), List.of(), List.of("キズぐすり ×12", "キズぐすり × 99"), List.of(), 0, List.of());
        var quantities = resolver.resolve(items);
        assertEquals(List.of("伤药 ×12", "伤药 × 99"), quantities.stream().map(TextResolver.Unit::chinese).toList());
        assertEquals(items.rawOcr(), quantities.stream().map(TextResolver.Unit::rawJapanese).toList());
        var nicknames = new PixelOcr.Result("list", "shared_list", "pc_pokemon", items.bounds(),
                List.of(), List.of(), List.of("やめる <Lv>12", "ピカチュウ <Lv>50", "あいうえお <Lv>3", "ゴースト <Lv>7", "やめる"), List.of(), 0, List.of());
        var pc = resolver.resolve(nicknames);
        assertEquals(List.of("取消 <Lv>12", "皮卡丘 <Lv>50", "あいうえお <Lv>3", "鬼斯通 <Lv>7", "取消"),
                pc.stream().map(TextResolver.Unit::display).toList());
        assertEquals(List.of("ready", "ready", "missing", "ready", "ready"),
                pc.stream().map(TextResolver.Unit::displayStatus).toList());
        assertEquals(nicknames.rawOcr(), pc.stream().map(TextResolver.Unit::rawJapanese).toList());
        var action = new PixelOcr.Result("action", "battle_action", "battle_action", items.bounds(),
                List.of(), List.of(), List.of("たたかう どうぐ", "ポケモン にげる"), List.of(), 2, List.of());
        assertEquals(List.of("战斗", "道具", "宝可梦", "逃走"),
                resolver.resolve(action).stream().map(TextResolver.Unit::chinese).toList());
    }

    /** Coverage measurement is not a claim that every emitted translation is semantically correct. */
    @Test void allScreenshots() throws Exception {
        var json = JsonMapper.builder().build();
        Path labelPath = Path.of("testpic/labels/japanese.json");
        var labels = json.readTree(labelPath.toFile());
        var regressions = json.readTree(Path.of("src/test/resources/chinese-regressions.json").toFile());
        int regressionImages = 0, regressionUnits = 0;
        var samples = new ArrayList<Object>();
        var summary = new LinkedHashMap<String, Object>();
        var failures = new ArrayList<String>();
        var pipeline = new OcrPipeline();
        java.util.function.UnaryOperator<String> canonical = text -> Normalizer.normalize(text, Normalizer.Form.NFC)
                .replace('リ', 'り').replace('ヘ', 'へ').replace('ベ', 'べ').replace('ペ', 'ぺ');
        summary.put("images", 0); summary.put("noTargetImages", 0);
        summary.put("allUnitsReadyChineseImages", 0); summary.put("partialChineseImages", 0);
        summary.put("noChineseImages", 0); summary.put("units", 0); summary.put("unitsWithChinese", 0);
        summary.put("displayStatusCounts", new LinkedHashMap<String, Integer>());
        for (var sample : labels.path("samples")) {
            String id = sample.path("sample_id").asText();
            Path imagePath = labelPath.getParent().resolve(sample.path("file").asText()).normalize();
            var frame = GbFrame.normalize(ImageIO.read(imagePath.toFile()), new GbFrame.Mapping(0, 0, 3));
            var actual = pipeline.recognize(frame);
            var expected = new ArrayList<com.fasterxml.jackson.databind.JsonNode>();
            sample.path("regions").forEach(r -> { if (!r.path("empty").asBoolean()) expected.add(r); });
            boolean japanesePass = actual.size() == expected.size();
            for (int i = 0; i < Math.min(actual.size(), expected.size()); i++) {
                var answer = expected.get(i);
                var lines = new ArrayList<String>(); answer.path("lines").forEach(l -> lines.add(canonical.apply(l.asText())));
                var footer = new ArrayList<String>(); answer.path("footer_lines").forEach(l -> footer.add(canonical.apply(l.asText())));
                japanesePass &= lines.equals(actual.get(i).rawOcr().stream().map(canonical).toList())
                        && footer.equals(actual.get(i).footerLines().stream().map(canonical).toList());
            }
            if (!japanesePass) failures.add(id + " raw Japanese mismatch");
            // Every image is independent; filename order supplies no historical context.
            var resolver = new TextResolver(corpus);
            var regions = new ArrayList<Object>();
            var units = new ArrayList<TextResolver.Unit>();
            for (var region : actual) {
                var translated = resolver.resolve(region);
                units.addAll(translated);
                regions.add(java.util.Map.of("id", region.id(), "layout", region.layout(),
                        "semanticType", region.semanticType(), "units", translated));
            }
            int published = (int) units.stream().filter(u -> !u.chinese().isEmpty()).count();
            if (regressions.path("samples").has(id)) {
                regressionImages++;
                var answers = regressions.path("samples").path(id);
                if (units.size() != answers.size()) failures.add(id + " regression unit count mismatch");
                for (int i = 0; i < Math.min(units.size(), answers.size()); i++) {
                    regressionUnits++;
                    if (!units.get(i).chinese().equals(answers.get(i).path("chinese").asText())
                            || !units.get(i).displayStatus().equals(answers.get(i).path("status").asText()))
                        failures.add(id + " regression unit " + i + ": " + units.get(i));
                }
            }
            boolean allReady = !units.isEmpty() && units.stream()
                    .allMatch(u -> !u.chinese().isEmpty() && u.displayStatus().equals("ready"));
            String outcome = units.isEmpty() ? "no_target" : allReady ? "all_ready"
                    : published == 0 ? "no_chinese" : "published_partial";
            summary.put("images", (int) summary.get("images") + 1);
            String bucket = switch (outcome) {
                case "no_target" -> "noTargetImages";
                case "all_ready" -> "allUnitsReadyChineseImages";
                case "published_partial" -> "partialChineseImages";
                default -> "noChineseImages";
            };
            summary.put(bucket, (int) summary.get(bucket) + 1);
            summary.put("units", (int) summary.get("units") + units.size());
            summary.put("unitsWithChinese", (int) summary.get("unitsWithChinese") + published);
            @SuppressWarnings("unchecked") var statuses = (Map<String, Integer>) summary.get("displayStatusCounts");
            for (var unit : units) statuses.merge(unit.displayStatus(), 1, Integer::sum);
            if (sample.path("expected_no_target_text").asBoolean() && !units.isEmpty())
                failures.add(id + " negative sample published text");
            var row = new LinkedHashMap<String, Object>();
            row.put("sampleId", id); row.put("source", imagePath.toString().replace('\\', '/'));
            row.put("sourceSha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(imagePath))));
            row.put("expectedNoTargetText", sample.path("expected_no_target_text").asBoolean());
            row.put("rawJapanesePass", japanesePass); row.put("rawOcrRegions", actual);
            row.put("outcome", outcome); row.put("units", units.size()); row.put("unitsWithChinese", published); row.put("regions", regions);
            samples.add(row);
        }
        var hashes = new LinkedHashMap<String, String>();
        try (var paths = Files.walk(Path.of("src"))) {
            for (var path : paths.filter(Files::isRegularFile).sorted().toList()) hashes.put(path.toString().replace('\\', '/'),
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))));
        }
        hashes.put("testpic/labels/japanese.json", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(labelPath))));
        var report = new LinkedHashMap<String, Object>();
        report.put("implementationVersion", System.getProperty("g1ocr.version"));
        report.put("scope", "108 independent PNGs; all corpus sources; no game version selection or historical context");
        report.put("measurement", "nonempty Chinese fields measure publication, not semantic correctness; nickname passthrough is not Chinese");
        report.put("images", samples.size());
        report.put("rawJapanesePassedImages", samples.size() - failures.stream().filter(f -> f.contains("raw Japanese")).count());
        report.put("executionFailures", failures); report.put("finalChineseAcceptance", false);
        report.put("regressionImages", regressionImages); report.put("regressionUnits", regressionUnits);
        report.put("regressionMethod", regressions.path("method").asText());
        report.put("summary", summary); report.put("sha256", hashes); report.put("samples", samples);
        Files.createDirectories(Path.of("reports"));
        json.writerWithDefaultPrettyPrinter().writeValue(Path.of("reports/chinese-images.json").toFile(), report);
        assertEquals(108, samples.size());
        assertEquals(regressions.path("samples").size(), regressionImages);
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }
}
