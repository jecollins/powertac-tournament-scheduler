package org.powertac.tourney.beans;

import org.powertac.tourney.services.Database;

import javax.faces.bean.ManagedBean;
import javax.faces.bean.SessionScoped;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;


@SessionScoped
@ManagedBean
public class User
{

  /*
   * Possible permisssions that a user can have Changes the form displayed to
   * the user
   */

  public static final String key = "user";

  private String username;
  private String password;
  private int permissions;
  private boolean loggedIn;
  private int userId;
  private boolean isEditing;
  private List<Broker> brokers;

  // Brokers

  public User ()
  {
    this.brokers = new Vector<Broker>();
    this.userId = -1;
    this.username = "Guest";
    this.password = "";
    this.permissions = Permission.GUEST;
    this.loggedIn = false;
  }

  public void addBroker (String brokerName, String shortDescription)
  {
    // Broker b = new Broker(brokerName, shortDescription);
    // brokers.add(b);

    if (userId != -1) {
      Database db = new Database();
      try {
        db.startTrans();
        db.addBroker(getUserId(), brokerName, shortDescription);
        db.commitTrans();
      }
      catch (SQLException e) {
        db.abortTrans();
        e.printStackTrace();
      }
    }

  }

  public void deleteBroker (int brokerId)
  {
    Database db = new Database();

    try {
      db.startTrans();
      db.deleteBrokerByBrokerId(brokerId);
      db.commitTrans();
    }
    catch (SQLException e) {
      db.abortTrans();
      e.printStackTrace();
    }
  }

  public Broker getBroker (int brokerId)
  {
    Database db = new Database();
    try {
      db.startTrans();
      Broker b = db.getBroker(brokerId);
      db.commitTrans();
      return b;
    }
    catch (SQLException e) {
      db.abortTrans();
      e.printStackTrace();
      return null;
    }

  }

  public static String getKey ()
  {
    return key;
  }

  public String getUsername ()
  {
    return this.username;
  }

  public void setUsername (String username)
  {
    this.username = username;
  }

  public String getPassword ()
  {
    return this.password;
  }

  public void setPassword (String password)
  {
    this.password = password;
  }

  public int getPermissions ()
  {
    return this.permissions;
  }

  public void setPermissions (int permissions)
  {
    this.permissions = permissions;
  }

  public boolean login ()
  {
    // Set loggedIn value
    this.loggedIn = true;

    return false;
  }

  public boolean logout ()
  {
    // There is probably a better way to do this
    this.brokers = null;
    this.userId = -1;
    this.username = "Guest";
    this.password = "";
    this.permissions = Permission.GUEST;
    this.loggedIn = false;

    return false;

  }

  public boolean getLoggedIn ()
  {
    return this.loggedIn;
  }

  public List<Broker> getBrokers ()
  {
    brokers = new ArrayList<Broker>();

    if (!isEditing && loggedIn) {
      Database db = new Database();
      try {
        db.startTrans();
        brokers = db.getBrokersByUserId(getUserId());
        db.commitTrans();
      }
      catch (SQLException e) {
        db.abortTrans();
      }
    }

    return brokers;
  }

  public int getUserId ()
  {
    return userId;
  }

  public void setUserId (int userId)
  {
    this.userId = userId;
  }

  public boolean isEdit ()
  {
    return isEditing;
  }

  public void setEdit (boolean isEditing)
  {
    this.isEditing = isEditing;
  }

}
