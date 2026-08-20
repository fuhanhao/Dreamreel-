#!/usr/bin/env python3
from pathlib import Path

TARGET = Path(__file__).resolve().parents[1] / (
    "services/api/src/main/java/com/dreamreel/api/dramaforge/service/DramaForgeService.java"
)

def main() -> None:
    text = TARGET.read_text(encoding="utf-8")
    import re
    text, n = re.subn(
        r'(\s+throw new IllegalStateException\(\s*)"\?\? " \+ shot\.getShotNumber\(\)\s*\+ "[^"]*"\);',
        r'\1"镜头 " + shot.getShotNumber()\n'
        r'                            + " 缺少可用参考图：请先生成角色/场景/道具设计图，并确认镜头已规划出场资产。");',
        text,
        count=1,
    )
    TARGET.write_text(text, encoding="utf-8", newline="\n")
    print("replaced", n)

if __name__ == "__main__":
    main()
