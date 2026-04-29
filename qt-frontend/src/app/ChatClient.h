#pragma once

#include <QObject>
#include <QStringList>

class QNetworkAccessManager;
class QNetworkReply;

// 聊天接口客户端：只负责把消息发给后端，并把后端回复整理成字符串段落。
// Chat API client: only sends messages to the backend and converts replies into
// text segments.
class ChatClient final : public QObject {
    Q_OBJECT

  public:
    explicit ChatClient(QObject *parent = nullptr);

    // 获取 QNetworkAccessManager 实例用于自定义请求
    // Get the QNetworkAccessManager instance for custom requests
    QNetworkAccessManager* networkManager() const;

    // 发起一次异步 POST 请求；Qt 网络请求不会阻塞 UI 线程。
    // Start one asynchronous POST request; Qt networking does not block the UI
    // thread.
    void sendMessage(const QString &baseUrl, const QString &message,
                     const QString &sessionId, const QString &characterName,
                     bool shortMode, const QString &aiBaseUrl,
                     const QString &aiApiKey, const QString &aiModelName,
                     const QString &embeddingBaseUrl, const QString &embeddingApiKey,
                     const QString &embeddingModelName);

    void startStoryReplay(const QString &baseUrl, const QString &sessionId,
                          const QString &characterName, int targetLength,
                          const QString &aiBaseUrl, const QString &aiApiKey,
                          const QString &aiModelName, const QString &embeddingBaseUrl,
                          const QString &embeddingApiKey, const QString &embeddingModelName);

  signals:
    // 后端正常返回并解析出至少一段可显示文本时触发。
    // Emitted when the backend returns successfully and at least one
    // displayable segment is parsed.
    void replyReady(const QStringList &segments);

    // 网络错误、HTTP/Qt 错误或空回复时触发。
    // Emitted for network errors, Qt reply errors, or empty replies.
    void requestFailed(const QString &message);

    void storyReplayReady(const QString &summary);
    void storyReplayFailed(const QString &message);

  private:
    // 统一处理 QNetworkReply：读取响应体、解析 JSON、发出成功或失败信号。
    // Central QNetworkReply handler: reads body, parses JSON, and emits success
    // or failure signals.
    void handleReply(QNetworkReply *reply);
    void handleStoryReplayReply(QNetworkReply *reply);

    // QNetworkAccessManager 应复用；以 this 为 parent，Qt 会自动释放。
    // Reuse QNetworkAccessManager; with this as parent, Qt deletes it
    // automatically.
    QNetworkAccessManager *m_network;
};
