/****************************************************************************
** Meta object code from reading C++ file 'FrontendSettings.h'
**
** Created by: The Qt Meta Object Compiler version 69 (Qt 6.11.0)
**
** WARNING! All changes made in this file will be lost!
*****************************************************************************/

#include "../../../../src/app/FrontendSettings.h"
#include <QtCore/qmetatype.h>

#include <QtCore/qtmochelpers.h>

#include <memory>


#include <QtCore/qxptype_traits.h>
#if !defined(Q_MOC_OUTPUT_REVISION)
#error "The header file 'FrontendSettings.h' doesn't include <QObject>."
#elif Q_MOC_OUTPUT_REVISION != 69
#error "This file was generated using the moc from 6.11.0. It"
#error "cannot be used with the include files from this version of Qt."
#error "(The moc has changed too much.)"
#endif

#ifndef Q_CONSTINIT
#define Q_CONSTINIT
#endif

QT_WARNING_PUSH
QT_WARNING_DISABLE_DEPRECATED
QT_WARNING_DISABLE_GCC("-Wuseless-cast")
namespace {
struct qt_meta_tag_ZN16FrontendSettingsE_t {};
} // unnamed namespace

template <> constexpr inline auto FrontendSettings::qt_create_metaobjectdata<qt_meta_tag_ZN16FrontendSettingsE_t>()
{
    namespace QMC = QtMocConstants;
    QtMocHelpers::StringRefStorage qt_stringData {
        "FrontendSettings",
        "settingsChanged",
        "",
        "setLanguageId",
        "value",
        "setThemeId",
        "setCharacterName",
        "setSessionId",
        "setWorkspaceName",
        "setOperatorName",
        "setBootAnimationEnabled",
        "setBootDurationMs",
        "setResponseDelayMs",
        "setMoteCount",
        "setUiScalePercent",
        "setBackendBaseUrl",
        "setAiBaseUrl",
        "setAiApiKey",
        "setAiModelName",
        "setEmbeddingBaseUrl",
        "setEmbeddingApiKey",
        "setEmbeddingModelName",
        "reset",
        "save",
        "languageId",
        "themeId",
        "characterName",
        "sessionId",
        "workspaceName",
        "operatorName",
        "bootAnimationEnabled",
        "bootDurationMs",
        "responseDelayMs",
        "moteCount",
        "uiScalePercent",
        "backendBaseUrl",
        "aiBaseUrl",
        "aiApiKey",
        "aiModelName",
        "embeddingBaseUrl",
        "embeddingApiKey",
        "embeddingModelName"
    };

    QtMocHelpers::UintData qt_methods {
        // Signal 'settingsChanged'
        QtMocHelpers::SignalData<void()>(1, 2, QMC::AccessPublic, QMetaType::Void),
        // Slot 'setLanguageId'
        QtMocHelpers::SlotData<void(const QString &)>(3, 2, QMC::AccessPublic, QMetaType::Void, {{
            { QMetaType::QString, 4 },
        }}),
        // Slot 'setThemeId'
        QtMocHelpers::SlotData<void(const QString &)>(5, 2, QMC::AccessPublic, QMetaType::Void, {{
            { QMetaType::QString, 4 },
        }}),
        // Slot 'setCharacterName'
        QtMocHelpers::SlotData<void(const QString &)>(6, 2, QMC::AccessPublic, QMetaType::Void, {{
            { QMetaType::QString, 4 },
        }}),
        // Slot 'setSessionId'
        QtMocHelpers::SlotData<void(const QString &)>(7, 2, QMC::AccessPublic, QMetaType::Void, {{
            { QMetaType::QString, 4 },
        }}),
        // Slot 'setWorkspaceName'
        QtMocHelpers::SlotData<void(const QString &)>(8, 2, QMC::AccessPublic, QMetaType::Void, {{
            { QMetaType::QString, 4 },
        }}),
        // Slot 'setOperatorName'
        QtMocHelpers::SlotData<void(const QString &)>(9, 2, QMC::AccessPublic, QMetaType::Void, {{
            { QMetaType::QString, 4 },
        }}),
        // Slot 'setBootAnimationEnabled'
        QtMocHelpers::SlotData<void(bool)>(10, 2, QMC::AccessPublic, QMetaType::Void, {{
            { QMetaType::Bool, 4 },
        }}),
        // Slot 'setBootDurationMs'
        QtMocHelpers::SlotData<void(int)>(11, 2, QMC::AccessPublic, QMetaType::Void, {{
            { QMetaType::Int, 4 },
        }}),
        // Slot 'setResponseDelayMs'
        QtMocHelpers::SlotData<void(int)>(12, 2, QMC::AccessPublic, QMetaType::Void, {{
            { QMetaType::Int, 4 },
        }}),
        // Slot 'setMoteCount'
        QtMocHelpers::SlotData<void(int)>(13, 2, QMC::AccessPublic, QMetaType::Void, {{
            { QMetaType::Int, 4 },
        }}),
        // Slot 'setUiScalePercent'
        QtMocHelpers::SlotData<void(int)>(14, 2, QMC::AccessPublic, QMetaType::Void, {{
            { QMetaType::Int, 4 },
        }}),
        // Slot 'setBackendBaseUrl'
        QtMocHelpers::SlotData<void(const QString &)>(15, 2, QMC::AccessPublic, QMetaType::Void, {{
            { QMetaType::QString, 4 },
        }}),
        // Slot 'setAiBaseUrl'
        QtMocHelpers::SlotData<void(const QString &)>(16, 2, QMC::AccessPublic, QMetaType::Void, {{
            { QMetaType::QString, 4 },
        }}),
        // Slot 'setAiApiKey'
        QtMocHelpers::SlotData<void(const QString &)>(17, 2, QMC::AccessPublic, QMetaType::Void, {{
            { QMetaType::QString, 4 },
        }}),
        // Slot 'setAiModelName'
        QtMocHelpers::SlotData<void(const QString &)>(18, 2, QMC::AccessPublic, QMetaType::Void, {{
            { QMetaType::QString, 4 },
        }}),
        // Slot 'setEmbeddingBaseUrl'
        QtMocHelpers::SlotData<void(const QString &)>(19, 2, QMC::AccessPublic, QMetaType::Void, {{
            { QMetaType::QString, 4 },
        }}),
        // Slot 'setEmbeddingApiKey'
        QtMocHelpers::SlotData<void(const QString &)>(20, 2, QMC::AccessPublic, QMetaType::Void, {{
            { QMetaType::QString, 4 },
        }}),
        // Slot 'setEmbeddingModelName'
        QtMocHelpers::SlotData<void(const QString &)>(21, 2, QMC::AccessPublic, QMetaType::Void, {{
            { QMetaType::QString, 4 },
        }}),
        // Method 'reset'
        QtMocHelpers::MethodData<void()>(22, 2, QMC::AccessPublic, QMetaType::Void),
        // Method 'save'
        QtMocHelpers::MethodData<void()>(23, 2, QMC::AccessPublic, QMetaType::Void),
    };
    QtMocHelpers::UintData qt_properties {
        // property 'languageId'
        QtMocHelpers::PropertyData<QString>(24, QMetaType::QString, QMC::DefaultPropertyFlags | QMC::Writable | QMC::StdCppSet, 0),
        // property 'themeId'
        QtMocHelpers::PropertyData<QString>(25, QMetaType::QString, QMC::DefaultPropertyFlags | QMC::Writable | QMC::StdCppSet, 0),
        // property 'characterName'
        QtMocHelpers::PropertyData<QString>(26, QMetaType::QString, QMC::DefaultPropertyFlags | QMC::Writable | QMC::StdCppSet, 0),
        // property 'sessionId'
        QtMocHelpers::PropertyData<QString>(27, QMetaType::QString, QMC::DefaultPropertyFlags | QMC::Writable | QMC::StdCppSet, 0),
        // property 'workspaceName'
        QtMocHelpers::PropertyData<QString>(28, QMetaType::QString, QMC::DefaultPropertyFlags | QMC::Writable | QMC::StdCppSet, 0),
        // property 'operatorName'
        QtMocHelpers::PropertyData<QString>(29, QMetaType::QString, QMC::DefaultPropertyFlags | QMC::Writable | QMC::StdCppSet, 0),
        // property 'bootAnimationEnabled'
        QtMocHelpers::PropertyData<bool>(30, QMetaType::Bool, QMC::DefaultPropertyFlags | QMC::Writable | QMC::StdCppSet, 0),
        // property 'bootDurationMs'
        QtMocHelpers::PropertyData<int>(31, QMetaType::Int, QMC::DefaultPropertyFlags | QMC::Writable | QMC::StdCppSet, 0),
        // property 'responseDelayMs'
        QtMocHelpers::PropertyData<int>(32, QMetaType::Int, QMC::DefaultPropertyFlags | QMC::Writable | QMC::StdCppSet, 0),
        // property 'moteCount'
        QtMocHelpers::PropertyData<int>(33, QMetaType::Int, QMC::DefaultPropertyFlags | QMC::Writable | QMC::StdCppSet, 0),
        // property 'uiScalePercent'
        QtMocHelpers::PropertyData<int>(34, QMetaType::Int, QMC::DefaultPropertyFlags | QMC::Writable | QMC::StdCppSet, 0),
        // property 'backendBaseUrl'
        QtMocHelpers::PropertyData<QString>(35, QMetaType::QString, QMC::DefaultPropertyFlags | QMC::Writable | QMC::StdCppSet, 0),
        // property 'aiBaseUrl'
        QtMocHelpers::PropertyData<QString>(36, QMetaType::QString, QMC::DefaultPropertyFlags | QMC::Writable | QMC::StdCppSet, 0),
        // property 'aiApiKey'
        QtMocHelpers::PropertyData<QString>(37, QMetaType::QString, QMC::DefaultPropertyFlags | QMC::Writable | QMC::StdCppSet, 0),
        // property 'aiModelName'
        QtMocHelpers::PropertyData<QString>(38, QMetaType::QString, QMC::DefaultPropertyFlags | QMC::Writable | QMC::StdCppSet, 0),
        // property 'embeddingBaseUrl'
        QtMocHelpers::PropertyData<QString>(39, QMetaType::QString, QMC::DefaultPropertyFlags | QMC::Writable | QMC::StdCppSet, 0),
        // property 'embeddingApiKey'
        QtMocHelpers::PropertyData<QString>(40, QMetaType::QString, QMC::DefaultPropertyFlags | QMC::Writable | QMC::StdCppSet, 0),
        // property 'embeddingModelName'
        QtMocHelpers::PropertyData<QString>(41, QMetaType::QString, QMC::DefaultPropertyFlags | QMC::Writable | QMC::StdCppSet, 0),
    };
    QtMocHelpers::UintData qt_enums {
    };
    return QtMocHelpers::metaObjectData<FrontendSettings, qt_meta_tag_ZN16FrontendSettingsE_t>(QMC::MetaObjectFlag{}, qt_stringData,
            qt_methods, qt_properties, qt_enums);
}
Q_CONSTINIT const QMetaObject FrontendSettings::staticMetaObject = { {
    QMetaObject::SuperData::link<QObject::staticMetaObject>(),
    qt_staticMetaObjectStaticContent<qt_meta_tag_ZN16FrontendSettingsE_t>.stringdata,
    qt_staticMetaObjectStaticContent<qt_meta_tag_ZN16FrontendSettingsE_t>.data,
    qt_static_metacall,
    nullptr,
    qt_staticMetaObjectRelocatingContent<qt_meta_tag_ZN16FrontendSettingsE_t>.metaTypes,
    nullptr
} };

void FrontendSettings::qt_static_metacall(QObject *_o, QMetaObject::Call _c, int _id, void **_a)
{
    auto *_t = static_cast<FrontendSettings *>(_o);
    if (_c == QMetaObject::InvokeMetaMethod) {
        switch (_id) {
        case 0: _t->settingsChanged(); break;
        case 1: _t->setLanguageId((*reinterpret_cast<std::add_pointer_t<QString>>(_a[1]))); break;
        case 2: _t->setThemeId((*reinterpret_cast<std::add_pointer_t<QString>>(_a[1]))); break;
        case 3: _t->setCharacterName((*reinterpret_cast<std::add_pointer_t<QString>>(_a[1]))); break;
        case 4: _t->setSessionId((*reinterpret_cast<std::add_pointer_t<QString>>(_a[1]))); break;
        case 5: _t->setWorkspaceName((*reinterpret_cast<std::add_pointer_t<QString>>(_a[1]))); break;
        case 6: _t->setOperatorName((*reinterpret_cast<std::add_pointer_t<QString>>(_a[1]))); break;
        case 7: _t->setBootAnimationEnabled((*reinterpret_cast<std::add_pointer_t<bool>>(_a[1]))); break;
        case 8: _t->setBootDurationMs((*reinterpret_cast<std::add_pointer_t<int>>(_a[1]))); break;
        case 9: _t->setResponseDelayMs((*reinterpret_cast<std::add_pointer_t<int>>(_a[1]))); break;
        case 10: _t->setMoteCount((*reinterpret_cast<std::add_pointer_t<int>>(_a[1]))); break;
        case 11: _t->setUiScalePercent((*reinterpret_cast<std::add_pointer_t<int>>(_a[1]))); break;
        case 12: _t->setBackendBaseUrl((*reinterpret_cast<std::add_pointer_t<QString>>(_a[1]))); break;
        case 13: _t->setAiBaseUrl((*reinterpret_cast<std::add_pointer_t<QString>>(_a[1]))); break;
        case 14: _t->setAiApiKey((*reinterpret_cast<std::add_pointer_t<QString>>(_a[1]))); break;
        case 15: _t->setAiModelName((*reinterpret_cast<std::add_pointer_t<QString>>(_a[1]))); break;
        case 16: _t->setEmbeddingBaseUrl((*reinterpret_cast<std::add_pointer_t<QString>>(_a[1]))); break;
        case 17: _t->setEmbeddingApiKey((*reinterpret_cast<std::add_pointer_t<QString>>(_a[1]))); break;
        case 18: _t->setEmbeddingModelName((*reinterpret_cast<std::add_pointer_t<QString>>(_a[1]))); break;
        case 19: _t->reset(); break;
        case 20: _t->save(); break;
        default: ;
        }
    }
    if (_c == QMetaObject::IndexOfMethod) {
        if (QtMocHelpers::indexOfMethod<void (FrontendSettings::*)()>(_a, &FrontendSettings::settingsChanged, 0))
            return;
    }
    if (_c == QMetaObject::ReadProperty) {
        void *_v = _a[0];
        switch (_id) {
        case 0: *reinterpret_cast<QString*>(_v) = _t->languageId(); break;
        case 1: *reinterpret_cast<QString*>(_v) = _t->themeId(); break;
        case 2: *reinterpret_cast<QString*>(_v) = _t->characterName(); break;
        case 3: *reinterpret_cast<QString*>(_v) = _t->sessionId(); break;
        case 4: *reinterpret_cast<QString*>(_v) = _t->workspaceName(); break;
        case 5: *reinterpret_cast<QString*>(_v) = _t->operatorName(); break;
        case 6: *reinterpret_cast<bool*>(_v) = _t->bootAnimationEnabled(); break;
        case 7: *reinterpret_cast<int*>(_v) = _t->bootDurationMs(); break;
        case 8: *reinterpret_cast<int*>(_v) = _t->responseDelayMs(); break;
        case 9: *reinterpret_cast<int*>(_v) = _t->moteCount(); break;
        case 10: *reinterpret_cast<int*>(_v) = _t->uiScalePercent(); break;
        case 11: *reinterpret_cast<QString*>(_v) = _t->backendBaseUrl(); break;
        case 12: *reinterpret_cast<QString*>(_v) = _t->aiBaseUrl(); break;
        case 13: *reinterpret_cast<QString*>(_v) = _t->aiApiKey(); break;
        case 14: *reinterpret_cast<QString*>(_v) = _t->aiModelName(); break;
        case 15: *reinterpret_cast<QString*>(_v) = _t->embeddingBaseUrl(); break;
        case 16: *reinterpret_cast<QString*>(_v) = _t->embeddingApiKey(); break;
        case 17: *reinterpret_cast<QString*>(_v) = _t->embeddingModelName(); break;
        default: break;
        }
    }
    if (_c == QMetaObject::WriteProperty) {
        void *_v = _a[0];
        switch (_id) {
        case 0: _t->setLanguageId(*reinterpret_cast<QString*>(_v)); break;
        case 1: _t->setThemeId(*reinterpret_cast<QString*>(_v)); break;
        case 2: _t->setCharacterName(*reinterpret_cast<QString*>(_v)); break;
        case 3: _t->setSessionId(*reinterpret_cast<QString*>(_v)); break;
        case 4: _t->setWorkspaceName(*reinterpret_cast<QString*>(_v)); break;
        case 5: _t->setOperatorName(*reinterpret_cast<QString*>(_v)); break;
        case 6: _t->setBootAnimationEnabled(*reinterpret_cast<bool*>(_v)); break;
        case 7: _t->setBootDurationMs(*reinterpret_cast<int*>(_v)); break;
        case 8: _t->setResponseDelayMs(*reinterpret_cast<int*>(_v)); break;
        case 9: _t->setMoteCount(*reinterpret_cast<int*>(_v)); break;
        case 10: _t->setUiScalePercent(*reinterpret_cast<int*>(_v)); break;
        case 11: _t->setBackendBaseUrl(*reinterpret_cast<QString*>(_v)); break;
        case 12: _t->setAiBaseUrl(*reinterpret_cast<QString*>(_v)); break;
        case 13: _t->setAiApiKey(*reinterpret_cast<QString*>(_v)); break;
        case 14: _t->setAiModelName(*reinterpret_cast<QString*>(_v)); break;
        case 15: _t->setEmbeddingBaseUrl(*reinterpret_cast<QString*>(_v)); break;
        case 16: _t->setEmbeddingApiKey(*reinterpret_cast<QString*>(_v)); break;
        case 17: _t->setEmbeddingModelName(*reinterpret_cast<QString*>(_v)); break;
        default: break;
        }
    }
}

const QMetaObject *FrontendSettings::metaObject() const
{
    return QObject::d_ptr->metaObject ? QObject::d_ptr->dynamicMetaObject() : &staticMetaObject;
}

void *FrontendSettings::qt_metacast(const char *_clname)
{
    if (!_clname) return nullptr;
    if (!strcmp(_clname, qt_staticMetaObjectStaticContent<qt_meta_tag_ZN16FrontendSettingsE_t>.strings))
        return static_cast<void*>(this);
    return QObject::qt_metacast(_clname);
}

int FrontendSettings::qt_metacall(QMetaObject::Call _c, int _id, void **_a)
{
    _id = QObject::qt_metacall(_c, _id, _a);
    if (_id < 0)
        return _id;
    if (_c == QMetaObject::InvokeMetaMethod) {
        if (_id < 21)
            qt_static_metacall(this, _c, _id, _a);
        _id -= 21;
    }
    if (_c == QMetaObject::RegisterMethodArgumentMetaType) {
        if (_id < 21)
            *reinterpret_cast<QMetaType *>(_a[0]) = QMetaType();
        _id -= 21;
    }
    if (_c == QMetaObject::ReadProperty || _c == QMetaObject::WriteProperty
            || _c == QMetaObject::ResetProperty || _c == QMetaObject::BindableProperty
            || _c == QMetaObject::RegisterPropertyMetaType) {
        qt_static_metacall(this, _c, _id, _a);
        _id -= 18;
    }
    return _id;
}

// SIGNAL 0
void FrontendSettings::settingsChanged()
{
    QMetaObject::activate(this, &staticMetaObject, 0, nullptr);
}
QT_WARNING_POP
