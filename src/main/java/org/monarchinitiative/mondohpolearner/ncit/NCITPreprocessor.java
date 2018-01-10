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
	private static String querySelectEquivClasses = ncitQueryPath + File.separator + "selectEquiv.sparql";
	private static String queryReplaceEquivClasses = ncitQueryPath + File.separator + "replaceEquiv.sparql";
	private static String querySelectSubClasses = ncitQueryPath + File.separator + "extractSubClasses.sparql";
	private static String querySelectSomeClasses = ncitQueryPath + File.separator + "extractSomeValuesFrom.sparql";
	private static String querySelectIntUniClasses = ncitQueryPath + File.separator + "extractIntersectUnionClasses.sparql";
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

	private void visitSuperClassNode(Model model, Resource classRsrc) {
		if (classRsrc.toString().contains("owl")) return;
		if (classRsrc.toString().contains("NCIT") != true) return;
		
		Collection<Resource> superClassIRIs = subClassRsrcMap.get(classRsrc);
		if (superClassIRIs.isEmpty()) return;

		for (Resource superClassIRI : superClassIRIs) {
			if (superClassIRI.isURIResource()) {
				Collection<Resource> equivBackClassRsrcs = equivBackClassRsrcMap.get(superClassIRI);
				for (Resource equivClassRsrc : equivBackClassRsrcs) {
					visitIntUniClassNode(model, classRsrc, equivClassRsrc, superClassIRI);
				}
			} else {
				Collection<Resource> equivForwClassRsrcs = equivForwClassRsrcMap.get(superClassIRI);
				for (Resource equivClassRsrc : equivForwClassRsrcs) {
					visitIntUniClassNode(model, classRsrc, superClassIRI, equivClassRsrc);
				}
			}
		}
	}

	private void visitIntUniClassNode(Model model, Resource aClassIRI, Resource bClassIRI, Resource superClassIRI) {
		if (bClassIRI.isURIResource()) return;
		Collection<Resource> intUniClassIRIs = classIntUniClassRsrcMap.get(bClassIRI);
		if (intUniClassIRIs.isEmpty()) {
			visitSomeClassNode(model, aClassIRI, bClassIRI, superClassIRI);	
			return;
		}

		for (Resource intUniClassIRI : intUniClassIRIs) {
			visitIntUniClassNode(model, bClassIRI, intUniClassIRI, superClassIRI);
		}
	}

	private void visitSomeClassNode(Model model, Resource aClassIRI, Resource bClassIRI, Resource superClassIRI) {
		Collection<Resource> someClassIRIs = someClassRsrcMap.get(bClassIRI);
		if (someClassIRIs.isEmpty()) {
			if (propSet.contains(bClassIRI)) return;
			
			Resource b1 = generateDummyResource(model, bClassIRI);
			model.add(b1, RDF.type, bClassIRI);

			if (superClassIRI.isAnon()) {
				Collection<Resource> equivBeginClassIRIs = equivForwClassRsrcMap.get(superClassIRI);
				if (equivBeginClassIRIs != null) {
					for (Resource equivBeginClassIRI : equivBeginClassIRIs){
						classEqEntityMap.put(curieUtil.getCurie(equivBeginClassIRI.getURI()).get(), b1.getURI());	
					}
				}
			} else {
				classEqEntityMap.put(curieUtil.getCurie(superClassIRI.getURI()).get(), b1.getURI());				
			}
			return;
		}

		for (Resource someClassIRI: someClassIRIs) {
			visitSomeClassNode(model, bClassIRI, someClassIRI, superClassIRI);
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
				if (a.toString().contains("owl") || b.toString().contains("owl")) continue;

				equivForwClassRsrcMap.put(b, a);
				equivBackClassRsrcMap.put(a, b);
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
				Resource classRsrc = (Resource)binding.get("s");
				diseaseCuries.add(curieUtil.getCurie(classRsrc.toString()).get());
			}

			ResultSet resultSet5 = queryExecutor.executeSelect(querySelectPropertyClasses);
			while (resultSet5.hasNext()) {
				QuerySolution binding = resultSet5.nextSolution();
				Resource classRsrc = (Resource)binding.get("s");
				propSet.add(classRsrc);
			}
			
			ResultSet resultSet6 = queryExecutor.executeSelect(querySelectIntUniClasses);
			while (resultSet6.hasNext()) {
				QuerySolution binding = resultSet6.nextSolution();
				Resource a = (Resource)binding.get("a");
				Resource b = (Resource)binding.get("b");
				if (a.toString().contains("owl") || b.toString().contains("owl")) continue;

				classIntUniClassRsrcMap.put(a, b);
			}

			for (Resource classRsrc: subClassRsrcMap.keySet()) 
				visitSuperClassNode(model, classRsrc);

			logger.info("classSubClassMap.size:" + subClassRsrcMap.size());
			logger.info("classSomeClassRsrcMa.size:" + someClassRsrcMap.size());
			logger.info("classIntUniClassRsrcMap.size:" + classIntUniClassRsrcMap.size());
			logger.info("classEqEntityMap.size:" + classEqEntityMap.size());

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