package org.monarchinitiative.mondohpolearner.util;

import java.util.Set;
import java.util.concurrent.Callable;

import org.apache.log4j.Logger;
import org.dllearner.algorithms.celoe.CELOE;
import org.dllearner.algorithms.celoe.OEHeuristicRuntime;
import org.dllearner.algorithms.ocel.OCEL;
import org.dllearner.learningproblems.PosNegLP;
import org.dllearner.learningproblems.PosNegLPStandard;
import org.dllearner.learningproblems.PosOnlyLP;
import org.dllearner.reasoning.ClosedWorldReasoner;
import org.dllearner.refinementoperators.RhoDRDown;
import org.dllearner.utilities.owl.OWLClassExpressionLengthMetric;
import org.monarchinitiative.mondohpolearner.common.ReportGenerator;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;

import com.google.common.collect.Sets;

import uk.ac.manchester.cs.owl.owlapi.OWLClassImpl;
import uk.ac.manchester.cs.owl.owlapi.OWLIndividualImpl;
import uk.ac.manchester.cs.owl.owlapi.OWLNamedIndividualImpl;
import uk.ac.manchester.cs.owl.owlapi.OWLObjectPropertyImpl;

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
			logger.info("Processing " + classCurie + " where params are " + posExamples);

			PosOnlyLP lp = new PosOnlyLP(closedWorldReasoner);
			lp.setPositiveExamples(posExamples);
			lp.init();

			RhoDRDown op = new RhoDRDown();
			op.setReasoner(closedWorldReasoner);
			op.setUseNegation(false);
			op.setDropDisjuncts(true);
			/*
			op.setUseHasSelf(false);
			op.setUseInverse(false);
			op.setUseCardinalityRestrictions(false);
			op.setUseDataHasValueConstructor(false);
			op.setUseAllConstructor(false);
			op.setDisjointChecks(false);
			*/
			
			/*
			OWLClassExpressionLengthMetric metric = new OWLClassExpressionLengthMetric();
			op.setLengthMetric(metric);
			metric.objectProperyLength = 0;
			metric.dataSomeValuesLength = 0;
			metric.objectSomeValuesLength = 0;
			op.init();
			*/
			
			// OEHeuristicRuntime heuristic = new OEHeuristicRuntime();
			// heuristic.setExpansionPenaltyFactor(1);
			// heuristic.setNodeRefinementPenalty(0);
			// heuristic.setGainBonusFactor(0.1);

			CELOE alg = new CELOE(lp, closedWorldReasoner);
			alg.setOperator(op);
			// alg.setHeuristic(heuristic);
			alg.setLearningProblem(lp);
			alg.setWriteSearchTree(true);
			alg.setSearchTreeFile("log/search-tree.log");
			alg.setReplaceSearchTree(true);
			alg.setNoisePercentage(75);
			alg.setMaxNrOfResults(15);
			alg.setMaxExecutionTimeInSeconds(600);
			
			// alg.setMaxDepth(3);
			// alg.setUseMinimizer(false);
			// alg.setReuseExistingDescription(true);
			// alg.setExpandAccuracy100Nodes(true);
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