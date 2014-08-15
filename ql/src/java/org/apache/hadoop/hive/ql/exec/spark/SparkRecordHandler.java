/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hive.ql.exec.spark;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.hive.ql.exec.MapredContext;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reporter;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Iterator;

public abstract class SparkRecordHandler {
  private static final Log LOG = LogFactory.getLog(SparkRecordHandler.class);

  // used to log memory usage periodically
  protected final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();

  protected JobConf jc;
  protected OutputCollector<?, ?> oc;
  protected Reporter rp;
  protected boolean abort = false;
  private long rowNumber = 0;
  private long nextLogThreshold = 1;

  public void init(JobConf job, OutputCollector output, Reporter reporter) {
    jc = job;
    MapredContext.init(false, new JobConf(jc));

    oc = output;
    rp = reporter;
    MapredContext.get().setReporter(reporter);

    LOG.info("maximum memory = " + memoryMXBean.getHeapMemoryUsage().getMax());

    try {
      LOG.info("conf classpath = "
        + Arrays.asList(((URLClassLoader) job.getClassLoader()).getURLs()));
      LOG.info("thread classpath = "
        + Arrays.asList(((URLClassLoader) Thread.currentThread()
        .getContextClassLoader()).getURLs()));
    } catch (Exception e) {
      LOG.info("cannot get classpath: " + e.getMessage());
    }
  }

  /**
   * Process row with single value.
   */
  public abstract void processRow(Object value) throws IOException;

  /**
   * Process row with key and value collection.
   */
  public abstract void processRow(Object key, Iterator values) throws IOException;

  /**
   * Log processed row number and used memory info.
   */
  protected void logMemoryInfo() {
    rowNumber++;
    if (rowNumber == nextLogThreshold) {
      long used_memory = memoryMXBean.getHeapMemoryUsage().getUsed();
      LOG.info("ExecReducer: processing " + rowNumber
        + " rows: used memory = " + used_memory);
      nextLogThreshold = getNextLogThreshold(rowNumber);
    }
  }

  abstract void close();

  /**
   * Log information to be logged at the end
   */
  protected void logCloseInfo() {
    long used_memory = memoryMXBean.getHeapMemoryUsage().getUsed();
    LOG.info("ExecMapper: processed " + rowNumber + " rows: used memory = "
      + used_memory);
  }

  private long getNextLogThreshold(long currentThreshold) {
    // A very simple counter to keep track of number of rows processed by the
    // reducer. It dumps
    // every 1 million times, and quickly before that
    if (currentThreshold >= 1000000) {
      return currentThreshold + 1000000;
    }

    return 10 * currentThreshold;
  }

  public boolean isAbort() {
    return abort;
  }

  public void setAbort(boolean abort) {
    this.abort = abort;
  }
}
