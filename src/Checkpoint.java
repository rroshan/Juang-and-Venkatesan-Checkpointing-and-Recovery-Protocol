import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

public class Checkpoint
{
	private int checkpointNum;
	private HashMap<Integer, Integer> sent;
	private HashMap<Integer, Integer> rcvd;
	private boolean rebActive;
	private int[] vectorClock;
	
	public Checkpoint(Set<Integer> neighborIds, int[] vectorClock)
	{
		sent = new HashMap<Integer, Integer>();
		rcvd = new HashMap<Integer, Integer>();
		
		Iterator<Integer> it = neighborIds.iterator();
		while(it.hasNext())
		{
			int neighborId = it.next();
			sent.put(neighborId, 0);
			rcvd.put(neighborId, 0);
		}
		
		checkpointNum = 0;
		
		rebActive = true;
		
		this.vectorClock = vectorClock;
	}
	
	public Checkpoint(HashMap<Integer, Integer> sent, HashMap<Integer, Integer> rcvd, int checkpointNum, boolean rebActive, int[] vectorClock)
	{
		this.sent = (HashMap<Integer, Integer>) sent.clone();
		this.rcvd = (HashMap<Integer, Integer>) rcvd.clone();
		this.checkpointNum = ++checkpointNum;
		this.rebActive = rebActive;
		
		this.vectorClock = vectorClock;
	}
	
	public void incrementSentVector(int index)
	{
		Integer val = sent.get(index);
		val++;
		sent.put(index, val);
	}
	
	public void incrementRcvdVector(int index)
	{
		Integer val = rcvd.get(index);
		val++;
		rcvd.put(index, val);
	}
	
	public int getJuangDifference(int incomingNodeId, int sentValue)
	{
		int rcvdValue = rcvd.get(incomingNodeId);
		return sentValue - rcvdValue;
	}
	
	public HashMap<Integer, Integer> getSent()
	{
		return sent;
	}
	
	public HashMap<Integer, Integer> getRcvd()
	{
		return rcvd;
	}
	
	public int getCheckpointNum()
	{
		return checkpointNum;
	}
	
	public boolean getActiveState()
	{
		return rebActive;
	}
	
	public int[] getVectorClock()
	{
		return vectorClock;
	}

	public void setVectorClock(int[] vectorClock)
	{
		this.vectorClock = vectorClock.clone();
	}

	public String toString()
	{
		StringBuilder strb = new StringBuilder();
		strb.append("Checkpoint Number: "+checkpointNum);
		strb.append("\n");
		strb.append("Sent Vector");
		strb.append("\n");
		strb.append(sent.toString());
		strb.append("\n");
		strb.append("Rcvd Vector");
		strb.append("\n");
		strb.append(rcvd.toString());
		strb.append("\n");
		strb.append("Active Status: ");
		if(rebActive)
		{
			strb.append("true");
		}
		else
		{
			strb.append("false");	
		}
		strb.append("\n");
		strb.append("Vector Clock");
		strb.append("\n");
		strb.append(Arrays.toString(vectorClock));
		
		return strb.toString();
	}
}
