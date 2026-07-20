package org.rosetta.sqlvalidator.crossdb.compare;

import org.rosetta.sqlvalidator.crossdb.baseline.SelectBaselineSnapshot;
import org.rosetta.sqlvalidator.crossdb.execution.SelectExecutionResult;
import org.rosetta.sqlvalidator.crossdb.execution.SelectExecutionStatus;

public final class SavedH2BaselineResultFactory {

    public SelectExecutionResult create(SelectBaselineSnapshot baseline) {
        if (!baseline.usable()) {
            return SelectExecutionResult.notExecuted("Saved H2 baseline is unusable.");
        }
        return new SelectExecutionResult(
                SelectExecutionStatus.SUCCESS,
                baseline.baselineH2RowCount(),
                baseline.baselineH2ResultHash(),
                baseline.baselineH2ColumnSignature(),
                null,
                "",
                null
        );
    }
}
