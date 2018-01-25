package org.monarchinitiative.mondohpolearner.ncit;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Resource;
import org.apache.log4j.Logger;
import org.monarchinitiative.mondohpolearner.util.QueryExecutor;

public class ExistentialsMaterializer {
	private static final Logger logger = Logger.getLogger(ExistentialsMaterializer.class.getName());
	private static String owltoolPath = "/home/yy20716/Workspace/owltools/OWLTools-Runner/bin/owltools";

	public String generateParams(QueryExecutor queryExecutor, String queryFilePath) {
		ResultSet resultSet = queryExecutor.executeSelect(queryFilePath);
		StringBuilder sb = new StringBuilder();
		while (resultSet.hasNext()) {
			QuerySolution binding = resultSet.nextSolution();
			// logger.info("binding: " + binding);

			Resource a = (Resource)binding.get("s");
			if (a.toString().contains("NCIT") != true) continue;
			if (a.isURIResource() != true) continue;
			sb.append(a.getURI() + " ");
		}

		return sb.toString();
	}

	public void callOwltoolForMaterialization(String inputOntologyPath, String outputOntologyPath, String propListStr) {
		Process p;
		String line;
		String[] params = new String [7];

		params[0] = owltoolPath;
		params[1] = System.getProperty("user.dir") + File.separator + inputOntologyPath;
		params[2] = "--materialize-existentials"; 
		params[3] = "-l"; 
		params[4] = propListStr;
		params[5] = "-o";
		params[6] = System.getProperty("user.dir") + File.separator + outputOntologyPath;

		try {
			p = Runtime.getRuntime().exec(params);
			BufferedReader input = new BufferedReader (new InputStreamReader(p.getInputStream()));
			BufferedReader error = new BufferedReader (new InputStreamReader(p.getErrorStream()));

			while ((line = input.readLine()) != null)
				logger.info(line);

			while ((line = error.readLine()) != null)
				logger.info(line);

			input.close();
			error.close();

		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
	}
}
