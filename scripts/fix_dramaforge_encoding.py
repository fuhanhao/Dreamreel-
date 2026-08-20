#!/usr/bin/env python3
from pathlib import Path

TARGET = Path(__file__).resolve().parents[1] / (
    "services/api/src/main/java/com/dreamreel/api/dramaforge/service/DramaForgeService.java"
)


def main() -> None:
    lines = TARGET.read_text(encoding="utf-8").splitlines(keepends=True)

    start = next(i for i, line in enumerate(lines) if "private String inventVoiceLabel" in line)
    end = next(i for i, line in enumerate(lines[start:], start) if "var out = result.outputText" in line)

    new_block = [
        '        var user = "请根据角色名称与描述，用一句中文概括该角色的音色特征（如：低沉男声、温柔女声、少年音）。角色："\n',
        '                + asset.getName() + "，描述："\n',
        '                + (asset.getDescription() != null ? asset.getDescription() : "无");\n',
        "        var result = tokenFreeClient.createChatCompletion(apiKey, model, java.util.List.of(\n",
        '                new TokenFreeClient.ChatMessage("system", "只输出一句音色描述，不要引号，不要解释。"),\n',
        '                new TokenFreeClient.ChatMessage("user", user)));\n',
        "        if (result.outputText() == null || result.outputText().isBlank()) {\n",
        '            return "自然男声";\n',
        "        }\n",
    ]
    lines[start + 3 : end] = new_block

    text = "".join(lines)
    text = text.replace(
        'return "??????" + asset.getName() + "??????" + voiceLabel + "?????????";',
        'return "大家好，我是" + asset.getName() + "。我的音色是" + voiceLabel + "，很高兴认识你们。";',
    )
    text = text.replace(
        'var female = text.contains("?") || text.contains("??") || text.contains("??") || text.contains("??")',
        'var female = text.contains("女") || text.contains("少女") || text.contains("女孩") || text.contains("温柔")',
    )
    text = text.replace(
        'var male = text.contains("?") || text.contains("??") || text.contains("??") || text.contains("??")',
        'var male = text.contains("男") || text.contains("少年") || text.contains("男子") || text.contains("低沉")',
    )
    text = text.replace("// OpenAI ??", "// OpenAI 兼容")

    old_err = (
        '                    "?????????TokenFree TTS ?? " + ttsModel + " ?????"\n'
        '                            + "?? TokenFree ???????? ElevenLabs??? application-local.yml ?? "\n'
        '                            + "dreamreel.tokenfree.default-tts-model ? elevenlabs/text-to-speech-turbo-2-5 ??????"\n'
        '                            + " ??: " + ex.getMessage(),'
    )
    new_err = (
        '                    "角色音色生成失败：TokenFree TTS 模型 " + ttsModel + " 调用出错。"\n'
        '                            + "请在 TokenFree 控制台确认已开通 ElevenLabs，并在 application-local.yml 设置 "\n'
        '                            + "dreamreel.tokenfree.default-tts-model 为 elevenlabs/text-to-speech-turbo-2-5 等可用模型。"\n'
        '                            + " 详情: " + ex.getMessage(),'
    )
    text = text.replace(old_err, new_err)

    # generateStoryboardImage 图生图失败提示
    img2img_fail = (
        '            if (requireReference) {\n'
        '                throw new IllegalStateException(\n'
        '                        "镜头 " + shot.getShotNumber() + " 图生图失败: "\n'
        '                                + (lastError.isEmpty() ? "未知错误" : lastError));\n'
        '            }\n'
    )
    missing_ref = (
        '        if (requireReference) {\n'
        '            throw new IllegalStateException(\n'
        '                    "镜头 " + shot.getShotNumber() + " 缺少参考图，无法生成分镜");\n'
        '        }\n'
    )

    # Replace garbled blocks inside generateStoryboardImage
    import re

    text = re.sub(
        r"            if \(requireReference\) \{\n"
        r"                throw new IllegalStateException\(\n"
        r'                        "[^"]*" \+ shot\.getShotNumber\(\) \+ "[^"]*"\n'
        r'                                \+ \(lastError\.isEmpty\(\) \? "[^"]*" : lastError\)\);\n'
        r"            \}\n",
        img2img_fail,
        text,
        count=1,
    )
    text = re.sub(
        r"        if \(requireReference\) \{\n"
        r"            throw new IllegalStateException\(\n"
        r'                    "[^"]*" \+ shot\.getShotNumber\(\) \+ "[^"]*"\);\n'
        r"        \}\n\n"
        r"        return imageGenerationService\.createForProject",
        missing_ref + "\n        return imageGenerationService.createForProject",
        text,
        count=1,
    )

    TARGET.write_text(text, encoding="utf-8", newline="\n")
    print(f"Fixed {TARGET}")


if __name__ == "__main__":
    main()
