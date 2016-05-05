
public class Message
{
	private int sourceId;
	private int destId;
	private String type;
	
	public Message(int src, int dest, String type)
	{
		this.sourceId = src;
		this.destId = dest;
		this.type = type;
	}
	
	public Message(String msg)
	{
		msg = stripMessage(msg);
		String[] parts = msg.trim().split("~");
		this.sourceId = Integer.parseInt(parts[0].trim());
		this.destId = Integer.parseInt(parts[1].trim());
		this.type = parts[2].trim();
	}
	
	public String stripMessage(String msg)
	{
		int index = msg.indexOf(Constants.END_TAG);
		return msg.substring(0, index);
	}
	
	public int getSourceId()
	{
		return sourceId;
	}

	public void setSourceId(int sourceId)
	{
		this.sourceId = sourceId;
	}

	public int getDestId()
	{
		return destId;
	}

	public void setDestId(int destId)
	{
		this.destId = destId;
	}
	
	public String getType()
	{
		return type;
	}

	public void setType(String type)
	{
		this.type = type;
	}

	public String getFromMessage(int part)
	{
		String str = this.toString();
		str = stripMessage(str);
		String[] parts = str.split("~");
		return parts[part].trim();
	}
	
	public String toString()
	{
		return sourceId+"~"+destId+"~"+type;
	}
}
