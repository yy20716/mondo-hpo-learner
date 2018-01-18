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

import org.apache.commons.io.FileUtils;
import org.apache.commons.text.CharacterPredicates;
import org.apache.commons.text.RandomStringGenerator;
import org.apache.jena.ontology.ObjectProperty;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
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
	private static String queryReplaceEquivClasses = ncitQueryPath + File.separator + "replaceEquiv.sparql";
	private static String querySelectSubClasses = ncitQueryPath + File.separator + "selectSubClasses.sparql";
	private static String querySelectSomeClasses = ncitQueryPath + File.separator + "selectSomeValuesFrom.sparql";
	private static String querySelectIntUniClasses = ncitQueryPath + File.separator + "selectIntersectUnionClasses.sparql";
	private static String querySelectOnProps = ncitQueryPath + File.separator + "selectOnProps.sparql";
	private static String querySelectEquivClasses = ncitQueryPath + File.separator + "selectEquiv.sparql";
	private static String querySelectDiseaseClasses= ncitQueryPath + File.separator + "selectDiseaseClasses.sparql";
	private static String querySelectContinuantClasses= ncitQueryPath + File.separator + "selectContinuantClasses.sparql";
	private static String querySelectPropertyClasses= ncitQueryPath + File.separator + "selectPropertyClasses.sparql";

	public Map<Resource, Resource> equivClassRsrcMap = Maps.newHashMap();
	public Map<Resource, Resource> onPropsMap = Maps.newHashMap();
	public Multimap<Resource, Resource> subClassRsrcMap = Multimaps.newSetMultimap(new LinkedHashMap<>(), HashSet::new);
	public Multimap<Resource, Resource> someClassRsrcMap = Multimaps.newSetMultimap(new LinkedHashMap<>(), HashSet::new);
	public Multimap<Resource, Resource> classIntUniClassRsrcMap = Multimaps.newSetMultimap(new LinkedHashMap<>(), HashSet::new);
	public Set<String> diseaseCuries = Sets.newHashSet();
	public Set<Resource> propSet = Sets.newHashSet();
	public Set<Resource> contSet = Sets.newHashSet();

	public OntModel ontoModel = ModelFactory.createOntologyModel();
	public ObjectProperty dummyObjProp = ontoModel.createObjectProperty("http://a.com/d");

	private RandomStringGenerator randomStringGenerator =
			new RandomStringGenerator.Builder()
			.withinRange('0', 'z')
			.filteredBy(CharacterPredicates.LETTERS, CharacterPredicates.DIGITS)
			.build();

	public NCITPreprocessor() {
		super();
	}

	// visitSuperClassNode recursively follows paths that match "?diseaseclass rdfs:subClassOf+ ?a"	
	private void visitSuperClassNode(Model model, Resource aClass) {
		if (aClass.toString().contains("owl")) return;
		if (aClass.toString().contains("NCIT") != true) return;
		if (aClass.isAnon()) return;

		Resource b1 = generateDummyResource(model, aClass, false);
		classEqEntityMap.put(curieUtil.getCurie(aClass.getURI()).get(), b1.getURI());
		model.add(b1, RDF.type, aClass);

		Collection<Resource> superClasses = subClassRsrcMap.get(aClass);
		if (superClasses.isEmpty()) return;

		for (Resource superClass : superClasses) {
			if (superClass.isAnon()) {
				visitIntUniClassNode(model, aClass, superClass, superClass);
			} else {
				classEqEntityMap.put(curieUtil.getCurie(superClass.getURI()).get(), b1.getURI());
			}
		}
	}

	//  visitIntUniClassNode recursively follows paths that match ?a owl:intersectionOf/rdf:rest*/rdf:first/owl:unionOf* ?b
	private void visitIntUniClassNode(Model model, Resource aClass, Resource bClass, Resource beginClass) {
		if (bClass.isURIResource()) return;
		Collection<Resource> intUniClasses = classIntUniClassRsrcMap.get(bClass);
		if (intUniClasses.isEmpty()) {
			visitSomeClassNode(model, aClass, bClass, beginClass);	
			return;
		}

		for (Resource intUniClass : intUniClasses) {
			visitIntUniClassNode(model, bClass, intUniClass, beginClass);
		}
	}

	// visitSomeClassNode recursively follows paths that match ?b owl:someValuesFrom ?relevantentity 
	private void visitSomeClassNode(Model model, Resource aClass, Resource bClass, Resource beginClass) {
		Collection<Resource> someClasses = someClassRsrcMap.get(bClass);
		if (someClasses.isEmpty()) {
			/* If this (bClass) is a meaningless common property used in NCIT, we skip it so that DL-Learner can avoid learning them. */
			if (propSet.contains(bClass)) return;

			Resource equivBeginClass = equivClassRsrcMap.get(beginClass);
			if (equivBeginClass == null) return;

			Resource b1 = generateDummyResource(model, equivBeginClass, false);
			model.add(b1, RDF.type, equivBeginClass);
			for (int i = 0; i < 50; i ++) {
				Resource b2 = generateDummyResource(model, bClass, true);
				model.add(b2, RDF.type, bClass);

				Resource propRsrc = onPropsMap.get(aClass);
				if (propRsrc == null) return;
				Property onProp = ResourceFactory.createProperty(propRsrc.getURI());
				model.add(b1, onProp, b2);	
			}

			return;
		}

		for (Resource someClass: someClasses) {
			visitSomeClassNode(model, bClass, someClass, beginClass);
		}
	}

	@Override
	public void run() {
		try {
			logger.info("Pre-processing NCIT begins ...");

			/* 1. select classes and equivalent ones from NCIT */ 
			ResultSet resultSet1 = queryExecutor.executeSelect(querySelectEquivClasses);
			while (resultSet1.hasNext()) {
				QuerySolution binding = resultSet1.nextSolution();
				Resource a = (Resource)binding.get("a");
				Resource b = (Resource)binding.get("b");
				if (a.toString().contains("owl") || b.toString().contains("owl")) continue;
				equivClassRsrcMap.put(b, a);
			}

			/* 2. replace all owl:equivalence property into rdfs:subclasses so that we can build up subclass hierarchies */ 
			queryExecutor.executeUpdate(queryReplaceEquivClasses);

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

			ResultSet resultSet7 = queryExecutor.executeSelect(querySelectContinuantClasses);
			while (resultSet7.hasNext()) {
				QuerySolution binding = resultSet7.nextSolution();
				Resource classRsrc = (Resource)binding.get("s");
				propSet.add(classRsrc);
			}

			ResultSet resultSet8 = queryExecutor.executeSelect(querySelectOnProps);
			while (resultSet8.hasNext()) {
				QuerySolution binding = resultSet8.nextSolution();
				Resource s = (Resource)binding.get("s");
				Resource o = (Resource)binding.get("o");
				onPropsMap.put(s, o);
			}

			/*
			queryExecutor.commonModel.removeAll(null, ResourceFactory.createProperty("http://www.w3.org/2002/07/owl#annotatedSource"), null);
			queryExecutor.commonModel.removeAll(null, ResourceFactory.createProperty("http://www.w3.org/2002/07/owl#annotatedProperty"), null);
			queryExecutor.commonModel.removeAll(null, ResourceFactory.createProperty("http://www.w3.org/2002/07/owl#annotatedTarget"), null);
			queryExecutor.commonModel.removeAll(null, ResourceFactory.createProperty("http://www.geneontology.org/formats/oboInOwl#external_class"), null);
			queryExecutor.commonModel.removeAll(null, ResourceFactory.createProperty("http://www.geneontology.org/formats/oboInOwl#ontology"), null);
			queryExecutor.commonModel.removeAll(null, ResourceFactory.createProperty("http://www.geneontology.org/formats/oboInOwl#source"), null);
			queryExecutor.commonModel.removeAll(null, ResourceFactory.createProperty("http://www.w3.org/2000/01/rdf-schema#label"), null);
			queryExecutor.commonModel.removeAll(null, ResourceFactory.createProperty("http://www.geneontology.org/formats/oboInOwl#hasExactSynonym"), null);
			queryExecutor.commonModel.removeAll(null, ResourceFactory.createProperty("http://www.geneontology.org/formats/oboInOwl#hasSynonymType"), null);
			queryExecutor.commonModel.removeAll(null, ResourceFactory.createProperty("http://www.geneontology.org/formats/oboInOwl#hasDbXref"), null);
			queryExecutor.commonModel.removeAll(null, null, ResourceFactory.createResource("http://www.w3.org/2002/07/owl#Axiom"));
			queryExecutor.commonModel.removeAll(null, ResourceFactory.createProperty("http://www.geneontology.org/formats/oboInOwl#date_retrieved"), null);
			queryExecutor.commonModel.removeAll(null, ResourceFactory.createProperty("http://www.geneontology.org/formats/oboInOwl#inSubset"), null); 
			queryExecutor.commonModel.removeAll(null, ResourceFactory.createProperty("http://www.geneontology.org/formats/oboInOwl#notes"), null);
			queryExecutor.commonModel.removeAll(null, ResourceFactory.createProperty("http://purl.obolibrary.org/obo/IAO_0000115"), null);

			for (Resource classRsrc: subClassRsrcMap.keySet()) {
				if (contSet.contains(classRsrc) != true) continue;
				queryExecutor.commonModel.removeAll(classRsrc, null, null);
				queryExecutor.commonModel.removeAll(null, null, classRsrc);
				Collection<Resource> superClassRsrcs = subClassRsrcMap.get(classRsrc);
				for (Resource superClassRsrc: superClassRsrcs) {
					if (superClassRsrc.isAnon()) continue;
					queryExecutor.commonModel.removeAll(superClassRsrc, null, null);
					queryExecutor.commonModel.removeAll(null, null, superClassRsrc);
				}
			}
			 */

			/*
			for (Resource classRsrc: subClassRsrcMap.keySet()) {
				Optional<String> optBClass = curieUtil.getCurie(classRsrc.toString());
				if (optBClass.isPresent())
					if (diseaseCuries.contains(optBClass.get()) != true) continue;
				if (classRsrc.isAnon()) continue;

				Collection<Resource> superClassRsrcs = subClassRsrcMap.get(classRsrc);
				for (Resource superClassRsrc: superClassRsrcs) {
					if (superClassRsrc.isAnon()) continue;
					queryExecutor.commonModel.removeAll(classRsrc, RDFS.subClassOf, superClassRsrc);
				}
			}
			 */

			/* 8. recursively follow property paths for finding relevant entries from given disease entities */
			for (Resource classRsrc: subClassRsrcMap.keySet()) 
				visitSuperClassNode(ontoModel, classRsrc);

			logger.info("classSubClassMap.size:" + subClassRsrcMap.size());
			logger.info("classSomeClassRsrcMa.size:" + someClassRsrcMap.size());
			logger.info("classIntUniClassRsrcMap.size:" + classIntUniClassRsrcMap.size());
			logger.info("classEqEntityMap.size:" + classEqEntityMap.size());

			for (String eachClass : classEqEntityMap.keySet()) {
				if  (eachClass.contains("C3212") !=true) continue;
				Collection<String> eqEntitySet = classEqEntityMap.get(eachClass);
				for (String eqEntity: eqEntitySet) {
					classParamMap.put(eachClass, new OWLNamedIndividualImpl(IRI.create(eqEntity)));
				}
			}
			/* Simple testing codes: C3402 is the superclass of C6532, i.e. its parameter needs to include every entities in the parameter of C6532. */
			for (String eachClass : classParamMap.keySet()) {
				logger.info(eachClass + "," + classParamMap.get(eachClass));					
			}

			ontoModel.add(queryExecutor.commonModel);

			File file = new File(Processor.hpofilewithAbox);
			FileUtils.deleteQuietly(file);
			OutputStream output = new FileOutputStream(file);
			ontoModel.write(output, "RDF/XML");
			output.close();

			logger.info("Pre-processing NCIT is finished ...");
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
	}

	/* Literally generate dummy resource from given resource (i.e. URI or blank node) 
	 * Reasoner or DL-learner often ignore resources or classes represented as blank nodes, so we create "dummy" URI nodes If needed
	 **/
	private Resource generateDummyResource(Model model, Resource aClass, boolean addRandomStr) {
		Resource b1 = null;
		if (aClass.isAnon()) {
			if (addRandomStr)
				b1 = model.createResource("http://a.com/" +  aClass.getId().toString().replace("-", "") + randomStringGenerator.generate(12));
			else
				b1 = model.createResource("http://a.com/" +  aClass.getId().toString().replace("-", ""));
		} else if (aClass.isURIResource()) {
			String[] classIRIArr = curieUtil.getCurie(aClass.toString()).get().split(":");
			if (addRandomStr)
				b1 = model.createResource("http://a.com/" + classIRIArr[0] + classIRIArr[1] + randomStringGenerator.generate(12));
			else
				b1 = model.createResource("http://a.com/" + classIRIArr[0] + classIRIArr[1]);
		}
		return b1;
	}
}