package nlp.assignments.parsing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import nlp.ling.Tree;
import nlp.util.CounterMap;

class BaselineCkyParser implements Parser {

    CounterMap<List<String>, Tree<String>> knownParses;
    CounterMap<Integer, String> spanToCategories;

    TreeAnnotator annotator;

    Lexicon lexicon;
    Grammar grammar;

    UnaryClosure unaryClosure;

    static class Chart {

        static class EdgeInfo {
            double score;
            BinaryRule binaryRule = null;
            UnaryRule unaryRule = null;
            int mid = -1;

            EdgeInfo(double score) {
                this.score = score;
            }

            @Override
            public String toString() {
                if (binaryRule == null && unaryRule == null) {
                    return Double.toString(score);
                } else {
                    return score + ": " + "[ rule = " + (binaryRule != null ? binaryRule : unaryRule) + ", mid = " + mid + "]";
                }

            }
        }

        Map<Integer, Map<Integer, Map<String, EdgeInfo>>> chart = new HashMap<Integer, Map<Integer, Map<String, EdgeInfo>>>();

        Chart(int seqLength) {
            for (int i = 0; i < seqLength; i++) {
                chart.put(i, new HashMap<Integer, Map<String, EdgeInfo>>());
                for (int j = i + 1; j <= seqLength; j++) {
                    chart.get(i).put(j, new HashMap<String, EdgeInfo>());
                }
            }
        }

        void set(int i, int j, String label, double score) {
            chart.get(i).get(j).put(label, new EdgeInfo(score));
        }

        double get(int i, int j, String label) {
            Map<String, EdgeInfo> edgeScores = chart.get(i).get(j);
            if (!edgeScores.containsKey(label)) {
                return 0;
            } else {
                return edgeScores.get(label).score;
            }
        }

        Set<String> getAllCandidateLabels(int i, int j) {
            return chart.get(i).get(j).keySet();
        }

        String getBestLabel(int i, int j) {
            Map<String, EdgeInfo> edgeScores = chart.get(i).get(j);
            double bestScore = Double.NEGATIVE_INFINITY;
            String optLabel = null;
            for (String label : edgeScores.keySet()) {
                if (bestScore < edgeScores.get(label).score) {
                    optLabel = label;
                    bestScore = edgeScores.get(label).score;
                }
            }
            return optLabel;
        }

        void setBackPointer(int i, int j, String label, BinaryRule rule, int midPoint) {
            EdgeInfo edgeInfo = chart.get(i).get(j).get(label);
            edgeInfo.binaryRule = rule;
            edgeInfo.mid = midPoint;
        }
        void setBackPointer(int i, int j, String label, UnaryRule rule) {
            EdgeInfo edgeInfo = chart.get(i).get(j).get(label);
            edgeInfo.unaryRule = rule;
        }

        int getMidPoint(int i, int j, String label) {
            return chart.get(i).get(j).get(label).mid;
        }

        boolean hasBinaryRule(int i, int j, String label) {
            return chart.get(i).get(j).get(label).binaryRule != null;
        }
        BinaryRule getBinaryRule(int i, int j, String label) {
            return chart.get(i).get(j).get(label).binaryRule;
        }
        UnaryRule getUnaryRule(int i, int j, String label) {
            return chart.get(i).get(j).get(label).unaryRule;
        }

        @Override
        public String toString() {
            return chart.toString();
        }

    }

    void traverseBackPointersHelper(List<String> sent, Chart chart, int i, int j, Tree<String> currTree) {
        String parent = currTree.getLabel();

        if (j - i > 1) { // binary or unary rules
            
            List<Tree<String>> children;

            if (chart.hasBinaryRule(i, j, parent)) {
                
                BinaryRule rule = chart.getBinaryRule(i, j, parent);
                int mid = chart.getMidPoint(i, j, parent);
                children = new ArrayList<Tree<String>>(2);
    
                Tree<String> t1 = new Tree<String>(rule.getLeftChild());
                traverseBackPointersHelper(sent, chart, i, mid, t1);
                children.add(t1);
    
                Tree<String> t2 = new Tree<String>(rule.getRightChild());
                traverseBackPointersHelper(sent, chart, mid, j, t2);
                children.add(t2);
                
            } else {

                UnaryRule rule = chart.getUnaryRule(i, j, parent);             
                children = new ArrayList<Tree<String>>(1);

                Tree<String> t = new Tree<String>(rule.getChild());
                traverseBackPointersHelper(sent, chart, i, j, t);
                children.add(t);

            }

            currTree.setChildren(children);

        } else { // preterminal production
            assert j - i == 1;

            Tree<String> termProd = new Tree<String>(sent.get(i));
            currTree.setChildren(Collections.singletonList(termProd));
        }

    }

    // traverse back pointers and create a tree
    Tree<String> traverseBackPointers(List<String> sentence, Chart chart) {

        Tree<String> annotatedBestParse;
        if (!chart.getAllCandidateLabels(0, sentence.size()).contains("ROOT")) {
            // this line is here only to make sure that a baseline without binary rules can output
            // something
            annotatedBestParse = new Tree<String>(chart.getBestLabel(0, sentence.size()));
        } else {
            // in reality we always want to start with the ROOT symbol of the grammar
            annotatedBestParse = new Tree<String>("ROOT");
        }
        traverseBackPointersHelper(sentence, chart, 0, sentence.size(), annotatedBestParse);
        return annotatedBestParse;

    }

    public Tree<String> getBestParse(List<String> sentence) {

        // note that chart.get(i, j, c) translates to chart[i][j][c] we used in the slides

        Chart chart = new Chart(sentence.size());

        // preterminal rules
        for (int k = 0; k < sentence.size(); k++) {
            for (String preterm : lexicon.getAllTags()) {
                chart.set(k, k + 1, preterm, lexicon.scoreTagging(sentence.get(k), preterm));

            }
        }

        // CKY for binary and unary trees
        for (int max = 1; max <= sentence.size(); max++) {
            for (int min = max - 1; min >= 0; min--) {
                for (String parent : grammar.states) {
                    double bestScore = Double.NEGATIVE_INFINITY;
                    int optMid = -1;
                    
                    BinaryRule optBinaryRule = null;
                    for (BinaryRule rule : grammar.getBinaryRulesByParent(parent)) {
                        for (int mid = min + 1; mid < max; mid++) {
                            double score1 = chart.get(min, mid, rule.getLeftChild());
                            double score2 = chart.get(mid, max, rule.getRightChild());
                            double currScore = score1 * score2 * rule.getScore();
                            if (currScore > bestScore) {
                                bestScore = currScore;
                                optMid = mid;
                                optBinaryRule = rule;
                            }
                        }
                    }
                    
                    UnaryRule optUnaryRule = null;
                    for (UnaryRule rule : unaryClosure.getClosedUnaryRulesByParent(parent)) {
                        double score = chart.get(min, max, rule.getChild());
                        double currScore = score * rule.getScore();
                        if (currScore > bestScore) {
                            bestScore = currScore;
                            optBinaryRule = null;
                            optUnaryRule = rule;
                        }
                    }
                    
                    if (bestScore != Double.NEGATIVE_INFINITY) {
                        chart.set(min, max, parent, bestScore);
                        if (optBinaryRule != null) {
                            chart.setBackPointer(min, max, parent, optBinaryRule, optMid);
                        } else {
                            chart.setBackPointer(min, max, parent, optUnaryRule);
                        }
                    }
                }
            }
        }

        // use back pointers to create a tree
        Tree<String> annotatedBestParse = traverseBackPointers(sentence, chart);

        return annotator.unAnnotateTree(annotatedBestParse);
    }

    public BaselineCkyParser(List<Tree<String>> trainTrees, TreeAnnotator annotator) {

        this.annotator = annotator;

        System.out.print("Annotating / binarizing training trees ... ");
        List<Tree<String>> annotatedTrainTrees = annotateTrees(trainTrees);

        System.out.println("done.");

        System.out.print("Building grammar ... ");
        grammar = new Grammar(annotatedTrainTrees);
        lexicon = new Lexicon(annotatedTrainTrees);
        System.out.println("done. (" + grammar.getStates().size() + " states)");

        unaryClosure = new UnaryClosure(grammar);

    }

    private List<Tree<String>> annotateTrees(List<Tree<String>> trees) {
        List<Tree<String>> annotatedTrees = new ArrayList<Tree<String>>();
        for (Tree<String> tree : trees) {
            annotatedTrees.add(annotator.annotateTree(tree));
        }
        return annotatedTrees;
    }

    @Override
    public double getLogScore(Tree<String> annotatedTree) {
        final List<Tree<String>> children = annotatedTree.getChildren();
        double logScore = 0.0;

        // The stop condition gets the score of the leaf word for this position
        if (annotatedTree.isPreTerminal()) {
            return Math.log(lexicon.scoreTagging(children.get(0).getLabel(), annotatedTree.getLabel()));
        }

        // If we're not pre-terminal, we look at how many children we have
        if (children.size() == 1) {
            // When there's only one child, use unary rules
            final List<UnaryRule> parentRules = grammar.getUnaryRulesByParent(annotatedTree.getLabel());
            final List<UnaryRule> childRules = grammar.getUnaryRulesByChild(children.get(0).getLabel());

            // Intersect the rules
            final List<UnaryRule> currentRules = new ArrayList<>(parentRules);
            currentRules.retainAll(childRules);

            // If there are non-existing sentence shapes the tree is not possible
            if (currentRules.isEmpty()) {
                return Double.NEGATIVE_INFINITY;
            }

            // Return its score and its child's score
            logScore += Math.log(currentRules.get(0).getScore()) + getLogScore(children.get(0));

        } else {
            // When there are two children, use binary rules
            final List<BinaryRule> parentRules = grammar.getBinaryRulesByParent(annotatedTree.getLabel());
            final List<BinaryRule> leftChildRules = grammar.getBinaryRulesByLeftChild(children.get(0).getLabel());
            final List<BinaryRule> rightChildRules = grammar.getBinaryRulesByRightChild(children.get(1).getLabel());

            // Intersect the rules
            final List<BinaryRule> currentRules = new ArrayList<>(parentRules);
            currentRules.retainAll(leftChildRules);
            currentRules.retainAll(rightChildRules);

            // If there are non-existing sentence shapes the tree is not possible
            if (currentRules.isEmpty()) {
                return Double.NEGATIVE_INFINITY;
            }

            // Return its score and its children's score
            logScore += Math.log(currentRules.get(0).getScore()) + getLogScore(children.get(0))
                    + getLogScore(children.get(1));
        }

        return logScore;
    }
}
