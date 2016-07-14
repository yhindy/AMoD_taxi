function [ passpaths, rebpaths ] = runOptimization( startTime, timeHorizon, filename )

%Process requests in question:

load(filename);
load('station_node_map.mat');
times = outdata(:,1);
Sources = outdata(:,2);
Sinks = outdata(:,3);

startind = 1;
endind = 1;

i = startind;
while times(i) < startTime
    i = i + 1;
end
startind = i;

while times(i) < startTime + timeHorizon;
    i = i + 1;
end
endind = i;

times = times(startind:endind);
Sources = Sources(startind:endind);
Sinks = Sinks(startind:endind);

assignin('base', 'Sources', Sources);

% filename = 'res/excessrequests.csv';
% MData = csvread(filename);
% for i = 1:length(MData)
%     Sources = [Sources; MData(i,1)];
%     Sinks = [Sinks; MData(i,2)];
    
LoadRoadGraphLarge;

RebWeight = .5;
milpflag = 1;
congrexflag = 1;

N = length(RoadGraph);
M = size(Sources, 1);

RoadCap = processCompressedStructure(RoadCap, N);

TravelTimes = processCompressedStructure(LinkTime, N);

RoadGraph = RoadGraph';

FlowsIn = ones(M, 1);

[SourcesReb, SinksReb] = cleanUpSourcesAndSinks(Sources, Sinks, FlowsIn);

cplex_out = TIBalancedMCFlow(RoadGraph, RoadCap, TravelTimes, RebWeight, Sources, Sinks, FlowsIn, milpflag, congrexflag, 0);
allpaths = TIPaxPathDecomposition(cplex_out, N, M, Sources, Sinks, FlowsIn);
passpaths = TISamplePaxPaths(allpaths);

FlowsIn = ones(1, length(SourcesReb));

FullRebPaths = cplex_out(N*N*M+1:N*N*(M+1));
[FracRebSols, FracRebWeights] = TIDecomposeFracRebSol(FullRebPaths, N, M, SourcesReb, SinksReb, FlowsIn);
[Reb_solution] = TISampleRebSol(FracRebSols, FracRebWeights); 

[rebpaths,~] = TIRebPathDecomposition(Reb_solution, N, M, SourcesReb, SinksReb, FlowsIn, FlowsIn);

rebpaths = rebpaths';

madeithere = 68;

save('optimizerpaths.mat', 'passpaths', 'rebpaths', 'madeithere');

end

