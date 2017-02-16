package ahrd.controller;

import static ahrd.controller.Settings.getSettings;
import static ahrd.controller.Utils.randomMultipleOfOneTenth;
import static ahrd.controller.Utils.randomMultipleOfOne;
import static ahrd.controller.Utils.randomMultipleOfTen;
import static ahrd.controller.Utils.randomSaveSubtract;
import static ahrd.controller.Utils.roundToNDecimalPlaces;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The following parameters are those subject to optimization. They are stored
 * wrapped in a distinct class from Settings in order to enable random
 * generation and scoring of these parameters.
 * 
 * @author Kathrin Klee, Asis Hallab
 */
public class Parameters implements Cloneable, Comparable<Parameters> {

	private Double tokenScoreBitScoreWeight;
	private Double tokenScoreDatabaseScoreWeight;
	private Double tokenScoreOverlapScoreWeight;

	private Map<String, Map<String, String>> blastDbParameters = new HashMap<String, Map<String, String>>();
	/**
	 * If we test different settings in the parameter-space, remember the
	 * average evaluation-score (objective-function).
	 */
	private Double avgEvaluationScore;
	/**
	 * If we test different settings in the parameter-space, remember the
	 * average Precision (PPV: Positive-Predictive-Value).
	 */
	private Double avgPrecision;
	/**
	 * If we test different settings in the parameter-space, remember the
	 * average Recall (TPR: True-Positives-Rate).
	 */
	private Double avgRecall;
	/**
	 * In order to increase hill-climbing during optimization, the last mutated
	 * parameter is remembered. So that the next neighbor can be generated by
	 * mutating the same parameter again -with some probability-, if and only
	 * if, the last mutation resulted in an increase of the average evaluation
	 * score.
	 */
	private Integer lastMutatedParameter = null;
	/**
	 * In order to fine tune the genetic algorithm for training of parameters
	 * it can be useful to remember the origin of the parameter set in the previous 
	 * generation.  
	 */
	private String origin;
	
	/**
	 * 
	 * @param sortedDistinctBlastDatabaseNames
	 * @return
	 */
	public static Parameters randomParameters(
			List<String> sortedDistinctBlastDatabaseNames) {
		Parameters out = new Parameters();
		out.setTokenScoreBitScoreWeight(randomMultipleOfOneTenth());
		out.setTokenScoreDatabaseScoreWeight(randomMultipleOfOneTenth());
		out.setTokenScoreOverlapScoreWeight(randomMultipleOfOneTenth());
		// normalize the randomly chosen weights:
		out.normalizeTokenScoreWeights();
		// Init BlastDbs' Parameters:
		for (String blastDbName : sortedDistinctBlastDatabaseNames) {
			out.setDescriptionScoreBitScoreWeight(blastDbName,
					randomMultipleOfOne().toString());
			out.setBlastDbWeight(blastDbName, randomMultipleOfTen().toString());
		}
		// Set origin for genetic training output
		out.setOrigin("random");
		return out;
	}

	/**
	 * If the last optimization step was done with better performing parameters,
	 * randomly decide to mutate the same Parameter to generate a new Neighbor
	 * in Parameter-Space. By this simulated annealing walks more likely uphill
	 * in Parameter-Score-Space. The probability P('Mutate same Parameter') :=
	 * 0, if score was not increased, (exp(-(1-increase.score))+s)/(exp(0)+s)
	 * else
	 * 
	 * @param increaseInAvgEvaluationScore
	 *            - The difference in average evaluation scores between the
	 *            currently accepted parameters and the currently evaluated
	 *            ones.
	 * @return Double
	 */
	public static Double pMutateSameParameter(
			Double increaseInAvgEvaluationScore) {
		double p = 0.0;
		if (increaseInAvgEvaluationScore != null
				&& increaseInAvgEvaluationScore > 0.0) {
			Double s = getSettings().getpMutateSameParameterScale();
			p = (Math.exp(-(1.0 - increaseInAvgEvaluationScore)) + s)
					/ (Math.exp(0) + s);
		}
		return p;
	}

	public int parameterToMutateRandomIndex() {
		int randParamInd = 0;
		// How many Parameters can be mutated?
		int noOfParams = 3 + 2 * getBlastDatabases().size();
		// Randomly choose a parameter to change:
		Random rand = Utils.random;
		randParamInd = rand.nextInt(noOfParams);
		return randParamInd;
	}

	/**
	 * Clones this instance and changes one of the following <em>six</em>
	 * fields, AHRD-parameters, in order to calculate AHRD's performance with
	 * different parameters:
	 * <ul>
	 * <li>Token-Score-Bit-Score-Weight</li>
	 * <li>Token-Score-Database-Score-Weight</li>
	 * <li>Token-Score-Overlap-Score-Weight</li>
	 * <li>Blast-Database-Weight</li>
	 * <li>Description-Score-Bit-Score-Weight (different for each
	 * Blast-Database)</li>
	 * <li>Description-Score-Relative-Description-Frequency-Weight</li>
	 * </ul>
	 * 
	 * @NOTE: The three <em>Token-Score-Weights</em> <strong>must</strong> sum
	 *        up to 1.
	 * 
	 * @param Double
	 *            diffEvalScoreToLastEvaluatedParams - To increase probability
	 *            to perform 'hill climbing' during optimization, a good
	 *            increase in evaluation score increases likelihood to mutate
	 *            the last mutated parameter again.
	 * @return clone of this instance with one of the above mentioned parameters
	 *         <em>slightly</em> changed.
	 */
	public Parameters neighbour(Double diffEvalScoreToLastEvaluatedParams) {
		Parameters ngb = this.clone();
		// Randomly decide to mutate the same parameter again, if last mutation
		// resulted in an increase of score:
		Integer randParamToMutate = getLastMutatedParameter();
		if (!(diffEvalScoreToLastEvaluatedParams != null
				&& diffEvalScoreToLastEvaluatedParams > 0.0
				&& randParamToMutate != null && Utils.random.nextDouble() <= pMutateSameParameter(diffEvalScoreToLastEvaluatedParams))) {
			// Do not mutate the same parameter again, but randomly choose one
			// to change:
			randParamToMutate = parameterToMutateRandomIndex();
		}
		// Once a parameter is chosen by its index, mutate it:
		if (randParamToMutate < 3) {
			// Mutate one of the four parameters independent of the number of
			// Blast-Databases:
			if (randParamToMutate == 0)
				ngb.mutateTokenScoreBitScoreWeight();
			else if (randParamToMutate == 1)
				ngb.mutateTokenScoreDatabaseScoreWeight();
			else if (randParamToMutate == 2)
				ngb.mutateTokenScoreOverlapScoreWeight();
		} else {
			// Mutate a Parameter associated with a Blast-Database:
			int indOfBlastDbToMutate = randParamToMutate - 3;
			int blastDbIndex = (new Double(
					Math.floor(indOfBlastDbToMutate / 2.0))).intValue();
			String blastDbToMutate = getSettings().getSortedBlastDatabases()
					.get(blastDbIndex);
			boolean mutateWeight = (indOfBlastDbToMutate % 2 == 0);
			if (mutateWeight)
				ngb.mutateBlastDatabaseWeight(blastDbToMutate);
			else
				ngb.mutateDescriptionScoreBitScoreWeight(blastDbToMutate);
		}
		// Remember what made the neighbor different from its parent:
		ngb.setLastMutatedParameter(randParamToMutate);
		// Reset average evaluation score
		ngb.setAvgEvaluationScore(null);
		ngb.setAvgPrecision(null);
		ngb.setAvgRecall(null);
		// Set origin for genetic training output
		ngb.setOrigin("mutation");
		return ngb;
	}

	public String randomBlastDatabaseName() {
		Random rand = Utils.random;
		int randBlastDbInd = rand.nextInt(getBlastDatabases().size());
		List<String> blastDbNamesList = new ArrayList<String>(
				getBlastDatabases());
		return blastDbNamesList.get(randBlastDbInd);
	}

	/**
	 * @param blastDatabaseName
	 */
	public void mutateBlastDatabaseWeight(String blastDatabaseName) {
		Long bdbw = getBlastDbWeight(blastDatabaseName).longValue();
		Long mutateBy = mutateBlastDatabaseWeightBy();
		if (randomSaveSubtract(bdbw, mutateBy))
			bdbw -= mutateBy;
		else
			bdbw += mutateBy;

		setBlastDbWeight(blastDatabaseName, bdbw.toString());
	}

	/**
	 * @param blastDatabaseName
	 */
	public void mutateDescriptionScoreBitScoreWeight(String blastDatabaseName) {
		Double bsw = getDescriptionScoreBitScoreWeight(blastDatabaseName);
		Double mutateBy = mutatePercentageBy();
		if (randomSaveSubtract(bsw, mutateBy))
			bsw -= mutateBy;
		else
			bsw += mutateBy;
		setDescriptionScoreBitScoreWeight(blastDatabaseName, bsw.toString());
	}

	/**
	 * Normalizes the three weights appearing in the Token-Score-Formula, so
	 * they sum up to 1.0
	 */
	public void normalizeTokenScoreWeights() {
		double s = roundToNDecimalPlaces(getTokenScoreBitScoreWeight()
				+ getTokenScoreDatabaseScoreWeight()
				+ getTokenScoreOverlapScoreWeight(), 4);
		setTokenScoreBitScoreWeight(roundToNDecimalPlaces(
				getTokenScoreBitScoreWeight() / s, 4));
		setTokenScoreDatabaseScoreWeight(roundToNDecimalPlaces(
				getTokenScoreDatabaseScoreWeight() / s, 4));
		setTokenScoreOverlapScoreWeight(roundToNDecimalPlaces(
				getTokenScoreOverlapScoreWeight() / s, 4));
	}

	/**
	 * Diminishes or increases Token-Score-Bit-Score-Weight by
	 * PERCENTAGE_MUTATOR_SEED and normalizes the other two weights in the
	 * Token-Score-Formula.
	 */
	public void mutateTokenScoreBitScoreWeight() {
		Double bsw = getTokenScoreBitScoreWeight();
		Double mutateBy = mutatePercentageBy();
		if (randomSaveSubtract(bsw, mutateBy))
			bsw = bsw - mutateBy;
		else
			bsw = bsw + mutateBy;
		setTokenScoreBitScoreWeight(bsw);
		// normalize:
		normalizeTokenScoreWeights();
	}

	/**
	 * Diminishes or increases Token-Score-Database-Score-Weight by
	 * PERCENTAGE_MUTATOR_SEED and normalizes the other two weights in the
	 * Token-Score-Formula.
	 */
	public void mutateTokenScoreDatabaseScoreWeight() {
		Double dbsw = getTokenScoreDatabaseScoreWeight();
		Double mutateBy = mutatePercentageBy();
		if (randomSaveSubtract(dbsw, mutateBy))
			dbsw = dbsw - mutateBy;
		else
			dbsw = dbsw + mutateBy;
		setTokenScoreDatabaseScoreWeight(dbsw);
		// normalize:
		normalizeTokenScoreWeights();
	}

	/**
	 * Diminishes or increases Token-Score-Overlap-Score-Weight by
	 * PERCENTAGE_MUTATOR_SEED and normalizes the other two weights in the
	 * Token-Score-Formula.
	 */
	public void mutateTokenScoreOverlapScoreWeight() {
		Double osw = getTokenScoreOverlapScoreWeight();
		Double mutateBy = mutatePercentageBy();
		if (randomSaveSubtract(osw, mutateBy))
			osw = osw - mutateBy;
		else
			osw = osw + mutateBy;
		setTokenScoreOverlapScoreWeight(osw);
		// normalize:
		normalizeTokenScoreWeights();
	}

	/**
	 * Returns the absolute of a random Gaussian distributed value with mean
	 * Settings.MUTATOR_MEAN and deviation Settings.MUTATOR_DEVIATION.
	 * 
	 * @Note: Only the <strong>absolute</strong> of the random Gaussian is
	 *        returned, as to subtract or add is decided elsewhere.
	 * 
	 * @return Double - The value to add or subtract from the Percentage to
	 *         mutate.
	 */
	public Double mutatePercentageBy() {
		return Math.abs(Utils.random.nextGaussian()
				* getSettings().getMutatorDeviation()
				+ getSettings().getMutatorMean());
	}

	/**
	 * Returns 100 multiplied with the rounded up absolute of a random Gaussian
	 * distributed value with mean Settings.MUTATOR_MEAN and deviation
	 * Settings.MUTATOR_DEVIATION.
	 * 
	 * @Note: Only the <strong>absolute</strong> of the random Gaussian is
	 *        returned, as to subtract or add is decided elsewhere.
	 * 
	 * @return Long - The value to add or subtract from the Percentage to
	 *         mutate.
	 */
	public Long mutateBlastDatabaseWeightBy() {
		return new Double(Math.ceil(100.0 * mutatePercentageBy())).longValue();
	}
	
	/**
	 * Creates an offspring with a random recombination of the current parameters and a given parameter set.
	 * 
	 * @NOTE: The three <em>Token-Score-Weights</em> are normalized to sum up to 1.
	 * 
	 * @param partner - The Parameters to recombine the current ones with 
	 * 
	 * @return The random offspring of the current and given Parameters  
	 */
	public Parameters recombine(Parameters partner) {
		Parameters offspring = this.clone();
		Random rand = Utils.random;
		if(rand.nextBoolean())
			offspring.setTokenScoreBitScoreWeight(partner.getTokenScoreBitScoreWeight());
		if(rand.nextBoolean())
			offspring.setTokenScoreDatabaseScoreWeight(partner.getTokenScoreDatabaseScoreWeight());
		if(rand.nextBoolean())
			offspring.setTokenScoreOverlapScoreWeight(partner.getTokenScoreOverlapScoreWeight());
		for (String blastDbName : getSettings().getSortedBlastDatabases()) {
			if(rand.nextBoolean())
				offspring.setDescriptionScoreBitScoreWeight(blastDbName, partner.getDescriptionScoreBitScoreWeight(blastDbName).toString());
			if(rand.nextBoolean())
				offspring.setBlastDbWeight(blastDbName, partner.getBlastDbWeight(blastDbName).toString());
		}
		offspring.normalizeTokenScoreWeights();
		offspring.setAvgEvaluationScore(null);
		offspring.setAvgPrecision(null);
		offspring.setAvgRecall(null);
		// Set origin for genetic training
		offspring.setOrigin("recombination");
		return offspring;
	}

	/**
	 * Returns a clone of this instance.
	 */
	public Parameters clone() {
		Parameters clone;
		try {
			clone = (Parameters) super.clone();
		} catch (CloneNotSupportedException e) {
			e.printStackTrace(System.err);
			return null;
		}
		// Clone the Blast-Database-Parameters-Map and Values:
		Map<String, Map<String, String>> blastDbSettings = new HashMap<String, Map<String, String>>();
		for (String blastDb : getBlastDbParameters().keySet()) {
			blastDbSettings.put(blastDb, new HashMap<String, String>());
			for (String iterKey : getParametersOfBlastDb(blastDb).keySet()) {
				blastDbSettings.get(blastDb)
						.put(new String(iterKey),
								new String(getParametersOfBlastDb(blastDb).get(
										iterKey)));
			}
		}
		clone.blastDbParameters = blastDbSettings;
		return clone;
	}

	@Override
	public boolean equals(Object eql) {
		if (!(eql instanceof Parameters))
			return false;
		// We are dealing with an Instance of Parameters:
		boolean areBlastParamsEqual = true;
		for (String blastDb : getBlastDbParameters().keySet()) {
			for (String iterKey : getParametersOfBlastDb(blastDb).keySet()) {
				areBlastParamsEqual = areBlastParamsEqual
						&& getParametersOfBlastDb(blastDb).get(iterKey).equals(
								((Parameters) eql).getParametersOfBlastDb(
										blastDb).get(iterKey));
			}
		}
		return areBlastParamsEqual
				&& ((Parameters) eql).getTokenScoreBitScoreWeight().equals(
						this.getTokenScoreBitScoreWeight())
				&& ((Parameters) eql).getTokenScoreDatabaseScoreWeight()
						.equals(this.getTokenScoreDatabaseScoreWeight())
				&& ((Parameters) eql).getTokenScoreOverlapScoreWeight().equals(
						this.getTokenScoreOverlapScoreWeight());
	}

	@Override
	public int hashCode() {
		String hashSrc = "";
		for (String blastDb : getBlastDbParameters().keySet()) {
			for (String iterKey : getParametersOfBlastDb(blastDb).keySet()) {
				hashSrc += getParametersOfBlastDb(blastDb).get(iterKey);
			}
		}
		hashSrc += getTokenScoreBitScoreWeight()
				+ getTokenScoreDatabaseScoreWeight()
				+ getTokenScoreOverlapScoreWeight();
		return hashSrc.hashCode();
	}

	/**
	 * @return Set<String> the names of the blast-databases used in the current
	 *         AHRD-Run.
	 */
	public Set<String> getBlastDatabases() {
		return getBlastDbParameters().keySet();
	}

	protected Map<String, String> getParametersOfBlastDb(String blastDbName) {
		Map<String, String> out = getBlastDbParameters().get(blastDbName);
		// Init new on first request:
		if (out == null) {
			out = new HashMap<String, String>();
			getBlastDbParameters().put(blastDbName, out);
		}
		return out;
	}

	public Integer getBlastDbWeight(String blastDatabaseName) {
		return Integer.parseInt(getParametersOfBlastDb(blastDatabaseName).get(
				Settings.BLAST_DB_WEIGHT_KEY));
	}

	public void setBlastDbWeight(String blastDatabaseName, String bdbw) {
		getParametersOfBlastDb(blastDatabaseName).put(
				Settings.BLAST_DB_WEIGHT_KEY, bdbw);
	}

	public Double getDescriptionScoreBitScoreWeight(String blastDatabaseName) {
		return Double.parseDouble(getParametersOfBlastDb(blastDatabaseName)
				.get(Settings.DESCRIPTION_SCORE_BIT_SCORE_WEIGHT));
	}

	public void setDescriptionScoreBitScoreWeight(String blastDatabaseName,
			String dsbsw) {
		getParametersOfBlastDb(blastDatabaseName).put(
				Settings.DESCRIPTION_SCORE_BIT_SCORE_WEIGHT, dsbsw);
	}

	public Double getTokenScoreBitScoreWeight() {
		return tokenScoreBitScoreWeight;
	}

	public void setTokenScoreBitScoreWeight(Double tokenScoreBitScoreWeight) {
		this.tokenScoreBitScoreWeight = tokenScoreBitScoreWeight;
	}

	public Double getTokenScoreDatabaseScoreWeight() {
		return tokenScoreDatabaseScoreWeight;
	}

	public void setTokenScoreDatabaseScoreWeight(
			Double tokenScoreDatabaseScoreWeight) {
		this.tokenScoreDatabaseScoreWeight = tokenScoreDatabaseScoreWeight;
	}

	public Double getTokenScoreOverlapScoreWeight() {
		return tokenScoreOverlapScoreWeight;
	}

	public void setTokenScoreOverlapScoreWeight(
			Double tokenScoreOverlapScoreWeight) {
		this.tokenScoreOverlapScoreWeight = tokenScoreOverlapScoreWeight;
	}

	public Double getAvgEvaluationScore() {
		return avgEvaluationScore;
	}

	public void setAvgEvaluationScore(Double avgEvaluationScore) {
		this.avgEvaluationScore = avgEvaluationScore;
	}

	public Map<String, Map<String, String>> getBlastDbParameters() {
		return blastDbParameters;
	}

	public Double getAvgPrecision() {
		return avgPrecision;
	}

	public void setAvgPrecision(Double avgPrecision) {
		this.avgPrecision = avgPrecision;
	}
	
	public Double getAvgRecall() {
		return avgRecall;
	}

	public void setAvgRecall(Double avgRecall) {
		this.avgRecall = avgRecall;
	}

	public Integer getLastMutatedParameter() {
		return lastMutatedParameter;
	}

	public void setLastMutatedParameter(Integer lastMutatedParameter) {
		this.lastMutatedParameter = lastMutatedParameter;
	}
	
	/**
	* Compares the average evaluation score of the current Parameters with 
	* the average evaluation score of the specified parameters for order. 
	* Returns a negative integer, zero, or a positive integer as the average 
	* evaluation score of the current Parameters of these Parameters is less
    * than, equal to, or greater than the average evaluation Score of the 
    * specified Parameters.
    * 
    * In case different parameter sets have been evaluated to the exact same 
    * average score they can be further distinguished by the values of the 
    * parameters themselves.
    * */
	@Override
	public int compareTo(Parameters other) {
		if (this.getAvgEvaluationScore() != null && other.getAvgEvaluationScore() != null){
			if (this.getAvgEvaluationScore() < other.getAvgEvaluationScore())
				return -1;
			if (this.getAvgEvaluationScore() > other.getAvgEvaluationScore())
				return 1;
			if (this.getTokenScoreBitScoreWeight() < other.getTokenScoreBitScoreWeight())
				return -1;
			if (this.getTokenScoreBitScoreWeight() > other.getTokenScoreBitScoreWeight())
				return 1;
			if (this.getTokenScoreDatabaseScoreWeight() < other.getTokenScoreDatabaseScoreWeight())
				return -1;
			if (this.getTokenScoreDatabaseScoreWeight() > other.getTokenScoreDatabaseScoreWeight())
				return 1;
			if (this.getTokenScoreOverlapScoreWeight() < other.getTokenScoreOverlapScoreWeight())
				return -1;
			if (this.getTokenScoreOverlapScoreWeight() > other.getTokenScoreOverlapScoreWeight())
				return 1;
			for (String blastDbName : getSettings().getSortedBlastDatabases()) {
				if (this.getDescriptionScoreBitScoreWeight(blastDbName) < other.getDescriptionScoreBitScoreWeight(blastDbName))
					return -1;
				if (this.getDescriptionScoreBitScoreWeight(blastDbName) > other.getDescriptionScoreBitScoreWeight(blastDbName))
					return 1;
				if (this.getBlastDbWeight(blastDbName) < other.getBlastDbWeight(blastDbName))
					return -1;
				if (this.getBlastDbWeight(blastDbName) > other.getBlastDbWeight(blastDbName))
					return 1;
			}
		}
		return 0;
	}

	public String getOrigin() {
		return origin;
	}

	public void setOrigin(String origin) {
		this.origin = origin;
	}
}