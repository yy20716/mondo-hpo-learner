package org.monarchinitiative.mondohpolearner.ncit;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.OutputStream;
import java.util.Collection;

import org.apache.commons.io.FileUtils;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.log4j.Logger;
import org.monarchinitiative.mondohpolearner.common.Preprocessor;
import org.monarchinitiative.mondohpolearner.common.Processor;
import org.monarchinitiative.mondohpolearner.util.QueryExecutor;
import org.semanticweb.owlapi.model.IRI;

import uk.ac.manchester.cs.owl.owlapi.OWLNamedIndividualImpl;

public class NCITPreprocessor extends Preprocessor {
	private static final Logger logger = Logger.getLogger(NCITPreprocessor.class.getName()); 
	private static String queryReplaceEquivClasses = "src/main/resources/org/monarchinitiative/mondohpolearner/ncit/replaceEquiv.sparql";
	private static String querySelectSubClassesWithSomeValues = "src/main/resources/org/monarchinitiative/mondohpolearner/ncit/extractSubclassesWithSomeValues.sparql";

	public NCITPreprocessor() {
		super();
	}

	@Override
	public void run() {
		try {
			logger.info("Pre-processing NCIT begins ...");
			Model model = queryExecutor.commonModel;
			queryExecutor.executeUpdate(queryReplaceEquivClasses);
			ResultSet resultSet = queryExecutor.executeSelect(querySelectSubClassesWithSomeValues);
			
			while (resultSet.hasNext()) {
				QuerySolution binding = resultSet.nextSolution();
				logger.info(binding);
				
				// 1. Building mappings between classes and subclasses.
				Resource subClassRsrc = (Resource)binding.get("subclass");
				Resource intClassRsrc = (Resource)binding.get("intclass");
				String intClassRsrcIRI;
				if (intClassRsrc.isAnon() != true)
					intClassRsrcIRI = curieUtil.getCurie(intClassRsrc.getURI()).get();
				else
					intClassRsrcIRI = intClassRsrc.getId().toString();
				String subClassRsrcIRI = curieUtil.getCurie(subClassRsrc.getURI()).get();
				classSubClassMap.put(intClassRsrcIRI, subClassRsrcIRI);
				
				// 2. Building mappings between classes and its leaf nodes
				Resource b1 = model.createResource();
				Triple t1 = Triple.create(b1.asNode(), RDF.type.asNode(), subClassRsrc.asNode()); 
				classEqEntityMap.put(intClassRsrcIRI, subClassRsrcIRI);
				
				model.getGraph().add(t1);
				// logger.info("t1: " + t1);
				
				if (binding.getResource("someclass") != null) {
					Resource someClassRsrc = (Resource)binding.get("someclass");
					Resource b2 = model.createResource();
					Triple t2; 
					
					if (intClassRsrc.isAnon()) {
						Node dummyProp = NodeFactory.createURI("http://purl.obolibrary.org/obo/NCIT_" + intClassRsrc.toString().replace("-", ""));
						t2 = Triple.create(b1.asNode(), dummyProp, b2.asNode());
					} else {
						t2 = Triple.create(b1.asNode(), intClassRsrc.asNode(), b2.asNode());
					}
					 
					Triple t3 = Triple.create(b2.asNode(), RDF.type.asNode(), someClassRsrc.asNode());
					model.getGraph().add(t2);
					model.getGraph().add(t3);
					// logger.info("t2: " + t2);
					// logger.info("t3: " + t3);
					
					classEqEntityMap.put(intClassRsrcIRI, subClassRsrcIRI);
				}
			}
			
			for (String eachClass : classEqEntityMap.keySet()) {
				Collection<String> eqEntitySet = classEqEntityMap.get(eachClass);
				for (String eqEntity: eqEntitySet) {
					String eqEntityIRI = curieUtil.getIri(eqEntity).get();
					classParamMap.put(eachClass, new OWLNamedIndividualImpl(IRI.create(eqEntityIRI)));
				}
			}
			
			File file = new File(Processor.hpofilewithAbox);
			FileUtils.deleteQuietly(file);
			FileWriter out = new FileWriter(file);
			model.write(out, "RDF/XML");

			out.close();
			logger.info("Pre-processing NCIT is finished ...");
			
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
	}	
}