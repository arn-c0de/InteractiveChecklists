[Home](../../README.md) | [Documentation Navigation](../docnavigation.md)

# PDF Checklist Feature

This document explains the concept and implementation for interactive checklists based on Markdown files in the ChecklistInteractive app.

## Concept

- Checklists are provided as PDF files stored in `assets/checklists`.
 - The app reads PDF files from `assets/checklists` and presents them using a PDF viewer.

## Architecture

- `Checklist` (data model) represents the full checklist with sections and items.
- `ChecklistRepository` handles data for PDF-based checklists (metadata), while PDF rendering is handled by a PDF viewer.
- `ChecklistViewModel` manages loading data and toggling items (UI-friendly view model for Compose).
- `ChecklistScreen` is a Compose UI showing sections and interactive items.

## File Format

- Files are stored in `app/src/main/assets/checklists`.
 - Files are stored in `app/src/main/assets/checklists` and may be organized in subfolders.
	 The app automatically lists all top-level folders and files, and you may create nested subfolders.
 	 The Browse screen auto-detects PDF files (`.pdf`) and folders and lets users navigate into subfolders to open checklists.
- Groups and sections are recognized by headings (#, ##). Items are recognized as list items; task items use `[ ]` or `[x]` markers.

Example:

# F-16 Startup Checklist

- [ ] Battery - ON
- [x] Avionics - ON

## Before Taxi

- [ ] Flaps - Set


## Extending the App

- Add more assets to `assets/checklists` to create new checklist files.
- Add file import UI to allow loading checklists from external storage or the web.
- Add a small editor or store checklists in external storage for sharing between devices.

*** Implementation details and further improvements added in the app code comments. ***


---
App Version: v1.0.25
Last Updated: 2026.01.19
---