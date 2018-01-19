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
			logger.info("Processing " + classCurie + "...");

			/*
			PosOnlyLP lp = new PosOnlyLP(closedWorldReasoner);
			lp.setPositiveExamples(posExamples);
			*/
			PosNegLP lp = new PosNegLPStandard(closedWorldReasoner);
			lp.setPositiveExamples(posExamples);
			Set<OWLIndividual> negSet = Sets.newHashSet();
			negSet.add(new OWLNamedIndividualImpl(IRI.create("http://a.com/NCITC16612")));
			negSet.add(new OWLNamedIndividualImpl(IRI.create("http://a.com/NCITC12913")));
			negSet.add(new OWLNamedIndividualImpl(IRI.create("http://a.com/NCITC1219")));
			negSet.add(new OWLNamedIndividualImpl(IRI.create("http://a.com/NCITC1908")));
			negSet.add(new OWLNamedIndividualImpl(IRI.create("http://a.com/GO0017145")));
			negSet.add(new OWLNamedIndividualImpl(IRI.create("http://a.com/GO0005634")));
			negSet.add(new OWLNamedIndividualImpl(IRI.create("http://a.com/GO0000791")));
			negSet.add(new OWLNamedIndividualImpl(IRI.create("http://a.com/BFO0000002")));
			negSet.add(new OWLNamedIndividualImpl(IRI.create("http://a.com/CARO0000003")));
			negSet.add(new OWLNamedIndividualImpl(IRI.create("http://a.com/NCBITaxon1")));
			negSet.add(new OWLNamedIndividualImpl(IRI.create("http://a.com/ENVO01000254")));
			negSet.add(new OWLNamedIndividualImpl(IRI.create("http://a.com/PATO0001993")));
			negSet.add(new OWLNamedIndividualImpl(IRI.create("http://a.com/PR000000001")));
			negSet.add(new OWLNamedIndividualImpl(IRI.create("http://a.com/PR000018263")));
			negSet.add(new OWLNamedIndividualImpl(IRI.create("http://a.com/RO0002577")));
			negSet.add(new OWLNamedIndividualImpl(IRI.create("http://a.com/NBO_0000347")));
			
			lp.setNegativeExamples(negSet);
			
			lp.init();

			CELOE alg = new CELOE();
			OEHeuristicRuntime heuristic = new OEHeuristicRuntime();
			heuristic.setExpansionPenaltyFactor(-1);
			// heuristic.setNodeRefinementPenalty(0);
			// heuristic.setGainBonusFactor(0.1);
			
			alg.setHeuristic(heuristic);
			alg.setLearningProblem(lp);
			alg.setReasoner(closedWorldReasoner);
			alg.setWriteSearchTree(true);
			alg.setSearchTreeFile("log/search-tree.log");
			alg.setReplaceSearchTree(true);
			alg.setNoisePercentage(75);
			alg.setMaxNrOfResults(1000);
			// alg.setMaxExecutionTimeInSeconds(3600);
			alg.setMaxDepth(500);
			alg.setUseMinimizer(false);
			/*
			OWLClassExpression startClass = new OWLClassImpl(IRI.create("http://purl.obolibrary.org/obo/NCIT_C3212"));
			alg.setStartClass(startClass);
			alg.setReuseExistingDescription(true);
			alg.setExpandAccuracy100Nodes(true);
			*/
			alg.init();
			((RhoDRDown)alg.getOperator()).setUseNegation(false);
			// ((RhoDRDown)alg.getOperator()).setFrequencyThreshold(10);
			// ((RhoDRDown)alg.getOperator()).setUseSomeOnly(true);
			((RhoDRDown)alg.getOperator()).setDropDisjuncts(true);
			// ((RhoDRDown)alg.getOperator()).setUseHasSelf(false);
			// ((RhoDRDown)alg.getOperator()).setUseInverse(false);
			alg.start();
			
			reportGenerator.render(classCurie, alg.getCurrentlyBestEvaluatedDescriptions().descendingSet());
		} catch (Exception e) {
			reportGenerator.render(classCurie, null);
			logger.error(e.getMessage(), e);
		}

		return null;
	}
}