# 专属联想插画

词间 0.23 使用 OpenAI 内置图像生成工具制作 72 张一词一图的离线联想插画。目标是替换旧版里重复最多、与具体词义距离较远的通用会议桌、医院、法庭、钞票和图书馆照片。生成结果经过逐格目视检查，再裁成 512 × 512 WebP；应用只读取 APK 资产，不会为这些图发起网络请求。

## 统一提示词

八个批次都使用同一套最终规格：

```text
Use case: scientific-educational
Asset type: a 3 by 3 sprite atlas for a Wear OS vocabulary learning app; each tile will be cropped into a separate mnemonic illustration.
Primary request: Create exactly nine distinct square visual mnemonic illustrations in a strict 3-column by 3-row grid, in the supplied exact row-major order. Express each supplied word through the specified visible action or relationship.
Style/medium: polished editorial 3D illustration, soft clay-like forms, premium educational app art, clear silhouettes, gentle depth, consistent across all nine.
Composition/framing: each concept centered in its own square tile; thick uniform black gutters between tiles; no element may cross a gutter; generous safe margins for later cropping.
Lighting/mood: cinematic but friendly, clear focal lighting.
Color palette: near-black backgrounds, mint green highlights, warm amber secondary accents, limited coral for danger; strong contrast for an AMOLED watch.
Constraints: exactly 3x3 tiles; exact supplied order; every tile visually distinct; one main idea per tile; readable at 140 pixels; no words, letters, readable numbers, captions, logos or watermarks.
Avoid: generic stock-photo scenes, repeated compositions and tiny decorative clutter.
```

每个批次的 `Primary request` 逐项写明了画面关系。最终行序如下；中文联想提示的完整文本在 `app/src/main/assets/visual-mnemonics.json`，裁切逻辑在 `tools/import_visual_mnemonics.py`。

1. allegation, convict, testimony, immune, outbreak, fracture, prescription, therapy, verdict
2. deficit, inflation, mortgage, recession, transaction, consensus, mediate, coalition, expertise
3. hypothesis, empirical, qualitative, quantitative, synthesis, parameter, literacy, manuscript, thesis
4. detain, fraud, lawsuit, legitimate, penalty, plead, proceeding, sue, warrant
5. chronic, complication, diagnose, epidemic, humanitarian, infectious, kidney, maternity, nutrition
6. asset, bankrupt, compensation, currency, funding, monetary, premium, tariff, allowance
7. alliance, ally, collaboration, consultant, corporate, counterpart, diplomatic, engagement, enterprise
8. chronicle, curriculum, glossary, intellect, narrative, prose, instructor, historian, handbook

## 可复核信息

原始九宫格按上面的批次顺序对应以下 SHA-256：

```text
6da3f8c653005dc1145ab971447f37efa4d05dbc53a29949cf6fbcab5a0bd706
a86983ac641e39a1e8d6c91d1962d3ec96ad3d7699f370b1c54dbf66d542ef0b
e5a4e69c0aea86ae44fe144993df4b470a129a1055ba9da62e8eb65fc602ac4f
e4350c03019fad0d5dfb4a0e7535f94e927e8bb1b3f0b9761a9c3c997ea24507
df008c80f1756b28b55dbd1a5accfacadfe466c6a65e3fc80516f1dcd8d13a14
134097c1f433e4463fb04bcdb1febfc91e366e07d0269db7a1afc05afc8c9f5e
907d87ed368313512053c4d751aa9bddd0e6390e6b1ed22326c25945187b0874
f53a6b9c49e51aa8ec49291fcc3110b390d809f394ebf137b22835fa15091dbd
```

导入脚本按三等分网格裁切，去掉约 1.2% 的边缘接缝，再使用 Lanczos 缩放为 512 × 512、WebP 质量 92。72 个输出文件名均为 `visual-<word>.webp`，测试会检查文件唯一、尺寸、可解码、词库归属、中文提示和打开图卡时没有网络请求。

这些插画用于视觉记忆，不是词源、医学诊断或完整词典释义。专属图的署名页标明为 “OpenAI image generation · Word Halo curation”。
