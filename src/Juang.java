import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.logging.Logger;

public class Juang implements Runnable, Observer
{
	private int checkpointCount;
	private ArrayList<Checkpoint> checkpointArr;
	private Set<Integer> neighborids;
	private Checkpoint currCheckpoint;
	LinkedList<FailurePoint> failureEvents;
	private Logger logger;
	private int nodeId;
	private int noOfCheckPointsAfterRecovery;
	private Network network;
	private int[] recoverySentCount;
	private int[] recoveryRcvdCount;
	private int roundNo;
	private int failuresEncountered;
	private int N;
	private int failures;
	private LinkedBlockingQueue<Message> messageQueue;
	private PriorityBlockingQueue<JuangMessage> sentMsgQueue;
	private int[] sentSentCount;
	private int[] sentRcvdCount;

	private int receiveSentCount;
	private int receiveRcvdCount;

	private int failureDoneSentCount;
	private int failureDoneRcvdCount;

	private int maxFailureSeen;

	private LinkedBlockingQueue<JuangMessage> receiveQueue;

	private TimestampService timestampService;

	private boolean isRoot;
	private boolean isLeaf;
	private int parent;
	private int[] children;

	private boolean recoveredForMthRound;
	private boolean mthRoundDone;
	private int forwardRcvdCount;

	public Juang(Logger logger, LinkedList<FailurePoint> failureEvents, int nodeId, Network network, int N, int failures, int parent, int[] children)
	{
		checkpointCount = 0;
		checkpointArr = new ArrayList<Checkpoint>();
		this.logger = logger;
		this.failureEvents = failureEvents;
		this.nodeId = nodeId;
		noOfCheckPointsAfterRecovery = 0;
		this.network = network;
		recoverySentCount = new int[failures + 1];
		recoveryRcvdCount = new int[failures + 1];
		roundNo = 0;
		this.N = N;
		failuresEncountered = 0;
		this.failures = failures;
		sentSentCount = new int[N];
		sentRcvdCount = new int[N];

		receiveSentCount = 0;
		receiveRcvdCount = 0;

		failureDoneSentCount = 0;
		failureDoneRcvdCount = 0;

		maxFailureSeen = 0;

		forwardRcvdCount = 0;

		mthRoundDone = false;

		recoveredForMthRound = false;

		sentMsgQueue = new PriorityBlockingQueue<JuangMessage>(11, new Comparator<JuangMessage>() {
			@Override
			public int compare(JuangMessage msg1, JuangMessage msg2)
			{
				if(msg1.getRoundNo() == msg2.getRoundNo())
				{
					return msg1.getSourceId() - msg2.getSourceId();
				}

				return msg1.getRoundNo() - msg2.getRoundNo();
			}
		});

		messageQueue = new LinkedBlockingQueue<Message>();
		receiveQueue = new LinkedBlockingQueue<JuangMessage>();

		this.isRoot = false;
		this.isLeaf = false;

		this.parent = parent;

		if(parent == -1)
		{
			this.isRoot = true;
		}

		//logger.info("Parent: " + parent);

		this.children = children;

		if(children == null)
		{
			this.isLeaf = true;
		}
		else
		{
			//logger.info("Children: " + Arrays.toString(children));
		}
	}

	public void setNeighborIds(String[] neighborIds)
	{
		this.neighborids = new HashSet<Integer>();

		for(int i = 0; i < neighborIds.length; i++)
		{
			this.neighborids.add(Integer.parseInt(neighborIds[i]));
		}

		//logger.info("Neighbors hashset: "+ this.neighborids.toString());

		Globals.neighborCount = neighborids.size();
	}

	public void update(Message message)
	{
		if(message != null)
		{
			messageQueue.add(message);
		}
	}

	class MessageProcessor implements Runnable
	{
		public MessageProcessor()
		{
			//logger.info("Taking first checkpoint number: "+checkpointCount);
			currCheckpoint = new Checkpoint(neighborids, timestampService.getCurrTimestamp().clone());
			
			checkpointArr.add(currCheckpoint);
			checkpointCount++;
			
			//logger.info("First checkpoint status : " + currCheckpoint);
		}

		public void floodRecovery()
		{
			Message recoveryMsg;
			//call network's send to all
			for(Integer i : neighborids)
			{
				recoveryMsg = new JuangMessage(nodeId, i, Constants.RECOVERY, Constants.BOTTOM, Constants.BOTTOM, maxFailureSeen);
				network.sendToNeighbor(i, recoveryMsg);
			}
		}

		public void floodSent()
		{
			roundNo++;

			if(roundNo < N)
			{
				Message sentMsg;
				//call network's send to all
				for(Integer i : neighborids)
				{
					sentMsg = new JuangMessage(nodeId, i, Constants.SENT, currCheckpoint.getSent().get(i), roundNo, Constants.BOTTOM);
					network.sendToNeighbor(i, sentMsg);
				}
			}
			else
			{
				TesterMessage tstSentMsg = new TesterMessage(nodeId, Constants.BOTTOM, Constants.SENT, failuresEncountered, currCheckpoint.getActiveState(), timestampService.getCurrTimestamp(), currCheckpoint.getSent(), currCheckpoint.getRcvd());

				//logger.info("Tester CP: " + currCheckpoint.toString());
				//logger.info("Tester Message: " + tstSentMsg.toString());

				network.sendToTester(tstSentMsg);
				
				int recVal;
				//logger.info("Round Number is N");
				//logger.info("Sending receive count to neighbors");
				Message recVectorMsg;

				for(Integer neighborId : neighborids)
				{
					recVal = currCheckpoint.getRcvd().get(neighborId);

					recVectorMsg = new JuangMessage(nodeId, neighborId, Constants.RECEIVE, recVal, Constants.BOTTOM, Constants.BOTTOM);

					network.sendToNeighbor(neighborId, recVectorMsg);
				}
			}
		}

		public void floodFailureDone()
		{
			Message failureDoneMsg;
			//call network's send to all
			for(Integer i : neighborids)
			{
				failureDoneMsg = new JuangMessage(nodeId, i, Constants.FAILURE_DONE, Constants.BOTTOM, Constants.BOTTOM, Constants.BOTTOM);
				network.sendToNeighbor(i, failureDoneMsg);
			}
		}

		public void recover()
		{
			JuangMessage msg = null;
			while(!sentMsgQueue.isEmpty() && sentMsgQueue.peek().getRoundNo() == roundNo)
			{
				try
				{
					msg = (JuangMessage) sentMsgQueue.take();
				}
				catch (InterruptedException e)
				{
					e.printStackTrace();
				}

				//logger.info("Message for recovery: "+msg);

				int sentVal = msg.getVal();

				for(int i = checkpointArr.size() - 1; i >= 0; i--)
				{
					//logger.info("Received vector count for "+ msg.getSourceId() + " is "+ checkpointArr.get(i).getRcvd().get(msg.getSourceId()));
					//logger.info("Sent val count: "+ sentVal);

					if(checkpointArr.get(i).getRcvd().get(msg.getSourceId()) <= sentVal)
					{
						currCheckpoint = checkpointArr.get(i);
						checkpointCount = checkpointArr.size() - 1;
						break;
					}
					else
					{
						checkpointArr.remove(i);
						if(roundNo % Globals.D == 0 || roundNo == N - 1)
						{
							recoveredForMthRound = recoveredForMthRound | true;
						}
					}
				}
				
				timestampService.setCurrTimestamp(currCheckpoint.getVectorClock());
				timestampService.setTesterTimestamp(timestampService.getCurrTimestamp());

				//logger.info("tester time stamp set to this value: " + Arrays.toString(timestampService.getTesterTimestamp()));

				//logger.info("checkpoint arr size: "+ checkpointArr.size());
				//logger.info("Recoverd for checkpoint: "+ currCheckpoint.getCheckpointNum());
				//logger.info("Recoverd checkpoint: " + currCheckpoint);
			}
		}

		private void fail()
		{
			Random random = new Random();
			int rollbackTo = random.nextInt(checkpointArr.size());

			//logger.info("Rolling back to checkpoint :"+rollbackTo);

			Globals.juangInRecovery = true;
			Globals.rebActive = false;

			currCheckpoint = checkpointArr.get(rollbackTo);

			//logger.info("Rolled back to checkpoint state is : " + currCheckpoint.toString());

			for(int i = checkpointArr.size() - 1; i > rollbackTo; i--)
			{
				checkpointArr.remove(i);
			}
			
			timestampService.setCurrTimestamp(currCheckpoint.getVectorClock());

			maxFailureSeen++;

			floodRecovery();
		}

		private boolean isTimeToFail()
		{
			if(!failureEvents.isEmpty())
			{
				if(failureEvents.peek().getNoOfCheckpoints() == noOfCheckPointsAfterRecovery && failureEvents.peek().getNodeId() == nodeId)
				{
					noOfCheckPointsAfterRecovery = 0;
					return true;
				}
			}

			return false;
		}

		public synchronized void takeCheckpoint(REBMessage rebMessage)
		{	
			/*if(checkpointCount == 0)
			{
				//logger.info("wrong first checkpoint number: "+checkpointCount);
				currCheckpoint = new Checkpoint(neighborids, timestampService.getCurrTimestamp());
			}
			else
			{*/
				timestampService.updateVectorClock(rebMessage);
				if(failureDoneSentCount == neighborids.size() && Globals.juangInRecovery && !rebMessage.getType().equals(Constants.LOST))
				{
					currCheckpoint = new Checkpoint(currCheckpoint.getSent(), currCheckpoint.getRcvd(), currCheckpoint.getCheckpointNum(), true, timestampService.getCurrTimestamp().clone());
				}
				else
				{
					currCheckpoint = new Checkpoint(currCheckpoint.getSent(), currCheckpoint.getRcvd(), currCheckpoint.getCheckpointNum(), currCheckpoint.getActiveState(), timestampService.getCurrTimestamp().clone());
				}

				//currCheckpoint.setVectorClock(timestampService.getCurrTimestamp());
			//}

			checkpointArr.add(currCheckpoint);

			if(rebMessage.getSourceId() == nodeId)
			{
				//logger.info("Comm event is of OUTGOING type");
				currCheckpoint.incrementSentVector(rebMessage.getDestId());
			}
			else if(rebMessage.getDestId() == nodeId)
			{
				//logger.info("Comm event is of INCOMING | LOST type");
				currCheckpoint.incrementRcvdVector(rebMessage.getSourceId());
			}

			//logger.info("Checkpoint state: "+ currCheckpoint.toString());

			checkpointCount++;

			if(!Globals.juangInRecovery)
			{
				noOfCheckPointsAfterRecovery++;

				if(isTimeToFail())
				{
					//logger.info("Time to fail");
					fail();
				}
			}
		}

		public boolean isRecoveryFloodDone(int msgFailureNumber)
		{
			//logger.info("Inside isRecoveryFloodDone: round: " + msgFailureNumber + " recoverySentCount: " + recoverySentCount[msgFailureNumber] + " recoveryRcvdCount: " + recoveryRcvdCount[msgFailureNumber]);
			if((recoverySentCount[msgFailureNumber] == neighborids.size()) && (recoveryRcvdCount[msgFailureNumber] == neighborids.size()))
			{
				//logger.info("Counts matched");
				return true;
			}

			return false;
		}

		public boolean isSentFloodDone(int msgRoundNo)
		{
			//logger.info("Inside isSentFloodDone: round: " + msgRoundNo + " sentSentCount: " + sentSentCount[msgRoundNo] + " sentRcvdCount: " + sentRcvdCount[msgRoundNo]);
			if((sentSentCount[msgRoundNo] == neighborids.size()) && (sentRcvdCount[msgRoundNo] == neighborids.size()))
			{
				//logger.info("Sent Counts matched");
				return true;
			}

			return false;
		}

		public boolean isReceiveDone()
		{
			if((receiveSentCount == neighborids.size()) && (receiveRcvdCount == neighborids.size()))
			{
				//logger.info("Receive Counts matched");
				return true;
			}

			return false;
		}

		public boolean isFailureDoneSent()
		{
			if((failureDoneSentCount == neighborids.size()) && (failureDoneRcvdCount == neighborids.size()))
			{
				//logger.info("Failure Done Counts matched");
				return true;
			}

			return false;
		}

		public void clearVariables()
		{
			noOfCheckPointsAfterRecovery = 0;

			failuresEncountered++;
			failureEvents.removeFirst();

			roundNo = 0;

			sentSentCount = new int[N];
			sentRcvdCount = new int[N];

			failureDoneSentCount = 0;
			failureDoneRcvdCount = 0;

			receiveRcvdCount = 0;
			receiveSentCount = 0;

			recoveredForMthRound = false;
			mthRoundDone = false;
			forwardRcvdCount = 0;

			//logger.info("failuresEncountered: " + failuresEncountered);
			//logger.info("failures: " + failures);

			if((failuresEncountered + 1) <= failures)
			{
				if(recoveryRcvdCount[failuresEncountered + 1] > 0)
				{
					floodRecovery();
				}
				else
				{
					Globals.juangInRecovery = false;
					Globals.rebActive = currCheckpoint.getActiveState();

					if(Globals.rebActive)
					{
						synchronized (Globals.rebLock)
						{
							Globals.rebLock.notify();
						}
					}
				}
			}
			else
			{
				Globals.juangInRecovery = false;
				Globals.rebActive = currCheckpoint.getActiveState();

				if(Globals.rebActive)
				{
					synchronized (Globals.rebLock)
					{
						Globals.rebLock.notify();
					}
				}
			}
		}

		private void transmitLostMessages()
		{
			int diff, source;
			int recVal;
			REBMessage lostMessage = null;
			JuangMessage receiveMessage = null;

			while(!receiveQueue.isEmpty())
			{
				try
				{
					receiveMessage = (JuangMessage) receiveQueue.take();
				}
				catch (InterruptedException e)
				{
					e.printStackTrace();
				}

				source = receiveMessage.getSourceId();
				recVal = receiveMessage.getVal();

				if(currCheckpoint.getSent().get(source) > recVal)
				{
					diff = currCheckpoint.getSent().get(source) - recVal;

					//logger.info("Diff: " + diff);

					for(int i = 0; i < diff; i++)
					{
						lostMessage = new REBMessage(nodeId, source, Constants.LOST, timestampService.getCurrTimestamp(), false);

						network.sendToNeighbor(source, lostMessage);
					}
				}
				else
				{
					//logger.info("No lost message for : " + source);
				}
			}
		}

		@Override
		public void run()
		{
			Message m;
			JuangMessage juangMessage;
			REBMessage rebMessage;
			TreeMessage treeMessage;
			while(true)
			{
				try
				{
					m = messageQueue.take();

					//logger.info("MessageProcessor processing : " + m);

					if(m.getType().equals(Constants.REB))
					{
						rebMessage = (REBMessage) m;

						if(!Globals.juangInRecovery || failureDoneSentCount == neighborids.size())
						{
							takeCheckpoint(rebMessage); 
						}

						rebMessage.setREBAllowed();

						synchronized (Globals.allowRebLock)
						{
							Globals.allowRebLock.notify();
						}
					}
					else if(m.getType().equals(Constants.RECOVERY) && m.getSourceId() == nodeId)
					{
						juangMessage = (JuangMessage) m;
						recoverySentCount[juangMessage.getFailureNumber()]++;

						if(isRecoveryFloodDone(juangMessage.getFailureNumber()))
						{
							floodSent();
						}
					}
					else if(m.getType().equals(Constants.RECOVERY) && m.getDestId() == nodeId)
					{
						juangMessage = (JuangMessage) m;

						if(juangMessage.getFailureNumber() > maxFailureSeen)
						{
							maxFailureSeen++;
						}

						if(!Globals.juangInRecovery)
						{
							Globals.juangInRecovery = true;
							Globals.rebActive = false;

							floodRecovery();
						}

						recoveryRcvdCount[juangMessage.getFailureNumber()]++;

						if(isRecoveryFloodDone(juangMessage.getFailureNumber()))
						{
							floodSent();
						}
					}
					else if(m.getType().equals(Constants.SENT))
					{
						juangMessage = (JuangMessage) m;

						if(m.getSourceId() == nodeId)
						{
							sentSentCount[juangMessage.getRoundNo()]++;

							if(isSentFloodDone(juangMessage.getRoundNo()))
							{
								recover();

								if(roundNo % Globals.D == 0 || roundNo == N - 1)
								{
									//logger.info("Done for mth round");
									mthRoundDone = true;

									if(isLeaf || forwardRcvdCount == children.length)
									{
										//send the tree message to root
										treeMessage = new TreeMessage(nodeId, parent, Constants.FORWARD, recoveredForMthRound);
										//logger.info("Sent forward: " + treeMessage);
										network.sendToNeighbor(parent, treeMessage);
									}
								}
								else
								{
									floodSent();
								}
							}
						}
						else if(m.getDestId() == nodeId)
						{
							sentRcvdCount[juangMessage.getRoundNo()]++;

							sentMsgQueue.add(juangMessage);

							if(isSentFloodDone(juangMessage.getRoundNo()))
							{
								recover();

								if(roundNo % Globals.D == 0 || roundNo == N - 1)
								{
									//logger.info("Done for mth round");
									mthRoundDone = true;

									if(isLeaf || forwardRcvdCount == children.length)
									{
										//send the tree message to root
										treeMessage = new TreeMessage(nodeId, parent, Constants.FORWARD, recoveredForMthRound);
										//logger.info("Sent forward: " + treeMessage);
										network.sendToNeighbor(parent, treeMessage);
									}
								}
								else
								{
									floodSent();
								}
							}
						}
					}
					else if(m.getType().equals(Constants.FORWARD) && m.getDestId() == nodeId)
					{
						treeMessage = (TreeMessage) m;

						//logger.info("Received forward: " + treeMessage);
						//logger.info("recoveredForMthRound is: "+recoveredForMthRound);

						//logger.info("treeMessage.getRecoveryStatus() is: "+treeMessage.getRecoveryStatus());

						recoveredForMthRound = recoveredForMthRound | treeMessage.getRecoveryStatus();

						//logger.info("recoveredForMthRound is after OR: "+recoveredForMthRound);

						forwardRcvdCount++;

						if((forwardRcvdCount == children.length) && mthRoundDone)
						{
							if(!isRoot)
							{
								//send the tree message to root
								treeMessage = new TreeMessage(nodeId, parent, Constants.FORWARD, recoveredForMthRound);

								network.sendToNeighbor(parent, treeMessage);
								
								//logger.info("After parent sending: " + currCheckpoint);
							}
							else
							{
								//logger.info("After parent sending: " + currCheckpoint);
								
								if(recoveredForMthRound)
								{
									//logger.info("Going to send continue for roundNo: " + roundNo);

									for(int i = 0; i < children.length; i++)
									{
										treeMessage = new TreeMessage(nodeId, children[i], Constants.CONTINUE, false);
										network.sendToNeighbor(children[i], treeMessage);
									}

									//clear round
									recoveredForMthRound = false;
									mthRoundDone = false;
									forwardRcvdCount = 0;

									//call flood sent
									floodSent();
								}
								else
								{
									//logger.info("Going to send abort for roundNo:  : " + roundNo);

									for(int i = 0; i < children.length; i++)
									{
										treeMessage = new TreeMessage(nodeId, children[i], Constants.ABORT, false);
										network.sendToNeighbor(children[i], treeMessage);
									}

									/*TesterMessage tstSentMsg = new TesterMessage(nodeId, Constants.BOTTOM, Constants.SENT, failuresEncountered, currCheckpoint.getActiveState(), timestampService.getCurrTimestamp());

									//logger.info("Tester CP: " + currCheckpoint.toString());
									//logger.info("Tester Message: " + tstSentMsg.toString());

									network.sendToTester(tstSentMsg);

									clearVariables();*/

									roundNo = N - 1;
									floodSent();
								}
							}
						}
					}
					else if(m.getType().equals(Constants.CONTINUE))
					{
						if(m.getDestId() == nodeId)
						{
							if(!isLeaf)
							{
								for(int i = 0; i < children.length; i++)
								{
									treeMessage = new TreeMessage(nodeId, children[i], Constants.CONTINUE, false);
									network.sendToNeighbor(children[i], treeMessage);
								}

								//clear round
								recoveredForMthRound = false;
								mthRoundDone = false;
								forwardRcvdCount = 0;

								//call flood sent
								floodSent();
							}
							else
							{
								//clear round
								recoveredForMthRound = false;
								mthRoundDone = false;
								forwardRcvdCount = 0;

								//call flood sent
								floodSent();
							}
						}
					}
					else if(m.getType().equals(Constants.ABORT))
					{
						if(m.getDestId() == nodeId)
						{
							if(!isLeaf)
							{
								for(int i = 0; i < children.length; i++)
								{
									treeMessage = new TreeMessage(nodeId, children[i], Constants.ABORT, false);
									network.sendToNeighbor(children[i], treeMessage);
								}

								/*TesterMessage tstSentMsg = new TesterMessage(nodeId, Constants.BOTTOM, Constants.SENT, failuresEncountered, currCheckpoint.getActiveState(), timestampService.getCurrTimestamp());

								//logger.info("Tester CP: " + currCheckpoint.toString());
								//logger.info("Tester Message: " + tstSentMsg.toString());

								network.sendToTester(tstSentMsg);

								//clear variables
								clearVariables();*/

								roundNo = N - 1;
								floodSent();
							}
							else
							{
								/*TesterMessage tstSentMsg = new TesterMessage(nodeId, Constants.BOTTOM, Constants.SENT, failuresEncountered, currCheckpoint.getActiveState(), timestampService.getCurrTimestamp());

								//logger.info("Tester CP: " + currCheckpoint.toString());
								//logger.info("Tester Message: " + tstSentMsg.toString());

								network.sendToTester(tstSentMsg);

								//clear variables
								clearVariables();*/

								roundNo = N - 1;
								floodSent();
							}
						}
					}
					else if(m.getType().equals(Constants.RECEIVE))
					{
						juangMessage = (JuangMessage) m;

						if(m.getSourceId() == nodeId)
						{
							receiveSentCount++;

							if(isReceiveDone())
							{
								transmitLostMessages();

								floodFailureDone();

								//uncomment
								/*TesterMessage tstSentMsg = new TesterMessage(nodeId, Constants.BOTTOM, Constants.SENT, failuresEncountered, currCheckpoint.getActiveState(), timestampService.getCurrTimestamp());

								//logger.info("Tester CP: " + currCheckpoint.toString());
								//logger.info("Tester Message: " + tstSentMsg.toString());

								network.sendToTester(tstSentMsg);*/
							}
						}
						else if(m.getDestId() == nodeId)
						{
							receiveRcvdCount++;

							receiveQueue.add(juangMessage);

							if(isReceiveDone())
							{
								transmitLostMessages();

								floodFailureDone();
								
								//uncomment
								/*TesterMessage tstSentMsg = new TesterMessage(nodeId, Constants.BOTTOM, Constants.SENT, failuresEncountered, currCheckpoint.getActiveState(), timestampService.getCurrTimestamp());

								//logger.info("Tester CP: " + currCheckpoint.toString());
								//logger.info("Tester Message: " + tstSentMsg.toString());

								network.sendToTester(tstSentMsg);*/
							}
						}
					}
					else if(m.getType().equals(Constants.LOST))
					{
						rebMessage = (REBMessage) m;

						if(m.getDestId() == nodeId)
						{
							takeCheckpoint(rebMessage);
						}
					}
					else if(m.getType().equals(Constants.FAILURE_DONE))
					{
						if(m.getSourceId() == nodeId)
						{
							failureDoneSentCount++;

							if(isFailureDoneSent())
							{
								/*TesterMessage tstSentMsg = new TesterMessage(nodeId, Constants.BOTTOM, Constants.SENT, failuresEncountered, currCheckpoint.getActiveState(), timestampService.getTesterTimestamp());

								//logger.info("Tester CP: " + currCheckpoint.toString());
								//logger.info("Tester Message: " + tstSentMsg.toString());

								network.sendToTester(tstSentMsg);*/
								clearVariables();
							}
						}
						else if(m.getDestId() == nodeId)
						{
							failureDoneRcvdCount++;

							if(isFailureDoneSent())
							{
								/*TesterMessage tstSentMsg = new TesterMessage(nodeId, Constants.BOTTOM, Constants.SENT, failuresEncountered, currCheckpoint.getActiveState(), timestampService.getTesterTimestamp());

								//logger.info("Tester CP: " + currCheckpoint.toString());
								//logger.info("Tester Message: " + tstSentMsg.toString());

								network.sendToTester(tstSentMsg);*/

								clearVariables();
							}
						}
					}
				}
				catch (InterruptedException e)
				{
					e.printStackTrace();
				}
			}
		}
	}

	public void setTimestampService(TimestampService timestampService)
	{
		this.timestampService = timestampService;
	}

	@Override
	public void run()
	{
		Thread messageProcessor = new Thread(new MessageProcessor());
		messageProcessor.start();
	}
}
