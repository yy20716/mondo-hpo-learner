package org.monarchinitiative.mondohpolearner.mondogo;


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.compress.utils.Lists;
import org.apache.jena.ext.com.google.common.collect.Sets;
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
import org.monarchinitiative.mondohpolearner.util.QueryExecutor;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;

import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

import net.steppschuh.markdowngenerator.list.UnorderedList;
import net.steppschuh.markdowngenerator.text.emphasis.BoldText;
import net.steppschuh.markdowngenerator.text.heading.Heading;

public class MondoGoReportGenerator extends ReportGenerator {
	private static final Logger logger = Logger.getLogger(MondoGoReportGenerator.class.getName());
	public Multimap<String, String> mondoClassSubClassMap = Multimaps.newSetMultimap(new LinkedHashMap<>(), HashSet::new);
	public Multimap<String, String> mondoGoMap = Multimaps.newSetMultimap(new LinkedHashMap<>(), HashSet::new);

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

	/* render and write a report file */
	@SuppressWarnings("unchecked")
	public void render(String classCurie, @SuppressWarnings("rawtypes") Set<? extends EvaluatedDescription> learnedExprs) {
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

			Collection<String> allGoClasses = Sets.newHashSet();
			Collection <String> mondoSubClasses = mondoClassSubClassMap.get(classCurie);
			for (String mondoSubClass: mondoSubClasses) {
				Collection<String> goClasses = mondoGoMap.get(mondoSubClass);	
				for (String goClass: goClasses) {
					allGoClasses.add(goClass);
				}
			}
			String annotatedGoClassStr = generateAnnoClassListStr(allGoClasses);
			sb.append(new BoldText("Mapped go classes:") + " " + annotatedGoClassStr).append(newLineChar).append(newLineChar);
			sb.append(new BoldText("Class expressions from DL-Learner:")).append(newLineChar).append(newLineChar);

			List<String> resultList = Lists.newArrayList();
			if (learnedExprs == null ) {
				sb.append("No results from DL-Learner...").append(newLineChar);
			} else {
				for(EvaluatedDescription<? extends Score> ed : learnedExprs) {
					OWLClassExpression description = ed.getDescription();
					Set<OWLClass> goClasses = description.getClassesInSignature();
					String goClassExprStr =  renderer.render(description);

					for (OWLClass goClass: goClasses) {
						String goClassStr = goClass.toString();
						if (goClassStr.contains("Thing")) continue;
						
						String goClassCurie = goClassStr.replace("_", ":");
						Optional<String> goClassIRIOpt = curieUtil.getIri(goClassCurie);

						if (goClassIRIOpt.isPresent()) {
							String goClassLabel = entityLabelMap.get(goClassIRIOpt.get());
							goClassExprStr = goClassExprStr.replace(goClassStr, annoCuriewithLink(goClassCurie) + " (" + goClassLabel + ")");	
						} else {
							goClassExprStr = goClassExprStr.replace(goClassStr, goClassCurie);
						}
					}

					goClassExprStr = goClassExprStr + " " + dfPercent.format(ed.getAccuracy());
					resultList.add(goClassExprStr);
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