package org.monarchinitiative.mondohpolearner.ncit;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.log4j.Logger;
import org.monarchinitiative.mondohpolearner.common.Preprocessor;
import org.monarchinitiative.mondohpolearner.common.Processor;
import org.semanticweb.owlapi.model.IRI;

import com.google.common.collect.Maps;

import uk.ac.manchester.cs.owl.owlapi.OWLNamedIndividualImpl;

public class NCITPreprocessor extends Preprocessor {
	private static final Logger logger = Logger.getLogger(NCITPreprocessor.class.getName()); 
	private static String querySelectEquivClasses = "src/main/resources/org/monarchinitiative/mondohpolearner/ncit/selectEquiv.sparql";
	private static String queryReplaceEquivClasses = "src/main/resources/org/monarchinitiative/mondohpolearner/ncit/replaceEquiv.sparql";
	private static String querySelectSubClassesWithSomeValues = "src/main/resources/org/monarchinitiative/mondohpolearner/ncit/extractSubclassesWithSomeValues.sparql";

	public Map<String, String> classEquivClassMap = Maps.newHashMap();

	public NCITPreprocessor() {
		super();
	}

	@Override
	public void run() {
		try {
			logger.info("Pre-processing NCIT begins ...");
			Model model = queryExecutor.commonModel;

			queryExecutor.executeUpdate(queryReplaceEquivClasses);
			ResultSet resultSet2 = queryExecutor.executeSelect(querySelectEquivClasses);
			while (resultSet2.hasNext()) {
				QuerySolution binding = resultSet2.nextSolution();
				/* logger.info(binding); */

				String c1 = curieUtil.getCurie(((Resource)binding.get("s")).getURI()).get();
				String c2 = ((Resource)binding.get("o")).getId().toString();
				classEquivClassMap.put(c2, c1);
			}

			ResultSet resultSet1 = queryExecutor.executeSelect(querySelectSubClassesWithSomeValues);
			Model modelWithAbox = ModelFactory.createDefaultModel();
			while (resultSet1.hasNext()) {
				QuerySolution binding = resultSet1.nextSolution();

				// 1. Building mappings between classes and subclasses.
				Resource subClassRsrc = (Resource)binding.get("subclass");
				Resource intClassRsrc = (Resource)binding.get("intclass");
				Resource someClassRsrc = (Resource)binding.get("someclass");

				String intClassRsrcIRI;
				if (intClassRsrc.isAnon() != true)
					intClassRsrcIRI = curieUtil.getCurie(intClassRsrc.getURI()).get();
				else
					intClassRsrcIRI = intClassRsrc.getId().toString();				
				Optional<String> optSubClass = curieUtil.getCurie(subClassRsrc.getURI());
				if (optSubClass.isPresent() != true) continue;
				String subClassRsrcIRI = optSubClass.get();

				String someClassRsrcIRI = curieUtil.getCurie(someClassRsrc.getURI()).get();
				Resource b1 = modelWithAbox.createResource("http://a.com/" + subClassRsrcIRI.split(":")[1]);
				Resource b2 = modelWithAbox.createResource("http://a.com/" + someClassRsrcIRI.toString().split(":")[1]);				
				Property dummyProp = null;
				if (intClassRsrc.isAnon()) {
					dummyProp = ResourceFactory.createProperty("http://a.com/d");
				} else {
					dummyProp = ResourceFactory.createProperty(intClassRsrc.getURI());
				}

				if (b1.toString().contains("0001031")) continue;
				if (b2.toString().contains("0001031")) continue;
				
				modelWithAbox.add(b1, RDF.type, subClassRsrc);
				modelWithAbox.add(b1, dummyProp, b2);
				modelWithAbox.add(b2, RDF.type, someClassRsrc);

				String equivClassRsrcIRI = classEquivClassMap.get(intClassRsrcIRI);
				if (equivClassRsrcIRI == null) continue;

				classSubClassMap.put(equivClassRsrcIRI, subClassRsrcIRI);
				classEqEntityMap.put(equivClassRsrcIRI, b1.getURI());
				classEqEntityMap.put(equivClassRsrcIRI, b2.getURI());
			}

			logger.info("classSubClassMap.size:" + classSubClassMap.size());
			logger.info("classEqEntityMap.size:" + classEqEntityMap.size());
			logger.info("model size: " + modelWithAbox.size());

			for (String eachClass : classEqEntityMap.keySet()) {
				Collection<String> eqEntitySet = classEqEntityMap.get(eachClass);
				for (String eqEntity: eqEntitySet) {
					classParamMap.put(eachClass, new OWLNamedIndividualImpl(IRI.create(eqEntity)));
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