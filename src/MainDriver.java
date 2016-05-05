import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class MainDriver 
{
	public static void main(String[] args)
	{
		LinkedList<FailurePoint> failureEvents = new LinkedList<FailurePoint>();
		
		Logger logger = Logger.getLogger("MyLog");
		
		int id = Integer.parseInt(args[0]);
		
		FileHandler fh = null; 
		try
		{
			//fh = new FileHandler("/home/012/r/rx/rxr151330/AOS3_testing/MyLogFile_"+id+".log");
			fh = new FileHandler(System.getProperty("user.home") + File.separator + "MyLogFile_"+id+".log");
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
		
		File treedataFile = new File(args[11]+"_treedata_"+id+".txt");
		BufferedReader br = null;
		String text;
		int parent = -1;
		int[] children = null;
		
		try
		{
			FileReader fr = new FileReader(treedataFile);
			br = new BufferedReader(fr);
			text = br.readLine();
			
			if(text.equals("*"))
			{
				parent = -1;
			}
			else
			{
				parent = Integer.parseInt(text);
			}
			
			text = br.readLine();
			
			if(!text.equals("*"))
			{
				String[] childrenStrArr = text.split(" ");
				children = new int[childrenStrArr.length];
				
				for(int i = 0; i < childrenStrArr.length; i++)
				{
					children[i] = Integer.parseInt(childrenStrArr[i]);
				}
			}
			
			text = br.readLine();
			
			Globals.D = Integer.parseInt(text);
		}
		catch (FileNotFoundException e)
		{
			e.printStackTrace();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		} finally {
			try {
				br.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		//no of nodes
		int N = Integer.parseInt(args[1]);
		
		//no of failures
		int failures = Integer.parseInt(args[2]);
		
		//maxNumber
		int maxNumber = Integer.parseInt(args[3]);
		
		//maxPerActive
		int maxPerActive = Integer.parseInt(args[4]);
		
		//interRequestDelay
		int minSendDelay = Integer.parseInt(args[5]);
		
		//nodeLocations
		String nodeLocations = args[6];
		String[] locationsArr = nodeLocations.split("#");
		String[][] nodeDetails = new String[locationsArr.length][3];

		for(int i = 0; i < locationsArr.length; i++)
		{
			nodeDetails[i] = locationsArr[i].split(" ");
		}
		
		//logger.info(args[7]);

		
		String neighbors = args[7];
		String[] neighborsArr = neighbors.split(" ");
		
		
		String[] failingSequenceArr = args[8].split("#");
		String[] fail_points;
		FailurePoint failurePoint;
		for(int i = 0; i < failingSequenceArr.length; i++)
		{
			fail_points = failingSequenceArr[i].split(" ");
			
			failurePoint = new FailurePoint(Integer.parseInt(fail_points[0]), Integer.parseInt(fail_points[1]));
			failureEvents.add(failurePoint);
		}
		
		//logger.info("Failure events:"+failureEvents.toString());
		
		ReentrantLock networkLock = new ReentrantLock(true);
		
		String testMc = args[9];
		String testPort = args[10];
		
		
		Network net = new Network(id, nodeDetails, neighborsArr, logger, N, testMc, Integer.parseInt(testPort));
		net.setNetworkLock(networkLock);
		
		TimestampService timestampService = new TimestampService(N, id);
		
		Juang juang = new Juang(logger, failureEvents, id, net, N, failures, parent, children);
		juang.setNeighborIds(neighborsArr);
		juang.setTimestampService(timestampService);
		
		//logger.info("Juang registered with network");
		net.register(juang);
		
		Thread juangThread = new Thread(juang);
		juangThread.start();
		
		
		REB reb = new REB(id, maxNumber, maxPerActive, net, neighborsArr, neighborsArr.length, minSendDelay, logger);
		//logger.info("REB registered with network");
		net.register(reb);
		reb.setTimestampService(timestampService);
		
		Thread network = new Thread(net);
		network.start();
		
		Thread rebThread = new Thread(reb);
		rebThread.start();
	}
}
