function [FracRebSols,FracRebWeights]=TIDecomposeFracRebSol(FullRebPaths,N,M,Sources,Sinks,Flows)

% Computes a fractional rebalancing solution decomposition of a rebalancing
% solution FullRebPaths
%BackupFullRebPaths=FullRebPaths;

FullRebPaths=FullRebPaths(1:N*N); %Cut out slack variables

AllFlow=sum(FullRebPaths);

FracRebSols={};
FracRebWeights=[];
rscounter=1;

while (AllFlow>1e-6)
    [FracRebSolFlow,rho_smallestpath]=TIFindFracRebSol(FullRebPaths,N,M,Sources,Sinks,Flows);
    FracRebSols{rscounter}=FracRebSolFlow;
    FracRebWeights(rscounter)=rho_smallestpath;
    rscounter=rscounter+1;
    FullRebPaths=FullRebPaths-FracRebSolFlow;
    Flows=Flows-rho_smallestpath;
    AllFlow=sum(FullRebPaths);
end