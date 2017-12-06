package org.monarchinitiative.mondohpolearner.ncit;

import java.io.File;

import org.apache.log4j.Logger;
import org.monarchinitiative.mondohpolearner.common.Preprocessor;
import org.monarchinitiative.mondohpolearner.common.Processor;
import org.monarchinitiative.mondohpolearner.common.ReportGenerator;
import org.monarchinitiative.mondohpolearner.orpha.OrphanetPreprocessor;
import org.monarchinitiative.mondohpolearner.orpha.OrphanetReportGenerator;

public class NCITProcessor extends Processor{
	private static final Logger logger = Logger.getLogger(NCITProcessor.class.getName()); 

	public NCITProcessor() {
		super();

		inputOWLFile = "ncit.owl"; 
		reportHomeDir = "ncit_report";
		markdownDir = reportHomeDir + File.separator + "markdown";
		hpofilewithAbox = "ncitwithabox.owl";
		queryExtractSubclasses = "src/main/resources/org/monarchinitiative/mondohpolearner/ncit/extractSubclasses.sparql";
		queryExecutor.loadModel(Processor.inputOWLFile);
		
		pp = new NCITPreprocessor ();
		reportGenerator = new NCITReportGenerator ();
	}
}