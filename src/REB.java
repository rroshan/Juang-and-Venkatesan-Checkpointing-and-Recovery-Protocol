import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

public class REB implements Runnable, Observer
{
	private int maxNumber;
	private int maxPerActive;
	//private volatile boolean isActive;
	private Network network;
	private int neighborSize;
	private ArrayList<Integer> neighborsArr; 
	private int sendMsgCount;
	private Logger logger;
	private int minSendDelay;
	private int id;
	//private boolean inRecovery;
	private ReentrantLock rebReentrantLock;
	private LinkedBlockingQueue<REBMessage> rebQueue;
	private TimestampService timestampService;

	public REB(int id, int maxNumber, int maxPerActive, Network network, String[] neighborsArr, int neighborSize, int minSendDelay, Logger logger)
	{
		this.id = id;
		this.maxNumber = maxNumber;
		this.maxPerActive = maxPerActive;
		this.network = network;
		this.neighborSize = neighborSize;
		this.logger = logger;
		sendMsgCount = 0;
		this.minSendDelay = minSendDelay;

		this.neighborsArr = new ArrayList<Integer>();

		for(int i = 0; i < neighborsArr.length; i++)
		{
			this.neighborsArr.add(Integer.parseInt(neighborsArr[i]));
		}

		rebReentrantLock = new ReentrantLock(true);
		rebQueue = new LinkedBlockingQueue<REBMessage>();
	}

	public void send()
	{
		int temp;

		//call networking layer with REB as parameter to differentiate from Juang Msg
		Random random = new Random();
		temp = (maxPerActive > neighborSize) ? neighborSize : maxPerActive;

		if(temp > 1)
		{
			temp = random.nextInt(temp - 1) + 1;
		}

		//logger.info("Choosing to send to : "+ temp);

		Collections.shuffle(neighborsArr);

		Message msg = null;

		for(int i = 0; i < temp && Globals.rebActive; i++)
		{
			msg = new REBMessage(id, neighborsArr.get(i), Constants.REB, timestampService.getCurrTimestamp(), false);

			network.sendToNeighbor(neighborsArr.get(i), msg);
			sendMsgCount++;

			try
			{
				Thread.sleep(minSendDelay);
			}
			catch (InterruptedException e)
			{
				e.printStackTrace();
			}
		}

		//logger.info("Setting isActive to false: " + msg);
		Globals.rebActive = false;
	}

	//reb thread
	@Override
	public void run()
	{

		Thread rebMessageProcessor = new Thread(new REBMessageProcessor());
		rebMessageProcessor.start();


		while(sendMsgCount < maxNumber)
		{
			if(Globals.rebActive)
			{
				rebReentrantLock.lock();
				send();
				//logger.info("Send message count run: " + sendMsgCount);
				rebReentrantLock.unlock();
			}
			else
			{
				synchronized (Globals.rebLock)
				{
					try
					{
						Globals.rebLock.wait();
					}
					catch (InterruptedException e)
					{
						e.printStackTrace();
					}
				}
			}
		}
	}

	class REBMessageProcessor implements Runnable
	{
		@Override
		public void run()
		{
			REBMessage rebMessage = null;

			while(sendMsgCount < maxNumber)
			{
				try
				{
					rebMessage = rebQueue.take();
				}
				catch (InterruptedException e)
				{
					e.printStackTrace();
				}

				if(!rebMessage.isREBAllowed())
				{
					synchronized (Globals.allowRebLock)
					{
						try
						{
							Globals.allowRebLock.wait();
							if(rebMessage.isREBAllowed())
							{
								//logger.info("REB allow notified");
							}
						}
						catch (InterruptedException e)
						{
							e.printStackTrace();
						}
					}
				}

				rebReentrantLock.lock();
				if((rebMessage.getType().equals(Constants.REB) && rebMessage.getDestId() == id) && !Globals.juangInRecovery)
				{
					//logger.info("incoming message of REB type: " + rebMessage);
					if(sendMsgCount < maxNumber)
					{
						if(!Globals.rebActive)
						{
							Globals.rebActive = true;

							synchronized (Globals.rebLock)
							{
								Globals.rebLock.notify();
							}
						}
					}
				}
				rebReentrantLock.unlock();
			}
		}

	}

	public void setTimestampService(TimestampService timestampService)
	{
		this.timestampService = timestampService;
	}

	@Override
	public void update(Message message)
	{
		REBMessage rebMessage = null;
		if(message != null)
		{
			if(message.getType().equals(Constants.REB) && message.getDestId() == id)
			{
				rebMessage = (REBMessage) message;
				rebQueue.add(rebMessage);
			}
		}
		else
		{
			//logger.info("First time");
			if(sendMsgCount < maxNumber)
			{
				Globals.rebActive = true;

				synchronized (Globals.rebLock)
				{
					Globals.rebLock.notify();
				}
			}
		}
	}
}
