// Copyright 2021-present StarRocks, Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.starrocks.sql.optimizer.rule.transformation;

import com.google.common.collect.Maps;
import com.starrocks.analysis.BinaryType;
import com.starrocks.catalog.Column;
import com.starrocks.catalog.HudiTable;
import com.starrocks.catalog.IcebergTable;
import com.starrocks.catalog.OdpsTable;
import com.starrocks.catalog.Type;
import com.starrocks.connector.TableVersionRange;
import com.starrocks.sql.optimizer.OptExpression;
import com.starrocks.sql.optimizer.OptimizerContext;
import com.starrocks.sql.optimizer.base.ColumnRefSet;
import com.starrocks.sql.optimizer.operator.logical.LogicalHudiScanOperator;
import com.starrocks.sql.optimizer.operator.logical.LogicalIcebergScanOperator;
import com.starrocks.sql.optimizer.operator.logical.LogicalOdpsScanOperator;
import com.starrocks.sql.optimizer.operator.scalar.BinaryPredicateOperator;
import com.starrocks.sql.optimizer.operator.scalar.ColumnRefOperator;
import com.starrocks.sql.optimizer.operator.scalar.ConstantOperator;
import com.starrocks.sql.optimizer.task.TaskContext;
import mockit.Expectations;
import mockit.Mocked;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PruneHDFSScanColumnRuleTest {
    private PruneHDFSScanColumnRule icebergRule = new PruneHDFSScanColumnRule();
    private PruneHDFSScanColumnRule hudiRule = new PruneHDFSScanColumnRule();
    private PruneHDFSScanColumnRule odpsRule = new PruneHDFSScanColumnRule();

    ColumnRefOperator intColumnOperator = new ColumnRefOperator(1, Type.INT, "id", true);
    ColumnRefOperator strColumnOperator = new ColumnRefOperator(2, Type.STRING, "name", true);
    ColumnRefOperator unknownColumnOperator = new ColumnRefOperator(3, Type.UNKNOWN_TYPE, "unknown", true);

    Map<ColumnRefOperator, Column> scanColumnMap = new HashMap<ColumnRefOperator, Column>() {
        {
            put(intColumnOperator, new Column("id", Type.INT));
            put(strColumnOperator, new Column("name", Type.STRING));
        }
    };

    Map<ColumnRefOperator, Column> scanColumnMapWithUnknown = new HashMap<ColumnRefOperator, Column>() {
        {
            put(intColumnOperator, new Column("id", Type.INT));
            put(strColumnOperator, new Column("name", Type.STRING));
            put(unknownColumnOperator, new Column("name", Type.UNKNOWN_TYPE));
        }
    };

    @Test
    public void transformIcebergWithPredicate(@Mocked IcebergTable table,
                                              @Mocked OptimizerContext context,
                                              @Mocked TaskContext taskContext) {
        OptExpression scan = new OptExpression(
                new LogicalIcebergScanOperator(table,
                        scanColumnMap, Maps.newHashMap(), -1,
                        new BinaryPredicateOperator(BinaryType.EQ,
                                new ColumnRefOperator(1, Type.INT, "id", true),
                                ConstantOperator.createInt(1)), TableVersionRange.empty()));

        List<TaskContext> taskContextList = new ArrayList<>();
        taskContextList.add(taskContext);

        ColumnRefSet requiredOutputColumns = new ColumnRefSet(new ArrayList<>(
                Collections.singleton(new ColumnRefOperator(1, Type.INT, "id", true))));

        doIcebergTransform(scan, context, requiredOutputColumns, taskContextList, taskContext);
    }

    @Test
    public void transformIcebergWithNoScanColumn(@Mocked IcebergTable table,
                                                 @Mocked OptimizerContext context,
                                                 @Mocked TaskContext taskContext) {
        OptExpression scan = new OptExpression(
                new LogicalIcebergScanOperator(table,
                        scanColumnMap, Maps.newHashMap(), -1, null, TableVersionRange.empty()));

        List<TaskContext> taskContextList = new ArrayList<>();
        taskContextList.add(taskContext);

        ColumnRefSet requiredOutputColumns = new ColumnRefSet(new ArrayList<>());

        doIcebergTransform(scan, context, requiredOutputColumns, taskContextList, taskContext);
    }

    private void doIcebergTransform(OptExpression scan,
                                    OptimizerContext context,
                                    ColumnRefSet requiredOutputColumns,
                                    List<TaskContext> taskContextList,
                                    TaskContext taskContext) {
        new Expectations() {
            {
                context.getTaskContext();
                minTimes = 0;
                result = taskContextList;

                taskContext.getRequiredColumns();
                minTimes = 0;
                result = requiredOutputColumns;

                context.getSessionVariable().isEnableCountStarOptimization();
                result = true;
            }
        };
        List<OptExpression> list = icebergRule.transform(scan, context);
        Map<ColumnRefOperator, Column> transferMap = ((LogicalIcebergScanOperator) list.get(0)
                .getOp()).getColRefToColumnMetaMap();
        Assertions.assertEquals(transferMap.size(), 1);
        Assertions.assertEquals(transferMap.get(intColumnOperator).getName(), "id");
    }

    @Test
    public void transformHudiWithPredicate(@Mocked HudiTable table,
                                           @Mocked OptimizerContext context,
                                           @Mocked TaskContext taskContext) {
        OptExpression scan = new OptExpression(
                new LogicalHudiScanOperator(table,
                        scanColumnMap, Maps.newHashMap(), -1,
                        new BinaryPredicateOperator(BinaryType.EQ,
                                new ColumnRefOperator(1, Type.INT, "id", true),
                                ConstantOperator.createInt(1))));

        List<TaskContext> taskContextList = new ArrayList<>();
        taskContextList.add(taskContext);

        ColumnRefSet requiredOutputColumns = new ColumnRefSet(new ArrayList<>(
                Collections.singleton(new ColumnRefOperator(1, Type.INT, "id", true))));

        doHudiTransform(scan, context, requiredOutputColumns, taskContextList, taskContext);
    }

    @Test
    public void transformHudiWithNoScanColumn(@Mocked HudiTable table,
                                              @Mocked OptimizerContext context,
                                              @Mocked TaskContext taskContext) {
        OptExpression scan = new OptExpression(
                new LogicalHudiScanOperator(table,
                        scanColumnMap, Maps.newHashMap(), -1, null));

        List<TaskContext> taskContextList = new ArrayList<>();
        taskContextList.add(taskContext);

        ColumnRefSet requiredOutputColumns = new ColumnRefSet(new ArrayList<>());

        doHudiTransform(scan, context, requiredOutputColumns, taskContextList, taskContext);
    }

    @Test
    public void transformHudiWithUnknownScanColumn(@Mocked HudiTable table,
                                                   @Mocked OptimizerContext context,
                                                   @Mocked TaskContext taskContext) {
        OptExpression scan = new OptExpression(
                new LogicalHudiScanOperator(table,
                        scanColumnMapWithUnknown, Maps.newHashMap(), -1, null));

        List<TaskContext> taskContextList = new ArrayList<>();
        taskContextList.add(taskContext);

        ColumnRefSet requiredOutputColumns = new ColumnRefSet(new ArrayList<>());

        doHudiTransform(scan, context, requiredOutputColumns, taskContextList, taskContext);
    }

    private void doHudiTransform(OptExpression scan,
                                 OptimizerContext context,
                                 ColumnRefSet requiredOutputColumns,
                                 List<TaskContext> taskContextList,
                                 TaskContext taskContext) {
        new Expectations() {
            {
                context.getTaskContext();
                minTimes = 0;
                result = taskContextList;

                taskContext.getRequiredColumns();
                minTimes = 0;
                result = requiredOutputColumns;

                context.getSessionVariable().isEnableCountStarOptimization();
                result = true;
            }
        };
        List<OptExpression> list = hudiRule.transform(scan, context);
        LogicalHudiScanOperator scanOperator = (LogicalHudiScanOperator) list.get(0).getOp();
        Assertions.assertEquals(scanOperator.getScanOptimizeOption().getCanUseAnyColumn(), (requiredOutputColumns.size() == 0));
        Map<ColumnRefOperator, Column> transferMap = scanOperator.getColRefToColumnMetaMap();
        Assertions.assertEquals(transferMap.size(), 1);
        Assertions.assertEquals(transferMap.get(intColumnOperator).getName(), "id");
    }

    @Test
    public void transformIcebergWithAllPartitionColumns(@Mocked IcebergTable table,
                                                        @Mocked OptimizerContext context,
                                                        @Mocked TaskContext taskContext) {
        OptExpression scan = new OptExpression(
                new LogicalIcebergScanOperator(table,
                        scanColumnMap, Maps.newHashMap(), -1, null, TableVersionRange.empty()));

        List<TaskContext> taskContextList = new ArrayList<>();
        taskContextList.add(taskContext);

        ColumnRefSet requiredOutputColumns = new ColumnRefSet(new ArrayList<>());

        new Expectations() {
            {
                context.getTaskContext();
                minTimes = 0;
                result = taskContextList;

                taskContext.getRequiredColumns();
                minTimes = 0;
                result = requiredOutputColumns;

                table.getPartitionColumnNames();
                result = scanColumnMap.values().stream().map(x -> x.getName()).collect(Collectors.toList());
            }
        };
        List<OptExpression> list = icebergRule.transform(scan, context);
        LogicalIcebergScanOperator op = ((LogicalIcebergScanOperator) list.get(0).getOp());
        Assertions.assertEquals(op.getScanOptimizeOption().getCanUseAnyColumn(), false);
    }

    @Test
    public void transformOdpsWithPredicate(@Mocked OdpsTable table,
                                           @Mocked OptimizerContext context,
                                           @Mocked TaskContext taskContext) {
        OptExpression scan = new OptExpression(
                new LogicalOdpsScanOperator(table,
                        scanColumnMap, Maps.newHashMap(), -1,
                        new BinaryPredicateOperator(BinaryType.EQ,
                                new ColumnRefOperator(1, Type.INT, "id", true),
                                ConstantOperator.createInt(1))));

        List<TaskContext> taskContextList = new ArrayList<>();
        taskContextList.add(taskContext);

        ColumnRefSet requiredOutputColumns = new ColumnRefSet(new ArrayList<>(
                Collections.singleton(new ColumnRefOperator(1, Type.INT, "id", true))));

        doOdpsTransform(scan, context, requiredOutputColumns, taskContextList, taskContext);
    }

    @Test
    public void transformOdpsWithNoScanColumn(@Mocked OdpsTable table,
                                              @Mocked OptimizerContext context,
                                              @Mocked TaskContext taskContext) {
        OptExpression scan = new OptExpression(
                new LogicalOdpsScanOperator(table,
                        scanColumnMap, Maps.newHashMap(), -1, null));

        List<TaskContext> taskContextList = new ArrayList<>();
        taskContextList.add(taskContext);

        ColumnRefSet requiredOutputColumns = new ColumnRefSet(new ArrayList<>());

        doOdpsTransform(scan, context, requiredOutputColumns, taskContextList, taskContext);
    }

    @Test
    public void transformOdpsWithUnknownScanColumn(@Mocked OdpsTable table,
                                                   @Mocked OptimizerContext context,
                                                   @Mocked TaskContext taskContext) {
        OptExpression scan = new OptExpression(
                new LogicalOdpsScanOperator(table,
                        scanColumnMapWithUnknown, Maps.newHashMap(), -1, null));

        List<TaskContext> taskContextList = new ArrayList<>();
        taskContextList.add(taskContext);

        ColumnRefSet requiredOutputColumns = new ColumnRefSet(new ArrayList<>());

        doOdpsTransform(scan, context, requiredOutputColumns, taskContextList, taskContext);
    }

    private void doOdpsTransform(OptExpression scan,
                                 OptimizerContext context,
                                 ColumnRefSet requiredOutputColumns,
                                 List<TaskContext> taskContextList,
                                 TaskContext taskContext) {
        new Expectations() {
            {
                context.getTaskContext();
                minTimes = 0;
                result = taskContextList;

                taskContext.getRequiredColumns();
                minTimes = 0;
                result = requiredOutputColumns;

                context.getSessionVariable().isEnableCountStarOptimization();
                result = true;
            }
        };
        List<OptExpression> list = odpsRule.transform(scan, context);
        LogicalOdpsScanOperator scanOperator = (LogicalOdpsScanOperator) list.get(0).getOp();
        Assertions.assertEquals(scanOperator.getScanOptimizeOption().getCanUseAnyColumn(),
                (requiredOutputColumns.size() == 0));
        Map<ColumnRefOperator, Column> transferMap = scanOperator.getColRefToColumnMetaMap();
        Assertions.assertEquals(transferMap.size(), 1);
        Assertions.assertEquals(transferMap.get(intColumnOperator).getName(), "id");
    }
}
