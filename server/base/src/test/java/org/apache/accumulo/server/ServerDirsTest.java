/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.accumulo.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.accumulo.core.Constants;
import org.apache.accumulo.core.conf.AccumuloConfiguration;
import org.apache.accumulo.core.conf.ConfigurationCopy;
import org.apache.accumulo.core.conf.Property;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocalFileSystem;
import org.apache.hadoop.fs.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "paths not set by user input")
public class ServerDirsTest {

  AccumuloConfiguration conf;
  Configuration hadoopConf = new Configuration();
  ServerDirs constants;

  @Before
  public void setup() throws IOException {
    String uuid = UUID.randomUUID().toString();

    var vols =
        init(folder.newFolder(), Arrays.asList(uuid), Arrays.asList(AccumuloDataVersion.get()));

    ConfigurationCopy copy = new ConfigurationCopy();
    copy.set(Property.INSTANCE_VOLUMES.getKey(), String.join(",", vols));
    conf = copy;
    constants = new ServerDirs(conf, hadoopConf);
  }

  @Rule
  public TemporaryFolder folder =
      new TemporaryFolder(new File(System.getProperty("user.dir") + "/target"));

  @Test
  public void testCheckBaseDirs() throws IOException {
    String uuid1 = UUID.randomUUID().toString();
    String uuid2 = UUID.randomUUID().toString();

    verifyAllPass(
        init(folder.newFolder(), Arrays.asList(uuid1), Arrays.asList(AccumuloDataVersion.get())));
    verifyAllPass(init(folder.newFolder(), Arrays.asList(uuid1, uuid1),
        Arrays.asList(AccumuloDataVersion.get(), AccumuloDataVersion.get())));

    verifyError(
        init(folder.newFolder(), Arrays.asList((String) null), Arrays.asList((Integer) null)));
    verifyError(init(folder.newFolder(), Arrays.asList(uuid1, uuid2),
        Arrays.asList(AccumuloDataVersion.get(), AccumuloDataVersion.get())));
    verifyError(init(folder.newFolder(), Arrays.asList(uuid1, uuid1),
        Arrays.asList(AccumuloDataVersion.get(), AccumuloDataVersion.get() - 1)));
    verifyError(init(folder.newFolder(), Arrays.asList(uuid1, uuid2),
        Arrays.asList(AccumuloDataVersion.get(), AccumuloDataVersion.get() - 1)));
    verifyError(init(folder.newFolder(), Arrays.asList(uuid1, uuid2, null), Arrays
        .asList(AccumuloDataVersion.get(), AccumuloDataVersion.get(), AccumuloDataVersion.get())));

    verifySomePass(init(folder.newFolder(), Arrays.asList(uuid1, uuid1, null),
        Arrays.asList(AccumuloDataVersion.get(), AccumuloDataVersion.get(), null)));
  }

  private void verifyAllPass(Set<String> paths) {
    assertEquals(paths, constants.checkBaseUris(hadoopConf, paths, true));
    assertEquals(paths, constants.checkBaseUris(hadoopConf, paths, false));
  }

  private void verifySomePass(Set<String> paths) {
    Set<String> subset = paths.stream().limit(2).collect(Collectors.toSet());
    assertEquals(subset, constants.checkBaseUris(hadoopConf, paths, true));
    assertThrows(RuntimeException.class, () -> constants.checkBaseUris(hadoopConf, paths, false));
  }

  private void verifyError(Set<String> paths) {
    assertThrows(RuntimeException.class, () -> constants.checkBaseUris(hadoopConf, paths, true));
    assertThrows(RuntimeException.class, () -> constants.checkBaseUris(hadoopConf, paths, false));
  }

  private Set<String> init(File newFile, List<String> uuids, List<Integer> dataVersions)
      throws IllegalArgumentException, IOException {
    String base = newFile.toURI().toString();

    LocalFileSystem fs = FileSystem.getLocal(new Configuration());

    LinkedHashSet<String> accumuloPaths = new LinkedHashSet<>();

    for (int i = 0; i < uuids.size(); i++) {
      String volume = "v" + i;

      String accumuloPath = base + "/" + volume + "/accumulo";
      accumuloPaths.add(accumuloPath);

      if (uuids.get(i) != null) {
        fs.mkdirs(new Path(accumuloPath + "/" + Constants.INSTANCE_ID_DIR));
        fs.createNewFile(
            new Path(accumuloPath + "/" + Constants.INSTANCE_ID_DIR + "/" + uuids.get(i)));
      }

      if (dataVersions.get(i) != null) {
        fs.mkdirs(new Path(accumuloPath + "/" + Constants.VERSION_DIR));
        fs.createNewFile(
            new Path(accumuloPath + "/" + Constants.VERSION_DIR + "/" + dataVersions.get(i)));
      }
    }

    return accumuloPaths;
  }

}
