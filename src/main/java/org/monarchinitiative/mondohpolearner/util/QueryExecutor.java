package org.monarchinitiative.mondohpolearner.util;

import java.io.File;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.FileUtils;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.util.FileManager;
import org.apache.log4j.Logger;

public class QueryExecutor {
	private static final Logger logger = Logger.getLogger(QueryExecutor.class.getName());
	private Model commonModel;

	public void loadModel(String dataFileName) {
		commonModel = FileManager.get().loadModel(dataFileName);
	}

	public ResultSet execute(String queryFileName) {
		try {
			File queryFile = new File(queryFileName);
			String queryString = FileUtils.readFileToString(queryFile, StandardCharsets.UTF_8);
			Query query = QueryFactory.create(queryString);
			QueryExecution qexec = QueryExecutionFactory.create(query, commonModel);
			return qexec.execSelect();
		} catch (Exception e) {
			logger.error(e.getMessage());
		}

		return null;
	}

	public static ResultSet executeOnce(String dataFileName, String queryFileName) {
		try {
			Model onetimeModel = FileManager.get().loadModel(dataFileName);
			File queryFile = new File(queryFileName);
			String queryString = FileUtils.readFileToString(queryFile, StandardCharsets.UTF_8);
			Query query = QueryFactory.create(queryString);
			QueryExecution qexec = QueryExecutionFactory.create(query, onetimeModel);
			return qexec.execSelect();
		} catch (Exception e) {
			logger.error(e.getMessage());
		}

		return null;
	}
}