import java.util.LinkedList;

public class TxHandler {

	/**
	 * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
	 * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
	 * constructor.
	 */

	private UTXOPool myUtxoPool;

	public TxHandler(UTXOPool utxoPool) {
		myUtxoPool = new UTXOPool(utxoPool);
	}

	/**
	 * @return true if:
	 * (1) all outputs claimed by {@code tx} are in the current UTXO pool, 
	 * (2) the signatures on each input of {@code tx} are valid, 
	 * (3) no UTXO is claimed multiple times by {@code tx},
	 * (4) all of {@code tx}s output values are non-negative, and
	 * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
	 *     values; and false otherwise.
	 * @throws Exception 
	 */


	private boolean isValidAllInputClaimsInPool(Transaction tx)
	{        	
		// 1 all outputs claimed by {@code tx} are in the current UTXO pool,	
		for (int index = 0;index < tx.numInputs();index++) {
			Transaction.Input in = tx.getInput(index);    		
			UTXO checker = new UTXO(in.prevTxHash, in.outputIndex);
			if (!myUtxoPool.contains(checker)) {
				// System.err.println("Not valid due to missing in pool");
				return false;
			} 
		}
		return true;
	}




	private boolean isValidAllInputClaimsSignatureValid(Transaction tx) {
		// (2) the signatures on each input of {@code tx} are valid, 
		for (int index = 0;index < tx.numInputs();index++) {
			Transaction.Input in = tx.getInput(index);    		
			UTXO u = new UTXO(in.prevTxHash, in.outputIndex);
			Transaction.Output out = myUtxoPool.getTxOutput(u);

			if (out == null) {
				// System.err.println("Not valid due to failed signature [1]");
				return false;
			}

			//    		// Switch comments here if you are using the testscripts at: http://bitcoinbook.cs.princeton.edu/    		
			//          RSAKey address = out.address;
			//          if (!address.verifySignature(tx.getRawDataToSign(index), in.signature)) {
			//  			// System.err.println("Not valid due to failed signature [2]");
			//             return false;
			//          }    		

			byte message[] = tx.getRawDataToSign(index);
			if (!Crypto.verifySignature(out.address, message, in.signature)) {
				// System.err.println("Not valid due to failed signature [2]");
				return false;
			}
		}
		return true;

	}

	private boolean isValidAllInputClaimsNoDoubleSpend(Transaction tx) {
		// (3) no UTXO is claimed multiple times by {@code tx},
		UTXOPool spentPool = new UTXOPool();
		for (int index = 0;index < tx.numInputs();index++) {
			Transaction.Input in = tx.getInput(index);    		
			UTXO checker = new UTXO(in.prevTxHash, in.outputIndex);
			if (spentPool.contains(checker)) {
				// System.err.println("Not valid due to double spend [1]");
				return false;  // Double spend
			} else {
				spentPool.addUTXO(checker,null);    				
			}
		}
		return true;
	}



	private boolean isValidAllOutputSpendsNonNegative(Transaction tx) {
		// (4) all of {@code tx}s output values are non-negative, and
		for (int index = 0;index < tx.numOutputs();index++) {
			Transaction.Output out = tx.getOutput(index);
			if (out.value < 0.0) {
				// System.err.println("Not valid due to negative amount");
				return false;
			}
		}
		return true;
	}



	private double getOutputSpends(Transaction tx) {
		double retVal = 0.0;
		for (int i = 0;i < tx.numOutputs();i++) {
			Transaction.Output out = tx.getOutput(i);
			retVal += out.value;
		}
		return retVal;
	}

	private double getInputClaims(Transaction tx)
	{
		double retValue = 0.0;
		for (int i = 0;i < tx.numInputs();i++) {
			Transaction.Input in = tx.getInput(i);
			UTXO utxo = new UTXO(in.prevTxHash,in.outputIndex);
			Transaction.Output out = myUtxoPool.getTxOutput(utxo);
			if (out != null) {
				retValue += out.value;
			}
		}

		return retValue;    	
	}

	private double getNetInputValue(Transaction tx) {
		return getInputClaims(tx) - getOutputSpends(tx);    	
	}

	private boolean isValidAllInputClaimsLargerEqualThanOutputSpends(Transaction tx) {
		//  (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output

		double net = getNetInputValue(tx);   	
		if (net < 0.0) {
			// System.err.println("Not valid due to out > in");
			return false;    		
		}
		return true;
	}

	public boolean isValidTx(Transaction tx) {
		if (!isValidAllInputClaimsInPool(tx)) {
			return false;
		}    	    

		if (!isValidAllInputClaimsSignatureValid(tx)) {
			return false;
		}

		if (!isValidAllInputClaimsNoDoubleSpend(tx)) {
			return false;
		}

		if (!isValidAllOutputSpendsNonNegative(tx)) {
			return false;
		}

		if (!isValidAllInputClaimsLargerEqualThanOutputSpends(tx)) {
			return false;
		}

		return true;
	}

	/**
	 * Handles each epoch by receiving an unordered array of proposed transactions, checking each
	 * transaction for correctness, returning a mutually valid array of accepted transactions, and
	 * updating the current UTXO pool as appropriate.
	 * @throws Exception 
	 */

	public Transaction[] handleTxs(Transaction[] possibleTxs)  {


		LinkedList<Transaction> returnedTx = new LinkedList<Transaction>();
		LinkedList<Transaction> remainingTx = new LinkedList<Transaction>();
		// #0 Just copy
		for (int i = 0;i < possibleTxs.length;i++) {
			remainingTx.add(possibleTxs[i]);
		}


		// try all items in "remaining" list until nothing more can be added.
		boolean foundSome = true;

		while (foundSome) {
			foundSome = false;
			LinkedList<Transaction> foundNowTx= new LinkedList<Transaction>();
			for (Transaction tx:remainingTx) {
				if (isValidTx(tx)) {
					foundSome = true;
					doTransaction(tx, myUtxoPool);
					foundNowTx.add(tx);
				}
			}
			for (Transaction tx:foundNowTx) {
				remainingTx.remove(tx);
				returnedTx.add(tx);
			}

		}

		int outerIndex = 0;
		Transaction[] retVal = new Transaction[returnedTx.size()];
		for (Transaction tx:returnedTx) {
			retVal[outerIndex++] = tx;
		}

		return retVal;
	}

	private void doTransaction(Transaction tx,UTXOPool UtxoPool) {
		addOutputsToUTXOPool(tx, UtxoPool);
		removeInputsFromUTX0Pool(tx, UtxoPool);
	}

	private void removeInputsFromUTX0Pool(Transaction tx,UTXOPool UtxoPool) {
		for (int i = 0;i < tx.numInputs();i++) {
			Transaction.Input in = tx.getInput(i);
			UTXO utxo = new UTXO(in.prevTxHash,in.outputIndex);
			if (!myUtxoPool.contains(utxo)) {
				throw new AssertionError("Unspent transaction not present in pool");
			}
			UtxoPool.removeUTXO(utxo);
		}
	}

	private void addOutputsToUTXOPool(Transaction tx,UTXOPool UtxoPool) {
		for (int i = 0;i < tx.numOutputs();i++) {
			Transaction.Output out = tx.getOutput(i);
			UTXO utxo = new UTXO(tx.getHash(), i);
			if (myUtxoPool.contains(utxo)) {
				throw new AssertionError("Unspent transaction already present in pool");
			}
			UtxoPool.addUTXO(utxo, out);
		}
	}


}
