package org.monarchinitiative.mondohpolearner;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.log4j.Logger;
import org.monarchinitiative.mondohpolearner.chart.BarChartGenerator;
import org.monarchinitiative.mondohpolearner.chart.ScatterChartGenerator;
import org.monarchinitiative.mondohpolearner.doid.DoidProcessor;
import org.monarchinitiative.mondohpolearner.mondo.MondoProcessor;

public class Main {
	private static final Logger logger = Logger.getLogger(Main.class.getName());
	public static final String queryComputeEntityLabel = "src/main/resources/org/monarchinitiative/mondohpolearner/computeEntityLabel.sparql";
	public static final String queryExtractVersion = "src/main/resources/org/monarchinitiative/mondohpolearner/extractVersion.sparql";
	public static final String queryExtractEqClasses = "src/main/resources/org/monarchinitiative/mondohpolearner/extractEqClasses.sparql";

	public static void main( String[] args ) {
		Option mondoOption = Option.builder("m")
				.longOpt("mondo")
				.required(false)
				.desc("learn class expressions over mondo.owl")
				.build();

		Option doidOption = Option.builder("d")
				.longOpt("doid")
				.required(false)
				.desc("learn class expressions over doid.owl")
				.build();

		Option chartOption = Option.builder("c")
				.longOpt("chart")
				.required(false)
				.desc("generate charts from the result of learnings")
				.build();

		Options options = new Options();
		options.addOption(mondoOption);
		options.addOption(doidOption);
		options.addOption(chartOption);

		try {
			CommandLineParser parser = new DefaultParser();
			CommandLine cmdLine = parser.parse(options, args);
			long startTime = System.currentTimeMillis();

			if (cmdLine.hasOption("m")) {
				MondoProcessor mpr = new MondoProcessor();
				mpr.run();
			} 
			else if (cmdLine.hasOption("d")) {
				DoidProcessor mpr = new DoidProcessor();
				mpr.run();
			} else if (cmdLine.hasOption("c")) {
				ScatterChartGenerator scg = new ScatterChartGenerator();
				scg.run();
				
				BarChartGenerator bcg = new BarChartGenerator();
				bcg.run();
			} else {
				HelpFormatter formatter = new HelpFormatter();
				formatter.printHelp("Mondo-HPO-Learner", options);
			}

			long stopTime = System.currentTimeMillis();
			long elapsedTime = stopTime - startTime;
			logger.info("A whole elapsed time: " + elapsedTime + " milliseconds.");
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
	}
}