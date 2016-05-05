
public class TimestampService
{
	private int[] currTimestamp;
	private int[] testerTimestamp;
	private int N;
	private int nodeId;

	public TimestampService(int N, int nodeId)
	{
		this.N = N;
		this.currTimestamp = new int[N];
		this.nodeId = nodeId;
	}

	public void updateVectorClock(Message message)
	{
		if(message != null)
		{
			if(message.getType().equals(Constants.REB) || message.getType().equals(Constants.LOST))
			{
				REBMessage rebMessage = (REBMessage) message;

				if(message.getType().equals(Constants.REB))
				{
					if(rebMessage.getSourceId() == nodeId)
					{
						currTimestamp[nodeId]++;
					}
					else if(rebMessage.getDestId() == nodeId)
					{
						int[] vectorClock = rebMessage.getVectorClock();

						for(int i = 0; i < N; i++)
						{
							if(i != nodeId)
							{	
								if(i == rebMessage.getSourceId())
								{
									if(vectorClock[i] >= currTimestamp[i])
									{
										currTimestamp[i] = vectorClock[i] + 1;
									}
								}
								else
								{
									currTimestamp[i] = Math.max(vectorClock[i], currTimestamp[i]);
								}
							}
						}

						//currTimestamp[nodeId]++;
					}
				}
				else if(message.getType().equals(Constants.LOST))
				{
					if(rebMessage.getDestId() == nodeId)
					{
						int[] vectorClock = rebMessage.getVectorClock();

						for(int i = 0; i < N; i++)
						{
							if(i != nodeId)
							{
								//currTimestamp[i] = Math.max(vectorClock[i], currTimestamp[i]);
								if(i == rebMessage.getSourceId())
								{
									currTimestamp[i] = Math.max(vectorClock[i], currTimestamp[i]);
								}
							}
						}
					}
				}
			}
		}
	}

	public int[] getCurrTimestamp()
	{
		return currTimestamp;
	}

	public int[] getTesterTimestamp()
	{
		return testerTimestamp;
	}

	public void setTesterTimestamp(int[] testerTimestamp)
	{
		this.testerTimestamp = testerTimestamp.clone();
	}

	public void setCurrTimestamp(int[] currTimestamp)
	{
		this.currTimestamp = currTimestamp.clone();
	}
}
