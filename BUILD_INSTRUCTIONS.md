# cPrint APK 构建指南

## 方法一：使用 Android Studio（推荐）

1. **打开项目**
   - 启动 Android Studio Hedgehog (2023.1.1) 或更新版本
   - 选择 "Open" → 选择 `/Users/mnhkahn/code/cPrint` 文件夹

2. **同步项目**
   - 打开项目后，Android Studio 会自动开始同步 Gradle
   - 等待同步完成（可能需要下载依赖，首次耗时较长）

3. **修复可能的同步问题**
   - 如果出现 Compose 版本警告，点击 "Update" 更新 Compose 编译器版本
   - 如果出现 SDK 警告，点击 "Install" 安装所需 SDK 组件

4. **构建 APK**
   - 菜单栏选择 `Build` → `Build Bundle(s) / APK(s)` → `Build APK(s)`
   - 或者使用快捷键：`Cmd+F9` (Mac) / `Ctrl+F9` (Windows/Linux)

5. **获取 APK 文件**
   - 构建成功后，在右下角会显示 "Build Analyzer" 弹窗
   - 点击 `locate` 链接即可找到 APK 文件
   - 默认路径：`app/build/outputs/apk/debug/app-debug.apk`

## 方法二：命令行构建（需确保环境正确）

```bash
cd /Users/mnhkahn/code/cPrint

# 确保 JAVA_HOME 设置为 Java 17
export JAVA_HOME=$(/usr/libexec/java_home -v 17)

# 构建 Debug APK
./gradlew assembleDebug

# 构建 Release APK
./gradlew assembleRelease
```

## 已知问题及解决方案

### 问题1：Compose 组件找不到
**原因**：`SingleChoiceSegmentedButtonRow` 和 `SegmentedButton` 需要 Material3 1.2.0+
**解决**：已在 build.gradle.kts 中更新 BOM 版本到 2024.02.00

### 问题2：AutoMirrored 图标找不到
**原因**：较旧版本的 Compose Material 不包含 automirrored 图标
**解决**：使用常规图标替代，或更新依赖版本

### 问题3：Room 编译错误
**原因**：Room 版本与 Kotlin 版本不兼容
**解决**：已更新 Room 到 2.6.1 版本

## APK 输出位置

- **Debug APK**: `app/build/outputs/apk/debug/app-debug.apk`
- **Release APK**: `app/build/outputs/apk/release/app-release.apk`

## 安装 APK

```bash
# 通过 adb 安装到连接的设备
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 系统要求

- Android Studio Hedgehog (2023.1.1) 或更新版本
- JDK 17
- Android SDK 34
- NDK（如果需要构建 C++ 部分）

## 项目结构

```
cPrint/
├── app/src/main/java/com/cprint/app/    # Kotlin 源代码
├── app/src/main/res/                    # 资源文件
├── app/build.gradle.kts                 # 应用模块构建配置
├── build.gradle.kts                     # 根项目构建配置
└── gradle.properties                    # Gradle 属性
```
