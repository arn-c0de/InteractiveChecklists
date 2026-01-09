
<!-- Badges -->
<p align="left">
	<a href="https://github.com/arn-c0de/InteractiveChecklists"><img src="https://img.shields.io/badge/GitHub-arn--c0de%2FInteractiveChecklists-181717?logo=github" alt="GitHub" /></a>
	<a href="https://deepwiki.com/arn-c0de/InteractiveChecklists"><img src="https://img.shields.io/badge/DeepWiki-Project%20Docs-blueviolet?logo=book" alt="DeepWiki" /></a>
	<img src="https://img.shields.io/badge/version-1.0-blue.svg" alt="Version" />
	<img src="https://img.shields.io/badge/platform-Android-green.svg" alt="Platform" />
	<img src="https://img.shields.io/badge/built_with-Jetpack%20Compose-orange.svg" alt="Jetpack Compose" />
	<img src="https://img.shields.io/badge/license-CC--BY--NC--SA%204.0-lightgrey.svg" alt="License: CC BY-NC-SA 4.0" />
</p>


<div align="center">
	<a href="../../CHANGELOG.md">Changelog</a> |
	<a href="../EN/docnavigation.md">Documentation</a> |
	<a href="../EN/planning/roadmap.md">Roadmap</a> |
	<a href="../../SECURITY.md">Security Policy</a> |
	<a href="../../LICENSE">License</a> |
	<a href="../../COLLABORATORS.md">Collaborators</a>
</div>

<div align="center">
	<a href="../../THIRD_PARTY_LICENSES.md">Third-Party Licenses</a>
</div>

<p align="center">
	<a href="#screenshots">Screenshots</a> • <a href="#demo-videos">Demo Videos</a>
</p>

<div align="center">
	<a href="../../README.md">英语</a> |
	<a href="README_zh.md">中文</a>
</div>


# InteractiveChecklists

InteractiveChecklists 是一款用于查看与操作 Markdown 及 PDF 清单的 Android 应用。本应用使用 Jetpack Compose 构建，遵循 MVVM-style 架构。

> **开发状态:** 此代码库为开发版本，并非正式发布。应用程序功能可用，但正处于积极开发中，可能包含实验性功能。

> **注意:** 计划提供 1.1 版本的预览版 APK。如果您不熟悉 Android Studio 或从源代码构建应用程序，请等待官方的预览版发布后再进行测试。

> **重要 — 需要全新安装（v1.0.21）** ⚠️  
> 内置的地图数据库（`assets/databases/map_data.db`）在 **v1.0.21** 中重置为 **版本 1**。如果您从早期版本升级，**必须完全卸载应用（包括应用数据）**，然后重新安装新的 APK，以避免模式不匹配错误和潜在的数据丢失。若需保留自定义标记，请备份任何本地数据库文件。

> **当前的开发重点(2025.12)**  
> 目前，我主要致力于核心基础设施的开发，以实现设备与 DCS World 之间的安全数据传输。这包括最终确定 ECDH 握手协议的实现，为未来的小队模式（多设备间安全数据共享）奠定基础，以及完成数据库集成与数据格式化。这些基础性改动复杂且耗时，这意味着其他方面的进展——例如 UI 性能优化、视觉增强及小型功能开发——仅能并行且缓慢地进行。感谢您的耐心！

**目录**

- [功能特性](#features)
- [截图](#screenshots)
- [演示视频](#demo-videos)
- [安装](#installation)
- [系统要求](#system-requirements)
- [如何构建与运行](#how-to-build--run)
- [核心组件](#key-components)
- [贡献指南](#contributing)
- [支持&联系](#support--contact)
- [常见问题](#faq)
- [致谢&鸣谢](#acknowledgements--credits)
- [许可证](#license)


## 功能特性

- **统一文件系统:** 在单一层次视图中管理来自内置资源和内部存储的文件。
- **多语言支持:** 本应用支持英语、西班牙语、德语和简体中文。您可以在设置菜单中切换语言。所有 UI 文本均有英文版本。
- **多标签系统:** 通过可滚动的标签栏、快速标签切换器、滑动导航和标签持久化，打开多个文档（MD/PDF）。
- **PDF 查看器:** 支持标注（绘制/高亮/擦除）、捏合缩放、页面对齐和颜色反相的 PDF 查看器。
- **交互式 Markdown 清单:** 用于交互式清单的有状态复选框和可折叠区域。
- **标签系统:** 为文件分配标签以便筛选和组织。
- **快速笔记:** 由 Room 提供支持的持久化笔记，支持搜索、自动保存和 Markdown。
- **数据持久化:** 本地存储用户偏好设置、标注、快捷方式、标签和打开的标签页。
- **DataPad (实验性):** 用于 DCS World 的实时飞行遥测数据显示（UDP）。 将飞机遥测数据流式传输到应用，以获取实时状态和弹窗详情——完整详情和设置说明请参见 `../EN/features/DATAPAD_FEATURE.md`
- **战术单位追踪 (实验性):** 在地图上实时更新显示战术单位标记（飞机、直升机、地面单位、舰船）。 标记弹窗包含 **"最后出现"** 时间戳以及刷新摘要（速度/高度）。**"仅显示活动单位"** 筛选器（显示最近 10 秒内出现的单位）在列表和地图间同步。设置和详情请参见 `../EN/features/TACTICAL_UNITS_TRACKING.md` 和 `../../scripts/DCS-SCRIPTS-FOLDER-Experimental/README_ENTITY_TRACKING.md` 
- **航空地图 (实验性):** 基于 OpenStreetMap 的地图查看器，通过 DataPad 数据流实现实时飞机位置追踪。添加 `MapViewer` 标签页，显示飞机位置、航向、高度和基本覆盖层——详情和配置请参见 `../EN/features/AVIATION_MAP_FEATURE.md`。
- **MapDatabaseTools (Python):** 一组用于接收、解密（AES-GCM）和可视化 DCS 飞行遥测数据的 Python 工具。包含一个带有嵌入式 OpenStreetMap/Leaflet 地图的 PySide6 GUI，用于实时飞机追踪、标记数据库以及管理地图资源的辅助脚本。使用和配置说明请参见 `scripts/MapDatabaseTools/README.md` 。
  新增：用于快速创建新地图的模板脚本 `scripts/MapDatabaseTools/add_new_map_template.py`。复制并重命名（例如 `add_syria_markers.py`），替换 `MAP_NAME` 和占位符并填入位置数据，然后运行 `python add_<map>_markers.py [--replace]` 将样本数据插入本地 `map_data.db`（`--replace` 会先清除现有位置数据）。
## 实验性功能: DataPad (实时飞行遥测)

DataPad 是一项实验性功能，通过 UDP（默认端口 **5010**）从 DCS World 接收实时飞机遥测数据。它面向高级用户，需要运行 `forward_parsed_udp.py` 脚本来将遥测数据转发到您的设备。

### 安全特性 (新增 - 2025.12)

**✅ ECDH 握手模式** - 可用于生产环境的安全通信：
- **端到端加密**：使用每个会话独立的 AES-256-GCM 密钥
- **设备认证**：通过白名单 (`authorized_devices.json`)
- **前向安全性**：泄露一个会话不会影响其他会话
- **重放攻击防护**：使用计数器/随机数机制防止重放
- **时间戳验证**（5 分钟窗口）
- **双向认证**（客户端 ↔ 服务器）

**🔒 服务器密钥固定（TOFU）** - 信任首次使用（Trust-On-First-Use），在首次成功连接后自动固定服务器密钥以便检测中间人攻击。

**🔑 预共享密钥（PSK）握手管理器（可选）** - 提供与 PSK 兼容的替代握手方案；请参阅文档了解如何安全生成和分发 32 字节（256 位）PSK 的建议。

**🛡️ 可选工作量证明（PoW）** - 可配置的抗 DoS 保护：在握手阶段要求客户端完成可调难度的 PoW（使用 `--enable-pow` 与 `--pow-difficulty`）。

**快速开始（示例）**

```bash
# 启用握手模式（ECDH + TOFU）
python forward_parsed_udp.py --interval 10 --host 192.168.178.132 --port 5010 --verbose --authorized-devices authorized_devices.json --bind-ip 192.168.178.100

# 启用工作量证明（PoW）
python forward_parsed_udp.py --enable-pow --pow-difficulty 16 --interval 10 --host 192.168.178.132 --port 5010 --verbose --authorized-devices authorized_devices.json --bind-ip 192.168.178.100

# 同机测试（带握手端口）
python forward_parsed_udp.py --repeat-last --interval 3 --host 127.0.0.1 --port 5010 --handshake-port 5011 --use-handshake --authorized-devices authorized_devices.json
```

DataPad 还支持接收从 DCS 导出的**实体接触信息**（战术单位）。在应用中启用**实体追踪**以接收战术单位并将其显示为实时标记（需要运行启用了实体追踪的转发器）。设置和详情说明请参见`../../scripts/DCS-SCRIPTS-FOLDER-Experimental/README_ENTITY_TRACKING.md` 和 `../EN/features/TACTICAL_UNITS_TRACKING.md`。

请参阅 `../EN/technical/ECDH_USAGE_GUIDE.md` 和 `../EN/technical/DATA_FLOW_ANALYSIS.md` 以获取设置、PSK 指南和 PoW 调优。

请参阅 `../EN/features/DATAPAD_FEATURE.md` 获取完整使用、配置和故障排除信息。

**阶段 1 (实验性)**: 这是 DataPad 的第一阶段——未来将增加更多遥测、可视化和安全改进。

**下一步计划:** 双向通信（实验性）以实现应用向 DCS 的数据回传。

## 截图

<p align="center">
		<img src="../../images/FileExplorer.png" alt="File explorer - list of files and folders" width="360" />
		<img src="../../images/md_viewer.png" alt="Markdown viewer showing interactive checklists" width="360" />
		<img src="../../images/pdf_viewer.png" alt="PDF viewer with annotation tools" width="360" />
		<img src="../../images/quick_notes.png" alt="QuickNotes bottom sheet and editor" width="360" />
		<img src="../../images/map_pattern_calculator.png" alt="Calculator for landing patterns" width="360" />
		<img src="../../images/map_flightpath.png" alt="Flight path overlay" width="360" />
		<img src="../../images/map-route.png" alt="Route lines overlay with labels" width="360" />
		<img src="../../images/routeplanner.png" alt="Route planner - line preview" width="360" />
		<img src="../../images/landingroute-planner.png" alt="Create Route sheet" width="360" />
		<img src="../../images/datapad.png" alt="DataPad live telemetry panel" width="360" />
		<img src="../../images/settings_menu.png" alt="Settings and preferences" width="360" />
</p>


<a name="demo-videos"></a>
## 演示视频 🎬

<p align="center">
<a href="https://youtu.be/V7vRuQvTFK8"><img src="https://img.youtube.com/vi/V7vRuQvTFK8/0.jpg" alt="Demo 1" width="360" /></a> &nbsp;
<a href="https://youtu.be/G5uiONmqxe0"><img src="https://img.youtube.com/vi/G5uiONmqxe0/0.jpg" alt="Demo 2" width="360" /></a>
</p>

<p align="center">
</p>

> 📝 **注意**  
> 此测试录制旨在评估录制性能、平板电脑捕获工作流程、分辨率设置以及 DCS 游戏过程中的整体系统稳定性。任务内容和节奏经过特意设计，力求简单实用。

## 安装

在本地运行项目的分步说明。

1. 前置要求
	 - 安装 Android Studio（推荐 Arctic Fox 或更高版本）。
	 - 安装兼容的 JDK（推荐 Java 11 或更高版本）。
	 - 配置 Android SDK 和至少一个模拟器，或使用物理设备。

2. 克隆本仓库

```bash
git clone https://github.com/arn-c0de/InteractiveChecklists.git
cd InteractiveChecklists
```

3. 使用 Gradle 构建（命令行）

```bash
./gradlew assembleDebug
```

4. 在 Android Studio 中打开
	 - 在 Android Studio 中打开 `InteractiveChecklists` 目录。
	 - 等待 Gradle 同步完成，并允许 Android Studio 下载任何缺失的 SDK 组件。
	 - 在模拟器或连接的设备上运行应用程序。


## 系统要求

- 支持的操作系统：Windows、macOS、Linux（用于开发）。
- Android Studio: 推荐 Arctic Fox 或更新版本。
- JDK: 推荐 Java 11+。
- Android SDK: API 级别需与项目的 `compileSdk` 和 `targetSdk` 对应（参见 `build.gradle.kts`）。

## 如何构建与运行

- 从 Android Studio：打开项目，等待 Gradle 完成同步，然后选择目标设备并点击 **运行**。
- 从命令行：`./gradlew assembleDebug` 构建 APK；使用 `./gradlew installDebug` 安装到已连接的设备。

## 核心组件

- `MainActivity.kt`: 应用程序入口点，负责导航协调。
- `data/files/InternalFileManager.kt`: 统一文件管理。
- `ui/files/InternalFilesScreen.kt`: 文件浏览器和标签 UI。
- `ui/checklist/MarkdownViewer.kt`: 交互式 Markdown 清单查看器。
- `ui/checklist/PdfViewer.kt`: PDF 查看器和标注工具。
- `data/quicknotes/QuickNoteManager.kt`: QuickNotes 数据层。


## 贡献指南

我们欢迎您为本应用做出任何贡献。有关指南、issue流程和编码标准，请参见[COLLABORATORS_zh.md](COLLABORATORS_zh.md)。

快速贡献思路：
- 改进文档或添加示例。
- 添加或扩展测试。
- 修复小的 UI/UX 错误或无障碍访问问题。

对于较大或破坏性的更改，请先提交一个 issue 以讨论设计和范围。


## 路线图

计划中的功能和长期改进记录在 [Roadmap](../EN/planning/roadmap.md) 中。


## 支持&联系

如果您遇到问题或有疑问

- 在此代码库中提 issue。
- 对于安全相关问题，请遵循 [SECURITY.md](SECURITY.md) 中的说明。
- 有关贡献协调和讨论，请参见 [COLLABORATORS_zh.md](COLLABORATORS_zh.md)。


## FAQ

- Q: 如何运行测试?
	- A: 单元测试在 `app/src/test`. 通过 `./gradlew test` 运行测试.
- Q: 许可证是什么?
	- A: 本项目采用 CC-BY-NC-SA 4.0 许可证。详情请参见 `LICENSE` 文件
- Q: 文档在哪里?
	- A: 请参见 `docs/` 文件夹 或点击 [Documentation index](docnavigation_zh.md).


## 致谢&鸣谢

感谢所有贡献者以及本项目所使用的 Jetpack Compose 和 Android 开源生态系统。

## 许可证

本项目基于 `LICENSE` 文件（CC BY-NC-SA 4.0）中的条款进行授权。



