package com.limelight.utils
// package com.solana

import java.net.URL
import com.solana.networking.HttpNetworkingRouter
import com.solana.networking.Network
import com.solana.networking.RPCEndpoint
import com.solana.core.DerivationPath
import com.solana.core.HotAccount
import com.solana.Solana

object SolanaUtil {

    // UPDATE THIS SHIT
    fun isInitialized(): Boolean {
        return router != null && api != null
    }

    const val RPC_URL = "https://api.devnet.solana.com"

    private val router = HttpNetworkingRouter(
        RPCEndpoint.custom(
            URL(RPC_URL),
            URL(RPC_URL),
            Network.devnet
        )
    )

    private val api = Api(router)

    fun createNewWallet(): HotAccount {
        return HotAccount()
    }

    fun getWalletFromSeedPhrase(words: List<String>, derivationPath: DerivationPath = DerivationPath.BIP44_M_44H_501H_0H): HotAccount {
        return HotAccount.fromMnemonic(words, "", derivationPath)
    }

    fun getPublicKey(account: HotAccount): String {
        return account.publicKey.toString()
    }

    suspend fun getBalance(account: HotAccount): String {
        val response = api.getBalance(account.publicKey.toString())
        return response
    }
}
