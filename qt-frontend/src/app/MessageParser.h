#pragma once

#include <QString>

// 解析后的消息：emotion 是表情/立绘状态，content 是真正显示的文本。
// Parsed message: emotion is the expression/portrait state, content is the visible text.
struct ParsedEmotionMessage {
    QString emotion;
    QString content;
};

// 消息解析工具类；只有静态函数，不需要创建对象。
// Message parsing utility; static-only, no instance needed.
class MessageParser {
public:
    // 支持类似 [happy]你好、【sad】你好、［angry］你好 这样的前缀。
    // Supports prefixes such as [happy]hello, 【sad】hello, and ［angry］hello.
    static ParsedEmotionMessage parseEmotionPrefix(const QString &value);

    // 统一表情名格式，便于把后端返回、JSON 配置和图片文件名进行匹配。
    // Normalizes emotion names so backend replies, JSON config, and image filenames can be matched.
    static QString normalizeEmotionToken(const QString &value);
};
