filename = 'SimTripData1820.csv';
MData = csvread(filename);

%this file writes TIME PICKUPNODE DROPOFFNODE for each trip in the March1
%dataset
LoadRoadGraphLarge;
load('tripDataNY100Stations.mat'); %station locations are C
load('station_node_map.mat');
LEN = length(MData);
outdata = zeros(LEN, 3);
NodesLocation = NodesLocation/1000;
Stations = C*1000;

disp('starting')
ind = 1;
for i=1:LEN
    time = MData(i,3)*60*60 + MData(i,4)*60 + MData(i,5);
    pickupxy = [MData(i,6) MData(i,7)];
    dropoffxy = [MData(i,8) MData(i,9)];
    pickupnode = findClosestNode(pickupxy, NodesLocation);
    pickupstation = nodestostations(pickupnode);
    dropoffnode = findClosestNode(dropoffxy, NodesLocation);
    dropoffstation = nodestostations(dropoffnode);
    
    
    if dropoffstation ~= pickupstation
        outdata(ind,:) = [time pickupstation dropoffstation];
        ind = ind + 1;
        disp(ind);
        if i < 100
           disp(pickupnode);
           disp(dropoffnode);
        end
    end 
end

save('1820SourcesSinks.mat', 'outdata')

