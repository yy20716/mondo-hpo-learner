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
	private static String querySelectIntUniClasses = ncitQueryPath + File.separator + "extractIntersectUnionClasses.sparql";
	private static String querySelectDiseaseClasses= ncitQueryPath + File.separator + "selectDiseaseClasses.sparql";

	/*
	private static String querySelectIntSomeClasses= ncitQueryPath + File.separator + "selectIntSomeClasses.sparql";
	private static String queryRemoveSubClassesSubj = ncitQueryPath + File.separator + "removeSubclassesSubj.sparql";
	private static String queryRemoveSubClassesObj = ncitQueryPath + File.separator + "removeSubclassesObj.sparql";
	 */
	private static Property dummyProp = ResourceFactory.createProperty("http://a.com/d");

	public Map<Resource, Resource> equivClassRsrcMap = Maps.newHashMap();
	public Multimap<Resource, Resource> subClassRsrcMap = Multimaps.newSetMultimap(new LinkedHashMap<>(), HashSet::new);
	public Multimap<Resource, Resource> someClassRsrcMap = Multimaps.newSetMultimap(new LinkedHashMap<>(), HashSet::new);
	public Multimap<Resource, Resource> classIntUniClassRsrcMap = Multimaps.newSetMultimap(new LinkedHashMap<>(), HashSet::new);
	public Set<Resource> superClassTrailSet = Sets.newHashSet();
	public Set<String> diseaseCuries = Sets.newHashSet();

	public NCITPreprocessor() {
		super();
	}

	private void visitSuperClassNode(Model model, Resource aClassIRI) {
		if (aClassIRI.toString().contains("owl")) return;		
		superClassTrailSet.add(aClassIRI);

		Collection<Resource> bClassIRIs = subClassRsrcMap.get(aClassIRI);
		if (bClassIRIs.isEmpty()) {
			// logger.info("aClassIRI: " + aClassIRI);
			visitIntUniClassNode(model, aClassIRI, aClassIRI);
			return;
		}

		for (Resource bClassIRI : bClassIRIs) {
			if (superClassTrailSet.contains(bClassIRI)) continue;
			visitSuperClassNode(model, bClassIRI);
		}
	}

	private void visitIntUniClassNode(Model model, Resource aClassIRI, Resource beginClassIRI) {
		Collection<Resource> bClassIRIs = classIntUniClassRsrcMap.get(aClassIRI);
		if (bClassIRIs.isEmpty()) {
			visitSomeClassNode(model, aClassIRI, beginClassIRI);	
			return;
		}

		for (Resource bClassIRI : bClassIRIs) {
			visitIntUniClassNode(model, bClassIRI, beginClassIRI);
		}
	}

	private void visitSomeClassNode(Model model, Resource aClassIRI, Resource beginClassIRI) {
		Collection<Resource> someClassIRIs = someClassRsrcMap.get(aClassIRI);
		for (Resource someClassIRI: someClassIRIs) {
			Resource b1 = generateDummyResource(model, someClassIRI);
			model.add(b1, RDF.type, someClassIRI);
			
			if (beginClassIRI.isAnon()) {
				Resource equivBeginClassIRI = equivClassRsrcMap.get(beginClassIRI);
				if (equivBeginClassIRI != null)
					classEqEntityMap.put(curieUtil.getCurie(equivBeginClassIRI.getURI()).get(), b1.getURI());	
			} else {
				classEqEntityMap.put(curieUtil.getCurie(beginClassIRI.getURI()).get(), b1.getURI());
			}
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
				Resource a = (Resource)binding.get("a");
				Resource b = (Resource)binding.get("b");
				equivClassRsrcMap.put(b, a);
			}

			ResultSet resultSet2 = queryExecutor.executeSelect(querySelectSubClasses);
			while (resultSet2.hasNext()) {
				QuerySolution binding = resultSet2.nextSolution();
				Resource a = (Resource)binding.get("a");
				Resource b = (Resource)binding.get("b");
				if (a.toString().contains("owl") || b.toString().contains("owl")) continue;

				subClassRsrcMap.put(a, b);

				Optional<String> classRsrcOpt = curieUtil.getCurie(b.toString());
				Optional<String> subClassRsrcOpt = curieUtil.getCurie(a.toString());
				if (classRsrcOpt.isPresent() && subClassRsrcOpt.isPresent()) 
					classSubClassMap.put(classRsrcOpt.get(), subClassRsrcOpt.get());
			}

			ResultSet resultSet3 = queryExecutor.executeSelect(querySelectSomeClasses);
			while (resultSet3.hasNext()) {
				QuerySolution binding = resultSet3.nextSolution();				
				Resource a = (Resource)binding.get("a");
				Resource b = (Resource)binding.get("b");
				if (a.toString().contains("owl") || b.toString().contains("owl")) continue;

				someClassRsrcMap.put(a, b);
			}

			ResultSet resultSet4 = queryExecutor.executeSelect(querySelectDiseaseClasses);
			while (resultSet4.hasNext()) {
				QuerySolution binding = resultSet4.nextSolution();
				Resource dClassRsrc = (Resource)binding.get("dsubclass");
				diseaseCuries.add(curieUtil.getCurie(dClassRsrc.toString()).get());
			}

			ResultSet resultSet6 = queryExecutor.executeSelect(querySelectIntUniClasses);
			while (resultSet6.hasNext()) {
				QuerySolution binding = resultSet6.nextSolution();
				Resource a = (Resource)binding.get("a");
				Resource b = (Resource)binding.get("b");
				if (a.toString().contains("owl") || b.toString().contains("owl")) continue;

				classIntUniClassRsrcMap.put(a, b);
			}

			for (Resource classIRI: subClassRsrcMap.keySet()) {
				visitSuperClassNode(model, classIRI);
			}

			logger.info("classSubClassMap.size:" + subClassRsrcMap.size());
			logger.info("classEquivClassRsrcMap.size:" + equivClassRsrcMap.size());
			logger.info("classSomeClassRsrcMa.size:" + someClassRsrcMap.size());
			logger.info("classIntUniClassRsrcMap.size:" + classIntUniClassRsrcMap.size());
			logger.info("classEqEntityMap.size:" + classEqEntityMap.size());

			for (String eachClass : classEqEntityMap.keySet()) {
				Collection<String> eqEntitySet = classEqEntityMap.get(eachClass);
				for (String eqEntity: eqEntitySet) {
					classParamMap.put(eachClass, new OWLNamedIndividualImpl(IRI.create(eqEntity)));
				}
			}

			/*
			int i = 0;
			for (String eachClass : classParamMap.keySet()) {
				logger.info(eachClass + " - " + classParamMap.get(eachClass));
				if ( classParamMap.get(eachClass).size() > 1 && eachClass.contains("NCIT")) {
					i+= 1;
				}
			}
			logger.info("i: " + i);
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