package com.linkedin.drelephant.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;


public class ProcessUtil {

  private static final Logger logger = Logger.getLogger(ProcessUtil.class);

  public static boolean executeScript(String command) {
    List<String> error = new ArrayList<String>();
    try {
      Process p = Runtime.getRuntime().exec(command);
      logger.info(command);

      BufferedReader errorStream = new BufferedReader(new InputStreamReader(p.getErrorStream()));
      String errorLine;
      while ((errorLine = errorStream.readLine()) != null) {
        error.add(errorLine);
      }
      if (error.size() != 0) {
        logger.error("Error in python script running whitelist manager: " + error.toString());
      } else {
        return true;
      }
    } catch (IOException e) {
      logger.error("Error in executeScript()", e);
    }
    return false;
  }

}
