package com.limelight.utils

import kotlinx.coroutines.runBlocking

object SolanaUtilManualTest {

    fun testSolanaInitialization() {
        if (SolanaUtil.isInitialized()) {
            println("Solana initialization successful")
        } else {
            println("Solana initialization failed")
        }
    }

    fun testGetBalance() {
        val wallet = SolanaUtil.createNewWallet()
        runBlocking {
            val balance = SolanaUtil.getBalance(wallet)
            println("Wallet balance: $balance")
        }
    }
}
