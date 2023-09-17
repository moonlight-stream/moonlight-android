// Copyright (c) 2014-present Tom Zhou<appnet.link@gmail.com>


package com.limelight.solanaWallet;

import java.io.UnsupportedEncodingException;
import java.security.SecureRandom;
import java.lang.System;
import java.util.concurrent.atomic.AtomicLong;


/*
 * @description 
 *   TweetNacl.c Java porting
 * */
public final class TweetNaclFast {

	private final static String TAG = "TweetNaclFast";

	/*
	 * @description 
	 *   Box algorithm, Public-key authenticated encryption 
	 * */
	public static final class Box {

		private final static String TAG = "Box";

		private AtomicLong nonce;

		private byte [] theirPublicKey;
		private byte [] mySecretKey;
		private byte [] sharedKey;

		public Box(byte [] theirPublicKey, byte [] mySecretKey) {
			this(theirPublicKey, mySecretKey, 68);
		}

		public Box(byte [] theirPublicKey, byte [] mySecretKey, long nonce) {
			this.theirPublicKey = theirPublicKey;
			this.mySecretKey = mySecretKey;

			this.nonce = new AtomicLong(nonce);

			// generate pre-computed shared key
			before();
		}

		public void setNonce(long nonce) {
			this.nonce.set(nonce);
		}
		public long getNonce() {
			return this.nonce.get();
		}
		public long incrNonce() {
			return this.nonce.incrementAndGet();
		}
		private byte[] generateNonce() {
			// generate nonce 
			long nonce = this.nonce.get();

			byte [] n = new byte[nonceLength];
			for (int i = 0; i < nonceLength; i += 8) {
				n[i+0] = (byte) (nonce>>> 0);
				n[i+1] = (byte) (nonce>>> 8);
				n[i+2] = (byte) (nonce>>>16);
				n[i+3] = (byte) (nonce>>>24);
				n[i+4] = (byte) (nonce>>>32);
				n[i+5] = (byte) (nonce>>>40);
				n[i+6] = (byte) (nonce>>>48);
				n[i+7] = (byte) (nonce>>>56);
			}

			return n;
		}

		/*
		 * @description 
		 *   Encrypt and authenticates message using peer's public key, 
		 *   our secret key, and the given nonce, which must be unique 
		 *   for each distinct message for a key pair.
		 *   
		 *   Returns an encrypted and authenticated message, 
		 *   which is nacl.box.overheadLength longer than the original message.
		 * */
		public byte [] box(byte [] message) {
			if (message==null) return null;
		  return box(message, 0, message.length);
		}

		public byte [] box(byte [] message, final int moff) {
			if (!(message!=null && message.length>moff)) return null;
		  return box(message, moff, message.length-moff);
		}
	
		public byte [] box(byte [] message, final int moff, final int mlen) {
			if (!(message!=null && message.length>=(moff+mlen))) return null;

			// prepare shared key
			if (this.sharedKey == null) before();

			return after(message, moff, mlen);
		}

		/*
		 * @description 
		 *   Encrypt and authenticates message using peer's public key, 
		 *   our secret key, and the given nonce, which must be unique 
		 *   for each distinct message for a key pair.
		 *
		 *   Explicitly pass the nonce
		 *   
		 *   Returns an encrypted and authenticated message, 
		 *   which is nacl.box.overheadLength longer than the original message.
		 * */
		public byte [] box(byte [] message, byte [] theNonce) {
			if (message==null) return null;
		  return box(message, 0, message.length, theNonce);
		}

		public byte [] box(byte [] message, final int moff, byte [] theNonce) {
			if (!(message!=null && message.length>moff)) return null;
		  return box(message, moff, message.length-moff, theNonce);
		}
	
		public byte [] box(byte [] message, final int moff, final int mlen, byte [] theNonce) {
			if (!(message!=null && message.length>=(moff+mlen) &&
			      theNonce!=null && theNonce.length==nonceLength)) 
			  return null;

			// prepare shared key
			if (this.sharedKey == null) before();

			return after(message, moff, mlen, theNonce);
		}

		/*
		 * @description 
		 *   Authenticates and decrypts the given box with peer's public key, 
		 *   our secret key, and the given nonce.
		 *   
		 *   Returns the original message, or null if authentication fails.
		 * */
		public byte [] open(byte [] box) {
			if (box==null) return null;

			// prepare shared key
			if (this.sharedKey == null) before();

			return open_after(box, 0, box.length);
		}
		public byte [] open(byte [] box, final int boxoff) {
			if (!(box!=null && box.length>boxoff)) return null;

			// prepare shared key
			if (this.sharedKey == null) before();

			return open_after(box, boxoff, box.length-boxoff);
		}
		public byte [] open(byte [] box, final int boxoff, final int boxlen) {
			if (!(box!=null && box.length>=(boxoff+boxlen))) return null;

			// prepare shared key
			if (this.sharedKey == null) before();

			return open_after(box, boxoff, boxlen);
		}


		/*
		 * @description 
		 *   Authenticates and decrypts the given box with peer's public key, 
		 *   our secret key, and the given nonce.
		 *   Explicit passing of nonce
		 *   Returns the original message, or null if authentication fails.
		 * */
		public byte [] open(byte [] box, byte [] theNonce) {
			if (!(box!=null &&
			      theNonce!=null && theNonce.length==nonceLength))
			  return null;

			// prepare shared key
			if (this.sharedKey == null) before();

			return open_after(box, 0, box.length, theNonce);
		}
		public byte [] open(byte [] box, final int boxoff, byte [] theNonce) {
			if (!(box!=null && box.length>boxoff &&
			      theNonce!=null && theNonce.length==nonceLength))
			  return null;

			// prepare shared key
			if (this.sharedKey == null) before();

			return open_after(box, boxoff, box.length-boxoff, theNonce);
		}
		public byte [] open(byte [] box, final int boxoff, final int boxlen, byte [] theNonce) {
			if (!(box!=null && box.length>=(boxoff+boxlen) &&
			      theNonce!=null && theNonce.length==nonceLength))
			  return null;

			// prepare shared key
			if (this.sharedKey == null) before();

			return open_after(box, boxoff, boxlen, theNonce);
		}


		/*
		 * @description 
		 *   Returns a precomputed shared key 
		 *   which can be used in nacl.box.after and nacl.box.open.after.
		 * */
		public byte [] before() {
			if (this.sharedKey == null) {
				this.sharedKey = new byte[sharedKeyLength];
				crypto_box_beforenm(this.sharedKey, this.theirPublicKey, this.mySecretKey);
			}

			return this.sharedKey;
		}

		/*
		 * @description 
		 *   Same as nacl.box, but uses a shared key precomputed with nacl.box.before.
		 * */
		public byte [] after(byte [] message, final int moff, final int mlen) {
		  return after(message, moff, mlen, generateNonce());
		}

		/*
		 * @description 
		 *   Same as nacl.box, but uses a shared key precomputed with nacl.box.before,
		 *   and passes a nonce explicitly.
		 * */
		public byte [] after(byte [] message, final int moff, final int mlen, byte [] theNonce) {
			// check message
			if (!(message!=null && message.length>=(moff+mlen) &&
			      theNonce!=null && theNonce.length==nonceLength))
				return null;

			// message buffer
			byte [] m = new byte[mlen + zerobytesLength];

			// cipher buffer
			byte [] c = new byte[m.length];

			for (int i = 0; i < mlen; i ++)
				m[i+zerobytesLength] = message[i+moff];

			if (0 != crypto_box_afternm(c, m, m.length, theNonce, sharedKey))
				return null;

			// wrap byte_buf_t on c offset@boxzerobytesLength
			///return new byte_buf_t(c, boxzerobytesLength, c.length-boxzerobytesLength);
			byte [] ret = new byte[c.length-boxzerobytesLength];

			for (int i = 0; i < ret.length; i ++)
				ret[i] = c[i+boxzerobytesLength];

			return ret;
		}

		/*
		 * @description 
		 *   Same as nacl.box.open, 
		 *   but uses a shared key pre-computed with nacl.box.before.
		 * */
		public byte [] open_after(byte [] box, final int boxoff, final int boxlen) {
		  return open_after(box, boxoff, boxlen, generateNonce());
		}
		
		public byte [] open_after(byte [] box, final int boxoff, final int boxlen, byte [] theNonce) {
			// check message
			if (!(box!=null && box.length>=(boxoff+boxlen) && boxlen>=boxzerobytesLength))
				return null;

			// cipher buffer
			byte [] c = new byte[boxlen + boxzerobytesLength];

			// message buffer
			byte [] m = new byte[c.length];

			for (int i = 0; i < boxlen; i++) 
				c[i+boxzerobytesLength] = box[i+boxoff];

			if (crypto_box_open_afternm(m, c, c.length, theNonce, sharedKey) != 0) 
				return null;

			// wrap byte_buf_t on m offset@zerobytesLength
			///return new byte_buf_t(m, zerobytesLength, m.length-zerobytesLength);
			byte [] ret = new byte[m.length-zerobytesLength];

			for (int i = 0; i < ret.length; i ++)
				ret[i] = m[i+zerobytesLength];

			return ret;
		}

		/*
		 * @description 
		 *   Length of public key in bytes.
		 * */
		public static final int publicKeyLength = 32;

		/*
		 * @description 
		 *   Length of secret key in bytes.
		 * */
		public static final int secretKeyLength = 32;

		/*
		 * @description 
		 *   Length of precomputed shared key in bytes.
		 * */
		public static final int sharedKeyLength = 32;

		/*
		 * @description 
		 *   Length of nonce in bytes.
		 * */
		public static final int nonceLength     = 24;

		/*
		 * @description
		 *   zero bytes in case box
		 * */
		public static final int zerobytesLength    = 32;
		/*
		 * @description
		 *   zero bytes in case open box
		 * */
		public static final int boxzerobytesLength = 16;

		/*
		 * @description 
		 *   Length of overhead added to box compared to original message.
		 * */
		public static final int overheadLength  = 16;

		public static class KeyPair {
			private byte [] publicKey;
			private byte [] secretKey;

			public KeyPair() {
				publicKey = new byte[publicKeyLength];
				secretKey = new byte[secretKeyLength];
			}

			public byte [] getPublicKey() {
				return publicKey;
			}

			public byte [] getSecretKey() {
				return secretKey;
			}
		}

		/*
		 * @description
		 *   Generates a new random key pair for box and 
		 *   returns it as an object with publicKey and secretKey members:
		 * */
		public static KeyPair keyPair() {
			KeyPair kp = new KeyPair();

			crypto_box_keypair(kp.getPublicKey(), kp.getSecretKey());
			return kp;
		}

		public static KeyPair keyPair_fromSecretKey(byte [] secretKey) {
			KeyPair kp = new KeyPair();
			byte [] sk = kp.getSecretKey();
			byte [] pk = kp.getPublicKey();

			// copy sk
			for (int i = 0; i < sk.length; i ++)
				sk[i] = secretKey[i];

			crypto_scalarmult_base(pk, sk);
			return kp;
		}

	}

	/*
	 * @description 
	 *   Secret Box algorithm, secret key
	 * */
	public static final class SecretBox {

		private final static String TAG = "SecretBox";

		private AtomicLong nonce;

		private byte [] key;

		public SecretBox(byte [] key) {
			this(key, 68);
		}

		public SecretBox(byte [] key, long nonce) {
			this.key = key;

			this.nonce = new AtomicLong(nonce);
		}

		public void setNonce(long nonce) {
			this.nonce.set(nonce);
		}
		public long getNonce() {
			return this.nonce.get();
		}
		public long incrNonce() {
			return this.nonce.incrementAndGet();
		}
		private byte[] generateNonce() {
			// generate nonce 
			long nonce = this.nonce.get();

			byte [] n = new byte[nonceLength];
			for (int i = 0; i < nonceLength; i += 8) {
				n[i+0] = (byte) (nonce>>> 0);
				n[i+1] = (byte) (nonce>>> 8);
				n[i+2] = (byte) (nonce>>>16);
				n[i+3] = (byte) (nonce>>>24);
				n[i+4] = (byte) (nonce>>>32);
				n[i+5] = (byte) (nonce>>>40);
				n[i+6] = (byte) (nonce>>>48);
				n[i+7] = (byte) (nonce>>>56);
			}

			return n;
		}

		/*
		 * @description 
		 *   Encrypt and authenticates message using the key and the nonce. 
		 *   The nonce must be unique for each distinct message for this key.
		 *   
		 *   Returns an encrypted and authenticated message, 
		 *   which is nacl.secretbox.overheadLength longer than the original message.
		 * */
		public byte [] box(byte [] message) {
			if (message==null) return null;
			return box(message, 0, message.length);
		}
		
		public byte [] box(byte [] message, final int moff) {
			if (!(message!=null && message.length>moff)) return null;
			return box(message, moff, message.length-moff);
		}
		
		public byte [] box(byte [] message, final int moff, final int mlen) {
			// check message
			if (!(message!=null && message.length>=(moff+mlen)))
				return null;
			return box(message, moff, message.length-moff, generateNonce());
		}

		public byte [] box(byte [] message, byte [] theNonce) {
			if (message==null) return null;
			return box(message, 0, message.length, theNonce);
		}
		
		public byte [] box(byte [] message, final int moff, byte [] theNonce) {
			if (!(message!=null && message.length>moff)) return null;
			return box(message, moff, message.length-moff, theNonce);
		}
		
		public byte [] box(byte [] message, final int moff, final int mlen, byte [] theNonce) {
			// check message
			if (!(message!=null && message.length>=(moff+mlen) &&
			      theNonce!=null && theNonce.length==nonceLength))
				return null;

			// message buffer
			byte [] m = new byte[mlen + zerobytesLength];

			// cipher buffer
			byte [] c = new byte[m.length];

			for (int i = 0; i < mlen; i ++)
				m[i+zerobytesLength] = message[i+moff];

			if (0 != crypto_secretbox(c, m, m.length, theNonce, key))
				return null;

			// TBD optimizing ...
			// wrap byte_buf_t on c offset@boxzerobytesLength
			///return new byte_buf_t(c, boxzerobytesLength, c.length-boxzerobytesLength);
			byte [] ret = new byte[c.length-boxzerobytesLength];

			for (int i = 0; i < ret.length; i ++)
				ret[i] = c[i+boxzerobytesLength];

			return ret;
		}

		/*
		 * @description 
		 *   Authenticates and decrypts the given secret box 
		 *   using the key and the nonce.
		 *   
		 *   Returns the original message, or null if authentication fails.
		 * */
		public byte [] open(byte [] box) {
			if (box==null) return null;
			return open(box, 0, box.length);
		}
		
		public byte [] open(byte [] box, final int boxoff) {
			if (!(box!=null && box.length>boxoff)) return null;
			return open(box, boxoff, box.length-boxoff);
		}	
		
		public byte [] open(byte [] box, final int boxoff, final int boxlen) {
			// check message
			if (!(box!=null && box.length>=(boxoff+boxlen) && boxlen>=boxzerobytesLength))
				return null;
			return open(box, boxoff, box.length-boxoff, generateNonce());
    }
    
		public byte [] open(byte [] box, byte [] theNonce) {
			if (box==null) return null;
			return open(box, 0, box.length, theNonce);
		}
		
		public byte [] open(byte [] box, final int boxoff, byte [] theNonce) {
			if (!(box!=null && box.length>boxoff)) return null;
			return open(box, boxoff, box.length-boxoff, theNonce);
		}	
		
		public byte [] open(byte [] box, final int boxoff, final int boxlen, byte [] theNonce) {
			// check message
			if (!(box!=null && box.length>=(boxoff+boxlen) && boxlen>=boxzerobytesLength &&
			      theNonce!=null && theNonce.length==nonceLength))
				return null;

			// cipher buffer
			byte [] c = new byte[boxlen + boxzerobytesLength];

			// message buffer
			byte [] m = new byte[c.length];

			for (int i = 0; i < boxlen; i++) 
				c[i+boxzerobytesLength] = box[i+boxoff];

			if (0 != crypto_secretbox_open(m, c, c.length, theNonce, key))
				return null;

			// wrap byte_buf_t on m offset@zerobytesLength
			///return new byte_buf_t(m, zerobytesLength, m.length-zerobytesLength);
			byte [] ret = new byte[m.length-zerobytesLength];

			for (int i = 0; i < ret.length; i ++)
				ret[i] = m[i+zerobytesLength];

			return ret;
		}

		/*
		 * @description 
		 *   Length of key in bytes.
		 * */
		public static final int keyLength      = 32;

		/*
		 * @description 
		 *   Length of nonce in bytes.
		 * */
		public static final int nonceLength    = 24;

		/*
		 * @description 
		 *   Length of overhead added to secret box compared to original message.
		 * */
		public static final int overheadLength = 16;

		/*
		 * @description
		 *   zero bytes in case box
		 * */
		public static final int zerobytesLength    = 32;
		/*
		 * @description
		 *   zero bytes in case open box
		 * */
		public static final int boxzerobytesLength = 16;

	}

	/*
	 * @description
	 *   Scalar multiplication, Implements curve25519.
	 * */
	public static final class ScalarMult {

		private final static String TAG = "ScalarMult";

		/*
		 * @description
		 *   Multiplies an integer n by a group element p and 
		 *   returns the resulting group element.
		 * */
		public static byte [] scalseMult(byte [] n, byte [] p) {
			if (!(n.length==scalarLength && p.length==groupElementLength))
				return null;
			
            byte [] q = new byte [scalarLength];
            
            crypto_scalarmult(q, n, p);
            
			return q;
		}

		/*
		 * @description
		 *   Multiplies an integer n by a standard group element and 
		 *   returns the resulting group element.    	 
		 * */
		public static byte [] scalseMult_base(byte [] n) {
			if (!(n.length==scalarLength))
				return null;
			
            byte [] q = new byte [scalarLength];
            
            crypto_scalarmult_base(q, n);
            
			return q;
		}

		/*
		 * @description
		 *   Length of scalar in bytes.
		 * */
		public static final int scalarLength        = 32;

		/*
		 * @description
		 *   Length of group element in bytes.
		 * */
		public static final int groupElementLength  = 32;

	}


	/*
	 * @description 
	 *   Hash algorithm, Implements SHA-512.
	 * */
	public static final class Hash {

		private final static String TAG = "Hash";

		/*
		 * @description
		 *   Returns SHA-512 hash of the message.
		 * */
		public static byte[] sha512(byte [] message) {
			if (!(message!=null && message.length>0))
				return null;

			byte [] out = new byte[hashLength];

			crypto_hash(out, message);

			return out;
		}
		public static byte[] sha512(String message) throws UnsupportedEncodingException {
			return sha512(message.getBytes("utf-8"));
		}
		
		/*
		 * @description
		 *   Length of hash in bytes.
		 * */
		public static final int hashLength       = 64;

	}


	/*
	 * @description 
	 *   Signature algorithm, Implements ed25519.
	 * */
	public static final class Signature {

		private final static String TAG = "Signature";

		private byte [] theirPublicKey;
		private byte [] mySecretKey;

		public Signature(byte [] theirPublicKey, byte [] mySecretKey) {
			this.theirPublicKey = theirPublicKey;
			this.mySecretKey = mySecretKey;
		}

		/*
		 * @description
		 *   Signs the message using the secret key and returns a signed message.
		 * */
		public byte [] sign(byte [] message) {
			if (message==null) return null;

			return sign(message, 0, message.length);
		}
		public byte [] sign(byte [] message, final int moff) {
			if (!(message!=null && message.length>moff)) return null;

			return sign(message, moff, message.length-moff);
		}
		public byte [] sign(byte [] message, final int moff, final int mlen) {
			// check message
			if (!(message!=null && message.length>=(moff+mlen)))
				return null;

			// signed message 
			byte [] sm = new byte[mlen + signatureLength];

			crypto_sign(sm, -1, message, moff, mlen, mySecretKey);

			return sm;
		}

		/*
		 * @description
		 *   Verifies the signed message and returns the message without signature.
		 *   Returns null if verification failed.
		 * */
		public byte [] open(byte [] signedMessage) {
			if (signedMessage==null) return null;

			return open(signedMessage, 0, signedMessage.length);
		}
		public byte [] open(byte [] signedMessage, final int smoff) {
			if (!(signedMessage!=null && signedMessage.length>smoff)) return null;

			return open(signedMessage, smoff, signedMessage.length-smoff);
		}
		public byte [] open(byte [] signedMessage, final int smoff, final int smlen) {
			// check sm length
			if (!(signedMessage!=null && signedMessage.length>=(smoff+smlen) && smlen>=signatureLength))
				return null;

			// temp buffer 
			byte [] tmp = new byte[smlen];

			if (0 != crypto_sign_open(tmp, -1, signedMessage, smoff, smlen, theirPublicKey))
				return null;

			// message 
			byte [] msg = new byte[smlen-signatureLength];
			for (int i = 0; i < msg.length; i ++)
				msg[i] = signedMessage[smoff+ i+signatureLength];

			return msg;
		}

		/*
		 * @description
		 *   Signs the message using the secret key and returns a signature.
		 * */
		public byte [] detached(byte [] message) {
			byte[] signedMsg = this.sign(message);
			byte[] sig = new byte[signatureLength];
			for (int i = 0; i < sig.length; i++)
				sig[i] = signedMsg[i];
			return sig;
		}

		/*
		 * @description
		 *   Verifies the signature for the message and 
		 *   returns true if verification succeeded or false if it failed.
		 * */
		public boolean detached_verify(byte [] message, byte [] signature) {
			if (signature.length != signatureLength)
				return false;
			if (theirPublicKey.length != publicKeyLength)
				return false;
			byte [] sm = new byte[signatureLength + message.length];
			byte [] m = new byte[signatureLength + message.length];
			for (int i = 0; i < signatureLength; i++)
				sm[i] = signature[i];
			for (int i = 0; i < message.length; i++)
				sm[i + signatureLength] = message[i];
			return (crypto_sign_open(m, -1, sm, 0, sm.length, theirPublicKey) >= 0);
		}

		/*
		 * @description
		 *   Generates new random key pair for signing and 
		 *   returns it as an object with publicKey and secretKey members
		 * */
		public static class KeyPair {
			private byte [] publicKey;
			private byte [] secretKey;

			public KeyPair() {
				publicKey = new byte[publicKeyLength];
				secretKey = new byte[secretKeyLength];
			}

			public byte [] getPublicKey() {
				return publicKey;
			}

			public byte [] getSecretKey() {
				return secretKey;
			}
		}

		/*
		 * @description
		 *   Signs the message using the secret key and returns a signed message.
		 * */
		public static KeyPair keyPair() {
			KeyPair kp = new KeyPair();

			crypto_sign_keypair(kp.getPublicKey(), kp.getSecretKey(), false);
			return kp;
		}
		public static KeyPair keyPair_fromSecretKey(byte [] secretKey) {
			KeyPair kp = new KeyPair();
			byte [] pk = kp.getPublicKey();
			byte [] sk = kp.getSecretKey();

			// copy sk
			for (int i = 0; i < kp.getSecretKey().length; i ++)
				sk[i] = secretKey[i];

			// copy pk from sk
			for (int i = 0; i < kp.getPublicKey().length; i ++) 
				pk[i] = secretKey[32+i]; // hard-copy

			return kp;
		}

		public static KeyPair keyPair_fromSeed(byte [] seed) {
			KeyPair kp = new KeyPair();
			byte [] pk = kp.getPublicKey();
			byte [] sk = kp.getSecretKey();

			// copy sk
			for (int i = 0; i < seedLength; i ++)
				sk[i] = seed[i];

			// generate pk from sk 
			crypto_sign_keypair(pk, sk, true);

			return kp;
		}

		/*
		 * @description
		 *   Length of signing public key in bytes.
		 * */
		public static final int publicKeyLength = 32;

		/*
		 * @description
		 *   Length of signing secret key in bytes.
		 * */
		public static final int secretKeyLength = 64;

		/*
		 * @description
		 *   Length of seed for nacl.sign.keyPair.fromSeed in bytes.
		 * */
		public static final int seedLength      = 32;

		/*
		 * @description
		 *   Length of signature in bytes.
		 * */
		public static final int signatureLength = 64;
	}


	////////////////////////////////////////////////////////////////////////////////////
	/*
	 * @description
	 *   Codes below are ported tweetnacl-fast.js from TweetNacl.c/TweetNacl.h
	 * */
	
	private static final byte [] _0 = new byte[16];
	private static final byte [] _9 = new byte[32];
	static {
		///for (int i = 0; i < _0.length; i ++) _0[i] = 0;

		///for (int i = 0; i < _9.length; i ++) _9[i] = 0;
		_9[0] = 9;
	}

	private static final long []     gf0 = new long[16];
	private static final long []     gf1 = new long[16];
	private static final long [] _121665 = new long[16];
	static {
		///for (int i = 0; i < gf0.length; i ++) gf0[i] = 0;

		///for (int i = 0; i < gf1.length; i ++)  gf1[i] = 0; 
		gf1[0] = 1;

		///for (int i = 0; i < _121665.length; i ++) _121665[i] = 0;
		_121665[0] = 0xDB41; _121665[1] = 1;
	}

	private static final long []  D = new long [] {
		0x78a3, 0x1359, 0x4dca, 0x75eb, 
		0xd8ab, 0x4141, 0x0a4d, 0x0070, 
		0xe898, 0x7779, 0x4079, 0x8cc7, 
		0xfe73, 0x2b6f, 0x6cee, 0x5203
	};
	private static final long [] D2 = new long [] {
		0xf159, 0x26b2, 0x9b94, 0xebd6, 
		0xb156, 0x8283, 0x149a, 0x00e0,
		0xd130, 0xeef3, 0x80f2, 0x198e,
		0xfce7, 0x56df, 0xd9dc, 0x2406
	};
	private static final long []  X = new long [] {
		0xd51a, 0x8f25, 0x2d60, 0xc956, 
		0xa7b2, 0x9525, 0xc760, 0x692c,
		0xdc5c, 0xfdd6, 0xe231, 0xc0a4, 
		0x53fe, 0xcd6e, 0x36d3, 0x2169
	};
	private static final long []  Y = new long [] {
		0x6658, 0x6666, 0x6666, 0x6666, 
		0x6666, 0x6666, 0x6666, 0x6666,
		0x6666, 0x6666, 0x6666, 0x6666,
		0x6666, 0x6666, 0x6666, 0x6666
	};
	private static final long []  I = new long [] {
		0xa0b0, 0x4a0e, 0x1b27, 0xc4ee,
		0xe478, 0xad2f, 0x1806, 0x2f43,
		0xd7a7, 0x3dfb, 0x0099, 0x2b4d, 
		0xdf0b, 0x4fc1, 0x2480, 0x2b83
	};

	private static void ts64(byte [] x, final int xoff, long u)
	{
		///int i;
		///for (i = 7;i >= 0;--i) { x[i+xoff] = (byte)(u&0xff); u >>>= 8; }

		x[7+xoff] = (byte)(u&0xff); u >>>= 8;
		x[6+xoff] = (byte)(u&0xff); u >>>= 8;
		x[5+xoff] = (byte)(u&0xff); u >>>= 8;
		x[4+xoff] = (byte)(u&0xff); u >>>= 8;
		x[3+xoff] = (byte)(u&0xff); u >>>= 8;
		x[2+xoff] = (byte)(u&0xff); u >>>= 8;
		x[1+xoff] = (byte)(u&0xff); u >>>= 8;
		x[0+xoff] = (byte)(u&0xff); ///u >>>= 8;
	}

	private static int vn(
			byte [] x, final int xoff, 
			byte [] y, final int yoff,
			int n)
	{
		int i,d = 0;
		for (i = 0; i < n; i ++) d |= (x[i+xoff]^y[i+yoff]) & 0xff;
		return (1 & ((d - 1) >>> 8)) - 1;
	}

	private static int crypto_verify_16(
			byte [] x, final int xoff, 
			byte [] y, final int yoff)
	{
		return vn(x,xoff,y,yoff,16);
	}
	public static int crypto_verify_16(byte [] x, byte [] y)
	{
		return crypto_verify_16(x,0, y,0);
	}

	private static int crypto_verify_32(
			byte [] x, final int xoff, 
			byte [] y, final int yoff)
	{
		return vn(x,xoff,y,yoff,32);
	}
	public static int crypto_verify_32(byte [] x, byte [] y)
	{
		return crypto_verify_32(x,0, y,0);
	}

	private static void core_salsa20(byte [] o, byte [] p, byte [] k, byte [] c) {
		int     j0  = c[ 0] & 0xff | (c[ 1] & 0xff)<<8 | (c[ 2] & 0xff)<<16 | (c[ 3] & 0xff)<<24,
				j1  = k[ 0] & 0xff | (k[ 1] & 0xff)<<8 | (k[ 2] & 0xff)<<16 | (k[ 3] & 0xff)<<24,
				j2  = k[ 4] & 0xff | (k[ 5] & 0xff)<<8 | (k[ 6] & 0xff)<<16 | (k[ 7] & 0xff)<<24,
				j3  = k[ 8] & 0xff | (k[ 9] & 0xff)<<8 | (k[10] & 0xff)<<16 | (k[11] & 0xff)<<24,
				j4  = k[12] & 0xff | (k[13] & 0xff)<<8 | (k[14] & 0xff)<<16 | (k[15] & 0xff)<<24,
				j5  = c[ 4] & 0xff | (c[ 5] & 0xff)<<8 | (c[ 6] & 0xff)<<16 | (c[ 7] & 0xff)<<24,
				j6  = p[ 0] & 0xff | (p[ 1] & 0xff)<<8 | (p[ 2] & 0xff)<<16 | (p[ 3] & 0xff)<<24,
				j7  = p[ 4] & 0xff | (p[ 5] & 0xff)<<8 | (p[ 6] & 0xff)<<16 | (p[ 7] & 0xff)<<24,
				j8  = p[ 8] & 0xff | (p[ 9] & 0xff)<<8 | (p[10] & 0xff)<<16 | (p[11] & 0xff)<<24,
				j9  = p[12] & 0xff | (p[13] & 0xff)<<8 | (p[14] & 0xff)<<16 | (p[15] & 0xff)<<24,
				j10 = c[ 8] & 0xff | (c[ 9] & 0xff)<<8 | (c[10] & 0xff)<<16 | (c[11] & 0xff)<<24,
				j11 = k[16] & 0xff | (k[17] & 0xff)<<8 | (k[18] & 0xff)<<16 | (k[19] & 0xff)<<24,
				j12 = k[20] & 0xff | (k[21] & 0xff)<<8 | (k[22] & 0xff)<<16 | (k[23] & 0xff)<<24,
				j13 = k[24] & 0xff | (k[25] & 0xff)<<8 | (k[26] & 0xff)<<16 | (k[27] & 0xff)<<24,
				j14 = k[28] & 0xff | (k[29] & 0xff)<<8 | (k[30] & 0xff)<<16 | (k[31] & 0xff)<<24,
				j15 = c[12] & 0xff | (c[13] & 0xff)<<8 | (c[14] & 0xff)<<16 | (c[15] & 0xff)<<24;

		int     x0 = j0, x1 = j1, x2 = j2, x3 = j3, x4 = j4, x5 = j5, x6 = j6, x7 = j7,
				x8 = j8, x9 = j9, x10 = j10, x11 = j11, x12 = j12, x13 = j13, x14 = j14,
				x15 = j15, u;

		for (int i = 0; i < 20; i += 2) {
			u = x0 + x12 | 0;
			x4 ^= u<<7 | u>>>(32-7);
			u = x4 + x0 | 0;
			x8 ^= u<<9 | u>>>(32-9);
			u = x8 + x4 | 0;
			x12 ^= u<<13 | u>>>(32-13);
			u = x12 + x8 | 0;
			x0 ^= u<<18 | u>>>(32-18);

			u = x5 + x1 | 0;
			x9 ^= u<<7 | u>>>(32-7);
			u = x9 + x5 | 0;
			x13 ^= u<<9 | u>>>(32-9);
			u = x13 + x9 | 0;
			x1 ^= u<<13 | u>>>(32-13);
			u = x1 + x13 | 0;
			x5 ^= u<<18 | u>>>(32-18);

			u = x10 + x6 | 0;
			x14 ^= u<<7 | u>>>(32-7);
			u = x14 + x10 | 0;
			x2 ^= u<<9 | u>>>(32-9);
			u = x2 + x14 | 0;
			x6 ^= u<<13 | u>>>(32-13);
			u = x6 + x2 | 0;
			x10 ^= u<<18 | u>>>(32-18);

			u = x15 + x11 | 0;
			x3 ^= u<<7 | u>>>(32-7);
			u = x3 + x15 | 0;
			x7 ^= u<<9 | u>>>(32-9);
			u = x7 + x3 | 0;
			x11 ^= u<<13 | u>>>(32-13);
			u = x11 + x7 | 0;
			x15 ^= u<<18 | u>>>(32-18);

			u = x0 + x3 | 0;
			x1 ^= u<<7 | u>>>(32-7);
			u = x1 + x0 | 0;
			x2 ^= u<<9 | u>>>(32-9);
			u = x2 + x1 | 0;
			x3 ^= u<<13 | u>>>(32-13);
			u = x3 + x2 | 0;
			x0 ^= u<<18 | u>>>(32-18);

			u = x5 + x4 | 0;
			x6 ^= u<<7 | u>>>(32-7);
			u = x6 + x5 | 0;
			x7 ^= u<<9 | u>>>(32-9);
			u = x7 + x6 | 0;
			x4 ^= u<<13 | u>>>(32-13);
			u = x4 + x7 | 0;
			x5 ^= u<<18 | u>>>(32-18);

			u = x10 + x9 | 0;
			x11 ^= u<<7 | u>>>(32-7);
			u = x11 + x10 | 0;
			x8 ^= u<<9 | u>>>(32-9);
			u = x8 + x11 | 0;
			x9 ^= u<<13 | u>>>(32-13);
			u = x9 + x8 | 0;
			x10 ^= u<<18 | u>>>(32-18);

			u = x15 + x14 | 0;
			x12 ^= u<<7 | u>>>(32-7);
			u = x12 + x15 | 0;
			x13 ^= u<<9 | u>>>(32-9);
			u = x13 + x12 | 0;
			x14 ^= u<<13 | u>>>(32-13);
			u = x14 + x13 | 0;
			x15 ^= u<<18 | u>>>(32-18);
		}
		x0 =  x0 +  j0 | 0;
		x1 =  x1 +  j1 | 0;
		x2 =  x2 +  j2 | 0;
		x3 =  x3 +  j3 | 0;
		x4 =  x4 +  j4 | 0;
		x5 =  x5 +  j5 | 0;
		x6 =  x6 +  j6 | 0;
		x7 =  x7 +  j7 | 0;
		x8 =  x8 +  j8 | 0;
		x9 =  x9 +  j9 | 0;
		x10 = x10 + j10 | 0;
		x11 = x11 + j11 | 0;
		x12 = x12 + j12 | 0;
		x13 = x13 + j13 | 0;
		x14 = x14 + j14 | 0;
		x15 = x15 + j15 | 0;

		o[ 0] = (byte) (x0 >>>  0 & 0xff);
		o[ 1] = (byte) (x0 >>>  8 & 0xff);
		o[ 2] = (byte) (x0 >>> 16 & 0xff);
		o[ 3] = (byte) (x0 >>> 24 & 0xff);

		o[ 4] = (byte) (x1 >>>  0 & 0xff);
		o[ 5] = (byte) (x1 >>>  8 & 0xff);
		o[ 6] = (byte) (x1 >>> 16 & 0xff);
		o[ 7] = (byte) (x1 >>> 24 & 0xff);

		o[ 8] = (byte) (x2 >>>  0 & 0xff);
		o[ 9] = (byte) (x2 >>>  8 & 0xff);
		o[10] = (byte) (x2 >>> 16 & 0xff);
		o[11] = (byte) (x2 >>> 24 & 0xff);

		o[12] = (byte) (x3 >>>  0 & 0xff);
		o[13] = (byte) (x3 >>>  8 & 0xff);
		o[14] = (byte) (x3 >>> 16 & 0xff);
		o[15] = (byte) (x3 >>> 24 & 0xff);

		o[16] = (byte) (x4 >>>  0 & 0xff);
		o[17] = (byte) (x4 >>>  8 & 0xff);
		o[18] = (byte) (x4 >>> 16 & 0xff);
		o[19] = (byte) (x4 >>> 24 & 0xff);

		o[20] = (byte) (x5 >>>  0 & 0xff);
		o[21] = (byte) (x5 >>>  8 & 0xff);
		o[22] = (byte) (x5 >>> 16 & 0xff);
		o[23] = (byte) (x5 >>> 24 & 0xff);

		o[24] = (byte) (x6 >>>  0 & 0xff);
		o[25] = (byte) (x6 >>>  8 & 0xff);
		o[26] = (byte) (x6 >>> 16 & 0xff);
		o[27] = (byte) (x6 >>> 24 & 0xff);

		o[28] = (byte) (x7 >>>  0 & 0xff);
		o[29] = (byte) (x7 >>>  8 & 0xff);
		o[30] = (byte) (x7 >>> 16 & 0xff);
		o[31] = (byte) (x7 >>> 24 & 0xff);

		o[32] = (byte) (x8 >>>  0 & 0xff);
		o[33] = (byte) (x8 >>>  8 & 0xff);
		o[34] = (byte) (x8 >>> 16 & 0xff);
		o[35] = (byte) (x8 >>> 24 & 0xff);

		o[36] = (byte) (x9 >>>  0 & 0xff);
		o[37] = (byte) (x9 >>>  8 & 0xff);
		o[38] = (byte) (x9 >>> 16 & 0xff);
		o[39] = (byte) (x9 >>> 24 & 0xff);

		o[40] = (byte) (x10 >>>  0 & 0xff);
		o[41] = (byte) (x10 >>>  8 & 0xff);
		o[42] = (byte) (x10 >>> 16 & 0xff);
		o[43] = (byte) (x10 >>> 24 & 0xff);

		o[44] = (byte) (x11 >>>  0 & 0xff);
		o[45] = (byte) (x11 >>>  8 & 0xff);
		o[46] = (byte) (x11 >>> 16 & 0xff);
		o[47] = (byte) (x11 >>> 24 & 0xff);

		o[48] = (byte) (x12 >>>  0 & 0xff);
		o[49] = (byte) (x12 >>>  8 & 0xff);
		o[50] = (byte) (x12 >>> 16 & 0xff);
		o[51] = (byte) (x12 >>> 24 & 0xff);

		o[52] = (byte) (x13 >>>  0 & 0xff);
		o[53] = (byte) (x13 >>>  8 & 0xff);
		o[54] = (byte) (x13 >>> 16 & 0xff);
		o[55] = (byte) (x13 >>> 24 & 0xff);

		o[56] = (byte) (x14 >>>  0 & 0xff);
		o[57] = (byte) (x14 >>>  8 & 0xff);
		o[58] = (byte) (x14 >>> 16 & 0xff);
		o[59] = (byte) (x14 >>> 24 & 0xff);

		o[60] = (byte) (x15 >>>  0 & 0xff);
		o[61] = (byte) (x15 >>>  8 & 0xff);
		o[62] = (byte) (x15 >>> 16 & 0xff);
		o[63] = (byte) (x15 >>> 24 & 0xff);
		
		/*String dbgt = "";
		for (int dbg = 0; dbg < o.length; dbg ++) dbgt += " "+o[dbg];
		Log.d(TAG, "core_salsa20 -> "+dbgt);
*/
	}

	private static void core_hsalsa20(byte [] o, byte [] p, byte [] k, byte [] c) {
		int     j0  = c[ 0] & 0xff | (c[ 1] & 0xff)<<8 | (c[ 2] & 0xff)<<16 | (c[ 3] & 0xff)<<24,
				j1  = k[ 0] & 0xff | (k[ 1] & 0xff)<<8 | (k[ 2] & 0xff)<<16 | (k[ 3] & 0xff)<<24,
				j2  = k[ 4] & 0xff | (k[ 5] & 0xff)<<8 | (k[ 6] & 0xff)<<16 | (k[ 7] & 0xff)<<24,
				j3  = k[ 8] & 0xff | (k[ 9] & 0xff)<<8 | (k[10] & 0xff)<<16 | (k[11] & 0xff)<<24,
				j4  = k[12] & 0xff | (k[13] & 0xff)<<8 | (k[14] & 0xff)<<16 | (k[15] & 0xff)<<24,
				j5  = c[ 4] & 0xff | (c[ 5] & 0xff)<<8 | (c[ 6] & 0xff)<<16 | (c[ 7] & 0xff)<<24,
				j6  = p[ 0] & 0xff | (p[ 1] & 0xff)<<8 | (p[ 2] & 0xff)<<16 | (p[ 3] & 0xff)<<24,
				j7  = p[ 4] & 0xff | (p[ 5] & 0xff)<<8 | (p[ 6] & 0xff)<<16 | (p[ 7] & 0xff)<<24,
				j8  = p[ 8] & 0xff | (p[ 9] & 0xff)<<8 | (p[10] & 0xff)<<16 | (p[11] & 0xff)<<24,
				j9  = p[12] & 0xff | (p[13] & 0xff)<<8 | (p[14] & 0xff)<<16 | (p[15] & 0xff)<<24,
				j10 = c[ 8] & 0xff | (c[ 9] & 0xff)<<8 | (c[10] & 0xff)<<16 | (c[11] & 0xff)<<24,
				j11 = k[16] & 0xff | (k[17] & 0xff)<<8 | (k[18] & 0xff)<<16 | (k[19] & 0xff)<<24,
				j12 = k[20] & 0xff | (k[21] & 0xff)<<8 | (k[22] & 0xff)<<16 | (k[23] & 0xff)<<24,
				j13 = k[24] & 0xff | (k[25] & 0xff)<<8 | (k[26] & 0xff)<<16 | (k[27] & 0xff)<<24,
				j14 = k[28] & 0xff | (k[29] & 0xff)<<8 | (k[30] & 0xff)<<16 | (k[31] & 0xff)<<24,
				j15 = c[12] & 0xff | (c[13] & 0xff)<<8 | (c[14] & 0xff)<<16 | (c[15] & 0xff)<<24;

		int     x0 = j0, x1 = j1, x2 = j2, x3 = j3, x4 = j4, x5 = j5, x6 = j6, x7 = j7,
				x8 = j8, x9 = j9, x10 = j10, x11 = j11, x12 = j12, x13 = j13, x14 = j14,
				x15 = j15, u;

		for (int i = 0; i < 20; i += 2) {
			u = x0 + x12 | 0;
			x4 ^= u<<7 | u>>>(32-7);
			u = x4 + x0 | 0;
			x8 ^= u<<9 | u>>>(32-9);
			u = x8 + x4 | 0;
			x12 ^= u<<13 | u>>>(32-13);
			u = x12 + x8 | 0;
			x0 ^= u<<18 | u>>>(32-18);

			u = x5 + x1 | 0;
			x9 ^= u<<7 | u>>>(32-7);
			u = x9 + x5 | 0;
			x13 ^= u<<9 | u>>>(32-9);
			u = x13 + x9 | 0;
			x1 ^= u<<13 | u>>>(32-13);
			u = x1 + x13 | 0;
			x5 ^= u<<18 | u>>>(32-18);

			u = x10 + x6 | 0;
			x14 ^= u<<7 | u>>>(32-7);
			u = x14 + x10 | 0;
			x2 ^= u<<9 | u>>>(32-9);
			u = x2 + x14 | 0;
			x6 ^= u<<13 | u>>>(32-13);
			u = x6 + x2 | 0;
			x10 ^= u<<18 | u>>>(32-18);

			u = x15 + x11 | 0;
			x3 ^= u<<7 | u>>>(32-7);
			u = x3 + x15 | 0;
			x7 ^= u<<9 | u>>>(32-9);
			u = x7 + x3 | 0;
			x11 ^= u<<13 | u>>>(32-13);
			u = x11 + x7 | 0;
			x15 ^= u<<18 | u>>>(32-18);

			u = x0 + x3 | 0;
			x1 ^= u<<7 | u>>>(32-7);
			u = x1 + x0 | 0;
			x2 ^= u<<9 | u>>>(32-9);
			u = x2 + x1 | 0;
			x3 ^= u<<13 | u>>>(32-13);
			u = x3 + x2 | 0;
			x0 ^= u<<18 | u>>>(32-18);

			u = x5 + x4 | 0;
			x6 ^= u<<7 | u>>>(32-7);
			u = x6 + x5 | 0;
			x7 ^= u<<9 | u>>>(32-9);
			u = x7 + x6 | 0;
			x4 ^= u<<13 | u>>>(32-13);
			u = x4 + x7 | 0;
			x5 ^= u<<18 | u>>>(32-18);

			u = x10 + x9 | 0;
			x11 ^= u<<7 | u>>>(32-7);
			u = x11 + x10 | 0;
			x8 ^= u<<9 | u>>>(32-9);
			u = x8 + x11 | 0;
			x9 ^= u<<13 | u>>>(32-13);
			u = x9 + x8 | 0;
			x10 ^= u<<18 | u>>>(32-18);

			u = x15 + x14 | 0;
			x12 ^= u<<7 | u>>>(32-7);
			u = x12 + x15 | 0;
			x13 ^= u<<9 | u>>>(32-9);
			u = x13 + x12 | 0;
			x14 ^= u<<13 | u>>>(32-13);
			u = x14 + x13 | 0;
			x15 ^= u<<18 | u>>>(32-18);
		}

		o[ 0] = (byte) (x0 >>>  0 & 0xff);
		o[ 1] = (byte) (x0 >>>  8 & 0xff);
		o[ 2] = (byte) (x0 >>> 16 & 0xff);
		o[ 3] = (byte) (x0 >>> 24 & 0xff);

		o[ 4] = (byte) (x5 >>>  0 & 0xff);
		o[ 5] = (byte) (x5 >>>  8 & 0xff);
		o[ 6] = (byte) (x5 >>> 16 & 0xff);
		o[ 7] = (byte) (x5 >>> 24 & 0xff);

		o[ 8] = (byte) (x10 >>>  0 & 0xff);
		o[ 9] = (byte) (x10 >>>  8 & 0xff);
		o[10] = (byte) (x10 >>> 16 & 0xff);
		o[11] = (byte) (x10 >>> 24 & 0xff);

		o[12] = (byte) (x15 >>>  0 & 0xff);
		o[13] = (byte) (x15 >>>  8 & 0xff);
		o[14] = (byte) (x15 >>> 16 & 0xff);
		o[15] = (byte) (x15 >>> 24 & 0xff);

		o[16] = (byte) (x6 >>>  0 & 0xff);
		o[17] = (byte) (x6 >>>  8 & 0xff);
		o[18] = (byte) (x6 >>> 16 & 0xff);
		o[19] = (byte) (x6 >>> 24 & 0xff);

		o[20] = (byte) (x7 >>>  0 & 0xff);
		o[21] = (byte) (x7 >>>  8 & 0xff);
		o[22] = (byte) (x7 >>> 16 & 0xff);
		o[23] = (byte) (x7 >>> 24 & 0xff);

		o[24] = (byte) (x8 >>>  0 & 0xff);
		o[25] = (byte) (x8 >>>  8 & 0xff);
		o[26] = (byte) (x8 >>> 16 & 0xff);
		o[27] = (byte) (x8 >>> 24 & 0xff);

		o[28] = (byte) (x9 >>>  0 & 0xff);
		o[29] = (byte) (x9 >>>  8 & 0xff);
		o[30] = (byte) (x9 >>> 16 & 0xff);
		o[31] = (byte) (x9 >>> 24 & 0xff);


		/*String dbgt = "";
		for (int dbg = 0; dbg < o.length; dbg ++) dbgt += " "+o[dbg];
		Log.d(TAG, "core_hsalsa20 -> "+dbgt);
*/
	}

	public static int crypto_core_salsa20(byte [] out, byte [] in, byte [] k, byte [] c)
	{
		///core(out,in,k,c,0);
		core_salsa20(out,in,k,c);

		///String dbgt = "";
		///for (int dbg = 0; dbg < out.length; dbg ++) dbgt += " "+out[dbg];
		///L/og.d(TAG, "crypto_core_salsa20 -> "+dbgt);

		return 0;
	}

	public static int crypto_core_hsalsa20(byte [] out, byte [] in, byte [] k, byte [] c)
	{
		///core(out,in,k,c,1);
		core_hsalsa20(out,in,k,c);

		///String dbgt = "";
		///for (int dbg = 0; dbg < out.length; dbg ++) dbgt += " "+out[dbg];
		///L/og.d(TAG, "crypto_core_hsalsa20 -> "+dbgt);

		return 0;
	}
	
    // "expand 32-byte k"
	private static final byte[] sigma = {101, 120, 112, 97, 110, 100, 32, 51, 50, 45, 98, 121, 116, 101, 32, 107};
	
	/*static {
		try {
			sigma = "expand 32-byte k".getBytes("utf-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
	}*/

	private static int crypto_stream_salsa20_xor(byte [] c,int cpos, byte [] m,int mpos, long b, byte [] n, byte [] k)
	{
		byte [] z = new byte[16], x = new byte[64];
		int u, i;
		for (i = 0; i < 16; i++) z[i] = 0;
		for (i = 0; i < 8; i++) z[i] = n[i];
		while (b >= 64) {
			crypto_core_salsa20(x,z,k,sigma);
			for (i = 0; i < 64; i++) c[cpos+i] = (byte) ((m[mpos+i] ^ x[i]) & 0xff);
			u = 1;
			for (i = 8; i < 16; i++) {
				u = u + (z[i] & 0xff) | 0;
				z[i] = (byte) (u & 0xff);
				u >>>= 8;
			}
			b -= 64;
			cpos += 64;
			mpos += 64;
		}
		if (b > 0) {
			crypto_core_salsa20(x,z,k,sigma);
			for (i = 0; i < b; i++) c[cpos+i] = (byte) ((m[mpos+i] ^ x[i]) & 0xff);
		}
		
		///String dbgt = "";
		///for (int dbg = 0; dbg < c.length-cpos; dbg ++) dbgt += " "+c[dbg +cpos];
		///Log.d(TAG, "crypto_stream_salsa20_xor, c -> "+dbgt);
		
		return 0;
	}

	public static int crypto_stream_salsa20(byte [] c,int cpos, long b, byte [] n, byte [] k) {
		byte [] z = new byte[16], x = new byte[64];
		int u, i;
		for (i = 0; i < 16; i++) z[i] = 0;
		for (i = 0; i < 8; i++) z[i] = n[i];
		while (b >= 64) {
			crypto_core_salsa20(x,z,k,sigma);
			for (i = 0; i < 64; i++) c[cpos+i] = x[i];
			u = 1;
			for (i = 8; i < 16; i++) {
				u = u + (z[i] & 0xff) | 0;
				z[i] = (byte) (u & 0xff);
				u >>>= 8;
			}
			b -= 64;
			cpos += 64;
		}
		if (b > 0) {
			crypto_core_salsa20(x,z,k,sigma);
			for (i = 0; i < b; i++) c[cpos+i] = x[i];
		}

		///String dbgt = "";
		///for (int dbg = 0; dbg < c.length-cpos; dbg ++) dbgt += " "+c[dbg +cpos];
		///Log.d(TAG, "crypto_stream_salsa20, c -> "+dbgt);
		
		return 0;
	}

	public static int  crypto_stream(byte [] c,int cpos, long d, byte [] n, byte [] k) {
		byte [] s = new byte[32];
		crypto_core_hsalsa20(s,n,k,sigma);
		byte [] sn = new byte[8];
		for (int i = 0; i < 8; i++) sn[i] = n[i+16];
		return crypto_stream_salsa20(c,cpos,d,sn,s);
	}

	public static int crypto_stream_xor(byte [] c,int cpos, byte [] m,int mpos, long d, byte [] n, byte [] k) {
		byte [] s = new byte[32];
		
		/*String dbgt = "";
		for (int dbg = 0; dbg < n.length; dbg ++) dbgt += " "+n[dbg];
		Log.d(TAG, "crypto_stream_xor, nonce -> "+dbgt);
		
		dbgt = "";
		for (int dbg = 0; dbg < k.length; dbg ++) dbgt += " "+k[dbg];
		Log.d(TAG, "crypto_stream_xor, shk -> "+dbgt);
		*/
		
		crypto_core_hsalsa20(s,n,k,sigma);
		byte [] sn = new byte[8];
		for (int i = 0; i < 8; i++) sn[i] = n[i+16];
		return crypto_stream_salsa20_xor(c,cpos,m,mpos,d,sn,s);
	}
	
	/*
	* Port of Andrew Moon's Poly1305-donna-16. Public domain.
	* https://github.com/floodyberry/poly1305-donna
	*/
	public static final class poly1305 {

		private byte[] buffer;
		private int[] r;
		private int[] h;
		private int[] pad;
		private int leftover;
		private int fin;

		public poly1305(byte [] key) {
			this.buffer = new byte[16];
			this.r = new int[10];
			this.h = new int[10];
			this.pad = new int[8];
			this.leftover = 0;
			this.fin = 0;

			int t0, t1, t2, t3, t4, t5, t6, t7;

			t0 = key[ 0] & 0xff | (key[ 1] & 0xff) << 8; this.r[0] = ( t0                     ) & 0x1fff;
			t1 = key[ 2] & 0xff | (key[ 3] & 0xff) << 8; this.r[1] = ((t0 >>> 13) | (t1 <<  3)) & 0x1fff;
			t2 = key[ 4] & 0xff | (key[ 5] & 0xff) << 8; this.r[2] = ((t1 >>> 10) | (t2 <<  6)) & 0x1f03;
			t3 = key[ 6] & 0xff | (key[ 7] & 0xff) << 8; this.r[3] = ((t2 >>>  7) | (t3 <<  9)) & 0x1fff;
			t4 = key[ 8] & 0xff | (key[ 9] & 0xff) << 8; this.r[4] = ((t3 >>>  4) | (t4 << 12)) & 0x00ff;
			this.r[5] = ((t4 >>>  1)) & 0x1ffe;
			t5 = key[10] & 0xff | (key[11] & 0xff) << 8; this.r[6] = ((t4 >>> 14) | (t5 <<  2)) & 0x1fff;
			t6 = key[12] & 0xff | (key[13] & 0xff) << 8; this.r[7] = ((t5 >>> 11) | (t6 <<  5)) & 0x1f81;
			t7 = key[14] & 0xff | (key[15] & 0xff) << 8; this.r[8] = ((t6 >>>  8) | (t7 <<  8)) & 0x1fff;
			this.r[9] = ((t7 >>>  5)) & 0x007f;

			this.pad[0] = key[16] & 0xff | (key[17] & 0xff) << 8;
			this.pad[1] = key[18] & 0xff | (key[19] & 0xff) << 8;
			this.pad[2] = key[20] & 0xff | (key[21] & 0xff) << 8;
			this.pad[3] = key[22] & 0xff | (key[23] & 0xff) << 8;
			this.pad[4] = key[24] & 0xff | (key[25] & 0xff) << 8;
			this.pad[5] = key[26] & 0xff | (key[27] & 0xff) << 8;
			this.pad[6] = key[28] & 0xff | (key[29] & 0xff) << 8;
			this.pad[7] = key[30] & 0xff | (key[31] & 0xff) << 8;
		}

		public poly1305 blocks(byte [] m, int mpos, int bytes) {
			int hibit = this.fin!=0 ? 0 : (1 << 11);
			int t0, t1, t2, t3, t4, t5, t6, t7, c;
			int d0, d1, d2, d3, d4, d5, d6, d7, d8, d9;

			int     h0 = this.h[0],
					h1 = this.h[1],
					h2 = this.h[2],
					h3 = this.h[3],
					h4 = this.h[4],
					h5 = this.h[5],
					h6 = this.h[6],
					h7 = this.h[7],
					h8 = this.h[8],
					h9 = this.h[9];

			int     r0 = this.r[0],
					r1 = this.r[1],
					r2 = this.r[2],
					r3 = this.r[3],
					r4 = this.r[4],
					r5 = this.r[5],
					r6 = this.r[6],
					r7 = this.r[7],
					r8 = this.r[8],
					r9 = this.r[9];

			while (bytes >= 16) {
				t0 = m[mpos+ 0] & 0xff | (m[mpos+ 1] & 0xff) << 8; h0 += ( t0                     ) & 0x1fff;
				t1 = m[mpos+ 2] & 0xff | (m[mpos+ 3] & 0xff) << 8; h1 += ((t0 >>> 13) | (t1 <<  3)) & 0x1fff;
				t2 = m[mpos+ 4] & 0xff | (m[mpos+ 5] & 0xff) << 8; h2 += ((t1 >>> 10) | (t2 <<  6)) & 0x1fff;
				t3 = m[mpos+ 6] & 0xff | (m[mpos+ 7] & 0xff) << 8; h3 += ((t2 >>>  7) | (t3 <<  9)) & 0x1fff;
				t4 = m[mpos+ 8] & 0xff | (m[mpos+ 9] & 0xff) << 8; h4 += ((t3 >>>  4) | (t4 << 12)) & 0x1fff;
				h5 += ((t4 >>>  1)) & 0x1fff;
				t5 = m[mpos+10] & 0xff | (m[mpos+11] & 0xff) << 8; h6 += ((t4 >>> 14) | (t5 <<  2)) & 0x1fff;
				t6 = m[mpos+12] & 0xff | (m[mpos+13] & 0xff) << 8; h7 += ((t5 >>> 11) | (t6 <<  5)) & 0x1fff;
				t7 = m[mpos+14] & 0xff | (m[mpos+15] & 0xff) << 8; h8 += ((t6 >>>  8) | (t7 <<  8)) & 0x1fff;
				h9 += ((t7 >>> 5)) | hibit;

				c = 0;

				d0 = c;
				d0 += h0 * r0;
				d0 += h1 * (5 * r9);
				d0 += h2 * (5 * r8);
				d0 += h3 * (5 * r7);
				d0 += h4 * (5 * r6);
				c = (d0 >>> 13); d0 &= 0x1fff;
				d0 += h5 * (5 * r5);
				d0 += h6 * (5 * r4);
				d0 += h7 * (5 * r3);
				d0 += h8 * (5 * r2);
				d0 += h9 * (5 * r1);
				c += (d0 >>> 13); d0 &= 0x1fff;

				d1 = c;
				d1 += h0 * r1;
				d1 += h1 * r0;
				d1 += h2 * (5 * r9);
				d1 += h3 * (5 * r8);
				d1 += h4 * (5 * r7);
				c = (d1 >>> 13); d1 &= 0x1fff;
				d1 += h5 * (5 * r6);
				d1 += h6 * (5 * r5);
				d1 += h7 * (5 * r4);
				d1 += h8 * (5 * r3);
				d1 += h9 * (5 * r2);
				c += (d1 >>> 13); d1 &= 0x1fff;

				d2 = c;
				d2 += h0 * r2;
				d2 += h1 * r1;
				d2 += h2 * r0;
				d2 += h3 * (5 * r9);
				d2 += h4 * (5 * r8);
				c = (d2 >>> 13); d2 &= 0x1fff;
				d2 += h5 * (5 * r7);
				d2 += h6 * (5 * r6);
				d2 += h7 * (5 * r5);
				d2 += h8 * (5 * r4);
				d2 += h9 * (5 * r3);
				c += (d2 >>> 13); d2 &= 0x1fff;

				d3 = c;
				d3 += h0 * r3;
				d3 += h1 * r2;
				d3 += h2 * r1;
				d3 += h3 * r0;
				d3 += h4 * (5 * r9);
				c = (d3 >>> 13); d3 &= 0x1fff;
				d3 += h5 * (5 * r8);
				d3 += h6 * (5 * r7);
				d3 += h7 * (5 * r6);
				d3 += h8 * (5 * r5);
				d3 += h9 * (5 * r4);
				c += (d3 >>> 13); d3 &= 0x1fff;

				d4 = c;
				d4 += h0 * r4;
				d4 += h1 * r3;
				d4 += h2 * r2;
				d4 += h3 * r1;
				d4 += h4 * r0;
				c = (d4 >>> 13); d4 &= 0x1fff;
				d4 += h5 * (5 * r9);
				d4 += h6 * (5 * r8);
				d4 += h7 * (5 * r7);
				d4 += h8 * (5 * r6);
				d4 += h9 * (5 * r5);
				c += (d4 >>> 13); d4 &= 0x1fff;

				d5 = c;
				d5 += h0 * r5;
				d5 += h1 * r4;
				d5 += h2 * r3;
				d5 += h3 * r2;
				d5 += h4 * r1;
				c = (d5 >>> 13); d5 &= 0x1fff;
				d5 += h5 * r0;
				d5 += h6 * (5 * r9);
				d5 += h7 * (5 * r8);
				d5 += h8 * (5 * r7);
				d5 += h9 * (5 * r6);
				c += (d5 >>> 13); d5 &= 0x1fff;

				d6 = c;
				d6 += h0 * r6;
				d6 += h1 * r5;
				d6 += h2 * r4;
				d6 += h3 * r3;
				d6 += h4 * r2;
				c = (d6 >>> 13); d6 &= 0x1fff;
				d6 += h5 * r1;
				d6 += h6 * r0;
				d6 += h7 * (5 * r9);
				d6 += h8 * (5 * r8);
				d6 += h9 * (5 * r7);
				c += (d6 >>> 13); d6 &= 0x1fff;

				d7 = c;
				d7 += h0 * r7;
				d7 += h1 * r6;
				d7 += h2 * r5;
				d7 += h3 * r4;
				d7 += h4 * r3;
				c = (d7 >>> 13); d7 &= 0x1fff;
				d7 += h5 * r2;
				d7 += h6 * r1;
				d7 += h7 * r0;
				d7 += h8 * (5 * r9);
				d7 += h9 * (5 * r8);
				c += (d7 >>> 13); d7 &= 0x1fff;

				d8 = c;
				d8 += h0 * r8;
				d8 += h1 * r7;
				d8 += h2 * r6;
				d8 += h3 * r5;
				d8 += h4 * r4;
				c = (d8 >>> 13); d8 &= 0x1fff;
				d8 += h5 * r3;
				d8 += h6 * r2;
				d8 += h7 * r1;
				d8 += h8 * r0;
				d8 += h9 * (5 * r9);
				c += (d8 >>> 13); d8 &= 0x1fff;

				d9 = c;
				d9 += h0 * r9;
				d9 += h1 * r8;
				d9 += h2 * r7;
				d9 += h3 * r6;
				d9 += h4 * r5;
				c = (d9 >>> 13); d9 &= 0x1fff;
				d9 += h5 * r4;
				d9 += h6 * r3;
				d9 += h7 * r2;
				d9 += h8 * r1;
				d9 += h9 * r0;
				c += (d9 >>> 13); d9 &= 0x1fff;

				c = (((c << 2) + c)) | 0;
				c = (c + d0) | 0;
				d0 = c & 0x1fff;
				c = (c >>> 13);
				d1 += c;

				h0 = d0;
				h1 = d1;
				h2 = d2;
				h3 = d3;
				h4 = d4;
				h5 = d5;
				h6 = d6;
				h7 = d7;
				h8 = d8;
				h9 = d9;

				mpos += 16;
				bytes -= 16;
			}
			this.h[0] = h0;
			this.h[1] = h1;
			this.h[2] = h2;
			this.h[3] = h3;
			this.h[4] = h4;
			this.h[5] = h5;
			this.h[6] = h6;
			this.h[7] = h7;
			this.h[8] = h8;
			this.h[9] = h9;

			return this;
		}

		public poly1305 finish(byte [] mac, int macpos) {
			int [] g = new int[10];
			int c, mask, f, i;

			if (this.leftover != 0) {
				i = this.leftover;
				this.buffer[i++] = 1;
				for (; i < 16; i++) this.buffer[i] = 0;
				this.fin = 1;
				this.blocks(this.buffer, 0, 16);
			}

			c = this.h[1] >>> 13;
			this.h[1] &= 0x1fff;
			for (i = 2; i < 10; i++) {
				this.h[i] += c;
				c = this.h[i] >>> 13;
				this.h[i] &= 0x1fff;
			}
			this.h[0] += (c * 5);
			c = this.h[0] >>> 13;
			this.h[0] &= 0x1fff;
			this.h[1] += c;
			c = this.h[1] >>> 13;
			this.h[1] &= 0x1fff;
			this.h[2] += c;

			g[0] = this.h[0] + 5;
			c = g[0] >>> 13;
			g[0] &= 0x1fff;
			for (i = 1; i < 10; i++) {
				g[i] = this.h[i] + c;
				c = g[i] >>> 13;
				g[i] &= 0x1fff;
			}
			g[9] -= (1 << 13); g[9] &= 0xffff;

                        /*
                        backport from tweetnacl-fast.js https://github.com/dchest/tweetnacl-js/releases/tag/v0.14.3
                        <<<
                        "The issue was not properly detecting if st->h was >= 2^130 - 5, 
                        coupled with [testing mistake] not catching the failure.
                        The chance of the bug affecting anything in the real world is essentially zero luckily, 
                        but it's good to have it fixed."
                        >>>
                        */
			///change mask = (g[9] >>> ((2 * 8) - 1)) - 1; to as 
			mask = (c ^ 1) - 1;
			mask &= 0xffff;
			///////////////////////////////////////
			
			for (i = 0; i < 10; i++) g[i] &= mask;
			mask = ~mask;
			for (i = 0; i < 10; i++) this.h[i] = (this.h[i] & mask) | g[i];

			this.h[0] = ((this.h[0]       ) | (this.h[1] << 13)                    ) & 0xffff;
			this.h[1] = ((this.h[1] >>>  3) | (this.h[2] << 10)                    ) & 0xffff;
			this.h[2] = ((this.h[2] >>>  6) | (this.h[3] <<  7)                    ) & 0xffff;
			this.h[3] = ((this.h[3] >>>  9) | (this.h[4] <<  4)                    ) & 0xffff;
			this.h[4] = ((this.h[4] >>> 12) | (this.h[5] <<  1) | (this.h[6] << 14)) & 0xffff;
			this.h[5] = ((this.h[6] >>>  2) | (this.h[7] << 11)                    ) & 0xffff;
			this.h[6] = ((this.h[7] >>>  5) | (this.h[8] <<  8)                    ) & 0xffff;
			this.h[7] = ((this.h[8] >>>  8) | (this.h[9] <<  5)                    ) & 0xffff;

			f = this.h[0] + this.pad[0];
			this.h[0] = f & 0xffff;
			for (i = 1; i < 8; i++) {
				f = (((this.h[i] + this.pad[i]) | 0) + (f >>> 16)) | 0;
				this.h[i] = f & 0xffff;
			}

			mac[macpos+ 0] = (byte) ((this.h[0] >>> 0) & 0xff);
			mac[macpos+ 1] = (byte) ((this.h[0] >>> 8) & 0xff);
			mac[macpos+ 2] = (byte) ((this.h[1] >>> 0) & 0xff);
			mac[macpos+ 3] = (byte) ((this.h[1] >>> 8) & 0xff);
			mac[macpos+ 4] = (byte) ((this.h[2] >>> 0) & 0xff);
			mac[macpos+ 5] = (byte) ((this.h[2] >>> 8) & 0xff);
			mac[macpos+ 6] = (byte) ((this.h[3] >>> 0) & 0xff);
			mac[macpos+ 7] = (byte) ((this.h[3] >>> 8) & 0xff);
			mac[macpos+ 8] = (byte) ((this.h[4] >>> 0) & 0xff);
			mac[macpos+ 9] = (byte) ((this.h[4] >>> 8) & 0xff);
			mac[macpos+10] = (byte) ((this.h[5] >>> 0) & 0xff);
			mac[macpos+11] = (byte) ((this.h[5] >>> 8) & 0xff);
			mac[macpos+12] = (byte) ((this.h[6] >>> 0) & 0xff);
			mac[macpos+13] = (byte) ((this.h[6] >>> 8) & 0xff);
			mac[macpos+14] = (byte) ((this.h[7] >>> 0) & 0xff);
			mac[macpos+15] = (byte) ((this.h[7] >>> 8) & 0xff);

			return this;
		}

		public poly1305 update(byte [] m, int mpos, int bytes) {
			int i, want;

			if (this.leftover != 0) {
				want = (16 - this.leftover);
				if (want > bytes)
					want = bytes;
				for (i = 0; i < want; i++)
					this.buffer[this.leftover + i] = m[mpos+i];
				bytes -= want;
				mpos += want;
				this.leftover += want;
				if (this.leftover < 16)
					return this;
				this.blocks(buffer, 0, 16);
				this.leftover = 0;
			}

			if (bytes >= 16) {
				want = bytes - (bytes % 16);
				this.blocks(m, mpos, want);
				mpos += want;
				bytes -= want;
			}

			if (bytes != 0) {
				for (i = 0; i < bytes; i++)
					this.buffer[this.leftover + i] = m[mpos+i];
				this.leftover += bytes;
			}

			return this;
		}

	}
    
	private static int crypto_onetimeauth(
			byte[] out,final int outpos,
			byte[] m,final int mpos,
			int n,
			byte [] k)
	{
		poly1305 s = new poly1305(k);
		s.update(m, mpos, n);
		s.finish(out, outpos);
		
		/*String dbgt = "";
		for (int dbg = 0; dbg < out.length-outpos; dbg ++) dbgt += " "+out[dbg+outpos];
		Log.d(TAG, "crypto_onetimeauth -> "+dbgt);
		*/
		
		return 0;
	}
	public static int crypto_onetimeauth(byte [] out, byte [] m, int /*long*/ n , byte [] k) {
		return crypto_onetimeauth(out,0, m,0, n, k);
	}

	private static int crypto_onetimeauth_verify(
			byte[] h,final int hoff,
			byte[] m,final int moff,
			int /*long*/ n,
			byte [] k)
	{
		byte [] x = new byte[16];
		crypto_onetimeauth(x,0,m,moff,n,k);
		return crypto_verify_16(h,hoff,x,0);
	}
	public static int crypto_onetimeauth_verify(byte [] h, byte [] m, int /*long*/ n, byte [] k) {
		return crypto_onetimeauth_verify(h,0, m,0, n, k);
	}
	public static int crypto_onetimeauth_verify(byte [] h, byte [] m, byte [] k) {
		return crypto_onetimeauth_verify(h, m, m!=null? m.length:0, k);
	}

	public static int crypto_secretbox(byte [] c, byte [] m, int /*long*/ d, byte [] n, byte [] k)
	{
		int i;
		if (d < 32) return -1;
		crypto_stream_xor(c,0,m,0,d,n,k);
		crypto_onetimeauth(c,16, c,32, d-32, c);
		///for (i = 0; i < 16; i++) c[i] = 0;
		return 0;
	}

	public static int crypto_secretbox_open(byte []m,byte []c,int /*long*/ d,byte []n,byte []k)
	{
		int i;
		byte[] x = new byte[32];
		if (d < 32) return -1;
		crypto_stream(x,0,32,n,k);
		if (crypto_onetimeauth_verify(c,16, c,32, d-32, x) != 0) return -1;
		crypto_stream_xor(m,0,c,0,d,n,k);
		///for (i = 0; i < 32; i++) m[i] = 0;
		return 0;
	}

	private static void set25519(long [] r, long [] a)
	{
		int i;
		for (i = 0; i < 16; i ++) r[i]=a[i];
	}

	private static void car25519(long [] o)
	{
		int i;
		long v, c = 1;
		for (i = 0; i < 16; i++) {
			v = o[i] + c + 65535;
			c = v>>16;
			o[i] = v - c * 65536;
		}
		o[0] += c-1 + 37 * (c-1);
	}
	
	private static void sel25519(
			long[] p,
			long[] q,
			int b)
	{
		sel25519(p,0, q,0, b);
	}
	private static void sel25519(
			long[] p,final int poff,
			long[] q,final int qoff,
			int b)
	{
		long t, c = ~(b-1);
		for (int i = 0; i < 16; i++) {
			t = c & (p[i+poff] ^ q[i+qoff]);
			p[i+poff] ^= t;
			q[i+qoff] ^= t;
		}
	}

	private static void pack25519(byte [] o, long [] n,final int noff)
	{
		int i, j, b;
		long [] m = new long[16], t = new long[16];
		for (i = 0; i < 16; i++) t[i] = n[i+noff];
		car25519(t);
		car25519(t);
		car25519(t);
		for (j = 0; j < 2; j++) {
			m[0] = t[0] - 0xffed;
			for (i = 1; i < 15; i++) {
				m[i] = t[i] - 0xffff - ((m[i-1]>>16) & 1);
				m[i-1] &= 0xffff;
			}
			m[15] = t[15] - 0x7fff - ((m[14]>>16) & 1);
			b = (int) ((m[15]>>16) & 1);
			m[14] &= 0xffff;
			sel25519(t,0, m,0, 1-b);
		}
		for (i = 0; i < 16; i++) {
			o[2*i] = (byte) (t[i] & 0xff);
			o[2*i+1] = (byte) (t[i]>>8);
		}
	}
	
	private static int neq25519(long [] a, long [] b) {
		return neq25519(a,0, b,0);
	}
	private static int neq25519(long [] a,final int aoff, long [] b,final int boff)
	{
		byte [] c = new byte[32], d = new byte[32];
		pack25519(c, a,aoff);
		pack25519(d, b,boff);
		return crypto_verify_32(c, 0, d, 0);
	}
	
	private static byte par25519(long [] a)
	{
		return par25519(a,0);
	}
	private static byte par25519(long [] a,final int aoff)
	{
		byte [] d = new byte[32];
		pack25519(d, a,aoff);
		return (byte) (d[0] & 1);
	}

	private static void unpack25519(long [] o, byte [] n)
	{
		int i;
		for (i = 0; i < 16; i ++) o[i]=(n[2*i]&0xff)+((long)((n[2*i+1]<<8)&0xffff));
		o[15] &= 0x7fff;
	}
	
	private static void A(
			long [] o,
			long [] a,
			long [] b)
	{
		A(o,0, a,0, b,0);
	}
	private static void A(
			long [] o,final int ooff,
			long [] a,final int aoff,
			long [] b,final int boff)
	{
		int i;
		for (i = 0; i < 16; i ++) o[i+ooff] = a[i+aoff] + b[i+boff];
	}
	
	private static void Z(
			long [] o,
			long [] a,
			long [] b)
	{
		Z(o,0, a,0, b,0);
	}
	private static void Z(
			long [] o,final int ooff,
			long [] a,final int aoff,
			long [] b,final int boff)
	{
		int i;
		for (i = 0; i < 16; i ++) o[i+ooff] = a[i+aoff] - b[i+boff];
	}
	
	private static void M(
			long [] o,
			long [] a,
			long [] b)
	{
		M(o,0, a,0, b,0);
	}
	private static void M(
			long [] o,final int ooff,
			long [] a,final int aoff,
			long [] b,final int boff)
	{
		  long v, c,
		     t0 = 0,  t1 = 0,  t2 = 0,  t3 = 0,  t4 = 0,  t5 = 0,  t6 = 0,  t7 = 0,
		     t8 = 0,  t9 = 0, t10 = 0, t11 = 0, t12 = 0, t13 = 0, t14 = 0, t15 = 0,
		    t16 = 0, t17 = 0, t18 = 0, t19 = 0, t20 = 0, t21 = 0, t22 = 0, t23 = 0,
		    t24 = 0, t25 = 0, t26 = 0, t27 = 0, t28 = 0, t29 = 0, t30 = 0,
		    b0 = b[0 +boff],
		    b1 = b[1 +boff],
		    b2 = b[2 +boff],
		    b3 = b[3 +boff],
		    b4 = b[4 +boff],
		    b5 = b[5 +boff],
		    b6 = b[6 +boff],
		    b7 = b[7 +boff],
		    b8 = b[8 +boff],
		    b9 = b[9 +boff],
		    b10 = b[10 +boff],
		    b11 = b[11 +boff],
		    b12 = b[12 +boff],
		    b13 = b[13 +boff],
		    b14 = b[14 +boff],
		    b15 = b[15 +boff];

		  v = a[0 +aoff];
		  t0 += v * b0;
		  t1 += v * b1;
		  t2 += v * b2;
		  t3 += v * b3;
		  t4 += v * b4;
		  t5 += v * b5;
		  t6 += v * b6;
		  t7 += v * b7;
		  t8 += v * b8;
		  t9 += v * b9;
		  t10 += v * b10;
		  t11 += v * b11;
		  t12 += v * b12;
		  t13 += v * b13;
		  t14 += v * b14;
		  t15 += v * b15;
		  v = a[1 +aoff];
		  t1 += v * b0;
		  t2 += v * b1;
		  t3 += v * b2;
		  t4 += v * b3;
		  t5 += v * b4;
		  t6 += v * b5;
		  t7 += v * b6;
		  t8 += v * b7;
		  t9 += v * b8;
		  t10 += v * b9;
		  t11 += v * b10;
		  t12 += v * b11;
		  t13 += v * b12;
		  t14 += v * b13;
		  t15 += v * b14;
		  t16 += v * b15;
		  v = a[2 +aoff];
		  t2 += v * b0;
		  t3 += v * b1;
		  t4 += v * b2;
		  t5 += v * b3;
		  t6 += v * b4;
		  t7 += v * b5;
		  t8 += v * b6;
		  t9 += v * b7;
		  t10 += v * b8;
		  t11 += v * b9;
		  t12 += v * b10;
		  t13 += v * b11;
		  t14 += v * b12;
		  t15 += v * b13;
		  t16 += v * b14;
		  t17 += v * b15;
		  v = a[3 +aoff];
		  t3 += v * b0;
		  t4 += v * b1;
		  t5 += v * b2;
		  t6 += v * b3;
		  t7 += v * b4;
		  t8 += v * b5;
		  t9 += v * b6;
		  t10 += v * b7;
		  t11 += v * b8;
		  t12 += v * b9;
		  t13 += v * b10;
		  t14 += v * b11;
		  t15 += v * b12;
		  t16 += v * b13;
		  t17 += v * b14;
		  t18 += v * b15;
		  v = a[4 +aoff];
		  t4 += v * b0;
		  t5 += v * b1;
		  t6 += v * b2;
		  t7 += v * b3;
		  t8 += v * b4;
		  t9 += v * b5;
		  t10 += v * b6;
		  t11 += v * b7;
		  t12 += v * b8;
		  t13 += v * b9;
		  t14 += v * b10;
		  t15 += v * b11;
		  t16 += v * b12;
		  t17 += v * b13;
		  t18 += v * b14;
		  t19 += v * b15;
		  v = a[5 +aoff];
		  t5 += v * b0;
		  t6 += v * b1;
		  t7 += v * b2;
		  t8 += v * b3;
		  t9 += v * b4;
		  t10 += v * b5;
		  t11 += v * b6;
		  t12 += v * b7;
		  t13 += v * b8;
		  t14 += v * b9;
		  t15 += v * b10;
		  t16 += v * b11;
		  t17 += v * b12;
		  t18 += v * b13;
		  t19 += v * b14;
		  t20 += v * b15;
		  v = a[6 +aoff];
		  t6 += v * b0;
		  t7 += v * b1;
		  t8 += v * b2;
		  t9 += v * b3;
		  t10 += v * b4;
		  t11 += v * b5;
		  t12 += v * b6;
		  t13 += v * b7;
		  t14 += v * b8;
		  t15 += v * b9;
		  t16 += v * b10;
		  t17 += v * b11;
		  t18 += v * b12;
		  t19 += v * b13;
		  t20 += v * b14;
		  t21 += v * b15;
		  v = a[7 +aoff];
		  t7 += v * b0;
		  t8 += v * b1;
		  t9 += v * b2;
		  t10 += v * b3;
		  t11 += v * b4;
		  t12 += v * b5;
		  t13 += v * b6;
		  t14 += v * b7;
		  t15 += v * b8;
		  t16 += v * b9;
		  t17 += v * b10;
		  t18 += v * b11;
		  t19 += v * b12;
		  t20 += v * b13;
		  t21 += v * b14;
		  t22 += v * b15;
		  v = a[8 +aoff];
		  t8 += v * b0;
		  t9 += v * b1;
		  t10 += v * b2;
		  t11 += v * b3;
		  t12 += v * b4;
		  t13 += v * b5;
		  t14 += v * b6;
		  t15 += v * b7;
		  t16 += v * b8;
		  t17 += v * b9;
		  t18 += v * b10;
		  t19 += v * b11;
		  t20 += v * b12;
		  t21 += v * b13;
		  t22 += v * b14;
		  t23 += v * b15;
		  v = a[9 +aoff];
		  t9 += v * b0;
		  t10 += v * b1;
		  t11 += v * b2;
		  t12 += v * b3;
		  t13 += v * b4;
		  t14 += v * b5;
		  t15 += v * b6;
		  t16 += v * b7;
		  t17 += v * b8;
		  t18 += v * b9;
		  t19 += v * b10;
		  t20 += v * b11;
		  t21 += v * b12;
		  t22 += v * b13;
		  t23 += v * b14;
		  t24 += v * b15;
		  v = a[10 +aoff];
		  t10 += v * b0;
		  t11 += v * b1;
		  t12 += v * b2;
		  t13 += v * b3;
		  t14 += v * b4;
		  t15 += v * b5;
		  t16 += v * b6;
		  t17 += v * b7;
		  t18 += v * b8;
		  t19 += v * b9;
		  t20 += v * b10;
		  t21 += v * b11;
		  t22 += v * b12;
		  t23 += v * b13;
		  t24 += v * b14;
		  t25 += v * b15;
		  v = a[11 +aoff];
		  t11 += v * b0;
		  t12 += v * b1;
		  t13 += v * b2;
		  t14 += v * b3;
		  t15 += v * b4;
		  t16 += v * b5;
		  t17 += v * b6;
		  t18 += v * b7;
		  t19 += v * b8;
		  t20 += v * b9;
		  t21 += v * b10;
		  t22 += v * b11;
		  t23 += v * b12;
		  t24 += v * b13;
		  t25 += v * b14;
		  t26 += v * b15;
		  v = a[12 +aoff];
		  t12 += v * b0;
		  t13 += v * b1;
		  t14 += v * b2;
		  t15 += v * b3;
		  t16 += v * b4;
		  t17 += v * b5;
		  t18 += v * b6;
		  t19 += v * b7;
		  t20 += v * b8;
		  t21 += v * b9;
		  t22 += v * b10;
		  t23 += v * b11;
		  t24 += v * b12;
		  t25 += v * b13;
		  t26 += v * b14;
		  t27 += v * b15;
		  v = a[13 +aoff];
		  t13 += v * b0;
		  t14 += v * b1;
		  t15 += v * b2;
		  t16 += v * b3;
		  t17 += v * b4;
		  t18 += v * b5;
		  t19 += v * b6;
		  t20 += v * b7;
		  t21 += v * b8;
		  t22 += v * b9;
		  t23 += v * b10;
		  t24 += v * b11;
		  t25 += v * b12;
		  t26 += v * b13;
		  t27 += v * b14;
		  t28 += v * b15;
		  v = a[14 +aoff];
		  t14 += v * b0;
		  t15 += v * b1;
		  t16 += v * b2;
		  t17 += v * b3;
		  t18 += v * b4;
		  t19 += v * b5;
		  t20 += v * b6;
		  t21 += v * b7;
		  t22 += v * b8;
		  t23 += v * b9;
		  t24 += v * b10;
		  t25 += v * b11;
		  t26 += v * b12;
		  t27 += v * b13;
		  t28 += v * b14;
		  t29 += v * b15;
		  v = a[15 +aoff];
		  t15 += v * b0;
		  t16 += v * b1;
		  t17 += v * b2;
		  t18 += v * b3;
		  t19 += v * b4;
		  t20 += v * b5;
		  t21 += v * b6;
		  t22 += v * b7;
		  t23 += v * b8;
		  t24 += v * b9;
		  t25 += v * b10;
		  t26 += v * b11;
		  t27 += v * b12;
		  t28 += v * b13;
		  t29 += v * b14;
		  t30 += v * b15;

		  t0  += 38 * t16;
		  t1  += 38 * t17;
		  t2  += 38 * t18;
		  t3  += 38 * t19;
		  t4  += 38 * t20;
		  t5  += 38 * t21;
		  t6  += 38 * t22;
		  t7  += 38 * t23;
		  t8  += 38 * t24;
		  t9  += 38 * t25;
		  t10 += 38 * t26;
		  t11 += 38 * t27;
		  t12 += 38 * t28;
		  t13 += 38 * t29;
		  t14 += 38 * t30;
		  // t15 left as is

		  // first car
		  c = 1;
		  v =  t0 + c + 65535; c = v >> 16;  t0 = v - c * 65536;
		  v =  t1 + c + 65535; c = v >> 16;  t1 = v - c * 65536;
		  v =  t2 + c + 65535; c = v >> 16;  t2 = v - c * 65536;
		  v =  t3 + c + 65535; c = v >> 16;  t3 = v - c * 65536;
		  v =  t4 + c + 65535; c = v >> 16;  t4 = v - c * 65536;
		  v =  t5 + c + 65535; c = v >> 16;  t5 = v - c * 65536;
		  v =  t6 + c + 65535; c = v >> 16;  t6 = v - c * 65536;
		  v =  t7 + c + 65535; c = v >> 16;  t7 = v - c * 65536;
		  v =  t8 + c + 65535; c = v >> 16;  t8 = v - c * 65536;
		  v =  t9 + c + 65535; c = v >> 16;  t9 = v - c * 65536;
		  v = t10 + c + 65535; c = v >> 16; t10 = v - c * 65536;
		  v = t11 + c + 65535; c = v >> 16; t11 = v - c * 65536;
		  v = t12 + c + 65535; c = v >> 16; t12 = v - c * 65536;
		  v = t13 + c + 65535; c = v >> 16; t13 = v - c * 65536;
		  v = t14 + c + 65535; c = v >> 16; t14 = v - c * 65536;
		  v = t15 + c + 65535; c = v >> 16; t15 = v - c * 65536;
		  t0 += c-1 + 37 * (c-1);

		  // second car
		  c = 1;
		  v =  t0 + c + 65535; c = v >> 16;  t0 = v - c * 65536;
		  v =  t1 + c + 65535; c = v >> 16;  t1 = v - c * 65536;
		  v =  t2 + c + 65535; c = v >> 16;  t2 = v - c * 65536;
		  v =  t3 + c + 65535; c = v >> 16;  t3 = v - c * 65536;
		  v =  t4 + c + 65535; c = v >> 16;  t4 = v - c * 65536;
		  v =  t5 + c + 65535; c = v >> 16;  t5 = v - c * 65536;
		  v =  t6 + c + 65535; c = v >> 16;  t6 = v - c * 65536;
		  v =  t7 + c + 65535; c = v >> 16;  t7 = v - c * 65536;
		  v =  t8 + c + 65535; c = v >> 16;  t8 = v - c * 65536;
		  v =  t9 + c + 65535; c = v >> 16;  t9 = v - c * 65536;
		  v = t10 + c + 65535; c = v >> 16; t10 = v - c * 65536;
		  v = t11 + c + 65535; c = v >> 16; t11 = v - c * 65536;
		  v = t12 + c + 65535; c = v >> 16; t12 = v - c * 65536;
		  v = t13 + c + 65535; c = v >> 16; t13 = v - c * 65536;
		  v = t14 + c + 65535; c = v >> 16; t14 = v - c * 65536;
		  v = t15 + c + 65535; c = v >> 16; t15 = v - c * 65536;
		  t0 += c-1 + 37 * (c-1);

		  o[ 0 +ooff] = t0;
		  o[ 1 +ooff] = t1;
		  o[ 2 +ooff] = t2;
		  o[ 3 +ooff] = t3;
		  o[ 4 +ooff] = t4;
		  o[ 5 +ooff] = t5;
		  o[ 6 +ooff] = t6;
		  o[ 7 +ooff] = t7;
		  o[ 8 +ooff] = t8;
		  o[ 9 +ooff] = t9;
		  o[10 +ooff] = t10;
		  o[11 +ooff] = t11;
		  o[12 +ooff] = t12;
		  o[13 +ooff] = t13;
		  o[14 +ooff] = t14;
		  o[15 +ooff] = t15;
	}
	
	private static void S(
			long [] o,
			long [] a)
	{
		S(o,0, a,0);
	}
	private static void S(
			long [] o,final int ooff,
			long [] a,final int aoff)
	{
		M(o,ooff, a,aoff, a,aoff);
	}
	
	private static void inv25519(
			long [] o,final int ooff,
			long [] i,final int ioff)
	{
		  long [] c = new long[16];
		  int a;
		  for (a = 0; a < 16; a++) c[a] = i[a+ioff];
		  for (a = 253; a >= 0; a--) {
		    S(c,0, c,0);
		    if(a != 2 && a != 4) M(c,0, c,0, i,ioff);
		  }
		  for (a = 0; a < 16; a++) o[a+ooff] = c[a];
}

	private static void pow2523(long [] o,long [] i)
	{
		long [] c = new long[16];
		int a;
		
		for (a = 0; a < 16; a ++) c[a]=i[a];
		
		for(a=250;a>=0;a--) {
			S(c,0, c,0);
			if(a!=1) M(c,0, c,0, i,0);
		}
		
		for (a = 0; a < 16; a ++) o[a]=c[a];
	}

	public static int crypto_scalarmult(byte []q,byte []n,byte []p)
	{
		byte [] z = new byte[32];
		long [] x = new long[80];
		int r, i;
		long [] a = new long[16], b = new long[16], c = new long[16],
				d = new long[16], e = new long[16], f = new long[16];
		for (i = 0; i < 31; i++) z[i] = n[i];
		z[31]=(byte) (((n[31]&127)|64) & 0xff);
		z[0]&=248;
		unpack25519(x,p);
		for (i = 0; i < 16; i++) {
			b[i]=x[i];
			d[i]=a[i]=c[i]=0;
		}
		a[0]=d[0]=1;
		for (i=254;i>=0;--i) {
			r=(z[i>>>3]>>>(i&7))&1;
			sel25519(a,b,r);
			sel25519(c,d,r);
			A(e,a,c);
			Z(a,a,c);
			A(c,b,d);
			Z(b,b,d);
			S(d,e);
			S(f,a);
			M(a,c,a);
			M(c,b,e);
			A(e,a,c);
			Z(a,a,c);
			S(b,a);
			Z(c,d,f);
			M(a,c,_121665);
			A(a,a,d);
			M(c,c,a);
			M(a,d,f);
			M(d,b,x);
			S(b,e);
			sel25519(a,b,r);
			sel25519(c,d,r);
		}
		for (i = 0; i < 16; i++) {
			x[i+16]=a[i];
			x[i+32]=c[i];
			x[i+48]=b[i];
			x[i+64]=d[i];
		}
		inv25519(x,32, x,32);
		M(x,16, x,16, x,32);
		pack25519(q, x,16);
		
		return 0;
}

	public static int crypto_scalarmult_base(byte []q,byte []n)
	{ 
		return crypto_scalarmult(q,n,_9);
	}

	public static int crypto_box_keypair(byte [] y, byte [] x)
	{
		randombytes(x,32);
		return crypto_scalarmult_base(y,x);
	}

	public static int crypto_box_beforenm(byte []k,byte []y,byte []x)
	{
		byte[] s = new byte[32];
		crypto_scalarmult(s,x,y);

		/*String dbgt = "";
		for (int dbg = 0; dbg < s.length; dbg ++) dbgt += " "+s[dbg];
		Log.d(TAG, "crypto_box_beforenm -> "+dbgt);
		
	    dbgt = "";
		for (int dbg = 0; dbg < x.length; dbg ++) dbgt += " "+x[dbg];
		Log.d(TAG, "crypto_box_beforenm, x -> "+dbgt);

	    dbgt = "";
		for (int dbg = 0; dbg < y.length; dbg ++) dbgt += " "+y[dbg];
		Log.d(TAG, "crypto_box_beforenm, y -> "+dbgt);
		*/
		
		return crypto_core_hsalsa20(k, _0, s, sigma);
	}

	public static int crypto_box_afternm(byte []c,byte []m,int /*long*/ d,byte []n,byte []k)
	{
		return crypto_secretbox(c,m,d,n,k);
	}

	public static int crypto_box_open_afternm(byte []m,byte []c,int /*long*/ d,byte []n,byte []k)
	{
		return crypto_secretbox_open(m,c,d,n,k);
	}

	public static int crypto_box(byte []c,byte []m,int /*long*/ d,byte []n,byte []y,byte []x)
	{
		byte[] k = new byte[32];

		///L/og.d(TAG, "crypto_box start ...");

		crypto_box_beforenm(k,y,x);
		return crypto_box_afternm(c,m,d,n,k);
	}

	public static int crypto_box_open(byte []m,byte []c,int /*long*/ d,byte []n,byte []y,byte []x)
	{
		byte[] k = new byte[32];
		crypto_box_beforenm(k,y,x);
		return crypto_box_open_afternm(m,c,d,n,k);
	}

	private static final long K[] = {
		0x428a2f98d728ae22L, 0x7137449123ef65cdL, 0xb5c0fbcfec4d3b2fL, 0xe9b5dba58189dbbcL,
		0x3956c25bf348b538L, 0x59f111f1b605d019L, 0x923f82a4af194f9bL, 0xab1c5ed5da6d8118L,
		0xd807aa98a3030242L, 0x12835b0145706fbeL, 0x243185be4ee4b28cL, 0x550c7dc3d5ffb4e2L,
		0x72be5d74f27b896fL, 0x80deb1fe3b1696b1L, 0x9bdc06a725c71235L, 0xc19bf174cf692694L,
		0xe49b69c19ef14ad2L, 0xefbe4786384f25e3L, 0x0fc19dc68b8cd5b5L, 0x240ca1cc77ac9c65L,
		0x2de92c6f592b0275L, 0x4a7484aa6ea6e483L, 0x5cb0a9dcbd41fbd4L, 0x76f988da831153b5L,
		0x983e5152ee66dfabL, 0xa831c66d2db43210L, 0xb00327c898fb213fL, 0xbf597fc7beef0ee4L,
		0xc6e00bf33da88fc2L, 0xd5a79147930aa725L, 0x06ca6351e003826fL, 0x142929670a0e6e70L,
		0x27b70a8546d22ffcL, 0x2e1b21385c26c926L, 0x4d2c6dfc5ac42aedL, 0x53380d139d95b3dfL,
		0x650a73548baf63deL, 0x766a0abb3c77b2a8L, 0x81c2c92e47edaee6L, 0x92722c851482353bL,
		0xa2bfe8a14cf10364L, 0xa81a664bbc423001L, 0xc24b8b70d0f89791L, 0xc76c51a30654be30L,
		0xd192e819d6ef5218L, 0xd69906245565a910L, 0xf40e35855771202aL, 0x106aa07032bbd1b8L,
		0x19a4c116b8d2d0c8L, 0x1e376c085141ab53L, 0x2748774cdf8eeb99L, 0x34b0bcb5e19b48a8L,
		0x391c0cb3c5c95a63L, 0x4ed8aa4ae3418acbL, 0x5b9cca4f7763e373L, 0x682e6ff3d6b2b8a3L,
		0x748f82ee5defb2fcL, 0x78a5636f43172f60L, 0x84c87814a1f0ab72L, 0x8cc702081a6439ecL,
		0x90befffa23631e28L, 0xa4506cebde82bde9L, 0xbef9a3f7b2c67915L, 0xc67178f2e372532bL,
		0xca273eceea26619cL, 0xd186b8c721c0c207L, 0xeada7dd6cde0eb1eL, 0xf57d4f7fee6ed178L,
		0x06f067aa72176fbaL, 0x0a637dc5a2c898a6L, 0x113f9804bef90daeL, 0x1b710b35131c471bL,
		0x28db77f523047d84L, 0x32caab7b40c72493L, 0x3c9ebe0a15c9bebcL, 0x431d67c49c100d4cL,
		0x4cc5d4becb3e42b6L, 0x597f299cfc657e2aL, 0x5fcb6fab3ad6faecL, 0x6c44198c4a475817L
	};

	private static int crypto_hashblocks_hl(int [] hh,int [] hl, byte [] m,final int moff, int n) {
		
		///String dbgt = "";
		///for (int dbg = 0; dbg < n; dbg ++) dbgt += " "+m[dbg+moff];
		///Log.d(TAG, "crypto_hashblocks_hl m/"+n + "-> "+dbgt);
		
		int []  wh = new int[16], wl = new int[16];
		int     bh0, bh1, bh2, bh3, bh4, bh5, bh6, bh7,
		        bl0, bl1, bl2, bl3, bl4, bl5, bl6, bl7,
	  	        th, tl, h, l, i, j, a, b, c, d;

		int     ah0 = hh[0],
			    ah1 = hh[1],
				ah2 = hh[2],
				ah3 = hh[3],
				ah4 = hh[4],
				ah5 = hh[5],
				ah6 = hh[6],
				ah7 = hh[7],

				al0 = hl[0],
				al1 = hl[1],
				al2 = hl[2],
				al3 = hl[3],
				al4 = hl[4],
				al5 = hl[5],
				al6 = hl[6],
				al7 = hl[7];

		int pos = 0;
		while (n >= 128) {
			for (i = 0; i < 16; i++) {
				j = 8 * i + pos;
				wh[i] = ((m[j+0+moff]&0xff) << 24) | ((m[j+1+moff]&0xff) << 16) | ((m[j+2+moff]&0xff) << 8) | ((m[j+3+moff]&0xff) << 0);
				wl[i] = ((m[j+4+moff]&0xff) << 24) | ((m[j+5+moff]&0xff) << 16) | ((m[j+6+moff]&0xff) << 8) | ((m[j+7+moff]&0xff) << 0);
			}
			for (i = 0; i < 80; i++) {
				bh0 = ah0;
				bh1 = ah1;
				bh2 = ah2;
				bh3 = ah3;
				bh4 = ah4;
				bh5 = ah5;
				bh6 = ah6;
				bh7 = ah7;

				bl0 = al0;
				bl1 = al1;
				bl2 = al2;
				bl3 = al3;
				bl4 = al4;
				bl5 = al5;
				bl6 = al6;
				bl7 = al7;

				// add
				h = ah7;
				l = al7;

				a = l & 0xffff; b = l >>> 16;
				c = h & 0xffff; d = h >>> 16;

				// Sigma1
				h = ((ah4 >>> 14) | (al4 << (32-14))) ^ ((ah4 >>> 18) | (al4 << (32-18))) ^ ((al4 >>> (41-32)) | (ah4 << (32-(41-32))));
				l = ((al4 >>> 14) | (ah4 << (32-14))) ^ ((al4 >>> 18) | (ah4 << (32-18))) ^ ((ah4 >>> (41-32)) | (al4 << (32-(41-32))));

				a += l & 0xffff; b += l >>> 16;
				c += h & 0xffff; d += h >>> 16;

				// Ch
				h = (ah4 & ah5) ^ (~ah4 & ah6);
				l = (al4 & al5) ^ (~al4 & al6);

				a += l & 0xffff; b += l >>> 16;
				c += h & 0xffff; d += h >>> 16;

				// K
				///h = K[i*2];
				///l = K[i*2+1];
				h = (int) ((K[i]>>>32) & 0xffffffff);
				l = (int) ((K[i]>>> 0) & 0xffffffff);
				
				///Log.d(TAG, "i"+i + ",h:0x"+Integer.toHexString(h) + ",l:0x"+Integer.toHexString(l));

				a += l & 0xffff; b += l >>> 16;
				c += h & 0xffff; d += h >>> 16;

				// w
				h = wh[i%16];
				l = wl[i%16];

				a += l & 0xffff; b += l >>> 16;
				c += h & 0xffff; d += h >>> 16;

				b += a >>> 16;
				c += b >>> 16;
				d += c >>> 16;

				th = c & 0xffff | d << 16;
				tl = a & 0xffff | b << 16;

				// add
				h = th;
				l = tl;

				a = l & 0xffff; b = l >>> 16;
				c = h & 0xffff; d = h >>> 16;

				// Sigma0
				h = ((ah0 >>> 28) | (al0 << (32-28))) ^ ((al0 >>> (34-32)) | (ah0 << (32-(34-32)))) ^ ((al0 >>> (39-32)) | (ah0 << (32-(39-32))));
				l = ((al0 >>> 28) | (ah0 << (32-28))) ^ ((ah0 >>> (34-32)) | (al0 << (32-(34-32)))) ^ ((ah0 >>> (39-32)) | (al0 << (32-(39-32))));

				a += l & 0xffff; b += l >>> 16;
				c += h & 0xffff; d += h >>> 16;

				// Maj
				h = (ah0 & ah1) ^ (ah0 & ah2) ^ (ah1 & ah2);
				l = (al0 & al1) ^ (al0 & al2) ^ (al1 & al2);

				a += l & 0xffff; b += l >>> 16;
				c += h & 0xffff; d += h >>> 16;

				b += a >>> 16;
				c += b >>> 16;
				d += c >>> 16;

				bh7 = (c & 0xffff) | (d << 16);
				bl7 = (a & 0xffff) | (b << 16);

				// add
				h = bh3;
				l = bl3;

				a = l & 0xffff; b = l >>> 16;
				c = h & 0xffff; d = h >>> 16;

				h = th;
				l = tl;

				a += l & 0xffff; b += l >>> 16;
				c += h & 0xffff; d += h >>> 16;

				b += a >>> 16;
				c += b >>> 16;
				d += c >>> 16;

				bh3 = (c & 0xffff) | (d << 16);
				bl3 = (a & 0xffff) | (b << 16);

				ah1 = bh0;
				ah2 = bh1;
				ah3 = bh2;
				ah4 = bh3;
				ah5 = bh4;
				ah6 = bh5;
				ah7 = bh6;
				ah0 = bh7;

				al1 = bl0;
				al2 = bl1;
				al3 = bl2;
				al4 = bl3;
				al5 = bl4;
				al6 = bl5;
				al7 = bl6;
				al0 = bl7;

				if (i%16 == 15) {
					for (j = 0; j < 16; j++) {
						// add
						h = wh[j];
						l = wl[j];

				a = l & 0xffff; b = l >>> 16;
				c = h & 0xffff; d = h >>> 16;

				h = wh[(j+9)%16];
				l = wl[(j+9)%16];

				a += l & 0xffff; b += l >>> 16;
				c += h & 0xffff; d += h >>> 16;

				// sigma0
				th = wh[(j+1)%16];
				tl = wl[(j+1)%16];
				h = ((th >>> 1) | (tl << (32-1))) ^ ((th >>> 8) | (tl << (32-8))) ^ (th >>> 7);
				l = ((tl >>> 1) | (th << (32-1))) ^ ((tl >>> 8) | (th << (32-8))) ^ ((tl >>> 7) | (th << (32-7)));

				a += l & 0xffff; b += l >>> 16;
				c += h & 0xffff; d += h >>> 16;

				// sigma1
				th = wh[(j+14)%16];
				tl = wl[(j+14)%16];
				h = ((th >>> 19) | (tl << (32-19))) ^ ((tl >>> (61-32)) | (th << (32-(61-32)))) ^ (th >>> 6);
				l = ((tl >>> 19) | (th << (32-19))) ^ ((th >>> (61-32)) | (tl << (32-(61-32)))) ^ ((tl >>> 6) | (th << (32-6)));

				a += l & 0xffff; b += l >>> 16;
				c += h & 0xffff; d += h >>> 16;

				b += a >>> 16;
				c += b >>> 16;
				d += c >>> 16;

				wh[j] = (c & 0xffff) | (d << 16);
				wl[j] = (a & 0xffff) | (b << 16);
					}
				}
			}

			// add
			h = ah0;
			l = al0;

			a = l & 0xffff; b = l >>> 16;
				c = h & 0xffff; d = h >>> 16;

				h = hh[0];
				l = hl[0];

				a += l & 0xffff; b += l >>> 16;
				c += h & 0xffff; d += h >>> 16;

				b += a >>> 16;
				c += b >>> 16;
				d += c >>> 16;

				hh[0] = ah0 = (c & 0xffff) | (d << 16);
				hl[0] = al0 = (a & 0xffff) | (b << 16);

				h = ah1;
				l = al1;

				a = l & 0xffff; b = l >>> 16;
				c = h & 0xffff; d = h >>> 16;

				h = hh[1];
				l = hl[1];

				a += l & 0xffff; b += l >>> 16;
				c += h & 0xffff; d += h >>> 16;

				b += a >>> 16;
				c += b >>> 16;
				d += c >>> 16;

				hh[1] = ah1 = (c & 0xffff) | (d << 16);
				hl[1] = al1 = (a & 0xffff) | (b << 16);

				h = ah2;
				l = al2;

				a = l & 0xffff; b = l >>> 16;
				c = h & 0xffff; d = h >>> 16;

				h = hh[2];
				l = hl[2];

				a += l & 0xffff; b += l >>> 16;
				c += h & 0xffff; d += h >>> 16;

				b += a >>> 16;
				c += b >>> 16;
				d += c >>> 16;

				hh[2] = ah2 = (c & 0xffff) | (d << 16);
				hl[2] = al2 = (a & 0xffff) | (b << 16);

				h = ah3;
				l = al3;

				a = l & 0xffff; b = l >>> 16;
				c = h & 0xffff; d = h >>> 16;

				h = hh[3];
				l = hl[3];

				a += l & 0xffff; b += l >>> 16;
				c += h & 0xffff; d += h >>> 16;

				b += a >>> 16;
				c += b >>> 16;
				d += c >>> 16;

				hh[3] = ah3 = (c & 0xffff) | (d << 16);
				hl[3] = al3 = (a & 0xffff) | (b << 16);

				h = ah4;
				l = al4;

				a = l & 0xffff; b = l >>> 16;
				c = h & 0xffff; d = h >>> 16;

				h = hh[4];
				l = hl[4];

				a += l & 0xffff; b += l >>> 16;
				c += h & 0xffff; d += h >>> 16;

				b += a >>> 16;
				c += b >>> 16;
				d += c >>> 16;

				hh[4] = ah4 = (c & 0xffff) | (d << 16);
				hl[4] = al4 = (a & 0xffff) | (b << 16);

				h = ah5;
				l = al5;

				a = l & 0xffff; b = l >>> 16;
				c = h & 0xffff; d = h >>> 16;

				h = hh[5];
				l = hl[5];

				a += l & 0xffff; b += l >>> 16;
				c += h & 0xffff; d += h >>> 16;

				b += a >>> 16;
				c += b >>> 16;
				d += c >>> 16;

				hh[5] = ah5 = (c & 0xffff) | (d << 16);
				hl[5] = al5 = (a & 0xffff) | (b << 16);

				h = ah6;
				l = al6;

				a = l & 0xffff; b = l >>> 16;
				c = h & 0xffff; d = h >>> 16;

				h = hh[6];
				l = hl[6];

				a += l & 0xffff; b += l >>> 16;
				c += h & 0xffff; d += h >>> 16;

				b += a >>> 16;
				c += b >>> 16;
				d += c >>> 16;

				hh[6] = ah6 = (c & 0xffff) | (d << 16);
				hl[6] = al6 = (a & 0xffff) | (b << 16);

				h = ah7;
				l = al7;

				a = l & 0xffff; b = l >>> 16;
				c = h & 0xffff; d = h >>> 16;

				h = hh[7];
				l = hl[7];

				a += l & 0xffff; b += l >>> 16;
				c += h & 0xffff; d += h >>> 16;

				b += a >>> 16;
				c += b >>> 16;
				d += c >>> 16;

				hh[7] = ah7 = (c & 0xffff) | (d << 16);
				hl[7] = al7 = (a & 0xffff) | (b << 16);

				pos += 128;
				n -= 128;
				
				/*dbgt = "";
				for (int dbg = 0; dbg < hh.length; dbg ++) dbgt += " "+hh[dbg];
				Log.d(TAG, "\ncrypto_hashblocks_hl hh -> "+dbgt);
				
				dbgt = "";
				for (int dbg = 0; dbg < hl.length; dbg ++) dbgt += " "+hl[dbg];
				Log.d(TAG, "\ncrypto_hashblocks_hl hl -> "+dbgt);*/
		}

		return n;
	}

	// TBD 64bits of n
	///int crypto_hash(byte [] out, byte [] m, long n)
	public static int crypto_hash(byte [] out, byte [] m,final int moff, int n)
	{
		int []  hh = new int[8],
				hl = new int[8];
		byte [] x = new byte[256];
		int     i, b = n;
		long    u;

		hh[0] = 0x6a09e667;
		hh[1] = 0xbb67ae85;
		hh[2] = 0x3c6ef372;
		hh[3] = 0xa54ff53a;
		hh[4] = 0x510e527f;
		hh[5] = 0x9b05688c;
		hh[6] = 0x1f83d9ab;
		hh[7] = 0x5be0cd19;

		hl[0] = 0xf3bcc908;
		hl[1] = 0x84caa73b;
		hl[2] = 0xfe94f82b;
		hl[3] = 0x5f1d36f1;
		hl[4] = 0xade682d1;
		hl[5] = 0x2b3e6c1f;
		hl[6] = 0xfb41bd6b;
		hl[7] = 0x137e2179;

		if (n >= 128) {
			crypto_hashblocks_hl(hh, hl, m,moff, n);
			n %= 128;
		}

		for (i = 0; i < n; i++) x[i] = m[b-n+i +moff];
		x[n] = (byte) 128;

		n = 256-128*(n<112?1:0);
		x[n-9] = 0;

		ts64(x, n-8,  b<<3/*(b / 0x20000000) | 0, b << 3*/);

		crypto_hashblocks_hl(hh, hl, x,0, n);

		for (i = 0; i < 8; i++) {
			u = hh[i]; u <<= 32; u |= hl[i]&0xffffffffL;
			ts64(out, 8*i, u);
		}

		return 0;
	}
	public static int crypto_hash(byte [] out, byte [] m) {
		return crypto_hash(out, m,0, m!=null? m.length : 0);
	}

	// gf: long[16]
	///private static void add(gf p[4],gf q[4])
	private static void add(long [] p[], long [] q[])
	{
		long [] a = new long[16];
		long [] b = new long[16];
		long [] c = new long[16];
		long [] d = new long[16];
		long [] t = new long[16];
		long [] e = new long[16];
		long [] f = new long[16];
		long [] g = new long[16];
		long [] h = new long[16];


		long [] p0 = p[0];
		long [] p1 = p[1];
		long [] p2 = p[2];
		long [] p3 = p[3];

		long [] q0 = q[0];
		long [] q1 = q[1];
		long [] q2 = q[2];
		long [] q3 = q[3];

		Z(a,0, p1,0, p0,0);
		Z(t,0, q1,0, q0,0);
		M(a,0, a,0,   t,0);
		A(b,0, p0,0, p1,0);
		A(t,0, q0,0, q1,0);
		M(b,0, b,0,   t,0);
		M(c,0, p3,0, q3,0);
		M(c,0, c,0,  D2,0);
		M(d,0, p2,0, q2,0);
		
		A(d,0, d,0, d,0);
		Z(e,0, b,0, a,0);
		Z(f,0, d,0, c,0);
		A(g,0, d,0, c,0);
		A(h,0, b,0, a,0);

		M(p0,0, e,0, f,0);
		M(p1,0, h,0, g,0);
		M(p2,0, g,0, f,0);
		M(p3,0, e,0, h,0);
	}

	private static void cswap(long [] p[], long [] q[], byte b)
	{
		int i;

		for (i = 0; i < 4; i ++)
			sel25519(p[i],0, q[i],0, b);  
	}

	private static void pack(byte [] r, long [] p[])
	{
		long [] tx = new long[16];
		long [] ty = new long[16];
		long [] zi = new long[16];

		inv25519(zi,0, p[2],0); 

		M(tx,0, p[0],0, zi,0);
		M(ty,0, p[1],0, zi,0);

		pack25519(r, ty,0);

		r[31] ^= par25519(tx,0) << 7;
	}

	private static void scalarmult(long [] p[], long [] q[], byte[] s,final int soff)
	{
		int i;

		set25519(p[0],gf0);
		set25519(p[1],gf1);
		set25519(p[2],gf1);
		set25519(p[3],gf0);

		for (i = 255;i >= 0;--i) {
			byte b = (byte) ((s[i/8+soff] >>> (i&7))&1);

			cswap(p,q,b);
			add(q,p);
			add(p,p);
			cswap(p,q,b);
		}

		///String dbgt = "";
		///for (int dbg = 0; dbg < p.length; dbg ++) for (int dd = 0; dd < p[dbg].length; dd ++) dbgt += " "+p[dbg][dd];
		///L/og.d(TAG, "scalarmult -> "+dbgt);
	}

	private static void scalarbase(long [] p[], byte[] s,final int soff)
	{
		long [] [] q = new long [4] [];
		
		q[0] = new long [16];
		q[1] = new long [16];
		q[2] = new long [16];
		q[3] = new long [16];

		set25519(q[0],X);
		set25519(q[1],Y);
		set25519(q[2],gf1);
		M(q[3],0, X,0, Y,0);
		scalarmult(p,q, s,soff);
	}

	public static int  crypto_sign_keypair(byte [] pk, byte [] sk, boolean seeded) {
		byte [] d = new byte[64];
		long [] [] p = new long [4] [];

		p[0] = new long [16];
		p[1] = new long [16];
		p[2] = new long [16];
		p[3] = new long [16];

		int i;

		if (!seeded) randombytes(sk, 32);
		crypto_hash(d, sk,0, 32);
		d[0]  &= 248;
		d[31] &= 127;
		d[31] |= 64;

		scalarbase(p, d,0);
		pack(pk, p);

		for (i = 0; i < 32; i++) sk[i+32] = pk[i];
		return 0;
	}

	private static final long L[] = {
		0xed, 0xd3, 0xf5, 0x5c, 0x1a, 0x63, 0x12, 0x58,
		0xd6, 0x9c, 0xf7, 0xa2, 0xde, 0xf9, 0xde, 0x14,
		0,    0,    0,    0,    0,    0,    0,    0, 
		0,    0,    0,    0,    0,    0,    0,    0x10
	};

	private static void modL(byte[] r,final int roff, long x[])
	{
		long carry;
		int i, j;

		for (i = 63;i >= 32;--i) {
			carry = 0;
			for (j = i - 32;j < i - 12;++j) {
				x[j] += carry - 16 * x[i] * L[j - (i - 32)];
				carry = (x[j] + 128) >> 8;
			x[j] -= carry << 8;
			}
			x[j] += carry;
			x[i] = 0;
		}
		carry = 0;

		for (j = 0; j < 32; j ++) {
			x[j] += carry - (x[31] >> 4) * L[j];
			carry = x[j] >> 8;
			x[j] &= 255;
		}

		for (j = 0; j < 32; j ++) x[j] -= carry * L[j];

		for (i = 0; i < 32; i ++) {
			x[i+1] += x[i] >> 8;
		    r[i+roff] = (byte) (x[i] & 255);
		}
	}

	private static void reduce(byte [] r)
	{
		long[] x = new long [64];
		int i;
		
		for (i = 0; i < 64; i ++) x[i] = (long) (r[i]&0xff);
		
		for (i = 0; i < 64; i ++) r[i] = 0;
		
		modL(r,0, x);
	}

	// TBD... 64bits of n
	///int crypto_sign(byte [] sm, long * smlen, byte [] m, long n, byte [] sk)
	public static int crypto_sign(byte [] sm, long dummy /* *smlen not used*/, byte [] m,final int moff, int/*long*/ n, byte [] sk)
	{
		byte[] d = new byte[64], h = new byte[64], r = new byte[64];

		int i, j;
		long [] x = new long[64];

		long [] [] p = new long [4] [];
		p[0] = new long [16];
		p[1] = new long [16];
		p[2] = new long [16];
		p[3] = new long [16];

		crypto_hash(d, sk,0, 32);
		d[0] &= 248;
		d[31] &= 127;
		d[31] |= 64;

		///*smlen = n+64;

		for (i = 0; i < n; i ++) sm[64 + i] = m[i+moff];
		
		for (i = 0; i < 32; i ++) sm[32 + i] = d[32 + i];

		crypto_hash(r, sm,32, n+32);
		reduce(r);
		scalarbase(p, r,0);
		pack(sm,p);

		for (i = 0; i < 32; i ++) sm[i+32] = sk[i+32];
		crypto_hash(h, sm,0, n + 64);
		reduce(h);

		for (i = 0; i < 64; i ++) x[i] = 0;
		
		for (i = 0; i < 32; i ++) x[i] = (long) (r[i]&0xff);
		
		for (i = 0; i < 32; i ++) for (j = 0; j < 32; j ++) x[i+j] += (h[i]&0xff) * (long) (d[j]&0xff);
		
		modL(sm,32, x);

		return 0;
	}

	private static int unpackneg(long [] r[], byte p[])
	{
		long []    t = new long [16];
		long []  chk = new long [16];
		long []  num = new long [16];
		long []  den = new long [16];
		long [] den2 = new long [16];
		long [] den4 = new long [16];
		long [] den6 = new long [16];

		set25519(r[2], gf1);
		unpack25519(r[1], p);
		S(num, r[1]);
		M(den, num, D);
		Z(num, num, r[2]);
		A(den, r[2], den);

		S(den2, den);
		S(den4, den2);
		M(den6, den4, den2);
		M(t, den6, num);
		M(t, t, den);

		pow2523(t, t);
		M(t, t, num);
		M(t, t, den);
		M(t, t, den);
		M(r[0], t, den);

		S(chk, r[0]);
		M(chk, chk, den);
		if (neq25519(chk, num)!=0) M(r[0], r[0], I);

		S(chk, r[0]);
		M(chk, chk, den);
		if (neq25519(chk, num)!=0) return -1;

		if (par25519(r[0]) == ((p[31]&0xFF)>>>7)) Z(r[0], gf0, r[0]);

		M(r[3], r[0], r[1]);

		return 0;
	}

	/// TBD 64bits of mlen
	///int crypto_sign_open(byte []m,long *mlen,byte []sm,long n,byte []pk)
	public static int crypto_sign_open(byte [] m, long dummy /* *mlen not used*/, byte [] sm,final int smoff, int/*long*/ n, byte []pk)
	{
		int i;
		byte[] t = new byte[32], h = new byte[64];
		
		long [] [] p = new long [4] [];
		p[0] = new long [16];
		p[1] = new long [16];
		p[2] = new long [16];
		p[3] = new long [16];

		long [] [] q = new long [4] [];
		q[0] = new long [16];
		q[1] = new long [16];
		q[2] = new long [16];
		q[3] = new long [16];

		///*mlen = -1;

		if (n < 64) return -1;

		if (unpackneg(q,pk)!=0) return -1;

		for (i = 0; i < n; i ++) m[i] = sm[i+smoff];

		for (i = 0; i < 32; i ++) m[i+32] = pk[i];

		crypto_hash(h, m,0, n);

		reduce(h);
		scalarmult(p,q, h,0);

		scalarbase(q, sm,32+smoff);
		add(p,q);
		pack(t,p);

		n -= 64;
		if (crypto_verify_32(sm,smoff, t,0)!=0) {
			// optimizing it
			///for (i = 0; i < n; i ++) m[i] = 0;
			return -1;
		}

		// TBD optimizing ...
		///for (i = 0; i < n; i ++) m[i] = sm[i + 64 + smoff];
		///*mlen = n;
		
		return 0;
	}

	/*
	 * @description
	 *   Java SecureRandom generator
	 * */
	private static final SecureRandom jrandom = new SecureRandom();

	public static byte[] randombytes(byte [] x) {
	  jrandom.nextBytes(x);
	  return x;
	}
	
	public static byte[] randombytes(int len) {
	  return randombytes(new byte[len]);
	}
	
	public static byte[] randombytes(byte [] x, int len) {
    byte [] b = randombytes(len);
    System.arraycopy(b, 0, x, 0, len);
    return x;
	}
	
/*
  public static byte[] randombytes(byte [] x, int len) {
		int ret = len % 8;
		long rnd;

		for (int i = 0; i < len-ret; i += 8) {
			rnd = jrandom.nextLong();

			x[i+0] = (byte) (rnd >>>  0);
			x[i+1] = (byte) (rnd >>>  8);
			x[i+2] = (byte) (rnd >>> 16);
			x[i+3] = (byte) (rnd >>> 24);
			x[i+4] = (byte) (rnd >>> 32);
			x[i+5] = (byte) (rnd >>> 40);
			x[i+6] = (byte) (rnd >>> 48);
			x[i+7] = (byte) (rnd >>> 56);
		}

		if (ret > 0) {         
			rnd = jrandom.nextLong();
			for (int i = len-ret; i < len; i ++)
				x[i] = (byte) (rnd >>> 8*i);
		}
		return x;
	}
*/

	public static byte[] makeBoxNonce() {
	  return randombytes(Box.nonceLength);
	}
	
	public static byte[] makeSecretBoxNonce() {
	  return randombytes(SecretBox.nonceLength);
	}
	
  public static String hexEncodeToString( byte [] raw ) {
    String HEXES = "0123456789ABCDEF";
    final StringBuilder hex = new StringBuilder( 2 * raw.length );
    for ( final byte b : raw ) {
      hex.append(HEXES.charAt((b & 0xF0) >> 4))
         .append(HEXES.charAt((b & 0x0F)));
    }
    return hex.toString();
  }

  public static byte[] hexDecode(String s) {
    byte[] b = new byte[s.length() / 2];
    for (int i = 0; i < s.length(); i += 2) {
      b[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                           + Character.digit(s.charAt(i+1), 16));
    }
    return b;
  }

	// public static boolean java.util.Arrays.equals(array1, array2);
		
}
