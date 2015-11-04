package doser.entitydisambiguation.algorithms.collective.hybrid;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;

import org.apache.commons.collections15.Factory;
import org.apache.commons.collections15.functors.MapTransformer;
import org.apache.commons.math.stat.descriptive.SummaryStatistics;

import doser.lucene.features.EnCenExtFeatures;
import edu.uci.ics.jung.algorithms.scoring.PageRankWithPriors;
import edu.uci.ics.jung.graph.DirectedSparseMultigraph;

public class Word2VecDisambiguator extends Word2VecPageRank {

	private static final int MAXIMUMCANDIDATESPERSF = 8;

	public List<SurfaceForm> origList;

	public Word2VecDisambiguator(EnCenExtFeatures featureDefinition,
			List<SurfaceForm> rep, Word2Vec w2v) {
		super(featureDefinition, rep, w2v);
		this.origList = new ArrayList<SurfaceForm>();
	}

	@Override
	public void setup() {
		this.graph = new DirectedSparseMultigraph<Vertex, Edge>();
		this.edgeWeights = new HashMap<Edge, Number>();
		this.edgeFactory = new Factory<Integer>() {
			int i = 0;

			public Integer create() {
				return i++;
			}
		};

		for (SurfaceForm sf : repList) {
			SurfaceForm clone = (SurfaceForm) sf.clone();
			this.origList.add(clone);
		}

		this.disambiguatedSurfaceForms = new BitSet(repList.size());
		for (int i = 0; i < repList.size(); i++) {
			if (repList.get(i).getCandidates().size() <= 1) {
				this.disambiguatedSurfaceForms.set(i);
			}
		}
		buildMainGraph();
	}

	@Override
	protected PageRankWithPriors<Vertex, Edge> performPageRank() {
		PageRankWithPriors<Vertex, Edge> pr = new PageRankWithPriors<Vertex, Edge>(
				graph, MapTransformer.getInstance(edgeWeights),
				getRootPrior(graph.getVertices()), 0.09);
		pr.setMaxIterations(250);
		pr.evaluate();
		return pr;
	}

	@Override
	public boolean analyzeResults(PageRankWithPriors<Vertex, Edge> pr) {
		boolean disambiguationStop = true;
		Collection<Vertex> vertexCol = graph.getVertices();
		for (int i = 0; i < repList.size(); i++) {
			if (!disambiguatedSurfaceForms.get(i)) {
				int qryNr = repList.get(i).getQueryNr();
				double maxScore = 0;
				SummaryStatistics stats = new SummaryStatistics();
				String tempSolution = "";
				List<Candidate> scores = new ArrayList<Candidate>();
				for (Vertex v : vertexCol) {
					if (v.getEntityQuery() == qryNr && v.isCandidate()) {
						scores.add(new Candidate(v.getUris().get(0), pr
								.getVertexScore(v)));
						double score = Math.abs(pr.getVertexScore(v));
						stats.addValue(score);
						if (score > maxScore) {
							tempSolution = v.getUris().get(0);
							maxScore = score;
						}
					}
				}
				SurfaceForm rep = repList.get(i);
				SurfaceForm clone = origList.get(i);
				Collections.sort(scores, Collections.reverseOrder());
				double secondMax = scores.get(1).score;
				
				List<String> newCandidates = new ArrayList<String>();
				for(int j = 0; j < MAXIMUMCANDIDATESPERSF; j++) {
					if(scores.size() > j) {
						newCandidates.add(scores.get(j).can);
					} else {
						break;
					}
				}

				if (!Double.isInfinite(maxScore)) {
					double avg = stats.getMean();
					double threshold = computeThreshold(avg, maxScore);
					if (secondMax < threshold) {
						updateGraph(rep.getCandidates(), tempSolution,
								rep.getQueryNr());
						rep.setDisambiguatedEntity(tempSolution);
						clone.setDisambiguatedEntity(tempSolution);
						disambiguatedSurfaceForms.set(i);
						disambiguationStop = false;
						break;
					} else {
						clone.setCandidates(newCandidates);
					}
				}
			}
		}
		return disambiguationStop;
	}

	/**
	 * Threshold Computation // IMPORTANT DISAMBIGUATION PARAMETER
	 * 
	 * @param avg
	 * @param highest
	 * @return
	 */
	private double computeThreshold(double avg, double highest) {
		double diff = highest - avg;
		double min = diff * 0.5;
		return highest - min;
	}

	@Override
	public List<SurfaceForm> getRepresentation() {
		return this.origList;
	}

	class Candidate implements Comparable<Candidate> {
		private double score;
		private String can;

		Candidate(String can, double score) {
			super();
			this.score = score;
			this.can = can;
		}

		@Override
		public int compareTo(Candidate o) {
			if (score < o.score) {
				return -1;
			} else if (score > o.score) {
				return 1;
			} else {
				return 0;
			}
		}
	}
}
