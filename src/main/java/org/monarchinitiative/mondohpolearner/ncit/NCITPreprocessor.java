package org.monarchinitiative.mondohpolearner.ncit;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.log4j.Logger;
import org.monarchinitiative.mondohpolearner.common.Preprocessor;
import org.monarchinitiative.mondohpolearner.common.Processor;
import org.semanticweb.owlapi.model.IRI;

import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;

import uk.ac.manchester.cs.owl.owlapi.OWLNamedIndividualImpl;

public class NCITPreprocessor extends Preprocessor {
	private static final Logger logger = Logger.getLogger(NCITPreprocessor.class.getName()); 
	private static String ncitQueryPath = "src/main/resources/org/monarchinitiative/mondohpolearner/ncit"; 
	private static String querySelectEquivClasses = ncitQueryPath + File.separator + "selectEquiv.sparql";
	private static String queryReplaceEquivClasses = ncitQueryPath + File.separator + "replaceEquiv.sparql";
	private static String querySelectSubClasses = ncitQueryPath + File.separator + "extractSubClasses.sparql";
	private static String querySelectSomeClasses = ncitQueryPath + File.separator + "extractSomeValuesFrom.sparql";
	private static String querySelectDiseaseClasses= ncitQueryPath + File.separator + "selectDiseaseClasses.sparql";
	private static String querySelectIntSomeClasses= ncitQueryPath + File.separator + "selectIntSomeClasses.sparql";
	private static String queryRemoveSubClassesSubj = ncitQueryPath + File.separator + "removeSubclassesSubj.sparql";
	private static String queryRemoveSubClassesObj = ncitQueryPath + File.separator + "removeSubclassesObj.sparql";

	public Map<Resource, Resource> classEquivClassRsrcMap = Maps.newHashMap();
	public Multimap<Resource, Resource> classSubClassRsrcMap = Multimaps.newSetMultimap(new LinkedHashMap<>(), HashSet::new);
	public Multimap<Resource, Resource> classSomeClassRsrcMap = Multimaps.newSetMultimap(new LinkedHashMap<>(), HashSet::new);
	public Set<Resource> subClassTrailSet = Sets.newHashSet();
	public Set<String> diseaseCuries = Sets.newHashSet();

	public NCITPreprocessor() {
		super();
	}

	private void visitSubClassNode(Model model, Resource classIRI) {
		subClassTrailSet.add(classIRI);
		Collection<Resource> subClassIRIs = classSubClassRsrcMap.get(classIRI);
		visitSomeClassNode(model, classIRI);

		Resource b1 = generateDummyResource(model, classIRI);
		/*
		if (classIRI.isAnon() != true) {
			if (classIRI.toString().contains("owl") != true)
				model.add(b1, RDF.type, classIRI);
		}
		*/
		if (subClassIRIs.isEmpty()) {
			return;
		}

		// Property dummyProp = ResourceFactory.createProperty("http://a.com/d0");

		for (Resource subClassIRI : subClassIRIs) {
			if (subClassTrailSet.contains(subClassIRI)) continue;
			Resource b2 = generateDummyResource(model, subClassIRI);
			if (classIRI.isAnon() != true && diseaseCuries.contains(curieUtil.getCurie(classIRI.toString()).get())) {
				if (diseaseCuries.contains(curieUtil.getCurie(subClassIRI.toString()).get())) {
					classEqEntityMap.put(curieUtil.getCurie(classIRI.getURI()).get(), b2.getURI());
				}
			}

			model.add(b1, OWL.sameAs, b2);
			// logger.info("1: <" + b1 + "> dummyProp <" + b2 + ">" );
			visitSubClassNode(model, subClassIRI);
		}
		
		if (classIRI.isAnon() != true)
			logger.info(classIRI + ", " + classEqEntityMap.get(curieUtil.getCurie(classIRI.getURI()).get()));
	}

	private void visitSomeClassNode(Model model, Resource classIRI) {
		Resource b1 = generateDummyResource(model, classIRI);
		// Property dummyProp = ResourceFactory.createProperty("http://a.com/d1");

		Collection<Resource> someClassIRIs = classSomeClassRsrcMap.get(classIRI);
		if (someClassIRIs.isEmpty()) return;

		for (Resource someClassIRI : someClassIRIs) {
			Resource b2 = generateDummyResource(model, someClassIRI);
			model.add(b1, OWL.sameAs, b2);
			// logger.info("<" + b1 + "> dummyProp <" + b2 + ">" );

			if (someClassIRI.isAnon() != true) {
				if (diseaseCuries.contains(curieUtil.getCurie(someClassIRI.toString()).get()) != true) {
					model.add(b2, RDF.type, someClassIRI);
					// logger.info("2: <" + b2 + "> rdf:type <" + someClassIRI + ">" );
				}
			}

			visitSubClassNode(model, someClassIRI);
		}
	}

	@Override
	public void run() {
		try {
			logger.info("Pre-processing NCIT begins ...");
			Model model = queryExecutor.commonModel;

			queryExecutor.executeUpdate(queryReplaceEquivClasses);

			ResultSet resultSet1 = queryExecutor.executeSelect(querySelectEquivClasses);
			while (resultSet1.hasNext()) {
				QuerySolution binding = resultSet1.nextSolution();
				Resource s = (Resource)binding.get("s");
				Resource o = (Resource)binding.get("o");
				classEquivClassRsrcMap.put(o, s);
			}

			ResultSet resultSet2 = queryExecutor.executeSelect(querySelectSubClasses);
			while (resultSet2.hasNext()) {
				QuerySolution binding = resultSet2.nextSolution();
				Resource subClassRsrc = (Resource)binding.get("subclass");
				Resource classRsrc = (Resource)binding.get("class");
				classSubClassRsrcMap.put(classRsrc, subClassRsrc);

				Optional<String> classRsrcOpt = curieUtil.getCurie(classRsrc.toString());
				Optional<String> subClassRsrcOpt = curieUtil.getCurie(subClassRsrc.toString());
				if (classRsrcOpt.isPresent() && subClassRsrcOpt.isPresent()) 
					classSubClassMap.put(classRsrcOpt.get(), subClassRsrcOpt.get());
			}

			ResultSet resultSet3 = queryExecutor.executeSelect(querySelectSomeClasses);
			while (resultSet3.hasNext()) {
				QuerySolution binding = resultSet3.nextSolution();
				Resource someClassRsrc = (Resource)binding.get("someclass");
				Resource classRsrc = (Resource)binding.get("class");
				classSomeClassRsrcMap.put(classRsrc, someClassRsrc);
				// classSomeClassRsrcMap.put(someClassRsrc, classRsrc);
			}

			ResultSet resultSet4 = queryExecutor.executeSelect(querySelectDiseaseClasses);
			while (resultSet4.hasNext()) {
				QuerySolution binding = resultSet4.nextSolution();
				Resource dClassRsrc = (Resource)binding.get("dsubclass");
				diseaseCuries.add(curieUtil.getCurie(dClassRsrc.toString()).get());
			}

			ResultSet resultSet5 = queryExecutor.executeSelect(querySelectIntSomeClasses);
			// Property dummyProp = ResourceFactory.createProperty("http://a.com/d2");
			while (resultSet5.hasNext()) {
				QuerySolution binding = resultSet5.nextSolution();
				Resource classRsrc = (Resource)binding.get("class");
				Resource someClassRsrc = (Resource)binding.get("someclass");

				Resource b1 = generateDummyResource(model, classRsrc);
				Resource b2 = generateDummyResource(model, someClassRsrc);
				if (classRsrc.isAnon() != true && classRsrc.toString().contains("owl") != true)
					model.add(b1, RDF.type, classRsrc);
				
				model.add(b1, OWL.sameAs, b2);
				// logger.info("3: <" + b1 + "> dummyProp2 <" + b2 + ">" );
				if (someClassRsrc.isAnon() != true ) {
					if (diseaseCuries.contains(curieUtil.getCurie(someClassRsrc.toString()).get()) != true) {
						model.add(b2, RDF.type, someClassRsrc);
						// logger.info("4: <" + b2 + "> rdf:type <" + someClassRsrc + ">" );
					}
				}
			}

			for (Resource classIRI: classSubClassRsrcMap.keySet()) {
				visitSubClassNode(model, classIRI);
			}

			/*
			queryExecutor.executeUpdate(queryRemoveSubClassesSubj);
			queryExecutor.executeUpdate(queryRemoveSubClassesObj);
			 */

			logger.info("classSubClassMap.size:" + classSubClassRsrcMap.size());
			logger.info("classEqEntityMap.size:" + classEqEntityMap.size());

			for (String eachClass : classEqEntityMap.keySet()) {
				Collection<String> eqEntitySet = classEqEntityMap.get(eachClass);
				for (String eqEntity: eqEntitySet) {
					classParamMap.put(eachClass, new OWLNamedIndividualImpl(IRI.create(eqEntity)));
				}
			}

			/*
			for (String eachClass: classParamMap.keySet()) {
				logger.info(eachClass + " : " + classParamMap.get(eachClass));
			}
			 */

			File file = new File(Processor.hpofilewithAbox);
			FileUtils.deleteQuietly(file);
			OutputStream output = new FileOutputStream(file);
			model.write(output, "RDF/XML");
			output.close();

			logger.info("Pre-processing NCIT is finished ...");
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
	}	

	private Resource generateDummyResource(Model model, Resource classIRI) {
		Resource b1 = null;
		if (classIRI.isAnon()) {
			b1 = model.createResource("http://a.com/" +  classIRI.getId().toString().replace("-", ""));
		} else if (classIRI.isURIResource()) {
			String[] classIRIArr = curieUtil.getCurie(classIRI.toString()).get().split(":");
			b1 = model.createResource("http://a.com/" + classIRIArr[0] + classIRIArr[1]);
		}
		return b1;
	}
}