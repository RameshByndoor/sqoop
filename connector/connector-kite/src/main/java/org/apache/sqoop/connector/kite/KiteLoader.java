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
package org.apache.sqoop.connector.kite;

import com.google.common.annotations.VisibleForTesting;
import org.apache.log4j.Logger;
import org.apache.sqoop.connector.common.FileFormat;
import org.apache.sqoop.connector.kite.configuration.LinkConfiguration;
import org.apache.sqoop.connector.kite.configuration.ToJobConfiguration;
import org.apache.sqoop.etl.io.DataReader;
import org.apache.sqoop.job.etl.Loader;
import org.apache.sqoop.job.etl.LoaderContext;
import org.apache.sqoop.schema.Schema;

/**
 * This class allows Kite connector to load data into a target system.
 */
public class KiteLoader extends Loader<LinkConfiguration, ToJobConfiguration> {

  private static final Logger LOG = Logger.getLogger(KiteLoader.class);

  private long rowsWritten = 0;
  @VisibleForTesting
  protected KiteDatasetExecutor getExecutor(String uri, Schema schema,
      FileFormat format) {
    // Note that instead of creating a dataset at destination, we create a
    // temporary dataset by every KiteLoader instance. They will be merged when
    // all data portions are written successfully. Unfortunately, KiteLoader is
    // not able to pass the temporary dataset uri to KiteToDestroyer. So we
    // delegate KiteDatasetExecutor to manage name convention for datasets.
    uri = KiteDatasetExecutor.suggestTemporaryDatasetUri(uri);

    return new KiteDatasetExecutor(uri, schema, format);
  }

  @Override
  public void load(LoaderContext context, LinkConfiguration linkConfig,
      ToJobConfiguration jobConfig) throws Exception {
    KiteDatasetExecutor executor = getExecutor(jobConfig.toJobConfig.uri,
        context.getSchema(), linkConfig.linkConfig.fileFormat);
    LOG.info("Temporary dataset created.");

    DataReader reader = context.getDataReader();
    Object[] array;
    boolean success = false;

    try {
      while ((array = reader.readArrayRecord()) != null) {
        executor.writeRecord(array);
        rowsWritten++;
      }
      LOG.info(rowsWritten + " data record(s) have been written into dataset.");
      success = true;
    } finally {
      executor.closeWriter();

      if (!success) {
        LOG.error("Fail to write data, dataset will be removed.");
        executor.deleteDataset();
      }
    }
  }

  /* (non-Javadoc)
   * @see org.apache.sqoop.job.etl.Loader#getRowsWritten()
   */
  @Override
  public long getRowsWritten() {
    return rowsWritten;
  }

}