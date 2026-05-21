import hashlib
import io
import json
import os
import posixpath
import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path

import openpyxl
import pymysql

DB_CONFIG = {
    "host": "192.168.0.168",
    "port": 3306,
    "user": "ITaimysql",
    "password": "Ai12345678@",
    "database": "ecommerce_workflow",
    "charset": "utf8mb4",
    "cursorclass": pymysql.cursors.DictCursor,
}

WORKBOOKS = [
    {
        "kind": "IP",
        "source_file": r"D:\choice_product\美区IP款侵权插画.xlsx",
        "table": "cp_ai_ip_rule",
        "rule_prefix": "IP_RULE",
    },
    {
        "kind": "MATERIAL",
        "source_file": r"D:\choice_product\工厂材质.xlsx",
        "table": "cp_ai_material_rule",
        "rule_prefix": "MAT_RULE",
    },
]

NS = {
    "xdr": "http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing",
    "a": "http://schemas.openxmlformats.org/drawingml/2006/main",
    "r": "http://schemas.openxmlformats.org/officeDocument/2006/relationships",
    "rel": "http://schemas.openxmlformats.org/package/2006/relationships",
}

LOCAL_VISION_MODEL_ID = os.getenv("CP_LOCAL_VISION_MODEL_ID", "HuggingFaceTB/SmolVLM2-256M-Video-Instruct")
LOCAL_VISION_MODEL_REVISION = os.getenv("CP_LOCAL_VISION_MODEL_REVISION", "")
LOCAL_VISION_LOCAL_FILES_ONLY = os.getenv("CP_LOCAL_VISION_LOCAL_FILES_ONLY", "0") == "1"

_LOCAL_IMAGE_TO_TEXT = None
_LOCAL_IMAGE_TO_TEXT_ERROR = None


class LocalImageToText:
    """
    本地视觉识别适配器，默认使用轻量 SmolVLM2-256M。

    Excel 图片只在导入阶段临时进入这个模块；规则表只保存识别后的文字，
    不保存图片路径、图片二进制或 base64。
    """

    def __init__(self):
        from PIL import Image
        import torch
        from transformers import AutoModelForImageTextToText, AutoProcessor

        self.image_cls = Image
        self.torch = torch
        self.device = "cuda" if torch.cuda.is_available() else "cpu"
        model_kwargs = {
            "local_files_only": LOCAL_VISION_LOCAL_FILES_ONLY,
        }
        processor_kwargs = {
            "local_files_only": LOCAL_VISION_LOCAL_FILES_ONLY,
        }
        if LOCAL_VISION_MODEL_REVISION:
            model_kwargs["revision"] = LOCAL_VISION_MODEL_REVISION
            processor_kwargs["revision"] = LOCAL_VISION_MODEL_REVISION
        model_kwargs["torch_dtype"] = torch.float16 if self.device == "cuda" else torch.float32
        self.processor = AutoProcessor.from_pretrained(
            LOCAL_VISION_MODEL_ID,
            **processor_kwargs,
        )
        self.model = AutoModelForImageTextToText.from_pretrained(
            LOCAL_VISION_MODEL_ID,
            **model_kwargs,
        )
        self.model.to(self.device)
        self.model.eval()

    def describe(self, image_bytes, question):
        image = self.image_cls.open(io.BytesIO(image_bytes)).convert("RGB")
        messages = [
            {
                "role": "user",
                "content": [
                    {"type": "image", "image": image},
                    {"type": "text", "text": question},
                ],
            }
        ]
        text = self.processor.apply_chat_template(
            messages, tokenize=False, add_generation_prompt=True
        )
        inputs = self.processor(
            text=[text],
            images=[image],
            return_tensors="pt",
        ).to(self.device)
        with self.torch.inference_mode():
            generated_ids = self.model.generate(**inputs, max_new_tokens=96)
        generated_ids_trimmed = [
            output_ids[len(input_ids):]
            for input_ids, output_ids in zip(inputs.input_ids, generated_ids)
        ]
        return self.processor.batch_decode(
            generated_ids_trimmed,
            skip_special_tokens=True,
            clean_up_tokenization_spaces=False,
        )[0]


def get_local_image_to_text():
    global _LOCAL_IMAGE_TO_TEXT, _LOCAL_IMAGE_TO_TEXT_ERROR
    if _LOCAL_IMAGE_TO_TEXT is not None:
        return _LOCAL_IMAGE_TO_TEXT
    if _LOCAL_IMAGE_TO_TEXT_ERROR is not None:
        return None
    try:
        _LOCAL_IMAGE_TO_TEXT = LocalImageToText()
        return _LOCAL_IMAGE_TO_TEXT
    except Exception as exc:
        _LOCAL_IMAGE_TO_TEXT_ERROR = str(exc)
        return None


def build_local_vision_question(kind, row_context):
    if kind == "IP":
        ip_name = row_context.get("ip_name", "")
        infringing_element = row_context.get("infringing_element", "")
        return (
            "Describe this phone case rule sample image in English. Focus only on what is visibly present: "
            "text, logo, character appearance, face, hair, clothing, pose, props, expression, iconic silhouette, "
            "colors, layout, sticker style, poster style, anime/cartoon style, album-cover style, and repeated patterns. "
            "Write a concrete visual caption, not instructions. "
            f"Known label from Excel: IP name={ip_name or 'unknown'}; infringement clue={infringing_element or 'unknown'}."
        )
    group_name = row_context.get("group_name", "")
    material_name = row_context.get("material_name", "")
    price = row_context.get("price", "")
    return (
        "Describe this phone case material rule sample image in English. Focus only on what is visibly present: "
        "material look, transparency, matte or glossy finish, soft or hard shell clues, edge wrapping, camera area, "
        "lens cutouts, surface texture, color, magnetic ring, lanyard parts, stickers, charms, and accessory shape. "
        "Write a concrete visual caption, not instructions. "
        f"Known label from Excel: group={group_name or 'unknown'}; material/accessory={material_name or 'unknown'}; price={price or 'unknown'}."
    )


def recognize_rule_image_with_local_model(kind, image_bytes, row_context):
    recognizer = get_local_image_to_text()
    if recognizer is None:
        return {
            "status": "VISION_SKIPPED",
            "description": "",
            "error": _LOCAL_IMAGE_TO_TEXT_ERROR or "Local image-to-text recognizer is unavailable.",
        }
    question = build_local_vision_question(kind, row_context)
    try:
        description = recognizer.describe(image_bytes, question)
        description = description.strip() if description else ""
        if not description:
            return {
                "status": "VISION_EMPTY",
                "description": "",
                "question": question,
                "model_id": LOCAL_VISION_MODEL_ID,
                "model_revision": LOCAL_VISION_MODEL_REVISION,
                "error": "Local image-to-text model returned empty description.",
            }
        return {
            "status": "VISION_RECOGNIZED",
            "description": description,
            "question": question,
            "model_id": LOCAL_VISION_MODEL_ID,
            "model_revision": LOCAL_VISION_MODEL_REVISION,
        }
    except Exception as exc:
        return {
            "status": "VISION_FAILED",
            "description": "",
            "question": question,
            "model_id": LOCAL_VISION_MODEL_ID,
            "model_revision": LOCAL_VISION_MODEL_REVISION,
            "error": str(exc),
        }


def ensure_tables(cursor):
    cursor.execute(
        """
        CREATE TABLE IF NOT EXISTS `cp_ai_ip_rule`
        (
            `id`
            bigint
        (
            20
        ) unsigned NOT NULL AUTO_INCREMENT COMMENT '自增主键',
            `rule_code` varchar
        (
            100
        ) NOT NULL COMMENT '规则编码，例如 IP_RULE_0001',
            `rule_name` varchar
        (
            255
        ) DEFAULT NULL COMMENT '规则名称',
            `ip_grade` varchar
        (
            10
        ) DEFAULT NULL COMMENT 'IP风险等级：S/A/B/C/D/E',
            `ip_type` varchar
        (
            100
        ) DEFAULT NULL COMMENT 'IP类型，例如 奢侈品大牌、美国本地IP品牌、非美国本地动漫IP、非美国本地潮牌品牌、擦边插画/不知名IP延伸扭曲设计、完全无风险',
            `keywords` longtext DEFAULT NULL COMMENT '规则关键词JSON数组',
            `image_description` longtext DEFAULT NULL COMMENT 'AI对规则样例图片的整体描述',
            `ocr_text` longtext DEFAULT NULL COMMENT 'AI/OCR从规则样例图片中识别出的文字',
            `visual_features` longtext DEFAULT NULL COMMENT '图片视觉特征JSON',
            `rule_reason` longtext DEFAULT NULL COMMENT '规则说明',
            `source_file` varchar
        (
            500
        ) DEFAULT NULL COMMENT '规则来源文件',
            `source_sheet` varchar
        (
            255
        ) DEFAULT NULL COMMENT '规则来源工作表名称',
            `source_row` int
        (
            11
        ) DEFAULT NULL COMMENT '规则来源Excel行号',
            `source_col` int
        (
            11
        ) DEFAULT NULL COMMENT '规则来源Excel列号',
            `enabled` tinyint
        (
            1
        ) NOT NULL DEFAULT 1 COMMENT '是否启用：1=启用，0=停用',
            `created_at` datetime NOT NULL DEFAULT current_timestamp
        (
        ) COMMENT '创建时间',
            `updated_at` datetime NOT NULL DEFAULT current_timestamp
        (
        ) ON UPDATE current_timestamp
        (
        ) COMMENT '更新时间',
            PRIMARY KEY
        (
            `id`
        ),
            UNIQUE KEY `uk_cp_ai_ip_rule_code`
        (
            `rule_code`
        ),
            KEY `idx_cp_ai_ip_rule_grade`
        (
            `ip_grade`
        ),
            KEY `idx_cp_ai_ip_rule_enabled`
        (
            `enabled`
        )
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE =utf8mb4_general_ci
            COMMENT='选品AI分析IP规则表';
        """
    )
    cursor.execute(
        """
        CREATE TABLE IF NOT EXISTS `cp_ai_material_rule`
        (
            `id`
            bigint
        (
            20
        ) unsigned NOT NULL AUTO_INCREMENT COMMENT '自增主键',
            `rule_code` varchar
        (
            100
        ) NOT NULL COMMENT '规则编码，例如 MAT_RULE_0001',
            `rule_name` varchar
        (
            255
        ) DEFAULT NULL COMMENT '规则名称',
            `material_type` varchar
        (
            100
        ) DEFAULT NULL COMMENT '材质大类，例如 工厂材质、非工厂材质、疑似材质、其他',
            `material_category` varchar
        (
            100
        ) DEFAULT NULL COMMENT '材质细分类，例如 TPU、硅胶、亚克力、金属、皮革、布料',
            `keywords` longtext DEFAULT NULL COMMENT '规则关键词JSON数组',
            `image_description` longtext DEFAULT NULL COMMENT 'AI对规则样例图片的整体描述',
            `ocr_text` longtext DEFAULT NULL COMMENT 'AI/OCR从规则样例图片中识别出的文字',
            `visual_features` longtext DEFAULT NULL COMMENT '图片视觉特征JSON',
            `rule_reason` longtext DEFAULT NULL COMMENT '规则说明',
            `source_file` varchar
        (
            500
        ) DEFAULT NULL COMMENT '规则来源文件',
            `source_sheet` varchar
        (
            255
        ) DEFAULT NULL COMMENT '规则来源工作表名称',
            `source_row` int
        (
            11
        ) DEFAULT NULL COMMENT '规则来源Excel行号',
            `source_col` int
        (
            11
        ) DEFAULT NULL COMMENT '规则来源Excel列号',
            `enabled` tinyint
        (
            1
        ) NOT NULL DEFAULT 1 COMMENT '是否启用：1=启用，0=停用',
            `created_at` datetime NOT NULL DEFAULT current_timestamp
        (
        ) COMMENT '创建时间',
            `updated_at` datetime NOT NULL DEFAULT current_timestamp
        (
        ) ON UPDATE current_timestamp
        (
        ) COMMENT '更新时间',
            PRIMARY KEY
        (
            `id`
        ),
            UNIQUE KEY `uk_cp_ai_material_rule_code`
        (
            `rule_code`
        ),
            KEY `idx_cp_ai_material_rule_category`
        (
            `material_type`,
            `material_category`
        ),
            KEY `idx_cp_ai_material_rule_enabled`
        (
            `enabled`
        )
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE =utf8mb4_general_ci
            COMMENT='选品AI分析材质规则表';
        """
    )


def get_sheet_names(xlsx_path):
    wb = openpyxl.load_workbook(xlsx_path, read_only=True, data_only=True)
    try:
        return wb.sheetnames
    finally:
        wb.close()


def load_first_sheet_values(xlsx_path):
    wb = openpyxl.load_workbook(xlsx_path, read_only=False, data_only=True)
    ws = wb[wb.sheetnames[0]]
    values = {}
    for row in ws.iter_rows():
        for cell in row:
            if cell.value is not None and str(cell.value).strip():
                values[(cell.row, cell.column)] = str(cell.value).strip()
    sheet_name = ws.title
    wb.close()
    return sheet_name, values


def get_cell(values, row, col):
    return values.get((row, col), "")


def get_nearest_left_cell(values, row, col):
    for current_col in range(col, 0, -1):
        value = get_cell(values, row, current_col)
        if value:
            return value
    return ""


def get_ip_context(values, anchor):
    row = anchor["row"]
    return {
        "ip_name": get_cell(values, row, 2),
        "infringing_element": get_cell(values, row, 3),
        "image_role": "主图" if anchor["col"] <= 3 else "类似主图",
    }


def material_group_start(col):
    # 工厂材质表按 4 列一组：序号 / 材质或配件 / 图片 / 价格。
    return ((col - 1) // 4) * 4 + 1


def get_material_context(values, anchor):
    start_col = material_group_start(anchor["col"])
    row = anchor["row"]
    return {
        "group_name": get_nearest_left_cell(values, 1, start_col),
        "item_no": get_cell(values, row, start_col),
        "material_name": get_cell(values, row, start_col + 1),
        "price": get_cell(values, row, start_col + 3),
    }


def load_rels(zip_file, drawing_path):
    rel_path = "xl/drawings/_rels/" + posixpath.basename(drawing_path) + ".rels"
    if rel_path not in zip_file.namelist():
        return {}
    root = ET.fromstring(zip_file.read(rel_path))
    return {rel.attrib.get("Id"): rel.attrib.get("Target") for rel in root}


def extract_image_anchors(xlsx_path):
    anchors = []
    with zipfile.ZipFile(xlsx_path) as z:
        drawings = [
            item
            for item in z.namelist()
            if item.startswith("xl/drawings/drawing") and item.endswith(".xml")
        ]
        for drawing_path in drawings:
            rels = load_rels(z, drawing_path)
            root = ET.fromstring(z.read(drawing_path))
            for tag in ("twoCellAnchor", "oneCellAnchor"):
                for anchor in root.findall("xdr:" + tag, NS):
                    frm = anchor.find("xdr:from", NS)
                    pic = anchor.find("xdr:pic", NS)
                    if frm is None or pic is None:
                        continue
                    blip = pic.find(".//a:blip", NS)
                    if blip is None:
                        continue
                    rid = blip.attrib.get(
                        "{http://schemas.openxmlformats.org/officeDocument/2006/relationships}embed"
                    )
                    target = rels.get(rid)
                    if not target:
                        continue
                    media_path = posixpath.normpath(
                        posixpath.join(posixpath.dirname(drawing_path), target)
                    )
                    anchors.append(
                        {
                            "media_path": media_path,
                            "row": int(frm.findtext("xdr:row", default="0", namespaces=NS)) + 1,
                            "col": int(frm.findtext("xdr:col", default="0", namespaces=NS)) + 1,
                        }
                    )
    anchors.sort(key=lambda item: (item["row"], item["col"], item["media_path"]))
    return anchors


def build_ip_rule_image_prompt():
    return """
你是选品系统的 IP 风险规则图片分析器。
请只根据输入图片生成规则库文本，不要输出商品审核结论。

请输出 JSON：
{
  "rule_name": "一句话概括这张规则图片代表的IP/图案规则",
  "ip_grade": "S/A/B/C/D/E 或 null",
  "ip_type": "奢侈品大牌/美国本地IP品牌/非美国本地动漫IP/非美国本地潮牌品牌/擦边插画/不知名IP延伸扭曲设计/完全无风险",
  "keywords": ["可用于后续匹配商品的关键词"],
  "image_description": "图片整体描述",
  "ocr_text": "图片中可见文字，没有则为空字符串",
  "visual_features": {
    "logo_clues": [],
    "character_clues": [],
    "shape_clues": [],
    "color_clues": [],
    "style_clues": []
  },
  "rule_reason": "为什么这张图可以作为该IP规则的判断依据"
}
""".strip()


def build_material_rule_image_prompt():
    return """
你是选品系统的工厂材质规则图片分析器。
请只根据输入图片生成规则库文本，不要输出商品审核结论。

请输出 JSON：
{
  "rule_name": "一句话概括这张规则图片代表的材质规则",
  "material_type": "工厂材质/非工厂材质/疑似材质/其他",
  "material_category": "TPU/硅胶/亚克力/金属/皮革/布料/其他 或 null",
  "keywords": ["可用于后续匹配商品的关键词"],
  "image_description": "图片整体描述",
  "ocr_text": "图片中可见文字，没有则为空字符串",
  "visual_features": {
    "material_clues": [],
    "texture_clues": [],
    "shape_clues": [],
    "color_clues": [],
    "usage_clues": []
  },
  "rule_reason": "为什么这张图可以作为该材质规则的判断依据"
}
""".strip()


def split_keywords(text):
    if not text:
        return []
    separators = ["（", "）", "(", ")", "、", "/", "\\n", "，", ",", "：", ":", " "]
    normalized = text
    for sep in separators:
        normalized = normalized.replace(sep, "|")
    return [part.strip() for part in normalized.split("|") if part.strip()][:20]


def infer_ip_type(ip_name, infringing_element):
    text = f"{ip_name}\n{infringing_element}".lower()
    luxury = [
        "prada", "burberry", "lv", "louis vuitton", "gucci", "hermès", "hermes",
        "chrome hearts", "克罗心", "爱马仕", "古驰", "路易威登", "奢侈",
    ]
    anime = [
        "动漫", "漫画", "anime", "pokémon", "pokemon", "nana", "eva", "死亡笔记",
        "剑风传奇", "日本", "卡通/玩具ip", "玩具ip", "monchhichi", "蒙奇奇",
        "san-x", "rilakkuma", "轻松熊", "火影", "naruto", "海贼王", "one piece",
        "咒术", "jujutsu", "jojo", "草莓娃娃", "strawberry shortcake",
    ]
    us_ip = [
        "disney", "mickey", "barbie", "bratz", "monster high", "powerpuff",
        "netflix", "k-pop demon hunters", "huntr/x", "huntrxx", "huntrix",
        "curious george", "shopkins", "michelob", "universal studios",
        "迪士尼", "芭比", "网飞", "猎魔人", "好奇的乔治", "购物精灵", "米凯罗",
        "贝兹娃娃", "飞天小女警", "美国本地"
    ]
    street = ["supreme", "stüssy", "stussy", "bape", "red bull", "红牛", "潮牌", "streetwear", "sp5der", "猿人头"]
    edge = ["擦边", "疑似", "不知名", "延伸", "扭曲", "再设计", "风格", "插画并非直接照搬"]

    if any(k in text for k in luxury):
        return "奢侈品大牌"
    if any(k in text for k in us_ip):
        return "美国本地 IP 品牌"
    if any(k in text for k in anime):
        return "非美国本地动漫 IP"
    if any(k in text for k in street):
        return "非美国本地潮牌品牌"
    if any(k in text for k in edge):
        return "擦边插画/不知名 IP 延伸扭曲设计"
    if ip_name:
        return "擦边插画/不知名 IP 延伸扭曲设计"
    return "完全无风险"


def infer_ip_grade(ip_type):
    if not ip_type:
        return None
    mapping = {
        "奢侈品大牌": "S",
        "美国本地 IP 品牌": "A",
        "非美国本地动漫 IP": "B",
        "非美国本地潮牌品牌": "C",
        "擦边插画/不知名 IP 延伸扭曲设计": "D",
        "完全无风险": "E",
    }
    return mapping.get(ip_type, "E")


def describe_franchise_character_clues(ip_name, infringing_element):
    text = f"{ip_name}\n{infringing_element}".lower()
    if "naruto" in text or "火影" in text:
        return (
            "如果规则指向《火影忍者》，需要把具体角色也作为识别线索：鸣人通常是金发、护额、橙黑忍者服、脸颊胡须纹；"
            "佐助通常是黑发、冷色忍者服、写轮眼/轮回眼、草薙剑或宇智波团扇标识；小樱常见粉发、红色服装、额头菱形印记；"
            "卡卡西常见银白发、遮住下半脸的面罩、单眼护额、写轮眼和亲热天堂书本。"
        )
    if "one piece" in text or "海贼王" in text:
        return (
            "如果规则指向《海贼王》，需要把草帽团角色拆开识别：路飞通常是草帽、红上衣、开朗笑脸和橡胶伸展动作；"
            "索隆常见绿色头发、三刀流、腹带和严肃表情；山治常见金发遮眼、黑西装、卷眉和踢击姿势；"
            "乔巴常见鹿角、蓝粉帽子和小型驯鹿轮廓；骷髅旗、草帽海贼团标志也属于强线索。"
        )
    if (
            "k-pop demon hunters" in text
            or "huntr/x" in text
            or "huntrxx" in text
            or "huntrix" in text
            or "猎魔人" in text
            or "zoey" in text
            or "rumi" in text
            or "mira" in text
    ):
        return (
            "该 IP 的关键不是普通 K-pop 元素，而是 Netflix《K-Pop Demon Hunters》的虚拟女团 HUNTR/X（也可能写作 HUNTRXX、Huntrix）。"
            "手机壳上如果出现 HUNTR/X、HUNTRXX、Huntrix 等团名文字，或三个 3D 动画女团角色同框，应视为强命中。"
            "三个主角可拆成角色线索：Zoey 通常是活泼甜酷的少女形象，常见高能舞台姿态和明亮发色/妆造；"
            "Rumi 通常位于核心位，整体更像主唱/中心位，表情自信，服装带舞台战斗感；"
            "Mira 通常更高冷利落，轮廓修长，造型偏酷感。"
            "画面常带 3D 动画电影海报质感、女团舞台站位、霓虹/紫粉/蓝色强对比配色、偶像服装与猎魔/战斗元素混合，"
            "这类组合会让人一眼联想到该作品，而不是泛泛的女团插画。"
        )
    return ""


def infer_material_category(group_name, material_name):
    text = f"{group_name} {material_name}".lower()
    if "tpu" in text:
        return "TPU"
    if "硅胶" in text or "silicone" in text:
        return "硅胶"
    if "亚克力" in text:
        return "亚克力"
    if "玻璃" in text:
        return "玻璃"
    if "金属" in text:
        return "金属"
    if "皮" in text:
        return "皮革/贴皮"
    if "磁吸" in text:
        return "磁吸类"
    if "挂绳" in text:
        return "挂绳配件"
    if "贴件" in text:
        return "贴件配件"
    return material_name or group_name or None


def describe_specific_ip_appearance(ip_name, infringing_element):
    text = f"{ip_name}\n{infringing_element}".lower()
    if "monchhichi" in text or "蒙奇奇" in text:
        return {
            "brand_text": "通常可能出现 Monchhichi、モンチッチ 或蒙奇奇相关文字；即使没有文字，只要出现标志性小猴玩偶形象也应视为强风险线索。",
            "logo_style": "品牌视觉常围绕可爱复古玩偶风格展开，常见圆润、柔软、儿童玩具感的排版或贴纸拼贴效果。",
            "character_style": "角色形象是棕色小猴/玩偶娃娃，浅米色圆脸，大耳朵，圆眼睛，小鼻嘴，常带有婴儿感、毛绒玩具感或吸手指/抱抱姿态。",
            "silhouette": "标志性轮廓是圆头、突出圆耳、浅色脸盘和棕色毛绒身体组合；手机壳上常以重复头像、半身小猴或贴纸阵列出现。",
            "composition": "常见表现为粉色、奶油色或浅色底，上面密集排列多个小猴头像/半身图案，整体像可爱贴纸或儿童玩具周边。",
        }
    if "mickey" in text or "米奇" in text or "迪士尼" in text:
        return {
            "brand_text": "可能出现 Disney、Mickey、Minnie 等字样，也可能完全不出现文字。",
            "logo_style": "重点是迪士尼式圆润卡通风和米奇三圆头轮廓。",
            "character_style": "角色通常是黑色圆耳、圆脸、白手套、红黑配色或米妮蝴蝶结等元素。",
            "silhouette": "最核心轮廓是一个大圆头加两个圆耳朵的三圆组合，出现在气球、图标、杯子、冰棍或装饰图案里也算风险。",
            "composition": "常见为乐园、甜品、粉色可爱贴纸、卡通人物或米奇头符号的组合图案。",
        }
    if "barbie" in text or "芭比" in text:
        return {
            "brand_text": "常见 Barbie 连笔草书字样。",
            "logo_style": "粉色、女性化、连笔手写字体，常搭配亮粉色或Y2K风格。",
            "character_style": "可能出现金发娃娃、时尚女孩、粉色玩具娃娃风。",
            "silhouette": "重点识别芭比粉、连笔Logo、娃娃轮廓和时尚玩具包装感。",
            "composition": "常见大面积粉色底、Barbie字样重复、娃娃头像或复古玩具海报风。",
        }
    if "prada" in text or "普拉达" in text:
        return {
            "brand_text": "PRADA 大写字母是核心文字商标。",
            "logo_style": "高对比衬线字体、奢侈品排版，可能搭配三角标或极简大牌风。",
            "character_style": "一般不是角色IP，重点看品牌文字和奢侈品Logo。",
            "silhouette": "重点识别 PRADA 字母结构、经典衬线字体和奢侈品标牌式构图。",
            "composition": "常见居中大Logo、动物插画点缀、黑白或高级感底色。",
        }
    return None


def build_ip_association_details(ip_name, infringing_element, vision_description):
    text = f"{ip_name}\n{infringing_element}\n{vision_description}".lower()
    ip_type = infer_ip_type(ip_name, infringing_element)
    ip_grade = infer_ip_grade(ip_type)

    # 按照你的要求构建标准结构的三字段关联句式
    reason_text = infringing_element if infringing_element else (ip_name if ip_name else "无明显风险特征")
    grade_reason = f"因为识别到原因是{reason_text}，所以将其打分为{ip_grade}，符合的规则是{ip_type}。"

    details = {
        "ip_name": ip_name or "未标注IP/品牌",
        "ip_type": ip_type,
        "ip_grade": ip_grade,
        "grade_reason": grade_reason,
        "brand_text": f"以 Excel 标注的「{ip_name or '未标注IP/品牌'}」作为核心文字线索；如果视觉描述中出现同名英文、中文、缩写、角色名或品牌字样，应作为直接命中证据。",
        "logo_style": "根据视觉描述中的字形、图形商标、官方标题、海报/动画/专辑/周边风格判断是否接近官方授权视觉。",
        "character_image": "根据视觉描述中的人物、动物、卡通角色、服装、姿态、道具、表情和角色组合关系判断。",
        "iconic_silhouette": "根据视觉描述中的头身比例、耳朵、发型、帽子、武器、服饰剪影、图形商标或重复纹样判断。",
        "composition_style": "根据视觉描述中的单角色、多角色同框、头像重复、贴纸拼贴、海报裁切、Logo居中或专辑封面式构图判断。",
        "vision_evidence": vision_description or "视觉模型未产出有效描述，主要依据 Excel 标注和侵权元素说明。",
    }

    if "michelob" in text or "米凯罗" in text:
        details.update({
            "brand_text": "核心命中点是 Michelob、ULTRA、SUPERIOR LIGHT、Beer 等英文啤酒品牌文字；这些字样本身就能直接指向 Michelob ULTRA。",
            "logo_style": "常见为 Michelob 的斜体/手写感英文品牌字，与 ULTRA 的大写无衬线字体组合；画面可能搭配啤酒标签式排版、丝带、徽章或浅色底。",
            "character_image": "该类不是角色IP，重点不在人物，而在啤酒品牌文字、酒标排版和商业包装视觉。",
            "iconic_silhouette": "标志性轮廓主要是酒标式文字组合、居中的 Michelob/ULTRA 排版、徽章或丝带装饰。",
            "composition_style": "常见为品牌文字居中、大面积白底或浅色底、黑色/粉色/金色等品牌包装风格，像把啤酒标签直接移植到手机壳。",
        })
    elif "shopkins" in text or "购物精灵" in text:
        details.update({
            "brand_text": "核心文字线索是 Shopkins 或购物精灵；即使没有完整Logo，只要出现该系列角色名或官方玩具风格文字，也属于强线索。",
            "logo_style": "Shopkins 视觉通常是儿童盲盒玩具风，颜色高饱和、圆润、糖果感强，Logo/标题常带玩具包装和儿童动画气质。",
            "character_image": "核心角色是拟人化的食物、日用品、购物物件等小精灵，常见大眼睛、笑脸、小手脚、夸张表情和可爱姿态。",
            "iconic_silhouette": "标志性轮廓是小体积、圆润、Q版、物品拟人化；一组彩色小角色密集出现时非常接近 Shopkins 官方玩具图案。",
            "composition_style": "常见为多角色同框、贴纸拼贴、彩色小图案重复、儿童玩具包装式构图，整体是明亮、可爱、密集的盲盒角色风格。",
        })
    elif "curious george" in text or "好奇的乔治" in text:
        details.update({
            "brand_text": "核心文字线索是 Curious George 或《好奇的乔治》；出现作品名、绘本标题或相关英文标题时可直接指向该IP。",
            "logo_style": "视觉风格接近儿童绘本/动画，线条温和，颜色明亮，画面有故事书插画或儿童教育动画气质。",
            "character_image": "核心角色是棕色小猴乔治，通常圆脸、大耳朵、好奇表情，动作活泼，可能拿书、食物、玩具或与黄色帽子男人等元素同框。",
            "iconic_silhouette": "标志性轮廓是小猴的圆头、大耳、棕色身体和活泼肢体动作；只要猴子形象接近儿童绘本乔治，就属于强线索。",
            "composition_style": "常见为单个小猴插画、多个手机壳样式并排、绘本故事场景、贴纸拼贴或儿童动画海报式构图。",
        })
    elif "strawberry shortcake" in text or "草莓娃娃" in text or "草莓脆饼" in text:
        details.update({
            "brand_text": "核心文字线索是 Strawberry Shortcake、草莓娃娃或草莓脆饼；出现角色名或作品名可直接指向该IP。",
            "logo_style": "视觉风格通常是甜品、草莓、粉红/红色、复古儿童动画或女孩玩具包装风。",
            "character_image": "核心角色通常是戴草莓帽或带草莓元素的小女孩，可能搭配粉色猫、泰迪熊、茶杯、甜点等可爱配件。",
            "iconic_silhouette": "标志性轮廓是草莓帽、草莓图案、女孩头像/半身像、甜点和玩具感角色组合。",
            "composition_style": "常见为粉色或奶油色背景、草莓元素重复、角色贴纸拼贴、甜品茶会或复古玩具海报构图。",
        })
    elif "mickey" in text or "米奇" in text or "迪士尼" in text:
        details.update({
            "brand_text": "核心文字线索是 Disney、Mickey、Minnie 或迪士尼相关字样；没有文字时也要看米奇三圆头轮廓。",
            "logo_style": "迪士尼式圆润卡通风，可能出现官方标题字、乐园感图形或米奇/米妮周边视觉。",
            "character_image": "角色常见黑色圆耳、圆脸、白手套、红黑配色，米妮还可能出现蝴蝶结。",
            "iconic_silhouette": "最强线索是一个大圆头加两个圆耳朵的三圆组合。",
            "composition_style": "常见为角色头像、米奇头符号重复、乐园/甜品/贴纸拼贴构图。",
        })
    elif "prada" in text or "普拉达" in text:
        details.update({
            "brand_text": "核心文字线索是 PRADA 大写字母；出现该品牌字样通常可直接判定为奢侈品大牌风险。",
            "logo_style": "PRADA 常见高对比衬线字、三角标、极简奢侈品排版和高级感留白。",
            "character_image": "该类不是角色IP，重点是品牌文字和奢侈品Logo。",
            "iconic_silhouette": "标志性轮廓是 PRADA 字母结构、三角标牌或奢侈品标签式构图。",
            "composition_style": "常见为Logo居中、黑白或高级底色、少量插画点缀。",
        })

    return details


def build_ip_image_description(ip_name, infringing_element, image_role, vision_description=""):
    association = build_ip_association_details(ip_name, infringing_element, vision_description)
    detail = describe_specific_ip_appearance(ip_name, infringing_element)
    character_clues = describe_franchise_character_clues(ip_name, infringing_element)
    if detail:
        association.update({
            "brand_text": detail["brand_text"],
            "logo_style": detail["logo_style"],
            "character_image": detail["character_style"],
            "iconic_silhouette": detail["silhouette"],
            "composition_style": detail["composition"],
        })
    if character_clues and association["character_image"].startswith("根据视觉描述"):
        association["character_image"] = character_clues
    return (
        f"IP规则关联说明：该规则样本为{image_role}，用于识别手机壳图案中是否出现「{association['ip_name']}」相关元素。\n"
        f"IP名字：{association['ip_name']}\n"
        f"风险等级：{association['ip_grade']}（{association['ip_type']}）\n"
        f"评级依据：{association['grade_reason']}\n"
        f"视觉证据：{association['vision_evidence']}\n"
        f"品牌文字特征：{association['brand_text']}\n"
        f"Logo描述：{association['logo_style']}\n"
        f"角色/形象：{association['character_image']}\n"
        f"标志性轮廓：{association['iconic_silhouette']}\n"
        f"构图/风格：{association['composition_style']}\n"
        f"Excel侵权元素说明：{infringing_element or '未标注'}"
    )


def merge_vision_with_rule_description(vision_result, rule_description):
    if vision_result.get("status") == "VISION_RECOGNIZED" and vision_result.get("description"):
        return (
            f"视觉模型识别结果：{vision_result['description']}"
            f"规则归档说明：{rule_description}"
        )
    return (
        f"视觉模型识别状态：{vision_result.get('status')}。"
        f"视觉模型暂未产出有效图片描述，本条仅使用 Excel 标注生成规则归档说明。"
        f"规则归档说明：{rule_description}"
    )


def build_material_image_description(group_name, material_name, material_category, price):
    name = material_name or material_category or "未标注材质"
    text = f"{group_name} {material_name}".lower()
    if "磨砂" in text and "tpu" in text:
        return (
            f"这是一类纯色磨砂TPU手机壳规则样本，代表材质/款式为「{name}」。"
            "外观看起来是哑光、低反光的软壳或半软壳，表面没有明显亮面反射，视觉质感偏细腻、干爽、防滑。"
            "图片中常见奶油白、黑色、蓝色等纯色壳体，背板干净无复杂图案，整体是简洁的素色保护壳。"
            "壳体边框为全包结构，四角圆润，边缘略高于手机背面；镜头区域有整体加高保护台，多个摄像头孔位独立开孔，"
            "镜头圈周围有厚度和包边感，适合识别为工厂可稳定生产的TPU磨砂纯色手机壳。"
            f"参考价格：{price or '未标注'}。"
        )
    if "透明" in text:
        return (
            f"这是一类透明或半透明手机壳规则样本，代表材质/款式为「{name}」。"
            "外观重点是清透背板、可露出手机本体颜色，边框可能为软质TPU或硬质亚克力。"
            "识别时关注透明度、背板是否平整、边框是否包边、镜头孔是否加高，以及是否有磁吸环或透明硬壳结构。"
            f"参考价格：{price or '未标注'}。"
        )
    if "玻璃" in text:
        return (
            f"这是一类玻璃壳规则样本，代表材质/款式为「{name}」。"
            "外观通常更平整、更亮，背板有玻璃或类玻璃反光质感，边缘与镜头区域更规整。"
            "识别时关注亮面反射、硬质背板、清晰边界、玻璃纹理或金属漆效果。"
            f"参考价格：{price or '未标注'}。"
        )
    if "皮" in text:
        return (
            f"这是一类贴皮或皮革纹理手机壳规则样本，代表材质/款式为「{name}」。"
            "外观重点是皮纹、拼接、包覆感或仿皮革表面，质感比普通TPU更厚、更有纹理。"
            "识别时关注表面纹路、皮革颗粒感、边缘包覆、缝线或贴皮分层。"
            f"参考价格：{price or '未标注'}。"
        )
    return (
        f"这是一类工厂材质/配件规则样本，代表材质/款式为「{name}」。"
        f"所属分组为「{group_name or '未标注'}」，材质细分类推断为「{material_category or '未标注'}」。"
        "识别时需要把外观描述具体化：背板是透明、磨砂、亮面还是纹理面；壳体是软壳、硬壳还是复合结构；"
        "边框是否全包，镜头区域是否加高，是否有独立镜头圈、磁吸环、挂绳孔、贴件或其他功能结构；"
        "颜色是纯色、渐变、金属光泽还是可打印图案。"
        f"参考价格：{price or '未标注'}。"
    )


def infer_ip_visual_features(ip_name, infringing_element):
    text = f"{ip_name}\n{infringing_element}".lower()
    logo_clues = []
    character_clues = []
    shape_clues = []
    color_clues = []
    style_clues = []

    if any(k in text for k in ["logo", "商标", "品牌", "字母", "文字商标"]):
        logo_clues.append("存在品牌名、Logo、商标文字或标志性字体风险")
    if any(k in text for k in ["角色", "主角", "卡通", "动漫", "动画", "娃娃", "猫", "女孩", "人物"]):
        character_clues.append("存在角色形象、卡通人物或动漫人物特征风险")
    if any(k in text for k in ["轮廓", "图形", "格纹", "老花", "monogram", "silhouette", "心形", "骷髅"]):
        shape_clues.append("存在可被识别的标志性轮廓、图形商标、老花或图案组合")
    if any(k in text for k in ["粉", "红", "蓝", "黑", "白", "卡其", "彩虹", "绿色"]):
        color_clues.append("存在与品牌或IP强绑定的特定配色/商业外观")
    if any(k in text for k in ["街头", "潮牌", "y2k", "涂鸦", "拼贴", "复古", "专辑", "电影"]):
        style_clues.append("存在特定品牌、影视、音乐或潮流文化风格引用")

    return {
        "logo_clues": logo_clues,
        "character_clues": character_clues,
        "shape_clues": shape_clues,
        "color_clues": color_clues,
        "style_clues": style_clues,
    }


def infer_material_visual_features(group_name, material_name):
    text = f"{group_name} {material_name}".lower()
    material_clues = []
    texture_clues = []
    shape_clues = []
    color_clues = []
    usage_clues = []

    if "tpu" in text:
        material_clues.append("TPU软壳或半软壳，通常具备柔韧性、抗摔缓冲和包边保护能力")
    if "磨砂" in text:
        texture_clues.append("哑光磨砂表面，低反光，不透明或半透明，手感偏细腻防滑")
    if "透明" in text:
        texture_clues.append("透明或半透明外观，可露出手机本体颜色或内部结构")
    if "电镀" in text:
        texture_clues.append("边框或镜头区域可能带电镀亮面效果，呈现金属光泽")
    if "镜头" in text or "鹰眼" in text:
        shape_clues.append("镜头区域重点保护，常见独立镜头孔、镜头圈或加高镜头框")
    if "全包" in text or "四包" in text:
        shape_clues.append("全包边结构，覆盖手机四边，边角有包覆和防摔保护")
    if "磁吸" in text:
        usage_clues.append("支持磁吸/MagSafe类使用场景，可能包含磁环或磁吸结构")
    if "玻璃" in text:
        material_clues.append("玻璃背板或玻璃质感，表面更硬、更亮，抗刮但可能更脆")
    if "亚克力" in text:
        material_clues.append("亚克力硬质透明/半透明背板，外观平整，硬度高于普通软壳")
    if "金属" in text:
        material_clues.append("金属或金属漆效果，视觉上有金属光泽或喷涂质感")
    if "皮" in text:
        texture_clues.append("皮革或贴皮纹理，表面有皮纹、拼接或包覆质感")
    if "贝壳" in text:
        texture_clues.append("贝壳纹、珠光或彩虹反光纹理，偏装饰性外观")
    if "挂绳" in text:
        usage_clues.append("配件类挂绳，重点识别绳带、挂扣、连接片等结构")
    if "贴件" in text:
        usage_clues.append("配件类贴件，重点识别背贴、支架、装饰贴或功能贴片")
    if "纯色" in text:
        color_clues.append("纯色外观，无复杂图案，主要依靠颜色和材质质感区分")
    if "打印" in text:
        color_clues.append("支持图案打印版本，可能出现印刷图案或定制图案")

    if not material_clues:
        material_clues.append("根据Excel材质名称归类，需结合标题、卖点和图片外观进行匹配")
    if not shape_clues:
        shape_clues.append("手机壳主体结构，需关注边框、背板、镜头孔和按键区域")
    if not texture_clues:
        texture_clues.append("需关注表面光泽、透明度、软硬质感和纹理特征")

    return {
        "material_clues": material_clues,
        "texture_clues": texture_clues,
        "shape_clues": shape_clues,
        "color_clues": color_clues,
        "usage_clues": usage_clues,
    }


def analyze_rule_image_template(kind, image_bytes, source_context, row_context):
    """
    规则图片识别分析模板函数。

    规则表不保存图片或 base64；图片只在这个函数里临时使用。
    后续接入 MiniMax 视觉能力时，把 image_bytes + prompt 发给模型，
    并把模型返回 JSON 解析成下面这个结构即可。
    """
    image_sha256 = hashlib.sha256(image_bytes).hexdigest()
    vision_result = recognize_rule_image_with_local_model(kind, image_bytes, row_context)
    if kind == "IP":
        prompt = build_ip_rule_image_prompt()
        ip_name = row_context.get("ip_name", "")
        infringing_element = row_context.get("infringing_element", "")
        ip_type = infer_ip_type(ip_name, infringing_element)
        ip_grade = infer_ip_grade(ip_type)
        keywords = split_keywords(ip_name) + split_keywords(infringing_element)[:10]
        visual = infer_ip_visual_features(ip_name, infringing_element)
        vision_description = vision_result.get("description") or ""
        association = build_ip_association_details(
            ip_name, infringing_element, vision_description
        )
        rule_description = build_ip_image_description(
            ip_name,
            infringing_element,
            row_context.get("image_role", "规则图"),
            vision_description,
        )
        return {
            "prompt": prompt,
            "rule_name": ip_name or f"IP规则图 {source_context['index']:04d}",
            "ip_grade": ip_grade,
            "ip_type": ip_type,
            "keywords": keywords,
            "image_description": merge_vision_with_rule_description(
                vision_result, rule_description
            ),
            "ocr_text": "",
            "visual_features": {
                "status": vision_result["status"],
                "image_sha256": image_sha256,
                "excel_ip_name": ip_name,
                "excel_infringing_element": infringing_element,
                "vision_model": vision_result.get("model_id"),
                "vision_model_revision": vision_result.get("model_revision"),
                "vision_question": vision_result.get("question"),
                "vision_description": vision_result.get("description"),
                "vision_error": vision_result.get("error"),
                "ip_association": association,
                **visual,
            },
            # 此处严格按照三个字段统一逻辑进行输出赋值
            "rule_reason": association["grade_reason"],
        }

    prompt = build_material_rule_image_prompt()
    group_name = row_context.get("group_name", "")
    material_name = row_context.get("material_name", "")
    price = row_context.get("price", "")
    material_category = infer_material_category(group_name, material_name)
    keywords = split_keywords(group_name) + split_keywords(material_name)
    visual = infer_material_visual_features(group_name, material_name)
    rule_description = build_material_image_description(
        group_name, material_name, material_category, price
    )
    return {
        "prompt": prompt,
        "rule_name": material_name or f"材质规则图 {source_context['index']:04d}",
        "material_type": "工厂材质",
        "material_category": material_category,
        "keywords": keywords,
        "image_description": merge_vision_with_rule_description(
            vision_result, rule_description
        ),
        "ocr_text": "",
        "visual_features": {
            "status": vision_result["status"],
            "image_sha256": image_sha256,
            "excel_group_name": group_name,
            "excel_material_name": material_name,
            "excel_price": price,
            "vision_model": vision_result.get("model_id"),
            "vision_model_revision": vision_result.get("model_revision"),
            "vision_question": vision_result.get("question"),
            "vision_description": vision_result.get("description"),
            "vision_error": vision_result.get("error"),
            **visual,
        },
        "rule_reason": (
            f"根据 Excel 已标注信息，该规则用于识别「{material_name or material_category or '未标注材质'}」"
            f"这类工厂材质/配件。所属分组：{group_name or '未标注'}。"
        ),
    }


def upsert_ip_rule(cursor, rule):
    cursor.execute(
        """
        INSERT INTO `cp_ai_ip_rule`
        (`rule_code`, `rule_name`, `ip_grade`, `ip_type`, `keywords`,
         `image_description`, `ocr_text`, `visual_features`, `rule_reason`,
         `source_file`, `source_sheet`, `source_row`, `source_col`, `enabled`)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, 1) ON DUPLICATE KEY
        UPDATE
            `rule_name` =
        VALUES (`rule_name`), `ip_grade` =
        VALUES (`ip_grade`), `ip_type` =
        VALUES (`ip_type`), `keywords` =
        VALUES (`keywords`), `image_description` =
        VALUES (`image_description`), `ocr_text` =
        VALUES (`ocr_text`), `visual_features` =
        VALUES (`visual_features`), `rule_reason` =
        VALUES (`rule_reason`), `source_file` =
        VALUES (`source_file`), `source_sheet` =
        VALUES (`source_sheet`), `source_row` =
        VALUES (`source_row`), `source_col` =
        VALUES (`source_col`)
        """,
        (
            rule["rule_code"],
            rule["rule_name"],
            rule["ip_grade"],
            rule["ip_type"],
            rule["keywords"],
            rule["image_description"],
            rule["ocr_text"],
            rule["visual_features"],
            rule["rule_reason"],
            rule["source_file"],
            rule["source_sheet"],
            rule["source_row"],
            rule["source_col"],
        ),
    )


def upsert_material_rule(cursor, rule):
    cursor.execute(
        """
        INSERT INTO `cp_ai_material_rule`
        (`rule_code`, `rule_name`, `material_type`, `material_category`, `keywords`,
         `image_description`, `ocr_text`, `visual_features`, `rule_reason`,
         `source_file`, `source_sheet`, `source_row`, `source_col`, `enabled`)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, 1) ON DUPLICATE KEY
        UPDATE
            `rule_name` =
        VALUES (`rule_name`), `material_type` =
        VALUES (`material_type`), `material_category` =
        VALUES (`material_category`), `keywords` =
        VALUES (`keywords`), `image_description` =
        VALUES (`image_description`), `ocr_text` =
        VALUES (`ocr_text`), `visual_features` =
        VALUES (`visual_features`), `rule_reason` =
        VALUES (`rule_reason`), `source_file` =
        VALUES (`source_file`), `source_sheet` =
        VALUES (`source_sheet`), `source_row` =
        VALUES (`source_row`), `source_col` =
        VALUES (`source_col`)
        """,
        (
            rule["rule_code"],
            rule["rule_name"],
            rule["material_type"],
            rule["material_category"],
            rule["keywords"],
            rule["image_description"],
            rule["ocr_text"],
            rule["visual_features"],
            rule["rule_reason"],
            rule["source_file"],
            rule["source_sheet"],
            rule["source_row"],
            rule["source_col"],
        ),
    )


def import_workbook(cursor, config):
    xlsx_path = config["source_file"]
    source_sheet, sheet_values = load_first_sheet_values(xlsx_path)
    anchors = extract_image_anchors(xlsx_path)
    import_limit = int(os.getenv("CP_RULE_IMPORT_LIMIT", "0") or "0")

    imported = 0
    vision_status_counts = {}
    with zipfile.ZipFile(xlsx_path) as z:
        for index, anchor in enumerate(anchors, start=1):
            if import_limit and imported >= import_limit:
                break
            image_bytes = z.read(anchor["media_path"])
            analysis = analyze_rule_image_template(
                config["kind"],
                image_bytes,
                {
                    "index": index,
                    "source_file": xlsx_path,
                    "source_sheet": source_sheet,
                    "source_row": anchor["row"],
                    "source_col": anchor["col"],
                },
                get_ip_context(sheet_values, anchor)
                if config["kind"] == "IP"
                else get_material_context(sheet_values, anchor),
            )
            vision_status = analysis["visual_features"].get("status", "UNKNOWN")
            vision_status_counts[vision_status] = vision_status_counts.get(vision_status, 0) + 1
            rule = {
                "rule_code": f"{config['rule_prefix']}_{index:04d}",
                "rule_name": analysis["rule_name"],
                "ip_grade": analysis.get("ip_grade"),
                "ip_type": analysis.get("ip_type"),
                "material_type": analysis.get("material_type"),
                "material_category": analysis.get("material_category"),
                "keywords": json.dumps(analysis["keywords"], ensure_ascii=False),
                "image_description": analysis["image_description"],
                "ocr_text": analysis["ocr_text"],
                "visual_features": json.dumps(analysis["visual_features"], ensure_ascii=False),
                "rule_reason": analysis["rule_reason"],
                "source_file": xlsx_path,
                "source_sheet": source_sheet,
                "source_row": anchor["row"],
                "source_col": anchor["col"],
            }

            if config["kind"] == "IP":
                upsert_ip_rule(cursor, rule)
            else:
                upsert_material_rule(cursor, rule)
            imported += 1
    return {
        "kind": config["kind"],
        "imported_images": imported,
        "vision_status_counts": vision_status_counts,
    }


def main():
    conn = pymysql.connect(**DB_CONFIG)
    try:
        with conn.cursor() as cursor:
            ensure_tables(cursor)
            summary = []
            for config in WORKBOOKS:
                summary.append(import_workbook(cursor, config))
            conn.commit()
            print(json.dumps(summary, ensure_ascii=False, indent=2))
    finally:
        conn.close()


if __name__ == "__main__":
    main()