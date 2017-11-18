package org.monarchinitiative.mondohpomapper.util;

import java.util.HashSet;
import java.util.Set;

import org.apache.log4j.Logger;
import org.dllearner.algorithms.celoe.CELOE;
import org.dllearner.core.EvaluatedDescription;
import org.dllearner.core.KnowledgeSource;
import org.dllearner.core.Score;
import org.dllearner.kb.OWLFile;
import org.dllearner.learningproblems.PosOnlyLP;
import org.dllearner.reasoning.ClosedWorldReasoner;
import org.semanticweb.owlapi.model.OWLIndividual;


public class DLLearnerWrapper{
	private static final Logger logger = Logger.getLogger(DLLearnerWrapper.class.getName());
	private OWLFile ks;
	private ClosedWorldReasoner reasoner;

	public DLLearnerWrapper() {
		try {
			ks = new OWLFile();
			ks.setFileName("hpwithabox.owl");
			ks.init();

			reasoner = new ClosedWorldReasoner();
			Set<KnowledgeSource> sources = new HashSet<>();
			sources.add(ks);
			reasoner.setSources(sources);
			reasoner.init();
		} catch (Exception e) {
			logger.error(e.getMessage());
		}
	}

	public Set<? extends EvaluatedDescription<? extends Score>> run(Set<OWLIndividual> posExamples) {
		try {
			PosOnlyLP lp = new PosOnlyLP(reasoner);
			lp.setPositiveExamples(posExamples);
			lp.init();
			
			CELOE alg = new CELOE();
			alg.setLearningProblem(lp);
			alg.setReasoner(reasoner);
			alg.setWriteSearchTree(true);
			alg.setSearchTreeFile("log/search-tree.log");
			alg.setReplaceSearchTree(true);
			alg.setNoisePercentage(75);
			alg.setMaxNrOfResults(15);
			alg.init();
			alg.start();
			
			return alg.getCurrentlyBestEvaluatedDescriptions().descendingSet();
		} catch (Exception e) {
			logger.error(e.getMessage());
		}
		
		return null;
	}
}