import sys
import unittest
from pathlib import Path


sys.path.insert(0, str(Path(__file__).parents[1] / "src"))

from utils import (  # noqa: E402
    DDNSPacketError,
    Request,
    https_signature,
    verify_https_signature,
)


class HttpsSignatureTests(unittest.TestCase):
    def test_signature_is_cross_language_stable(self):
        self.assertEqual(
            https_signature(1234, 1700000000, "2001:db8::1", "secret"),
            "9594fb83c9260474443ac469d2c4ac36e046a95f7d481682838d630f8269f0cc",
        )

    def test_signature_verification_rejects_changes(self):
        signature = https_signature(1234, 1700000000, "2001:db8::1", "secret")

        self.assertTrue(verify_https_signature(1234, 1700000000, "2001:db8::1", signature, "secret"))
        self.assertFalse(verify_https_signature(1234, 1700000001, "2001:db8::1", signature, "secret"))
        self.assertFalse(verify_https_signature(1234, 1700000000, "2001:db8::2", signature, "secret"))
        self.assertFalse(verify_https_signature(1234, 1700000000, "2001:db8::1", signature, "wrong"))


class UdpProtocolTests(unittest.TestCase):
    def test_request_round_trip_remains_compatible(self):
        request = Request()
        payload = request.make(1234, "client-secret", "2001:db8::1")

        received = Request()
        received.frombuf(payload)

        self.assertEqual(received.id_client, 1234)
        self.assertEqual(received.ip, "2001:0db8:0000:0000:0000:0000:0000:0001")
        self.assertTrue(received.verify("client-secret"))
        self.assertEqual(received.ack("server-secret"), request.ack("server-secret"))

    def test_request_rejects_invalid_packet_length(self):
        with self.assertRaises(DDNSPacketError):
            Request().frombuf(b"invalid")

if __name__ == "__main__":
    unittest.main()
