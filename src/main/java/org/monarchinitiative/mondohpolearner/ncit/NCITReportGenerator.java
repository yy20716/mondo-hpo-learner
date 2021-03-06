package org.monarchinitiative.mondohpolearner.ncit;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.compress.utils.Lists;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Resource;
import org.apache.log4j.Logger;
import org.dllearner.core.EvaluatedDescription;
import org.dllearner.core.Score;
import org.monarchinitiative.mondohpolearner.Main;
import org.monarchinitiative.mondohpolearner.common.Processor;
import org.monarchinitiative.mondohpolearner.common.ReportGenerator;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;

import com.google.common.collect.Multimap;

import net.steppschuh.markdowngenerator.list.UnorderedList;
import net.steppschuh.markdowngenerator.text.emphasis.BoldText;
import net.steppschuh.markdowngenerator.text.heading.Heading;

public class NCITReportGenerator extends ReportGenerator {
	private static final Logger logger = Logger.getLogger(NCITReportGenerator.class.getName());
	public Multimap<Resource, Resource> classSomeClassRsrcMap;
	public Map<Resource, Resource> classEquivClassRsrcMap;

	public NCITReportGenerator() {
		super();
	}

	@Override 
	public void precomputeLabels() {
		/* 1. extract versionIRIs from doid ontology, 
		 * e.g., <http://purl.obolibrary.org/obo/doid/releases/2017-11-10/doid.owl> 
		 * We assume that there will be only one version URI in datasets.*/
		ResultSet versionResultSet = queryExecutor.executeSelect(Main.queryExtractVersion);
		while (versionResultSet.hasNext()) {
			QuerySolution binding = versionResultSet.nextSolution();
			Resource versionRsrc = (Resource)binding.get("version");
			version = versionRsrc.getURI().split("/")[6];
		}

		/* 2. extract class labels from doid.owl */
		ResultSet labelResultSet = queryExecutor.executeSelect(Main.queryComputeEntityLabel);
		while (labelResultSet.hasNext()) {
			QuerySolution binding = labelResultSet.nextSolution();
			Resource classRsrc = (Resource)binding.get("class");
			Literal label = (Literal) binding.get("label");
			entityLabelMap.put(classRsrc.toString(), label.toString());
		}
	}
	

	/* render and write a report file */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public void render(String classCurie, Set<? extends EvaluatedDescription> learnedExprs) {
		StringBuilder sb = new StringBuilder();
		logger.info("Rendering a report file...");
		try{
			String reportFilenameBody = classCurie.replace(":", "_");
			FileWriter fw = new FileWriter(Processor.markdownDir + File.separator + reportFilenameBody + ".md", false);
			BufferedWriter bw = new BufferedWriter(fw);
			PrintWriter out = new PrintWriter(bw);

			sb.append(newLineChar).append(new Heading(annoCuriewithLink(classCurie), 3)).append(newLineChar);

			String classIRI = curieUtil.getIri(classCurie).get();
			String mondoClassLabel = entityLabelMap.get(classIRI);
			sb.append(new BoldText("Label:") + " " + mondoClassLabel).append(newLineChar).append(newLineChar);
			sb.append(new BoldText("Subclasses:") + " " + generateAnnoClassListStr(classSubclassMap.get(classCurie))).append(newLineChar).append(newLineChar);
			sb.append(new BoldText("Class expressions from DL-Learner:")).append(newLineChar).append(newLineChar);

			List<String> resultList = Lists.newArrayList();
			if (learnedExprs == null ) {
				sb.append("No results from DL-Learner...").append(newLineChar);
			} else {
				for(EvaluatedDescription<? extends Score> ed : learnedExprs) {
					OWLClassExpression description = ed.getDescription();
					Set<OWLClass> hpClasses = description.getClassesInSignature();
					String hpClassExprStr =  renderer.render(description);

					for (OWLClass hpClass: hpClasses) {
						String hpClassStr = hpClass.toString();
						if (hpClassStr.contains("Thing")) continue;
						
						String hpClassCurie = hpClassStr.replace("_", ":");
						Optional<String> hpClassIRIOpt = curieUtil.getIri(hpClassCurie);

						if (hpClassIRIOpt.isPresent()) {
							String hpClassLabel = entityLabelMap.get(hpClassIRIOpt.get());
							hpClassExprStr = hpClassExprStr.replace(hpClassStr, annoCuriewithLink(hpClassCurie) + " (" + hpClassLabel + ")");	
						} else {
							hpClassExprStr = hpClassExprStr.replace(hpClassStr, hpClassCurie);
						}
					}

					hpClassExprStr = hpClassExprStr + " " + dfPercent.format(ed.getAccuracy());
					resultList.add(hpClassExprStr);
				}	
			}

			sb.append(new UnorderedList<>(resultList));
			sb.append(newLineChar).append(newLineChar);

			out.println(sb.toString());
			sb.setLength(0);

			out.flush();
			out.close();
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
	}
}