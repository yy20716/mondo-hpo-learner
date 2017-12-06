package org.monarchinitiative.mondohpolearner.doid;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.dllearner.core.EvaluatedDescription;
import org.monarchinitiative.mondohpolearner.common.Processor;
import org.monarchinitiative.mondohpolearner.orpha.OrphanetPreprocessor;
import org.monarchinitiative.mondohpolearner.orpha.OrphanetReportGenerator;
import org.monarchinitiative.mondohpolearner.util.CurieMapGenerator;
import org.monarchinitiative.mondohpolearner.util.DLLearnerRunner;
import org.prefixcommons.CurieUtil;
import org.semanticweb.owlapi.model.OWLIndividual;

import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

public class DoidProcessor extends Processor {
	private static final Logger logger = Logger.getLogger(DoidProcessor.class.getName()); 
	
	public DoidProcessor() {
		super();
		
		inputOWLFile = "doid.owl"; 
		reportHomeDir = "doid_report";
		markdownDir = reportHomeDir + File.separator + "markdown";
		hpofilewithAbox = "hpwithdoidabox.owl";
		queryExtractSubclasses = "src/main/resources/org/monarchinitiative/mondohpolearner/doid/extractSubclasses.sparql";
		queryExecutor.loadModel(Processor.inputOWLFile);
		
		pp = new DoidPreprocessor ();
		reportGenerator = new DoidReportGenerator ();
	}
}