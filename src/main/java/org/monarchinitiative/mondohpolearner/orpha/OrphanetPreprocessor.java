package org.monarchinitiative.mondohpolearner.orpha;

import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Resource;
import org.apache.log4j.Logger;
import org.monarchinitiative.mondohpolearner.common.Preprocessor;
import org.monarchinitiative.mondohpolearner.common.Processor;
import org.monarchinitiative.mondohpolearner.util.QueryExecutor;
import org.semanticweb.owlapi.model.OWLIndividual;

import com.google.common.collect.Multimap;

public class OrphanetPreprocessor extends Preprocessor {
	private static final Logger logger = Logger.getLogger(OrphanetPreprocessor.class.getName()); 

	public OrphanetPreprocessor() {
		super();
	}
	
	@Override
	protected void computeClassMappings() {
		logger.info("Computing all subclasses and equivalent entities from " + Processor.inputOWLFile + "...");
		ResultSet resultSet = QueryExecutor.executeSelectOnce (Processor.inputOWLFile, Processor.queryExtractSubclasses);

		while (resultSet.hasNext()) {
			QuerySolution binding = resultSet.nextSolution();
			
			Resource classRsrc = (Resource)binding.get("class");
			Resource subClassRsrc = (Resource)binding.get("subclass");
			Literal source = (Literal) binding.get("source");

			String classRsrcIRI = curieUtil.getCurie(classRsrc.getURI()).get();
			String subClassRsrcIRI = curieUtil.getCurie(subClassRsrc.getURI()).get();
			String sourceIRI = source.getString();

			classSubClassMap.put(classRsrcIRI, subClassRsrcIRI);
			
			if (sourceIRI.contains("Orphanet"))
				sourceIRI = 	sourceIRI.replace("Orphanet", "ORPHA");
			classEqEntityMap.put(classRsrcIRI, sourceIRI);
			
			if (subClassRsrcIRI.contains("Orphanet"))
				subClassRsrcIRI= subClassRsrcIRI.replace("Orphanet", "ORPHA");
			classEqEntityMap.put(classRsrcIRI, subClassRsrcIRI);
		}
	}
}
