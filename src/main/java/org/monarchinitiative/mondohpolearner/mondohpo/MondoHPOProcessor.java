package org.monarchinitiative.mondohpolearner.mondohpo;

import java.io.File;

import org.apache.log4j.Logger;
import org.monarchinitiative.mondohpolearner.common.Processor;

public class MondoHPOProcessor extends Processor {
	private static final Logger logger = Logger.getLogger(MondoHPOProcessor.class.getName());

	public MondoHPOProcessor() {
		super();
		
		inputOWLFile = "mondo.owl"; 
		reportHomeDir = "mondohpo_report";
		markdownDir = reportHomeDir + File.separator + "markdown";
		hpofilewithAbox = "hpwithmondoabox.owl";
		queryExtractSubclasses = "src/main/resources/org/monarchinitiative/mondohpolearner/mondo/extractSubclassesWithDBXRef.sparql";
		queryExecutor.loadModel(Processor.inputOWLFile);
		
		pp = new MondoHPOPreprocessor ();
		reportGenerator = new MondoHPOReportGenerator ();
	}
}
