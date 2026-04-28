#include "FrontendSettings.h"

#include <QCoreApplication>
#include <QSettings>
#include <QtGlobal>

namespace {

int clampValue(int value, int min, int max) {
    // 把数值限制在允许范围内，防止 QML 滑块或配置文件写入异常值。
    // Clamp values into an allowed range to protect against odd QML slider or config values.
    return qMin(max, qMax(min, value));
}

} // namespace

FrontendSettings::FrontendSettings(QObject *parent)
    : QObject(parent) {
    // QSettings 默认用组织名/应用名决定保存位置。
    // QSettings uses organization/application names to decide where settings are stored.
    QCoreApplication::setOrganizationName(QStringLiteral("Arklight"));
    QCoreApplication::setApplicationName(QStringLiteral("ArklightQtFrontend"));

    // 先放入默认值，再用本地保存值覆盖。
    // Fill defaults first, then override them with saved local values.
    reset();
    load();
}

QString FrontendSettings::themeId() const {
    return m_themeId;
}

QString FrontendSettings::characterName() const {
    return m_characterName;
}

QString FrontendSettings::sessionId() const {
    return m_sessionId;
}

QString FrontendSettings::workspaceName() const {
    return m_workspaceName;
}

QString FrontendSettings::operatorName() const {
    return m_operatorName;
}

bool FrontendSettings::bootAnimationEnabled() const {
    return m_bootAnimationEnabled;
}

int FrontendSettings::bootDurationMs() const {
    return m_bootDurationMs;
}

int FrontendSettings::responseDelayMs() const {
    return m_responseDelayMs;
}

int FrontendSettings::moteCount() const {
    return m_moteCount;
}

int FrontendSettings::uiScalePercent() const {
    return m_uiScalePercent;
}

QString FrontendSettings::backendBaseUrl() const {
    return m_backendBaseUrl;
}

QString FrontendSettings::aiBaseUrl() const {
    return m_aiBaseUrl;
}

QString FrontendSettings::aiApiKey() const {
    return m_aiApiKey;
}

QString FrontendSettings::aiModelName() const {
    return m_aiModelName;
}

QString FrontendSettings::embeddingBaseUrl() const {
    return m_embeddingBaseUrl;
}

QString FrontendSettings::embeddingApiKey() const {
    return m_embeddingApiKey;
}

QString FrontendSettings::embeddingModelName() const {
    return m_embeddingModelName;
}

void FrontendSettings::reset() {
    // reset 只改内存中的当前值；真正写入磁盘由 save() 完成。
    // reset only changes in-memory values; save() is responsible for writing to disk.
    m_themeId = QStringLiteral("system");
    m_characterName.clear();
    m_sessionId = QStringLiteral("qt-session");
    m_workspaceName = QStringLiteral("ArkLight Pioneer");
    m_operatorName = QStringLiteral("Local");
    m_bootAnimationEnabled = true;
    m_bootDurationMs = 1200;
    m_responseDelayMs = 800;
    m_moteCount = 28;
    m_uiScalePercent = 100;
    m_backendBaseUrl = QStringLiteral("http://localhost:8080");
    m_aiBaseUrl.clear();
    m_aiApiKey.clear();
    m_aiModelName.clear();
    m_embeddingBaseUrl.clear();
    m_embeddingApiKey.clear();
    m_embeddingModelName.clear();
    emit settingsChanged();
}

void FrontendSettings::save() {
    // QSettings 会在不同平台使用不同后端：Windows 通常是注册表，macOS/Linux 使用配置文件。
    // QSettings uses platform-native storage: usually Registry on Windows and config files on macOS/Linux.
    QSettings settings;
    settings.setValue(QStringLiteral("themeId"), m_themeId);
    settings.setValue(QStringLiteral("characterName"), m_characterName);
    settings.setValue(QStringLiteral("sessionId"), m_sessionId);
    settings.setValue(QStringLiteral("workspaceName"), m_workspaceName);
    settings.setValue(QStringLiteral("operatorName"), m_operatorName);
    settings.setValue(QStringLiteral("bootAnimationEnabled"), m_bootAnimationEnabled);
    settings.setValue(QStringLiteral("bootDurationMs"), m_bootDurationMs);
    settings.setValue(QStringLiteral("responseDelayMs"), m_responseDelayMs);
    settings.setValue(QStringLiteral("moteCount"), m_moteCount);
    settings.setValue(QStringLiteral("uiScalePercent"), m_uiScalePercent);
    settings.setValue(QStringLiteral("backendBaseUrl"), m_backendBaseUrl);
    settings.setValue(QStringLiteral("aiBaseUrl"), m_aiBaseUrl);
    settings.setValue(QStringLiteral("aiApiKey"), m_aiApiKey);
    settings.setValue(QStringLiteral("aiModelName"), m_aiModelName);
    settings.setValue(QStringLiteral("embeddingBaseUrl"), m_embeddingBaseUrl);
    settings.setValue(QStringLiteral("embeddingApiKey"), m_embeddingApiKey);
    settings.setValue(QStringLiteral("embeddingModelName"), m_embeddingModelName);
}

void FrontendSettings::setThemeId(const QString &value) {
    // setter 统一做规范化和“值没变就不发信号”，避免 QML 重复刷新。
    // Setters normalize input and skip unchanged values to avoid unnecessary QML refreshes.
    QString normalized = value.trimmed().toLower();
    if (normalized.isEmpty() || normalized == QStringLiteral("qt-default")) {
        normalized = QStringLiteral("system");
    }
    if (normalized != QStringLiteral("system") && normalized != QStringLiteral("light") && normalized != QStringLiteral("dark")) {
        normalized = QStringLiteral("system");
    }
    if (m_themeId == normalized) {
        return;
    }
    m_themeId = normalized;
    emit settingsChanged();
}

void FrontendSettings::setCharacterName(const QString &value) {
    const QString normalized = value.trimmed();
    if (m_characterName == normalized) {
        return;
    }
    m_characterName = normalized;
    emit settingsChanged();
}

void FrontendSettings::setSessionId(const QString &value) {
    const QString normalized = value.trimmed().isEmpty() ? QStringLiteral("qt-session") : value.trimmed();
    if (m_sessionId == normalized) {
        return;
    }
    m_sessionId = normalized;
    emit settingsChanged();
}

void FrontendSettings::setWorkspaceName(const QString &value) {
    const QString normalized = value.trimmed().isEmpty() ? QStringLiteral("ArkLight Pioneer") : value.trimmed();
    if (m_workspaceName == normalized) {
        return;
    }
    m_workspaceName = normalized;
    emit settingsChanged();
}

void FrontendSettings::setOperatorName(const QString &value) {
    const QString normalized = value.trimmed().isEmpty() ? QStringLiteral("Local") : value.trimmed();
    if (m_operatorName == normalized) {
        return;
    }
    m_operatorName = normalized;
    emit settingsChanged();
}

void FrontendSettings::setBootAnimationEnabled(bool value) {
    if (m_bootAnimationEnabled == value) {
        return;
    }
    m_bootAnimationEnabled = value;
    emit settingsChanged();
}

void FrontendSettings::setBootDurationMs(int value) {
    const int normalized = clampValue(value, 0, 10000);
    if (m_bootDurationMs == normalized) {
        return;
    }
    m_bootDurationMs = normalized;
    emit settingsChanged();
}

void FrontendSettings::setResponseDelayMs(int value) {
    const int normalized = clampValue(value, 0, 10000);
    if (m_responseDelayMs == normalized) {
        return;
    }
    m_responseDelayMs = normalized;
    emit settingsChanged();
}

void FrontendSettings::setMoteCount(int value) {
    const int normalized = clampValue(value, 0, 120);
    if (m_moteCount == normalized) {
        return;
    }
    m_moteCount = normalized;
    emit settingsChanged();
}

void FrontendSettings::setUiScalePercent(int value) {
    const int normalized = clampValue(value, 80, 140);
    if (m_uiScalePercent == normalized) {
        return;
    }
    m_uiScalePercent = normalized;
    emit settingsChanged();
}

void FrontendSettings::setBackendBaseUrl(const QString &value) {
    QString normalized = value.trimmed();
    // 去掉末尾斜杠，后续拼接 /api/chat/send 时不会变成双斜杠。
    // Remove trailing slashes so appending /api/chat/send does not create double slashes.
    while (normalized.endsWith('/')) {
        normalized.chop(1);
    }
    if (normalized.isEmpty()) {
        normalized = QStringLiteral("http://localhost:8080");
    }
    if (m_backendBaseUrl == normalized) {
        return;
    }
    m_backendBaseUrl = normalized;
    emit settingsChanged();
}

void FrontendSettings::setAiBaseUrl(const QString &value) {
    QString normalized = value.trimmed();
    while (normalized.endsWith('/')) {
        normalized.chop(1);
    }
    if (m_aiBaseUrl == normalized) {
        return;
    }
    m_aiBaseUrl = normalized;
    emit settingsChanged();
}

void FrontendSettings::setAiApiKey(const QString &value) {
    const QString normalized = value.trimmed();
    if (m_aiApiKey == normalized) {
        return;
    }
    m_aiApiKey = normalized;
    emit settingsChanged();
}

void FrontendSettings::setAiModelName(const QString &value) {
    const QString normalized = value.trimmed();
    if (m_aiModelName == normalized) {
        return;
    }
    m_aiModelName = normalized;
    emit settingsChanged();
}

void FrontendSettings::setEmbeddingBaseUrl(const QString &value) {
    QString normalized = value.trimmed();
    while (normalized.endsWith('/')) {
        normalized.chop(1);
    }
    if (m_embeddingBaseUrl == normalized) {
        return;
    }
    m_embeddingBaseUrl = normalized;
    emit settingsChanged();
}

void FrontendSettings::setEmbeddingApiKey(const QString &value) {
    const QString normalized = value.trimmed();
    if (m_embeddingApiKey == normalized) {
        return;
    }
    m_embeddingApiKey = normalized;
    emit settingsChanged();
}

void FrontendSettings::setEmbeddingModelName(const QString &value) {
    const QString normalized = value.trimmed();
    if (m_embeddingModelName == normalized) {
        return;
    }
    m_embeddingModelName = normalized;
    emit settingsChanged();
}

void FrontendSettings::load() {
    // 通过 setter 读取配置，这样读取到的旧值也会走同一套默认值和范围校验逻辑。
    // Load through setters so old saved values still pass through defaulting and validation logic.
    QSettings settings;
    setThemeId(settings.value(QStringLiteral("themeId"), m_themeId).toString());
    setCharacterName(settings.value(QStringLiteral("characterName"), m_characterName).toString());
    setSessionId(settings.value(QStringLiteral("sessionId"), m_sessionId).toString());
    setWorkspaceName(settings.value(QStringLiteral("workspaceName"), m_workspaceName).toString());
    setOperatorName(settings.value(QStringLiteral("operatorName"), m_operatorName).toString());
    setBootAnimationEnabled(settings.value(QStringLiteral("bootAnimationEnabled"), m_bootAnimationEnabled).toBool());
    setBootDurationMs(settings.value(QStringLiteral("bootDurationMs"), m_bootDurationMs).toInt());
    setResponseDelayMs(settings.value(QStringLiteral("responseDelayMs"), m_responseDelayMs).toInt());
    setMoteCount(settings.value(QStringLiteral("moteCount"), m_moteCount).toInt());
    setUiScalePercent(settings.value(QStringLiteral("uiScalePercent"), m_uiScalePercent).toInt());
    setBackendBaseUrl(settings.value(QStringLiteral("backendBaseUrl"), m_backendBaseUrl).toString());
    setAiBaseUrl(settings.value(QStringLiteral("aiBaseUrl"), m_aiBaseUrl).toString());
    setAiApiKey(settings.value(QStringLiteral("aiApiKey"), m_aiApiKey).toString());
    setAiModelName(settings.value(QStringLiteral("aiModelName"), m_aiModelName).toString());
    setEmbeddingBaseUrl(settings.value(QStringLiteral("embeddingBaseUrl"), m_embeddingBaseUrl).toString());
    setEmbeddingApiKey(settings.value(QStringLiteral("embeddingApiKey"), m_embeddingApiKey).toString());
    setEmbeddingModelName(settings.value(QStringLiteral("embeddingModelName"), m_embeddingModelName).toString());
}
