package com.limelight.solanaWallet

import com.solana.core.PublicKey
import com.solana.core.DerivationPath
import javax.crypto.spec.PBEKeySpec
import javax.crypto.SecretKeyFactory
import com.limelight.solanaWallet.TweetNaclFast

interface Account {
    val publicKey: PublicKey
    fun sign(serializedMessage: ByteArray): ByteArray
}

class SolanaAccount(internal val keyPair: TweetNaclFast.Signature.KeyPair) : Account {

    override val publicKey: PublicKey
        get() = PublicKey(keyPair.publicKey)

    override fun sign(serializedMessage: ByteArray): ByteArray {
        val signature = TweetNaclFast.Signature(keyPair.publicKey, keyPair.secretKey)
        return signature.detached(serializedMessage)
    }

    companion object {

        fun fromMnemonic(words: List<String>, password: String = "", derivationPath: DerivationPath): SolanaAccount {
            fun generateSeed(mnemonic: String, password: String): ByteArray {
                val salt = "mnemonic$password".toByteArray()
                val spec = PBEKeySpec(mnemonic.toCharArray(), salt, 2048, 512)
                val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
                val key = skf.generateSecret(spec)
                return key.encoded
            }

            val mnemonic = words.joinToString(" ")
            val seed = generateSeed(mnemonic, password)
            val keyPair = TweetNaclFast.Signature.keyPair_fromSeed(seed)  // Directly derive the keyPair from the seed
            return SolanaAccount(keyPair)
        }

        fun fromSeed(seed: ByteArray): SolanaAccount {
            val keyPair = TweetNaclFast.Signature.keyPair_fromSeed(seed)
            return SolanaAccount(keyPair)
        }

        fun create(): SolanaAccount {
            val keyPair = TweetNaclFast.Signature.keyPair()
            return SolanaAccount(keyPair)
        }

    }

    fun getKeyPair(): TweetNaclFast.Signature.KeyPair {
        return keyPair
    }
}
