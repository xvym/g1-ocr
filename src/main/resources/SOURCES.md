# 识别与翻译资源来源

OCR 运行时读取 `font.tsv`；独立翻译层读取 `corpus/records.csv`、`fragments.csv` 和 `slot_rules.csv`，不读取截图答案。结构、固定来源、建设与核对状态见 [语料说明](corpus/README.md) 及 [来源清单](corpus/sources.csv)。

上游：[Narishma-gb/pokegreen](https://github.com/Narishma-gb/pokegreen/tree/953f41b34108621b2bf13c3b1e53abfc9c3e5aec)，固定提交 `953f41b34108621b2bf13c3b1e53abfc9c3e5aec`。该仓库说明为日版红/绿反汇编；这不证明未知版本截图属于红/绿，也不证明另两个版本布局通过。

| 文件 | SHA-256 | 用途 |
| - | - | - |
| `constants/charmap.asm` | `0b4adbe558c2d8de8e88563fa302dea41021dd62c4b257f833426ee97b8213b7` | 字符码与四组同形假名依据 |
| `gfx/font/font.png` | `2579f65843562a1addd355e8a33e0d32aad5a327908391ca96db93f1c0bd87af` | 128 个主字形，含清浊点、数字及光标 |
| `gfx/font/font_extra.png` | `65bec927782290b91c345b799b3356202fa8ec4f89b61dbcaab9200b90c1fe54` | 标点、框线及附加假名 |
| `gfx/font/font_battle_extra.png` | `7fc5145da077e55d4b35f843176808f41bf442c528a5b761f80fb0697b170cee` | `<Lv>` 字形 |
| 派生 `font.tsv` | `56022863949bdbdd3e097cbc62123d33d601a95596a88db69bbb710d3975b4a4` | 209 个模板，其中 198 个文字模板；每行保留所有相同像素候选 |

生成方式：`python tools/import_font.py`（开发工具需要 Pillow；运行 Java 应用不需要 Python 或网络）。脚本仅从固定上游图形和字符表生成模板，不读取验收答案。每个 8×8 字形按 `bit=y*8+x` 写入 64 位数，低位在左上；清浊点保留为独立的上方 8×8 图块。只接受 128 位字形距离为 0 的匹配，非零距离输出未知及最近候选，不作模糊纠错。

布局参考：[data/text_boxes.asm](https://github.com/Narishma-gb/pokegreen/blob/953f41b34108621b2bf13c3b1e53abfc9c3e5aec/data/text_boxes.asm)；实际网格与遮罩证据见 `reports/layouts.md`，运行时由 `RegionDetector` 定义。

分发条件：固定提交的文件树未提供 LICENSE；游戏字形的再分发授权尚未确认。本次为本地研究实现及固定素材验证，不声明已满足对外分发条件。S6 正式交付前须落实字形及文本派生资源的使用与分发条件。未引入 ROM；第三方完整仓库仅用于离线建库，位于被忽略的输出目录，不作为产品资源。
