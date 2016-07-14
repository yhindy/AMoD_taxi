import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.contrib.dvrp.data.Requests;
import org.matsim.contrib.dvrp.data.Vehicle;
import org.matsim.contrib.dvrp.path.VrpPathWithTravelData;
import org.matsim.contrib.dvrp.schedule.Schedule;
import org.matsim.contrib.dvrp.schedule.Schedule.ScheduleStatus;
import org.matsim.contrib.dvrp.schedule.Task;
import org.matsim.contrib.taxi.data.TaxiRequest;
import org.matsim.contrib.taxi.optimizer.*;
import org.matsim.contrib.taxi.optimizer.fifo.FifoSchedulingProblem;
import org.matsim.contrib.taxi.optimizer.fifo.FifoTaxiOptimizerParams;
import org.matsim.contrib.taxi.schedule.TaxiSchedules;
import org.matsim.contrib.taxi.schedule.TaxiTask;
import org.matsim.core.mobsim.framework.events.MobsimBeforeSimStepEvent;
import org.matsim.core.router.ArrayFastRouterDelegateFactory;
import org.matsim.core.router.FastMultiNodeDijkstra;
import org.matsim.core.router.FastRouterDelegateFactory;
import org.matsim.core.router.util.ArrayRoutingNetworkFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.PreProcessDijkstra;
import org.matsim.core.router.util.RoutingNetwork;
import org.matsim.core.router.util.LeastCostPathCalculator.Path;

import com.jmatio.io.MatFileReader;
import com.jmatio.types.MLArray;
import com.jmatio.types.MLCell;
import com.jmatio.types.MLDouble;

public class AMoDTaxiOptimizer
	extends AbstractTaxiOptimizer
{
	private Map<Link, Set<Path>> routes; //maps origins to a map connecting destinations to routes.
	private Map<Link, Set<Path>> reb_routes; //rebalancing routes
	private Map<Link, Set<Path>> new_routes;
	private Map<Link, Set<Path>> new_reb_routes;
	private int TIME_HORIZON = 1200; //20 Minutes
	private int optimizerDelay; 
	private final BestDispatchFinder dispatchFinder;
	private LeastCostPathCalculator router;
	private int optimizerEndTime = 0;
	private double rebWeight;
	private int reoptimizationTimeStep;
	private String optimizerDataFile;
	
	private Map<Pair, Link> roads = new HashMap<Pair, Link>();
	
	public class Pair<L,R> {
		private final L left;
		private final R right;
		
		public Pair(L left, R right) {
			this.left = left;
			this.right = right;
		}
		
		public L getLeft() {
			return left;
		}
		
		public R getRight() {
			return right;
		}
		
		@Override
		public boolean equals(Object o) {
		    if (!(o instanceof Pair)) return false;
		    Pair pairo = (Pair) o;
		    return this.left.equals(pairo.getLeft()) &&
		            this.right.equals(pairo.getRight());
		  }
	}
	
	
	public AMoDTaxiOptimizer(TaxiOptimizerContext optimContext, AMoDTaxiOptimizerParams params)
	{
		super(optimContext, params, new PriorityQueue<TaxiRequest>(100, Requests.T0_COMPARATOR),
				true);
		initializeRoads();
		dispatchFinder = new BestDispatchFinder(optimContext);
		
		optimizerDelay = params.optimizerDelay;
		rebWeight = params.rebWeight;
		reoptimizationTimeStep = params.reoptimizationTimeStep;
		optimizerDataFile = params.optimizerDataFile;
		
		PreProcessDijkstra preProcessDijkstra = null;
        FastRouterDelegateFactory fastRouterFactory = new ArrayFastRouterDelegateFactory();

        RoutingNetwork routingNetwork = new ArrayRoutingNetworkFactory(preProcessDijkstra)
                .createRoutingNetwork(optimContext.network);
        router = new FastMultiNodeDijkstra(routingNetwork, optimContext.travelDisutility,
                optimContext.travelTime, preProcessDijkstra, fastRouterFactory, false);
		
	}
	
	private void initializeRoads() {
		Network network = optimContext.network;
		Map<Id<Link>,? extends Link> linknetwork = network.getLinks();
		
		for (Id<Link> id : linknetwork.keySet()) {
			Link curr = linknetwork.get(id);
			Node from = curr.getFromNode();
			Node to = curr.getToNode();
			roads.put(new Pair(from, to), curr);
		}
	}

	@Override
    public void notifyMobsimBeforeSimStep(@SuppressWarnings("rawtypes") MobsimBeforeSimStepEvent e)
    {
		double simTime = e.getSimulationTime();
		if (isNewDecisionEpoch(simTime)) {
			try {
				callCPLEXOptimizer(simTime, TIME_HORIZON, rebWeight);
				new AMoDSchedulingProblem(optimContext, dispatchFinder, router).rebalanceVehicles(reb_routes);
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}
		if (simTime >= optimizerEndTime + optimizerDelay) {
			routes = new_routes;
			reb_routes = new_reb_routes;
		}
		if (requiresReoptimization) {
			scheduleUnplannedRequests();
			requiresReoptimization = false;
		}
		//super.notifyMobsimBeforeSimStep(e);
    }

	private boolean isNewDecisionEpoch(double simTime) {
		return simTime % reoptimizationTimeStep == 0;
	}

	@Override
	protected void scheduleUnplannedRequests()
    {
        new AMoDSchedulingProblem(optimContext, dispatchFinder, router)
                .scheduleUnplannedRequests((Queue<TaxiRequest>)unplannedRequests, routes, reb_routes);
    }
	
	@Override
	public void nextTask(Schedule<? extends Task> schedule)
    {
        shiftTimings(schedule);
        schedule.nextTask();
    }
	
	private void shiftTimings(Schedule<? extends Task> schedule)
    {
		Schedule<TaxiTask> taxischedule = TaxiSchedules.asTaxiSchedule(schedule);
        if (taxischedule.getStatus() != ScheduleStatus.STARTED) {
            return;
        }

        double now = optimContext.timer.getTimeOfDay();
        Task currentTask = schedule.getCurrentTask();
        double diff = now - currentTask.getEndTime();

        if (diff == 0) {
            return;
        }

        currentTask.setEndTime(now);

        
        List<TaxiTask> tasks = taxischedule.getTasks();
        int nextTaskIdx = currentTask.getTaskIdx() + 1;

        //all except the last task (waiting)
        for (int i = nextTaskIdx; i < tasks.size() - 1; i++) {
            Task task = tasks.get(i);
            task.setBeginTime(task.getBeginTime() + diff);
            task.setEndTime(task.getEndTime() + diff);
        }

        //wait task
        if (nextTaskIdx != tasks.size()) {
            Task waitTask = tasks.get(tasks.size() - 1);
            waitTask.setBeginTime(waitTask.getBeginTime() + diff);

            double tEnd = Math.max(waitTask.getBeginTime(), taxischedule.getVehicle().getT1());
            waitTask.setEndTime(tEnd);
        }
    }

	public void callCPLEXOptimizer(double startTime, int horizon, double rebWeight) throws FileNotFoundException, IOException
	{
		// Call MATLAB script to generate trips
		new_routes = new HashMap<Link, Set<Path>>();
		new_reb_routes = new HashMap<Link, Set<Path>>();
		//MATLAB OPTIMIZER RUNS HERE
		MatFileReader matfilereader = new MatFileReader("src/main/resources/optimizerpaths.mat");
		MLCell passpaths = (MLCell) matfilereader.getMLArray("passpaths");
		MLCell rebpaths = (MLCell) matfilereader.getMLArray("rebpaths");
		System.out.println("OPTMIZING");
		
		decomposePassPaths(new_routes, passpaths);
		decomposePassPaths(new_reb_routes, rebpaths);
	}

	private void decomposePassPaths(Map<Link, Set<Path>> routes, MLCell j) {
		Network network = optimContext.network;
		Map<Id<Node>,? extends Node> nodenetwork = network.getNodes();
		ArrayList<MLArray> paths = j.cells();
		for (MLArray path : paths) {
			MLCell cellpath = (MLCell) path;
			if (path.getM() != 0) {
				List<Node> nodelist = new ArrayList<Node>();
				List<Link> linklist = new ArrayList<Link>();
				MLDouble route = (MLDouble)(cellpath.get(0));
				double[][] nodes = route.getArray();
				for (int i = 0; i < nodes.length-1; i++) {
					int currnode = (int) nodes[i][0];
					int nextnode = (int) nodes[i+1][0];
					Node start = nodenetwork.get(Id.createNodeId(Integer.toString(currnode)));
					Node finish = nodenetwork.get(Id.createNodeId(Integer.toString(nextnode)));
					nodelist.add(start);
					Collection<? extends Link> possiblelinks = start.getOutLinks().values();
					for (Link l : possiblelinks) {
						if (l.getToNode().equals(finish)) {
							linklist.add(l);
							break;
						}
								
					}
					if (i == nodes.length - 2 ) {
						nodelist.add(finish);
					}
				
				Path temppath = convertNodeListtoPath(nodelist, linklist);
				if (routes.get(linklist.get(0)) == null) { //add path to routelist 
					routes.put(linklist.get(0), new HashSet<Path>());
				}
				routes.get(linklist.get(0)).add(temppath);
				}
		    }
		}
	}

	private Path convertNodeListtoPath(List<Node> nodelist, List<Link> linklist) {
		double traveltime = 0;
		for (Link l : linklist) {
			traveltime += l.getLength()/l.getFreespeed();
		}
		return new Path(nodelist, linklist, traveltime, 0);
	}
}
