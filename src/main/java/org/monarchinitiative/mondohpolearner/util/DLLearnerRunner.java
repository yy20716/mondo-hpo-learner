package org.monarchinitiative.mondohpolearner.util;

import java.util.Set;
import java.util.concurrent.Callable;

import org.apache.log4j.Logger;
import org.dllearner.algorithms.celoe.CELOE;
import org.dllearner.learningproblems.PosOnlyLP;
import org.dllearner.reasoning.ClosedWorldReasoner;
import org.monarchinitiative.mondohpolearner.common.ReportGenerator;
import org.semanticweb.owlapi.model.OWLIndividual;

public class DLLearnerRunner implements Callable<Void> {
	private static final Logger logger = Logger.getLogger(DLLearnerRunner.class.getName());
	
	private ClosedWorldReasoner closedWorldReasoner;
	private Set<OWLIndividual> posExamples;
	private String classCurie;
	private ReportGenerator reportGenerator;
	
	public DLLearnerRunner (ClosedWorldReasoner closedWorldReasoner, 
			ReportGenerator reportGenerator, String classCurie, Set<OWLIndividual> posExamples) {
		
		this.closedWorldReasoner = closedWorldReasoner;
		this.posExamples = posExamples;
		this.classCurie = classCurie;
		this.reportGenerator = reportGenerator;
	}

	@Override
	public Void call() throws Exception {
		try {
			logger.info("Processing " + classCurie + "...");
			
			PosOnlyLP lp = new PosOnlyLP(closedWorldReasoner);
			lp.setPositiveExamples(posExamples);
			lp.init();

			CELOE alg = new CELOE();
			alg.setLearningProblem(lp);
			alg.setReasoner(closedWorldReasoner);
			alg.setWriteSearchTree(true);
			alg.setSearchTreeFile("log/search-tree.log");
			alg.setReplaceSearchTree(true);
			alg.setNoisePercentage(100);
			alg.setMaxNrOfResults(15);
			alg.init();
			alg.start();

			reportGenerator.render(classCurie, alg.getCurrentlyBestEvaluatedDescriptions().descendingSet());
		} catch (Exception e) {
			reportGenerator.render(classCurie, null);
			logger.error(e.getMessage(), e);
		}
		
		return null;
	}
}