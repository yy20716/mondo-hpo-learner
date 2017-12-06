package org.monarchinitiative.mondohpolearner.mondo;

import java.io.File;

import org.apache.log4j.Logger;
import org.monarchinitiative.mondohpolearner.common.Processor;

public class MondoProcessor extends Processor {
	private static final Logger logger = Logger.getLogger(MondoProcessor.class.getName());

	public MondoProcessor() {
		super();
		
		inputOWLFile = "mondo.owl"; 
		reportHomeDir = "mondo_report";
		markdownDir = reportHomeDir + File.separator + "markdown";
		hpofilewithAbox = "hpwithorphaabox.owl";
		queryExtractSubclasses = "src/main/resources/org/monarchinitiative/mondohpolearner/orpha/extractSubclasses.sparql";
		queryExecutor.loadModel(Processor.inputOWLFile);
		
		pp = new MondoPreprocessor ();
		reportGenerator = new MondoReportGenerator ();
	}
}
