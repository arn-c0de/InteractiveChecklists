# Collaborators

Thank you for your interest in contributing to InteractiveChecklists! This guide explains how to get started, how we review work, and where to ask questions.

## Maintainers

- The maintainers are the project owners and active contributors listed on the repository. If you need to contact maintainers directly, open an issue or use the project's Discussions tab.

## Quick Start for Contributors

1. Fork the repository on GitHub and clone your fork.
2. Create a topic branch: `git checkout -b feat/short-description`.
3. Run the project locally and add tests for your change when applicable.
4. Commit with a clear message and open a pull request against the main branch.

Recommended local commands:

- Build the app: `./gradlew assembleDebug`
- Run unit tests: `./gradlew test`
- Run static checks or lint: `./gradlew lint`

## Contribution Process

- File an issue to propose features or report bugs. Include a clear title, steps to reproduce (if applicable), and expected behavior.
- Reference the issue in your pull request using `Fixes #<issue-number>` when appropriate.
- Keep pull requests focused and small to simplify review. Large refactors are better split into multiple PRs.

## PR Checklist

- Link to a related issue (if any).
- Include a clear description of what changed and why.
- Add or update tests covering your change.
- Ensure the project builds and checks pass locally.
- Keep formatting and style consistent with existing code.

## Coding Guidelines

- Prefer idiomatic Kotlin and Jetpack Compose patterns used in the project.
- Keep UI changes small and easy to visually verify.
- If adding dependencies, explain why and keep them minimal.

## Communication & Support

- Use GitHub Issues and Pull Requests for discussion and review.
- For broader planning or coordination, use GitHub Discussions (if enabled) or open an issue with the `discussion` tag.

## Code of Conduct & License

- Please follow the project's Code of Conduct. If a `CODE_OF_CONDUCT.md` is not present yet, maintain respectful and inclusive behaviour in issues, PRs, and discussions.
- By contributing, you agree to the repository license. Larger contributions may require a signed CLA depending on the maintainer's policy.

## Thank you

We appreciate your time and contributions — thank you for helping make InteractiveChecklists better!
