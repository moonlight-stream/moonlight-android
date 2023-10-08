package com.limelight.shagaMap

import android.util.Base64
import android.util.Log
import com.limelight.solanaWallet.EncryptionHelper.decrypt
import com.limelight.solanaWallet.SolanaApi
import com.limelight.solanaWallet.SolanaPreferenceManager
import com.solana.core.Account
import com.solana.core.AccountMeta
import com.solana.core.DerivationPath
import com.solana.core.HotAccount
import com.solana.core.PublicKey
import com.solana.core.Transaction
import com.solana.core.TransactionBuilder
import com.solana.core.TransactionInstruction
import com.solana.vendor.TweetNaclFast
import java.nio.ByteBuffer
import com.solana.api.getRecentBlockhash
class ShagaTransactions {
    fun getHotAccountForFeePayer(): HotAccount? {
        // Step 1: Retrieve Encrypted Mnemonic and Decryption Key
        val encryptedMnemonic = SolanaPreferenceManager.encryptedMnemonic
        val encryptionKey = SolanaPreferenceManager.encryptionKeyFromPrefs
        // Step 2: Decrypt the Mnemonic
        val decryptedMnemonic: String
        try {
            decryptedMnemonic = decrypt(encryptedMnemonic ?: "")
        } catch (e: Exception) {
            Log.e("ShagaTransactions", "Failed to decrypt mnemonic. Cannot proceed with the transaction.")
            return null
        }
        // Step 3: Convert Mnemonic to HotAccount
        val words = decryptedMnemonic.split(" ")
        val passphrase = ""  // Replace with actual passphrase if you have one
        val derivationPath = DerivationPath.BIP44_M_44H_501H_0H  // Using the same path as in createNewWalletAccount
        val hotAccount = SolanaPreferenceManager.getHotAccountFromMnemonic(words, passphrase, derivationPath)

        return hotAccount
    }


    fun startRental(
        authority: PublicKey,
        client: PublicKey,
        programId: PublicKey,
        args: SolanaApi.StartRentalInstructionArgs
    ): TransactionInstruction {

        // Use the helper functions to get the public keys
        val (lender, _) = ProgramAddressHelper.findLender(authority, programId)
        val (affair, _) = ProgramAddressHelper.findAffair(authority, programId)
        val (affairsList, _) = ProgramAddressHelper.findAffairList(programId)
        val (escrow, _) = ProgramAddressHelper.findRentEscrow(lender, client, programId)
        val (rental, _) = ProgramAddressHelper.findRentAccount(lender, client, programId)
        val (vault, _) = ProgramAddressHelper.findVault(programId)
        val (threadAuthority, _) = ProgramAddressHelper.findThreadAuthority(programId)
        val (rentalClockworkThread, _) = ProgramAddressHelper.findClockworkThreadAccount(threadAuthority, affair, programId) // Assuming threadId = affair
        val (clockworkProgram, _) = ProgramAddressHelper.findClockworkThreadAccount(threadAuthority, affair, programId) // Replace this if you have a specific ProgramId for clockwork
        // Initialize the keys list for TransactionInstruction
        val keys = mutableListOf<AccountMeta>()
        keys.add(AccountMeta(client, true, true))
        keys.add(AccountMeta(lender, false, false))
        keys.add(AccountMeta(affair, false, false))
        keys.add(AccountMeta(affairsList, false, false))
        keys.add(AccountMeta(escrow, false, false))
        keys.add(AccountMeta(rental, false, false))
        keys.add(AccountMeta(vault, false, false))
        keys.add(AccountMeta(rentalClockworkThread, false, false))
        keys.add(AccountMeta(threadAuthority, false, false))
        keys.add(AccountMeta(clockworkProgram, false, false))
        // Encode the arguments
        val encodedArgs: ByteArray = ByteBuffer.allocate(8).putLong(args.rentalTerminationTime.toLong()).array()

        return TransactionInstruction(programId, keys, encodedArgs)
    }
    object ProgramAddressHelper {
        fun findProgramAddressSync(seeds: List<ByteArray>, programId: PublicKey): Pair<PublicKey, Int> {
            var nonce = 255
            var address: PublicKey
            while (nonce != 0) {
                try {
                    val seedsWithNonce = seeds.toMutableList()
                    seedsWithNonce.add(ByteBuffer.allocate(1).put(nonce.toByte()).array())
                    address = PublicKey.createProgramAddress(seedsWithNonce, programId)
                } catch (e: Exception) {
                    // Here, you can check for a specific condition if needed
                    nonce--
                    continue
                }
                return Pair(address, nonce)
            }
            throw Exception("Unable to find a viable program address nonce")
        }

        // The constants SEED_AFFAIR_LIST, SEED_ESCROW, etc., need to be defined.
        fun findAffairList(programId: PublicKey): Pair<PublicKey, Int> {
            return findProgramAddressSync(listOf("SEED_AFFAIR_LIST".toByteArray()), programId)
        }

        fun findVault(programId: PublicKey): Pair<PublicKey, Int> {
            return findProgramAddressSync(listOf("SEED_ESCROW".toByteArray()), programId)
        }

        fun findThreadAuthority(programId: PublicKey): Pair<PublicKey, Int> {
            return findProgramAddressSync(listOf("SEED_AUTHORITY_THREAD".toByteArray()), programId)
        }

        fun findAffair(authority: PublicKey, programId: PublicKey): Pair<PublicKey, Int> {
            return findProgramAddressSync(listOf("SEED_AFFAIR".toByteArray(), authority.toByteArray()), programId)
        }

        fun findLender(authority: PublicKey, programId: PublicKey): Pair<PublicKey, Int> {
            return findProgramAddressSync(listOf("SEED_LENDER".toByteArray(), authority.toByteArray()), programId)
        }

        fun findRentEscrow(lenderAccount: PublicKey, clientAccount: PublicKey, programId: PublicKey): Pair<PublicKey, Int> {
            return findProgramAddressSync(listOf("SEED_ESCROW".toByteArray(), lenderAccount.toByteArray(), clientAccount.toByteArray()), programId)
        }

        fun findRentAccount(lenderAccount: PublicKey, clientAccount: PublicKey, programId: PublicKey): Pair<PublicKey, Int> {
            return findProgramAddressSync(listOf("SEED_RENTAL".toByteArray(), lenderAccount.toByteArray(), clientAccount.toByteArray()), programId)
        }

        fun findRentalThreadId(clientAccount: PublicKey, affairAccount: PublicKey, programId: PublicKey): Pair<PublicKey, Int> {
            return findProgramAddressSync(listOf("SEED_THREAD".toByteArray(), clientAccount.toByteArray(), affairAccount.toByteArray()), programId)
        }

        fun findClockworkThreadAccount(threadAuthority: PublicKey, threadId: PublicKey, programId: PublicKey): Pair<PublicKey, Int> {
            return findProgramAddressSync(listOf("SEED_THREAD".toByteArray(), threadAuthority.toByteArray(), threadId.toByteArray()), programId)
        }
    }


}