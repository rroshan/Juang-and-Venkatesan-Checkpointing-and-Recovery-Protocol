
public class TreeMessage extends Message
{
	private boolean recoveryStatus;
	
	public TreeMessage(int src, int dest, String type, boolean recoveryStatus)
	{
		super(src, dest, type);
		
		this.recoveryStatus = recoveryStatus;
	}
	
	public TreeMessage(String msg)
	{
		super(msg);
		
		msg = stripMessage(msg);
		String[] parts = msg.trim().split("~");
		
		this.recoveryStatus = Boolean.parseBoolean(parts[3]);
	}
	
	public boolean getRecoveryStatus()
	{
		return recoveryStatus;
	}

	public void setRecoveryStatus(boolean recoveryStatus)
	{
		this.recoveryStatus = recoveryStatus;
	}

	public String toString()
	{
		return super.toString()+"~"+recoveryStatus+Constants.END_TAG;
	}
}
