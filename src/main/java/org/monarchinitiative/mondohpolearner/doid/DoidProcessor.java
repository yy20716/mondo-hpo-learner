package org.monarchinitiative.mondohpolearner.doid;

import java.io.File;

import org.monarchinitiative.mondohpolearner.common.Processor;

public class DoidProcessor extends Processor {
	
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