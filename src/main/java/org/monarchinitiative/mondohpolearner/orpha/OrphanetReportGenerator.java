package org.monarchinitiative.mondohpolearner.orpha;

import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Resource;
import org.monarchinitiative.mondohpolearner.Main;
import org.monarchinitiative.mondohpolearner.common.Processor;
import org.monarchinitiative.mondohpolearner.common.ReportGenerator;
import org.monarchinitiative.mondohpolearner.util.QueryExecutor;

public class OrphanetReportGenerator extends ReportGenerator {

	public OrphanetReportGenerator() {
		super();
	}

	/* pre-compute all labels (rdfs:label)for orphanet classes */ 
	@Override
	public void precomputeLabels() {
		queryExecutor.loadModel(Processor.inputOWLFile);

		/* 1. extract versionIRIs from orphanet ontology, 
		 * e.g., <http://www.orpha.net/version2.4>, we assume that there will be only one version URI in datasets.*/
		ResultSet versionResultSet = queryExecutor.executeSelect(Main.queryExtractVersion);
		while (versionResultSet.hasNext()) {
			QuerySolution binding = versionResultSet.nextSolution();
			Resource versionRsrc = (Resource)binding.get("version");
			version = versionRsrc.getURI().split("/")[3].replace("version", "");
		}

		/* 2. extract class labels from doid.owl */
		ResultSet labelResultSet = queryExecutor.executeSelect(Main.queryComputeEntityLabel);
		while (labelResultSet.hasNext()) {
			QuerySolution binding = labelResultSet.nextSolution();
			Resource classRsrc = (Resource)binding.get("class");
			Literal label = (Literal) binding.get("label");
			entityLabelMap.put(classRsrc.toString(), label.toString());
		}

		/* 3. extract class labels from hp.owl */
		ResultSet hpResultSet = QueryExecutor.executeSelectOnce("hp.owl", Main.queryComputeEntityLabel);
		while (hpResultSet.hasNext()) {
			QuerySolution binding = hpResultSet.nextSolution();
			Resource classRsrc = (Resource)binding.get("class");
			Literal label = (Literal) binding.get("label");
			entityLabelMap.put(classRsrc.toString(), label.toString());
		}
	}
}