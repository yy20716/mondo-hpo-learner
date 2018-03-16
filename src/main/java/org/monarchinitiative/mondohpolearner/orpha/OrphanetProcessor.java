package org.monarchinitiative.mondohpolearner.orpha;

import java.io.File;

import org.monarchinitiative.mondohpolearner.common.Processor;

public class OrphanetProcessor extends Processor{

	public OrphanetProcessor() {
		super();
		
		inputOWLFile = "ordo_orphanet.owl"; 
		reportHomeDir = "orpha_report";
		markdownDir = reportHomeDir + File.separator + "markdown";
		hpofilewithAbox = "hpwithorphaabox.owl";
		queryExtractSubclasses = "src/main/resources/org/monarchinitiative/mondohpolearner/orpha/extractSubclasses.sparql";
		queryExecutor.loadModel(Processor.inputOWLFile);
		
		pp = new OrphanetPreprocessor ();
		reportGenerator = new OrphanetReportGenerator ();
	}
}