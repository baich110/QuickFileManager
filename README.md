# QuickFileManager - 快速文件管理器

一个基于 Jetpack Compose + Kotlin 开发的 Android 文件管理器，支持无障碍访问。

## 技术栈

- **UI框架**: Jetpack Compose (Material 3)
- **架构模式**: MVVM + Clean Architecture
- **依赖注入**: Hilt (Dagger)
- **协程**: Kotlin Coroutines + Flow
- **最小SDK**: 26 (Android 8.0)
- **目标SDK**: 34 (Android 14)

## 功能特性

### 核心功能
- 📁 文件/文件夹浏览
- 📋 复制、粘贴、剪切、删除
- 🔍 文件搜索
- 📊 存储空间统计
- ➕ 新建文件夹
- ✏️ 文件重命名

### 无障碍支持
- ♿ 所有UI元素添加语义描述
- 🎯 焦点顺序控制
- 📱 屏幕阅读器适配
- 🖐️ 长按选择模式
- ✅ 操作反馈

## 项目结构

```
QuickFileManager/
├── app/
│   ├── src/main/
│   │   ├── java/com/quickfilemanager/
│   │   │   ├── data/repository/       # 数据层
│   │   │   ├── di/                    # 依赖注入
│   │   │   ├── domain/model/          # 数据模型
│   │   │   ├── ui/components/         # UI组件
│   │   │   ├── ui/screens/            # 屏幕
│   │   │   ├── ui/theme/              # 主题
│   │   │   ├── viewmodel/             # ViewModel
│   │   │   ├── MainActivity.kt
│   │   │   └── QuickFileManagerApp.kt
│   │   ├── res/                       # 资源文件
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── gradlew
```

## 构建

```bash
# 下载 Gradle Wrapper
gradle wrapper

# 构建 Debug APK
./gradlew assembleDebug

# 构建 Release APK
./gradlew assembleRelease
```

## 权限说明

- `READ_EXTERNAL_STORAGE`: 读取外部存储
- `WRITE_EXTERNAL_STORAGE`: 写入外部存储
- `MANAGE_EXTERNAL_STORAGE`: Android 11+ 全文件访问

## License

MIT License
