from app.keys import normalize_key, key_hash, key_for_order, Signer


def test_key_is_deterministic_and_formatted():
    k = key_for_order("s", "order-1")
    assert k == key_for_order("s", "order-1")
    assert k != key_for_order("other", "order-1")
    assert len(k) == 19 and k.count("-") == 3


def test_normalize_and_hash():
    assert normalize_key(" dpla–aaaa — bbbb ") == "DPLA-AAAA-BBBB"
    assert key_hash("dpla-aaaa") == key_hash(" DPLA-AAAA ")


def test_signer_rejects_bad_seed():
    import base64, pytest
    with pytest.raises(ValueError):
        Signer(base64.b64encode(b"short").decode())
