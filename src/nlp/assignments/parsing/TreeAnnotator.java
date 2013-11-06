package nlp.assignments.parsing;

import nlp.ling.Tree;

interface TreeAnnotator {

	public abstract Tree<String> annotateTree(Tree<String> unAnnotatedTree);

	public abstract Tree<String> unAnnotateTree(Tree<String> annotatedTree);

}