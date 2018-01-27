package org.monarchinitiative.mondohpolearner.mondogo;

import java.io.File;

import org.apache.log4j.Logger;
import org.monarchinitiative.mondohpolearner.common.Processor;

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
	}
}