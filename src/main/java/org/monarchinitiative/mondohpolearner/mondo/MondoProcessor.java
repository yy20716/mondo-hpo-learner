package org.monarchinitiative.mondohpolearner.mondo;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.dllearner.core.EvaluatedDescription;
import org.monarchinitiative.mondohpolearner.util.CurieMapGenerator;
import org.monarchinitiative.mondohpolearner.util.DLLearnerWrapper;
import org.prefixcommons.CurieUtil;
import org.semanticweb.owlapi.model.OWLIndividual;

import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

public class MondoProcessor {
	private static final Logger logger = Logger.getLogger(MondoProcessor.class.getName());

	public static final String inputOWLFile = "mondo.owl";
	public static final String reportHomeDir = "mondo_report";
	public static final String markdownDir = reportHomeDir + File.separator + "markdown";
	public static final String hpofilewithAbox = "hpwithmondoabox.owl";
	public static final String queryExtractVersion = "src/main/resources/org/monarchinitiative/mondohpolearner/mondo/extractVersion.sparql";
	public static final String queryExtractSubclasses = "src/main/resources/org/monarchinitiative/mondohpolearner/mondo/extractSubclasses.sparql";
	
	/* key: mondo class (curie), value: a list of equivalent classes that will be fed as parameters for running DL-Learner */
	private Multimap<String, OWLIndividual> classParamMap = Multimaps.newSetMultimap(new LinkedHashMap<>(), HashSet::new);
	/* key: mondo class (curie), value: a list of equivalent classes in mondo, e.g., Orphanet, OMIM */
	private Multimap<String, String> classEqEntityMap = Multimaps.newSetMultimap(new LinkedHashMap<>(), HashSet::new);
	/* key: mondo class (curie), value: a list of mondo subclasses in mondo */
	private Multimap<String, String> classSubClassMap = Multimaps.newSetMultimap(new LinkedHashMap<>(), HashSet::new);
	/* key: mondo class (curie), value: a result of running DL-learner, i.e., a list of class expressions that define the key mondo class */
	private Map<String, Set<? extends EvaluatedDescription>> classResultMap = Maps.newHashMap();

	public void run() {
		/* Initialize curieUtil which converts Curie to IRI and vice versa */
		CurieMapGenerator generator = new CurieMapGenerator();
		Map<String, String> curieMap = generator.generate();
		CurieUtil curieUtil = new CurieUtil(curieMap);

		/* preprocess the data */
		MondoPreprocessor pp = new MondoPreprocessor(classParamMap, classEqEntityMap, classSubClassMap);
		pp.setCurieUtil(curieUtil);
		pp.run();

		/* Initialize MarkDownReportGenerator */
		MondoReportGenerator reportGenerator = new MondoReportGenerator(classSubClassMap, classEqEntityMap);
		reportGenerator.setCurieUtil(curieUtil);
		reportGenerator.precomputeLabels();
		
		int classParamSize = classParamMap.keySet().size();
		DLLearnerWrapper wrapper = new DLLearnerWrapper(hpofilewithAbox);
		logger.info("the number of classes: " + classParamSize);

		/* Run DL-Learner over each mondo class using the parameters stored in classParamMap */
		int classIndex = 0;
		List<String> classKeyList = new ArrayList<>(classParamMap.asMap().keySet());
		Collections.sort(classKeyList);
		for (String classCurie : classKeyList) {
			logger.info("Processing " + classCurie + " (" + ++classIndex + "/" + classParamSize + ")");

			File f = new File(markdownDir + File.separator + classCurie.replace(":", "_") + ".md");
			if(f.exists() && f.length() > 0) continue;

			/* we skip processing classes if the # of subclasses or equivalent classes is less than 2. */ 
			int eqEntitySize = classEqEntityMap.get(classCurie).size();
			if (eqEntitySize < 2) continue;

			int subClassSize = classSubClassMap.get(classCurie).size();
			if (subClassSize < 2) continue;

			Set<OWLIndividual> paramSet = new HashSet<>(classParamMap.get(classCurie));
			classResultMap.put(classCurie, wrapper.run(paramSet));

			reportGenerator.setClassResultMap(classResultMap);
			reportGenerator.render(classCurie);

			classResultMap.clear();
		}

		reportGenerator.generateIndexFile();
	}
}
