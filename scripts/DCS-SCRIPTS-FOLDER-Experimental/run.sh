#!/usr/bin/env bash
set -euo pipefail

echo
echo "========================================"
echo " DCS DataPad Server - Launcher"
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
  exit 1
fi

if [ ! -d "venv" ]; then
  echo "ERROR: Virtual environment not found!"
  echo "Please run ./install.sh first to set up the environment."
  exit 1
fi

echo
echo "Activating virtual environment..."
# shellcheck disable=SC1091
source venv/bin/activate || { echo "ERROR: Failed to activate virtual environment"; exit 1; }

if [ ! -f "forward_parsed_udp.py" ]; then
  echo "ERROR: forward_parsed_udp.py not found!"
  echo "Make sure you are running this script from the correct directory."
  deactivate || true
  exit 1
fi

echo "Starting configuration menu..."
python config.py || { echo "Server exited with error"; deactivate || true; exit 1; }

deactivate || true

exit 0
