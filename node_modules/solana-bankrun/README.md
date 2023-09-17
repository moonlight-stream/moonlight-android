<div align="center">
    <img src="https://raw.githubusercontent.com/kevinheavey/solana-bankrun/main/logo.png" width="50%" height="50%">
</div>

---
# Bankrun

`Bankrun` is a superfast, powerful and lightweight framework
for testing Solana programs in NodeJS.

While people often use `solana-test-validator` for this,
`bankrun` is orders of magnitude faster and far more convenient.

You can also do things that are not possible with `solana-test-validator`,
such as jumping back and forth in time or dynamically setting account data.

If you've used [solana-program-test](https://crates.io/crates/solana-program-test)
you'll be familiar with `bankrun`, since that's what it uses under the hood.

For those unfamiliar, `bankrun` and `solana-program-test` work by spinning up a lightweight
`BanksServer` that's like an RPC node but much faster, and creating a `BanksClient` to talk to the
server. This author thought `solana-program-test` was a boring name, so he chose ``bankrun`` instead
(you're running Solana [Banks](https://github.com/solana-labs/solana/blob/master/runtime/src/bank.rs)).

## Minimal example

This example just transfers lamports from Alice to Bob without loading
any programs of our own. It uses the [jest](https://jestjs.io/)
test runner but you can use any test runner you like.

Note: If you have multiple test files you should disable parallel tests using the `--runInBand` Jest flag for now.
There is an [open issue](https://github.com/kevinheavey/solana-bankrun/issues/2)
where concurrent Jest tests occasionally fail due to the program name getting garbled.

Note: The underlying Rust process may print a lot of logs. You can control these with the `RUST_LOG` environment variable. If you want to silence these logs your test command would look like `RUST_LOG= jest --runInBand`.


```ts
import { start } from "solana-bankrun";
import { PublicKey, Transaction, SystemProgram } from "@solana/web3.js";

test("one transfer", async () => {
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
```

Some things to note here:

* The `context` object contains a `banks_client` to talk to the `BanksServer`,
  a `payer` keypair that has been funded with a bunch of SOL, and a `last_blockhash`
  that we can use in our transactions.
* We haven't loaded any specific programs, but by default we have access to
  the System Program, the SPL token programs and the SPL memo program.


## Installation

```
yarn add solana-bankrun
```

## Contributing

Make sure you have Yarn and the Rust toolchain installed.

Then run `yarn` to install deps, run `yarn build` to build the binary and `yarn test` to run the tests.
