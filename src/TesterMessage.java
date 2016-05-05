import java.util.Arrays;
import java.util.HashMap;

public class TesterMessage extends Message 
{
	private int failureNumber;
	private boolean activeState;
	private int[] vectorClock;
	private HashMap<Integer, Integer> sent;
	private HashMap<Integer, Integer> rcvd;
	private String strSent;
	private String strRcvd;
	
	public TesterMessage(int src, int dest, String type, int failureNumber, boolean activeState, int[] vectorClock, HashMap<Integer, Integer> sent, HashMap<Integer, Integer> rcvd)
	{
		super(src, dest, type);
		this.failureNumber = failureNumber;
		this.activeState = activeState;
		this.vectorClock = vectorClock.clone();
		this.sent = (HashMap<Integer, Integer>) sent.clone();
		this.rcvd = (HashMap<Integer, Integer>) rcvd.clone();
	}
	
	public TesterMessage(String msg)
	{
		super(msg);
		
		msg = stripMessage(msg);
		String[] parts = msg.trim().split("~");
		this.failureNumber = Integer.parseInt(parts[3].trim());
		this.activeState = Boolean.parseBoolean(parts[4].trim());
		
		String[] items = parts[5].trim().replaceAll("\\[", "").replaceAll("\\]", "").split(",");
		vectorClock = new int[items.length];
		
		for(int i = 0; i < items.length; i++)
		{
			vectorClock[i] = Integer.parseInt(items[i].trim());
		}
		
		strSent = parts[6];
		strRcvd = parts[7];
	}

	public int getFailureNumber()
	{
		return failureNumber;
	}

	public void setFailureNumber(int failureNumber)
	{
		this.failureNumber = failureNumber;
	}

	public boolean isActiveState()
	{
		return activeState;
	}

	public void setActiveState(boolean activeState)
	{
		this.activeState = activeState;
	}
	
	public int[] getVectorClock()
	{
		return vectorClock;
	}

	public void setVectorClock(int[] vectorClock)
	{
		this.vectorClock = vectorClock.clone();
	}
	

	public String getStrSent() {
		return strSent;
	}

	public void setStrSent(String strSent) {
		this.strSent = strSent;
	}

	public String getStrRcvd() {
		return strRcvd;
	}

	public void setStrRcvd(String strRcvd) {
		this.strRcvd = strRcvd;
	}

	public String toString()
	{
		return super.toString()+"~"+failureNumber+"~"+activeState+"~"+Arrays.toString(vectorClock)+"~"+sent.toString()+"~"+rcvd.toString()+Constants.END_TAG;
	}
}
