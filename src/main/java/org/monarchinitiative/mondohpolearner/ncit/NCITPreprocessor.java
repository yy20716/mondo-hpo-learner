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
	private static String queryRemoveSubClassesWithSomeValues = ncitQueryPath + File.separator + "removeSubclassesWithSomeValues.sparql";

	public Map<Resource, Resource> classEquivClassMap = Maps.newHashMap();
	public Multimap<Resource, Resource> classSubClassMap = Multimaps.newSetMultimap(new LinkedHashMap<>(), HashSet::new);
	public Multimap<Resource, Resource> classSomeClassMap = Multimaps.newSetMultimap(new LinkedHashMap<>(), HashSet::new);
	public Set<Resource> subClassTrailSet = Sets.newHashSet();
	
	public NCITPreprocessor() {
		super();
	}

	private void visitSubClassNode(Model model, Resource classIRI) {
		subClassTrailSet.add(classIRI);
		Collection<Resource> subClassIRIs = classSubClassMap.get(classIRI);
		if (subClassIRIs.isEmpty()) {
			/* logger.info("visiting someValueFrom: " + classIRI + " , " + classIRI); */
			visitSomeClassNode(model, classIRI, classIRI);
			return;
		}

		for (Resource subClassIRI : subClassIRIs) {
			if (subClassTrailSet.contains(subClassIRI)) continue;
			/* logger.info("visiting subclass: " + classIRI + " , " + subClassIRI); */
			visitSubClassNode(model, subClassIRI);
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
	
	private void visitSomeClassNode(Model model, Resource classIRI, Resource beginClassIRI) {
		Collection<Resource> someClassIRIs = classSomeClassMap.get(classIRI);
		/* logger.info("classIRI: " + classIRI + " - " + someClassIRIs); */
		
		Resource b1 = generateDummyResource(model, classIRI);
		if (classIRI.isURIResource()) {
			/* reasoners say that no instance should be the instance of owl:Nothing */
			if (b1.toString().contains("owl")) return;
			model.add(b1, RDF.type, classIRI);
		}
		
		if (someClassIRIs.isEmpty()) {	
			/* logger.info("classIRI: " + classIRI + " - no children."); */
			return;
		}
		
		for (Resource someClassIRI : someClassIRIs) {
			visitSomeClassNode(model, someClassIRI, classIRI);
			
			Resource equivSomeClassIRI = classEquivClassMap.get(someClassIRI);
			if (equivSomeClassIRI == null) continue;
			
			Resource b2 = generateDummyResource(model, someClassIRI);
			Property dummyProp = ResourceFactory.createProperty("http://a.com/d" + b1.getURI() + b2.getURI());

			model.add(b2, RDF.type, equivSomeClassIRI);
			model.add(b1, dummyProp, b2);
			classEqEntityMap.put(curieUtil.getCurie(beginClassIRI.getURI()).get(), b2.getURI());
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
				classEquivClassMap.put(o, s);
			}

			ResultSet resultSet2 = queryExecutor.executeSelect(querySelectSubClasses);
			while (resultSet2.hasNext()) {
				QuerySolution binding = resultSet2.nextSolution();
				Resource subClassRsrc = (Resource)binding.get("subclass");
				Resource classRsrc = (Resource)binding.get("class");
				classSubClassMap.put(classRsrc, subClassRsrc);
			}

			ResultSet resultSet3 = queryExecutor.executeSelect(querySelectSomeClasses);
			while (resultSet3.hasNext()) {
				QuerySolution binding = resultSet3.nextSolution();
				Resource someClassRsrc = (Resource)binding.get("someclass");
				Resource classRsrc = (Resource)binding.get("class");
				classSomeClassMap.put(classRsrc, someClassRsrc);
			}

			for (Resource classIRI: classSubClassMap.keySet()) {
				visitSubClassNode(model, classIRI);
			}

			logger.info("classSubClassMap.size:" + classSubClassMap.size());
			logger.info("classEqEntityMap.size:" + classEqEntityMap.size());

			for (String eachClass : classEqEntityMap.keySet()) {
				Collection<String> eqEntitySet = classEqEntityMap.get(eachClass);
				/* logger.info("param: " + eachClass + " : " + eqEntitySet); */
				for (String eqEntity: eqEntitySet) {
					classParamMap.put(eachClass, new OWLNamedIndividualImpl(IRI.create(eqEntity)));
				}
			}

			/* queryExecutor.executeUpdate(queryRemoveSubClassesWithSomeValues); */
			
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
}