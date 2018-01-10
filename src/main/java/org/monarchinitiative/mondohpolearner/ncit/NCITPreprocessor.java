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
import org.prefixcommons.CurieUtil;
import org.semanticweb.owlapi.model.IRI;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;

import uk.ac.manchester.cs.owl.owlapi.OWLNamedIndividualImpl;

public class NCITPreprocessor extends Preprocessor {
	private static final Logger logger = Logger.getLogger(NCITPreprocessor.class.getName()); 
	
	private static String ncitQueryPath = "src/main/resources/org/monarchinitiative/mondohpolearner/ncit"; 
	private static String queryReplaceEquivClasses = ncitQueryPath + File.separator + "replaceEquiv.sparql";
	private static String querySelectSubClasses = ncitQueryPath + File.separator + "selectSubClasses.sparql";
	private static String querySelectSomeClasses = ncitQueryPath + File.separator + "selectSomeValuesFrom.sparql";
	private static String querySelectIntUniClasses = ncitQueryPath + File.separator + "selectIntersectUnionClasses.sparql";
	private static String querySelectEquivClasses = ncitQueryPath + File.separator + "selectEquiv.sparql";
	private static String querySelectDiseaseClasses= ncitQueryPath + File.separator + "selectDiseaseClasses.sparql";
	private static String querySelectPropertyClasses= ncitQueryPath + File.separator + "selectPropertyClasses.sparql";

	public Multimap<Resource, Resource> equivForwClassRsrcMap = Multimaps.newSetMultimap(new LinkedHashMap<>(), HashSet::new);
	public Multimap<Resource, Resource> equivBackClassRsrcMap = Multimaps.newSetMultimap(new LinkedHashMap<>(), HashSet::new);
	public Multimap<Resource, Resource> subClassRsrcMap = Multimaps.newSetMultimap(new LinkedHashMap<>(), HashSet::new);
	public Multimap<Resource, Resource> someClassRsrcMap = Multimaps.newSetMultimap(new LinkedHashMap<>(), HashSet::new);
	public Multimap<Resource, Resource> classIntUniClassRsrcMap = Multimaps.newSetMultimap(new LinkedHashMap<>(), HashSet::new);
	public Set<String> diseaseCuries = Sets.newHashSet();
	public Set<Resource> propSet = Sets.newHashSet();

	public NCITPreprocessor() {
		super();
	}

	// visitSuperClassNode recursively follows paths that match "?diseaseclass rdfs:subClassOf+ ?a"	
	private void visitSuperClassNode(Model model, Resource aClass) {
		if (aClass.toString().contains("owl")) return;
		if (aClass.toString().contains("NCIT") != true) return;

		Collection<Resource> superClasses = subClassRsrcMap.get(aClass);
		if (superClasses.isEmpty()) return;

		for (Resource superClassIRI : superClasses) {
			if (superClassIRI.isURIResource()) {
				Collection<Resource> equivBackClassRsrcs = equivBackClassRsrcMap.get(superClassIRI);
				for (Resource equivClassRsrc : equivBackClassRsrcs) {
					visitIntUniClassNode(model, aClass, equivClassRsrc, superClassIRI);
				}
			} else {
				Collection<Resource> equivForwClassRsrcs = equivForwClassRsrcMap.get(superClassIRI);
				for (Resource equivClassRsrc : equivForwClassRsrcs) {
					visitIntUniClassNode(model, aClass, superClassIRI, equivClassRsrc);
				}
			}
		}
	}

	//  visitIntUniClassNode recursively follows paths that match ?a owl:intersectionOf/rdf:rest*/rdf:first/owl:unionOf* ?b
	private void visitIntUniClassNode(Model model, Resource aClass, Resource bClass, Resource superClass) {
		if (bClass.isURIResource()) return;
		Collection<Resource> intUniClasses = classIntUniClassRsrcMap.get(bClass);
		if (intUniClasses.isEmpty()) {
			visitSomeClassNode(model, aClass, bClass, superClass);	
			return;
		}

		for (Resource intUniClass : intUniClasses) {
			visitIntUniClassNode(model, bClass, intUniClass, superClass);
		}
	}

	// sitSomeClassNode recursively follows paths that match ?b owl:someValuesFrom ?relevantentity 
	// and build the parameters for given superClass
	private void visitSomeClassNode(Model model, Resource aClass, Resource bClass, Resource superClass) {
		Collection<Resource> someClasses = someClassRsrcMap.get(bClass);
		if (someClasses.isEmpty()) {
			/* If this (bClass) is a meaningless common property used in NCIT, we skip it so that DL-Learner can avoid learning them. */
			if (propSet.contains(bClass)) return;

			/* If this (bClass) is a disease entity in NCIT, we skip it so that DL-Learner can avoid learning them. */
			Optional<String> optBClass = curieUtil.getCurie(bClass.toString());
			if (optBClass.isPresent())
				if (diseaseCuries.contains(optBClass.get())) return;

			Resource b1 = generateDummyResource(model, bClass);
			model.add(b1, RDF.type, bClass);
			classEqEntityMap.put(curieUtil.getCurie(superClass.getURI()).get(), b1.getURI());
			return;
		}

		for (Resource someClass: someClasses) {
			visitSomeClassNode(model, bClass, someClass, superClass);
		}
	}

	@Override
	public void run() {
		try {
			logger.info("Pre-processing NCIT begins ...");
			Model model = queryExecutor.commonModel;

			/* 1. replace all owl:equivalence property into rdfs:subclasses so that we can build up subclass hierarchies */ 
			queryExecutor.executeUpdate(queryReplaceEquivClasses);

			/* 2. select classes and equivalent (now subclasses) ones from NCIT */ 
			ResultSet resultSet1 = queryExecutor.executeSelect(querySelectEquivClasses);
			while (resultSet1.hasNext()) {
				QuerySolution binding = resultSet1.nextSolution();
				Resource a = (Resource)binding.get("a");
				Resource b = (Resource)binding.get("b");
				if (a.toString().contains("owl") || b.toString().contains("owl")) continue;

				equivForwClassRsrcMap.put(b, a);
				equivBackClassRsrcMap.put(a, b);
			}

			/* 3. compute the closure of subclass hierarchy */
			ResultSet resultSet2 = queryExecutor.executeSelect(querySelectSubClasses);
			while (resultSet2.hasNext()) {
				QuerySolution binding = resultSet2.nextSolution();
				Resource a = (Resource)binding.get("a");
				Resource b = (Resource)binding.get("b");

				/* We don't want to include owl-related entities such as owl:Nothing */
				if (a.toString().contains("owl") || b.toString().contains("owl")) continue;

				subClassRsrcMap.put(a, b);

				Optional<String> classRsrcOpt = curieUtil.getCurie(b.toString());
				Optional<String> subClassRsrcOpt = curieUtil.getCurie(a.toString());
				if (classRsrcOpt.isPresent() && subClassRsrcOpt.isPresent()) 
					classSubClassMap.put(classRsrcOpt.get(), subClassRsrcOpt.get());
			}

			/* 4. compute the closure of someValuesFrom hierarchy */
			ResultSet resultSet3 = queryExecutor.executeSelect(querySelectSomeClasses);
			while (resultSet3.hasNext()) {
				QuerySolution binding = resultSet3.nextSolution();				
				Resource a = (Resource)binding.get("a");
				Resource b = (Resource)binding.get("b");
				if (a.toString().contains("owl") || b.toString().contains("owl")) continue;

				someClassRsrcMap.put(a, b);
			}

			/* 5. compute the closure of diseases (later we use it to selectively process disease entities from NCIT) */
			ResultSet resultSet4 = queryExecutor.executeSelect(querySelectDiseaseClasses);
			while (resultSet4.hasNext()) {
				QuerySolution binding = resultSet4.nextSolution();
				Resource classRsrc = (Resource)binding.get("s");
				diseaseCuries.add(curieUtil.getCurie(classRsrc.toString()).get());
			}

			/* 6. compute the closure of properties (later we use it to avoid learning property entities from NCIT) */
			ResultSet resultSet5 = queryExecutor.executeSelect(querySelectPropertyClasses);
			while (resultSet5.hasNext()) {
				QuerySolution binding = resultSet5.nextSolution();
				Resource classRsrc = (Resource)binding.get("s");
				propSet.add(classRsrc);
			}

			/* 7. compute the closure of entities linked by intersection/first/rest */
			ResultSet resultSet6 = queryExecutor.executeSelect(querySelectIntUniClasses);
			while (resultSet6.hasNext()) {
				QuerySolution binding = resultSet6.nextSolution();
				Resource a = (Resource)binding.get("a");
				Resource b = (Resource)binding.get("b");
				if (a.toString().contains("owl") || b.toString().contains("owl")) continue;

				classIntUniClassRsrcMap.put(a, b);
			}

			/* 8. recursively follow property paths for finding relevant entries from given disease entities */
			for (Resource classRsrc: subClassRsrcMap.keySet()) 
				visitSuperClassNode(model, classRsrc);

			logger.info("classSubClassMap.size:" + subClassRsrcMap.size());
			logger.info("classSomeClassRsrcMa.size:" + someClassRsrcMap.size());
			logger.info("classIntUniClassRsrcMap.size:" + classIntUniClassRsrcMap.size());
			logger.info("classEqEntityMap.size:" + classEqEntityMap.size());

			/* 9. If disease A is the super class of some classes (e.g., B and C), the relevant entities for B, C are added into the entities for A. */ 
			for (String eachClass : classEqEntityMap.keySet()) {
				Collection<String> eqEntitySet = classEqEntityMap.get(eachClass);
				Resource eachClassRsrc = ResourceFactory.createResource(curieUtil.getIri(eachClass).get());
				Collection<Resource> superClassRsrcs = subClassRsrcMap.get(eachClassRsrc);
				superClassRsrcs.add(eachClassRsrc);
				for (Resource eachSuperClassRsrc: superClassRsrcs) {
					if (eachSuperClassRsrc.isAnon()) continue;
					for (String eqEntity: eqEntitySet) {
						classParamMap.put(curieUtil.getCurie(eachSuperClassRsrc.toString()).get(), new OWLNamedIndividualImpl(IRI.create(eqEntity)));
					}
				}
			}

			/* Simple testing codes: C3402 is the superclass of C6532, i.e. its parameter needs to include every entities in the parameter of C6532. */
			for (String eachClass : classParamMap.keySet()) {
				if (eachClass.contains("C3402"))
					logger.info(eachClass + "," + classParamMap.get(eachClass));
				if (eachClass.contains("C6532"))
					logger.info(eachClass + "," + classParamMap.get(eachClass));
			}
			
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

	/* Literally generate dummy resource from given resource (i.e. URI or blank node) 
	 * Reasoner or DL-learner often ignore resources or classes represented as blank nodes, so we create "dummy" URI nodes If needed
	 **/
	private Resource generateDummyResource(Model model, Resource aClass) {
		Resource b1 = null;
		if (aClass.isAnon()) {
			b1 = model.createResource("http://a.com/" +  aClass.getId().toString().replace("-", ""));
		} else if (aClass.isURIResource()) {
			String[] classIRIArr = curieUtil.getCurie(aClass.toString()).get().split(":");
			b1 = model.createResource("http://a.com/" + classIRIArr[0] + classIRIArr[1]);
		}
		return b1;
	}
}