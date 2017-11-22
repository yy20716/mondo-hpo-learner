package org.monarchinitiative.mondohpolearner.doid;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.util.FileManager;
import org.apache.jena.vocabulary.RDF;
import org.apache.log4j.Logger;
import org.monarchinitiative.mondohpolearner.util.QueryExecutor;
import org.prefixcommons.CurieUtil;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLIndividual;

import com.google.common.collect.Multimap;
import com.univocity.parsers.tsv.TsvParser;
import com.univocity.parsers.tsv.TsvParserSettings;

import uk.ac.manchester.cs.owl.owlapi.OWLNamedIndividualImpl;

public class DoidPreprocessor {
	private static final Logger logger = Logger.getLogger(DoidPreprocessor.class.getName());

	private Multimap<String, OWLIndividual> classParamMap;
	private Multimap<String, String> classEqEntityMap;	
	private Multimap<String, String> classSubClassMap;
	private CurieUtil curieUtil;

	public DoidPreprocessor(Multimap<String, OWLIndividual> classParamMap, Multimap<String, String> classEqEntityMap,
			Multimap<String, String> classSubClassMap) {
		super();
		this.classParamMap = classParamMap;
		this.classEqEntityMap = classEqEntityMap;
		this.classSubClassMap = classSubClassMap;
	}

	public void setCurieUtil(CurieUtil curieUtil) {
		this.curieUtil = curieUtil;
	}

	public void run() {
		computeClassMappings();
		generateConfParam();
		generateAbox();
	}
	
	private void generateAbox() {
		try {
			logger.info("Generating aboxes for hp.owl...");
			File file = new File(DoidProcessor.hpofilewithAbox);
			if(file.exists() && !file.isDirectory()) {
				logger.info("The combined file already exists, so we skip generating the abox..");
				return;
			}
			
			Set<String> equivClassSet = new HashSet<String>(classEqEntityMap.values());
			Model model = FileManager.get().loadModel("hp.owl");
			InputStream inputStream = new FileInputStream("phenotype_annotation.tab");
			Reader inputStreamReader = new InputStreamReader(inputStream);
			TsvParserSettings settings = new TsvParserSettings();
			settings.selectIndexes(4, 5);
			TsvParser parser = new TsvParser(settings);
			List<String[]> allRows = parser.parseAll(inputStreamReader);
			for (String[] eachRow : allRows) {
				if (eachRow[0] == null || eachRow[1] == null) {
					continue;
				}

				if (equivClassSet.contains(eachRow[1].trim())) {
					if (eachRow[1].contains("ORPHA"))
						eachRow[1] = 	eachRow[1].replace("ORPHA", "Orphanet");

					Resource subj = ResourceFactory.createResource(curieUtil.getIri(eachRow[1].trim()).get());
					Resource obj = ResourceFactory.createResource(curieUtil.getIri(eachRow[0].trim()).get());
					model.add (subj, RDF.type, obj);
				}
			}

			inputStreamReader.close();

			FileUtils.deleteQuietly(file);
			FileWriter out = new FileWriter(file);
			model.write(out, "RDF/XML");
		} catch (Exception e) {
			logger.error(e.getMessage());
		}
	}

	private void generateConfParam() {
		logger.info("Generating parameters for DL-learners...");
		for (String eachClass : classEqEntityMap.keySet()) {
			Collection<String> eqEntitySet = classEqEntityMap.get(eachClass);

			for (String eqEntity: eqEntitySet) {
				if (eqEntity.contains("ORPHA"))
					eqEntity = eqEntity.replace("ORPHA", "Orphanet");
				String eqEntityIRI = curieUtil.getIri(eqEntity).get();
				classParamMap.put(eachClass, new OWLNamedIndividualImpl(IRI.create(eqEntityIRI)));
			}
		}
	}

	private void computeClassMappings() {
		logger.info("Computing all subclasses and equivalent entities from " + DoidProcessor.inputOWLFile + "...");
		ResultSet resultSet = QueryExecutor.executeOnce (DoidProcessor.inputOWLFile, DoidProcessor.queryExtractSubclasses);

		while (resultSet.hasNext()) {
			QuerySolution binding = resultSet.nextSolution();

			Resource classRsrc = (Resource)binding.get("class");
			Resource subClassRsrc = (Resource)binding.get("subclass");
			Literal source = (Literal) binding.get("source");

			String classRsrcIRI = curieUtil.getCurie(classRsrc.getURI()).get();
			String subClassRsrcIRI = curieUtil.getCurie(subClassRsrc.getURI()).get();
			String sourceIRI = source.getString();

			classSubClassMap.put(classRsrcIRI, subClassRsrcIRI);
			if (sourceIRI.contains("ORDO"))
				sourceIRI = 	sourceIRI.replace("ORDO", "ORPHA");
			classEqEntityMap.put(classRsrcIRI, sourceIRI);
		}
	}
}