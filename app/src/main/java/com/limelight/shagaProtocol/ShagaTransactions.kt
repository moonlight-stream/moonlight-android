package com.limelight.shagaProtocol

import android.util.Log
import com.limelight.shagaProtocol.ShagaTransactions.ProgramAddressHelper.findAffair
import com.limelight.shagaProtocol.ShagaTransactions.ProgramAddressHelper.findAffairList
import com.limelight.shagaProtocol.ShagaTransactions.ProgramAddressHelper.findClockworkThreadAccount
import com.limelight.shagaProtocol.ShagaTransactions.ProgramAddressHelper.findLender
import com.limelight.shagaProtocol.ShagaTransactions.ProgramAddressHelper.findRentAccount
import com.limelight.shagaProtocol.ShagaTransactions.ProgramAddressHelper.findRentEscrow
import com.limelight.shagaProtocol.ShagaTransactions.ProgramAddressHelper.findRentalThreadId
import com.limelight.shagaProtocol.ShagaTransactions.ProgramAddressHelper.findThreadAuthority
import com.limelight.shagaProtocol.ShagaTransactions.ProgramAddressHelper.findVault
import com.limelight.solanaWallet.SolanaApi
import com.solana.core.AccountMeta
import com.solana.core.PublicKey
import com.solana.core.TransactionInstruction
import java.nio.ByteBuffer
import com.solana.networking.serialization.format.Borsh
import com.solana.networking.serialization.serializers.solana.AnchorInstructionSerializer

class ShagaTransactions {
    companion object {
        const val SEED_AFFAIR_LIST = "affair_list"
        const val SEED_ESCROW = "escrow"
        const val SEED_LENDER = "lender"
        const val SEED_AFFAIR = "affair"
        const val SEED_RENTAL = "rental"
        const val SEED_THREAD = "thread"
        const val SEED_AUTHORITY_THREAD = "authority_thread"
        const val PROGRAM_ID_STRING = "9SwYZxTQUYruFSHYeTqrtB5pTtuGJEGksh7ufpNS1YK5"
        val PROGRAM_ID: PublicKey = PublicKey(PROGRAM_ID_STRING)
        const val CLOCKWORK_ID_STRING = "CLoCKyJ6DXBJqqu2VWx9RLbgnwwR6BMHHuyasVmfMzBh"
        val CLOCKWORK_ID: PublicKey = PublicKey(CLOCKWORK_ID_STRING)
        const val SYSTEM_PROGRAM_ID_STRING = "11111111111111111111111111111111"
        val SYSTEM_PROGRAM_ID: PublicKey = PublicKey(SYSTEM_PROGRAM_ID_STRING)
    }
    fun startRental(
        authority: PublicKey,
        client: PublicKey,
        args: SolanaApi.StartRentalInstructionArgs
    ): TransactionInstruction {
        // Use the helper functions to get the public keys
        val (affair, _) = findAffair(authority, PROGRAM_ID)
        val (lender, _) = findLender(authority, PROGRAM_ID)
        val (affairsList, _) = findAffairList(PROGRAM_ID)
        val (vault, _) = findVault(PROGRAM_ID)
        val (threadAuthority, _) = findThreadAuthority(PROGRAM_ID)
        val (escrow, _) = findRentEscrow(lender, client, PROGRAM_ID)
        val (rental, _) = findRentAccount(lender, client, PROGRAM_ID)
        val (threadId, _) = findRentalThreadId(threadAuthority, rental, PROGRAM_ID)
        val (rentalClockworkThread, _) = findClockworkThreadAccount(threadAuthority, threadId, CLOCKWORK_ID)
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
        keys.add(AccountMeta(SYSTEM_PROGRAM_ID, false, false))
        keys.add(AccountMeta(CLOCKWORK_ID, false, false))
        // Encode the arguments (assuming you have a Borsh encoding function)
        keys.forEach { accountMeta ->
            Log.d("DebugTag", "Account: ${accountMeta}, isSigner: ${accountMeta.isSigner}, isWritable: ${accountMeta.isWritable}")
        }
        val data = Borsh.encodeToByteArray(
            AnchorInstructionSerializer("start_rental"),
            args.rentalTerminationTime
        )

        return TransactionInstruction(
            PROGRAM_ID,
            keys,
            data
        )
    }


    fun endRental(
        authority: PublicKey,
        client: PublicKey
    ): TransactionInstruction {
        // Fetch the necessary public keys for the accounts involved
        val (affair, _) = findAffair(authority, PROGRAM_ID)
        val (lender, _) = findLender(authority, PROGRAM_ID)
        val (affairsList, _) = findAffairList(PROGRAM_ID)
        val (vault, _) = findVault(PROGRAM_ID)
        val (threadAuthority, _) = findThreadAuthority(PROGRAM_ID)
        val (escrow, _) = findRentEscrow(lender, client, PROGRAM_ID)
        val (rental, _) = findRentAccount(lender, client, PROGRAM_ID)
        val (threadId, _) = findRentalThreadId(threadAuthority, rental, PROGRAM_ID)
        val (rentalClockworkThread, _) = findClockworkThreadAccount(threadAuthority, threadId, CLOCKWORK_ID)
        // Create a list for the AccountMeta objects
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
        keys.add(AccountMeta(SYSTEM_PROGRAM_ID, false, false))
        keys.add(AccountMeta(CLOCKWORK_ID, false, false))
        // Encode the data (assuming you have a Borsh encoding function)
        keys.forEach { accountMeta ->
            Log.d("DebugTag", "Account: ${accountMeta}, isSigner: ${accountMeta.isSigner}, isWritable: ${accountMeta.isWritable}")
        }
        val data = Borsh.encodeToByteArray(
            AnchorInstructionSerializer("end_rental"),
            1
        )

        return TransactionInstruction(
            PROGRAM_ID,
            keys,
            data
        )
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

}