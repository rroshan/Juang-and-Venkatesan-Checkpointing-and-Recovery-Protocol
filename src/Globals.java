
public class Globals
{
	public static volatile boolean rebActive = false;
	public static volatile boolean juangInRecovery = false;
	public static volatile Object rebLock = new Object();
	public static volatile Object allowRebLock = new Object();
	public static int neighborCount;
	public static boolean iAmTester = false;
	public static int D;
}
