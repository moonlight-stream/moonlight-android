import { start } from 'solana-bankrun';
import { PublicKey, Transaction, SystemProgram } from '@solana/web3.js';

test('one transfer', async () => {
  const context = await start([], []);
  const client = context.banksClient;
  const payer = context.payer;
  const receiver = PublicKey.unique();
  const blockhash = context.lastBlockhash;
  const transferLamports = 1_000_000n;
  const ixs = [
    SystemProgram.transfer({
      fromPubkey: payer.publicKey,
      toPubkey: receiver,
      lamports: transferLamports,
    }),
  ];
  const tx = new Transaction();
  tx.recentBlockhash = blockhash;
  tx.add(...ixs);
  tx.sign(payer);
  await client.processTransaction(tx);
  const balanceAfter = await client.getBalance(receiver);
  expect(balanceAfter).toEqual(transferLamports);
});
