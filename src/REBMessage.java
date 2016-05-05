import java.util.Arrays;

public class REBMessage extends Message
{
	private int[] vectorClock;
	private boolean rebAllowed;
	
	public REBMessage(int src, int dest, String type, int[] vectorClock, boolean rebAllowed)
	{
		super(src, dest, type);
		
		this.vectorClock = vectorClock.clone();
	}
	
	public REBMessage(String msg)
	{
		super(msg);
		
		msg = stripMessage(msg);
		String[] parts = msg.trim().split("~");
		
		String[] items = parts[3].trim().replaceAll("\\[", "").replaceAll("\\]", "").split(",");
		vectorClock = new int[items.length];
		
		for(int i = 0; i < items.length; i++)
		{
			vectorClock[i] = Integer.parseInt(items[i].trim());
		}
		
		rebAllowed = false;
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
		return super.toString()+"~"+Arrays.toString(vectorClock)+Constants.END_TAG;
	}
	
	public void setREBAllowed()
	{
		this.rebAllowed = true;
	}
	
	public boolean isREBAllowed()
	{
		return rebAllowed;
	}
}
