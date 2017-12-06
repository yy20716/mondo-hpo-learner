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
import org.apache.jena.update.UpdateAction;
import org.apache.jena.util.FileManager;
import org.apache.log4j.Logger;

public class QueryExecutor {
	private static final Logger logger = Logger.getLogger(QueryExecutor.class.getName());
	public Model commonModel;

	public void loadModel(String dataFileName) {
		commonModel = FileManager.get().loadModel(dataFileName);
	}

	public void executeUpdate(String queryFileName) {
		UpdateAction.readExecute(queryFileName, commonModel);
	}

	public ResultSet executeSelect(String queryFileName) {
		try {
			File queryFile = new File(queryFileName);
			String queryString = FileUtils.readFileToString(queryFile, StandardCharsets.UTF_8);
			Query query = QueryFactory.create(queryString);
			QueryExecution qexec = QueryExecutionFactory.create(query, commonModel);
			return qexec.execSelect();
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}

		return null;
	}

	public static ResultSet executeSelectOnce(Model onetimeModel, String queryFileName) {
		try {
			File queryFile = new File(queryFileName);
			String queryString = FileUtils.readFileToString(queryFile, StandardCharsets.UTF_8);
			Query query = QueryFactory.create(queryString);
			QueryExecution qexec = QueryExecutionFactory.create(query, onetimeModel);
			return qexec.execSelect();
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}

		return null;
	}
	
	public static ResultSet executeSelectOnce(String dataFileName, String queryFileName) {
		try {
			Model onetimeModel = FileManager.get().loadModel(dataFileName);
			File queryFile = new File(queryFileName);
			String queryString = FileUtils.readFileToString(queryFile, StandardCharsets.UTF_8);
			Query query = QueryFactory.create(queryString);
			QueryExecution qexec = QueryExecutionFactory.create(query, onetimeModel);
			return qexec.execSelect();
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}

		return null;
	}
}