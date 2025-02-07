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
package org.apache.accumulo.hadoop.mapred;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.accumulo.core.client.IteratorSetting;
import org.apache.accumulo.core.iterators.user.RegExFilter;
import org.apache.accumulo.core.iterators.user.VersioningIterator;
import org.apache.accumulo.core.iterators.user.WholeRowIterator;
import org.apache.accumulo.core.iteratorsImpl.system.CountingIterator;
import org.apache.accumulo.core.security.Authorizations;
import org.apache.accumulo.hadoop.mapreduce.InputFormatBuilder.InputFormatOptions;
import org.apache.accumulo.hadoopImpl.mapreduce.lib.InputConfigurator;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.JobConf;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

public class AccumuloInputFormatTest {

  private JobConf job;
  private static Properties clientProperties;

  @Rule
  public TestName test = new TestName();

  @Before
  public void createJob() {
    job = new JobConf();
  }

  @BeforeClass
  public static void setupClientInfo() {
    clientProperties =
        org.apache.accumulo.hadoop.mapreduce.AccumuloInputFormatTest.setupClientProperties();
  }

  @Test
  public void testMissingTable() throws Exception {
    Properties clientProps =
        org.apache.accumulo.hadoop.mapreduce.AccumuloInputFormatTest.setupClientProperties();
    assertThrows(IllegalArgumentException.class,
        () -> AccumuloInputFormat.configure().clientProperties(clientProps).store(new JobConf()));
  }

  /**
   * Check that the iterator configuration is getting stored in the Job conf correctly.
   */
  @Test
  public void testSetIterator() throws Exception {
    InputFormatOptions<JobConf> opts = AccumuloInputFormat.configure()
        .clientProperties(clientProperties).table("test").auths(Authorizations.EMPTY);

    IteratorSetting is = new IteratorSetting(1, "WholeRow", WholeRowIterator.class);
    opts.addIterator(is).store(job);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    is.write(new DataOutputStream(baos));
    String iterators = job.get("AccumuloInputFormat.ScanOpts.Iterators");
    assertEquals(Base64.getEncoder().encodeToString(baos.toByteArray()), iterators);
  }

  @Test
  public void testAddIterator() throws Exception {
    InputFormatOptions<JobConf> opts = AccumuloInputFormat.configure()
        .clientProperties(clientProperties).table("test").auths(Authorizations.EMPTY);

    IteratorSetting iter1 = new IteratorSetting(1, "WholeRow", WholeRowIterator.class);
    IteratorSetting iter2 = new IteratorSetting(2, "Versions", VersioningIterator.class);
    IteratorSetting iter3 = new IteratorSetting(3, "Count", CountingIterator.class);
    iter3.addOption("v1", "1");
    iter3.addOption("junk", "\0omg:!\\xyzzy");
    opts.addIterator(iter1).addIterator(iter2).addIterator(iter3).store(job);

    List<IteratorSetting> list = InputConfigurator.getIterators(AccumuloInputFormat.class, job);

    // Check the list size
    assertEquals(3, list.size());

    // Walk the list and make sure our settings are correct
    IteratorSetting setting = list.get(0);
    assertEquals(1, setting.getPriority());
    assertEquals(WholeRowIterator.class.getName(), setting.getIteratorClass());
    assertEquals("WholeRow", setting.getName());
    assertEquals(0, setting.getOptions().size());

    setting = list.get(1);
    assertEquals(2, setting.getPriority());
    assertEquals(VersioningIterator.class.getName(), setting.getIteratorClass());
    assertEquals("Versions", setting.getName());
    assertEquals(0, setting.getOptions().size());

    setting = list.get(2);
    assertEquals(3, setting.getPriority());
    assertEquals(CountingIterator.class.getName(), setting.getIteratorClass());
    assertEquals("Count", setting.getName());
    assertEquals(2, setting.getOptions().size());
    assertEquals("1", setting.getOptions().get("v1"));
    assertEquals("\0omg:!\\xyzzy", setting.getOptions().get("junk"));
  }

  /**
   * Test adding iterator options where the keys and values contain both the FIELD_SEPARATOR
   * character (':') and ITERATOR_SEPARATOR (',') characters. There should be no exceptions thrown
   * when trying to parse these types of option entries.
   *
   * This test makes sure that the expected raw values, as appears in the Job, are equal to what's
   * expected.
   */
  @Test
  public void testIteratorOptionEncoding() throws Throwable {
    String key = "colon:delimited:key";
    String value = "comma,delimited,value";
    IteratorSetting iter1 = new IteratorSetting(1, "iter1", WholeRowIterator.class);
    iter1.addOption(key, value);
    // also test if reusing options will create duplicate iterators
    InputFormatOptions<JobConf> opts = AccumuloInputFormat.configure()
        .clientProperties(clientProperties).table("test").auths(Authorizations.EMPTY);
    opts.addIterator(iter1).store(job);

    List<IteratorSetting> list = InputConfigurator.getIterators(AccumuloInputFormat.class, job);
    assertEquals(1, list.size());
    assertEquals(1, list.get(0).getOptions().size());
    assertEquals(list.get(0).getOptions().get(key), value);

    IteratorSetting iter2 = new IteratorSetting(1, "iter2", WholeRowIterator.class);
    iter2.addOption(key, value);
    iter2.addOption(key + "2", value);
    opts.addIterator(iter1).addIterator(iter2).store(job);
    list = InputConfigurator.getIterators(AccumuloInputFormat.class, job);
    assertEquals(2, list.size());
    assertEquals(1, list.get(0).getOptions().size());
    assertEquals(list.get(0).getOptions().get(key), value);
    assertEquals(2, list.get(1).getOptions().size());
    assertEquals(list.get(1).getOptions().get(key), value);
    assertEquals(list.get(1).getOptions().get(key + "2"), value);
  }

  /**
   * Test getting iterator settings for multiple iterators set
   */
  @Test
  public void testGetIteratorSettings() throws Exception {
    IteratorSetting iter1 = new IteratorSetting(1, "WholeRow", WholeRowIterator.class.getName());
    IteratorSetting iter2 = new IteratorSetting(2, "Versions", VersioningIterator.class.getName());
    IteratorSetting iter3 = new IteratorSetting(3, "Count", CountingIterator.class.getName());
    AccumuloInputFormat.configure().clientProperties(clientProperties).table("test")
        .auths(Authorizations.EMPTY).addIterator(iter1).addIterator(iter2).addIterator(iter3)
        .store(job);

    List<IteratorSetting> list = InputConfigurator.getIterators(AccumuloInputFormat.class, job);

    // Check the list size
    assertEquals(3, list.size());

    // Walk the list and make sure our settings are correct
    IteratorSetting setting = list.get(0);
    assertEquals(1, setting.getPriority());
    assertEquals(WholeRowIterator.class.getName(), setting.getIteratorClass());
    assertEquals("WholeRow", setting.getName());

    setting = list.get(1);
    assertEquals(2, setting.getPriority());
    assertEquals(VersioningIterator.class.getName(), setting.getIteratorClass());
    assertEquals("Versions", setting.getName());

    setting = list.get(2);
    assertEquals(3, setting.getPriority());
    assertEquals(CountingIterator.class.getName(), setting.getIteratorClass());
    assertEquals("Count", setting.getName());

  }

  @Test
  public void testSetRegex() throws Exception {
    String regex = ">\"*%<>\'\\";

    IteratorSetting is = new IteratorSetting(50, regex, RegExFilter.class);
    RegExFilter.setRegexs(is, regex, null, null, null, false);
    AccumuloInputFormat.configure().clientProperties(clientProperties).table("test")
        .auths(Authorizations.EMPTY).addIterator(is).store(job);

    assertEquals(regex,
        InputConfigurator.getIterators(AccumuloInputFormat.class, job).get(0).getName());
  }

  @Test
  public void testEmptyColumnFamily() throws Exception {
    Set<IteratorSetting.Column> cols = new HashSet<>();
    cols.add(new IteratorSetting.Column(new Text(""), null));
    cols.add(new IteratorSetting.Column(new Text("foo"), new Text("bar")));
    cols.add(new IteratorSetting.Column(new Text(""), new Text("bar")));
    cols.add(new IteratorSetting.Column(new Text(""), new Text("")));
    cols.add(new IteratorSetting.Column(new Text("foo"), new Text("")));
    AccumuloInputFormat.configure().clientProperties(clientProperties).table("test")
        .auths(Authorizations.EMPTY).fetchColumns(cols).store(job);

    assertEquals(cols, InputConfigurator.getFetchedColumns(AccumuloInputFormat.class, job));
  }

  @Test
  public void testJobStoreException() {
    // test exception thrown when not calling store
    AccumuloInputFormat.configure().clientProperties(clientProperties).table("table")
        .auths(Authorizations.EMPTY);
    AccumuloInputFormat aif = new AccumuloInputFormat();

    assertThrows("IllegalStateException should have been thrown for not calling store",
        IllegalStateException.class, () -> aif.getSplits(job, 1));
  }
}
