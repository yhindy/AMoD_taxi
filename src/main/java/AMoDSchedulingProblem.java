import java.util.*;


import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Node;
import org.matsim.contrib.dvrp.data.Requests;
import org.matsim.contrib.dvrp.data.Vehicle;
import org.matsim.contrib.dvrp.examples.onetaxi.OneTaxiServeTask;
import org.matsim.contrib.dvrp.path.VrpPathWithTravelData;
import org.matsim.contrib.dvrp.path.VrpPathWithTravelDataImpl;
import org.matsim.contrib.dvrp.path.VrpPaths;
import org.matsim.contrib.dvrp.schedule.DriveTaskImpl;
import org.matsim.contrib.dvrp.schedule.Schedule;
import org.matsim.contrib.dvrp.schedule.Schedules;
import org.matsim.contrib.dvrp.schedule.StayTask;
import org.matsim.contrib.dvrp.schedule.StayTaskImpl;
import org.matsim.contrib.dvrp.schedule.Task;
import org.matsim.contrib.dvrp.schedule.Schedule.ScheduleStatus;
import org.matsim.contrib.taxi.data.TaxiRequest;
import org.matsim.contrib.taxi.optimizer.*;
import org.matsim.contrib.taxi.schedule.TaxiDropoffTask;
import org.matsim.contrib.taxi.schedule.TaxiEmptyDriveTask;
import org.matsim.contrib.taxi.schedule.TaxiOccupiedDriveTask;
import org.matsim.contrib.taxi.schedule.TaxiPickupTask;
import org.matsim.contrib.taxi.schedule.TaxiSchedules;
import org.matsim.contrib.taxi.schedule.TaxiStayTask;
import org.matsim.contrib.taxi.schedule.TaxiTask;
import org.matsim.core.mobsim.framework.MobsimTimer;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.LeastCostPathCalculator.Path;
import org.matsim.core.router.util.TravelTime;

public class AMoDSchedulingProblem 
{
	private final TaxiOptimizerContext optimContext;
    private final BestDispatchFinder dispatchFinder;
    private final LeastCostPathCalculator router;
    private final MobsimTimer timer;
    private final TravelTime travelTime;
    
    public AMoDSchedulingProblem(TaxiOptimizerContext optimContext, BestDispatchFinder vrpFinder, LeastCostPathCalculator router)
    {
        this.optimContext = optimContext;
        this.dispatchFinder = vrpFinder;
        this.router = router;
        this.timer = optimContext.timer;
        this.travelTime = optimContext.travelTime;
    }
    
    private VrpPathWithTravelData findBestPath(Link from, Link to, Set<Path> possiblepaths, double starttime) {
    	if (possiblepaths == null) {
    		return VrpPaths.calcAndCreatePath(from, to, starttime, router, travelTime);
    	}
    	
        for (Path p : possiblepaths) {
        	if (p.links.get(p.links.size()-1).equals(to)) {
        		return VrpPaths.createPath(from, to, starttime, p, travelTime); 
        	}
        } 
        
        return null;
        
    }

    private void scheduleOneRequest(Vehicle v, TaxiRequest req, double currentTime, Link fromlink, Link toLink,
    		Map<Link, Set<Path>> routes) 
    {
    	Schedule<TaxiTask> schedule = TaxiSchedules.asTaxiSchedule(v.getSchedule());
        
        TaxiStayTask lastTask = (TaxiStayTask)Schedules.getLastTask(schedule);
        Set<Path> possiblepaths = routes.get(fromlink);
        
        switch (lastTask.getStatus()) {
        case PLANNED:
            schedule.removeLastTask();// remove waiting
            break;

        case STARTED:
            lastTask.setEndTime(currentTime);// shorten waiting
            break;

        default:
            throw new IllegalStateException();
        }
        
        double t0 = schedule.getStatus() == ScheduleStatus.UNPLANNED ? // see what time it starts
                Math.max(v.getT0(), currentTime) : //
                Schedules.getLastTask(schedule).getEndTime();
                
        VrpPathWithTravelData p1 = VrpPaths.calcAndCreatePath(lastTask.getLink(), fromlink, t0, //path to pickup
                router, travelTime);
        schedule.addTask(new TaxiEmptyDriveTask(p1));
        
        double t1 = p1.getArrivalTime(); //pickup of passenger
        double t2 = t1 + 120;// 2 minutes for picking up the passenger
        schedule.addTask(new TaxiPickupTask(t1, t2, req));
        
        VrpPathWithTravelData finalfinalpath; //need to find actual path for driving
        
        VrpPathWithTravelData path = findBestPath(fromlink, toLink, possiblepaths, t2);
        if (path == null) { //path is not in the map lookup
        	path = VrpPaths.calcAndCreatePath(fromlink, toLink, t2, router,
                    travelTime);
        }
        
        schedule.addTask(new TaxiOccupiedDriveTask(path, req)); // add actual drive task
        
        double t3 = path.getArrivalTime();
        double t4 = t3 + 60;// 1 minute for dropping off the passenger
        schedule.addTask(new TaxiDropoffTask(t3, t4,req));
        
        double tEnd = Math.max(t4, v.getT1());
        schedule.addTask(new TaxiStayTask(t4, tEnd, toLink));
    	
    }
    public void scheduleUnplannedRequests(Queue<TaxiRequest> unplannedRequests, Map<Link, Set<Path>> routes,
    		Map<Link, Set<Path>> reb_routes)
    {
        while (!unplannedRequests.isEmpty()) {
            TaxiRequest req = unplannedRequests.peek();
            
            double currentTime = timer.getTimeOfDay();

            BestDispatchFinder.Dispatch<TaxiRequest> best = dispatchFinder
                    .findBestVehicleForRequest(req, optimContext.taxiData.getVehicles().values());
            
            if (best == null) {//TODO won't work with req filtering; use VehicleData to find out when to exit???
                return;
            }
            
            Node from = req.getFromLink().getFromNode();
            Link fromlink = req.getFromLink();
            Node to = req.getToLink().getToNode();
            Link toLink = req.getToLink();
            
            scheduleOneRequest(best.vehicle, req, currentTime, fromlink, toLink, routes);

            //TODO search only through available vehicles
            //TODO what about k-nearstvehicle filtering?

          
            //optimContext.scheduler.scheduleRequest(best.vehicle, best.destination, finalfinalpath);
            unplannedRequests.poll();
        }  
    }
    
    

	public void rebalanceVehicles(Map<Link, Set<Path>> reb_routes) {
		System.out.println("Rebalancing...");
		int count = 0;
		for (Vehicle veh : optimContext.taxiData.getVehicles().values()) {
			if (optimContext.scheduler.isIdle(veh)) {
				count++;
				rebalanceVehicle(veh, reb_routes);
			} //TODO: figure out why vehicles aren't rebalancing
		}	
		System.out.println(count + " free vehicles.");
	}

	private void rebalanceVehicle(Vehicle veh, Map<Link, Set<Path>> reb_routes) {
		Schedule<TaxiTask> curr = TaxiSchedules.asTaxiSchedule(veh.getSchedule());
		TaxiStayTask lasttask = (TaxiStayTask)curr.getCurrentTask();
		Link lastlink = lasttask.getLink();
		Path chosen;
		if (reb_routes.get(lastlink) != null) {
			Set<Path> possiblerebroutes = reb_routes.get(lastlink);
			chosen = chooseRandomPath(possiblerebroutes);
			if (chosen.links.size() == 1 || chosen.nodes.size() == 1) {
				return;
			}
		} else {
			return;
		}
		
		List<Link> links = chosen.links;
		List<Node> nodes = chosen.nodes;
		removeDuplicates(links);
		removeDuplicates(nodes);
		scheduleRebalanceTrip(curr, chosen);
		
	}

	private void removeDuplicates(List<?> listofthings) {
		for (int i = listofthings.size()-1; i > 0; i--) {
			if (listofthings.get(i).equals(listofthings.get(i-1))) {
				listofthings.remove(i);
			}
		}
	}

	private void scheduleRebalanceTrip(Schedule<TaxiTask> curr, Path chosen) {
		TaxiStayTask lastTask = (TaxiStayTask)Schedules.getLastTask(curr);
		
		double currentTime = timer.getTimeOfDay();
		
		switch (lastTask.getStatus()) {
        case PLANNED:
            curr.removeLastTask();// remove waiting
            break;

        case STARTED:
            lastTask.setEndTime(currentTime);// shorten waiting
            break;

        default:
            throw new IllegalStateException();
        }
		
		List<Link> links = chosen.links;
		Link tolink = links.get(links.size()-1);

		VrpPathWithTravelData path = VrpPaths.createPath(lastTask.getLink(), tolink, 
				currentTime, chosen, travelTime); 
		curr.addTask(new TaxiEmptyDriveTask(path));
		
		double arrivalTime = path.getArrivalTime();
		double tEnd = Math.max(arrivalTime, curr.getVehicle().getT1());
        curr.addTask(new TaxiStayTask(arrivalTime, tEnd, tolink));
        
        System.out.println("VEHICLE IS SET TO REBALANCE.");
		
	}

	private Path chooseRandomPath(Set<Path> possiblerebroutes) {
		int size = possiblerebroutes.size();
		int item = new Random().nextInt(size);
		int i = 0;
		for (Path p : possiblerebroutes) {
			if (i == item) 
				return p;
			i++;
		}
		// shouldn't reach here
		return null;
	}

//	private double findTravelTime(double[] traveltimes) {
//    	double tt = 0;
//    	for (double d : traveltimes) {
//    		tt += d;
//    	}
//    	
//    	return tt;
//    }
//
//	private double[] makeTravelTimes(Link[] finalpath) {
//		double[] tts = new double[finalpath.length];
//		for (int i = 0; i < tts.length; i ++) {
//			tts[i] = finalpath[i].getFreespeed()/finalpath[i].getLength();
//		}
//		return tts;
//	}
//
//	private Link[] turnPathIntoArray(Path path) {
//		List<Link> links = path.links;
//		Link[] linkarr = new Link[links.size()];
//		for (int i = 0; i < links.size(); i++) {
//			linkarr[i] = links.get(i);
//		}
//		return linkarr;
//	}
}
