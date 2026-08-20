#!/usr/bin/env python3
import re
from pathlib import Path

TARGET = Path(__file__).resolve().parents[1] / (
    "services/api/src/main/java/com/dreamreel/api/dramaforge/service/DramaForgeService.java"
)

IMG2IMG_FAIL = (
    "            if (requireReference) {\n"
    "                throw new IllegalStateException(\n"
    '                        "镜头 " + shot.getShotNumber() + " 图生图失败: "\n'
    '                                + (lastError.isEmpty() ? "未知错误" : lastError));\n'
    "            }\n"
)

MISSING_REF = (
    "        if (requireReference) {\n"
    "            throw new IllegalStateException(\n"
    '                    "镜头 " + shot.getShotNumber() + " 缺少参考图，无法生成分镜");\n'
    "        }\n"
)


def main() -> None:
    text = TARGET.read_text(encoding="utf-8")
    text, n1 = re.subn(
        r"            if \(requireReference\) \{\n"
        r"                throw new IllegalStateException\(\n"
        r'                        "[^"]*" \+ shot\.getShotNumber\(\) \+ "[^"]*"\n'
        r'                                \+ \(lastError\.isEmpty\(\) \? "[^"]*" : lastError\)\);\n'
        r"            \}\n",
        IMG2IMG_FAIL,
        text,
        count=1,
    )
    text, n2 = re.subn(
        r"        if \(requireReference\) \{\n"
        r"            throw new IllegalStateException\(\n"
        r'                    "[^"]*" \+ shot\.getShotNumber\(\) \+ "[^"]*"\);\n'
        r"        \}\n\n"
        r"        return imageGenerationService\.createForProject",
        MISSING_REF + "\n        return imageGenerationService.createForProject",
        text,
        count=1,
    )
    TARGET.write_text(text, encoding="utf-8", newline="\n")
    print(f"img2img={n1}, missing_ref={n2}")


if __name__ == "__main__":
    main()
