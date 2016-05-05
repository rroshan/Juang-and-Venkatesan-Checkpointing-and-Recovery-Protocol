
public class JuangMessage extends Message
{
	private int val;
	private int roundNo;
	private int failureNumber; 
	
	public JuangMessage(int src, int dest, String type, int val, int roundNo, int failureNumber)
	{
		super(src, dest, type);
		this.val = val;
		this.roundNo = roundNo;
		this.failureNumber = failureNumber;
	}
	
	public JuangMessage(String msg)
	{
		super(msg);
		
		msg = stripMessage(msg);
		String[] parts = msg.trim().split("~");
		this.val = Integer.parseInt(parts[3].trim());
		this.roundNo = Integer.parseInt(parts[4].trim());
		this.failureNumber = Integer.parseInt(parts[5].trim());
	}

	public int getVal()
	{
		return val;
	}

	public void setVal(int val)
	{
		this.val = val;
	}

	public int getRoundNo()
	{
		return roundNo;
	}

	public void setRoundNo(int roundNo)
	{
		this.roundNo = roundNo;
	}
	
	public String toString()
	{
		return super.toString()+"~"+val+"~"+roundNo+"~"+failureNumber+Constants.END_TAG;
	}
	
	public int getFailureNumber()
	{
		return failureNumber;
	}
}
