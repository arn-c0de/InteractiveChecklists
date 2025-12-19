import pytest
from network.datapad_receiver import DataPadReceiver, default_gcm_nonce_manager

class DummyECDH:
    def __init__(self, response: bytes = b'plaintext'):
        self._resp = response
        self.called = False

    def decrypt_payload(self, encrypted_data: bytes):
        self.called = True
        return self._resp


def make_nonce(sender_id: int = 0x01, counter: int = 1) -> bytes:
    # nonce format: [sender_id:1][reserved:3][counter:8]
    return bytes([sender_id]) + b"\x00\x00\x00" + counter.to_bytes(8, 'big')


def test_replay_blocked(monkeypatch):
    receiver = DataPadReceiver(sender_ip='127.0.0.1', sender_port=5010)

    # replace the ECDH client with a dummy that would succeed if called
    dummy = DummyECDH()
    receiver.ecdh_client = dummy

    # Make a packet with a valid-looking nonce but force validate_nonce to return False
    nonce = make_nonce(counter=42)
    pkt = nonce + b"\x00" * 16  # minimal ciphertext+tag filler

    monkeypatch.setattr(default_gcm_nonce_manager, 'validate_nonce', lambda n, expected_sender, client_addr=None: False)

    # Should be rejected by nonce validation and not call dummy.decrypt_payload
    res = receiver.decrypt_payload(pkt, sender_addr=('1.2.3.4', 9999))
    assert res is None
    assert dummy.called is False


def test_valid_nonce_allows_decrypt(monkeypatch):
    receiver = DataPadReceiver(sender_ip='127.0.0.1', sender_port=5010)

    dummy = DummyECDH(response=b'ok')
    receiver.ecdh_client = dummy

    nonce = make_nonce(counter=100)
    pkt = nonce + b"\x00" * 16

    # Allow nonce validation to pass
    monkeypatch.setattr(default_gcm_nonce_manager, 'validate_nonce', lambda n, expected_sender, client_addr=None: True)

    res = receiver.decrypt_payload(pkt, sender_addr=('1.2.3.4', 9999))
    assert res == b'ok'
    assert dummy.called is True
