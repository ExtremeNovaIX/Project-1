#include "CharacterCatalog.h"

#include "MessageParser.h"

#include <QCoreApplication>
#include <QDesktopServices>
#include <QDir>
#include <QFile>
#include <QFileInfo>
#include <QJsonArray>
#include <QJsonDocument>
#include <QJsonObject>
#include <QUrl>

namespace {

QString normalizeBaseName(const QString &fileName) {
    // completeBaseName 会去掉最后一个扩展名，例如 happy.png -> happy。
    // completeBaseName removes the final extension, e.g. happy.png -> happy.
    return QFileInfo(fileName).completeBaseName();
}

QString resolveImageMatch(const QString &emotionName, const QStringList &imageFileNames) {
    // 先把表情名和图片文件名都规范化，再做匹配。
    // Normalize both the emotion name and image filename before matching.
    const QString normalizedEmotion = MessageParser::normalizeEmotionToken(emotionName);
    for (const QString &fileName : imageFileNames) {
        if (MessageParser::normalizeEmotionToken(normalizeBaseName(fileName)) == normalizedEmotion) {
            return fileName;
        }
    }
    // 找不到同名图片时，用第一张图做兜底，避免角色完全没有立绘。
    // If no matching image exists, use the first image as fallback so the character still has a portrait.
    return imageFileNames.isEmpty() ? QString() : imageFileNames.first();
}

} // namespace

CharacterCatalog::CharacterCatalog(QObject *parent)
    : QObject(parent) {
    // 创建后立即扫描一次角色目录，让 QML 首屏就能拿到数据。
    // Scan the character folder immediately so QML has data on first render.
    reload();
}

QVariantList CharacterCatalog::characters() const {
    return m_characters;
}

QString CharacterCatalog::charactersPath() const {
    return m_charactersPath;
}

void CharacterCatalog::reload() {
    // 重新扫描时先清空旧缓存。
    // Clear the old cache before rescanning.
    m_characters.clear();
    m_charactersPath = resolveCharactersPath();

    if (!m_charactersPath.isEmpty()) {
        QDir rootDir(m_charactersPath);
        // 只扫描 chara 目录下的一级子目录，每个子目录代表一个角色。
        // Scan only first-level subdirectories under chara; each folder is one character.
        const QFileInfoList entries = rootDir.entryInfoList(QDir::Dirs | QDir::NoDotAndDotDot, QDir::Name);
        for (const QFileInfo &entry : entries) {
            const QVariantMap character = buildCharacterMap(entry.absoluteFilePath());
            if (!character.isEmpty()) {
                m_characters.push_back(character);
            }
        }
    }

    // 无论是否找到目录都发信号，让界面能显示“未找到”等状态。
    // Emit even if no folder is found so the UI can display states like "Not found".
    emit charactersChanged();
}

bool CharacterCatalog::openCharactersFolder() const {
    if (m_charactersPath.isEmpty() || !QFileInfo::exists(m_charactersPath)) {
        return false;
    }
    return QDesktopServices::openUrl(QUrl::fromLocalFile(m_charactersPath));
}

QStringList CharacterCatalog::characterNames() const {
    // QML ComboBox 只需要名字列表，所以从缓存 map 中提取 name。
    // QML ComboBox only needs names, so extract name from each cached map.
    QStringList names;
    for (const QVariant &character : m_characters) {
        names.append(character.toMap().value(QStringLiteral("name")).toString());
    }
    return names;
}

QString CharacterCatalog::defaultEmotion(const QString &characterName) const {
    // 根据角色名查默认表情，用于初次显示或切换角色。
    // Look up the default emotion by character name for initial display or character switching.
    for (const QVariant &character : m_characters) {
        const QVariantMap map = character.toMap();
        if (map.value(QStringLiteral("name")).toString() == characterName) {
            return map.value(QStringLiteral("defaultEmotion")).toString();
        }
    }
    return QString();
}

QString CharacterCatalog::imageFor(const QString &characterName, const QString &emotion) const {
    // 后端返回的 emotion 可能大小写或括号不同，先规范化。
    // Backend emotion text may differ in case or brackets, so normalize it first.
    const QString normalizedEmotion = MessageParser::normalizeEmotionToken(emotion);

    for (const QVariant &character : m_characters) {
        const QVariantMap map = character.toMap();
        if (map.value(QStringLiteral("name")).toString() != characterName) {
            continue;
        }

        const QVariantMap imageUrls = map.value(QStringLiteral("imageUrls")).toMap();
        // 优先找与当前表情匹配的图片。
        // Prefer an image matching the current emotion.
        for (auto it = imageUrls.cbegin(); it != imageUrls.cend(); ++it) {
            if (MessageParser::normalizeEmotionToken(it.key()) == normalizedEmotion) {
                return it.value().toString();
            }
        }
        // 没有当前表情图片时，回退到默认表情图片。
        // If the current emotion has no image, fall back to the default emotion image.
        return imageUrls.value(map.value(QStringLiteral("defaultEmotion")).toString()).toString();
    }

    return QString();
}

QString CharacterCatalog::resolveCharactersPath() const {
    // 支持从源码目录、构建目录或安装目录启动：从当前目录和程序目录向上找 chara。
    // Support launching from source, build, or install folders by walking upward from current/app dirs.
    QStringList searchRoots;
    searchRoots << QDir::currentPath() << QCoreApplication::applicationDirPath();

    for (const QString &root : searchRoots) {
        QDir dir(root);
        for (int depth = 0; depth < 6; ++depth) {
            if (dir.exists(QStringLiteral("chara"))) {
                return dir.absoluteFilePath(QStringLiteral("chara"));
            }
            if (!dir.cdUp()) {
                break;
            }
        }
    }

    return QString();
}

QVariantMap CharacterCatalog::buildCharacterMap(const QString &characterDirPath) const {
    // 约定目录结构：
    // Expected folder layout:
    // chara/<name>/prompt.txt
    // chara/<name>/emotion/emotions.json
    // chara/<name>/emotion/*.png|*.jpg|*.webp|*.gif
    const QDir characterDir(characterDirPath);
    const QString promptFilePath = characterDir.filePath(QStringLiteral("prompt.txt"));
    const QString emotionDirPath = characterDir.filePath(QStringLiteral("emotion"));
    const QString emotionsFilePath = QDir(emotionDirPath).filePath(QStringLiteral("emotions.json"));
    const QStringList imageFileNames = imageFilesFor(emotionDirPath);

    // prompt.txt 是角色有效性的最低要求；没有提示词就不加入列表。
    // prompt.txt is the minimum requirement for a valid character; skip folders without it.
    if (!QFileInfo::exists(promptFilePath)) {
        return QVariantMap();
    }

    // 如果有图片，就尝试解析 emotions.json；没有 JSON 时会用图片名生成表情。
    // If images exist, parse emotions.json; without JSON, image basenames become emotions.
    const QVariantList emotions = imageFileNames.isEmpty()
        ? QVariantList()
        : parseEmotions(emotionsFilePath, imageFileNames);
    QVariantMap imageUrls;

    // 把本地文件路径转成 file:// URL，QML Image.source 可以直接使用。
    // Convert local file paths to file:// URLs, which QML Image.source can use directly.
    for (const QVariant &emotionValue : emotions) {
        const QVariantMap emotion = emotionValue.toMap();
        const QString emotionName = emotion.value(QStringLiteral("emotion")).toString();
        const QString fileName = emotion.value(QStringLiteral("fileName")).toString();
        imageUrls.insert(emotionName, QUrl::fromLocalFile(QDir(emotionDirPath).filePath(fileName)).toString());
    }

    // 如果 emotions.json 没解析出结果，但目录里有图片，至少注册第一张图。
    // If emotions.json yields nothing but images exist, register at least the first image.
    if (imageUrls.isEmpty()) {
        if (!imageFileNames.isEmpty()) {
            const QString fallbackName = normalizeBaseName(imageFileNames.first());
            imageUrls.insert(fallbackName, QUrl::fromLocalFile(QDir(emotionDirPath).filePath(imageFileNames.first())).toString());
        }
    }

    // 默认表情优先取 emotions.json 的第一项；没有时取第一张图片。
    // Default emotion prefers the first emotions.json item; otherwise use the first image.
    const QString defaultEmotionName = emotions.isEmpty()
        ? (imageUrls.isEmpty() ? QString() : imageUrls.firstKey())
        : emotions.first().toMap().value(QStringLiteral("emotion")).toString();

    return {
        {QStringLiteral("name"), characterDir.dirName()},
        {QStringLiteral("defaultEmotion"), defaultEmotionName},
        {QStringLiteral("emotions"), emotions},
        {QStringLiteral("iconUrl"), defaultEmotionName.isEmpty() ? QString() : imageUrls.value(defaultEmotionName).toString()},
        {QStringLiteral("imageUrls"), imageUrls}
    };
}

QVariantList CharacterCatalog::parseEmotions(const QString &emotionsFilePath, const QStringList &imageFileNames) const {
    QFile file(emotionsFilePath);
    // 没有 emotions.json 时，直接用图片文件名当作表情名。
    // Without emotions.json, use image filenames as emotion names.
    if (!file.exists() || !file.open(QIODevice::ReadOnly)) {
        QVariantList fallback;
        for (const QString &fileName : imageFileNames) {
            const QString emotionName = normalizeBaseName(fileName);
            fallback.append(QVariantMap{
                {QStringLiteral("emotion"), emotionName},
                {QStringLiteral("fileName"), fileName}
            });
        }
        return fallback;
    }

    const QJsonDocument document = QJsonDocument::fromJson(file.readAll());
    QVariantList emotions;

    // 小工具：记录一个表情，并把配置里的文件名匹配到真实存在的图片。
    // Small helper: record one emotion and match the configured filename to an existing image.
    const auto appendEmotion = [&](const QString &emotionName, const QString &preferredFile) {
        const QString resolvedFile = preferredFile.isEmpty()
            ? resolveImageMatch(emotionName, imageFileNames)
            : resolveImageMatch(preferredFile, imageFileNames);
        if (resolvedFile.isEmpty()) {
            return;
        }

        emotions.append(QVariantMap{
            {QStringLiteral("emotion"), emotionName},
            {QStringLiteral("fileName"), resolvedFile}
        });
    };

    // 支持数组格式：["happy"] 或 [{ "name": "happy", "file": "happy.png" }]。
    // Support array format: ["happy"] or [{ "name": "happy", "file": "happy.png" }].
    if (document.isArray()) {
        const QJsonArray array = document.array();
        for (const QJsonValue &value : array) {
            if (value.isString()) {
                appendEmotion(value.toString(), value.toString());
                continue;
            }

            const QJsonObject object = value.toObject();
            const QString emotionName = object.value(QStringLiteral("name")).toString(
                object.value(QStringLiteral("label")).toString(
                    object.value(QStringLiteral("key")).toString()));
            const QString fileName = object.value(QStringLiteral("file")).toString(
                object.value(QStringLiteral("fileName")).toString(
                    object.value(QStringLiteral("image")).toString()));
            if (!emotionName.isEmpty()) {
                appendEmotion(emotionName, fileName);
            }
        }
    // 支持对象格式：{ "emotions": [...] } 或 { "happy": "happy.png" }。
    // Support object format: { "emotions": [...] } or { "happy": "happy.png" }.
    } else if (document.isObject()) {
        const QJsonObject object = document.object();
        const QJsonArray nestedEmotions = object.value(QStringLiteral("emotions")).toArray();

        if (!nestedEmotions.isEmpty()) {
            for (const QJsonValue &value : nestedEmotions) {
                if (!value.isObject()) {
                    continue;
                }
                const QJsonObject item = value.toObject();
                const QString emotionName = item.value(QStringLiteral("name")).toString(
                    item.value(QStringLiteral("label")).toString(
                        item.value(QStringLiteral("key")).toString()));
                const QString fileName = item.value(QStringLiteral("file")).toString(
                    item.value(QStringLiteral("fileName")).toString(
                        item.value(QStringLiteral("image")).toString()));
                if (!emotionName.isEmpty()) {
                    appendEmotion(emotionName, fileName);
                }
            }
        } else {
            for (auto it = object.begin(); it != object.end(); ++it) {
                if (it.value().isString()) {
                    appendEmotion(it.key(), it.value().toString());
                    continue;
                }
                if (!it.value().isObject()) {
                    continue;
                }
                const QJsonObject item = it.value().toObject();
                const QString emotionName = item.value(QStringLiteral("name")).toString(
                    item.value(QStringLiteral("label")).toString(
                        item.value(QStringLiteral("key")).toString(it.key())));
                const QString fileName = item.value(QStringLiteral("file")).toString(
                    item.value(QStringLiteral("fileName")).toString(
                        item.value(QStringLiteral("image")).toString(emotionName)));
                appendEmotion(emotionName, fileName);
            }
        }
    }

    // JSON 存在但内容不可用时，仍然用图片文件名兜底。
    // If JSON exists but is unusable, still fall back to image filenames.
    if (emotions.isEmpty()) {
        for (const QString &fileName : imageFileNames) {
            const QString emotionName = normalizeBaseName(fileName);
            emotions.append(QVariantMap{
                {QStringLiteral("emotion"), emotionName},
                {QStringLiteral("fileName"), fileName}
            });
        }
    }

    return emotions;
}

QStringList CharacterCatalog::imageFilesFor(const QString &emotionDirPath) const {
    // 只列出 QML Image 常见可显示格式，并按文件名排序，保证默认图稳定。
    // List common QML Image formats only, sorted by name for stable default selection.
    const QDir emotionDir(emotionDirPath);
    return emotionDir.entryList(
        QStringList{QStringLiteral("*.png"), QStringLiteral("*.jpg"), QStringLiteral("*.jpeg"), QStringLiteral("*.webp"), QStringLiteral("*.gif")},
        QDir::Files,
        QDir::Name);
}
