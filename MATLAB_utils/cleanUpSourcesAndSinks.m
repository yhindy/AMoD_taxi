function [SourcesReb, SinksReb] = cleanUpSourcesAndSinks(Sources, Sinks, Flows)

SourcesReb=Sinks';
SinksReb=Sources';
SourcesRebIdx=ones(size(SourcesReb)); %We use these to keep track of what sources and sinks are duplicated and should be removed
SinksRebIdx=ones(size(SinksReb));


for i=1:length(SourcesReb)
   if sum(SinksReb==SourcesReb(i)) %If there is a duplicate
        SinkIndex=find(SinksReb==SourcesReb(i));
        for l=1:length(SinkIndex)
            if (SinksRebIdx(SinkIndex(l))) %If some sink that corresponds to a source is not removed
                SourcesRebIdx(i)=0;
                SinksRebIdx(SinkIndex(l))=0;
                break;
            end
        end
    end
end
 
% %Select the surviving sources and sinks.
SourcesReb=SourcesReb(SourcesRebIdx==1);
SinksReb=SinksReb(SinksRebIdx==1);
SourceFlows=Flows(SourcesRebIdx==1)';
SinkFlows=Flows(SinksRebIdx==1)';