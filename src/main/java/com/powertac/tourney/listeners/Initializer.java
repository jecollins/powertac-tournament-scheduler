package com.powertac.tourney.listeners;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import com.powertac.tourney.beans.Games;

public class Initializer implements ServletContextListener {

	public void contextDestroyed(ServletContextEvent arg0) {
		// TODO Auto-generated method stub

	}

	public void contextInitialized(ServletContextEvent e) {
		// TODO Check database and load in pending and inprogress games
		
		e.getServletContext().setAttribute(Games.getKey(), new Games());
	}

}