package org.monarchinitiative.mondohpolearner.common;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.dllearner.kb.OWLFile;
import org.dllearner.reasoning.ClosedWorldReasoner;
import org.dllearner.reasoning.OWLAPIReasoner;
import org.dllearner.reasoning.ReasonerImplementation;
import org.monarchinitiative.mondohpolearner.util.CurieMapGenerator;
import org.monarchinitiative.mondohpolearner.util.DLLearnerRunner;
import org.monarchinitiative.mondohpolearner.util.QueryExecutor;
import org.prefixcommons.CurieUtil;
import org.semanticweb.elk.owlapi.ElkReasoner;
import org.semanticweb.owlapi.model.OWLIndividual;

import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

public class Processor {
	private static final Logger logger = Logger.getLogger(Processor.class.getName());
	protected static final Integer threadNumber = 1;
	
	public Preprocessor pp;
	public ReportGenerator reportGenerator;
	public CurieUtil curieUtil;
	public QueryExecutor queryExecutor;

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

	protected OWLFile ks;
	protected OWLAPIReasoner reasoner;
	protected ClosedWorldReasoner closedWorldReasoner; 

	public Processor() {
		/* Initialize curieUtil which converts Curie to IRI and vice versa */
		CurieMapGenerator generator = new CurieMapGenerator();
		Map<String, String> curieMap = generator.generate();
		curieUtil = new CurieUtil(curieMap);
		queryExecutor = new QueryExecutor();
	}

	public void initialize() {
		pp.curieUtil = this.curieUtil;
		pp.classEqEntityMap = this.classEqEntityMap;
		pp.classParamMap = this.classParamMap;
		pp.classSubClassMap = this.classSubClassMap;
		pp.queryExecutor = this.queryExecutor;

		reportGenerator.curieUtil = this.curieUtil;
		reportGenerator.classEqEntityMap =  this.classEqEntityMap;
		reportGenerator.classSubclassMap = this.classSubClassMap;
		reportGenerator.queryExecutor = this.queryExecutor;
	}

	public void prepareDLLearner() {
		try {
			ks = new OWLFile();
			ks.setFileName(hpofilewithAbox);
			ks.init();

			reasoner = new OWLAPIReasoner(ks);
			reasoner.setReasonerImplementation(ReasonerImplementation.ELK);
			reasoner.setUseFallbackReasoner(true);
			reasoner.init();

			Logger.getLogger(ElkReasoner.class).setLevel(Level.OFF);
			closedWorldReasoner = new ClosedWorldReasoner(ks);
			closedWorldReasoner.setReasonerComponent(reasoner); 
//			closedWorldReasoner.setHandlePunning(true);
//			closedWorldReasoner.setMaterializeExistentialRestrictions(true);
			closedWorldReasoner.init();
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
	}

	public void run() {
		initialize();

		pp.run(); /* preprocess the data */
		prepareDLLearner(); /* read the pre-processed data and hand it over to DL-learner */
		reportGenerator.precomputeLabels(); /* Initialize MarkDownReportGenerator */

		int classParamSize = classParamMap.keySet().size();
		logger.info("the number of classes: " + classParamSize);

		ExecutorService exe = Executors.newFixedThreadPool(threadNumber);

		/* Run DL-Learner over each class using the parameters stored in classParamMap */
		List<String> classKeyList = new ArrayList<>(classParamMap.asMap().keySet());

		for (String classCurie : classKeyList) {
			File f = new File(markdownDir + File.separator + classCurie.replace(":", "_") + ".md");
			if(f.exists() && f.length() > 0) continue;

			/* we skip processing classes if the # of subclasses or equivalent classes is less than 2. */ 
			int eqEntitySize = classEqEntityMap.get(classCurie).size();
			if (eqEntitySize < 2) continue;

			int subClassSize = classSubClassMap.get(classCurie).size();
			if (subClassSize < 2) continue;

			Set<OWLIndividual> paramSet = new HashSet<>(classParamMap.get(classCurie));
			DLLearnerRunner runner = new DLLearnerRunner (closedWorldReasoner, reportGenerator, classCurie, paramSet);
			exe.submit(runner);
		}

		try {
			exe.shutdown();
			exe.awaitTermination(Integer.MAX_VALUE, TimeUnit.SECONDS);
			reportGenerator.generateIndexFile();
		} catch (InterruptedException e) {
			logger.error(e.getMessage(), e);
		}
	}
}