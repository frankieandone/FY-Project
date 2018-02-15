package com.merzadyan;

import edu.stanford.nlp.dcoref.CorefChain;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.util.CoreMap;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import static edu.stanford.nlp.dcoref.CorefCoreAnnotations.CorefChainAnnotation;
import static edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagAnnotation;
import static edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import static edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import static edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import static edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import static edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation;

public class SentientAnalyser {
    public static void analyse(String text) {
        // TODO: prevent an OOM (Out Of Memory) issue when the input text is too large.
        if (text.length() > 300000) {
            return;
        }
        
        // Creates a StanfordCoreNLP object, with POS tagging, lemmatization, NER, parsing, coreference resolution, etc.
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize, ssplit, pos, lemma, ner, parse, dcoref");
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
        
        // Create an empty Annotation just with the given text.
        Annotation document = new Annotation(text);
        
        // Run all Annotators on this text.
        pipeline.annotate(document);
        
        List<CoreMap> sentences = document.get(SentencesAnnotation.class);
        
        for (CoreMap sentence : sentences) {
            for (CoreLabel token : sentence.get(TokensAnnotation.class)) {
                String word = token.get(TextAnnotation.class);
                String partOfSpeech = token.get(PartOfSpeechAnnotation.class);
                String namedEntityRecognition = token.get(NamedEntityTagAnnotation.class);
                System.out.println("word: " + word + " POS: " + partOfSpeech + " NER: " + namedEntityRecognition +
                        " lemma: " + token.get(CoreAnnotations.LemmaAnnotation.class));
                String lemma = token.get(CoreAnnotations.LemmaAnnotation.class);
            }
            Tree tree = sentence.get(TreeCoreAnnotations.TreeAnnotation.class);
            SemanticGraph dependencies = sentence.get(EnhancedDependenciesAnnotation.class);
        }
        
        Map<Integer, CorefChain> graph = document.get(CorefChainAnnotation.class);
    }
    
    public static void main(String[] args) {
        final String input = "Frankie kicks the ball into the goal and scores one point which puts his team in the lead.";
        SentientAnalyser.analyse(input);
    }
}