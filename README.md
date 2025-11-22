一款轻量高效的个人时间管理工具，专注于精确计时、分段记录与数据导出，帮助用户追踪和管理时间分配。

## Software Introduction

TimeManagerAndroid 是一款面向个人用户的时间管理应用，核心功能包括：

- **精确计时**：支持毫秒级计时器，记录累计时间与分段间隔
- **分段管理**：通过分类（工作 / 休息等）和详细描述记录每段时间用途
- **数据导出**：支持将记录导出为 Excel 格式，便于后续分析
- **模式切换**：内置白天 / 黑夜模式，适配不同使用场景
- **状态留存**：即使应用被后台回收，仍能恢复计时状态，避免数据丢失

应用设计遵循简洁易用原则，界面直观，操作流畅，适用于需要精确记录时间分配的场景（如工作计时、学习规划、任务管理等）。

## Software Architecture

### 整体架构

项目基于 Android 原生开发，采用**MVVM 架构**（最新版本重构后），严格分离 UI 展示与业务逻辑：

- **UI 层**：Activity/Fragment 负责界面渲染与用户交互
- **ViewModel 层**：管理状态数据与业务逻辑（如计时控制、数据处理）
- **数据层**：通过 SharedPreferences 实现状态持久化，文件系统处理数据导出

### 核心模块

1. **计时模块**
   - 基于`Handler`和`Timer`实现高精度计时逻辑
   - 处理暂停 / 继续 / 重置等状态切换，确保后台恢复后数据准确
   - 核心类：`MainActivity`（UI 交互）、`LapRecord`（分段数据模型）
2. **UI 组件**
   - 主界面：`activity_main.xml`布局，包含计时器显示、控制按钮与分段列表
   - 分段对话框：`InputDialogFragment`，用于输入分段分类与详情
   - 列表展示：`RecyclerView` + `LapAdapter`，高效展示分段记录
3. **主题与样式**
   - 日夜模式通过`values/themes.xml`与`values-night/themes.xml`实现
   - 统一颜色管理：使用`colorSurface`、`colorOnSurface`等主题属性实现样式一致性
   - 主题切换逻辑：`applyThemeColors()`方法统一更新所有 UI 元素颜色
4. **数据持久化与导出**
   - 状态保存：`SharedPreferences`存储计时状态、模式设置等
   - 日志系统：`LogUtils`记录操作日志，保存至`sdcard/Download/TimeManager`目录
   - 导出功能：通过`fileSaverLauncher`实现 Excel 格式导出
5. **兼容性处理**
   - 支持 Android 7.0+（minSdk=24），适配高版本系统权限与 API 变化
   - 对话框宽度适配：`setDialogWidthCompatible()`确保在不同屏幕尺寸下显示正常

## Installation Guide

### 从源码构建

1. **克隆仓库**

   ```bash
   git clone https://github.com/yupingya/TimeManagerAndroid.git
   cd TimeManagerAndroid
   ```

2. **环境要求**

   - Android Studio Giraffe 或更高版本
   - JDK 11
   - Android SDK 36（targetSdk）与 SDK 24（minSdk）

3. **构建步骤**

   - 打开 Android Studio，导入项目
   - 等待 Gradle 同步完成（依赖将自动下载，包括 Gson、Material 组件等）
   - 连接 Android 设备或启动模拟器，点击 "Run" 按钮构建并安装

## 参与贡献

欢迎任何形式的贡献，包括 Bug 报告、功能建议、代码优化等。

**独立开发者：** philipslive

**联系邮箱：** [jingyeyousi_yuping@163.com](mailto:jingyeyousi_yuping@163.com)

贡献需遵循项目代码规范，确保新增功能兼容现有逻辑，且包含必要的测试。

## 开源协议

本项目采用**GPL-3.0 License**开源协议，详情参见 LICENSE 文件。

## 版本更新历史

### TimeManager V1.5.1（2025-11-22）

- 重构 MainActivity，严格遵循 MVVM 架构：ViewModel 管理状态，Activity 负责 UI 与计时器生命周期
- 修复日夜模式切换后计时器线程未恢复、按钮状态不同步问题
- 修复分段弹窗主题错乱（白天显示黑夜样式）：通过 MainActivity 提供 isNightMode () 实现主题同步
- 修正 InputDialogFragment 中 Fragment 类型错误（android.app.Fragment → androidx.fragment.app.Fragment）
- 统一 applyThemeColors (isNight) 应用逻辑，确保所有控件颜色正确更新
- 所有修改严格保留原有 XML ID 和功能行为，无破坏性变更
- 新增中文系统日志与工具日志，便于调试追踪

### TimeManager V1.3.3（2025-11-21 后）

- 将保存的日志都改为中文日志

### TimeManager V1.3.2（2025-11-21）

- 修复黑夜模式下确认弹窗标题颜色与居中问题：
  - 使用自定义 TextView 替代系统标题，解决部分 ROM 强制覆盖样式问题
  - 确保 "确认操作" 文字在黑夜模式下显示为白色（colorOnSurface），白天为黑色
  - 标题文字严格居中显示
- 统一取消按钮、返回键、点击外部区域均触发二次确认弹窗
- 增强异常日志记录（中文 Log.e + LogUtils）
- 新增 dpToPx 工具方法支持自定义内边距

### TimeManager V1.3.1（2025-11-XX）

- 修复软件被内存杀死导致的卡顿问题
- 添加日志功能，日志文件保存在手机的`sdcard/Download/TimeManager`目录

### TimeManager V1.2.4（2025-11-XX）

- 更新类别列表

### TimeManager V1.2.3（2025-11-XX）

- 修复开始时间列的记录时间
- 更新 push 文件

### TimeManager V1.2.2（2025-11-XX）

- 优化后台内存回收处理，确保回收后仍能继续计时
- 修复操作失误导致的问题

### TimeManager V1.2（2025-11-XX）

- 增加程序后台留存的逻辑代码
- 将界面组件向下挪动 60dp，优化布局

### TimeManager V1.1（2025-11-XX）

- 初步实现程序后台留存逻辑
- 调整界面组件位置

### TimeManager V1.0（2025-XX-XX）

- 初始版本发布，包含基础计时、分段记录、导出功能

### Initial commit

- 项目初始化