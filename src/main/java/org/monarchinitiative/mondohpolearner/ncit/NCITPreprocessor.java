package org.monarchinitiative.mondohpolearner.ncit;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.OutputStream;
import java.util.Collection;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
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
			Model modelWithAbox = ModelFactory.createDefaultModel();
			
			while (resultSet.hasNext()) {
				QuerySolution binding = resultSet.nextSolution();
				/* logger.info(binding); */

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
				Resource b1 = modelWithAbox.createResource("http://purl.obolibrary.org/obo/NCIT_" + UUID.randomUUID().toString().replace("-", ""));
				classEqEntityMap.put(intClassRsrcIRI, subClassRsrcIRI);
				modelWithAbox.add(b1, RDF.type, subClassRsrc);

				if (binding.getResource("someclass") != null) {
					Resource someClassRsrc = (Resource)binding.get("someclass");
					Resource b2 = modelWithAbox.createResource("http://purl.obolibrary.org/obo/NCIT_" + UUID.randomUUID().toString().replace("-", ""));
					Property dummyProp;

					if (intClassRsrc.isAnon()) {
						dummyProp = ResourceFactory.createProperty("http://purl.obolibrary.org/obo/NCIT_" + intClassRsrc.toString().replace("-", ""));
					} else {
						dummyProp = ResourceFactory.createProperty(intClassRsrc.getURI());
					}

					modelWithAbox.add(b1, dummyProp, b2);
					modelWithAbox.add(b2, RDF.type, someClassRsrc);
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

			modelWithAbox.add(model);
			File file = new File(Processor.hpofilewithAbox);
			FileUtils.deleteQuietly(file);
			OutputStream output = new FileOutputStream(file);
			modelWithAbox.write(output, "RDF/XML");
			output.close();

			logger.info("Pre-processing NCIT is finished ...");
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
	}	
}