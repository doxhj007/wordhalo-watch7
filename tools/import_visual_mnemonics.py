"""Crop reviewed 3x3 atlases into word-specific offline mnemonic art.

The atlas order is intentional. Pass the eight reviewed source PNGs in the same
order as BATCHES; the script writes compact WebP tiles plus their app metadata.
"""
import argparse
import hashlib
import json
from pathlib import Path

from PIL import Image, ImageOps


BATCHES = [
    [
        ("allegation", "手指 + 问号文件：只是指控，尚未证实"),
        ("convict", "法槌落下 + 戴铐入狱：定罪；罪犯"),
        ("testimony", "证人席举手陈述：证词 / 证言"),
        ("immune", "人体护盾挡住病毒：免疫，不受影响"),
        ("outbreak", "一个源头向人群扩散：突然爆发"),
        ("fracture", "X 光中的裂缝：骨折；断裂"),
        ("prescription", "医生签字 + 药瓶：处方；指示"),
        ("therapy", "面对面疏导：治疗；疗法"),
        ("verdict", "法槌 + 天平定下来：最终裁决"),
    ],
    [
        ("deficit", "一边空缺的天平：收入不够，出现赤字"),
        ("inflation", "钱币气球越涨越大，购物篮却没变：通胀"),
        ("mortgage", "房子被合同链住：抵押贷款"),
        ("recession", "下降箭头穿过关门店铺：经济衰退"),
        ("transaction", "钱与包裹双向交换：交易"),
        ("consensus", "大家放下同样的勾：达成共识"),
        ("mediate", "站在争吵双方中间：调解"),
        ("coalition", "不同颜色拼图组成一环：联合 / 联盟"),
        ("expertise", "精密工具 + 发光知识：专长"),
    ],
    [
        ("hypothesis", "问号想法指向试管：待验证的假设"),
        ("empirical", "亲眼观察并记录实验：经验 / 实证"),
        ("qualitative", "比较颜色、材质和感受：定性"),
        ("quantitative", "量具、计数珠和柱图：定量"),
        ("synthesis", "不同碎片汇成新晶体：综合 / 合成"),
        ("parameter", "边界柱 + 可调旋钮：限定参数范围"),
        ("literacy", "书本点亮理解力：读写能力"),
        ("manuscript", "亲手写下的原始长稿：手稿 / 原稿"),
        ("thesis", "向评审展示研究成果：论文 / 论点"),
    ],
    [
        ("detain", "关卡拦住并暂扣证件：拘留 / 留住"),
        ("fraud", "漂亮礼盒里藏着陷阱：欺诈"),
        ("lawsuit", "双方带材料走向法院：诉讼"),
        ("legitimate", "文件通过绿色认证门：合法 / 正当"),
        ("penalty", "违规后亮牌退场：处罚 / 惩罚"),
        ("plead", "在法官前张开双手恳求：辩护 / 恳求"),
        ("proceeding", "按步骤走完司法流程：诉讼程序 / 进程"),
        ("sue", "把起诉材料正式交给对方：起诉"),
        ("warrant", "带授权印章的搜查令：授权 / 许可证"),
    ],
    [
        ("chronic", "日历长带反复环绕：长期的 / 慢性的"),
        ("complication", "简单医疗路径突然缠成多条：并发症 / 复杂化"),
        ("diagnose", "放大症状并锁定病因：诊断"),
        ("epidemic", "疾病标记在区域内扩散：流行病"),
        ("humanitarian", "灾后送水、食物与药箱：人道主义"),
        ("infectious", "飞沫从一人传到另一人：传染性的"),
        ("kidney", "成对器官过滤体液：肾脏"),
        ("maternity", "母亲抱着新生儿：母性 / 产科"),
        ("nutrition", "均衡餐盘让身体有力量：营养"),
    ],
    [
        ("asset", "房屋、机器和储蓄收进保护盾：资产"),
        ("bankrupt", "空收银机 + 空口袋 + 断线：破产"),
        ("compensation", "修补损失并给予补偿：补偿 / 赔偿"),
        ("currency", "纸币、硬币和卡片循环流通：货币"),
        ("funding", "资金浇灌项目幼苗：资金 / 资助"),
        ("monetary", "中央机制调节货币流向：货币的"),
        ("premium", "标准保护盾上再加一层：额外费用 / 高级的"),
        ("tariff", "货物过边境时加收费用：关税"),
        ("allowance", "大预算中分出固定小袋：津贴 / 零用钱"),
    ],
    [
        ("alliance", "两个不同群体架起一座桥：联盟"),
        ("ally", "伙伴背靠背互相保护：盟友"),
        ("collaboration", "多只手一起装好同一机器：协作"),
        ("consultant", "专家为具体问题指一条路：顾问"),
        ("corporate", "多个部门连进同一组织：公司的 / 法人的"),
        ("counterpart", "不同地点承担同一角色的两人：对应的人"),
        ("diplomatic", "两国代表谨慎握手：外交的 / 圆融的"),
        ("engagement", "从旁观者变成参与者：参与 / 投入"),
        ("enterprise", "团队把新事业发射升空：企业 / 进取心"),
    ],
    [
        ("chronicle", "卷轴按顺序记录历史事件：编年史 / 记录"),
        ("curriculum", "沿规划路线学完各门课程：课程体系"),
        ("glossary", "陌生词和解释一一对应：术语表"),
        ("intellect", "用推理连通复杂图案：智力 / 才智"),
        ("narrative", "多段场景被一条故事线串起：叙事"),
        ("prose", "文字连续自然流动，不分诗行：散文"),
        ("instructor", "老师分步骤示范技能：教员 / 导师"),
        ("historian", "由文物、地图和时间线重建过去：历史学家"),
        ("handbook", "照小手册一步步完成任务：手册"),
    ],
]


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--atlas", action="append", required=True, type=Path)
    parser.add_argument("--assets", required=True, type=Path)
    args = parser.parse_args()
    if len(args.atlas) != len(BATCHES):
        parser.error(f"expected {len(BATCHES)} --atlas arguments")
    args.assets.mkdir(parents=True, exist_ok=True)
    metadata = {
        "version": 1,
        "style": "word-specific AMOLED mnemonic illustrations",
        "atlasSha256": [digest(path) for path in args.atlas],
        "words": {},
    }
    for batch_index, (atlas_path, batch) in enumerate(zip(args.atlas, BATCHES), 1):
        with Image.open(atlas_path) as source:
            image = source.convert("RGB")
            width, height = image.size
            for index, (word, cue) in enumerate(batch):
                row, column = divmod(index, 3)
                left, right = round(column * width / 3), round((column + 1) * width / 3)
                top, bottom = round(row * height / 3), round((row + 1) * height / 3)
                inset = max(3, round(min(right - left, bottom - top) * .012))
                tile = image.crop((left + inset, top + inset, right - inset, bottom - inset))
                tile = ImageOps.fit(tile, (512, 512), Image.Resampling.LANCZOS,
                                    centering=(.5, .5))
                filename = f"visual-{word}.webp"
                tile.save(args.assets / filename, "WEBP", quality=92, method=6)
                metadata["words"][word] = {
                    "file": filename,
                    "caption": cue,
                    "title": f"Word Halo mnemonic illustration: {word}",
                    "artist": "OpenAI image generation · Word Halo curation",
                    "license": "Generated project artwork",
                    "source": f"Reviewed atlas {batch_index}, tile {index + 1}",
                    "generated": True,
                }
    output = args.assets / "visual-mnemonics.json"
    output.write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n",
                      encoding="utf-8")
    print(f"wrote {len(metadata['words'])} word-specific images to {args.assets}")


if __name__ == "__main__":
    main()
