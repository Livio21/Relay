import io
import json
import unittest

from relay_extension import main


class RelayExtensionTest(unittest.TestCase):
    def call(self, message):
        output = io.StringIO()
        main(io.StringIO(json.dumps(message) + "\n"), output)
        return json.loads(output.getvalue())

    def test_handshake_is_versioned(self):
        result = self.call({"id": "one", "method": "handshake"})["result"]
        self.assertEqual("example.relay.source", result["id"])
        self.assertEqual({"minimum": 1, "maximum": 1}, result["api"])

    def test_bad_messages_fail_closed(self):
        self.assertEqual("Unknown method.", self.call({"id": "one", "method": "delete"})["error"])
        self.assertEqual("Request ID is required.", self.call({"method": "browse"})["error"])


if __name__ == "__main__":
    unittest.main()
