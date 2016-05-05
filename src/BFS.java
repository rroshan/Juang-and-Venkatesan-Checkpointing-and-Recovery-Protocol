import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedList;

public class BFS
{
	private static String FILE_NAME = "_treedata_";
	class Node
	{
		private int nodeId;
		private boolean visited;
		private LinkedList<Node> adjNodes = new LinkedList<Node>();
		private int dist;
		private Node parent;
		private LinkedList<Node> children;

		public Node(int nodeId)
		{
			this.nodeId = nodeId;
			visited = false;
			dist = -1;
			parent = null;
			children = new LinkedList<Node>();
		}

		public void setVisited(boolean visited)
		{
			this.visited = visited;
		}

		public boolean isVisited()
		{
			return visited;
		}

		public void addAdjVertex(Node adjVertex)
		{
			adjNodes.add(adjVertex);
		}

		public LinkedList<Node> getAdjVertices()
		{
			return adjNodes;
		}

		public int getNodeId()
		{
			return nodeId;
		}

		public void setDist(int dist)
		{
			this.dist = dist;
		}

		public int getDist()
		{
			return dist;
		}

		public void setParent(Node parent)
		{
			this.parent = parent;
		}

		public Node getParent()
		{
			return parent;
		}

		public void addChildren(Node child)
		{
			children.add(child);
		}

		public LinkedList<Node> getChildren()
		{
			return children;
		}

		public String toString()
		{
			StringBuilder strBuilder = new StringBuilder();
			if(parent != null)
			{
				strBuilder.append(parent.getNodeId());
			}
			else
			{
				strBuilder.append("*");
			}

			strBuilder.append("\n");
			
			if(children.isEmpty())
			{
				strBuilder.append("*");
			}

			for(int i = 0; i < children.size(); i++)
			{
				if(i < children.size() - 1)
				{
					strBuilder.append(children.get(i).getNodeId() + " ");
				}
				else if(i == children.size() - 1)
				{
					{
						strBuilder.append(children.get(i).getNodeId());
					}
				}
			}

			return strBuilder.toString();
		}
	}

	public void findBFSTree(String neighbors, String configFileName)
	{
		Node[] nodes;
		int N;

		String[] neighborsArr = neighbors.split("#");

		N = neighborsArr.length; 

		nodes = new Node[N];

		for(int i = 0; i < N; i++)
		{
			nodes[i] = new Node(i);
		}

		for(int i = 0; i < N; i++)
		{
			String[] iNeighbors = neighborsArr[i].split(" ");

			for(int j = 0; j < iNeighbors.length; j++)
			{
				nodes[i].addAdjVertex(nodes[Integer.parseInt(iNeighbors[j])]);
			}
		}

		//source
		int maxDistEncountered = 0;
		int S = 0;

		nodes[S].setDist(0);
		nodes[S].setVisited(true);

		LinkedList<Node> queue = new LinkedList<Node>();
		queue.add(nodes[S]);

		while(!queue.isEmpty())
		{
			Node curr = queue.poll();
			int currDist = curr.getDist();

			LinkedList<Node> adjNodes = curr.getAdjVertices();
			for(Node adjNode : adjNodes)
			{
				if(!adjNode.isVisited())
				{
					adjNode.setDist(currDist + 1);

					maxDistEncountered = currDist + 1;

					adjNode.setVisited(true);
					queue.add(adjNode);

					adjNode.setParent(curr);
					curr.addChildren(adjNode);
				}
			}
		}

		FileWriter fileWriter = null;
		BufferedWriter bw = null;

		for(Node node : nodes)
		{
			try
			{
				fileWriter = new FileWriter(new File(configFileName+FILE_NAME+node.getNodeId()+".txt"));
				bw = new BufferedWriter(fileWriter);
				bw.write(node.toString());
				bw.write("\n");
				bw.write(new Integer(maxDistEncountered).toString());
			}
			catch (IOException e)
			{
				e.printStackTrace();
			} finally {
				try {
					bw.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public static void main(String[] args)
	{
		System.out.println(args[0]);
		System.out.println(args[1]);
		BFS bfs = new BFS();
		bfs.findBFSTree(args[0], args[1]);
	}
}
