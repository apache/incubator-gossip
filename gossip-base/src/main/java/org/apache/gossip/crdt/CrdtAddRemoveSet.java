package org.apache.gossip.crdt;

import java.util.Set;

// Interface extends CrdtSet interface with add and remove operation that are guaranteed to be immutable.
// If your implementation provide immutable add/remove operations you can extend AbstractCRDTStringSetTest to check it in the most ways.

public interface CrdtAddRemoveSet<T, SetType extends Set<T>, R extends CrdtAddRemoveSet<T, SetType, R>> extends CrdtSet<T, SetType, R> {
  R add(T element);

  R remove(T element);
}
