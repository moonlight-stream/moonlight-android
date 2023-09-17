package com.limelight.solanaWallet

import kotlinx.coroutines.runBlocking

import com.limelight.solanaWallet.SolanaAccount
import com.limelight.solanaWallet.SolanaApi


object SolanaUtilManualTest {

    /*
    fun testSolanaInitialization() {
        if (SolanaUtil.isInitialized()) {
            println("Solana initialization successful")
        } else {
            println("Solana initialization failed")
        }
    }
    */

    fun testGetBalance() {
        val wallet = SolanaAccount.create() // Create a new Solana account
        val address = wallet.publicKey.toString() // Get the public key (address) as a String
        runBlocking {
            SolanaApi.getBalance(address, object : SolanaApi.BalanceCallback {
                override fun onBalanceReceived(balance: Double?) {
                    println("Wallet balance: $balance")
                }
            })
        }
    }

}


