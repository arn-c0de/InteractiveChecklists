[← Back to README](README.md)

# 🤝 Contributing to InteractiveChecklists

Thank you for wanting to help make InteractiveChecklists better!  
This guide explains how to get started quickly and contribute effectively.
## 💰 Important: Unpaid Contributions (for now)

All contributions are currently **voluntary and completely unpaid**.  
This is a **non-commercial, hobby/side-project** at the moment — there are **no donation or sponsorship options** active yet.

> **However**, if the project gains enough traction in the future and stable **sponsoring and/or donation** channels become available (e.g. GitHub Sponsors, Buy Me a Coffee, Patreon, etc.), I plan to open **paid developer slots** for recurring or high-impact contributors.
> 
> That means:  
> → **Right now → 100 % voluntary**
> 
> → Potentially later → possibility of getting **paid** for meaningful work
> 
> I want to be very upfront about this so nobody has wrong expectations.


##  Contributor Recognition & Credits

Even though contributions are unpaid, **every contributor is acknowledged and appreciated**.

Depending on the type and impact of your contribution, you may receive recognition in one or more of the following ways:

###  GitHub Recognition
- Your name and GitHub profile will appear in:
  - The repository’s **Contributors** list
  - Relevant **release notes** (for notable contributions)
- Significant or recurring contributors may be listed in a dedicated **THANKS / CONTRIBUTORS** section in the README

### 📱 In-App Credits (when applicable)
For meaningful contributions (e.g. features, UI/UX, translations, accessibility, major bug fixes):

- Your name or GitHub handle may appear in the app’s **Credits / About** screen
- Optional: role-based credit (e.g. *Translation*, *UI/UX*, *Core Contributor*)

> Displayed names are taken from your **GitHub username** unless you request a different name.

###  Privacy & Opt-Out
- Credits are **opt-in by default**
- If you **do not want to be mentioned**, simply say so in your PR or issue
- You can also request removal or changes to your displayed name at any time


## 🚀 Quick Start in 5 Steps

```bash
# 1. Fork and clone the repository
git clone https://github.com/arn-c0de/InteractiveChecklists.git
cd InteractiveChecklists

# 2. Create a feature branch (see naming convention below)
git checkout -b feat/your-feature-name

# 3. Build and run basic checks
./gradlew assembleDebug
./gradlew test
./gradlew lint
# Optional (if detekt is set up):
./gradlew detekt

# 4. Commit using conventional commits (preferred)
git commit -m "feat: add pinch-to-zoom support for maps"
# or
git commit -m "fix: prevent crash when datapad connection is lost"

# 5. Push and open a Pull Request
git push origin feat/your-feature-name
```

Then → create a Pull Request at https://github.com/arn-c0de/InteractiveChecklists

## 🌿 Branch Naming & Commit Convention

We use **semantic branch names** and **Conventional Commits**:

| Type              | Branch Prefix | Example                              |
|-------------------|---------------|--------------------------------------|
| New feature       | `feat/`       | `feat/map-pinch-zoom`               |
| Bug fix           | `fix/`        | `fix/crash-on-null-datapad`         |
| Documentation     | `docs/`       | `docs/improve-contributing-guide`   |
| Refactoring       | `refactor/`   | `refactor/checklist-viewmodel`      |
| Performance       | `perf/`       | `perf/optimize-checklist-rendering` |
| Maintenance       | `chore/`      | `chore/upgrade-compose-bom`         |

Commit messages should start with a type:

```text
feat: add dark mode toggle to settings screen
fix: resolve memory leak in image caching
docs: clarify checklist export format
chore(deps): bump Jetpack Compose BOM to 2025.x.x
```

## 💡 Ways to Contribute

### 1. 🐛 Report a Bug

- Check first if the issue already exists
- Open an issue with:
  - Clear, concise **title**
  - **Steps to reproduce**
  - **Expected** vs **actual** behavior
  - Device model, Android version, app version
  - Screenshots or short screen recordings (very helpful!)

### 2. ✨ Suggest a Feature or Improvement

Best as a **Discussion** or issue labeled `enhancement` / `idea`:

- What problem does it solve?
- Who would benefit most?
- Rough idea of how it could work
- Any alternatives you’ve considered?

### 3. Submit Code

1. Check open issues — someone might already be working on it
2. For larger changes → open an issue / discussion first to align
3. Keep Pull Requests small and focused (ideally 1–3 logical changes)
4. Use `Fixes #123` or `Closes #456` in the PR description when relevant


## ✅ Pull Request Checklist

Please tick these before submitting:

- [ ] Linked to a related issue (if applicable)
- [ ] PR description clearly explains **what** & **why**
- [ ] Added or updated tests
- [ ] `./gradlew build test lint detekt` runs clean
- [ ] Code follows project style guidelines
- [ ] No unnecessary new dependencies (or very well justified)
- [ ] **Optional (UI changes):** Screenshot(s) showing the changes attached



## 🙌 Areas Where Help Is Especially Welcome (as of early 2026)

| Area                  | Skills Needed                          | Label              |
|-----------------------|----------------------------------------|--------------------|
| Translations          | Native speakers (DE/EN/FR/ES/…)        | `translations`     |
| Documentation         | Clear technical writing                | `documentation`    |
| Performance           | Profiling, leak detection, jank fixing | `performance`      |
| Automated Testing     | Unit, integration, UI tests            | `testing`          |
| UI/UX & Accessibility | Compose, TalkBack, contrast ratios     | `ui`, `accessibility` |
| Onboarding / Tutorials| Short videos, walkthroughs             | `onboarding`       |


Also look for issues labeled [`good first issue`](https://github.com/arn-c0de/InteractiveChecklists/labels/good%20first%20issue) or [`help wanted`](https://github.com/arn-c0de/InteractiveChecklists/labels/help%20wanted)

## 💬 Communication

- **Questions?** → Use [Discussions](https://github.com/arn-c0de/InteractiveChecklists/discussions) or open an issue with label `question`
- **Review feedback?** → Reply directly in the PR thread
- **Security vulnerability?** → **Do NOT** post publicly — see [SECURITY.md](SECURITY.md)

## 📄 License

By contributing, you agree that your contributions will be licensed under the project’s **[CC BY-NC-SA 4.0](LICENSE)** license.

---

**Thanks a lot for contributing — every bit helps!** 🎉


---
App Version: v1.0.25
Last Updated: 2026.01.19
---