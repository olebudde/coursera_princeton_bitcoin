import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/* CompliantNode refers to a node that follows the rules (not malicious)*/
public class CompliantNode implements Node {
	double p_graph;
	double p_malicious;
	double p_txDistribution;
	int numRounds;
	int roundsLeft;
	boolean followees[];
	
	
	Set<Transaction> myValidSet;
	
    public CompliantNode(double p_graph, double p_malicious, double p_txDistribution, int numRounds) {
    	this.p_graph = p_graph;
    	this.p_malicious = p_malicious;
    	this.p_txDistribution = p_txDistribution;
    	this.numRounds = numRounds;
    	this.roundsLeft = numRounds;
    	    	
    	this.myValidSet = new HashSet<Transaction>();    	    	    	
    	
    }

    public void setFollowees(boolean[] followees) {
    	this.followees  = followees;
    }

    public void setPendingTransaction(Set<Transaction> pendingTransactions) {
    	for (Transaction tx:pendingTransactions) {
    		myValidSet.add(tx);
    	}
    }

    public Set<Transaction> sendToFollowers() {
    	// Result is returned here.
    	roundsLeft--;
    	if (roundsLeft < 0) {
    		return myValidSet;
    	}
    	// Valid transactions
    	return myValidSet;
    	// return new HashSet<Transaction>();
        // IMPLEMENT THIS
    }

    public void receiveFromFollowees(Set<Candidate> candidates) {
    	for (Candidate c:candidates) {
    		if (!myValidSet.contains(c.tx)) {
    			myValidSet.add(c.tx);
    		}    		
    	}
    }
}
