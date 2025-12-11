# ChecklistInteractive

[![License: CC BY-NC-SA 4.0](https://img.shields.io/badge/License-CC%20BY--NC--SA%204.0-lightgrey.svg)](https://creativecommons.org/licenses/by-nc-sa/4.0/)
[![Language](https://img.shields.io/badge/language-Kotlin-blue.svg)](https://kotlinlang.org/)

Interactive checklist app for flight procedures (DCS-style) using PDF and MD checklists.

## Features

*   **PDF Checklists**: Easily import and use your existing PDF checklists.
*   **Automatic Discovery**: The app automatically discovers and organizes your checklists from your assets folder.
*   **Folder Organization**: Organize your checklists into subfolders for better management.
*   **PDF Viewer**: A built-in PDF viewer to navigate through your checklists.
*   **Sharing**: Share your checklists with others.

## Getting Started

### Prerequisites

*   Android Studio
*   An Android device or emulator

### Building

1.  **Clone the repository:**
    ```bash
    git clone https://github.com/arn-c0de/InteractiveChecklists.git
    ```
2.  **Open the project in Android Studio.**
3.  **Build and run the project.**

## Usage

*   Add your `.pdf` checklist files to the `app/src/main/assets/checklists/` directory. You can create subfolders to organize them.
*   Launch the app, and your checklists will be available in the "Browse" screen.
*   Select a checklist to open it in the PDF viewer.

## Future Improvements

*   Allow importing `.pdf` files from external storage or remote URLs.
*   Add a dedicated editor for authoring checklists in-app.
*   Allow sync to cloud/backups (Drive, GitHub gist, etc.).
*   Support multi-level items and nested lists.

## Contributing

Contributions are welcome! Please feel free to submit a pull request.

## License

This project is licensed under the [Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International License](LICENSE).