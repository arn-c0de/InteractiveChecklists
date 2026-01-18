[← Back to README](README.md)

# 🤝 Contributing to InteractiveChecklists

Thanks for helping improve InteractiveChecklists! This guide covers everything you need to get started.

---

## 💰 Unpaid Contributions

All contributions are currently **unpaid and voluntary**. This is a **non-commercial** project without donation functionality or sponsorship options at this time.

---

## 🚀 Quick Start

```bash
# 1. Fork & clone
git clone https://github.com/arn-c0de/InteractiveChecklists.git
cd InteractiveChecklists

# 2. Create a feature branch
git checkout -b feat/your-feature-name

# 3. Build & test
./gradlew assembleDebug
./gradlew test
./gradlew lint

# 4. Commit & push
git commit -m "feat: add your feature description"
git push origin feat/your-feature-name

# 5. Open a Pull Request on GitHub
```

---

## 🌿 Branch Naming

| Type | Pattern | Example |
|------|---------|---------|
| Feature | `feat/short-description` | `feat/map-zoom-controls` |
| Bug fix | `fix/short-description` | `fix/datapad-reconnect` |
| Docs | `docs/short-description` | `docs/setup-guide` |
| Refactor | `refactor/short-description` | `refactor/file-manager` |

---

## 💡 How to Contribute

### 🐛 Report Bugs
Open an issue with:
- Clear title
- Steps to reproduce
- Expected vs. actual behavior
- Device/Android version info

### ✨ Suggest Features
Open an issue describing:
- The problem you're solving
- Your proposed solution
- Any alternatives you considered

### 📝 Submit Code
1. Check existing issues — maybe someone's already working on it
2. For large changes, open an issue first to discuss
3. Keep PRs small and focused
4. Reference issues with `Fixes #123` in your PR description

---

## ✅ PR Checklist

Before submitting:

- [ ] Linked to related issue (if any)
- [ ] Clear description of changes
- [ ] Tests added/updated
- [ ] `./gradlew assembleDebug` passes
- [ ] `./gradlew test` passes
- [ ] `./gradlew lint` passes
- [ ] Code follows project style

---

## 🎨 Code Style

- **Kotlin** — idiomatic patterns, no Java-style code
- **Compose** — follow official Compose guidelines
- **Naming** — clear, descriptive names; no abbreviations
- **Comments** — explain *why*, not *what*
- **Dependencies** — minimize new ones; justify in PR if needed

---

## 🙌 Areas Where Help is Needed

| Area | Skills | Issue Label |
|------|--------|-------------|
| 🌍 Translations | Native speaker | `translations` |
| 📚 Documentation | Technical writing | `documentation` |
| ⚡ Performance | Profiling, optimization | `performance` |
| 🧪 Testing | Unit/UI tests | `testing` |
| 🎨 UI/UX | Design, accessibility | `ui` |

Check issues labeled [`help wanted`](https://github.com/arn-c0de/InteractiveChecklists/labels/help%20wanted) or [`good first issue`](https://github.com/arn-c0de/InteractiveChecklists/labels/good%20first%20issue).

---

## 💬 Communication

- **Questions?** Open a [Discussion](https://github.com/arn-c0de/InteractiveChecklists/discussions) or issue with `question` label
- **PR feedback?** Respond in the PR thread
- **Security issues?** See [SECURITY.md](SECURITY.md) — do not post publicly

---

## 📄 License

By contributing, you agree that your contributions will be licensed under the project's [CC BY-NC-SA 4.0](LICENSE) license.

---

Thanks for contributing! 🎉
