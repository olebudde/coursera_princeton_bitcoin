import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

public class MaxFeeTxHandler {

	private UTXOPool myUtxoPool;

	/**
	 * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
	 * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
	 * constructor.
	 */

	public MaxFeeTxHandler(UTXOPool utxoPool) {
		myUtxoPool = new UTXOPool(utxoPool);
	}

	// Lesson learned from profiling testscripts at: http://bitcoinbook.cs.princeton.edu/: Verifying signature is extremely CPU intensive
	// We need to cache results.
	private class MyTransaction {
		private Transaction tx;
		private Boolean [] verifySignatureCache;  // Boolean array, so each element can have three states. Not set, true or false

		public Transaction getTx() {
			return tx;
		}

		public MyTransaction(Transaction tx) {
			super();
			this.tx = tx;
			this.verifySignatureCache = new Boolean[tx.getInputs().size()];
		}

		boolean isSignatureCheckCached(int index)
		{
			return verifySignatureCache[index] != null ? true : false;
		}

		public void setSignatureCheckCache(int index,boolean value) {
			verifySignatureCache[index] = value == true ? Boolean.TRUE : Boolean.FALSE;
		}

		public boolean getSignatureCheckCache(int index) {
			return verifySignatureCache[index].equals(Boolean.TRUE) ? true : false;    		
		}    	
	}

	private boolean isValidAllInputClaimsInPool(Transaction tx,UTXOPool UtxoPool)
	{        	
		// 1 all outputs claimed by {@code tx} are in the current UTXO pool,	
		for (int index = 0;index < tx.numInputs();index++) {
			Transaction.Input in = tx.getInput(index);    		
			UTXO checker = new UTXO(in.prevTxHash, in.outputIndex);
			if (!UtxoPool.contains(checker)) {
				// System.err.println("Not valid due to missing in pool");
				return false;
			} 
		}
		return true;
	}

	private boolean isValidAllInputClaimsSignatureValid(MyTransaction myTx,UTXOPool UtxoPool) {
		// (2) the signatures on each input of {@code tx} are valid,
		Transaction tx = myTx.getTx();
		for (int index = 0;index < tx.numInputs();index++) {
			Transaction.Input in = tx.getInput(index);    		
			UTXO u = new UTXO(in.prevTxHash, in.outputIndex);
			Transaction.Output out = UtxoPool.getTxOutput(u);

			if (out == null) {
				// System.err.println("Not valid due to failed signature [1]");
				return false;
			}

			if (!myTx.isSignatureCheckCached(index)) {

				//        		// Switch comments here if you are using the testscripts at: http://bitcoinbook.cs.princeton.edu/
				byte message[] = tx.getRawDataToSign(index);
				boolean result = Crypto.verifySignature(out.address, message, in.signature);
				//    			RSAKey address = out.address;
				//              boolean result = address.verifySignature(tx.getRawDataToSign(index), in.signature);

				myTx.setSignatureCheckCache(index, result);
			}

			if (myTx.getSignatureCheckCache(index) == false) {
				// System.err.println("Not valid due to failed signature [2]");
				return false;
			}
		}
		return true;

	}

	private boolean isValidAllInputClaimsNoDoubleSpend(Transaction tx,UTXOPool UtxoPool) {
		// (3) no UTXO is claimed multiple times by {@code tx},
		UTXOPool spentPool = new UTXOPool();
		for (int index = 0;index < tx.numInputs();index++) {
			Transaction.Input in = tx.getInput(index);    		
			UTXO checker = new UTXO(in.prevTxHash, in.outputIndex);
			if (!UtxoPool.contains(checker)) {
				return false;  // Safe guard, should be there
			} else {
				if (spentPool.contains(checker)) {
					return false;  // Double spend
				} else {
					spentPool.addUTXO(checker,null);    				
				}
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


	private double getInputClaims(Transaction tx,UTXOPool UtxoPool)
	{
		double retValue = 0.0;
		for (int i = 0;i < tx.numInputs();i++) {
			Transaction.Input in = tx.getInput(i);
			UTXO utxo = new UTXO(in.prevTxHash,in.outputIndex);
			Transaction.Output out = UtxoPool.getTxOutput(utxo);
			if (out != null) {
				retValue += out.value;
			}
		}

		return retValue;    	
	}

	private double getNetInputValue(Transaction tx,UTXOPool UtxoPool) {
		return getInputClaims(tx,UtxoPool) - getOutputSpends(tx);    	
	}



	private boolean isValidAllInputClaimsLargerEqualThanOutputSpends(Transaction tx, UTXOPool UtxoPool) {
		//  (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
		double net = getNetInputValue(tx,UtxoPool);

		if (net < 0.0) {
			// System.err.println("Not valid due to out > in");
			return false;    		
		}
		return true;
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


	public boolean isValidTx(Transaction tx) {
		return isValidTx(new MyTransaction(tx), myUtxoPool);
	}

	private boolean isValidTx(MyTransaction myTx,UTXOPool utxoPool) {
		if (!isValidAllInputClaimsInPool(myTx.getTx(),utxoPool)) {
			return false;
		}    	    

		if (!isValidAllInputClaimsSignatureValid(myTx,utxoPool)) {
			return false;
		}

		if (!isValidAllInputClaimsNoDoubleSpend(myTx.getTx(),utxoPool)) {
			return false;
		}

		if (!isValidAllOutputSpendsNonNegative(myTx.getTx())) {
			return false;
		}

		if (!isValidAllInputClaimsLargerEqualThanOutputSpends(myTx.getTx(),utxoPool)) {
			return false;
		}

		return true;
	}

	private double doTransaction(MyTransaction tx,UTXOPool UtxoPool) {
		double retValue = 0.0;
		retValue -= addOutputsToUTXOPool(tx.getTx(), UtxoPool);
		retValue += removeInputsFromUTX0Pool(tx.getTx(), UtxoPool);
		return retValue;
	}


	private double removeInputsFromUTX0Pool(Transaction tx,UTXOPool UtxoPool) {
		double retValue = 0.0;
		for (int i = 0;i < tx.numInputs();i++) {
			Transaction.Input in = tx.getInput(i);
			UTXO utxo = new UTXO(in.prevTxHash,in.outputIndex);
			if (!UtxoPool.contains(utxo)) {
				throw new AssertionError("Unspent transaction not present in pool");
			}
			Transaction.Output out = UtxoPool.getTxOutput(utxo);
			if (out != null) {
				retValue += out.value;
			}			
			UtxoPool.removeUTXO(utxo);
		}
		return retValue;
	}

	private double addOutputsToUTXOPool(Transaction tx,UTXOPool UtxoPool) {
		double retValue = 0.0;
		for (int i = 0;i < tx.numOutputs();i++) {
			Transaction.Output out = tx.getOutput(i);
			UTXO utxo = new UTXO(tx.getHash(), i);
			if (UtxoPool.contains(utxo)) {
				throw new AssertionError("Unspent transaction already present in pool");
			}
			retValue += out.value;
			UtxoPool.addUTXO(utxo, out);
		}
		return retValue;
	}

	private double doTransactionAndMovebetweenLists(LinkedList<MyTransaction> remainingTx,MyTransaction tx,LinkedList<MyTransaction> returnedTx,UTXOPool utxoPool)
	{
		double retVal = doTransaction(tx,utxoPool);
		returnedTx.add(tx);
		remainingTx.remove(tx);   			
		return retVal;    	
	}

	private boolean isIndependantTransaction(MyTransaction txTocheck,LinkedList<MyTransaction> list) {

		Transaction checker = txTocheck.getTx(); 

		Set<UTXO> claimsSet = new HashSet<UTXO>();
		for (int index = 0;index < checker.numInputs();index++) {
			Transaction.Input in = checker.getInput(index);    		
			claimsSet.add(new UTXO(in.prevTxHash, in.outputIndex));
		}

		for (MyTransaction txIter:list) {
			Transaction tx = txIter.getTx();
			for (int index = 0;index < tx.numInputs();index++) {
				Transaction.Input in = tx.getInput(index);    		
				UTXO u = new UTXO(in.prevTxHash, in.outputIndex);
				if (claimsSet.contains(u)) {
					return false;
				}
			}        		
		}
		return true;    	
	}

	private LinkedList<MyTransaction> getIndependantTransactions(LinkedList<MyTransaction> candidateList,LinkedList<MyTransaction> remainingList)
	{

		LinkedList<MyTransaction> retVal = new LinkedList<MyTransaction>();

		for (MyTransaction tx:candidateList) {
			LinkedList<MyTransaction> testRemainingList = new LinkedList<MyTransaction>(remainingList);
			testRemainingList.remove(tx);			
			if (isIndependantTransaction(tx, testRemainingList)) {
				retVal.add(tx);
			}				
		}

		return retVal;
	}    

	private void getTxList(LinkedList<MyTransaction> remainingTx,MyTransaction foundTx,LinkedList<MyTransaction> returnedTx,UTXOPool utxoPool,double fee) 
	{

		if (foundTx != null) {
			fee += doTransactionAndMovebetweenLists(remainingTx, foundTx, returnedTx, utxoPool);
		}

		boolean outerLoopFlag = true;
		while (outerLoopFlag) {   		
			outerLoopFlag = false;

			LinkedList<MyTransaction> candidateList = new LinkedList<MyTransaction>();
			for (MyTransaction tx:remainingTx) {
				if (isValidTx(tx, utxoPool)) {	   				
					candidateList.add(tx);
				}
			}

			LinkedList<MyTransaction> txList = getIndependantTransactions(candidateList,remainingTx);
			for (MyTransaction tx:txList) {	   			
				outerLoopFlag = true;
				fee += doTransactionAndMovebetweenLists(remainingTx, tx, returnedTx, utxoPool);
				candidateList.remove(tx);   				
			}

			for (MyTransaction tx:candidateList) {
				LinkedList<MyTransaction> newRemainingTx = new LinkedList<MyTransaction>(remainingTx);
				LinkedList<MyTransaction> newReturnedTx = new LinkedList<MyTransaction>(returnedTx);
				UTXOPool newUtxoPool = new UTXOPool(utxoPool);   			
				getTxList(newRemainingTx,tx,newReturnedTx,newUtxoPool,fee);   				
			}
		}

		if (fee > highestFee) {
			highestFee = fee;
			highestFeeCandidate = returnedTx;
		}
	}


	double highestFee = -1.0;
	private LinkedList<MyTransaction> highestFeeCandidate;

	/**
	 * Handles each epoch by receiving an unordered array of proposed transactions, checking each
	 * transaction for correctness, returning a mutually valid array of accepted transactions, and
	 * updating the current UTXO pool as appropriate.
	 * @throws Exception 
	 */

	public Transaction[] handleTxs(Transaction[] possibleTxs)  {
		// A brute force attack to the problem. follow all solutions when multiple transactions are competing 
		// for the same UTX0.  
		// Sorting by highest fee, then adding in that order does NOT solve the problem because adding one transaction 
		// with one fee can block for two later fees that in total are higher.    	

		highestFee = -1.0;
		highestFeeCandidate = null;

		LinkedList<MyTransaction> returnedTx = new LinkedList<MyTransaction>();
		LinkedList<MyTransaction> remainingTx = new LinkedList<MyTransaction>();
		// #0 Just copy
		for (int i = 0;i < possibleTxs.length;i++) {
			remainingTx.add(new MyTransaction(possibleTxs[i]));
		}


		// #1 Find candidate using recursion
		UTXOPool tmpPool = new UTXOPool(myUtxoPool);    	   		   		
		getTxList(remainingTx,null,returnedTx,tmpPool,0.0);

		returnedTx = highestFeeCandidate;

		// #3 Commit result and make output buffer
		int outerIndex = 0;
		Transaction[] retVal = new Transaction[returnedTx.size()];
		for (MyTransaction tx:returnedTx) {
			doTransaction(tx, myUtxoPool);
			retVal[outerIndex++] = tx.getTx();
		}

		return retVal;
	}


	static String byte2String(byte b[])
	{
		int hash = 0;
		for (int i = 0;i < b.length;i++) {
			hash += 128 + b[i];
		}
		return String.format("0x%04x",hash);
	}
}
