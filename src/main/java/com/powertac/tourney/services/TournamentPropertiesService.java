package com.powertac.tourney.services;

	/*
	 * Copyright (c) 2011 by the original author
	 *
	 * Licensed under the Apache License, Version 2.0 (the "License");
	 * you may not use this file except in compliance with the License.
	 * You may obtain a copy of the License at
	 *
	 * http://www.apache.org/licenses/LICENSE-2.0
	 *
	 * Unless required by applicable law or agreed to in writing, software
	 * distributed under the License is distributed on an "AS IS" BASIS,
	 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	 * See the License for the specific language governing permissions and
	 * limitations under the License.
	 */

	import java.io.File;
	import java.io.IOException;
	import java.net.URL;
	import java.util.Collection;

	import org.apache.commons.configuration.CompositeConfiguration;
	import org.apache.commons.configuration.Configuration;
	import org.apache.commons.configuration.ConfigurationException;
	import org.apache.commons.configuration.PropertiesConfiguration;
	import org.apache.commons.configuration.XMLConfiguration;
	import org.apache.log4j.Logger;
	import org.powertac.common.config.Configurator;
	import org.springframework.beans.BeansException;
	import org.springframework.context.ApplicationContext;
	import org.springframework.context.ApplicationContextAware;
	import org.springframework.core.io.Resource;
	import org.springframework.stereotype.Service;

	/**
	 * @author jcollins
	 */
	@Service
	public class TournamentPropertiesService
	implements ApplicationContextAware
	{
	  static private Logger log = Logger.getLogger(TournamentPropertiesService.class);

	  private ApplicationContext context;
	  private CompositeConfiguration config;
	  private Configurator configurator;
	  
	  private boolean initialized = false;
	  
	  /**
	   * Default constructor
	   */
	  public TournamentPropertiesService ()
	  {
	    super();
	    System.out.println("Instantiated TournamentProperties Service");
	    
	    recycle();
	  }

	  // test support
	  void recycle ()
	  {
	    // set up the config instance
	    config = new CompositeConfiguration();
	    configurator = new Configurator();
	    initialized = false;
	  }
	  
	  /**
	   * Loads the properties from classpath, default config file,
	   * and user-specified config file, just in case it's not already been
	   * loaded. This is done when properties are first requested, to ensure
	   * that the logger has been initialized. Because the CompositeConfiguration
	   * treats its config sources in FIFO order, this should be called <i>after</i>
	   * any user-specified config is loaded.
	   */
	  void lazyInit ()
	  {
	    // only do this once
	    if (initialized)
	      return;
	    initialized = true;

	    // find and load the default properties file
	    log.debug("lazyInit");
	    try {
	      File defaultProps = new File("config/broker.properties");
	      log.debug("adding " + defaultProps.getName());
	      config.addConfiguration(new PropertiesConfiguration(defaultProps));
	    }
	    catch (Exception e1) {
	      log.warn("config/server.properties not found: " + e1.toString());
	    }
	    
	    // set up the classpath props
	    try {
	      Resource[] xmlResources = context.getResources("classpath*:/**/properties.xml");
	      for (Resource xml : xmlResources) {
	        if (validXmlResource(xml)) {
	          log.info("loading config from " + xml.getURI());
	          XMLConfiguration xconfig = new XMLConfiguration();
	          xconfig.load(xml.getInputStream());
	          config.addConfiguration(xconfig);
	        }
	      }
	      Resource[] propResources = context.getResources("classpath*:*.properties");
	      for (Resource prop : propResources) {
	        if (validPropResource(prop)) {
	          log.info("loading config from " + prop.getURI());
	          PropertiesConfiguration pconfig = new PropertiesConfiguration();
	          pconfig.load(prop.getInputStream());
	          config.addConfiguration(pconfig);
	        }
	      }
	    }
	    catch (ConfigurationException e) {
	      log.error("Error loading configuration: " + e.toString());
	    }
	    catch (Exception e) {
	      log.error("Error loading configuration: " + e.toString());
	    }
	    
	    // set up the configurator
	    configurator.setConfiguration(config);
	  }
	  
	  public void setUserConfig (URL userConfigURL)
	  {
	    // then load the user-specified config
	    try {
	      PropertiesConfiguration pconfig = new PropertiesConfiguration();
	      pconfig.load(userConfigURL.openStream());
	      config.addConfiguration(pconfig);
	      log.debug("setUserConfig " + userConfigURL.toExternalForm());
	    }
	    catch (IOException e) {
	      log.error("IO error loading " + userConfigURL + ": " + e.toString());
	    }
	    catch (ConfigurationException e) {
	      log.error("Config error loading " + userConfigURL + ": " + e.toString());
	    }
	    lazyInit();
	  }

	  public void configureMe (Object target)
	  {
	    lazyInit();
	    configurator.configureSingleton(target);    
	  }
	 
	  public Collection<?> configureInstances (Class<?> target)
	  {
	    lazyInit();
	    return configurator.configureInstances(target);
	  }
	  
	  public String getProperty (String name)
	  {
	    lazyInit();
	    return config.getString(name);
	  }

	  public String getProperty (String name, String defaultValue)
	  {
	    lazyInit();
	    return config.getString(name, defaultValue);
	  }

	  public Integer getIntegerProperty (String name, Integer defaultValue)
	  {
	    lazyInit();
	    return config.getInteger(name, defaultValue);
	  }

	  public Double getDoubleProperty (String name, Double defaultValue)
	  {
	    lazyInit();
	    return config.getDouble(name, defaultValue);
	  }

	  @Override
	  public void setApplicationContext (ApplicationContext context)
	      throws BeansException
	  {
	     this.context = context;
	  }
	  
	  /**
	   * Changes the value of a property (or adds a property).
	   */
	  public void setProperty (String key, Object value)
	  {
	    lazyInit();
	    config.setProperty(key, value);
	  }
	  
	  // -- valid configuration resources --
	  private String[] excludedPaths =
	    {".*/test-classes/.*", ".*/log4j.properties"};
	  
	  private boolean validXmlResource (Resource xml)
	  {
	    try {
	      String path = xml.getFile().getPath();
	      for (String regex : excludedPaths) {
	        if (path.matches(regex)) {
	          return false;
	        }
	      }
	      return true;
	    }
	    catch (IOException e) {
	      log.error("Should not happen: " + e.toString());
	      return false;
	    }
	  }
	  
	  private boolean validPropResource (Resource prop)
	  {
	    return validXmlResource(prop);
	  }
	  
	  // test support
	  Configuration getConfig ()
	  {
	    return config;
	  }
	}
