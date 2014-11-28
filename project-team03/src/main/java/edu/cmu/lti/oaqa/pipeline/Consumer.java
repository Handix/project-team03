package edu.cmu.lti.oaqa.pipeline;

import org.apache.uima.cas.CAS;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.collection.CasConsumer_ImplBase;
import org.apache.uima.resource.ResourceProcessException;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import json.JsonCollectionReaderHelper;
import json.gson.Snippet;
import json.gson.TestQuestion;
import json.gson.Triple;

import org.apache.uima.cas.CASException;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.TOP;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;

import util.MyUtils;
import util.TypeUtil;
import edu.cmu.lti.oaqa.type.input.Question;
import edu.cmu.lti.oaqa.type.kb.Concept;
import edu.cmu.lti.oaqa.type.retrieval.ConceptSearchResult;
import edu.cmu.lti.oaqa.type.retrieval.Document;
import edu.cmu.lti.oaqa.type.retrieval.Passage;
import edu.cmu.lti.oaqa.type.retrieval.TripleSearchResult;

public class Consumer extends CasConsumer_ImplBase {
  static final String PUBPREFIX = "http://www.ncbi.nlm.nih.gov/pubmed/";

  // File golden_file;//refer to the file that has golden standard answer
  String goldPath, outputPath;

  Map<String, List<String>> docMaps;

  Map<String, List<String>> conceptMaps;

  Map<String, List<Triple>> tripleMaps;
  
  Map<String, List<Snippet>> snippetMaps;

  List<TestQuestion> goldStandards;

  JsonCollectionReaderHelper jsHelper;

  @Override
  public void initialize() throws ResourceInitializationException {

    // read goldStandard by JsonCollectionReader
    jsHelper = new JsonCollectionReaderHelper();
    goldStandards = jsHelper.testRun();

    // for each question, we store the documents, concepts, triple info corresponding to each
    // question
    docMaps = new HashMap<String, List<String>>();
    conceptMaps = new HashMap<String, List<String>>();
    tripleMaps = new HashMap<String, List<Triple>>();
    snippetMaps = new HashMap<String, List<Snippet>>();

    for (int i = 0; i < goldStandards.size(); i++) {
      docMaps.put(goldStandards.get(i).getId(), goldStandards.get(i).getDocuments());
      conceptMaps.put(goldStandards.get(i).getId(), goldStandards.get(i).getConcepts());
      tripleMaps.put(goldStandards.get(i).getId(), goldStandards.get(i).getTriples());
      snippetMaps.put(goldStandards.get(i).getId(), goldStandards.get(i).getSnippets());
    }

  }

  @Override
  public void processCas(CAS aCAS) throws ResourceProcessException {

    outputPath = "/MyOutput.json";// the path of output file

    JCas jcas = null;
    try {
      jcas = aCAS.getJCas();
    } catch (CASException e) {
      e.printStackTrace();
    }
    String curQId = TypeUtil.getQuestion(jcas).getId();

    // For the documents:
    Collection<Document> documents = TypeUtil.getRankedDocuments(jcas);
    LinkedList<Document> documentList = new LinkedList<Document>();
    documentList.addAll(documents);
    
    List<String> docResult = docMaps.get(curQId);
    //System.out.println(documentList.size());
    int docTotalPositive = 0;
    double totalPrecision = 0.0;
    double docPrecision = 0.0;
    for (int i = 0; i < documentList.size(); i++) {
      if (docResult.contains(documentList.get(i).getUri())) {
//        System.out.println(documentList.get(i).getRank()+":"+documentList.get(i).getUri());
        docTotalPositive++;
        totalPrecision += (docTotalPositive * 1.0) / ((i + 1) * 1.0);
      }
    }
    if (docTotalPositive == 0) {
      docPrecision = 0.0;
    } else {
      docPrecision = totalPrecision / (docTotalPositive * 1.0);
    }
    System.out.println("docPrecision:" + docPrecision);

    // For the Concepts:
    Collection<ConceptSearchResult> concepts = TypeUtil.getRankedConceptSearchResults(jcas);

    LinkedList<ConceptSearchResult> conceptList = new LinkedList<ConceptSearchResult>();
    conceptList.addAll(concepts);

    List<String> collectionResult = conceptMaps.get(curQId);
    //System.out.println("curID:" + curQId);
    int conceptTotalPositive = 0;
    double concepttotalPrecision = 0.0;
    double conceptPrecision = 0.0;

    for (int i = 0; i < conceptList.size(); i++) {
      if (collectionResult.contains(conceptList.get(i).getUri())) {
        //System.out.println(i + ":" + conceptList.get(i).getUri());
        conceptTotalPositive++;
        concepttotalPrecision += (conceptTotalPositive * 1.0) / ((i + 1) * 1.0);
      }
    }
    if (conceptTotalPositive == 0) {
      conceptPrecision = 0.0;
    } else {
      conceptPrecision = concepttotalPrecision / (conceptTotalPositive * 1.0);
    }

    System.out.println("ConceptPrecision:" + conceptPrecision);

    // For collection
    Collection<TripleSearchResult> triples = TypeUtil.getRankedTripleSearchResults(jcas);
    LinkedList<TripleSearchResult> tripleList = new LinkedList<TripleSearchResult>();
    tripleList.addAll(triples);
    

    //System.out.println("--------------I'm a hualiliful segmentation line-------------");
    Collection<Passage> snippets = TypeUtil.getRankedPassages(jcas);
    LinkedList<Passage> snippetList = new LinkedList<Passage>();
    snippetList.addAll(snippets);
    LinkedList<Snippet> test = new LinkedList<Snippet>();
    for(Passage p:snippetList)
      test.add(new Snippet(p.getUri(), p.getText(), p.getOffsetInBeginSection(), p.getOffsetInEndSection(),
              p.getBeginSection(), p.getEndSection(),p.getTitle(), p.getScore()));
    List<Snippet> gold = snippetMaps.get(curQId);
    System.out.println("snippets golden standard size:"+gold.size());
    double precision = MyUtils.calcSnippetPrecision(gold, test);
    double recall = MyUtils.calcSnippetRecall(gold, test);
    System.out.println("snippet precision:"+precision);
    System.out.println("snippet recall:"+recall);
    

    System.out.println("Done");

  }
}
