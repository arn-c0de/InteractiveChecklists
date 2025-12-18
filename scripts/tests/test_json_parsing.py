import json
import pytest
from forward_parsed_udp import safe_json_parse


def test_safe_json_parse_size_limit():
    # Test that large JSON is rejected before parsing
    large_json = '{"data": "' + "x" * 9000 + '"}'
    result = safe_json_parse(large_json, max_size=1000)
    assert result is None


def test_safe_json_parse_depth_limit():
    # Test that deeply nested JSON is rejected
    deep_json = '{"a":' * 10 + '"value"' + '}' * 10
    result = safe_json_parse(deep_json, max_depth=5)
    assert result is None


def test_safe_json_parse_valid():
    # Test that valid JSON within limits works
    valid_json = '{"key": "value", "number": 42}'
    result = safe_json_parse(valid_json)
    assert result == {"key": "value", "number": 42}


def test_safe_json_parse_invalid_structure():
    # Test that non-object/array root is rejected
    invalid_json = '"just a string"'
    result = safe_json_parse(invalid_json)
    assert result is None