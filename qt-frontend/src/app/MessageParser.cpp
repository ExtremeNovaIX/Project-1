#include "MessageParser.h"

#include <QRegularExpression>

namespace {

// 匹配消息开头的表情标记，支持英文中括号、中文中括号和全角中括号。
// Match an emotion tag at the start of a message; supports ASCII, Chinese, and full-width brackets.
const QRegularExpression kEmotionPrefixPattern(
    QStringLiteral(R"(^\s*[\[\x{3010}\x{FF3B}]([^\]\x{3011}\x{FF3D}]+)[\]\x{3011}\x{FF3D}]\s*)"));

} // namespace

ParsedEmotionMessage MessageParser::parseEmotionPrefix(const QString &value) {
    // 先去掉首尾空白，避免用户输入或后端返回的空格影响解析。
    // Trim first so user/backend whitespace does not affect parsing.
    const QString trimmed = value.trimmed();
    const QRegularExpressionMatch match = kEmotionPrefixPattern.match(trimmed);

    // 没有表情前缀时，emotion 留空，content 使用原文本。
    // Without an emotion prefix, leave emotion empty and keep the text as content.
    if (!match.hasMatch()) {
        return ParsedEmotionMessage{QString(), trimmed};
    }

    // 去掉前缀后剩下的就是实际聊天内容。
    // After removing the prefix, the rest is the actual chat content.
    QString content = trimmed;
    content.remove(kEmotionPrefixPattern);

    return ParsedEmotionMessage{
        match.captured(1).trimmed(),
        content.trimmed()
    };
}

QString MessageParser::normalizeEmotionToken(const QString &value) {
    // 小写化、去括号、去空白后，Happy、[happy]、ha ppy 都能匹配到同一表情。
    // Lowercase, remove brackets, and strip spaces so Happy, [happy], and ha ppy can match.
    QString normalized = value.trimmed().toLower();
    normalized.remove(QRegularExpression(QStringLiteral(R"(^[\[\]\s\x{3010}\x{3011}\x{FF3B}\x{FF3D}]+|[\[\]\s\x{3010}\x{3011}\x{FF3B}\x{FF3D}]+$)")));
    normalized.remove(QRegularExpression(QStringLiteral(R"(\s+)")));
    return normalized;
}
