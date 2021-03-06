package org.powertac.tourney.scheduling;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;

import static org.powertac.tourney.services.Utils.log;

public class MainScheduler
{
  private int noofagents;
  private DbConnection db;
  private ServerPanel serverpanel;

  /*
   * cube is the routing matrix based on IncidenceCube class
   */
  private GameCube scheduleMatrix;

  public MainScheduler (int agents, int nservers) throws Exception
  {
    noofagents = agents;
    // TODO Why is this a different connection?
    db = new DbConnection();
    try {
      db.Setup();
    }
    catch (Exception E) {
      log("Cannot open connection to DB");
      E.printStackTrace();
      System.exit(1);
    }

    // TODO Check if this is needed
    // might not be needed
    //InitServers(nservers);
  }

  private void InitServers (int num)
  {
    int i = 0, len;
    String sql_insert, insertstring = "";
    for (; i < num; i++) {
      insertstring += " (0,'Server" + (i + 1) + "',default,0), ";
    }
    len = insertstring.length();
    insertstring = insertstring.substring(0, len - 2);
    // TODO Move this to Constants
    sql_insert =
      "insert into GameServers (ServerID, ServerName, ServerNumber, IsPlaying) values "
              + insertstring;
    try {
      db.SetQuery(sql_insert, "update");
    }
    catch (Exception e) {
      log("Cannot create Server Table Entries");
      System.exit(1);
    }
  }

  public void initServerPanel (int n) throws Exception
  {
    serverpanel = new ServerPanel(db, n);
  }

  public int getGamesEstimate () throws Exception
  {
    /*
     * This is function is supposed will return the total number of games to be
     * played.
     * 1. initialize the panel
     * 2. initialize the score board
     * 3. orchestrate with the database.
     * a. gets the number of agents and initializes the database. (AGENTS table)
     * b. gets the game types and number of each game to be played.
     * c. gets the number of servers available
     */
    int gametype, navail, nrepeats, gamenumber, games = 0,
        iteration = 0, nscheduleds = 0;
    boolean available_server;
    Server[] slist;
    AgentLet[] agents;
    float repeatratio;
    while ((gametype = scheduleMatrix.getDisparity()) > 0) {
      agents = scheduleMatrix.getAgentsForGamesEstimates(db, gametype);
      games++;
    }
    return games;
  }

  public HashMap<Server,AgentLet[]> Schedule () throws Exception
  {
    /*
     * This is function is supposed to
     * 1. initialize the panel
     * 2. initialize the score board
     * 3. orchestrate with the database.
     * a. gets the number of agents and initializes the database. (AGENTS table)
     * b. gets the game types and number of each game to be played.
     * c. gets the number of servers available
     */
    int gametype, navail, nrepeats, gamenumber, iteration = 0, nscheduleds = 0;
    Server available_server;
    Server[] slist;
    AgentLet[] agents;
    float repeatratio;

    navail = serverpanel.LoadServerPanelFromDB(db);
    available_server = serverpanel.getAvailableServer();
    HashMap<Server,AgentLet[]> gamesToStart = new HashMap<Server,AgentLet[]>();
    while (((gametype = scheduleMatrix.getDisparity()) > 0)
           && (available_server != null)/* server availability */) {
      //log("Gametype {0}", gametype);
      /*
       * this queries the game cube to get the next schedule for a
       * particular game type.
       */
      agents = scheduleMatrix.getAgents(db, gametype);
      
      if (agents != null) {
        db.startTransaction();
        /* Make entries in Gamelog and GameArchive */
        gamenumber = createGameArchive(gametype, available_server);
        logGame(agents, gamenumber);
        /* Mark Agent's IsPlaying as true */
        updateAgentsStatus(agents);
        
        /* Commit transaction */
        db.commitTransction();
        /* Set the server busy */
        serverpanel.publishBusy(available_server);
        gamesToStart.put(available_server, agents);
        
        /* have some means to SEND selected Agents to the Server *** */
        nscheduleds++;
        printAgents(agents);
        iteration++;
        available_server = serverpanel.getAvailableServer();
      }
    }
    scheduleMatrix.clearLookAheads();
    slist = serverpanel.getScheduledServers();
    serverpanel.publishDeployedServersToDB(db, slist);
    //log("-----------{0}", iteration);
    return gamesToStart;
  }

  public void initGameCube (int[] gtypes, int[] mxgames) throws Exception
  {
    scheduleMatrix = new GameCube(db, noofagents, gtypes, mxgames);
  }

  public void initializeAgentsDB (int noofagents, int noofcopies)
    throws SQLException
  {
    /*
     * This inserts and initializes the agent tables.
     * TO CHANGE: to insert in RANDOM
     */
    int i, j;
    String sql_insert_into_agents, sql_insert_into_queue;
    for (i = 1; i <= noofagents; i++) {
      for (j = 1; j <= noofcopies; j++) {
        // TODO Move this to Constants
        sql_insert_into_agents =
          "insert into AgentAdmin (InternalAgentID,AgentType, AgentCopy, AgentName, AgentDescription) "
                  + "values (default, "
                  + i
                  + ", "
                  + j
                  + ",'Agent"
                  + i
                  + "_Copy" + j + "','')";
        // log(sql_insert_into_agents);
        db.SetQuery(sql_insert_into_agents, "update");
      }
    }
    // TODO Move this to Constants
    sql_insert_into_queue =
      "insert into AgentQueue "
              + "select InternalAgentID,AgentType,0,0,0 from	AgentAdmin order by rand()";
    // log(sql_insert_into_queue);
    db.SetQuery(sql_insert_into_queue, "update");
  }

  private void logGame (AgentLet[] agents, int gamenumber /*
                                                           * game label ie.
                                                           * InternalGameID
                                                           */) throws Exception
  {
    int i, len;
    String valuesstring = "", sql_insert;
    for (i = 0; i < agents.length; i++) {
      valuesstring += " (" + agents[i].getAgentId() + "," + gamenumber + "), ";
    }
    len = valuesstring.length();
    valuesstring = valuesstring.substring(0, len - 2);
    // TODO Move this to Constants
    sql_insert = "INSERT into GameLog Values " + valuesstring;
    // log(sql_insert);
    db.SetQuery(sql_insert, "update");
  }

  private int createGameArchive (int gametype, Server aserver) throws Exception
  {

    String sql_insert, sql_gamenumber;
    ResultSet rs;
    // TODO Move this to Constants
    sql_insert =
      "INSERT INTO GameArchive" + " (" + " InternalGameID," + " GameType,"
              + " TimePlayed," + " TimeCompleted," + " ServerNumber,"
              + " IsPlaying" + ") " + "VALUES " + " (default," + gametype + ","
              + " now()," + " '0000-00-00 00:00:00',"
              + aserver.getServerNumber() + "," + " 1)";
    db.SetQuery(sql_insert, "update");
    // TODO Move this to Constants
    sql_gamenumber =
      "select max(InternalGameID) as mx from GameArchive where IsPlaying = 1";
    rs = db.SetQuery(sql_gamenumber);
    rs.next();
    return rs.getInt("mx");
  }

  /* calculates the number of m combinations out of n samples */
  private int nCm (int noofagents, int gametype)
  {
    return factorial(noofagents)
           / (factorial(noofagents - gametype) * factorial(gametype));
  }

  /* Calculates Factorial */
  private int factorial (int n)
  {
    int i, fact = 1;
    for (i = 2; i <= n; i++) {
      fact *= i;
    }
    return fact;
  }

  private void updateAgentsStatus (AgentLet[] agentids) throws Exception
  {
    /*
     * agentids are a list of agents whose numbers IsPlaying fields needs to be
     * set to 1;
     */

    int i, len;
    String wherestring = "", secondwherestring = "";

    for (i = 0; i < agentids.length; i++) {
      wherestring += " AgentType = " + agentids[i].getAgentType() + " OR";
      secondwherestring +=
        " InternalAgentID = " + agentids[i].getAgentId() + " OR";
    }
    len = wherestring.length();
    wherestring = wherestring.substring(0, len - 3);
    len = secondwherestring.length();
    secondwherestring = secondwherestring.substring(0, len - 3);
    // TODO Move this to Constants
    String sql_update_string =
      " update AgentQueue " + " set Prev_Age=Age, Age = Age+pow(2,"
              + agentids.length + ") " + " where (" + wherestring + ")";

    db.SetQuery(sql_update_string, "update");

    // TODO Move this to Constants
    sql_update_string =
      " update AgentQueue " + " set IsPlaying = 1 " + " where ("
              + secondwherestring + ")";

    db.SetQuery(sql_update_string, "update");
  }

  /*
   * 05/17/2012 Instead of querying the database the
   * It will query the game cube
   * CHANGE getAgents();
   */

  /* returns a whole number which designates the number of samw combinations */
  private int getNumberOfSameCombinations (AgentLet[] agents, int gtype)
    throws Exception
  {
    int i, len, cnt = 0;
    ResultSet rs;
    String wherestring = "", sql_check_combos;
    for (i = 0; i < agents.length; i++) {
      wherestring += " b.AgentType = " + agents[i].getAgentType() + " OR ";
    }
    len = wherestring.length();
    wherestring = wherestring.substring(0, len - 3);

    // TODO Move this to Constants
    sql_check_combos =
      "select count(distinct b.AgentType) cnt" + " from GameLog a join "
              + " AgentQueue b on a.InternalAgentID=b.InternalAgentID join "
              + " GameArchive c on a.InternalGameID=c.InternalGameID"
              + " where c.GameType = " + gtype + " AND ( " + wherestring
              + " ) " + " group by a.InternalGameID" + " having cnt = " + gtype;
    rs = db.SetQuery(sql_check_combos);
    while (rs.next()) {
      cnt++;
    }
    return cnt;
  }

  /*
   * private boolean gameTypeExists(int gametype) throws Exception {
   * ResultSet rs;
   * int cnt = -1;
   * String sql_get_agent_count =
   * "select count(*) as cnt from" +
   * " (select InternalAgentID" +
   * " 	from AgentQueue " +
   * "	where IsPlaying = 0" +
   * "	group by AgentType" +
   * ") as AA";
   * //log(sql_get_agent_count);
   * rs = db.SetQuery(sql_get_agent_count);
   * rs.next();
   * cnt = Integer.parseInt(rs.getString("cnt"));
   * if(cnt >=gametype) return true;
   * return false;
   * }
   */
  public void resetServers (int ServerNumber) throws Exception
  {
    /*int i = 0, len, num, randnum;
    Server[] servernumbers;
    ResultSet rs;
    String wherestring = "(";
    String sql_select =
      "select count(*) cnt from GameServers " + " where IsPlaying = 1 ";
    rs = db.SetQuery(sql_select);
    rs.next();
    num = rs.getInt("cnt");
    randnum = (int) Math.ceil(Math.random() * num);
    servernumbers = new Server[randnum];
    // log(randnum.toString());
    sql_select =
      "select ServerNumber from GameServers " + " where IsPlaying = 1 "
              + " limit " + randnum;
    // log(sql_select);
    rs = db.SetQuery(sql_select);
    while (rs.next()) {
      servernumbers[i] = new Server();
      servernumbers[i].setServerNumber(rs.getInt("ServerNumber"));
      wherestring +=
        " ServerNumber =  " + servernumbers[i].getServerNumber() + " OR ";
      i++;
    }
    len = wherestring.length();
    if (len > 1) {
      wherestring = wherestring.substring(0, len - 3) + ")";
      log("Releasing {0}", wherestring);
      gameRelease(servernumbers);*/

	  Server[] server = new Server[1];

    // TODO Move this to Constants
	  ResultSet rs = db.SetQuery("SELECT * FROM GameServers WHERE ServerNumber="+ServerNumber);
	  if(rs.next()){
		  server[0] = new Server(rs);
	  }
	  
	  gameRelease(server);

    // TODO Move this to Constants
    String sql_reset =
      "update GameServers " + "set	IsPlaying = 0 " + "where  ServerNumber = " + ServerNumber;
    db.SetQuery(sql_reset, "update");
  }

  public void resetCube ()
  {
    scheduleMatrix.resetActualSumsAndProposedSums();
  }

  /*
   * IMPORTANT FUNCTION: basically releases IsPlaying and completion time
   * statuses in the GameArchive,
   * Also resets IsPlaying in Agents table
   * Clears bit in Server Panel
   */
  public void gameRelease (Server[] servernumbers) throws Exception
  {
    int i, len;
    ResultSet rs;
    String wherestring = "(";
    String sql_release;
    for (i = 0; i < servernumbers.length; i++) {
      wherestring +=
        "a.ServerNumber = " + servernumbers[i].getServerNumber() + " OR ";
    }

    len = wherestring.length();
    wherestring = wherestring.substring(0, len - 3) + ")";
    // TODO Move this to Constants
    rs =
      db.SetQuery("select count(*) cnt from GameArchive a where " + wherestring
                  + " and IsPlaying = 1");
    rs.next();
    // TODO Move this to Constants
    sql_release =
      "update  GameArchive a 	"
              + "join GameLog b"
              + " on a.InternalGameID =  b.InternalGameID "
              + "join AgentQueue c "
              + "on b.InternalAgentID = c.InternalAgentID "
              + "set c.IsPlaying = 0, a.TimeCompleted = now(), a.IsPlaying = 0 "
              + "where " + wherestring + " and a.IsPlaying = 1";
    db.SetQuery(sql_release, "update");
    /*
     * rs =
     * db.SetQuery("select count(*) cnt from GameArchive a where "+wherestring
     * +" and IsPlaying = 1");
     * rs.next();
     * log("Change2: {0}", rs.getInt("cnt"));
     */
  }

  private void printAgents (AgentLet[] agents)
  {
    /*int i = 0;
    log("Agents");
    for (i = 0; i < agents.length; i++) {
      log("Agent ID {0} Agent Type {1}",
          agents[i].getAgentId(), agents[i].getAgentType());
    }*/
  }

  private void reArrangeAgents (AgentLet[] agents, int gtype) throws Exception
  {
    ResultSet rs;
    int i, len, rval;
    int[] values = new int[agents.length];
    int[] atype = new int[agents.length];
    String sql;
    String sqlpre = "update AgentQueue a set a.Age  = a.Age+";
    String sqlmid = " where AgentType = ";
    String sqlpost = " and IsPlaying=0 ";
    String wherestring = "";
    for (i = 0; i < agents.length; i++) {
      wherestring += " c.AgentType = " + agents[i].getAgentType() + " OR";
    }
    len = wherestring.length();
    wherestring = wherestring.substring(0, len - 2);
    // TODO Move this to Constants
    String sql_rearrange =
      "select count(distinct b.`InternalGameID`) cnt, AgentType "
              + " from GameLog a "
              + " join GameArchive b on a.`InternalGameID`=b.`InternalGameID` "
              + " join AgentQueue c on a.`InternalAgentID`= c.`InternalAgentID` "
              + " where (" + wherestring + ") and GameType =  " + gtype
              + " group by AgentType ";
    rs = db.SetQuery(sql_rearrange);
    i = 0;
    while (rs.next()) {
      values[i] = rs.getInt("cnt");
      atype[i] = rs.getInt("AgentType");
      i++;
    }
    for (i = 0; i < values.length; i++) {
      sql = sqlpre + values[i] + sqlmid + atype[i] + sqlpost;
      db.SetQuery(sql, "update");
    }
  }

  public boolean equilibrium ()
  {
    return !(scheduleMatrix.getDisparity() > 0);
  }
}

