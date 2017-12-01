package org.monarchinitiative.mondohpolearner.orpha;

import java.io.File;

import org.apache.log4j.Logger;
import org.monarchinitiative.mondohpolearner.common.Preprocessor;
import org.monarchinitiative.mondohpolearner.common.Processor;
import org.monarchinitiative.mondohpolearner.common.ReportGenerator;

public class OrphanetProcessor extends Processor{
	private static final Logger logger = Logger.getLogger(OrphanetProcessor.class.getName()); 

	public OrphanetProcessor() {
		super();
		
		inputOWLFile = "ordo_orphanet.owl"; 
		reportHomeDir = "orpha_report";
		markdownDir = reportHomeDir + File.separator + "markdown";
		hpofilewithAbox = "hpwithorphaabox.owl";
		queryExtractSubclasses = "src/main/resources/org/monarchinitiative/mondohpolearner/orpha/extractSubclasses.sparql";
		
		pp = new OrphanetPreprocessor ();
		pp.curieUtil = this.curieUtil;
		pp.classEqEntityMap = this.classEqEntityMap;
		pp.classParamMap = this.classParamMap;
		pp.classSubClassMap = this.classSubClassMap;
		
		reportGenerator = new OrphanetReportGenerator ();
		reportGenerator.curieUtil = this.curieUtil;
		reportGenerator.classEqEntityMap =  this.classEqEntityMap;
		reportGenerator.classSubclassMap = this.classSubClassMap;
	}
}