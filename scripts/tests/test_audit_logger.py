import os
import base64
import tempfile
import json
import pathlib
import pytest

from crypto_handshake import SecurityAuditLogger


def test_audit_logger_encrypts_when_key_present(tmp_path, monkeypatch):
    key = os.urandom(32)
    os.environ['AUDIT_LOG_KEY'] = base64.b64encode(key).decode('ascii')

    audit_file = tmp_path / "audit.jsonl"
    logger = SecurityAuditLogger(str(audit_file))

    assert getattr(logger, '_encryption_enabled') is True
    assert logger._audit_key == key

    logger.log_event('test', 'low', {'msg': 'hello'})

    # file should contain one line and be valid JSON with encrypted field
    content = audit_file.read_text(encoding='utf-8').strip().splitlines()
    assert len(content) == 1
    obj = json.loads(content[0])
    assert 'encrypted' in obj and obj['encrypted'] is True
    assert 'payload' in obj


def test_audit_logger_raises_when_permissions_cannot_be_enforced(tmp_path, monkeypatch):
    # ensure no env key
    monkeypatch.delenv('AUDIT_LOG_KEY', raising=False)
    audit_file = tmp_path / "audit_fail.jsonl"

    # Force chmod to raise an exception to simulate environment where permissions can't be set
    def fake_chmod(path):
        raise PermissionError("simulated failure")

    monkeypatch.setattr(pathlib.Path, 'chmod', lambda self, mode: (_ for _ in ()).throw(PermissionError("simulated")))

    with pytest.raises(RuntimeError):
        SecurityAuditLogger(str(audit_file))
