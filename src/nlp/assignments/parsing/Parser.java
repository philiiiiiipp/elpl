package nlp.assignments.parsing;

import java.util.List;

import nlp.ling.Tree;

/**
   * Parsers are required to map sentences to trees.  How a parser is constructed and trained is not specified.
   */
  interface Parser {
    Tree<String> getBestParse(List<String> sentence);
    double getLogScore(Tree<String> tree);
  }