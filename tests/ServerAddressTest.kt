package app.captunes.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerAddressTest {

    @Test fun addsHttpWhenNoSchemeIsTyped() {
        assertEquals("http://mypc:4533", ServerAddress.normalize("mypc:4533"))
    }

    @Test fun trimsSpacesAndTrailingSlashes() {
        assertEquals("https://music.example.com", ServerAddress.normalize("  https://music.example.com// "))
    }

    @Test fun acceptsCapitalLettersInTheScheme() {
        assertEquals("https://music.example.com", ServerAddress.normalize("Https://music.example.com"))
        assertEquals("http://10.0.0.5:4533", ServerAddress.normalize("HTTP://10.0.0.5:4533"))
    }

    @Test fun httpsIsAlwaysAllowed() {
        assertTrue(ServerAddress.isSafe("https://music.example.com"))
        assertTrue(ServerAddress.isSafe("Https://8.8.8.8"))
    }

    @Test fun plainHttpIsAllowedOnPrivateNetworks() {
        for (ok in listOf(
            "10.0.0.1", "192.168.1.5:4533", "172.16.0.9", "172.31.255.1", "127.0.0.1",
            "100.64.0.1", "100.101.1.2:4533", "localhost:4533", "mypc:4533", "my-pc",
            "pc.tail1234.ts.net", "http://[::1]:4533", "http://[fd7a:115c:a1e0::1]:4533", "http://[fe80::1]",
        )) assertTrue("should allow $ok", ServerAddress.isSafe(ok))
    }

    @Test fun plainHttpIsRefusedOnThePublicInternet() {
        for (bad in listOf(
            "8.8.8.8", "music.example.com", "172.32.0.1", "100.128.0.1", "192.169.1.1", "11.0.0.1",
            "http://[2001:db8::1]:4533", "http://[::ffff:8.8.8.8]", "http://[fd::1]",
        )) assertFalse("should refuse $bad", ServerAddress.isSafe(bad))
    }

    @Test fun lookAlikeAddressesAreRefused() {
        // Starts like a private address but is really a public host name.
        assertFalse(ServerAddress.isSafe("10.0.0.1.evil.com"))
        assertFalse(ServerAddress.isSafe("192.168.1.1.example.org:4533"))
        assertFalse(ServerAddress.isSafe("localhost.evil.com"))
        assertFalse(ServerAddress.isSafe("evil.com/.ts.net"))
        assertFalse(ServerAddress.isSafe("999.1.1.1"))
        assertFalse(ServerAddress.isSafe("10.0.0"))
    }

    @Test fun nonsenseIsRefusedRatherThanCrashing() {
        assertFalse(ServerAddress.isSafe(""))
        assertFalse(ServerAddress.isSafe("http://"))
        assertFalse(ServerAddress.isSafe("my pc"))
        assertFalse(ServerAddress.isSafe("http://[not-an-address]"))
    }
}
