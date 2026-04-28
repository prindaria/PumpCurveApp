# PumpCurveApp - 压力-抽速曲线分析工具 (Android)

## 功能特性

- **内置数据库**：预装 DATABASE.csv，包含 200+ 真空泵型号的抽速数据
- **曲线绘制**：基于 MPAndroidChart 绘制压力-抽速曲线
- **多型号对比**：支持同时选择多条曲线叠加显示
- **坐标轴控制**：
  - X轴（压力）：线性 / 对数 切换
  - Y轴（抽速）：线性 / 对数 切换
- **单位选择**：Torr/Pa/mbar、L/min/m³/h/CFM
- **自定义范围**：可设置 X 轴最小/最大值
- **独立图表**：支持全屏查看图表

## 数据格式

CSV 文件格式：`泵型号, 压力(Torr), 抽速(L/min)`

## 构建 APK

### 方式一：GitHub Actions 云端构建（推荐）

1. Fork 本仓库到你的 GitHub 账号
2. 进入 Actions 页面 → 选择 "Build PumpCurveApp APK" → Run workflow
3. 构建完成后在 Artifacts 中下载 `PumpCurveApp-debug.apk`
4. 安装到 Android 手机即可使用

### 方式二：本地构建

需要安装：
- Android SDK (API 34)
- JDK 17+
- Gradle 8.5+

```bash
./gradlew assembleDebug
```

APK 输出路径: `app/build/outputs/apk/debug/app-debug.apk`

## 技术栈

- Kotlin + AndroidX
- Material Design 3
- MPAndroidChart v3.1.0 (图表库)
- Apache Commons CSV (CSV解析)

## UI 布局

参考桌面版软件设计，采用左右分栏布局：
- 左侧：文件管理、曲线选择列表、坐标轴设置
- 右侧：实时曲线图表
