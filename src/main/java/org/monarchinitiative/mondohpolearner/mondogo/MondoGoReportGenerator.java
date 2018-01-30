package org.monarchinitiative.mondohpolearner.mondogo;


import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Resource;
import org.apache.log4j.Logger;
import org.monarchinitiative.mondohpolearner.Main;
import org.monarchinitiative.mondohpolearner.common.ReportGenerator;
import org.monarchinitiative.mondohpolearner.util.QueryExecutor;

public class MondoGoReportGenerator extends ReportGenerator {
	private static final Logger logger = Logger.getLogger(MondoGoReportGenerator.class.getName());

	public MondoGoReportGenerator() {
		super();
	}

	/* pre-compute all labels (rdfs:label)for doid classes */
	public void precomputeLabels() {
		/* 1. extract versionIRIs from ontology, 
		 * e.g., <http://purl.obolibrary.org/obo/doid/releases/2017-11-10/doid.owl> 
		 * We assume that there will be only one version URI in datasets.*/
		ResultSet versionResultSet = queryExecutor.executeSelect(Main.queryExtractVersion);
		while (versionResultSet.hasNext()) {
			QuerySolution binding = versionResultSet.nextSolution();
			Resource versionRsrc = (Resource)binding.get("version");
			version = versionRsrc.getURI().split("/")[6];
		}

		/* 2. extract class labels from mondo.owl */
		ResultSet labelResultSet = queryExecutor.executeSelect(Main.queryComputeEntityLabel);
		while (labelResultSet.hasNext()) {
			QuerySolution binding = labelResultSet.nextSolution();
			Resource classRsrc = (Resource)binding.get("class");
			Literal label = (Literal) binding.get("label");
			entityLabelMap.put(classRsrc.toString(), label.toString());
		}

		/* 3. extract class labels from go.owl */
		ResultSet hpResultSet = QueryExecutor.executeSelectOnce("go.owl", Main.queryComputeEntityLabel);
		while (hpResultSet.hasNext()) {
			QuerySolution binding = hpResultSet.nextSolution();
			Resource classRsrc = (Resource)binding.get("class");
			Literal label = (Literal) binding.get("label");
			entityLabelMap.put(classRsrc.toString(), label.toString());
		}
	}
}