import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import com.sun.nio.sctp.MessageInfo;
import com.sun.nio.sctp.SctpChannel;
import com.sun.nio.sctp.SctpServerChannel;

public class Tester
{
	private InetSocketAddress address; //my address
	private Selector serverSelector;
	private Logger logger;
	private static final int MESSAGE_SIZE = 300;
	private int N;
	private int failures;
	private int failuresEncountered;
	private LinkedBlockingQueue<TesterMessage> testerQueue;
	private int takenCount;
	private boolean consistent;
	private static String configFileName;

	public Tester()
	{

		logger = Logger.getLogger("TesterLog");

		FileHandler fh = null; 
		try
		{
			//fh = new FileHandler("/home/012/r/rx/rxr151330/AOS3_bonus/TesterLog.log");
			fh = new FileHandler(System.getProperty("user.home") + File.separator + "TesterLog.log");
			logger.addHandler(fh);
		}
		catch (SecurityException e1)
		{
			logger.log(Level.INFO, e1.getMessage(), e1);
		}
		catch (IOException e1)
		{
			logger.log(Level.INFO, e1.getMessage(), e1);
		}

		fh.setFormatter(new SimpleFormatter());

		failuresEncountered = 0;

		testerQueue = new LinkedBlockingQueue<TesterMessage>();

		takenCount = 0;

		consistent = true;
	}

	public void parseInput(int port, String domain, int failures, int N)
	{
		//////logger.info("Inside parseInput");

		this.failures = failures;
		this.N = N;

		try
		{
			this.address = new InetSocketAddress(InetAddress.getByName(domain), port);
			//////logger.info("tester address: " + this.address);
		}
		catch (UnknownHostException e)
		{
			e.printStackTrace();
		}

		try
		{
			serverSelector = Selector.open();
		}
		catch (Exception e)
		{
			logger.log(Level.INFO, e.getMessage(), e);
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
				//////logger.info("Read message: " + msg);

				if(msg != null)
				{
					TesterMessage message = new TesterMessage(msg);
					//////logger.info(message.toString());
					testerQueue.add(message);
				}
			}
		}
	}

	public void startServer()
	{
		SctpServerChannel sctpServerChannel = null;
		try
		{
			sctpServerChannel = SctpServerChannel.open();
			sctpServerChannel.configureBlocking(false);
			//Create a socket addess in the current machine at port 5000
			InetSocketAddress serverAddr = address;

			//////logger.info("tester address start: " + this.address);

			//Bind the channel's socket to the server in the current machine at port 5000
			sctpServerChannel.bind(serverAddr);

			sctpServerChannel.register(serverSelector, SelectionKey.OP_ACCEPT);

			//////logger.info("tester Server up!!1");
		}
		catch (Exception e)
		{
			logger.log(Level.INFO, e.getMessage(), e);
		}

		while (failuresEncountered < failures)
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

	public void startCompare()
	{
		Thread testerCompare = new Thread(new TesterCompare());
		testerCompare.start();
		//////logger.info("Started tester compare thread");
	}

	class TesterCompare implements Runnable
	{
		int[][] testMatrix = new int[N][N];
		@Override
		public void run()
		{
			TesterMessage testerMessage = null;
			while(true) //try failure encountered
			{	
				try
				{
					testerMessage = testerQueue.take();
					//////logger.info("Taking message from testerQueue: " + testerMessage);
				}
				catch (InterruptedException e)
				{
					e.printStackTrace();
				}
				
				BufferedWriter bw = null;
				try {
					int f = testerMessage.getFailureNumber() + 1;
					
					FileWriter fw = new FileWriter(new File(Tester.configFileName + "_failure_report_" + f + ".out"), true);
					bw = new BufferedWriter(fw);
					bw.write("Node Id: " + testerMessage.getSourceId());
					bw.write("\n");
					bw.write("Sent vector:" + testerMessage.getStrSent());
					bw.write("\n");
					bw.write("Rcvd vector:" + testerMessage.getStrRcvd());
					bw.write("\n");
					bw.close();
					
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				takenCount++;

				addToMatrix(testerMessage);

				if(takenCount == N)
				{
					//////logger.info("Calling processNXN()");
					//processNXN(testerMessage.getFailureNumber() + 1, testerMessage);
					takenCount = 0;
					testMatrix = new int[N][N];
				}
			}
		}

		public void addToMatrix(TesterMessage testerMessage)
		{
			int sourceId = testerMessage.getSourceId();
			int[] vectorClock = testerMessage.getVectorClock();

			testMatrix[sourceId] = vectorClock;
		}

		public void processNXN(int failureNumber, TesterMessage testerMessage)
		{
			BufferedWriter bw = null;
			try
			{
				FileWriter fw = new FileWriter(new File(Tester.configFileName + "_failure_report_" + failureNumber + ".out"), true);
				bw = new BufferedWriter(fw);
				
				for(int i = 0; i < N; i++)
				{
					bw.write(Arrays.toString(testMatrix[i]));
					bw.write("\n");
				}
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
			
			int maxForJ;
			for(int i = 0; i < N; i++)
			{
				maxForJ = Integer.MIN_VALUE;

				for(int j = 0; j < N; j++)
				{
					if(maxForJ < testMatrix[j][i])
					{
						maxForJ = testMatrix[j][i];
					}
				}

				if(testMatrix[i][i] != maxForJ)
				{
					try
					{
						//bw.write("State: Not Consistent");
						consistent = false;
						bw.close();
						break;
					}
					catch (IOException e)
					{
						e.printStackTrace();
					}
				}
			}

			if(consistent)
			{
				try {
					//bw.write("State: Consistent");
					bw.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}

	public static void main(String[] args)
	{
		int port = Integer.parseInt(args[0]);
		String domain = args[1];
		int N = Integer.parseInt(args[2]);
		String failures = args[3];
		String configFileName = args[4];
		
		Tester.configFileName = configFileName;

		Tester tester = new Tester();
		tester.parseInput(port, domain, Integer.parseInt(failures), N);

		tester.startCompare();

		tester.startServer();
	}
}
