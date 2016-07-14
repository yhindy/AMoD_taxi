

import org.apache.commons.configuration.Configuration;
import org.matsim.contrib.taxi.optimizer.AbstractTaxiOptimizerParams;

public class AMoDTaxiOptimizerParams 
	extends AbstractTaxiOptimizerParams
{
	 private final String OPTIMIZER_DELAY = "optimizerDelay";
	 private final String REBALANCE_WEIGHT = "rebalanceWeight";
	 private final String OPTIMIZER_DATA = "optimizerDataFile";
	 
	 public int optimizerDelay;
	 public double rebWeight;
	 public String optimizerDataFile;
	 
	 public AMoDTaxiOptimizerParams(Configuration optimizerConfig)
	    {
		    super(optimizerConfig);
			optimizerDelay = optimizerConfig.getInt(OPTIMIZER_DELAY);
			rebWeight = optimizerConfig.getDouble(REBALANCE_WEIGHT);
			optimizerDataFile = optimizerConfig.getString(OPTIMIZER_DATA);
			
	    }
}
