#!/usr/bin/env python3
"""
config.py - Modern TUI configuration menu for DCS DataPad Server
Features: Grid navigation, command builder, live preview, arrow key support
"""

import json
import os
import sys
import time
import math
import subprocess
from pathlib import Path
from enum import Enum

# Windows keyboard support
if os.name == 'nt':
    import msvcrt
else:
    import select
    import tty
    import termios

CONFIG_FILE = "server_config.json"
CUSTOM_COMMANDS_FILE = "custom_commands.json"
DEFAULT_CONFIG = {
    "last_mode": 1,
    "ip_address": "192.168.178.100",
    "target_ip": "192.168.178.132",  # Supports multiple IPs (comma-separated) and wildcards (e.g., "192.168.178.*")
    "port": 5010,
    "handshake_port": 5011,
    "interval": 10,
    "authorized_devices": "authorized_devices.json",
    "enable_pow": False,
    "pow_difficulty": 16,
    "custom_args": ""
}

class Key(Enum):
    """Keyboard key codes"""
    UP = 'up'
    DOWN = 'down'
    LEFT = 'left'
    RIGHT = 'right'
    ENTER = 'enter'
    ESC = 'esc'
    BACKSPACE = 'backspace'
    TAB = 'tab'
    CHAR = 'char'
    UNKNOWN = 'unknown'

MODES = {
    1: {
        "name": "🔐 ECDH with APP + PoW",
        "description": "Secure mode with Proof-of-Work DoS protection",
        "command": "python forward_parsed_udp.py --enable-pow --pow-difficulty {pow_difficulty} --interval {interval} --host {target_ip} --port {port} --verbose --authorized-devices {authorized_devices} --bind-ip {ip_address}"
    },
    2: {
        "name": "🔐 ECDH with APP",
        "description": "Secure mode without PoW (faster handshakes)",
        "command": "python forward_parsed_udp.py --interval {interval} --host {target_ip} --port {port} --verbose --authorized-devices {authorized_devices} --bind-ip {ip_address}"
    },
    3: {
        "name": "🗺️  Map-Tools Mode",
        "description": "Repeat last data for Map Database Tools",
        "command": "python forward_parsed_udp.py --repeat-last --interval 500 --host {ip_address} --port {port} --verbose --authorized-devices {authorized_devices} --bind-ip {ip_address} --handshake-port {handshake_port}"
    },
    4: {
        "name": "⚙️  Custom Command",
        "description": "Build your own custom command line",
        "command": "custom"
    }
}

# ANSI color codes
class Color:
    RESET = '\033[0m'
    BOLD = '\033[1m'
    DIM = '\033[2m'
    
    BLACK = '\033[30m'
    RED = '\033[31m'
    GREEN = '\033[32m'
    YELLOW = '\033[33m'
    BLUE = '\033[34m'
    MAGENTA = '\033[35m'
    CYAN = '\033[36m'
    WHITE = '\033[37m'
    
    BG_BLACK = '\033[40m'
    BG_RED = '\033[41m'
    BG_GREEN = '\033[42m'
    BG_YELLOW = '\033[43m'
    BG_BLUE = '\033[44m'
    BG_MAGENTA = '\033[45m'
    BG_CYAN = '\033[46m'
    BG_WHITE = '\033[47m'


def load_config():
    """Load configuration from file or return defaults"""
    if os.path.exists(CONFIG_FILE):
        try:
            with open(CONFIG_FILE, 'r') as f:
                config = json.load(f)
                return {**DEFAULT_CONFIG, **config}
        except Exception as e:
            print(f"⚠️  Warning: Could not load config ({e}), using defaults")
    return DEFAULT_CONFIG.copy()


def save_config(config):
    """Save configuration to file"""
    try:
        with open(CONFIG_FILE, 'w') as f:
            json.dump(config, f, indent=2)
    except Exception as e:
        print(f"⚠️  Warning: Could not save config ({e})")


def load_custom_commands():
    """Load custom commands from file"""
    if os.path.exists(CUSTOM_COMMANDS_FILE):
        try:
            with open(CUSTOM_COMMANDS_FILE, 'r') as f:
                return json.load(f)
        except Exception as e:
            print(f"⚠️  Warning: Could not load custom commands ({e})")
    return {}


def save_custom_commands(custom_commands):
    """Save custom commands to file"""
    try:
        with open(CUSTOM_COMMANDS_FILE, 'w') as f:
            json.dump(custom_commands, f, indent=2)
    except Exception as e:
        print(f"⚠️  Warning: Could not save custom commands ({e})")


def clear_screen():
    """Clear console screen"""
    os.system('cls' if os.name == 'nt' else 'clear')


def get_key():
    """Get single keypress from user (cross-platform)"""
    if os.name == 'nt':  # Windows
        if msvcrt.kbhit():
            ch = msvcrt.getch()
            
            # Handle arrow keys (escape sequences)
            if ch == b'\xe0' or ch == b'\x00':  # Arrow key prefix
                ch2 = msvcrt.getch()
                if ch2 == b'H':  # Up
                    return Key.UP, None
                elif ch2 == b'P':  # Down
                    return Key.DOWN, None
                elif ch2 == b'K':  # Left
                    return Key.LEFT, None
                elif ch2 == b'M':  # Right
                    return Key.RIGHT, None
            elif ch == b'\r':  # Enter
                return Key.ENTER, None
            elif ch == b'\x1b':  # Escape
                return Key.ESC, None
            elif ch == b'\x08':  # Backspace
                return Key.BACKSPACE, None
            elif ch == b'\t':  # Tab
                return Key.TAB, None
            else:
                try:
                    return Key.CHAR, ch.decode('utf-8')
                except:
                    return Key.UNKNOWN, None
    else:  # Unix-like
        fd = sys.stdin.fileno()
        old_settings = termios.tcgetattr(fd)
        try:
            tty.setraw(fd)
            ch = sys.stdin.read(1)
            
            if ch == '\x1b':  # Escape sequence
                ch2 = sys.stdin.read(1)
                if ch2 == '[':
                    ch3 = sys.stdin.read(1)
                    if ch3 == 'A':
                        return Key.UP, None
                    elif ch3 == 'B':
                        return Key.DOWN, None
                    elif ch3 == 'C':
                        return Key.RIGHT, None
                    elif ch3 == 'D':
                        return Key.LEFT, None
                return Key.ESC, None
            elif ch == '\r' or ch == '\n':
                return Key.ENTER, None
            elif ch == '\x7f' or ch == '\x08':
                return Key.BACKSPACE, None
            elif ch == '\t':
                return Key.TAB, None
            else:
                return Key.CHAR, ch
        finally:
            termios.tcsetattr(fd, termios.TCSADRAIN, old_settings)
    
    return Key.UNKNOWN, None


def get_key_with_timeout(timeout=3.0, countdown=False, selected_mode=None):
    """Non-blocking get key with timeout. Returns (Key, char) or (None, None) on timeout.

    If countdown=True, prints a per-second countdown message (uses {selected_mode}).
    This implementation polls for keypresses without blocking (works on Windows and Unix).
    """
    # Flush any pre-existing key presses so auto-start isn't cancelled by buffered input
    if os.name == 'nt':
        try:
            while msvcrt.kbhit():
                msvcrt.getch()
        except Exception:
            pass
    else:
        # On Unix, drain stdin if data is waiting
        fd = sys.stdin.fileno()
        old_settings = termios.tcgetattr(fd)
        try:
            tty.setcbreak(fd)
            while select.select([sys.stdin], [], [], 0)[0]:
                sys.stdin.read(1)
        except Exception:
            pass
        finally:
            try:
                termios.tcsetattr(fd, termios.TCSADRAIN, old_settings)
            except Exception:
                pass

    end_time = time.time() + timeout
    last_display = None

    def _clear_countdown_line():
        if countdown:
            print('\r' + ' ' * 80 + '\r', end='', flush=True)

    while time.time() < end_time:
        # Update countdown display once per second when requested
        if countdown:
            remaining = max(0, math.ceil(end_time - time.time()))
            if remaining != last_display:
                msg = f"{Color.DIM}  Press any key to cancel auto-start (starting mode {selected_mode} in {remaining}s)...{Color.RESET}"
                # Overwrite the same terminal line and pad to clear any leftover characters
                print('\r' + msg + ' ' * 40, end='', flush=True)
                last_display = remaining

        # Check for keypress non-blocking
        if os.name == 'nt':
            if msvcrt.kbhit():
                ch = msvcrt.getch()
                # Handle arrow keys (escape sequences)
                if ch == b'\xe0' or ch == b'\x00':
                    ch2 = msvcrt.getch()
                    _clear_countdown_line()
                    if ch2 == b'H':
                        return Key.UP, None
                    elif ch2 == b'P':
                        return Key.DOWN, None
                    elif ch2 == b'K':
                        return Key.LEFT, None
                    elif ch2 == b'M':
                        return Key.RIGHT, None
                elif ch == b'\r':
                    _clear_countdown_line()
                    return Key.ENTER, None
                elif ch == b'\x1b':
                    _clear_countdown_line()
                    return Key.ESC, None
                elif ch == b'\x08':
                    _clear_countdown_line()
                    return Key.BACKSPACE, None
                elif ch == b'\t':
                    _clear_countdown_line()
                    return Key.TAB, None
                else:
                    try:
                        _clear_countdown_line()
                        return Key.CHAR, ch.decode('utf-8')
                    except Exception:
                        _clear_countdown_line()
                        return Key.UNKNOWN, None
        else:
            # Use select to see if input is available
            if select.select([sys.stdin], [], [], 0)[0]:
                fd = sys.stdin.fileno()
                old_settings = termios.tcgetattr(fd)
                try:
                    tty.setraw(fd)
                    ch = sys.stdin.read(1)
                    if ch == '\x1b':  # Escape sequence
                        # Read rest of sequence if available
                        if select.select([sys.stdin], [], [], 0.01)[0]:
                            ch2 = sys.stdin.read(1)
                            if ch2 == '[' and select.select([sys.stdin], [], [], 0.01)[0]:
                                ch3 = sys.stdin.read(1)
                                _clear_countdown_line()
                                if ch3 == 'A':
                                    return Key.UP, None
                                elif ch3 == 'B':
                                    return Key.DOWN, None
                                elif ch3 == 'C':
                                    return Key.RIGHT, None
                                elif ch3 == 'D':
                                    return Key.LEFT, None
                        _clear_countdown_line()
                        return Key.ESC, None
                    elif ch == '\r' or ch == '\n':
                        _clear_countdown_line()
                        return Key.ENTER, None
                    elif ch == '\x7f' or ch == '\x08':
                        _clear_countdown_line()
                        return Key.BACKSPACE, None
                    elif ch == '\t':
                        _clear_countdown_line()
                        return Key.TAB, None
                    else:
                        _clear_countdown_line()
                        return Key.CHAR, ch
                finally:
                    try:
                        termios.tcsetattr(fd, termios.TCSADRAIN, old_settings)
                    except Exception:
                        pass

        time.sleep(0.05)

    # Clear the countdown/message when we finish waiting
    _clear_countdown_line()

    return None, None


def print_at(x, y, text, color=''):
    """Print text at specific position"""
    sys.stdout.write(f'\033[{y};{x}H{color}{text}{Color.RESET}')
    sys.stdout.flush()


def print_box(x, y, width, height, title='', selected=False):
    """Draw a box with optional title"""
    color = Color.CYAN + Color.BOLD if selected else Color.DIM
    
    # Top border
    border = f"╔{'═' * (width - 2)}╗"
    if title:
        title_text = f" {title} "
        title_pos = (width - len(title_text)) // 2
        border = border[:title_pos] + title_text + border[title_pos + len(title_text):]
    
    print_at(x, y, border, color)
    
    # Sides
    for i in range(1, height - 1):
        print_at(x, y + i, f"║{' ' * (width - 2)}║", color)
    
    # Bottom border
    print_at(x, y + height - 1, f"╚{'═' * (width - 2)}╝", color)


def show_main_menu(config, selected_mode, all_modes):
    """Display modern main menu with grid layout"""
    clear_screen()

    # Header
    print(f"{Color.BOLD}{Color.CYAN}╔{'═' * 76}╗{Color.RESET}")
    print(f"{Color.BOLD}{Color.CYAN}║{' ' * 20}🚀 DCS DATAPAD SERVER - LAUNCHER{' ' * 23}║{Color.RESET}")
    print(f"{Color.BOLD}{Color.CYAN}╚{'═' * 76}╝{Color.RESET}")
    print()

    # Current settings bar
    print(f"{Color.DIM}┌─ Current Configuration ─────────────────────────────────────────────────┐{Color.RESET}")
    print(f"{Color.DIM}│{Color.RESET} Bind IP: {Color.GREEN}{config['ip_address']:<15}{Color.RESET} │ Target IP: {Color.GREEN}{config['target_ip']:<15}{Color.RESET} │ Port: {Color.GREEN}{config['port']:<6}{Color.RESET} {Color.DIM}│{Color.RESET}")
    print(f"{Color.DIM}└──────────────────────────────────────────────────────────────────────────┘{Color.RESET}")
    print()

    # Mode selection grid
    print(f"{Color.BOLD}  SELECT SERVER MODE:{Color.RESET}")
    print()

    for mode_id, mode_info in all_modes.items():
        selected = (mode_id == selected_mode)
        last_used = " ⭐" if mode_id == config.get('last_mode', 1) else ""
        is_custom = mode_info.get('is_custom', False)
        custom_marker = " 🔧" if is_custom else ""

        mode_name = mode_info['name'] + last_used + custom_marker
        name_len = len(mode_info['name']) + len(last_used) + len(custom_marker)

        if selected:
            print(f"  {Color.BG_CYAN}{Color.BLACK}▶ [{mode_id}] {mode_name}{' ' * (60 - name_len)}{Color.RESET}")
            print(f"      {Color.CYAN}{mode_info['description']}{Color.RESET}")
        else:
            print(f"    [{mode_id}] {mode_name}")
            print(f"      {Color.DIM}{mode_info['description']}{Color.RESET}")
        print()

    print(f"{Color.DIM}{'─' * 78}{Color.RESET}")
    print(f"  {Color.YELLOW}[C]{Color.RESET} Command Builder  {Color.YELLOW}[S]{Color.RESET} Settings  {Color.YELLOW}[D]{Color.RESET} Delete Custom  {Color.YELLOW}[Q]{Color.RESET} Quit")
    print(f"{Color.DIM}{'─' * 78}{Color.RESET}")
    print()
    print(f"{Color.DIM}  ↑↓: Navigate  ENTER: Start Server  ESC: Quit{Color.RESET}")
    print(f"{Color.DIM}  💡 Tip: Target IP supports multiple IPs (comma) or wildcards (192.168.178.*){Color.RESET}")


def update_main_selection(old_id: int, new_id: int, config: dict, all_modes: dict):
    """Update only the two mode blocks that changed (flicker-free)."""
    if old_id == new_id:
        return

    def format_block(mode_id: int, selected: bool, is_last_used: bool):
        m = all_modes[mode_id]
        last_used = " ⭐" if is_last_used else ""
        is_custom = m.get('is_custom', False)
        custom_marker = " 🔧" if is_custom else ""
        mode_name = m['name'] + last_used + custom_marker
        name_len = len(m['name']) + len(last_used) + len(custom_marker)

        if selected:
            line1 = f"  {Color.BG_CYAN}{Color.BLACK}▶ [{mode_id}] {mode_name}{' ' * (60 - name_len)}{Color.RESET}"
            line2 = f"      {Color.CYAN}{m['description']}{Color.RESET}"
        else:
            line1 = f"    [{mode_id}] {mode_name}"
            line2 = f"      {Color.DIM}{m['description']}{Color.RESET}"
        return line1, line2

    # Compute line numbers: modes start at line 11, each mode uses 3 lines
    def top_line_for(mode_id: int) -> int:
        return 11 + (mode_id - 1) * 3

    # Redraw old (deselected) - clear entire line first
    if old_id is not None and old_id in all_modes:
        t = top_line_for(old_id)
        # Clear all 3 lines completely
        print_at(1, t, ' ' * 78)
        print_at(1, t + 1, ' ' * 78)
        print_at(1, t + 2, ' ' * 78)
        # Now redraw the deselected block
        l1, l2 = format_block(old_id, selected=False, is_last_used=(old_id == config.get('last_mode', 1)))
        print_at(1, t, l1)
        print_at(1, t + 1, l2)

    # Draw new (selected) - clear entire line first
    if new_id is not None and new_id in all_modes:
        t = top_line_for(new_id)
        # Clear all 3 lines completely
        print_at(1, t, ' ' * 78)
        print_at(1, t + 1, ' ' * 78)
        print_at(1, t + 2, ' ' * 78)
        # Now draw the selected block
        l1, l2 = format_block(new_id, selected=True, is_last_used=(new_id == config.get('last_mode', 1)))
        print_at(1, t, l1)
        print_at(1, t + 1, l2)


def edit_setting_inline(prompt, current_value, validator=None):
    """Edit a setting value inline with live feedback"""
    clear_screen()
    print(f"\n{prompt}")
    print(f"{Color.DIM}Current: {Color.GREEN}{current_value}{Color.RESET}")
    print(f"\n{Color.DIM}New value (Enter to keep current): {Color.RESET}", end='', flush=True)
    
    user_input = ""
    while True:
        key, char = get_key()
        
        if key == Key.ENTER:
            print()
            if user_input.strip():
                if validator:
                    if validator(user_input.strip()):
                        return user_input.strip()
                    else:
                        print(f"{Color.RED}❌ Invalid value!{Color.RESET}")
                        time.sleep(1)
                        return current_value
                return user_input.strip()
            return current_value
        elif key == Key.ESC:
            print()
            return current_value
        elif key == Key.BACKSPACE:
            if user_input:
                user_input = user_input[:-1]
                print('\b \b', end='', flush=True)
        elif key == Key.CHAR and char:
            user_input += char
            print(char, end='', flush=True)


def show_settings_menu(config):
    """Interactive settings editor with grid navigation (flicker-free)"""
    settings_items = [
        ("bind_ip", "Bind IP (Server - single IP only)", "ip_address", lambda x: True),
        ("target_ip", "Target IPs (comma/wildcard: 192.168.*)", "target_ip", lambda x: True),
        ("port", "Data Port", "port", lambda x: x.isdigit() and 1 <= int(x) <= 65535),
        ("handshake_port", "Handshake Port", "handshake_port", lambda x: x.isdigit() and 1 <= int(x) <= 65535),
        ("interval", "Update Interval (ms)", "interval", lambda x: x.isdigit() and int(x) > 0),
        ("pow", "Enable PoW Protection", "enable_pow", lambda x: x.lower() in ['yes', 'no', 'y', 'n']),
        ("pow_diff", "PoW Difficulty (bits)", "pow_difficulty", lambda x: x.isdigit() and 8 <= int(x) <= 24),
        ("auth_file", "Authorized Devices File", "authorized_devices", lambda x: True),
    ]
    
    selected_idx = 0
    last_selected = -1
    needs_full_redraw = True
    
    # Initial draw
    clear_screen()
    
    while True:
        # Full redraw only when necessary
        if needs_full_redraw:
            clear_screen()
            print(f"{Color.BOLD}{Color.MAGENTA}╔{'═' * 76}╗{Color.RESET}")
            print(f"{Color.BOLD}{Color.MAGENTA}║{' ' * 28}⚙️  SETTINGS{' ' * 35}║{Color.RESET}")
            print(f"{Color.BOLD}{Color.MAGENTA}╚{'═' * 76}╝{Color.RESET}")
            print()
            
            for idx, (key, label, config_key, validator) in enumerate(settings_items):
                value = config[config_key]
                selected = (idx == selected_idx)
                
                if selected:
                    print(f"  {Color.BG_MAGENTA}{Color.BLACK}▶ {label:<30} {Color.RESET} {Color.YELLOW}{value}{Color.RESET}")
                else:
                    print(f"    {label:<30} {Color.DIM}{value}{Color.RESET}")
            
            print()
            print(f"{Color.DIM}{'─' * 78}{Color.RESET}")
            print(f"{Color.DIM}  ↑↓: Navigate  ENTER: Edit  ESC: Back{Color.RESET}")
            
            last_selected = selected_idx
            needs_full_redraw = False
        else:
            # Partial update - only redraw changed lines
            if last_selected != selected_idx:
                # Redraw old selection (deselect)
                if last_selected >= 0:
                    _, label, config_key, _ = settings_items[last_selected]
                    value = config[config_key]
                    line_num = 5 + last_selected  # Header is 4 lines
                    print_at(1, line_num, f"    {label:<30} {Color.DIM}{value}{Color.RESET}" + " " * 20)
                
                # Redraw new selection
                _, label, config_key, _ = settings_items[selected_idx]
                value = config[config_key]
                line_num = 5 + selected_idx
                print_at(1, line_num, f"  {Color.BG_MAGENTA}{Color.BLACK}▶ {label:<30} {Color.RESET} {Color.YELLOW}{value}{Color.RESET}" + " " * 10)
                
                last_selected = selected_idx
        
        key, char = get_key()
        
        if key == Key.UP:
            selected_idx = (selected_idx - 1) % len(settings_items)
        elif key == Key.DOWN:
            selected_idx = (selected_idx + 1) % len(settings_items)
        elif key == Key.ENTER:
            _, label, config_key, validator = settings_items[selected_idx]
            
            if config_key == "enable_pow":
                # Toggle PoW
                config[config_key] = not config.get(config_key, False)
                save_config(config)
                needs_full_redraw = True
            else:
                new_value = edit_setting_inline(
                    f"\n{Color.BOLD}Edit: {label}{Color.RESET}",
                    config[config_key],
                    validator
                )
                
                if config_key in ["port", "handshake_port", "interval", "pow_difficulty"]:
                    new_value = int(new_value)
                
                config[config_key] = new_value
                save_config(config)
                needs_full_redraw = True
        elif key == Key.ESC or (key == Key.CHAR and char and char.upper() == 'Q'):
            return config


def show_command_builder(config, existing_state=None, existing_name=None):
    """Interactive modular command builder - build custom commands with options

    Args:
        config: Main configuration dict
        existing_state: Optional existing builder state to load (for editing)
        existing_name: Optional existing command name (for editing)
    """
    # Initialize builder state with config defaults or existing state
    if existing_state:
        builder_state = existing_state.copy()
    else:
        builder_state = {
            "enable_pow": config.get("enable_pow", False),
            "pow_difficulty": config.get("pow_difficulty", 16),
            "repeat_last": False,
            "verbose": True,
            "interval": config.get("interval", 10),
            "host": config.get("target_ip", "192.168.178.132"),
            "port": config.get("port", 5010),
            "bind_ip": config.get("ip_address", "192.168.178.100"),
            "handshake_port": config.get("handshake_port", 5011),
            "authorized_devices": config.get("authorized_devices", "authorized_devices.json"),
            "extra_args": ""
        }

    # Define builder items: (key, label, type, options)
    # type: 'bool' for toggles, 'value' for editable values
    builder_items = [
        ("enable_pow", "Enable Proof-of-Work", "bool", None),
        ("pow_difficulty", "  └─ PoW Difficulty (bits)", "value", lambda x: x.isdigit() and 8 <= int(x) <= 24),
        ("repeat_last", "Repeat Last Data (Map mode)", "bool", None),
        ("verbose", "Verbose Logging", "bool", None),
        ("separator1", "PARAMETERS", "separator", None),
        ("host", "Target Host/IP", "value", lambda x: True),
        ("port", "Data Port", "value", lambda x: x.isdigit() and 1 <= int(x) <= 65535),
        ("bind_ip", "Bind IP Address", "value", lambda x: True),
        ("handshake_port", "Handshake Port", "value", lambda x: x.isdigit() and 1 <= int(x) <= 65535),
        ("interval", "Update Interval (ms)", "value", lambda x: x.isdigit() and int(x) > 0),
        ("authorized_devices", "Authorized Devices File", "value", lambda x: True),
        ("separator2", "ADVANCED", "separator", None),
        ("extra_args", "Extra Arguments", "value", lambda x: True),
    ]

    selected_idx = 0
    needs_full_redraw = True

    def build_command():
        """Build command string from current state"""
        cmd_parts = ["python forward_parsed_udp.py"]

        # Add flags
        if builder_state["enable_pow"]:
            cmd_parts.append("--enable-pow")
            cmd_parts.append(f"--pow-difficulty {builder_state['pow_difficulty']}")

        if builder_state["repeat_last"]:
            cmd_parts.append("--repeat-last")

        if builder_state["verbose"]:
            cmd_parts.append("--verbose")

        # Add parameters
        cmd_parts.append(f"--host {builder_state['host']}")
        cmd_parts.append(f"--port {builder_state['port']}")
        cmd_parts.append(f"--bind-ip {builder_state['bind_ip']}")
        cmd_parts.append(f"--handshake-port {builder_state['handshake_port']}")
        cmd_parts.append(f"--interval {builder_state['interval']}")
        cmd_parts.append(f"--authorized-devices {builder_state['authorized_devices']}")

        # Add extra args if any
        if builder_state["extra_args"].strip():
            cmd_parts.append(builder_state["extra_args"].strip())

        return " ".join(cmd_parts)

    def draw_builder_item(idx, item_key, label, item_type, is_selected):
        """Draw a single builder item"""
        if item_type == "separator":
            return f"{Color.DIM}{'─' * 30} {label} {'─' * (44 - len(label))}{Color.RESET}"
        elif item_type == "bool":
            value = builder_state[item_key]
            status = f"{Color.GREEN}[✓]{Color.RESET}" if value else f"{Color.DIM}[ ]{Color.RESET}"
            if is_selected:
                return f"  {Color.BG_GREEN}{Color.BLACK}▶ {status} {label}{' ' * 40}{Color.RESET}"
            else:
                return f"    {status} {label}"
        elif item_type == "value":
            value = builder_state[item_key]
            # Skip pow_difficulty if pow is disabled
            if item_key == "pow_difficulty" and not builder_state["enable_pow"]:
                return f"{Color.DIM}    {label}: {value} (disabled){Color.RESET}"

            if is_selected:
                return f"  {Color.BG_GREEN}{Color.BLACK}▶ {label}{' ' * 20}{Color.RESET} {Color.YELLOW}{value}{Color.RESET}"
            else:
                return f"    {label}: {Color.CYAN}{value}{Color.RESET}"

    while True:
        if needs_full_redraw:
            clear_screen()
            print(f"{Color.BOLD}{Color.GREEN}╔{'═' * 76}╗{Color.RESET}")
            print(f"{Color.BOLD}{Color.GREEN}║{' ' * 25}⚙️  COMMAND BUILDER{' ' * 32}║{Color.RESET}")
            print(f"{Color.BOLD}{Color.GREEN}╚{'═' * 76}╝{Color.RESET}")
            print()
            print(f"{Color.BOLD}Build your custom command - Toggle options and edit values:{Color.RESET}")
            print()

            # Draw all items
            for idx, (item_key, label, item_type, _) in enumerate(builder_items):
                is_selected = (idx == selected_idx)
                line = draw_builder_item(idx, item_key, label, item_type, is_selected)
                print(line)

            print()
            print(f"{Color.YELLOW}{'─' * 78}{Color.RESET}")
            print(f"{Color.BOLD}Generated Command:{Color.RESET}")
            cmd = build_command()
            print(f"{Color.GREEN}{cmd}{Color.RESET}")
            print(f"{Color.YELLOW}{'─' * 78}{Color.RESET}")
            print()
            print(f"{Color.DIM}  ↑↓: Navigate  ENTER: Edit/Toggle  [R]: Run  [S]: Save  ESC: Back{Color.RESET}")

            needs_full_redraw = False

        key, char = get_key()

        if key == Key.UP:
            # Skip separators
            selected_idx = (selected_idx - 1) % len(builder_items)
            while builder_items[selected_idx][2] == "separator":
                selected_idx = (selected_idx - 1) % len(builder_items)
            needs_full_redraw = True
        elif key == Key.DOWN:
            # Skip separators
            selected_idx = (selected_idx + 1) % len(builder_items)
            while builder_items[selected_idx][2] == "separator":
                selected_idx = (selected_idx + 1) % len(builder_items)
            needs_full_redraw = True
        elif key == Key.ENTER:
            item_key, label, item_type, validator = builder_items[selected_idx]

            if item_type == "bool":
                # Toggle boolean value
                builder_state[item_key] = not builder_state[item_key]
                needs_full_redraw = True
            elif item_type == "value":
                # Skip editing pow_difficulty if pow is disabled
                if item_key == "pow_difficulty" and not builder_state["enable_pow"]:
                    continue

                # Edit value
                new_value = edit_setting_inline(
                    f"\n{Color.BOLD}Edit: {label}{Color.RESET}",
                    str(builder_state[item_key]),
                    validator
                )

                # Convert to int if needed
                if item_key in ["port", "handshake_port", "interval", "pow_difficulty"]:
                    new_value = int(new_value)

                builder_state[item_key] = new_value
                needs_full_redraw = True
        elif key == Key.CHAR and char:
            ch_upper = char.upper()
            if ch_upper == 'R':
                # Run the command
                cmd = build_command()
                clear_screen()
                print(f"{Color.BOLD}{Color.GREEN}{'═' * 78}{Color.RESET}")
                print(f"{Color.BOLD}{Color.GREEN}🚀 Starting Custom Command{Color.RESET}")
                print(f"{Color.BOLD}{Color.GREEN}{'═' * 78}{Color.RESET}")
                print()
                print(f"{Color.DIM}Command:{Color.RESET} {Color.CYAN}{cmd}{Color.RESET}")
                print()
                print(f"{Color.YELLOW}Press Ctrl+C to stop the server{Color.RESET}")
                print(f"{Color.GREEN}{'═' * 78}{Color.RESET}")
                print()

                try:
                    subprocess.run(cmd, shell=True, check=True)
                except KeyboardInterrupt:
                    print(f"\n\n{Color.YELLOW}⏹️  Server stopped by user{Color.RESET}")
                    time.sleep(1)
                except subprocess.CalledProcessError as e:
                    print(f"\n{Color.RED}❌ Server exited with error code {e.returncode}{Color.RESET}")
                    time.sleep(2)

                print(f"\n{Color.DIM}Press any key to return to builder...{Color.RESET}")
                get_key()
                needs_full_redraw = True
            elif ch_upper == 'S':
                # Save custom command
                default_name = existing_name if existing_name else ""
                prompt_text = f"\n{Color.BOLD}{'Update' if existing_name else 'Save'} Custom Command{Color.RESET}"

                cmd_name = edit_setting_inline(
                    prompt_text,
                    default_name,
                    lambda x: len(x.strip()) > 0
                )

                if cmd_name.strip():
                    # Load existing custom commands
                    custom_commands = load_custom_commands()

                    # If we're editing and the name changed, delete the old entry
                    if existing_name and existing_name != cmd_name and existing_name in custom_commands:
                        del custom_commands[existing_name]

                    # Create/update command entry
                    custom_commands[cmd_name] = {
                        "state": builder_state.copy(),
                        "command": build_command()
                    }

                    # Save to file
                    save_custom_commands(custom_commands)

                    clear_screen()
                    action = "updated" if existing_name else "saved"
                    print(f"\n{Color.GREEN}✓ Custom command '{cmd_name}' {action} successfully!{Color.RESET}")
                    print(f"{Color.DIM}You can now select it from the main menu.{Color.RESET}")
                    print(f"\n{Color.DIM}Press any key to continue...{Color.RESET}")
                    get_key()

                needs_full_redraw = True
            elif ch_upper == 'Q':
                return
        elif key == Key.ESC:
            return


def build_command(mode_id, config, all_modes):
    """Build command line for selected mode"""
    if mode_id not in all_modes:
        return None

    mode = all_modes[mode_id]

    # If it's a saved custom command, return the stored command
    if mode.get('is_custom', False):
        return mode.get('command', None)

    if mode['command'] == "custom":
        return show_command_builder(config)

    # Expand target_ip to handle multiple IPs or wildcards
    target_ip_str = config['target_ip']
    # Support comma-separated IPs or single IP/wildcard
    target_ips = [ip.strip() for ip in target_ip_str.split(',')]

    # Build command with expanded IPs
    # For now, we'll use the first target_ip in the template format,
    # then replace it with proper --host arguments in the final command
    base_cmd = mode['command'].format(
        ip_address=config['ip_address'],
        target_ip=target_ips[0],  # Use first IP for template
        port=config['port'],
        handshake_port=config['handshake_port'],
        interval=config['interval'],
        authorized_devices=config['authorized_devices'],
        pow_difficulty=config.get('pow_difficulty', 16)
    )

    # Replace single --host argument with multiple if needed
    if len(target_ips) > 1:
        # Find --host argument and replace with multiple --host arguments
        import re
        base_cmd = re.sub(r'--host \S+', lambda m: ' '.join(f'--host {ip}' for ip in target_ips), base_cmd)

    return base_cmd


def get_all_modes():
    """Combine standard modes with custom commands"""
    all_modes = MODES.copy()

    # Load custom commands
    custom_commands = load_custom_commands()

    # Add custom commands starting from mode 5
    next_id = max(all_modes.keys()) + 1
    for name, data in custom_commands.items():
        all_modes[next_id] = {
            "name": name,
            "description": "Custom saved command",
            "command": data.get("command", ""),
            "is_custom": True,
            "custom_name": name
        }
        next_id += 1

    return all_modes


def show_custom_command_menu(config, mode_id, mode_info, all_modes):
    """Show action menu for a custom command: Start, Edit, Delete, Cancel

    Returns: 'start', 'edit', 'delete', or None
    """
    options = [
        ("start", "▶  Start Command", "Run this custom command"),
        ("edit", "✏  Edit Command", "Modify settings in Command Builder"),
        ("delete", "🗑  Delete Command", "Remove this custom command"),
        ("cancel", "← Cancel", "Return to main menu"),
    ]

    selected_idx = 0
    last_selected = -1

    # Initial full draw
    clear_screen()
    print(f"{Color.BOLD}{Color.CYAN}╔{'═' * 76}╗{Color.RESET}")
    print(f"{Color.BOLD}{Color.CYAN}║{' ' * 26}CUSTOM COMMAND ACTIONS{' ' * 27}║{Color.RESET}")
    print(f"{Color.BOLD}{Color.CYAN}╚{'═' * 76}╝{Color.RESET}")
    print()
    print(f"{Color.BOLD}Command: {Color.GREEN}{mode_info['name']}{Color.RESET}")
    print(f"{Color.DIM}Command line: {mode_info['command']}{Color.RESET}")
    print()
    print(f"{Color.BOLD}What would you like to do?{Color.RESET}")
    print()

    # Draw options initially
    for idx, (action, label, desc) in enumerate(options):
        if idx == selected_idx:
            print(f"  {Color.BG_CYAN}{Color.BLACK}▶ {label}{' ' * 40}{Color.RESET}")
            print(f"      {Color.CYAN}{desc}{Color.RESET}")
        else:
            print(f"    {label}")
            print(f"      {Color.DIM}{desc}{Color.RESET}")
        print()

    print(f"{Color.DIM}{'─' * 78}{Color.RESET}")
    print(f"{Color.DIM}  ↑↓: Navigate  ENTER: Select  ESC: Cancel{Color.RESET}")

    while True:
        # Update only changed options (flicker-free)
        if last_selected != selected_idx and last_selected >= 0:
            # Redraw old selection (deselected) - line starts at 10 + (option_idx * 3)
            old_y = 10 + (last_selected * 3)
            _, old_label, old_desc = options[last_selected]
            # Clear lines completely first
            print_at(1, old_y, " " * 78)
            print_at(1, old_y + 1, " " * 78)
            print_at(1, old_y + 2, " " * 78)
            # Then redraw
            print_at(1, old_y, f"    {old_label}")
            print_at(1, old_y + 1, f"      {Color.DIM}{old_desc}{Color.RESET}")

            # Redraw new selection (selected)
            new_y = 10 + (selected_idx * 3)
            _, new_label, new_desc = options[selected_idx]
            # Clear lines completely first
            print_at(1, new_y, " " * 78)
            print_at(1, new_y + 1, " " * 78)
            print_at(1, new_y + 2, " " * 78)
            # Then redraw
            print_at(1, new_y, f"  {Color.BG_CYAN}{Color.BLACK}▶ {new_label}{' ' * 40}{Color.RESET}")
            print_at(1, new_y + 1, f"      {Color.CYAN}{new_desc}{Color.RESET}")

            last_selected = selected_idx

        key, char = get_key()

        if key == Key.UP:
            last_selected = selected_idx
            selected_idx = (selected_idx - 1) % len(options)
        elif key == Key.DOWN:
            last_selected = selected_idx
            selected_idx = (selected_idx + 1) % len(options)
        elif key == Key.ENTER:
            action, _, _ = options[selected_idx]
            if action == "cancel":
                return None
            elif action == "delete":
                # Confirm deletion
                clear_screen()
                print(f"\n{Color.YELLOW}Are you sure you want to delete '{mode_info['name']}'?{Color.RESET}")
                print(f"{Color.DIM}This action cannot be undone.{Color.RESET}")
                print(f"\n{Color.BOLD}Type 'yes' to confirm: {Color.RESET}", end='', flush=True)

                confirm_input = ""
                while True:
                    k, c = get_key()
                    if k == Key.ENTER:
                        print()
                        if confirm_input.lower() == "yes":
                            # Delete the command
                            custom_commands = load_custom_commands()
                            cmd_name = mode_info['custom_name']
                            if cmd_name in custom_commands:
                                del custom_commands[cmd_name]
                                save_custom_commands(custom_commands)
                                print(f"\n{Color.GREEN}✓ Custom command '{cmd_name}' deleted.{Color.RESET}")
                            else:
                                print(f"\n{Color.RED}❌ Command not found.{Color.RESET}")
                        else:
                            print(f"\n{Color.YELLOW}Deletion cancelled.{Color.RESET}")
                        print(f"\n{Color.DIM}Press any key to continue...{Color.RESET}")
                        get_key()
                        return 'delete'
                    elif k == Key.ESC:
                        print()
                        return None
                    elif k == Key.BACKSPACE:
                        if confirm_input:
                            confirm_input = confirm_input[:-1]
                            print('\b \b', end='', flush=True)
                    elif k == Key.CHAR and c:
                        confirm_input += c
                        print(c, end='', flush=True)
            else:
                return action
        elif key == Key.ESC:
            return None


def delete_custom_command(config, all_modes):
    """Delete a custom command"""
    # Find all custom commands
    custom_modes = {mid: mode for mid, mode in all_modes.items() if mode.get('is_custom', False)}

    if not custom_modes:
        clear_screen()
        print(f"\n{Color.YELLOW}No custom commands to delete.{Color.RESET}")
        print(f"\n{Color.DIM}Press any key to continue...{Color.RESET}")
        get_key()
        return

    # Show selection menu
    clear_screen()
    print(f"{Color.BOLD}{Color.RED}╔{'═' * 76}╗{Color.RESET}")
    print(f"{Color.BOLD}{Color.RED}║{' ' * 26}DELETE CUSTOM COMMAND{' ' * 29}║{Color.RESET}")
    print(f"{Color.BOLD}{Color.RED}╚{'═' * 76}╝{Color.RESET}")
    print()
    print(f"{Color.BOLD}Select a custom command to delete:{Color.RESET}")
    print()

    mode_list = list(custom_modes.items())
    for idx, (mode_id, mode_info) in enumerate(mode_list, 1):
        print(f"  [{idx}] {mode_info['name']}")
        print(f"      {Color.DIM}{mode_info['description']}{Color.RESET}")
        print()

    print(f"{Color.DIM}{'─' * 78}{Color.RESET}")
    print(f"{Color.DIM}Enter number to delete, or ESC to cancel{Color.RESET}")
    print()
    print(f"Delete: ", end='', flush=True)

    user_input = ""
    while True:
        key, char = get_key()

        if key == Key.ENTER:
            print()
            if user_input.strip().isdigit():
                idx = int(user_input.strip())
                if 1 <= idx <= len(mode_list):
                    mode_id, mode_info = mode_list[idx - 1]
                    cmd_name = mode_info['custom_name']

                    # Confirm deletion
                    print(f"\n{Color.YELLOW}Are you sure you want to delete '{cmd_name}'? (y/N): {Color.RESET}", end='', flush=True)
                    confirm = ""
                    while True:
                        k, c = get_key()
                        if k == Key.CHAR and c:
                            confirm = c
                            print(c)
                            break
                        elif k == Key.ENTER:
                            print()
                            break
                        elif k == Key.ESC:
                            print()
                            return

                    if confirm.lower() == 'y':
                        # Load and delete
                        custom_commands = load_custom_commands()
                        if cmd_name in custom_commands:
                            del custom_commands[cmd_name]
                            save_custom_commands(custom_commands)
                            print(f"\n{Color.GREEN}✓ Custom command '{cmd_name}' deleted.{Color.RESET}")
                        else:
                            print(f"\n{Color.RED}❌ Command not found.{Color.RESET}")
                    else:
                        print(f"\n{Color.YELLOW}Deletion cancelled.{Color.RESET}")
                else:
                    print(f"{Color.RED}Invalid selection.{Color.RESET}")
            else:
                print(f"{Color.RED}Invalid input.{Color.RESET}")

            print(f"\n{Color.DIM}Press any key to continue...{Color.RESET}")
            get_key()
            return
        elif key == Key.ESC:
            print()
            return
        elif key == Key.BACKSPACE:
            if user_input:
                user_input = user_input[:-1]
                print('\b \b', end='', flush=True)
        elif key == Key.CHAR and char and char.isdigit():
            user_input += char
            print(char, end='', flush=True)


def main():
    """Main application loop with modern TUI (flicker-free)"""
    config = load_config()
    selected_mode = config.get('last_mode', 1)
    last_selected = -1
    needs_redraw = True
    # When True, suppress the automatic countdown/start for one menu cycle
    skip_auto_start = False
    # Track number of modes to detect changes
    last_mode_count = 0

    while True:
        # Reload all modes (to pick up new custom commands)
        all_modes = get_all_modes()

        # Check if number of modes changed (custom commands added/deleted)
        current_mode_count = len(all_modes)
        if current_mode_count != last_mode_count:
            needs_redraw = True
            last_mode_count = current_mode_count

        # Validate selected mode is still valid
        if selected_mode not in all_modes:
            selected_mode = 1

        # Only redraw when necessary
        if needs_redraw or last_selected != selected_mode:
            show_main_menu(config, selected_mode, all_modes)
            last_selected = selected_mode
            needs_redraw = False

        # If a run just finished, skip auto-start and wait for explicit user input
        if skip_auto_start:
            skip_auto_start = False
            # Wait for user keypress without countdown
            while True:
                key, char = get_key()
                if key != Key.UNKNOWN:
                    break
                time.sleep(0.05)
        else:
            # Auto-start with countdown and non-blocking input check
            key, char = get_key_with_timeout(3.0, countdown=True, selected_mode=selected_mode)

            # If timeout occurred, auto-start the selected mode
            if key is None:
                key = Key.ENTER
                # Record that we auto-started so we don't immediately auto-start again upon returning
                skip_auto_start = True
        # If navigation key pressed, cancel auto-start and enter interactive mode
        if key in (Key.UP, Key.DOWN, Key.LEFT, Key.RIGHT, Key.CHAR):
            # Enter interactive navigation mode until user presses ENTER/ESC/Q
            interactive = True
            needs_redraw = True
            # Initial draw for interactive mode (single full draw)
            show_main_menu(config, selected_mode, all_modes)
            last_interactive = selected_mode
            interactive_mode_count = len(all_modes)
            while interactive:
                k, ch = get_key()
                if k == Key.UP:
                    # Navigate up through available modes
                    mode_ids = sorted(all_modes.keys())
                    current_idx = mode_ids.index(selected_mode)
                    new_idx = (current_idx - 1) % len(mode_ids)
                    new_sel = mode_ids[new_idx]
                    if new_sel != selected_mode:
                        # Always do full redraw for stability
                        show_main_menu(config, new_sel, all_modes)
                        selected_mode = new_sel
                elif k == Key.DOWN:
                    # Navigate down through available modes
                    mode_ids = sorted(all_modes.keys())
                    current_idx = mode_ids.index(selected_mode)
                    new_idx = (current_idx + 1) % len(mode_ids)
                    new_sel = mode_ids[new_idx]
                    if new_sel != selected_mode:
                        # Always do full redraw for stability
                        show_main_menu(config, new_sel, all_modes)
                        selected_mode = new_sel
                elif k == Key.ENTER:
                    # Check if this is a custom command
                    if all_modes[selected_mode].get('is_custom', False):
                        # Show custom command menu
                        action = show_custom_command_menu(config, selected_mode, all_modes[selected_mode], all_modes)

                        # Reload modes after any action (edit/delete changes the list)
                        all_modes = get_all_modes()
                        interactive_mode_count = len(all_modes)

                        # Validate selected mode still exists (in case of delete)
                        if selected_mode not in all_modes:
                            selected_mode = 1

                        if action == 'start':
                            # Start the command
                            config['last_mode'] = selected_mode
                            save_config(config)
                            command = build_command(selected_mode, config, all_modes)
                            if command:
                                clear_screen()
                                print(f"{Color.BOLD}{Color.GREEN}{'═' * 78}{Color.RESET}")
                                print(f"{Color.BOLD}{Color.GREEN}🚀 Starting: {all_modes[selected_mode]['name']}{Color.RESET}")
                                print(f"{Color.BOLD}{Color.GREEN}{'═' * 78}{Color.RESET}")
                                print()
                                print(f"{Color.DIM}Command:{Color.RESET} {Color.CYAN}{command}{Color.RESET}")
                                print()
                                print(f"{Color.YELLOW}Press Ctrl+C to stop the server{Color.RESET}")
                                print(f"{Color.GREEN}{'═' * 78}{Color.RESET}")
                                print()
                                try:
                                    subprocess.run(command, shell=True, check=True)
                                except KeyboardInterrupt:
                                    print(f"\n\n{Color.YELLOW}⏹️  Server stopped by user{Color.RESET}")
                                    time.sleep(1)
                                except subprocess.CalledProcessError as e:
                                    print(f"\n{Color.RED}❌ Server exited with error code {e.returncode}{Color.RESET}")
                                    time.sleep(2)
                                print(f"\n{Color.DIM}Press any key to return to menu...{Color.RESET}")
                                get_key()
                        elif action == 'edit':
                            # Edit the command - load state and open builder
                            custom_commands = load_custom_commands()
                            cmd_name = all_modes[selected_mode]['custom_name']
                            if cmd_name in custom_commands:
                                existing_state = custom_commands[cmd_name].get('state', None)
                                show_command_builder(config, existing_state=existing_state, existing_name=cmd_name)
                        elif action == 'delete':
                            # Command was deleted
                            pass

                        needs_redraw = True
                        interactive = False
                    else:
                        # User confirmed selection - launch (standard mode)
                        config['last_mode'] = selected_mode
                        save_config(config)
                        command = build_command(selected_mode, config, all_modes)
                        if command:
                            clear_screen()
                            print(f"{Color.BOLD}{Color.GREEN}{'═' * 78}{Color.RESET}")
                            print(f"{Color.BOLD}{Color.GREEN}🚀 Starting: {all_modes[selected_mode]['name']}{Color.RESET}")
                            print(f"{Color.BOLD}{Color.GREEN}{'═' * 78}{Color.RESET}")
                            print()
                            print(f"{Color.DIM}Command:{Color.RESET} {Color.CYAN}{command}{Color.RESET}")
                            print()
                            print(f"{Color.YELLOW}Press Ctrl+C to stop the server{Color.RESET}")
                            print(f"{Color.GREEN}{'═' * 78}{Color.RESET}")
                            print()
                            try:
                                subprocess.run(command, shell=True, check=True)
                            except KeyboardInterrupt:
                                print(f"\n\n{Color.YELLOW}⏹️  Server stopped by user{Color.RESET}")
                                time.sleep(1)
                            except subprocess.CalledProcessError as e:
                                print(f"\n{Color.RED}❌ Server exited with error code {e.returncode}{Color.RESET}")
                                time.sleep(2)
                            print(f"\n{Color.DIM}Press any key to return to menu...{Color.RESET}")
                            get_key()
                            needs_redraw = True
                        interactive = False
                elif k == Key.CHAR and ch:
                    ch_upper = ch.upper()
                    if ch_upper == 'Q':
                        clear_screen()
                        print(f"\n{Color.CYAN}👋 Goodbye!{Color.RESET}\n")
                        save_config(config)
                        sys.exit(0)
                    elif ch_upper == 'S':
                        config = show_settings_menu(config)
                        needs_redraw = True
                        interactive = False
                    elif ch_upper == 'C':
                        show_command_builder(config)
                        needs_redraw = True
                        interactive = False
                    elif ch_upper == 'D':
                        delete_custom_command(config, all_modes)
                        needs_redraw = True
                        interactive = False
                    elif ch.isdigit():
                        mode_num = int(ch)
                        if mode_num in all_modes:
                            new_sel = mode_num
                            update_main_selection(selected_mode, new_sel, config, all_modes)
                            selected_mode = new_sel
                elif k == Key.ESC:
                    clear_screen()
                    print(f"\n{Color.CYAN}👋 Goodbye!{Color.RESET}\n")
                    save_config(config)
                    sys.exit(0)
                else:
                    # No relevant key - small sleep
                    time.sleep(0.05)
        elif key == Key.ENTER:
            # Check if this is a custom command
            if all_modes[selected_mode].get('is_custom', False):
                # Show custom command menu
                action = show_custom_command_menu(config, selected_mode, all_modes[selected_mode], all_modes)

                # Reload modes after any action (edit/delete changes the list)
                all_modes = get_all_modes()

                # Validate selected mode still exists (in case of delete)
                if selected_mode not in all_modes:
                    selected_mode = 1

                if action == 'start':
                    # Start the command
                    config['last_mode'] = selected_mode
                    save_config(config)
                    command = build_command(selected_mode, config, all_modes)
                    if command:
                        clear_screen()
                        print(f"{Color.BOLD}{Color.GREEN}{'═' * 78}{Color.RESET}")
                        print(f"{Color.BOLD}{Color.GREEN}🚀 Starting: {all_modes[selected_mode]['name']}{Color.RESET}")
                        print(f"{Color.BOLD}{Color.GREEN}{'═' * 78}{Color.RESET}")
                        print()
                        print(f"{Color.DIM}Command:{Color.RESET} {Color.CYAN}{command}{Color.RESET}")
                        print()
                        print(f"{Color.YELLOW}Press Ctrl+C to stop the server{Color.RESET}")
                        print(f"{Color.GREEN}{'═' * 78}{Color.RESET}")
                        print()

                        try:
                            subprocess.run(command, shell=True, check=True)
                        except KeyboardInterrupt:
                            print(f"\n\n{Color.YELLOW}⏹️  Server stopped by user{Color.RESET}")
                            time.sleep(1)
                        except subprocess.CalledProcessError as e:
                            print(f"\n{Color.RED}❌ Server exited with error code {e.returncode}{Color.RESET}")
                            time.sleep(2)

                        print(f"\n{Color.DIM}Press any key to return to menu...{Color.RESET}")
                        get_key()
                elif action == 'edit':
                    # Edit the command - load state and open builder
                    custom_commands = load_custom_commands()
                    cmd_name = all_modes[selected_mode]['custom_name']
                    if cmd_name in custom_commands:
                        existing_state = custom_commands[cmd_name].get('state', None)
                        show_command_builder(config, existing_state=existing_state, existing_name=cmd_name)
                elif action == 'delete':
                    # Command was deleted
                    pass

                needs_redraw = True
            else:
                # Standard mode
                config['last_mode'] = selected_mode
                save_config(config)

                command = build_command(selected_mode, config, all_modes)
                if command:
                    clear_screen()
                    print(f"{Color.BOLD}{Color.GREEN}{'═' * 78}{Color.RESET}")
                    print(f"{Color.BOLD}{Color.GREEN}🚀 Starting: {all_modes[selected_mode]['name']}{Color.RESET}")
                    print(f"{Color.BOLD}{Color.GREEN}{'═' * 78}{Color.RESET}")
                    print()
                    print(f"{Color.DIM}Command:{Color.RESET} {Color.CYAN}{command}{Color.RESET}")
                    print()
                    print(f"{Color.YELLOW}Press Ctrl+C to stop the server{Color.RESET}")
                    print(f"{Color.GREEN}{'═' * 78}{Color.RESET}")
                    print()

                    try:
                        subprocess.run(command, shell=True, check=True)
                    except KeyboardInterrupt:
                        print(f"\n\n{Color.YELLOW}⏹️  Server stopped by user{Color.RESET}")
                        time.sleep(1)
                    except subprocess.CalledProcessError as e:
                        print(f"\n{Color.RED}❌ Server exited with error code {e.returncode}{Color.RESET}")
                        time.sleep(2)

                    print(f"\n{Color.DIM}Press any key to return to menu...{Color.RESET}")
                    get_key()
                    needs_redraw = True
        elif key == Key.CHAR and char:
            ch_upper = char.upper()
            if ch_upper == 'Q':
                clear_screen()
                print(f"\n{Color.CYAN}👋 Goodbye!{Color.RESET}\n")
                save_config(config)
                sys.exit(0)
            elif ch_upper == 'S':
                config = show_settings_menu(config)
                needs_redraw = True
            elif ch_upper == 'C':
                show_command_builder(config)
                needs_redraw = True
            elif ch_upper == 'D':
                delete_custom_command(config, all_modes)
                needs_redraw = True
            elif char.isdigit():
                mode_num = int(char)
                if mode_num in all_modes:
                    selected_mode = mode_num
        elif key == Key.ESC:
            clear_screen()
            print(f"\n{Color.CYAN}👋 Goodbye!{Color.RESET}\n")
            save_config(config)
            sys.exit(0)


if __name__ == '__main__':
    try:
        # Enable ANSI colors on Windows
        if os.name == 'nt':
            os.system('')
        
        main()
    except KeyboardInterrupt:
        clear_screen()
        print(f"\n{Color.CYAN}👋 Goodbye!{Color.RESET}\n")
        sys.exit(0)
    except Exception as e:
        print(f"\n{Color.RED}❌ Unexpected error: {e}{Color.RESET}")
        import traceback
        traceback.print_exc()
        input("\nPress Enter to exit...")
        sys.exit(1)
