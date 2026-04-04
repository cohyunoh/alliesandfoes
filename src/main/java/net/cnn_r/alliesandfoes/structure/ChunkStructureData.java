package net.cnn_r.alliesandfoes.structure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChunkStructureData {
    private final int structureValue;
    private final List<String> structureNames;

    public ChunkStructureData(int structureValue, List<String> structureNames) {
        this.structureValue = structureValue;
        this.structureNames = Collections.unmodifiableList(new ArrayList<>(structureNames));
    }

    public int getStructureValue() {
        return structureValue;
    }

    public List<String> getStructureNames() {
        return structureNames;
    }
}