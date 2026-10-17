# compat: TeaVM 0.16.0 registers the six standard charsets under their canonical names only. The JDK also knows
# them by their historical java.io names and common aliases ("UTF8", "ISO8859_1", "latin1", "ASCII",
# "UnicodeBigUnmarked", ...), which is what ZXing's CharacterSetECI looks up (QR codes with a UTF-8 ECI).
s/                value.put(charset.name(), charset);/                value.put(charset.name(), charset); compatAddAliases(value, charset);/
