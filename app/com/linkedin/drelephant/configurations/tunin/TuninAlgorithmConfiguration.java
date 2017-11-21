/*
 * Copyright 2016 LinkedIn Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.linkedin.drelephant.configurations.tunin;

import com.linkedin.drelephant.util.Utils;
import org.apache.log4j.Logger;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TuninAlgorithmConfiguration {

    private static final Logger logger = Logger.getLogger(TuninAlgorithmConfiguration.class);
    private List<TuninAlgorithmConfigurationData> _tuninAlgorithmConfDataList;

    public TuninAlgorithmConfiguration(Element configuration) {
        parseTuninAlgorithmConfiguration(configuration);
    }

    /**
     * Returns the list of Tuning algorithms with their Configuration Information
     *
     * @return A list of Configuration Data for tuning
     */
    public List<TuninAlgorithmConfigurationData> getTuninAlgorithmsConfData() {
        return _tuninAlgorithmConfDataList;
    }

    /**
     * Parses the Tunin configuration file and loads the algorithm Information to a list of TuninAlgorithmConfigurationData
     *
     * @param configuration The dom Element to be parsed
     */
    private void parseTuninAlgorithmConfiguration(Element configuration) {
        _tuninAlgorithmConfDataList = new ArrayList<TuninAlgorithmConfigurationData>();

        NodeList nodes = configuration.getChildNodes();
        int n = 0;
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                n++;
                Element tuninAlgoNode = (Element) node;

                String className;
                Node classNameNode = tuninAlgoNode.getElementsByTagName("classname").item(0);
                if (classNameNode == null) {
                    throw new RuntimeException("No tag 'classname' in tunin algorithm " + n);
                }
                className = classNameNode.getTextContent();
                if (className.equals("")) {
                    throw new RuntimeException("Empty tag 'classname' in tunin algorithm " + n);
                }

                Node algoNameNode = tuninAlgoNode.getElementsByTagName("algoname").item(0);
                if (algoNameNode == null) {
                    throw new RuntimeException(
                            "No tag or invalid tag 'algoname' in tunin algorithm " + n + " classname " + className);
                }
                String algoName = algoNameNode.getTextContent();
                if (algoName == null) {
                    logger.error("Algorithm name is not specified in tunin algorithm " + n + " classname " + className
                            + ". Skipping this configuration.");
                    continue;
                }

                Map<String, String> paramsMap = Utils.getConfigurationParameters(tuninAlgoNode);

                TuninAlgorithmConfigurationData tuninAlgorithmData = new TuninAlgorithmConfigurationData(className, algoName, paramsMap);

                _tuninAlgorithmConfDataList.add(tuninAlgorithmData);

            }
        }
    }

}
