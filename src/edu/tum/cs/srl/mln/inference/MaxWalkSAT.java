/*
 * Created on Aug 5, 2009
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.tum.cs.srl.mln.inference;

import java.util.ArrayList;

import edu.tum.cs.logic.GroundAtom;
import edu.tum.cs.logic.PossibleWorld;
import edu.tum.cs.logic.sat.weighted.WeightedClausalKB;
import edu.tum.cs.srl.mln.MarkovRandomField;

public class MaxWalkSAT extends MAPInferenceAlgorithm {

	protected edu.tum.cs.logic.sat.weighted.MaxWalkSAT sat;
	
	public MaxWalkSAT(MarkovRandomField mrf) throws Exception {
		super(mrf);
        WeightedClausalKB wckb = new WeightedClausalKB(mrf, false);
        PossibleWorld state = new PossibleWorld(mrf.getWorldVariables());
        sat = new edu.tum.cs.logic.sat.weighted.MaxWalkSAT(wckb, state, mrf.getWorldVariables(), mrf.getDb());
	}
	
	@Override
	public double getResult(GroundAtom ga) {
		return sat.getBestState().get(ga.index) ? 1.0 : 0.0;
	}

	@Override
	public ArrayList<InferenceResult> infer(Iterable<String> queries, int maxSteps) throws Exception {
        sat.setMaxSteps(maxSteps);
        sat.run();	        
		return getResults(queries);
	}

	public PossibleWorld getSolution() {
		return sat.getBestState();
	}
	
	/**
	 * sets the probability of a greedy move
	 * @param p
	 */
	public void setP(double p) {
		sat.setP(p);
	}
	
	@Override
	public String getAlgorithmName() {
		return super.getAlgorithmName() + "[p=" + sat.getP() + "]";
	}

}
