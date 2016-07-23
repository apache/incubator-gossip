/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.gossip;

import org.apache.log4j.Logger;
import org.json.JSONException;
import org.junit.Test;

import io.teknek.tunit.TUnit;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Tests support of using {@code StartupSettings} and thereby reading
 * setup config from file.
 */
public class StartupSettingsTest {
  private static final Logger log = Logger.getLogger( StartupSettingsTest.class );
  private static final String CLUSTER = UUID.randomUUID().toString();

  @Test
  public void testUsingSettingsFile() throws IOException, InterruptedException, JSONException, URISyntaxException {
    File settingsFile = File.createTempFile("gossipTest",".json");
    log.debug( "Using settings file: " + settingsFile.getAbsolutePath() );
    settingsFile.deleteOnExit();
    writeSettingsFile(settingsFile);
    URI uri = new URI("udp://" + "127.0.0.1" + ":" + 50000);
    final GossipService firstService = new GossipService(
            CLUSTER, uri, UUID.randomUUID().toString(),
      new ArrayList<GossipMember>(), new GossipSettings(), null);
    
    firstService.start();
    
    TUnit.assertThat(new Callable<Integer> (){
      public Integer call() throws Exception {
          return firstService.get_gossipManager().getLiveMembers().size();
      }}).afterWaitingAtMost(30, TimeUnit.SECONDS).isEqualTo(0);
    final GossipService serviceUnderTest = new GossipService(
            StartupSettings.fromJSONFile( settingsFile )
          );
    serviceUnderTest.start();
    TUnit.assertThat(new Callable<Integer> (){
      public Integer call() throws Exception {
          return serviceUnderTest.get_gossipManager().getLiveMembers().size();
      }}).afterWaitingAtMost(10, TimeUnit.SECONDS).isEqualTo(1);
    firstService.shutdown();
    serviceUnderTest.shutdown();
  }

  private void writeSettingsFile( File target ) throws IOException {
    String settings =
            "[{\n" + // It is odd that this is meant to be in an array, but oh well.
            "  \"cluster\":\"" + CLUSTER + "\",\n" +
            "  \"id\":\"" + UUID.randomUUID() + "\",\n" +
            "  \"uri\":\"udp://127.0.0.1:50001\",\n" +
            "  \"gossip_interval\":1000,\n" +
            "  \"cleanup_interval\":10000,\n" +
            "  \"members\":[\n" +
            "    {\"cluster\": \"" + CLUSTER + "\",\"uri\":\"udp://127.0.0.1:5000\"}\n" +
            "  ]\n" +
            "}]";

    log.info( "Using settings file with contents of:\n---\n" + settings + "\n---" );
    FileOutputStream output = new FileOutputStream(target);
    output.write( settings.getBytes() );
    output.close();
  }
}
