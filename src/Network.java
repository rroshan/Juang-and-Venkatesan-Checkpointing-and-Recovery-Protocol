import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.sun.nio.sctp.MessageInfo;
import com.sun.nio.sctp.SctpChannel;
import com.sun.nio.sctp.SctpServerChannel;

public class Network implements Subject, Runnable
{
	private ArrayList<Observer> observers;
	private HashMap<Integer, NodeNeighbor> neighborsMap;
	private InetSocketAddress address; //my address
	private int id;
	Selector serverSelector;
	Selector clientSelector;
	private static final int MESSAGE_SIZE = 300;
	private Logger logger;
	private int connectedCount = 0;
	private Object clientConnected = new Object();
	private ReentrantLock networkLock;
	private int N;

	class NodeNeighbor
	{
		public int neighborId;
		public InetSocketAddress neighborAddress;
		public SctpChannel sctpChannel;

		public NodeNeighbor(int neighborId, InetSocketAddress neighborAddress)
		{
			this.neighborId = neighborId;
			this.neighborAddress = neighborAddress;
		}

		public InetSocketAddress getNeighborAddress()
		{
			return neighborAddress;
		}

		public void setSctpChannel(SctpChannel sctpChannel)
		{
			this.sctpChannel = sctpChannel;
		}

		public SctpChannel getSctpChannel()
		{
			return sctpChannel;
		}

		public int getNeighborId()
		{
			return neighborId;
		}
	}

	public Network(int id, String[][] nodeDetails, String[] neighborsArr, Logger logger, int N, String testMc, int testPort)
	{
		this.id = id;
		this.logger = logger;
		this.N = N;

		observers = new ArrayList<Observer>();

		int myPort = Integer.parseInt(nodeDetails[id][2]);
		try {
			this.address = new InetSocketAddress(InetAddress.getByName(nodeDetails[this.id][1]), myPort);
		} catch (UnknownHostException e) {
			logger.log(Level.INFO, e.getMessage(), e);
		}

		neighborsMap = new HashMap<Integer, NodeNeighbor>();

		NodeNeighbor node = null;

		for(int i = 0; i < neighborsArr.length; i++)
		{
			int neighborId = Integer.parseInt(neighborsArr[i]);
			int neighborPort = Integer.parseInt(nodeDetails[neighborId][2]);
			try {
				node = new NodeNeighbor(neighborId, new InetSocketAddress(InetAddress.getByName(nodeDetails[neighborId][1]), neighborPort));
				this.neighborsMap.put(neighborId, node);
			} catch (UnknownHostException e) {
				logger.log(Level.INFO, e.getMessage(), e);
			}
		}


		try
		{
			node = new NodeNeighbor(N, new InetSocketAddress(InetAddress.getByName(testMc), testPort));
		}
		catch (UnknownHostException e1)
		{
			e1.printStackTrace();
		}

		neighborsMap.put(N, node);

		try
		{
			serverSelector = Selector.open();
		}
		catch (Exception e)
		{
			logger.log(Level.INFO, e.getMessage(), e);
		}
	}

	@Override
	public void register(Observer o)
	{
		observers.add(o);
	}

	@Override
	public void unregister(Observer o)
	{
		observers.remove(o);
	}

	@Override
	public void notifyObserver(Message m)
	{
		for(Observer observer : observers)
		{
			observer.update(m);
		}
	}

	public void setNetworkLock(ReentrantLock networkLock)
	{
		this.networkLock = networkLock;
	}

	private boolean processConnect(SelectionKey key)
	{
		SctpChannel channel = (SctpChannel) key.channel();
		while (channel.isConnectionPending())
		{
			try
			{
				channel.finishConnect();
			}
			catch (Exception e)
			{
				logger.log(Level.INFO, e.getMessage(), e);
			}
		}
		return true;
	}

	private void clientProcessReadySet(Set readySet)
	{
		Iterator iterator = readySet.iterator();

		while (iterator.hasNext())
		{
			SelectionKey key = (SelectionKey) iterator.next();
			iterator.remove();
			if (key.isConnectable())
			{
				processConnect(key);
				connectedCount++;
			}
		}
	}

	public void processClientConnection()
	{
		//logger.info("Inside process client connections");
		try
		{
			clientSelector = Selector.open();
		}
		catch (Exception e1)
		{
			logger.log(Level.INFO, e1.getMessage(), e1);
		}

		SctpChannel channel = null;
		int operations = SelectionKey.OP_CONNECT;

		Iterator it = neighborsMap.entrySet().iterator();
		while (it.hasNext())
		{
			Map.Entry pair = (Map.Entry)it.next();

			try
			{
				channel = SctpChannel.open();
				channel.configureBlocking(false);
				channel.bind(null);
				NodeNeighbor neighbor = ((NodeNeighbor) pair.getValue());

				//logger.info("Attempting to connect to " + neighbor.getNeighborId());
				//logger.info("Attempting to connect to " + neighbor.getNeighborAddress());

				channel.connect(neighbor.getNeighborAddress());

				channel.register(clientSelector, operations);

				neighbor.setSctpChannel(channel);
			}
			catch (Exception e)
			{
				//e.printStackTrace();
				logger.log(Level.INFO, e.getMessage(), e);
			}
		}

		while (connectedCount < neighborsMap.size())
		{
			try
			{
				if (clientSelector.select() > 0)
				{
					clientProcessReadySet(clientSelector.selectedKeys());
				}
			}
			catch (Exception e)
			{
				logger.log(Level.INFO, e.getMessage(), e);
			}
		}

		//logger.info("DOne process client connections");
	}

	public void sendToTester(TesterMessage testMessage)
	{
		//logger.info("Inside sendToTester");
		ByteBuffer byteBuffer = null;
		MessageInfo messageInfo = null;
		try
		{
			byteBuffer = ByteBuffer.allocate(MESSAGE_SIZE);
			String message = testMessage.toString();
			//logger.info("Sending msg: " + message.toString());

			messageInfo = MessageInfo.createOutgoing(null,0);
			byteBuffer.clear();

			byteBuffer.put(message.getBytes());				

			byteBuffer.flip();
		}
		catch(Exception e)
		{
			logger.log(Level.INFO, e.getMessage(), e);
		}

		try
		{
			//logger.info("Sending to "+neighborsMap.get(N).getNeighborId());
			neighborsMap.get(N).getSctpChannel().send(byteBuffer,messageInfo);
		}
		catch (Exception e)
		{
			logger.log(Level.INFO, e.getMessage(), e);
		}
	}

	public void sendToNeighbor(int neighborId, Message msg)
	{
		//logger.info("Inside sentToNeighbor : " + neighborId);
		//logger.info("Inside sentToNeighbor message : " + msg);
		ByteBuffer byteBuffer = null;
		MessageInfo messageInfo = null;
		try
		{
			byteBuffer = ByteBuffer.allocate(MESSAGE_SIZE);
			String message = msg.toString();
			//logger.info("Sending msg: " + message.toString());

			messageInfo = MessageInfo.createOutgoing(null,0);
			byteBuffer.clear();

			byteBuffer.put(message.getBytes());				

			byteBuffer.flip();
		}
		catch(Exception e)
		{
			logger.log(Level.INFO, e.getMessage(), e);
		}

		try
		{
			//logger.info("Sending to "+neighborsMap.get(neighborId).getNeighborId());

			networkLock.lock();
			neighborsMap.get(neighborId).getSctpChannel().send(byteBuffer,messageInfo);
			//logger.info("Trying to call notifyObserver");
			notifyObserver(msg);
			networkLock.unlock();
		}
		catch (Exception e)
		{
			logger.log(Level.INFO, e.getMessage(), e);
		}	
	}

	class Server implements Runnable
	{
		public void run()
		{
			synchronized (clientConnected)
			{
				try
				{
					//logger.info("Server going to wait");
					clientConnected.wait();
					//logger.info("Server notified");
				}
				catch (InterruptedException e)
				{
					e.printStackTrace();
				}
			}

			while (true)
			{
				try
				{
					if (serverSelector.select() <= 0)
					{
						continue;
					}

					processReadySet(serverSelector.selectedKeys());
				}
				catch (Exception ioe)
				{
					logger.log(Level.INFO, ioe.getMessage(), ioe);
				}
			}
		}

		public void processReadySet(Set readySet)
		{
			Iterator iterator = readySet.iterator();

			while (iterator.hasNext())
			{
				SelectionKey key = (SelectionKey) iterator.next();
				iterator.remove();

				if (key.isAcceptable())
				{
					SctpServerChannel ssChannel = (SctpServerChannel) key.channel();
					SctpChannel clientChannel;
					try
					{
						clientChannel = (SctpChannel) ssChannel.accept();
						clientChannel.configureBlocking(false);
						clientChannel.register(key.selector(), SelectionKey.OP_READ);
					}
					catch (Exception e)
					{
						logger.log(Level.INFO, e.getMessage(), e);
					}
				}

				if (key.isReadable())
				{
					String msg = processRead(key);
					//logger.info(id+" "+msg);

					if(msg != null)
					{
						Message message = new Message(msg);

						if(message.getType().equals(Constants.REB) || message.getType().equals(Constants.LOST))
						{
							message = new REBMessage(msg);
						}
						else if(message.getType().equals(Constants.RECOVERY) || message.getType().equals(Constants.SENT) 
								|| message.getType().equals(Constants.RECEIVE) || message.getType().equals(Constants.FAILURE_DONE))
						{
							message = new JuangMessage(msg);
						}
						else if(message.getType().equals(Constants.FORWARD))
						{
							message = new TreeMessage(msg);
						}

						networkLock.lock();
						notifyObserver(message);
						networkLock.unlock();
					}
				}
			}
		}

		public String processRead(SelectionKey key)
		{
			SctpChannel sChannel = (SctpChannel) key.channel();
			ByteBuffer buffer = ByteBuffer.allocate(MESSAGE_SIZE);
			MessageInfo msgInfo = null;

			try
			{
				msgInfo = sChannel.receive(buffer, null, null);
			}
			catch (Exception e)
			{
				logger.log(Level.INFO, e.getMessage(), e);
			}

			if(msgInfo != null)
			{
				int bytesCount = buffer.position();
				if (bytesCount > 0)
				{
					buffer.flip();
					return new String(buffer.array());
				}
			}

			return null;
		}
	}

	@Override
	public void run()
	{
		SctpServerChannel sctpServerChannel = null;
		try
		{
			sctpServerChannel = SctpServerChannel.open();
			sctpServerChannel.configureBlocking(false);
			//Create a socket addess in the current machine at port 5000
			InetSocketAddress serverAddr = address;
			//Bind the channel's socket to the server in the current machine at port 5000
			sctpServerChannel.bind(serverAddr);

			sctpServerChannel.register(serverSelector, SelectionKey.OP_ACCEPT);
		}
		catch (Exception e)
		{
			logger.log(Level.INFO, e.getMessage(), e);
		}

		//starting server thread
		Thread serverThread = new Thread(new Server());
		serverThread.start();

		try
		{
			Thread.sleep(5000);
		}
		catch (InterruptedException e)
		{
			logger.log(Level.INFO, e.getMessage(), e);
		}

		processClientConnection();

		synchronized (clientConnected)
		{
			clientConnected.notify();
		}
		
		notifyObserver(null);
	}
}
