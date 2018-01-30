package org.monarchinitiative.mondohpolearner.mondogo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.util.FileManager;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.log4j.Logger;
import org.monarchinitiative.mondohpolearner.common.Preprocessor;
import org.monarchinitiative.mondohpolearner.common.Processor;
import org.monarchinitiative.mondohpolearner.util.QueryExecutor;
import org.semanticweb.owlapi.model.IRI;

import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.univocity.parsers.tsv.TsvParser;
import com.univocity.parsers.tsv.TsvParserSettings;

import uk.ac.manchester.cs.owl.owlapi.OWLNamedIndividualImpl;

public class MondoGoPreprocessor extends Preprocessor {
	private static final Logger logger = Logger.getLogger(MondoGoPreprocessor.class.getName());
	public Multimap<String, String> mondoClassSubClassMap = Multimaps.newSetMultimap(new LinkedHashMap<>(), HashSet::new);
	public Multimap<String, String> goClassSubClassMap = Multimaps.newSetMultimap(new LinkedHashMap<>(), HashSet::new);
	public Multimap<String, String> mondoGoMap = Multimaps.newSetMultimap(new LinkedHashMap<>(), HashSet::new);

	@Override
	public void run() {
		try {
			logger.info("Materializing subClass statements..");

			InputStream inputStream = new FileInputStream("mondo-leaves-to-go.tsv");
			Reader inputStreamReader = new InputStreamReader(inputStream);
			TsvParserSettings settings = new TsvParserSettings();
			settings.selectIndexes(1, 3);
			TsvParser parser = new TsvParser(settings);
			List<String[]> allRows = parser.parseAll(inputStreamReader);
			for (String[] eachRow : allRows) {
				if (eachRow[0] == null || eachRow[1] == null) continue;
				Optional<String> mondoOpt = curieUtil.getCurie(eachRow[0].trim());
				if (mondoOpt.isPresent() != true) continue;
				mondoGoMap.put(mondoOpt.get(), eachRow[1].trim());
			}

			logger.info("Computing subClass closures...");
			computeSubClassMapping(Processor.inputOWLFile,mondoClassSubClassMap);
			computeSubClassMapping("go.owl", goClassSubClassMap);
			Model goModel = FileManager.get().loadModel("go.owl");

			goModel.removeAll(null, ResourceFactory.createProperty("http://www.w3.org/2002/07/owl#annotatedSource"), null);
			goModel.removeAll(null, ResourceFactory.createProperty("http://www.w3.org/2002/07/owl#annotatedProperty"), null);
			goModel.removeAll(null, ResourceFactory.createProperty("http://www.w3.org/2002/07/owl#annotatedTarget"), null);
			goModel.removeAll(null, RDF.type, ResourceFactory.createResource("http://www.w3.org/2002/07/owl#Axiom"));
			goModel.removeAll(null, ResourceFactory.createProperty("http://www.geneontology.org/formats/oboInOwl#inSubset"), null);
			goModel.removeAll(null, ResourceFactory.createProperty("http://www.geneontology.org/formats/oboInOwl#id"), null);
			goModel.removeAll(null, ResourceFactory.createProperty("http://www.geneontology.org/formats/oboInOwl#external_class"), null);
			goModel.removeAll(null, ResourceFactory.createProperty("http://www.geneontology.org/formats/oboInOwl#ontology"), null);
			goModel.removeAll(null, ResourceFactory.createProperty("http://www.geneontology.org/formats/oboInOwl#source"), null);
			goModel.removeAll(null, ResourceFactory.createProperty("http://www.geneontology.org/formats/oboInOwl#hasExactSynonym"), null);
			goModel.removeAll(null, ResourceFactory.createProperty("http://www.geneontology.org/formats/oboInOwl#hasSynonymType"), null);
			goModel.removeAll(null, ResourceFactory.createProperty("http://www.geneontology.org/formats/oboInOwl#hasBroadSynonym"), null);
			goModel.removeAll(null, ResourceFactory.createProperty("http://www.geneontology.org/formats/oboInOwl#hasDbXref"), null);
			goModel.removeAll(null, ResourceFactory.createProperty("http://www.geneontology.org/formats/oboInOwl#date_retrieved"), null);
			goModel.removeAll(null, ResourceFactory.createProperty("http://www.geneontology.org/formats/oboInOwl#inSubset"), null); 
			goModel.removeAll(null, ResourceFactory.createProperty("http://www.geneontology.org/formats/oboInOwl#notes"), null);
			goModel.removeAll(null, ResourceFactory.createProperty("http://purl.obolibrary.org/obo/IAO_0000115"), null);
			goModel.removeAll(null, RDFS.comment, null);
			goModel.removeAll(null, RDFS.subPropertyOf, null);

			for (String mondoClass : mondoClassSubClassMap.keySet()) {
				if (mondoClass.contains("18466") != true) continue;
				
				Collection<String> mondoSubClasses = mondoClassSubClassMap.get(mondoClass); 
				classSubClassMap.putAll(mondoClass, mondoSubClasses);
				
				for (String mondoSubClass: mondoSubClasses) {
					Collection<String> goClasses = mondoGoMap.get(mondoSubClass);
					if (goClasses == null) continue;
		
					for (String goClass: goClasses) {
						Collection<String> goSubSubClasses = goClassSubClassMap.get(goClass);
						if (goSubSubClasses != null && goSubSubClasses.size() > 0) continue;
						
						String[] curieSplitArr = goClass.split(":");
						Resource dumSubRsrc = goModel.createResource("http://a.com/" + curieSplitArr[0] + curieSplitArr[1]);
						Resource dumSubClass = ResourceFactory.createResource(curieUtil.getIri(goClass).get());
						goModel.add(dumSubRsrc, RDF.type, dumSubClass);
						classEqEntityMap.put(mondoClass, dumSubRsrc.getURI());
						/*
						Collection<String> goSubClasses = goClassSubClassMap.get(goClass);

						for (String goSubClass: goSubClasses) {
							Collection<String> goSubSubClasses = goClassSubClassMap.get(goSubClass);
							if (goSubSubClasses != null && goSubSubClasses.size() > 0) continue;
							
							String[] curieSplitArr = goSubClass.split(":");
							Resource dumSubRsrc = goModel.createResource("http://a.com/" + curieSplitArr[0] + curieSplitArr[1]);
							Resource dumSubClass = ResourceFactory.createResource(curieUtil.getIri(goSubClass).get());
							goModel.add(dumSubRsrc, RDF.type, dumSubClass);
							classEqEntityMap.put(mondoClass, dumSubRsrc.getURI());
						}
						*/
					}
				}
			}
			
			for (String eachClass : classEqEntityMap.keySet()) {
				Collection<String> eqEntitySet = classEqEntityMap.get(eachClass);
				for (String eqEntity: eqEntitySet) {
					classParamMap.put(eachClass, new OWLNamedIndividualImpl(IRI.create(eqEntity)));
				}
			}

			for (String eachClass : classParamMap.keySet()) {
				logger.info(eachClass + "," + classParamMap.get(eachClass));
			}
			
			inputStreamReader.close();
			File file = new File(Processor.hpofilewithAbox);
			FileUtils.deleteQuietly(file);
			FileWriter out = new FileWriter(file);
			goModel.write(out, "RDF/XML");
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
	}

	protected void computeSubClassMapping(String ontologyPath, Multimap<String, String> classSubClassMap) {
		ResultSet resultSet = QueryExecutor.executeSelectOnce (ontologyPath, Processor.queryExtractSubclasses);
		while (resultSet.hasNext()) {
			QuerySolution binding = resultSet.nextSolution();

			Resource classRsrc = (Resource)binding.get("class");
			Resource subClassRsrc = (Resource)binding.get("subclass");

			if (classRsrc.isURIResource() != true) continue;
			if (subClassRsrc.isURIResource() != true) continue;

			Optional<String> classRsrcOpt = curieUtil.getCurie(classRsrc.getURI());
			Optional<String> subClassRsrcOpt = curieUtil.getCurie(subClassRsrc.getURI());

			if (classRsrcOpt.isPresent() != true) continue;
			if (subClassRsrcOpt.isPresent() != true) continue;

			String classRsrcIRI = classRsrcOpt.get();
			String subClassRsrcIRI = subClassRsrcOpt.get();
			classSubClassMap.put(classRsrcIRI, subClassRsrcIRI);
		}
	}
}