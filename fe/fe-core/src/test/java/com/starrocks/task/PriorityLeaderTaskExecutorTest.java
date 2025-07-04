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


package com.starrocks.task;

import com.starrocks.common.PriorityThreadPoolExecutor;
import com.starrocks.common.jmockit.Deencapsulation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class PriorityLeaderTaskExecutorTest {
    private static final Logger LOG = LoggerFactory.getLogger(PriorityLeaderTaskExecutorTest.class);
    private static final int THREAD_NUM = 1;
    private static final long SLEEP_MS = 200L;

    private static List<Long> SEQ = new ArrayList<>();

    private PriorityLeaderTaskExecutor executor;

    @BeforeEach
    public void setUp() {
        executor = new PriorityLeaderTaskExecutor("priority_task_executor_test", THREAD_NUM, 100, false);
        executor.start();
    }

    @AfterEach
    public void tearDown() {
        if (executor != null) {
            executor.close();
        }
    }

    @Test
    public void testSubmit() {
        // submit task
        PriorityLeaderTask task1 = new TestLeaderTask(1L);
        Assertions.assertTrue(executor.submit(task1));
        Assertions.assertEquals(1, executor.getTaskNum());
        // submit same running task error
        Assertions.assertFalse(executor.submit(task1));
        Assertions.assertEquals(1, executor.getTaskNum());

        // submit another task
        PriorityLeaderTask task2 = new TestLeaderTask(2L);
        Assertions.assertTrue(executor.submit(task2));
        Assertions.assertEquals(2, executor.getTaskNum());

        // submit priority task
        PriorityLeaderTask task3 = new TestLeaderTask(3L, 1);
        Assertions.assertTrue(executor.submit(task3));
        Assertions.assertEquals(3, executor.getTaskNum());

        // submit priority task
        PriorityLeaderTask task4 = new TestLeaderTask(4L);
        Assertions.assertTrue(executor.submit(task4));
        Assertions.assertEquals(4, executor.getTaskNum());

        Assertions.assertTrue(executor.updatePriority(4L, 5));

        // wait for tasks run to end
        try {
            Thread.sleep(2000);
            Assertions.assertEquals(0, executor.getTaskNum());
        } catch (InterruptedException e) {
            LOG.error("error", e);
        }

        Assertions.assertEquals(4, SEQ.size());
        Assertions.assertEquals(1L, SEQ.get(0).longValue());
        Assertions.assertEquals(4L, SEQ.get(1).longValue());
        Assertions.assertEquals(3L, SEQ.get(2).longValue());
        Assertions.assertEquals(2L, SEQ.get(3).longValue());
    }

    @Test
    public void testUpdatePoolSize() {
        PriorityThreadPoolExecutor priorityExecutor = Deencapsulation.getField(executor, "executor");
        Assertions.assertEquals(THREAD_NUM, executor.getCorePoolSize());
        Assertions.assertEquals(THREAD_NUM, priorityExecutor.getMaximumPoolSize());

        // set from 1 to 2
        int newThreadNum = THREAD_NUM + 1;
        executor.setPoolSize(newThreadNum);
        Assertions.assertEquals(newThreadNum, executor.getCorePoolSize());
        Assertions.assertEquals(newThreadNum, priorityExecutor.getMaximumPoolSize());

        // set from 2 to 1
        executor.setPoolSize(THREAD_NUM);
        Assertions.assertEquals(THREAD_NUM, executor.getCorePoolSize());
        Assertions.assertEquals(THREAD_NUM, priorityExecutor.getMaximumPoolSize());
    }

    private class TestLeaderTask extends PriorityLeaderTask {

        public TestLeaderTask(long signature) {
            this.signature = signature;
        }

        public TestLeaderTask(long signature, int priority) {
            super(priority);
            this.signature = signature;
        }

        @Override
        protected void exec() {
            LOG.info("run exec. signature: {}, priority: {}", signature, getPriority());
            SEQ.add(signature);
            try {
                Thread.sleep(SLEEP_MS);
            } catch (InterruptedException e) {
                LOG.error("error", e);
            }
        }

    }
}
