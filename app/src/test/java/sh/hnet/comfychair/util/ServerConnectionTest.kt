package sh.hnet.comfychair.util

import org.junit.Assert.*
import org.junit.Test
import sh.hnet.comfychair.ComfyUIClient
import sh.hnet.comfychair.storage.BackupValidator

class ServerConnectionTest {

    @Test
    fun testBuildServerUrl() {
        // No protocol prefix, with port
        assertEquals("https://comfy.renegadeinc.net:8188", ServerUrlUtils.buildServerUrl("https", "comfy.renegadeinc.net", 8188))
        // No protocol prefix, no port (0 or negative)
        assertEquals("https://comfy.renegadeinc.net", ServerUrlUtils.buildServerUrl("https", "comfy.renegadeinc.net", 0))
        assertEquals("https://comfy.renegadeinc.net", ServerUrlUtils.buildServerUrl("https", "comfy.renegadeinc.net", -1))
        
        // Protocol prefix already present, with port
        assertEquals("http://comfy.renegadeinc.net:8188", ServerUrlUtils.buildServerUrl("https", "http://comfy.renegadeinc.net", 8188))
        // Protocol prefix already present, no port
        assertEquals("https://comfy.renegadeinc.net", ServerUrlUtils.buildServerUrl("http", "https://comfy.renegadeinc.net", 0))
    }

    @Test
    fun testComfyUIClientNormalization() {
        // Case 1: https prefix, no port
        val client1 = ComfyUIClient(hostname = "https://comfy.renegadeinc.net", port = 0)
        client1.setWorkingProtocol("https")
        assertEquals("https://comfy.renegadeinc.net", client1.getBaseUrl())

        // Case 2: http prefix, with port in hostname
        val client2 = ComfyUIClient(hostname = "http://localhost:8188", port = 0)
        client2.setWorkingProtocol("http")
        assertEquals("http://localhost:8188", client2.getBaseUrl())

        // Case 3: No prefix, with port parameter
        val client3 = ComfyUIClient(hostname = "comfy.renegadeinc.net", port = 443)
        client3.setWorkingProtocol("https")
        assertEquals("https://comfy.renegadeinc.net:443", client3.getBaseUrl())
    }

    @Test
    fun testBackupValidatorHostnameAndPort() {
        val validator = BackupValidator()

        // Valid hostnames with/without protocols and ports
        assertTrue(validator.validateHostname("comfy.renegadeinc.net"))
        assertTrue(validator.validateHostname("https://comfy.renegadeinc.net"))
        assertTrue(validator.validateHostname("http://192.168.1.100:8188"))
        assertTrue(validator.validateHostname("127.0.0.1"))

        // Invalid hostnames
        assertFalse(validator.validateHostname(""))
        assertFalse(validator.validateHostname("   "))
        assertFalse(validator.validateHostname("invalid_host_name@foo"))

        // Ports
        assertTrue(validator.validatePort(0)) // Optional port represented as 0
        assertTrue(validator.validatePort(8188))
        assertTrue(validator.validatePort(65535))
        assertFalse(validator.validatePort(-1))
        assertFalse(validator.validatePort(65536))
    }
}
