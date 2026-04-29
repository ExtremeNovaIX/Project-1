#include "ChatMessageModel.h"

ChatMessageModel::ChatMessageModel(QObject *parent)
    : QAbstractListModel(parent) {
}

int ChatMessageModel::rowCount(const QModelIndex &parent) const {
    // 这是一个一维列表模型；如果 parent 有效，说明 Qt 在询问子节点数量，直接返回 0。
    // This is a flat list model; a valid parent means Qt asks for child count, so return 0.
    if (parent.isValid()) {
        return 0;
    }
    return m_messages.size();
}

QVariant ChatMessageModel::data(const QModelIndex &index, int role) const {
    // 防御性检查：无效行不能访问 QVector，避免越界。
    // Defensive check: invalid rows must not access QVector, avoiding out-of-range reads.
    if (!index.isValid() || index.row() < 0 || index.row() >= m_messages.size()) {
        return QVariant();
    }

    // 根据 QML 请求的 role 返回对应字段。
    // Return the requested field based on the role asked for by QML.
    const MessageItem &message = m_messages.at(index.row());
    switch (role) {
    case IdRole:
        return message.id;
    case RoleRole:
        return message.role;
    case ContentRole:
        return message.content;
    case EmotionRole:
        return message.emotion;
    default:
        return QVariant();
    }
}

QHash<int, QByteArray> ChatMessageModel::roleNames() const {
    // role 名称会成为 QML delegate 里的变量名，例如 messageContent。
    // Role names become variable names inside the QML delegate, such as messageContent.
    return {
        {IdRole, "messageId"},
        {RoleRole, "messageRole"},
        {ContentRole, "messageContent"},
        {EmotionRole, "messageEmotion"}
    };
}

void ChatMessageModel::clear() {
    // 重置模型时必须用 beginResetModel/endResetModel 包住数据变化。
    // Model resets must wrap data changes with beginResetModel/endResetModel.
    beginResetModel();
    m_messages.clear();
    endResetModel();
}

void ChatMessageModel::appendMessage(const MessageItem &message) {
    // 插入行时也要先通知视图，再改数据，最后结束通知。
    // When inserting rows, notify views first, change data, then finish the notification.
    const int insertRow = m_messages.size();
    beginInsertRows(QModelIndex(), insertRow, insertRow);
    m_messages.push_back(message);
    endInsertRows();
}
