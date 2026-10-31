import hashlib
import importlib.util
import json
from pathlib import Path
import ssl
import unittest
from unittest.mock import Mock, patch

spec = importlib.util.spec_from_file_location("atv_control", Path(__file__).parents[1] / "atv_control.py")
control = importlib.util.module_from_spec(spec)
spec.loader.exec_module(control)


class ControlClientTest(unittest.TestCase):
    def setUp(self):
        self.der = b"fixture certificate bytes"
        self.pin = hashlib.sha256(self.der).hexdigest()
        self.connection = Mock()
        self.connection.sock.getpeercert.return_value = self.der
        self.connection.getresponse.return_value.status = 200
        self.connection.getresponse.return_value.read.return_value = b'{"catalogVersion":"v1","channels":[]}'
        self.factory = patch.object(control.http.client, "HTTPSConnection", return_value=self.connection)
        self.factory.start()
        self.addCleanup(self.factory.stop)
        self.client = control.ControlClient("https://127.0.0.1:8765", self.pin, "fixture-token")

    def test_bad_pin_never_sends_the_authorization_header(self):
        self.connection.sock.getpeercert.return_value = b"different identity"
        with self.assertRaises(ssl.SSLError):
            self.client.list_channels()
        self.connection.request.assert_not_called()
        self.connection.close.assert_called_once()

    def test_verified_identity_can_retrieve_the_catalog(self):
        self.assertEqual("v1", self.client.list_channels()["catalogVersion"])
        self.connection.request.assert_called_once_with(
            "GET", "/api/v1/channels", body=None, headers={"Authorization": "Bearer fixture-token"})

    def test_switch_uses_the_version_the_agent_selected_without_refetching(self):
        self.connection.getresponse.return_value.status = 202
        self.connection.getresponse.return_value.read.return_value = b'{"status":"accepted"}'
        self.assertEqual({"status": "accepted"}, self.client.switch_channel(17, "selected-version"))
        args, kwargs = self.connection.request.call_args
        self.assertEqual(("POST", "/api/v1/playback/channel"), args)
        self.assertEqual({"number": 17, "catalogVersion": "selected-version"}, json.loads(kwargs["body"]))
        self.connection.request.assert_called_once()

    def test_stale_catalog_error_is_returned_to_the_agent(self):
        self.connection.getresponse.return_value.status = 409
        self.connection.getresponse.return_value.read.return_value = b'{"error":{"code":"CATALOG_CHANGED"}}'
        with self.assertRaises(control.ControlApiError) as raised:
            self.client.switch_channel(17, "old")
        self.assertEqual(409, raised.exception.status)

    def test_plaintext_urls_and_invalid_pins_are_rejected(self):
        for url in ("http://127.0.0.1", "https://user:password@tv", "https://tv/path", "https://tv?token=secret"):
            with self.assertRaises(ValueError):
                control.ControlClient(url, self.pin, "fixture-token")
        with self.assertRaises(ValueError):
            control.ControlClient("https://tv", "incomplete", "fixture-token")

    def test_invalid_selection_never_makes_a_network_request(self):
        for number in (True, "17", 0, -1, 2147483648):
            with self.assertRaises(ValueError):
                self.client.switch_channel(number, "v1")
        self.connection.request.assert_not_called()


if __name__ == "__main__":
    unittest.main()
