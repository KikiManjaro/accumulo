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
package org.apache.accumulo.core.metadata.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

public class SortSkewTest {
  private static final String shortpath = "1";
  private static final String longpath =
      "/verylongpath/12345679xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxiiiiiiiiiiiiiiiiii/zzzzzzzzzzzzzzzzzzzzz"
          + "aaaaaaaaaaaaabbbbbbbbbbbbbbbbbbbccccccccccccccccccccccccccxxxxxxxxxxxxxxxxxxxxxxxxyyyyyyyyyyyyyyyyzzzzzzzzzzzzzzzz";;
  // these are values previously generated from SortSkew.getCode() for the above
  private static final String shortcode = "9416ac93";
  private static final String longcode = "b9ddf266";

  @Test
  public void verifyCodeSize() {
    int expectedLength = SortSkew.SORTSKEW_LENGTH;
    assertEquals(expectedLength, SortSkew.getCode(shortpath).length());
    assertEquals(expectedLength, SortSkew.getCode(longpath).length());
  }

  @Test
  public void verifySame() {
    assertEquals(SortSkew.getCode("123"), SortSkew.getCode("123"));
    assertNotEquals(SortSkew.getCode("123"), SortSkew.getCode("321"));
  }

  @Test
  public void verifyStable() {
    assertEquals(shortcode, SortSkew.getCode(shortpath));
    assertEquals(longcode, SortSkew.getCode(longpath));
  }

}
