#!/usr/bin/env python3
"""
config.py - Modern TUI configuration menu for DCS DataPad Server
Features: Grid navigation, command builder, live preview, arrow key support
"""

import json
import os
import sys
import time
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
DEFAULT_CONFIG = {
    "last_mode": 1,
    "ip_address": "192.168.178.100",
    "target_ip": "192.168.178.132",
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


def get_key_with_timeout(timeout=3.0):
    """Get key with timeout, returns (Key, char) or (None, None) on timeout"""
    start_time = time.time()
    
    while (time.time() - start_time) < timeout:
        key, char = get_key()
        if key != Key.UNKNOWN:
            return key, char
        time.sleep(0.05)
    
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


def show_main_menu(config, selected_mode):
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
    
    for mode_id, mode_info in MODES.items():
        selected = (mode_id == selected_mode)
        last_used = " ⭐" if mode_id == config.get('last_mode', 1) else ""
        
        if selected:
            print(f"  {Color.BG_CYAN}{Color.BLACK}▶ [{mode_id}] {mode_info['name']}{last_used}{' ' * (60 - len(mode_info['name']) - len(last_used))}{Color.RESET}")
            print(f"      {Color.CYAN}{mode_info['description']}{Color.RESET}")
        else:
            print(f"    [{mode_id}] {mode_info['name']}{last_used}")
            print(f"      {Color.DIM}{mode_info['description']}{Color.RESET}")
        print()
    
    print(f"{Color.DIM}{'─' * 78}{Color.RESET}")
    print(f"  {Color.YELLOW}[C]{Color.RESET} Command Builder  {Color.YELLOW}[S]{Color.RESET} Settings  {Color.YELLOW}[Q]{Color.RESET} Quit")
    print(f"{Color.DIM}{'─' * 78}{Color.RESET}")
    print()
    print(f"{Color.DIM}  ↑↓: Navigate  ENTER: Start Server  ESC: Quit{Color.RESET}")


def update_main_selection(old_id: int, new_id: int, config: dict):
    """Update only the two mode blocks that changed (flicker-free)."""
    if old_id == new_id:
        return

    def format_block(mode_id: int, selected: bool, is_last_used: bool):
        m = MODES[mode_id]
        last_used = " ⭐" if is_last_used else ""
        if selected:
            line1 = f"  {Color.BG_CYAN}{Color.BLACK}▶ [{mode_id}] {m['name']}{last_used}{' ' * 20}{Color.RESET}"
            line2 = f"      {Color.CYAN}{m['description']}{Color.RESET}"
        else:
            line1 = f"    [{mode_id}] {m['name']}{last_used}{' ' * 20}"
            line2 = f"      {Color.DIM}{m['description']}{Color.RESET}"
        return line1, line2

    # Compute line numbers: modes start at line 11, each mode uses 3 lines
    def top_line_for(mode_id: int) -> int:
        return 11 + (mode_id - 1) * 3

    # Redraw old (deselected)
    if old_id is not None and 1 <= old_id <= len(MODES):
        l1, l2 = format_block(old_id, selected=False, is_last_used=(old_id == config.get('last_mode', 1)))
        t = top_line_for(old_id)
        print_at(1, t, l1)
        print_at(1, t + 1, l2)
        print_at(1, t + 2, ' ' * 76)

    # Draw new (selected)
    if new_id is not None and 1 <= new_id <= len(MODES):
        l1, l2 = format_block(new_id, selected=True, is_last_used=(new_id == config.get('last_mode', 1)))
        t = top_line_for(new_id)
        print_at(1, t, l1)
        print_at(1, t + 1, l2)
        print_at(1, t + 2, ' ' * 76)


def edit_setting_inline(prompt, current_value, validator=None):
    """Edit a setting value inline with live feedback"""
    print(f"\n{prompt}")
    print(f"{Color.DIM}Current: {Color.GREEN}{current_value}{Color.RESET}")
    print(f"{Color.DIM}New value (Enter to keep current): {Color.RESET}", end='', flush=True)
    
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
        ("bind_ip", "Bind IP Address (Server)", "ip_address", lambda x: True),
        ("target_ip", "Target IP Address (Client)", "target_ip", lambda x: True),
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


def show_command_builder(config):
    """Interactive command builder"""
    clear_screen()
    print(f"{Color.BOLD}{Color.GREEN}╔{'═' * 76}╗{Color.RESET}")
    print(f"{Color.BOLD}{Color.GREEN}║{' ' * 25}⚙️  COMMAND BUILDER{' ' * 32}║{Color.RESET}")
    print(f"{Color.BOLD}{Color.GREEN}╚{'═' * 76}╝{Color.RESET}")
    print()
    
    print(f"{Color.BOLD}Build your custom command:{Color.RESET}")
    print()
    
    # Base command
    cmd_parts = ["python forward_parsed_udp.py"]
    
    # Options menu
    options = [
        ("--enable-pow", "Enable Proof-of-Work", config.get("enable_pow", False)),
        ("--repeat-last", "Repeat last data (Map mode)", False),
        ("--verbose", "Verbose logging", True),
    ]
    
    print(f"{Color.CYAN}Options:{Color.RESET}")
    for flag, desc, default in options:
        status = f"{Color.GREEN}✓{Color.RESET}" if default else f"{Color.DIM}○{Color.RESET}"
        print(f"  {status} {flag:<20} {Color.DIM}{desc}{Color.RESET}")
    
    print()
    print(f"{Color.CYAN}Parameters:{Color.RESET}")
    print(f"  --host {config['target_ip']}")
    print(f"  --port {config['port']}")
    print(f"  --bind-ip {config['ip_address']}")
    print(f"  --interval {config['interval']}")
    print(f"  --authorized-devices {config['authorized_devices']}")
    
    if config.get("enable_pow", False):
        print(f"  --pow-difficulty {config['pow_difficulty']}")
    
    # Build full command
    cmd = f"python forward_parsed_udp.py"
    if config.get("enable_pow", False):
        cmd += f" --enable-pow --pow-difficulty {config['pow_difficulty']}"
    cmd += f" --interval {config['interval']}"
    cmd += f" --host {config['target_ip']}"
    cmd += f" --port {config['port']}"
    cmd += f" --verbose"
    cmd += f" --authorized-devices {config['authorized_devices']}"
    cmd += f" --bind-ip {config['ip_address']}"
    
    print()
    print(f"{Color.YELLOW}{'─' * 78}{Color.RESET}")
    print(f"{Color.BOLD}Generated Command:{Color.RESET}")
    print(f"{Color.GREEN}{cmd}{Color.RESET}")
    print(f"{Color.YELLOW}{'─' * 78}{Color.RESET}")
    print()
    
    print(f"{Color.DIM}Press any key to return to menu...{Color.RESET}")
    get_key()


def build_command(mode_id, config):
    """Build command line for selected mode"""
    if mode_id not in MODES:
        return None
    
    mode = MODES[mode_id]
    if mode['command'] == "custom":
        return show_command_builder(config)
    
    return mode['command'].format(
        ip_address=config['ip_address'],
        target_ip=config['target_ip'],
        port=config['port'],
        handshake_port=config['handshake_port'],
        interval=config['interval'],
        authorized_devices=config['authorized_devices'],
        pow_difficulty=config.get('pow_difficulty', 16)
    )


def main():
    """Main application loop with modern TUI (flicker-free)"""
    config = load_config()
    selected_mode = config.get('last_mode', 1)
    last_selected = -1
    needs_redraw = True
    
    while True:
        # Only redraw when necessary
        if needs_redraw or last_selected != selected_mode:
            show_main_menu(config, selected_mode)
            last_selected = selected_mode
            needs_redraw = False
        
        # Auto-start with timeout
        print(f"{Color.DIM}  Press any key to cancel auto-start (starting mode {selected_mode} in 3s)...{Color.RESET}", end='', flush=True)
        
        key, char = get_key_with_timeout(3.0)
        
        # Clear auto-start message
        print('\r' + ' ' * 80 + '\r', end='', flush=True)
        
        # If timeout occurred, auto-start the selected mode
        if key is None:
            key = Key.ENTER
        # If navigation key pressed, cancel auto-start and enter interactive mode
        elif key in (Key.UP, Key.DOWN, Key.LEFT, Key.RIGHT, Key.CHAR):
            # Enter interactive navigation mode until user presses ENTER/ESC/Q
            interactive = True
            needs_redraw = True
            # Initial draw for interactive mode (single full draw)
            show_main_menu(config, selected_mode)
            last_interactive = selected_mode
            while interactive:
                k, ch = get_key()
                if k == Key.UP:
                    new_sel = selected_mode - 1 if selected_mode > 1 else len(MODES)
                    if new_sel != selected_mode:
                        update_main_selection(selected_mode, new_sel, config)
                        selected_mode = new_sel
                elif k == Key.DOWN:
                    new_sel = selected_mode + 1 if selected_mode < len(MODES) else 1
                    if new_sel != selected_mode:
                        update_main_selection(selected_mode, new_sel, config)
                        selected_mode = new_sel
                elif k == Key.ENTER:
                    # User confirmed selection - launch
                    config['last_mode'] = selected_mode
                    save_config(config)
                    command = build_command(selected_mode, config)
                    if command:
                        clear_screen()
                        print(f"{Color.BOLD}{Color.GREEN}{'═' * 78}{Color.RESET}")
                        print(f"{Color.BOLD}{Color.GREEN}🚀 Starting: {MODES[selected_mode]['name']}{Color.RESET}")
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
                    elif ch.isdigit():
                        mode_num = int(ch)
                        if mode_num in MODES:
                            new_sel = mode_num
                            update_main_selection(selected_mode, new_sel, config)
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
            config['last_mode'] = selected_mode
            save_config(config)
            
            command = build_command(selected_mode, config)
            if command:
                clear_screen()
                print(f"{Color.BOLD}{Color.GREEN}{'═' * 78}{Color.RESET}")
                print(f"{Color.BOLD}{Color.GREEN}🚀 Starting: {MODES[selected_mode]['name']}{Color.RESET}")
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
            elif char.isdigit():
                mode_num = int(char)
                if mode_num in MODES:
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
