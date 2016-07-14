import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.dvrp.data.file.VehicleReader;
import org.matsim.contrib.dvrp.run.VrpQSimConfigConsistencyChecker;
import org.matsim.contrib.dvrp.trafficmonitoring.VrpTravelTimeModules;
import org.matsim.contrib.dynagent.run.DynQSimModule;
import org.matsim.contrib.otfvis.OTFVisLiveModule;
import org.matsim.contrib.taxi.data.TaxiData;
import org.matsim.contrib.taxi.run.TaxiConfigGroup;
import org.matsim.contrib.taxi.run.TaxiModule;
import org.matsim.contrib.taxi.run.TaxiQSimProvider;
import org.matsim.core.config.*;
import org.matsim.core.controler.Controler;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.trafficmonitoring.FreeSpeedTravelTime;
import org.matsim.vehicles.VehicleUtils;
import org.matsim.vis.otfvis.OTFVisConfigGroup;
import org.matsim.contrib.taxi.run.TaxiModule;

public class RunAMoDExample 
{
	public static void run(String configFile, boolean otfvis)
    {
        Config config = ConfigUtils.loadConfig(configFile, new TaxiConfigGroup(),
                new OTFVisConfigGroup());
        createControler(config, otfvis).run();
    }
	
	public static Controler createControler(Config config, boolean otfvis)
    {
        TaxiConfigGroup taxiCfg = TaxiConfigGroup.get(config);
        config.addConfigConsistencyChecker(new VrpQSimConfigConsistencyChecker());
        config.checkConsistency();

        Scenario scenario = ScenarioUtils.loadScenario(config);
        TaxiData taxiData = new TaxiData();
        new VehicleReader(scenario.getNetwork(), taxiData).parse(taxiCfg.getTaxisFile());
        return createControler(scenario, taxiData, otfvis, taxiCfg);
    }
	
	public static Controler createControler(Scenario scenario, TaxiData taxiData, boolean otfvis, TaxiConfigGroup taxiCfg)
    {
        Controler controler = new Controler(scenario);
        controler.addOverridingModule(new TaxiModule(taxiData));
        double expAveragingAlpha = 0.05;//from the AV flow paper 
        controler.addOverridingModule(
                VrpTravelTimeModules.createTravelTimeEstimatorModule( expAveragingAlpha ));
        controler.addOverridingModule(new DynQSimModule<>(AMoDQSimProvider.class));

        if (otfvis) {
            controler.addOverridingModule(new OTFVisLiveModule());
        }

        return controler;
    }
	
	public static void main(String[] args)
    {
        String configFile = "./src/main/resources/amod/amod_config.xml";
        //String configFile = "./src/main/resources/amod/amod_config_small.xml";
        //String configFile = "./src/main/resources/mielec_2014_02/config.xml";
        //String configFile = "./src/main/resources/one_taxi/one_taxi_config.xml";
        RunAMoDExample.run(configFile, false);
    }
}
