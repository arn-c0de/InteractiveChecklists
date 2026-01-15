#!/usr/bin/env bash
set -euo pipefail

echo
echo "========================================"
echo " DCS DataPad Server - Installation"
echo "========================================="
echo

# Find python (prefer python3)
PYTHON=""
if command -v python3 >/dev/null 2>&1; then
  PYTHON=python3
elif command -v python >/dev/null 2>&1; then
  PYTHON=python
else
  echo "ERROR: Python is not installed or not in PATH"
  echo "Please install Python 3.8 or newer from https://www.python.org/"
  exit 1
fi

echo "[1/3] Checking virtual environment..."
if [ ! -d "venv" ]; then
  echo "Creating new virtual environment..."
  $PYTHON -m venv venv || { echo "ERROR: Failed to create virtual environment"; exit 1; }
  echo "Virtual environment created successfully!"
else
  echo "Virtual environment already exists"
fi

echo
echo "[2/3] Activating virtual environment..."
# shellcheck disable=SC1091
source venv/bin/activate || { echo "ERROR: Failed to activate virtual environment"; exit 1; }

echo
echo "[3/3] Installing/updating dependencies..."
python -m pip install --upgrade pip
if [ -f requirements.txt ]; then
  python -m pip install -r requirements.txt || { echo "ERROR: Failed to install dependencies"; exit 1; }
else
  echo "requirements.txt not found; skipping dependency install"
fi

echo
echo "========================================"
echo " Installation completed successfully!"
echo "========================================"
echo
echo "You can now run the server with: ./run.sh"
echo
exit 0
