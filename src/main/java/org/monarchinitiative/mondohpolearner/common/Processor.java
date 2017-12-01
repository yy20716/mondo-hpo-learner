package org.monarchinitiative.mondohpolearner.common;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.dllearner.core.EvaluatedDescription;
import org.monarchinitiative.mondohpolearner.doid.DoidPreprocessor;
import org.monarchinitiative.mondohpolearner.doid.DoidReportGenerator;
import org.monarchinitiative.mondohpolearner.util.CurieMapGenerator;
import org.monarchinitiative.mondohpolearner.util.DLLearnerWrapper;
import org.prefixcommons.CurieUtil;
import org.semanticweb.owlapi.model.OWLIndividual;

import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

public class Processor {
	private static final Logger logger = Logger.getLogger(Processor.class.getName()); 
	public Preprocessor pp;
	public ReportGenerator reportGenerator;
	public CurieUtil curieUtil;

	public static String inputOWLFile;
	public static String markdownDir;
	public static String reportHomeDir;
	public static String hpofilewithAbox;
	public static String queryExtractSubclasses;

	/* key: class (curie), value: a list of equivalent classes that will be fed as parameters for running DL-Learner */
	public Multimap<String, OWLIndividual> classParamMap = Multimaps.newSetMultimap(new LinkedHashMap<>(), HashSet::new);
	/* key: class (curie), value: a list of equivalent classes in mondo, e.g., Orphanet, OMIM */
	public Multimap<String, String> classEqEntityMap = Multimaps.newSetMultimap(new LinkedHashMap<>(), HashSet::new);
	/* key: class (curie), value: a list of mondo subclasses */
	public Multimap<String, String> classSubClassMap = Multimaps.newSetMultimap(new LinkedHashMap<>(), HashSet::new);
	/* key: class (curie), value: a result of running DL-learner, i.e., a list of class expressions that define the key class */
	public Map<String, Set<? extends EvaluatedDescription>> classResultMap = Maps.newHashMap();

	public Processor() {
		/* Initialize curieUtil which converts Curie to IRI and vice versa */
		CurieMapGenerator generator = new CurieMapGenerator();
		Map<String, String> curieMap = generator.generate();
		curieUtil = new CurieUtil(curieMap);
	}
	
	public void run() {
		/* preprocess the data */
		pp.run();

		/* Initialize MarkDownReportGenerator */
		reportGenerator.precomputeLabels();

		int classParamSize = classParamMap.keySet().size();
		DLLearnerWrapper wrapper = new DLLearnerWrapper(hpofilewithAbox);
		logger.info("the number of classes: " + classParamSize);

		/* Run DL-Learner over each class using the parameters stored in classParamMap */
		int classIndex = 0;
		List<String> classKeyList = new ArrayList<>(classParamMap.asMap().keySet());
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

			reportGenerator.classResultMap = this.classResultMap;
			reportGenerator.render(classCurie);

			classResultMap.clear();
		}

		reportGenerator.generateIndexFile();
	}
}