# Gossip ![Build status](https://travis-ci.org/edwardcapriolo/incubator-gossip.svg?)

Gossip protocol is a method for a group of nodes to discover and check the liveliness of a cluster. More information can be found at http://en.wikipedia.org/wiki/Gossip_protocol.

The original implementation was forked from https://code.google.com/p/java-gossip/. Several bug fixes and changes have already been added.

A set of easily-run examples, illustrating various features of Gossip, are available in the gossip-examples module. The README.md file, in that module described how to run those examples.

Below, a list of code snippits which show how to incorproate Apache Gossip into your project.  

Usage
-----

To gossip you need one or more seed nodes. Seed is just a list of places to initially connect to.

```java
  GossipSettings settings = new GossipSettings();
  int seedNodes = 3;
  List<Member> startupMembers = new ArrayList<>();
  String cluster = UUID.randomUUID().toString();
  for (int i = 1; i < seedNodes+1; ++i) {
    URI uri = new URI("udp://" + "127.0.0.1" + ":" + (50000 + i));
    startupMembers.add(new RemoteMember(cluster, uri, i + ""));
  }
```

Here we start five gossip processes and check that they discover each other. (Normally these are on different hosts but here we give each process a distinct local ip.

```java
  List<GossipManager> clients = new ArrayList<>();
  int clusterMembers = 5;
  for (int i = 1; i < clusterMembers+1; ++i) {
    URI uri = new URI("udp://" + "127.0.0.1" + ":" + (50000 + i));
    GossipManager gossipManager = GossipManagerBuilder.newBuilder()
           .cluster(cluster)
           .uri(uri)
           .id(i + "")
           .gossipSettings(settings)
           .gossipMembers(startupMembers)
           .build();
    gossipManager.init();
    clients.add(gossipManager);
  }
```

Later we can check that the nodes discover each other

```java
  Thread.sleep(10000);
  for (int i = 0; i < clusterMembers; ++i) {
    Assert.assertEquals(4, clients.get(i).getLiveMembers().size());
  }
```

Usage with Settings File
-----

For a very simple client setup with a settings file you first need a JSON file such as:

```json
[{
  "cluster":"9f1e6ddf-8e1c-4026-8fc6-8585d0132f77",
  "id":"447c5bec-f112-492d-968b-f64c8e36dfd7",
  "uri":"udp://127.0.0.1:50001",
  "gossip_interval":1000,
  "cleanup_interval":10000,
  "members":[
    {"cluster": "9f1e6ddf-8e1c-4026-8fc6-8585d0132f77","uri":"udp://127.0.0.1:5000"}
  ]
}]
```

where:

* `cluster` - is the name of the cluster 
* `id` - is a unique id for this node (you can use any string, but above we use a UUID)
* `uri` - is a URI object containing IP/hostname and port to use on the default adapter on the node's machine
* `gossip_interval` - how often (in milliseconds) to gossip list of members to other node(s)
* `cleanup_interval` - when to remove 'dead' nodes (in milliseconds) (deprecated may be coming back)
* `members` - initial seed nodes

Then starting a local node is as simple as:

```java
GossipManager gossipManager = GossipManagerBuilder.newBuilder()
        .startupSettings(StartupSettings.fromJSONFile(new File("node_settings.json")))
        .build();
gossipManager.init();
```

And then when all is done, shutdown with:

```java
gossipManager.shutdown();
```

Event Listener
------

The status can be polled using the getters that return immutable lists.

```java
   public List<LocalMember> getLiveMembers()
   public List<LocalMember> getDeadMembers()
```

Users can also attach an event listener:

```java
GossipManager gossipService = GossipManagerBuilder.newBuilder()
        .cluster(cluster)
        .uri(uri)
        .id(i + "")
        .gossipSettings(settings)
        .gossipMembers(startupMembers)
        .listener(new GossipListener() {
          @Override
          public void gossipEvent(Member member, GossipState state) {
            System.out.println(System.currentTimeMillis() + " Member " + j + " reports "
                  + member + " " + state);
          }
        })
        .build();
//The lambda syntax is (a,b) -> { }  //NICE!
```

