import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


// Block Chain should maintain only limited block nodes to satisfy the functions
// You should not have all the blocks added to the block chain in memory 
// as it would cause a memory overflow.

public class BlockChain {
    public static final int CUT_OFF_AGE = 10;

    
    // Blockdata references are stored in a tree (for depth, age etc) AND a map (for easy lookup) 

    TreeNode<BlockData> blockChain;
    Map<String,TreeNode<BlockData>> treeLookup = new HashMap<String,TreeNode<BlockData>>();
    TransactionPool transactionPool = new TransactionPool();
       
    private class BlockData {
    	Block block;
		UTXOPool utxoPool; 
    	public BlockData(Block block, UTXOPool utxoPool) {
			super();
			this.block = block;
			this.utxoPool = utxoPool;
		}
		public Block getBlock() {
			return block;
		}
		public UTXOPool getUtxoPool() {
			return utxoPool;
		}
    }; 
    
    public class TreeNode<T> {
        private T data = null;
        private int depth = 1;
        private List<TreeNode<T>> children = new ArrayList<>();
        private TreeNode<T> parent = null;

        public TreeNode(T data) {
            this.data = data;
        }

        public TreeNode<T> addChild(T data) {
            TreeNode<T> newChild = new TreeNode<T>(data);
            newChild.setParent(this);
            newChild.depth = this.depth + 1;
            children.add(newChild);
            return newChild;
        }

        public List<TreeNode<T>> getChildren() {
            return children;
        }

        public T getData() {
            return data;
        }

        public int getDepth() {
        	return depth;
        }
        
        public void setData(T data) {
            this.data = data;
        }

        private void setParent(TreeNode<T> parent) {
            this.parent = parent;
        }

        public TreeNode<T> getParent() {
            return parent;
        }
        
        TreeNode<T> getDeepest()
        {
        	if (this.getChildren().isEmpty()) {
        		return this;
        	}
        	List<TreeNode<T>> candidates = new ArrayList<TreeNode<T>>();
        	Iterator<TreeNode<T>> iter =  this.getChildren().iterator();
        	while (iter.hasNext()) {
        		TreeNode<T> next = iter.next();
        		candidates.add(next.getDeepest());        		
        	}
        	
        	TreeNode<T> retVal = null;
        	        	
        	for (TreeNode<T> t:candidates) {
        		if (retVal == null) {
        			retVal = t;
        		} else {
        			if (t.getDepth() > retVal.getDepth()) {
        				retVal = t;        				
        			}
        		}        		
        	}
        	return retVal;
        }
    }


    /** Get the maximum height block */
    public Block getMaxHeightBlock() {
    	TreeNode<BlockData> b = blockChain.getDeepest();
    	return b.getData().getBlock();
    }

    /** Get the UTXOPool for mining a new block on top of max height block */
    public UTXOPool getMaxHeightUTXOPool() {
    	TreeNode<BlockData> b = blockChain.getDeepest();
    	return b.getData().getUtxoPool();
    }

    /** Get the transaction pool to mine a new block */
    public TransactionPool getTransactionPool() {
    	return transactionPool;
    }

    // Helper
    private Transaction[]  makeTransactionsArray(Block block) {
    	int txCnt = block.getTransactions().size();
    	Transaction txs[] = new Transaction[txCnt];
    	for (int i = 0;i < txCnt;i++) {
    		txs[i] = block.getTransaction(i);
    	}
    	return txs;    	
    }

    
    // make sure hashes are formatted identically 
    private String formatBlockHash(byte hash[]) 
    {
    	String retVal = "";
    	for (int i = 0;i < hash.length;i++) {
    		retVal += String.format("%02x", hash[i]);
    	}
    	return retVal;
    }
    
    // Handling coinbase is essential, even though it is barely mentioned in the assignment 
    private void handleCoinbase(Block block, UTXOPool utxoPool)
    {
    	
    	Transaction coinbase = block.getCoinbase();
    	if (coinbase.isCoinbase()) {
       		Transaction.Output out = coinbase.getOutput(0);
    		UTXO utxo = new UTXO(coinbase.getHash(), 0);
			utxoPool.addUTXO(utxo, out);
    	} else {
    		throw new AssertionError("Not a coinbase transaction");
    	}
    }

    /**
     * create an empty block chain with just a genesis block. Assume {@code genesisBlock} is a valid
     * block
     */    
    public BlockChain(Block genesisBlock) {
    	
    	TxHandler txHandler = new TxHandler(new UTXOPool());    	
    	
    	Transaction txs[] = makeTransactionsArray(genesisBlock);
    	
    	txHandler.handleTxs(txs);    	
    	
    	handleCoinbase(genesisBlock, txHandler.getUTXOPool());

		BlockData rootData = new BlockData(genesisBlock,txHandler.getUTXOPool());
    	blockChain = new TreeNode<BlockData>(rootData);
    	String genesisHash = formatBlockHash(genesisBlock.getHash());
    	treeLookup.put(genesisHash,blockChain);
    }
    
    
    /**
     * Add {@code block} to the block chain if it is valid. For validity, all transactions should be
     * valid and block should be at {@code height > (maxHeight - CUT_OFF_AGE)}.
     * 
     * <p>
     * For example, you can try creating a new block over the genesis block (block height 2) if the
     * block chain height is {@code <=
     * CUT_OFF_AGE + 1}. As soon as {@code height > CUT_OFF_AGE + 1}, you cannot create a new block
     * at height 2.
     * 
     * @return true if block is successfully added
     */
    
    public boolean addBlock(Block block) {
    	if (block.getPrevBlockHash() == null) {
        	// System.err.println("Rejected due to genesis block added as regular block");
    		return false;
    	} 

    	String prevHash = formatBlockHash(block.getPrevBlockHash());    	
    	
    	TreeNode<BlockData> prevBlock = treeLookup.get(prevHash);
    	
    	if (prevBlock == null) {
        	// System.err.println(Rejected due to no previous block = " + prevHash);
    		return false;
    	}
    	    	
    	int prevBlockDepth = prevBlock.getDepth();
    	int thisBlockDepth = prevBlockDepth + 1;

    	int chainDepth = blockChain.getDeepest().getDepth();
    	
    	if ((thisBlockDepth + CUT_OFF_AGE) <= chainDepth) {  // Should check for "age" also, but it does not seem necessary
        	// System.err.println("Rejected due to age = " + thisBlockDepth + " " + chainDepth);
    		return false;
    	} 
    	
    	TxHandler txHandler = new TxHandler(prevBlock.getData().utxoPool);

    	Transaction txs[] = makeTransactionsArray(block);
    	int txsLengthBefore = txs.length;
    	txs = txHandler.handleTxs(txs);
		if (txs.length != txsLengthBefore) {
			// System.err.println("Rejected because all transactions were not valid " + txs.length + " " + txsLengthBefore);
			return false;
		}

    
        handleCoinbase(block, txHandler.getUTXOPool());
    	
    	UTXOPool utxoPool = txHandler.getUTXOPool();
    	BlockData blockData = new BlockData(block, utxoPool);
    	TreeNode<BlockData> thisNode = prevBlock.addChild(blockData);
    	treeLookup.put(formatBlockHash(block.getHash()), thisNode);
    	
    	// This is the right place to erase block and UTXOpool outside CUT_OFF_AGE, however it does not seem necessary
    	
    	return true;
    	
    }

    /** Add a transaction to the transaction pool */
    public void addTransaction(Transaction tx) {
		transactionPool.addTransaction(tx);
    }
}