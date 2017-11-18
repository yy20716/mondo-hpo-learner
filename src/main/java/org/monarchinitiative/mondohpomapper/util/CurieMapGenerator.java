package org.monarchinitiative.mondohpomapper.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Map;

import org.apache.log4j.Logger;
import org.monarchinitiative.mondohpomapper.Preprocessor;
import org.yaml.snakeyaml.Yaml;

public class CurieMapGenerator {
	private static final Logger logger = Logger.getLogger(Preprocessor.class.getName());
	
	@SuppressWarnings("unchecked")
	public Map<String, String> generate() {
		try {
			File oboJSONMappingFile = new File("src/main/resources/curie_map.yaml");
			InputStream inputStream = new FileInputStream(oboJSONMappingFile.getAbsolutePath());
			Yaml yaml = new Yaml();
			Map<String, String> curieMap = (Map<String, String>) yaml.load(inputStream);
			return curieMap;
		} catch (Exception e) {
			logger.error(e.getMessage());
		}
		return null;
	}
}
