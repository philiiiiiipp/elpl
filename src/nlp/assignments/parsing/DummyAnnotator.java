package nlp.assignments.parsing;

import nlp.ling.Tree;

// Does nothing
public class DummyAnnotator implements TreeAnnotator {

	@Override
	public Tree<String> annotateTree(Tree<String> unAnnotatedTree) {
		return unAnnotatedTree;
	}

	@Override
	public Tree<String> unAnnotateTree(Tree<String> annotatedTree) {
		return annotatedTree;
	}

}
