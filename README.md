# Gossip ![Build status](https://travis-ci.org/edwardcapriolo/incubator-gossip.svg?)

Gossip protocol is a method for a group of nodes to discover and check the liveliness of a cluster. More information can be found at http://en.wikipedia.org/wiki/Gossip_protocol.

The original implementation was forked from https://code.google.com/p/java-gossip/. Several bug fixes and changes have already been added.

Usage
-----

To gossip you need one or more seed nodes. Seed is just a list of places to initially connect to.

```java
  GossipSettings settings = new GossipSettings();
  int seedNodes = 3;
  List<GossipMember> startupMembers = new ArrayList<>();
  for (int i = 1; i < seedNodes+1; ++i) {
    startupMembers.add(new RemoteGossipMember("127.0.0." + i, 2000, i + ""));
  }
```

Here we start five gossip processes and check that they discover each other. (Normally these are on different hosts but here we give each process a distinct local ip.

```java
  List<GossipService> clients = new ArrayList<>();
  int clusterMembers = 5;
  for (int i = 1; i < clusterMembers+1; ++i) {
    GossipService gossipService = new GossipService("127.0.0." + i, 2000, i + "",
      LogLevel.DEBUG, startupMembers, settings, null);
    clients.add(gossipService);
    gossipService.start();
  }
```

Later we can check that the nodes discover each other

```java
  Thread.sleep(10000);
  for (int i = 0; i < clusterMembers; ++i) {
    Assert.assertEquals(4, clients.get(i).get_gossipManager().getMemberList().size());
  }
```

Usage with Settings File
-----

For a very simple client setup with a settings file you first need a JSON file such as:

```json
[{
  "id":"419af818-0114-4c7b-8fdb-952915335ce4",
  "port":50001,
  "gossip_interval":1000,
  "cleanup_interval":10000,
  "members":[
    {"host":"127.0.0.1", "port":50000}
  ]
}]
```

where:

* `id` - is a unique id for this node (you can use any string, but above we use a UUID)
* `port` - the port to use on the default adapter on the node's machine
* `gossip_interval` - how often (in milliseconds) to gossip list of members to other node(s)
* `cleanup_interval` - when to remove 'dead' nodes (in milliseconds)
* `members` - initial seed nodes

Then starting a local node is as simple as:

```java
GossipService gossipService = new GossipService(
  StartupSettings.fromJSONFile( "node_settings.json" )
);
gossipService.start();
```

And then when all is done, shutdown with:

```java
gossipService.shutdown();
```

Event Listener
------

The status can be polled using the getters that return immutable lists.

```java
   List<LocalGossipMember> getMemberList()
   public List<LocalGossipMember> getDeadList()
```

These can be accessed from the `GossipManager` on your `GossipService`, e.g:
`gossipService.get_gossipManager().getMemberList();`

Users can also attach an event listener:

```java
  GossipService gossipService = new GossipService("127.0.0." + i, 2000, i + "", LogLevel.DEBUG,
          startupMembers, settings,
          new GossipListener(){
    @Override
    public void gossipEvent(GossipMember member, GossipState state) {
      System.out.println(member+" "+ state);
    }
  });
```

