#pragma once

#include <QAbstractListModel>
#include <QByteArray>
#include <QString>
#include <QVariant>
#include <QVector>

// 单条聊天消息的数据结构；ListView delegate 会通过 role 读取这些字段。
// Data for one chat message; the ListView delegate reads these fields through model roles.
struct MessageItem {
    qint64 id = 0;
    QString role;
    QString content;
    QString emotion;
};

// QML ListView 使用的列表模型。
// List model used by QML ListView.
//
// QAbstractListModel 是 Qt 的标准 Model/View 基类：C++ 存数据，QML 负责显示。
// QAbstractListModel is Qt's standard Model/View base: C++ stores data, QML renders it.
class ChatMessageModel final : public QAbstractListModel {
    Q_OBJECT

public:
    // 自定义 role 从 Qt::UserRole + 1 开始，避免和 Qt 内置 role 冲突。
    // Custom roles start at Qt::UserRole + 1 to avoid conflicts with built-in Qt roles.
    enum Roles {
        IdRole = Qt::UserRole + 1,
        RoleRole,
        ContentRole,
        EmotionRole
    };
    Q_ENUM(Roles)

    explicit ChatMessageModel(QObject *parent = nullptr);

    // QAbstractListModel 必须实现的三个核心函数。
    // The three core functions required by QAbstractListModel.
    int rowCount(const QModelIndex &parent = QModelIndex()) const override;
    QVariant data(const QModelIndex &index, int role = Qt::DisplayRole) const override;
    QHash<int, QByteArray> roleNames() const override;

    // Q_INVOKABLE 让 QML 可以直接调用 clear()。
    // Q_INVOKABLE makes clear() callable directly from QML.
    Q_INVOKABLE void clear();

    // C++ 控制器追加消息时调用；内部会通知视图插入了新行。
    // Called by the C++ controller to append a message; it notifies views about the new row.
    void appendMessage(const MessageItem &message);

private:
    // QVector 是实际存储；QML 不直接访问它，而是通过 model role 访问。
    // QVector is the actual storage; QML accesses it through model roles, not directly.
    QVector<MessageItem> m_messages;
};
