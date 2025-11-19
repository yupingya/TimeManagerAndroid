# Time Manager Android

**专注、精确的分段计时工具**

## 🌐 软件介绍 (Software Introduction)

Time Manager 是一款专为精确时间管理和活动分段记录设计的 Android 应用。它不仅提供基础的计时功能，更通过精密的架构设计，确保计时过程的**稳定性和数据的完整性**，尤其适用于需要长时间、不间断记录工作、学习或训练时长的用户。

本项目关注**核心计时逻辑的稳定、数据模型的纯净以及轻量级的数据导出能力**，是 Android 应用开发中架构设计与工程实践的典范。

### 核心功能

- **高精度计时**：支持小时、分、秒、毫秒（两位厘秒）显示。
- **持久化运行**：通过后台服务（Foreground Service）机制，确保应用在后台或设备休眠时，计时器依然稳定、准确地运行，解决了 Android 系统 Activity 生命周期带来的计时中断问题。
- **分段记录与标记**：支持“分段”（Lap）功能，记录当前间隔时间和累计时间，并能为每段记录添加**分类（Spinner 选择）和详细事件描述**。
- **数据持久化**：使用 `SharedPreferences` + `Gson` 机制，在应用重启、Activity 重建或意外退出时，自动保存和恢复计时状态及所有分段记录。
- **轻量级数据导出**：支持一键将所有分段记录导出为 **Excel 文件（.xls 格式）**，便于用户进行后续的数据分析。
- **自定义主题**：支持一键切换日/夜模式，提供舒适的视觉体验。

## 🏛️ 软件架构深度剖析 (Software Architecture)

本项目的架构思想是 **“稳定为王，高内聚低耦合”**，其精华在于对 Android 生命周期和数据流的精妙控制。

### 1. 核心稳定性层 (The Daemon Layer)

- **关键组件：** `DaemonService.java`, `DaemonManager.java`
- **设计思想：** 解决了 Android 计时应用中最大的痛点——后台被杀。
  - `DaemonManager` 负责调用 `DaemonService`，并在 `MainActivity` 启动时启动，销毁时停止。
  - `DaemonService` 继承自 `Service`，并运行为 **Foreground Service（前台服务）**，通过创建低优先级的通知（Notification），极大地提高了应用在后台时的系统存活率和优先级，确保计时任务不被系统回收。
  - 计时逻辑核心依赖于 `Handler` 和 `Runnable` 在主线程驱动 UI 更新，服务确保了主线程的存活。

### 2. 数据与模型层 (Model Layer - High Cohesion)

- **关键组件：** `LapRecord.java`
- **设计思想：** 贯彻**高内聚原则**，将数据和操作数据的逻辑封装在一起。
  - **职责集成**：原始的 `LapRecord` 仅为数据容器（DTO），重构后，**将时间格式化工具方法 `formatTime(long millis)` 封装为 `LapRecord` 的静态方法。** 这样，无论是 `MainActivity` 更新主计时，还是 `LapRecord` 自身初始化，都统一调用此方法，消除了代码冗余，使得数据模型更加“自给自足”。
  - **类型安全**：`LapRecord` 的构造函数直接接收 `long` 类型的毫秒数（原始数据），而不是格式化后的 `String`，确保了数据源头的准确性。

### 3. 用户交互与主题层 (UI/UX Layer)

- **关键组件：** `InputDialogFragment.java`, `CustomSpinnerAdapter.java`, `ColorUtils.java`
- **设计思想：** 优化用户体验和界面定制性。
  - `InputDialogFragment` 使用 `DialogFragment` 实现弹出层，通过接口 `InputDialogListener`（MVP/Listener模式的体现）与 `MainActivity` 隔离，职责分离明确。
  - 采用 **`Spinner` 下拉框** 代替纯文本输入框来选择“种类”，强制了输入规范，提高了数据的结构化程度。
  - `ColorUtils` 独立处理日夜模式的颜色逻辑，通过手动配置颜色资源 ID 并使用 `Context` 配置，实现了 **不依赖系统主题的、自定义粒度更高的日夜模式切换**。

### 4. 数据导出层 (Export Layer - Lightweight Solution)

- **关键组件：** `ExcelExportUtil.java`
- **设计思想：** 实用、轻量、无依赖。
  - `ExcelExportUtil` **没有引入** Apache POI 等大型第三方 Excel 库，而是通过直接生成 Excel XML 格式（兼容旧版 `.xls`）的字符串，并写入文件流。
  - 这种设计极大地**减小了应用体积**，**避免了复杂的兼容性和依赖冲突**，是一种高效且轻量的解决方案。
  - 文件保存通过 Android 的 **Storage Access Framework (SAF)** API (`ActivityResultContracts.CreateDocument`) 实现，符合现代 Android 系统的权限和文件管理规范。

## 🛠️ 安装教程 (Installation Guide)

本项目使用标准的 Android Gradle 构建系统。

1. **克隆项目：**

   Bash

   ```
   git clone https://gitee.com/philipslive/time-manager-android.git
   cd time-manager-android
   ```

2. **导入 Android Studio：** 打开 Android Studio，选择 `File` -> `Open`，导航至克隆的目录并打开。

3. **同步依赖：** 等待 Gradle 同步完成。项目主要依赖于 `AppCompat`、`Material`、`ConstraintLayout` 和 `Gson`。

4. **运行：** 连接 Android 设备或启动模拟器，点击 `Run` 按钮即可部署运行。

> **环境要求：**
>
> - `compileSdk`：36 (或更高)
> - `minSdk`：24 (Android 7.0 Nougat)

## 📖 使用说明 (Usage Instructions)

1. **开始/暂停计时：**
   - 点击 **“开始/暂停”** 按钮启动或暂停计时器。
   - 应用在后台运行时，计时器仍将继续准确运行。
2. **分段记录：**
   - 在计时运行过程中，点击 **“分段”** 按钮。
   - 计时器将自动暂停，并弹出输入对话框。
   - 在对话框中选择 **“种类”** 并填写 **“事件描述”**，点击 **“确认”** 完成分段记录。
   - 分段记录将显示在下方列表中，包括本次**间隔时间**和**累计时间**。
3. **重置：**
   - 点击 **“重置”** 按钮，清空计时器和所有分段记录。
4. **主题切换：**
   - 点击 **“模式”** 按钮，快速切换应用的主题颜色为**日间模式**或**夜间模式**。
5. **导出数据：**
   - 点击 **“导出”** 按钮，系统会弹出文件保存界面。选择保存路径后，即可将所有记录导出为命名格式为 `TimeManager_yyyyMMdd_HHmmss.xls` 的 Excel 文件。

## 🤝 参与贡献 (Contribution)

欢迎任何形式的贡献，包括 Bug 报告、功能建议、代码优化等。

**独立开发者：** philipslive

**联系邮箱：** [jingyeyousi_yuping@163.com](mailto:jingyeyousi_yuping@163.com)

------

**感谢您对本项目的关注与支持！**