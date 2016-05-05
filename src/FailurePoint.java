
public class FailurePoint
{
	private int nodeId;
	private int noOfCheckpoints;
	
	public FailurePoint(int nodeId, int noOfCheckpoints)
	{
		this.nodeId = nodeId;
		this.noOfCheckpoints = noOfCheckpoints;
	}
	
	public int getNodeId()
	{
		return nodeId;
	}
	
	public int getNoOfCheckpoints()
	{
		return noOfCheckpoints;
	}	
	
	public String toString()
	{
		return "{"+nodeId+","+noOfCheckpoints+"}";
	}
}
