package org.powertac.tourney.listeners;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

public class Initializer implements ServletContextListener
{
  public void contextDestroyed (ServletContextEvent e)
  {
    // e.getServletContext().getAttribute(Games.getKey());
    // e.getServletContext().getAttribute(Tournaments.getKey());
    // e.getServletContext().getAttribute(Machines.getKey());
  }

  public void contextInitialized (ServletContextEvent e)
  {
    // TODO Check database and load in pending and inprogress games
    // Load games, tournaments and machines into the application context
  }
}
