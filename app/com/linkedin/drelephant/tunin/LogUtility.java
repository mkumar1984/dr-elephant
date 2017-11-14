package com.linkedin.drelephant.tunin;

import org.apache.log4j.Logger;

public class LogUtility {

    private static final Logger logger = Logger.getLogger("LogUtility");

    public static void log(String msg)
    {
        logger.error(msg);
    }

}
