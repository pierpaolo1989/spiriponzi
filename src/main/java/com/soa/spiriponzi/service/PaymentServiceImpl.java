package com.soa.spiriponzi.service;

import java.util.List;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.script.Script.ScriptType;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class PaymentServiceImpl implements PaymentService {
	
	private final NetworkParameters networkParameters;
	
	public PaymentServiceImpl(@Value("${bitcoin.network}") String bitcoinNetwork) {
		networkParameters = NetworkParameters.fromID(bitcoinNetwork);
	}

	@Async
	@Override
	public void createTransaction(String address, long amount) {
		
		Wallet wallet = Wallet.createDeterministic(networkParameters, ScriptType.P2PKH);
		DeterministicSeed keyChainSeed = wallet.getKeyChainSeed();
		String seedWords = String.join("  ", keyChainSeed.getMnemonicCode());
		
		// Create a transaction object
		Transaction transaction = new Transaction(networkParameters);

		// Determine amount to send - satoshis in long data type
		final Coin value = Coin.valueOf(amount);

		// Define the recipient address
		Address to = Address.fromString(networkParameters, address);

		// Get unspent transaction outputs
		List<TransactionOutput> unspents = wallet.getUnspents();

		transaction.addInput(wallet.getUnspents().get(0));
		transaction.addOutput(value, to);

		SendRequest request = SendRequest.forTx(transaction);
		request.feePerKb = Coin.valueOf(1000);

		// Instantiate a PeerGroup
		PeerGroup peerGroup = new PeerGroup(networkParameters);

		try {
		    Wallet.SendResult sendResult = wallet.sendCoins(peerGroup, request);
		    log.info("sendResult: {} ", sendResult); 

		} catch (Exception e) {
			log.error("error sending payment, err={}", e.getMessage(), e);
		}
	}

}
