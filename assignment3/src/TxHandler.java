import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;

public class TxHandler {

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
	
	private UTXOPool pristineUtxoPool;
	
    public TxHandler(UTXOPool utxoPool) {
    	pristineUtxoPool = new UTXOPool(utxoPool);
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
    

    
    
    private boolean isValidAllInputClaimsSignatureValid(Transaction tx,UTXOPool UtxoPool) {
        // (2) the signatures on each input of {@code tx} are valid, 
    	for (int index = 0;index < tx.numInputs();index++) {
    		Transaction.Input in = tx.getInput(index);    		
    		UTXO u = new UTXO(in.prevTxHash, in.outputIndex);
    		Transaction.Output out = UtxoPool.getTxOutput(u);
    			
    		if (out == null) {
    			// System.err.println("Not valid due to failed signature [1]");
    			return false;
    		}
    		
    		byte message[] = tx.getRawDataToSign(index);
    		if (!Crypto.verifySignature(out.address, message, in.signature)) {
    			// System.err.println("Not valid due to failed signature [2]");
    			return false;
    		}
    	}
    	return true;
    	
    }

    private boolean isValidAllInputClaimsNoDoubleSpend(Transaction tx,UTXOPool UtxoPool) {
    	// (3) no UTXO is claimed multiple times by {@code tx},
    	UTXOPool spentPool = new UTXOPool();
//    	UTXOPool tmpPool = new UTXOPool(UtxoPool);
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
    public boolean isValidTx(Transaction tx) {
    	// Strictly not necessary since pool is not changed
    	UTXOPool tmpPool = new UTXOPool(pristineUtxoPool);
    	return isValidTx(tx, tmpPool);
    }
    
    private boolean isValidTx(Transaction tx,UTXOPool utxoPool) {
    	if (!isValidAllInputClaimsInPool(tx,utxoPool)) {
    		return false;
    	}    	    

    	if (!isValidAllInputClaimsSignatureValid(tx,utxoPool)) {
    		return false;
    	}
    	
        if (!isValidAllInputClaimsNoDoubleSpend(tx,utxoPool)) {
        	return false;
        }
    	
        if (!isValidAllOutputSpendsNonNegative(tx)) {
        	return false;
        }
        
        if (!isValidAllInputClaimsLargerEqualThanOutputSpends(tx,utxoPool)) {
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


    private void getTxList(LinkedList<Transaction> remainingTx,Transaction foundTx,LinkedList<Transaction> returnedTx,UTXOPool utxoPool,LinkedList<LinkedList<Transaction>> candidates) 
    {
    			
   		if (foundTx != null) {
			doTransaction(foundTx,utxoPool);
       		returnedTx.add(foundTx);
       		remainingTx.remove(foundTx);   			
   		}
    	
   		boolean foundSome = false;
   		for (Transaction tx:remainingTx) {
   			if (isValidTx(tx, utxoPool)) {
   				LinkedList<Transaction> newRemainingTx = new LinkedList<Transaction>(remainingTx);
   				LinkedList<Transaction> newReturnedTx = new LinkedList<Transaction>(returnedTx);
   				UTXOPool newUtxoPool = new UTXOPool(utxoPool);
   				getTxList(newRemainingTx,tx,newReturnedTx,newUtxoPool,candidates);   				
   				foundSome = true;
   				break;  // Stop loop after first find
   			}   		
    	}
        	   	
   		if (!foundSome) {
   			candidates.add(returnedTx);
   		}
    }
    
    
    public Transaction[] handleTxs(Transaction[] possibleTxs)  {

    	
    	// Remove the "premature" testing, all in big loop?
    	// Stop when no adding is possible anymore?
    	
   		
   		LinkedList<Transaction> returnedTx = new LinkedList<Transaction>();
    	LinkedList<Transaction> remainingTx = new LinkedList<Transaction>();
    	// #0 Just copy
    	for (int i = 0;i < possibleTxs.length;i++) {
			remainingTx.add(possibleTxs[i]);
    	}

    	    	
    	LinkedList<LinkedList<Transaction>> candidates = new LinkedList<LinkedList<Transaction>>();
   		UTXOPool tmpPool = new UTXOPool(pristineUtxoPool);

    	getTxList(remainingTx,null,returnedTx,tmpPool,candidates);
    	    	
    	returnedTx = candidates.getFirst();
    	

    	// Make output buffer
    	int outerIndex = 0;
    	Transaction[] retVal = new Transaction[returnedTx.size()];
    	for (Transaction tx:returnedTx) {
    		doTransaction(tx, pristineUtxoPool);
    		retVal[outerIndex++] = tx;
    	}
    	    	    	
    	return retVal;
    	
    }


    
	private void doTransaction(Transaction tx,UTXOPool UtxoPool) {
		addOutputsToUTXOPool(tx, UtxoPool);
		removeInputsFromUTX0Pool(tx, UtxoPool);
	}

	
	public UTXOPool getUTXOPool(){
		return pristineUtxoPool;
	}
	
    
    // 
    // This would be like putting back stuff to get coins
	private void removeInputsFromUTX0Pool(Transaction tx,UTXOPool UtxoPool) {
		for (int i = 0;i < tx.numInputs();i++) {
			Transaction.Input in = tx.getInput(i);
			UTXO utxo = new UTXO(in.prevTxHash,in.outputIndex);
			if (!UtxoPool.contains(utxo)) {
				// Safeguard, make it crash
				UtxoPool = null;
			}
			UtxoPool.removeUTXO(utxo);
		}
	}

    // This would be like generating coins 
	private void addOutputsToUTXOPool(Transaction tx,UTXOPool UtxoPool) {
    	for (int i = 0;i < tx.numOutputs();i++) {
    		Transaction.Output out = tx.getOutput(i);
    		UTXO utxo = new UTXO(tx.getHash(), i);
			if (UtxoPool.contains(utxo)) {
				// Safeguard, make it crash
				UtxoPool = null;				
			}
			UtxoPool.addUTXO(utxo, out);
    	}
	}
    
    
}
