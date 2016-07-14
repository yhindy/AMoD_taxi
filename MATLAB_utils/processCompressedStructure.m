function [matrix] = processCompressedStructure(structure, N);

matrix = zeros(N,N);

for i = 1:length(structure)
    row = structure(i,:);
    matrix(row(1)+1, row(2)+1) = row(3);
end

end

    