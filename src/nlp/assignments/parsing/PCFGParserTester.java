package nlp.assignments.parsing;


import java.util.*;

import nlp.io.PennTreebankReader;
import nlp.ling.Tree;
import nlp.ling.Trees;
import nlp.parser.EnglishPennTreebankParseEvaluator;
import nlp.util.*;

/**
 * Harness for PCFG Parser project.
 */
public class PCFGParserTester {

  public static void main(String[] args) {
    // Parse command line flags and arguments
    Map<String, String> argMap = CommandLineUtils.simpleCommandLineParser(args);

    // Set up default parameters and settings
    String basePath = "data";
    boolean verbose = true;
    String testMode = "validate";
    
    // you may want to change this for initial annotation experiments (e.g., maxTrainLength = 10) so that to make debugging
    // the annotation process easier
    //int maxTrainLength = 1000;   // this is the standard setting (e.g., to compare with the previous work) 
    int maxTrainLength = 20;  // use if for time/memory constraints cannot use the default setting  
    //int maxTestLength = 40; // this is the standard setting (e.g., to compare with the previous work) 
    int maxTestLength = 20;  // use if for time/memory constraints cannot use the default settingint
    // Please keep the above values (20 / 20) in the versions you submit
    

    // Update defaults using command line specifications
    if (argMap.containsKey("-path")) {
      basePath = argMap.get("-path");
      System.out.println("Using base path: " + basePath);
    }
    if (argMap.containsKey("-test")) {
      testMode = "test";
      System.out.println("Testing on final test data.");
    } else {
      System.out.println("Testing on validation data.");
    }
    if (argMap.containsKey("-maxTrainLength")) {
      maxTrainLength = Integer.parseInt(argMap.get("-maxTrainLength"));
    }
    System.out.println("Maximum length for training sentences: " + maxTrainLength);
    if (argMap.containsKey("-maxTestLength")) {
      maxTrainLength = Integer.parseInt(argMap.get("-maxTestLength"));
    }
    System.out.println("Maximum length for test sentences: " + maxTestLength);
    if (argMap.containsKey("-verbose")) {
      verbose = true;
    }
    if (argMap.containsKey("-quiet")) {
      verbose = false;
    }
    

    System.out.print("Loading training trees  ... ");
    //TODO for initial experiments you may choose to load only a subset of the training section, 
    // rather than entire section 2 - 22 (as standard)
    List<Tree<String>> trainTrees = readTrees(basePath, 200, 2199, maxTrainLength);
   
    System.out.println("done. (" + trainTrees.size() + " trees)");
    List<Tree<String>> testTrees = null;
    if (testMode.equalsIgnoreCase("validate")) {
      
      System.out.print("Loading validation trees ... ");
      
      //This is only a subset of the validation set - 393 trees (as used, e.g., in Klein and Manning 2003 for initial experiments)
      //testTrees = readTrees(basePath, 2200, 2219, maxTestLength); // 
      
      testTrees = readTrees(basePath, 2200, 2299, maxTestLength);  
    } else {
      System.out.print("Loading test trees  ... ");
      testTrees = readTrees(basePath, 2300, 2399, maxTestLength);
    }
    System.out.println("done. (" + testTrees.size() + " trees)");

    TreeAnnotator annotator;
    if (argMap.containsKey("-no-binarization")) {
    	annotator = new DummyAnnotator();
    } else if (argMap.containsKey("-lossless-binarization")) {
    	// TODO this is the baseline (needs to be supported)
    	annotator = new BaselineTreeAnnotations();
    } else if (argMap.containsKey("-grammar-annotation")) {
    	// TODO this option should result in your grammar annotation
    	throw new UnsupportedOperationException("Grammar annotation is not yet supported");
    } else {
    	// TODO by default use the baseline 
    	annotator = new BaselineTreeAnnotations();
    }
    
    // TODO : Fix the parser to support binary rules
    Parser parser = new BaselineCkyParser(trainTrees, annotator);

    if (argMap.containsKey("-scoring-mode")) {
    	double totLogProb = 0.;
    	System.out.println(" Scoring test trees ...");
    	int sentIdx = 0;
    	for (Tree<String> testTree : testTrees) {
    		double logProb = parser.getLogScore(annotator.annotateTree(testTree));
    		System.out.println((sentIdx++ ) + ":  logscore =  " + logProb);
    		totLogProb += logProb;
    		if (sentIdx > 9) {
    			break;
    		}
    	}
    	System.out.println("Total log prob: " + totLogProb);
    	
    } else {
    	testParser(parser, testTrees, verbose);
    }
  }

  private static void testParser(Parser parser, List<Tree<String>> testTrees, boolean verbose) {
    EnglishPennTreebankParseEvaluator.LabeledConstituentEval<String> eval = new EnglishPennTreebankParseEvaluator.LabeledConstituentEval<String>(Collections.singleton("ROOT"), new HashSet<String>(Arrays.asList(new String[]{"''", "``", ".", ":", ","})));
    for (Tree<String> testTree : testTrees) {
      List<String> testSentence = testTree.getYield();
      Tree<String> guessedTree = parser.getBestParse(testSentence);
      if (verbose) {
        System.out.println("Guess:\n" + Trees.PennTreeRenderer.render(guessedTree));
        System.out.println("Gold:\n" + Trees.PennTreeRenderer.render(testTree));
      }
      eval.evaluate(guessedTree, testTree);
    }
    eval.display(true);
  }

  private static List<Tree<String>> readTrees(String basePath, int low, int high, int maxLength) {
    Collection<Tree<String>> trees = PennTreebankReader.readTrees(basePath, low, high);
    // normalize trees
    Trees.TreeTransformer<String> treeTransformer = new Trees.StandardTreeNormalizer();
    List<Tree<String>> normalizedTreeList = new ArrayList<Tree<String>>();
    for (Tree<String> tree : trees) {
      Tree<String> normalizedTree = treeTransformer.transformTree(tree);
      if (normalizedTree.getYield().size() > maxLength)
        continue;
//      System.out.println(Trees.PennTreeRenderer.render(normalizedTree));
      normalizedTreeList.add(normalizedTree);
    }
    return normalizedTreeList;
  }
}
