package org.monarchinitiative.mondohpolearner;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.log4j.Logger;
import org.monarchinitiative.mondohpolearner.doid.DoidProcessor;
import org.monarchinitiative.mondohpolearner.mondo.MondoProcessor;

public class Main {
	private static final Logger logger = Logger.getLogger(Main.class.getName());
	public static final String queryComputeEntityLabel = "src/main/resources/org/monarchinitiative/mondohpolearner/computeEntityLabel.sparql";
	
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

		Options options = new Options();
		options.addOption(mondoOption);
		options.addOption(doidOption);

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
			}
			else {
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