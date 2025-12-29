[← 返回 README](README_zh.md)

# 🤝 贡献指南

感谢您帮助改进 InteractiveChecklists！本指南涵盖了您开始所需的一切。

---

## 🚀 快速开始

```bash
# 1. Fork & clone
git clone https://github.com/arn-c0de/InteractiveChecklists.git
cd InteractiveChecklists

# 2. 创建功能分支
git checkout -b feat/your-feature-name

# 3. 构建与测试
./gradlew assembleDebug
./gradlew test
./gradlew lint

# 4. 提交 & 推送
git commit -m "feat: add your feature description"
git push origin feat/your-feature-name

# 5. 在 GitHub 上发起 Pull Request
```

---

## 🌿 分支命名

| 类型 | 模式 | 示例 |
|------|---------|---------|
| 功能 (Feature) | `feat/short-description` | `feat/map-zoom-controls` |
| 修复 (Bug fix) | `fix/short-description` | `fix/datapad-reconnect` |
| 文档 (Docs) | `docs/short-description` | `docs/setup-guide` |
| 重构 (Refactor) | `refactor/short-description` | `refactor/file-manager` |

---

## 💡 如何贡献

### 🐛 报告 Bug
提交 Issue 并包含：
- 清晰的标题
- 复现步骤
- 预期行为 vs 实际行为
- 设备/Android 版本信息

### ✨ 建议功能
提交 Issue 并描述：
- 您要解决的问题
- 您建议的解决方案
- 您考虑过的任何替代方案

### 📝 提交代码
1. 检查现有的 Issue — 也许已经有人在处理了
2. 对于较大的更改，请先提交 Issue 进行讨论
3. 保持 PR 小而专注
4. 在您的 PR 描述中使用 `Fixes #123` 引用 Issue

---

## ✅ PR 检查清单

提交前：

- [ ] 链接到相关 Issue（如果有）
- [ ] 清晰的更改描述
- [ ] 添加/更新了测试
- [ ] `./gradlew assembleDebug` 通过
- [ ] `./gradlew test` 通过
- [ ] `./gradlew lint` 通过
- [ ] 代码遵循项目风格

---

## 🎨 代码风格

- **Kotlin** — 惯用模式，避免 Java 风格的代码
- **Compose** — 遵循官方 Compose 指南
- **命名** — 清晰、描述性的名称；不使用缩写
- **注释** — 解释 *为什么*，而不是 *什么*
- **依赖** — 尽量减少新的依赖；如果需要，请在 PR 中说明理由

---

## 🙌 需要帮助的领域

| 领域 | 技能 | Issue 标签 |
|------|--------|-------------|
| 🌍 翻译 | 母语使用者 | `translations` |
| 📚 文档 | 技术写作 | `documentation` |
| ⚡ 性能 | 分析、优化 | `performance` |
| 🧪 测试 | 单元/UI 测试 | `testing` |
| 🎨 UI/UX | 设计、无障碍 | `ui` |
