package com.limelight.utils;

import com.limelight.nvstream.http.ComputerDetails;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NetHelperTest {
    @Test
    public void hostnameIsNotResolvedForLanClassification() {
        // "localhost" used to resolve to loopback and was therefore classified as LAN.
        // Hostnames must remain unknown here so polling cannot trigger DNS as a side effect.
        assertFalse(NetHelper.INSTANCE.isLanAddress("localhost"));
        assertFalse(NetHelper.INSTANCE.isLanAddress("sunshine.example.com"));
    }

    @Test
    public void ipLiteralsKeepLanClassification() {
        assertTrue(NetHelper.INSTANCE.isLanAddress("127.0.0.1"));
        assertTrue(NetHelper.INSTANCE.isLanAddress("192.168.1.10"));
        assertTrue(NetHelper.INSTANCE.isLanAddress("::1"));
        assertTrue(NetHelper.INSTANCE.isLanAddress("[::1]"));
        assertFalse(NetHelper.INSTANCE.isLanAddress("8.8.8.8"));
        assertFalse(NetHelper.INSTANCE.isLanAddress("+127.0.0.1"));
        assertFalse(NetHelper.INSTANCE.isLanAddress("2001%3Adb8::1"));
        assertFalse(NetHelper.INSTANCE.isLanAddress("[127.0.0.1]"));
    }

    @Test
    public void malformedBracketsAreRejected() {
        assertFalse(NetHelper.INSTANCE.isLanAddress("[::1"));
        assertFalse(NetHelper.INSTANCE.isLanAddress("::1]"));
        assertFalse(NetHelper.INSTANCE.isPrivateAddress("[::1"));
        assertFalse(NetHelper.INSTANCE.isPrivateAddress("::1]"));
    }

    @Test
    public void ipLiteralValidationIsStrict() {
        assertTrue(NetHelper.INSTANCE.isIpLiteral("192.168.1.10"));
        assertTrue(NetHelper.INSTANCE.isIpLiteral("2001:db8::1"));
        assertTrue(NetHelper.INSTANCE.isIpLiteral("[2001:db8::1]"));
        assertTrue(NetHelper.INSTANCE.isIpLiteral("[::1]"));
        assertFalse(NetHelper.INSTANCE.isIpLiteral("sunshine.example.com"));
        assertFalse(NetHelper.INSTANCE.isIpLiteral("foo:bar"));
        assertFalse(NetHelper.INSTANCE.isIpLiteral("1:2"));
        assertFalse(NetHelper.INSTANCE.isIpLiteral("[2001:db8::1"));
        assertFalse(NetHelper.INSTANCE.isIpLiteral("+127.0.0.1"));
        assertFalse(NetHelper.INSTANCE.isIpLiteral("2001%3Adb8::1"));
        assertFalse(NetHelper.INSTANCE.isIpLiteral("[127.0.0.1]"));
    }

    @Test
    public void computerDetailsLanIpv4ClassificationUsesLiteralValidation() {
        assertTrue(ComputerDetails.Companion.isLanIpv4Address(
                new ComputerDetails.AddressTuple("192.168.1.10", 47989)));
        assertFalse(ComputerDetails.Companion.isLanIpv4Address(
                new ComputerDetails.AddressTuple("sunshine.example.com", 47989)));
        assertFalse(ComputerDetails.Companion.isLanIpv4Address(
                new ComputerDetails.AddressTuple("foo:bar", 47989)));
        assertFalse(ComputerDetails.Companion.isLanIpv4Address(
                new ComputerDetails.AddressTuple("+127.0.0.1", 47989)));
        assertFalse(ComputerDetails.Companion.isLanIpv4Address(
                new ComputerDetails.AddressTuple("2001%3Adb8::1", 47989)));
        assertFalse(ComputerDetails.Companion.isLanIpv4Address(
                new ComputerDetails.AddressTuple("[127.0.0.1]", 47989)));
        assertFalse(ComputerDetails.Companion.isLanIpv4Address(
                new ComputerDetails.AddressTuple("[::1]", 47989)));
    }
}
