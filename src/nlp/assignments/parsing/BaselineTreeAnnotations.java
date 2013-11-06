package nlp.assignments.parsing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import nlp.ling.Tree;
import nlp.ling.Trees;
import nlp.util.Filter;

/**
   * Class which contains code for annotating and binarizing trees for the parser's use, and debinarizing and
   * unannotating them for scoring.
   */
  class BaselineTreeAnnotations implements TreeAnnotator {
	  
	public BaselineTreeAnnotations() {
	}
	
	
    @Override
	public  Tree<String> annotateTree(Tree<String> unAnnotatedTree) {
      // Currently, the only annotation done is a lossless binarization
      // TODO : change the annotation from a lossless binarization to a finite-order markov process (try at least 1st and 2nd order)
      // TODO : mark nodes with the label of their parent nodes, giving a second order vertical markov process
      // TODO : used for marking parents "^" symbol, e.g., for vertical Markovization:  if NP is under NP used NP^VP, in this case
      // it will be automatically postprocessed into NP in unAnnotateTree()
      
      return binarizeTree(unAnnotatedTree);
    }

    private Tree<String> binarizeTree(Tree<String> tree) {
      String label = tree.getLabel();
      if (tree.isLeaf())
        return new Tree<String>(label);
      if (tree.getChildren().size() == 1) {
        return new Tree<String>(label, Collections.singletonList(binarizeTree(tree.getChildren().get(0))));
      }
      // otherwise, it's a binary-or-more local tree, so decompose it into a sequence of binary and unary trees.
      String intermediateLabel = "@" + label + "->";
      Tree<String> intermediateTree = binarizeTreeHelper(tree, 0, intermediateLabel);
      return new Tree<String>(label, intermediateTree.getChildren());
    }

    private  Tree<String> binarizeTreeHelper(Tree<String> tree, int numChildrenGenerated, String intermediateLabel) {
      Tree<String> leftTree = tree.getChildren().get(numChildrenGenerated);
      List<Tree<String>> children = new ArrayList<Tree<String>>();
      children.add(binarizeTree(leftTree));
      if (numChildrenGenerated < tree.getChildren().size() - 1) {
        Tree<String> rightTree = binarizeTreeHelper(tree, numChildrenGenerated + 1, intermediateLabel + "_" + leftTree.getLabel());
        children.add(rightTree);
      }
      return new Tree<String>(intermediateLabel, children);
    }

    /* (non-Javadoc)
	 * @see nlp.assignments.parsing.TreeAnnotator#unAnnotateTree(nlp.ling.Tree)
	 */
    @Override
	public Tree<String> unAnnotateTree(Tree<String> annotatedTree) {
      // Remove intermediate nodes (labels beginning with "@"
      // Remove all material on node labels which follow their base symbol (cuts at the leftmost -, ^, or : character)
      // Examples: a node with label @NP->DT_JJ will be spliced out, and a node with label NP^S will be reduced to NP

      Tree<String> debinarizedTree = Trees.spliceNodes(annotatedTree, new Filter<String>() {
        public boolean accept(String s) {
          return s.startsWith("@");
        }
      });
      Tree<String> unAnnotatedTree = (new Trees.FunctionNodeStripper()).transformTree(debinarizedTree);
      return unAnnotatedTree;
    }
  }