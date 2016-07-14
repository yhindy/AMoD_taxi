% Call cplex_out=TIBalancedMCFlow to solve the entire problem (fractional).
% Call allpaths=TIPaxPathDecomposition(cplex_out) to compute the passenger path decomposition.
% Call TISamplePaxPaths(allpaths) to sample passenger paths
% NO: Compute the residual road capacity
% NO: Call cplex_reb=TIMulticommodityFlow with the modified capacities to solve the rebalancing problem (relax congestion constraints).
% CDC16: pass the rebalancing flow (output(N^2*M+1:N^2*(M+1)) to TIDecomposeFracRebSol.
%        This decomposes the rebalancing flow in something we can sample from.
%        Syntax: [FracRebSols,FracRebWeights]=TIDecomposeFracRebSol(FullRebPaths,N,M,Sources,Sinks,Flows)
% CDC16: sample from the output of the decomposition above with
%        TISampleRebSol. Syntax: [Reb_solution]=TISampleRebSol(FracRebSols,FracRebWeights) 
% Call TIRebPathDecomposition(Reb_solution) to get rebalancing paths