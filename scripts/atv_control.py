#!/usr/bin/env python3
"""ATV's two-operation controller, with mandatory out-of-band TLS certificate pinning."""

import argparse
import getpass
import hashlib
import hmac
import http.client
import json
import os
import ssl
import sys
from urllib.parse import urlsplit

MAX_RESPONSE_BYTES = 4 * 1024 * 1024


class ControlApiError(RuntimeError):
    def __init__(self, status, payload):
        super().__init__(f"HTTP {status}: {json.dumps(payload, ensure_ascii=False)}")
        self.status = status
        self.payload = payload


class ControlClient:
    def __init__(self, url, fingerprint, token):
        endpoint = urlsplit(url)
        if (endpoint.scheme != "https" or not endpoint.hostname or endpoint.username is not None
                or endpoint.password is not None or endpoint.query or endpoint.fragment
                or endpoint.path not in ("", "/")):
            raise ValueError("Use the HTTPS base address displayed by ATV, without credentials or a path.")
        pin = fingerprint.replace(":", "").lower()
        if len(pin) != 64 or any(character not in "0123456789abcdef" for character in pin):
            raise ValueError("Copy the complete SHA-256 certificate fingerprint from the TV.")
        if not token or any(character.isspace() for character in token):
            raise ValueError("A non-empty access token without whitespace is required.")
        self._host = endpoint.hostname
        self._port = endpoint.port or 443
        self._fingerprint = pin
        self._token = token

    def list_channels(self):
        return self._request("GET", "/api/v1/channels")

    def switch_channel(self, number, catalog_version):
        if type(number) is not int or not 1 <= number <= 2147483647:
            raise ValueError("Channel number must be a positive 32-bit integer.")
        if not isinstance(catalog_version, str) or not catalog_version.strip() or len(catalog_version) > 128:
            raise ValueError("Use catalogVersion from the catalog used to choose this number.")
        return self._request("POST", "/api/v1/playback/channel", {
            "number": number, "catalogVersion": catalog_version,
        })

    def _request(self, method, path, body=None):
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
        context.minimum_version = ssl.TLSVersion.TLSv1_2
        # A TV has a self-signed identity and a changing LAN address. Authenticate
        # its exact certificate below BEFORE sending any HTTP request or token.
        context.check_hostname = False
        context.verify_mode = ssl.CERT_NONE
        connection = http.client.HTTPSConnection(self._host, self._port, context=context, timeout=5)
        try:
            connection.connect()
            certificate = connection.sock.getpeercert(binary_form=True)
            actual = hashlib.sha256(certificate).hexdigest()
            if not hmac.compare_digest(actual, self._fingerprint):
                raise ssl.SSLError("TV certificate fingerprint mismatch; no access token was sent.")
            headers = {"Authorization": "Bearer " + self._token}
            payload = None
            if body is not None:
                headers["Content-Type"] = "application/json"
                payload = json.dumps(body).encode("utf-8")
            connection.request(method, path, body=payload, headers=headers)
            response = connection.getresponse()
            data = response.read(MAX_RESPONSE_BYTES + 1)
            if len(data) > MAX_RESPONSE_BYTES:
                raise ValueError("Controller response limit exceeded.")
            result = json.loads(data.decode("utf-8"))
            if response.status not in (200, 202):
                raise ControlApiError(response.status, result)
            return result
        finally:
            connection.close()


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", required=True, help="HTTPS TV address shown in Connection details")
    parser.add_argument("--fingerprint", required=True, help="SHA-256 certificate fingerprint shown on the TV")
    operations = parser.add_subparsers(dest="operation", required=True)
    operations.add_parser("list", help="Retrieve the complete channel catalog")
    switch = operations.add_parser("switch", help="Request a channel number from a previously retrieved catalog")
    switch.add_argument("number", type=int)
    switch.add_argument("--catalog-version", required=True)
    args = parser.parse_args(argv)
    try:
        token = os.environ.get("ATV_CONTROL_TOKEN")
        if token is None:
            if not sys.stdin.isatty():
                raise ValueError("Set ATV_CONTROL_TOKEN when running non-interactively.")
            token = getpass.getpass("ATV access token: ")
        client = ControlClient(args.url, args.fingerprint, token)
        result = (client.list_channels() if args.operation == "list"
                  else client.switch_channel(args.number, args.catalog_version))
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return 0
    except (OSError, ValueError, http.client.HTTPException, ControlApiError) as error:
        print(str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
