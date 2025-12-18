import os
import base64
import json
import tempfile

import pytest

from crypto_handshake import SecurityAuditLogger


def random_b64_key(length=32):
    return base64.b64encode(os.urandom(length)).decode('ascii')


def test_parse_audit_key_valid_32_bytes():
    key_b64 = random_b64_key(32)
    key = SecurityAuditLogger._parse_audit_key(key_b64)
    assert isinstance(key, bytes)
    assert len(key) == 32


def test_parse_audit_key_invalid_base64():
    key = SecurityAuditLogger._parse_audit_key('not-base64!!')
    assert key is None


def test_encrypted_log_written_when_key_set(tmp_path, monkeypatch):
    # Set AUDIT_LOG_KEY for constructor fallback
    key_b64 = random_b64_key(32)
    monkeypatch.setenv('AUDIT_LOG_KEY', key_b64)

    audit_path = tmp_path / 'security_audit.jsonl'
    logger = SecurityAuditLogger(str(audit_path))
    # Should have enabled encryption due to env var
    assert logger._encryption_enabled is True
    assert logger._audit_key is not None

    logger.log_event('test_event', 'low', {'detail': 'x'})

    content = audit_path.read_text(encoding='utf-8')
    # Should contain encrypted payload field
    assert '"encrypted": true' in content or '"encrypted":true' in content
    # Try to parse JSON line
    line = content.strip().splitlines()[-1]
    j = json.loads(line)
    assert j.get('encrypted') is True
    assert 'payload' in j


def test_generated_key_fallback(tmp_path, monkeypatch):
    # Ensure no AUDIT_LOG_KEY present
    monkeypatch.delenv('AUDIT_LOG_KEY', raising=False)

    # Force _try_apply_windows_acl to fail to exercise fallback path
    monkeypatch.setattr(SecurityAuditLogger, '_try_apply_windows_acl', lambda self, user: False)

    audit_path = tmp_path / 'security_audit.jsonl'
    logger = SecurityAuditLogger(str(audit_path))

    # Should enable encryption via generated key fallback
    assert logger._encryption_enabled is True
    assert logger._audit_key is not None
    # Key file may have been created; if so, ensure it contains the key
    key_file = audit_path.with_suffix('.key')
    if key_file.exists():
        data = key_file.read_bytes()
        assert data == logger._audit_key
    else:
        # Ephemeral fallback accepted as valid behavior
        assert logger._audit_key is not None
