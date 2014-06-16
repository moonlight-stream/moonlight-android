package com.limelight.nvstream.http;

import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;

public interface LimelightCryptoProvider {
	public X509Certificate getClientCertificate();
	public RSAPrivateKey getClientPrivateKey();
	public byte[] getPemEncodedClientCertificate();
}
