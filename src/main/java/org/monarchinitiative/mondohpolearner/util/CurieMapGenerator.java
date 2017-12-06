package org.monarchinitiative.mondohpolearner.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Map;

import org.apache.log4j.Logger;
import org.monarchinitiative.mondohpolearner.mondo.MondoPreprocessor;
import org.yaml.snakeyaml.Yaml;

/* read curie_map.yaml and pur the k-v entries into curieMap, which will be used as a parameter of */
public class CurieMapGenerator {
	private static final Logger logger = Logger.getLogger(MondoPreprocessor.class.getName());
	
	@SuppressWarnings("unchecked")
	public Map<String, String> generate() {
		try {
			File oboJSONMappingFile = new File("src/main/resources/org/monarchinitiative/mondohpolearner/curie_map.yaml");
			InputStream inputStream = new FileInputStream(oboJSONMappingFile.getAbsolutePath());
			Yaml yaml = new Yaml();
			Map<String, String> curieMap = (Map<String, String>) yaml.load(inputStream);
			return curieMap;
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return null;
	}
}
