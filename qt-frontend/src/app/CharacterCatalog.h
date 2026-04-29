#pragma once

#include <QObject>
#include <QVariant>

// 角色目录扫描器。
// Character directory scanner.
//
// 它把本地 chara/<角色名>/prompt.txt 与 emotion 图片整理成 QML 易用的数据。
// It converts local chara/<character>/prompt.txt and emotion images into QML-friendly data.
class CharacterCatalog final : public QObject {
    Q_OBJECT
    Q_PROPERTY(QVariantList characters READ characters NOTIFY charactersChanged)
    Q_PROPERTY(QString charactersPath READ charactersPath NOTIFY charactersChanged)

public:
    explicit CharacterCatalog(QObject *parent = nullptr);

    // QVariantList/QVariantMap 适合从 C++ 传给 QML，当作 JS 数组/对象读取。
    // QVariantList/QVariantMap pass nicely from C++ to QML as JS-like arrays/objects.
    QVariantList characters() const;
    QString charactersPath() const;

    // QML 可调用接口：刷新目录、列出角色、查默认表情和图片。
    // QML-callable API: reload folders, list characters, find default emotions and images.
    Q_INVOKABLE void reload();
    Q_INVOKABLE bool openCharactersFolder() const;
    Q_INVOKABLE QStringList characterNames() const;
    Q_INVOKABLE QString defaultEmotion(const QString &characterName) const;
    Q_INVOKABLE QString imageFor(const QString &characterName, const QString &emotion) const;

signals:
    // 角色列表或角色目录路径变化时通知 QML 更新。
    // Notifies QML when the character list or character path changes.
    void charactersChanged();

private:
    // 下面这些私有函数负责“找目录 -> 读配置 -> 匹配图片”的流水线。
    // These private helpers implement the "find folder -> read config -> match images" pipeline.
    QString resolveCharactersPath() const;
    QVariantMap buildCharacterMap(const QString &characterDirPath) const;
    QVariantList parseEmotions(const QString &emotionsFilePath, const QStringList &imageFileNames) const;
    QStringList imageFilesFor(const QString &emotionDirPath) const;

    QVariantList m_characters;
    QString m_charactersPath;
};
