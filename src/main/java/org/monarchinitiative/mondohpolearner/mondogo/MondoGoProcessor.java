package org.monarchinitiative.mondohpolearner.mondogo;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.monarchinitiative.mondohpolearner.common.Processor;
import org.monarchinitiative.mondohpolearner.util.DLLearnerRunner;
import org.semanticweb.owlapi.model.OWLIndividual;

public class MondoGoProcessor extends Processor {
	private static final Logger logger = Logger.getLogger(MondoGoProcessor.class.getName());

	public MondoGoProcessor() {
		super();
		
		inputOWLFile = "mondo.owl"; 
		reportHomeDir = "mondogo_report";
		markdownDir = reportHomeDir + File.separator + "markdown";
		hpofilewithAbox = "gowithabox.owl";
		queryExtractSubclasses = "src/main/resources/org/monarchinitiative/mondohpolearner/mondo/extractSubclasses.sparql";
		queryExecutor.loadModel(Processor.inputOWLFile);
		
		pp = new MondoGoPreprocessor ();
		reportGenerator = new MondoGoReportGenerator ();
		
		((MondoGoReportGenerator) reportGenerator).mondoClassSubClassMap = ((MondoGoPreprocessor) pp).mondoClassSubClassMap;
		((MondoGoReportGenerator) reportGenerator).mondoGoMap = ((MondoGoPreprocessor) pp).mondoGoMap;
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
			/* The first entity is always the root class that represents "a disease", which is pretty meaningless */
			if (classCurie.contains("0000001")) continue;
			
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