# Эмодзи в стиле iOS

Свои эмодзи Apple распространять нельзя, поэтому RyzikChat использует похожий объёмный набор
[Microsoft Fluent Emoji 3D](https://github.com/microsoft/fluentui-emoji) (лицензия MIT).

`build_font.py` собирает из него шрифт для `androidx.emoji2` (CBDT + метаданные `meta/Emji`).
CI делает это сам перед сборкой APK. Локально:

```
pip install fonttools pillow flatbuffers   # и pngquant, чтобы шрифт был меньше
git clone --depth 1 https://github.com/microsoft/fluentui-emoji /tmp/fluent
python3 tools/emoji/build_font.py /tmp/fluent/assets android/app/src/main/assets/emoji/ryzik_emoji.ttf
```
