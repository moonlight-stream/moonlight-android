package com.limelight.shagaMap

import android.content.Context
import android.util.Log
import com.limelight.solanaWallet.EncryptionHelper.decrypt
import com.limelight.solanaWallet.SolanaApi
import com.limelight.solanaWallet.SolanaPreferenceManager
import com.limelight.solanaWallet.WalletUtils
import com.limelight.utils.Loggatore
import com.solana.core.AccountMeta
import com.solana.core.HotAccount
import com.solana.core.PublicKey
import com.solana.core.TransactionInstruction
import java.nio.ByteBuffer
import com.solana.networking.serialization.format.Borsh
import com.solana.networking.serialization.serializers.solana.AnchorInstructionSerializer

class ShagaTransactions {
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
        val (rentalClockworkThread, _) = ProgramAddressHelper.findRentalThreadId(client, affair, programId)
        val (clockworkProgram, _) = ProgramAddressHelper.findClockworkThreadAccount(threadAuthority, affair, programId)
        // Initialize the keys list for TransactionInstruction
        val keys = mutableListOf<AccountMeta>()
        keys.add(AccountMeta(client, true, true))
        keys.add(AccountMeta(lender, false, true))
        keys.add(AccountMeta(affair, false, true))
        keys.add(AccountMeta(affairsList, false, true))
        keys.add(AccountMeta(escrow, false, true))
        keys.add(AccountMeta(rental, false, true))
        keys.add(AccountMeta(vault, false, true))
        keys.add(AccountMeta(rentalClockworkThread, false, true))
        keys.add(AccountMeta(threadAuthority, false, false))
        keys.add(AccountMeta(clockworkProgram, false, false))
        // Encode the arguments
        return TransactionInstruction(programId, keys, Borsh.encodeToByteArray(
                AnchorInstructionSerializer("start_rental"), args.rentalTerminationTime))
    }

    object ProgramAddressHelper {
        private fun findProgramAddressSync(seeds: List<ByteArray>, programId: PublicKey): Pair<PublicKey, Int> {
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

        fun findAffairList(programId: PublicKey): Pair<PublicKey, Int> {
            return findProgramAddressSync(listOf(SEED_AFFAIR_LIST.toByteArray()), programId)
        }

        fun findVault(programId: PublicKey): Pair<PublicKey, Int> {
            return findProgramAddressSync(listOf(SEED_ESCROW.toByteArray()), programId)
        }

        fun findThreadAuthority(programId: PublicKey): Pair<PublicKey, Int> {
            return findProgramAddressSync(listOf(SEED_AUTHORITY_THREAD.toByteArray()), programId)
        }

        fun findAffair(authority: PublicKey, programId: PublicKey): Pair<PublicKey, Int> {
            return findProgramAddressSync(listOf(SEED_AFFAIR.toByteArray(), authority.toByteArray()), programId)
        }

        fun findLender(authority: PublicKey, programId: PublicKey): Pair<PublicKey, Int> {
            return findProgramAddressSync(listOf(SEED_LENDER.toByteArray(), authority.toByteArray()), programId)
        }

        fun findRentEscrow(lenderAccount: PublicKey, clientAccount: PublicKey, programId: PublicKey): Pair<PublicKey, Int> {
            return findProgramAddressSync(listOf(SEED_ESCROW.toByteArray(), lenderAccount.toByteArray(), clientAccount.toByteArray()), programId)
        }

        fun findRentAccount(lenderAccount: PublicKey, clientAccount: PublicKey, programId: PublicKey): Pair<PublicKey, Int> {
            return findProgramAddressSync(listOf(SEED_RENTAL.toByteArray(), lenderAccount.toByteArray(), clientAccount.toByteArray()), programId)
        }

        fun findRentalThreadId(clientAccount: PublicKey, affairAccount: PublicKey, programId: PublicKey): Pair<PublicKey, Int> {
            return findProgramAddressSync(listOf(SEED_THREAD.toByteArray(), clientAccount.toByteArray(), affairAccount.toByteArray()), programId)
        }

        fun findAffairThreadId(threadAuthority: PublicKey, affairAccount: PublicKey, programId: PublicKey): Pair<PublicKey, Int> {
            return findProgramAddressSync(listOf(SEED_THREAD.toByteArray(), threadAuthority.toByteArray(), affairAccount.toByteArray()), programId)
        }

        fun findClockworkThreadAccount(threadAuthority: PublicKey, threadId: PublicKey, programId: PublicKey): Pair<PublicKey, Int> {
            return findProgramAddressSync(listOf(SEED_THREAD.toByteArray(), threadAuthority.toByteArray(), threadId.toByteArray()), programId)
        }
    }
    companion object {
        const val SEED_AFFAIR_LIST = "affair_list"
        const val SEED_ESCROW = "escrow"
        const val SEED_LENDER = "lender"
        const val SEED_AFFAIR = "affair"
        const val SEED_RENTAL = "rental"
        const val SEED_THREAD = "thread"
        const val SEED_AUTHORITY_THREAD = "authority_thread"
    }

}